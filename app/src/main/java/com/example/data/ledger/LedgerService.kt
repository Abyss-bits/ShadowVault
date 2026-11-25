package com.example.data.ledger

import androidx.room.withTransaction
import com.example.data.local.AppDatabase
import com.example.data.local.LedgerEntryEntity
import com.qalqan.domain.clock.AppClock
import com.qalqan.domain.ledger.LedgerPosting
import com.qalqan.domain.ledger.LedgerRules
import com.qalqan.domain.ledger.LedgerSide
import com.qalqan.domain.model.DepositStatus
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LedgerService @Inject constructor(
  private val db: AppDatabase,
  private val clock: AppClock
) {
  /**
   * Атомарное перемещение средств между контурами (счёт, депозит, эскроу или внешний клиринг)
   * внутри одной транзакции Room с обязательной двойной записью DEBIT/CREDIT.
   */
  suspend fun postTransferMovement(
    fromNodeId: String,
    toNodeId: String,
    amountTenge: Long,
    description: String,
    beforeCreditHook: (() -> Unit)? = null
  ) {
    require(amountTenge > 0L) { "Сумма проводки должна быть положительной" }
    require(fromNodeId != toNodeId) { "Счёт списания и зачисления должны отличаться" }

    val postings = listOf(
      LedgerPosting(fromNodeId, LedgerSide.DEBIT, amountTenge),
      LedgerPosting(toNodeId, LedgerSide.CREDIT, amountTenge)
    )
    require(LedgerRules.validateBalancedGroup(postings)) { "Нарушен баланс двойной записи" }

    db.withTransaction {
      val now = clock.nowMillis()
      val groupId = "lg_${UUID.randomUUID()}"

      debitNode(fromNodeId, amountTenge)

      // Хук для тестирования отката транзакции при сбое посередине
      beforeCreditHook?.invoke()

      creditNode(toNodeId, amountTenge)

      db.ledgerDao().insertAll(
        listOf(
          LedgerEntryEntity(
            id = "le_${UUID.randomUUID()}",
            transactionGroupId = groupId,
            accountOrDepositId = fromNodeId,
            entrySide = LedgerSide.DEBIT.name,
            amountTenge = amountTenge,
            description = description,
            createdAtMillis = now
          ),
          LedgerEntryEntity(
            id = "le_${UUID.randomUUID()}",
            transactionGroupId = groupId,
            accountOrDepositId = toNodeId,
            entrySide = LedgerSide.CREDIT.name,
            amountTenge = amountTenge,
            description = description,
            createdAtMillis = now
          )
        )
      )
    }
  }

  private suspend fun debitNode(nodeId: String, amountTenge: Long) {
    val account = db.accountDao().getById(nodeId)
    if (account != null) {
      check(account.balanceTenge >= amountTenge) { "Недостаточно средств на счёте" }
      db.accountDao().updateBalance(account.id, account.balanceTenge - amountTenge)
      return
    }
    val deposit = db.depositDao().getById(nodeId)
    if (deposit != null) {
      check(deposit.balanceTenge >= amountTenge) { "Недостаточно средств на депозите" }
      db.depositDao().updateDepositState(
        id = deposit.id,
        status = deposit.status,
        isLocked = deposit.isMoneyLocked,
        balance = deposit.balanceTenge - amountTenge
      )
      return
    }
    error("Неизвестный узел списания: $nodeId")
  }

  private suspend fun creditNode(nodeId: String, amountTenge: Long) {
    if (nodeId == LedgerRules.EXTERNAL_SETTLEMENT_ID) {
      // Внешнее зачисление фиксируется кредитом EXTERNAL_SETTLEMENT в таблице ledger_entries
      return
    }
    val account = db.accountDao().getById(nodeId)
    if (account != null) {
      db.accountDao().updateBalance(account.id, account.balanceTenge + amountTenge)
      return
    }
    val deposit = db.depositDao().getById(nodeId)
    if (deposit != null) {
      val nextStatus = if (deposit.status == DepositStatus.PENDING_EARLY_CLOSURE) {
        DepositStatus.ACTIVE
      } else {
        deposit.status
      }
      db.depositDao().updateDepositState(
        id = deposit.id,
        status = nextStatus,
        isLocked = deposit.isMoneyLocked,
        balance = deposit.balanceTenge + amountTenge
      )
      return
    }
    error("Неизвестный узел зачисления: $nodeId")
  }

  suspend fun checkConservationInvariant(expectedTotalSystemCapital: Long): Boolean {
    val accounts = db.accountDao().getAllSnapshot()
    val deposits = db.depositDao().getAllSnapshot()
    val entries = db.ledgerDao().getAllSnapshot()

    if (accounts.any { it.balanceTenge < 0L } || deposits.any { it.balanceTenge < 0L }) {
      return false
    }

    val allGroupsBalanced = entries
      .groupBy { it.transactionGroupId }
      .values
      .all { group ->
        val debits = group.filter { it.entrySide == LedgerSide.DEBIT.name }.sumOf { it.amountTenge }
        val credits = group.filter { it.entrySide == LedgerSide.CREDIT.name }.sumOf { it.amountTenge }
        group.size >= 2 && debits > 0L && debits == credits
      }
    if (!allGroupsBalanced) return false

    val externalOutflow = entries
      .filter { it.accountOrDepositId == LedgerRules.EXTERNAL_SETTLEMENT_ID }
      .sumOf { entry ->
        if (entry.entrySide == LedgerSide.CREDIT.name) entry.amountTenge else -entry.amountTenge
      }

    val onBooksTotal = accounts.sumOf { it.balanceTenge } + deposits.sumOf { it.balanceTenge }
    return (onBooksTotal + externalOutflow) == expectedTotalSystemCapital
  }
}
