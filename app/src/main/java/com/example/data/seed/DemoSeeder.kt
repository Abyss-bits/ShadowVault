package com.example.data.seed

import androidx.room.withTransaction
import com.example.crypto.PinHasher
import com.example.data.local.AccountEntity
import com.example.data.local.AppDatabase
import com.example.data.local.AuditLogEntity
import com.example.data.local.DepositEntity
import com.example.data.local.KnownRecipientEntity
import com.example.data.local.LedgerEntryEntity
import com.example.data.local.SecurityConfigEntity
import com.example.data.local.TransferEntity
import com.qalqan.domain.clock.AppClock
import com.qalqan.domain.model.AccountType
import com.qalqan.domain.model.DepositStatus
import com.qalqan.domain.model.TransferCodeType
import com.qalqan.domain.model.TransferStatus
import com.qalqan.domain.model.TransferType
import com.qalqan.domain.model.UserRole
import com.qalqan.domain.validation.KzIdentifierValidator
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.random.Random

@Singleton
class DemoSeeder @Inject constructor(
  private val db: AppDatabase,
  private val pinHasher: PinHasher,
  private val clock: AppClock
) {
  suspend fun ensureSeeded() = withContext(Dispatchers.Default) {
    if (db.securityConfigDao().getConfig() != null) return@withContext
    seedAll()
  }

  suspend fun resetDemoData() = withContext(Dispatchers.Default) {
    db.withTransaction {
      db.accountDao().clearAll()
      db.depositDao().clearAll()
      db.transferDao().clearAll()
      db.secondHandDao().clearAll()
      db.nbrkIncidentDao().clearAll()
      db.securityConfigDao().clearAll()
      db.ledgerDao().clearAll()
      db.auditLogDao().clearAll()
      db.knownRecipientDao().clearAll()
    }
    seedAll()
  }

  private suspend fun seedAll() {
    val now = clock.nowMillis()
    val mainIban = KzIdentifierValidator.buildValidKzIban("601", "0001004521800")
    val safeIban = KzIdentifierValidator.buildValidKzIban("601", "0001009841200")
    val escrowIban = KzIdentifierValidator.buildValidKzIban("601", "9999000000001")

    val trusted1Iban = KzIdentifierValidator.buildValidKzIban("947", "0002211334455")
    val trusted2Iban = KzIdentifierValidator.buildValidKzIban("722", "0004455667788")
    val trusted3Iban = KzIdentifierValidator.buildValidKzIban("856", "0007788991122")
    val dropper1Iban = KzIdentifierValidator.buildValidKzIban("965", "0006660006661")
    val dropper2Iban = KzIdentifierValidator.buildValidKzIban("826", "0007770007772")

    val accounts = listOf(
      AccountEntity(
        id = ACC_MAIN,
        title = "Основной текущий счёт",
        iban = mainIban,
        type = AccountType.MAIN,
        balanceTenge = 845_200L
      ),
      AccountEntity(
        id = ACC_ISOLATED_SAFE,
        title = "Текущий счёт KZT",
        iban = safeIban,
        type = AccountType.ISOLATED_SAFE,
        balanceTenge = 48_500L
      ),
      AccountEntity(
        id = ACC_ESCROW_HOLD,
        title = "Внутренний эскроу-счёт банка",
        iban = escrowIban,
        type = AccountType.ESCROW_HOLD,
        balanceTenge = 0L
      )
    )

    val deposits = listOf(
      DepositEntity(
        id = DEP_PREMIER,
        title = "Премиальный Money Lock",
        balanceTenge = 8_450_000L,
        annualRatePercent = 14.5,
        isMoneyLocked = true,
        isDecoy = false,
        status = DepositStatus.ACTIVE
      ),
      DepositEntity(
        id = DEP_RESERVE,
        title = "Накопительный сейф",
        balanceTenge = 1_200_000L,
        annualRatePercent = 13.0,
        isMoneyLocked = true,
        isDecoy = false,
        status = DepositStatus.ACTIVE
      ),
      DepositEntity(
        id = DEP_DECOY,
        title = "Копилка на отпуск",
        balanceTenge = 120_000L,
        annualRatePercent = 10.5,
        isMoneyLocked = false,
        isDecoy = true,
        status = DepositStatus.ACTIVE
      )
    )

    val recipients = listOf(
      KnownRecipientEntity(
        id = "rec_trusted_1",
        name = "Айгуль Сапарова (супруга)",
        iban = trusted1Iban,
        bankName = "Qalqan Bank",
        isDropperFlag = false
      ),
      KnownRecipientEntity(
        id = "rec_trusted_2",
        name = "Данияр Касымов",
        iban = trusted2Iban,
        bankName = "Halyk Demo",
        isDropperFlag = false
      ),
      KnownRecipientEntity(
        id = "rec_trusted_3",
        name = "ТОО «Алатау Энергосбыт»",
        iban = trusted3Iban,
        bankName = "CenterCredit Demo",
        isDropperFlag = false
      ),
      KnownRecipientEntity(
        id = "rec_dropper_1",
        name = "Неизвестный счёт (фрод-база)",
        iban = dropper1Iban,
        bankName = "DropBank Demo",
        isDropperFlag = true
      ),
      KnownRecipientEntity(
        id = "rec_dropper_2",
        name = "Транзитный кошелёк (фрод-база)",
        iban = dropper2Iban,
        bankName = "ShadowPay Demo",
        isDropperFlag = true
      )
    )

    // Демо-ПИН и коды задаются исключительно здесь (в сидере) и сразу хешируются PBKDF2.
    val config = SecurityConfigEntity(
      id = 1,
      standardPinHash = pinHasher.hashSecret(DEMO_STANDARD_PIN),
      duressPinHash = pinHasher.hashSecret(DEMO_DURESS_PIN),
      standardCodeHash = pinHasher.hashSecret(DEMO_STANDARD_TRANSFER_CODE),
      duressCodeHash = pinHasher.hashSecret(DEMO_DURESS_TRANSFER_CODE),
      secondHandPinHash = pinHasher.hashSecret(DEMO_SECOND_HAND_PIN),
      thresholdTenge = 300_000L,
      isolatedSafeTargetTenge = 48_500L,
      coolingOffMinutes = 30,
      escrowBusinessDays = 5,
      primaryTrustedName = "Айгуль Сапарова",
      primaryTrustedPhone = "+7 701 555 12 34",
      backupTrustedName = "Тимур Есенов",
      backupTrustedPhone = "+7 707 888 45 67",
      failedPinAttempts = 0,
      lockedUntilMillis = 0L,
      lastSecurityChangeMillis = now - 30L * 86_400_000L,
      seed = 42L
    )

    val decoyTransfers = generateDecoyHistory(now)
    val mainTransfers = listOf(
      TransferEntity(
        id = "tr_main_1",
        sourceAccountId = ACC_MAIN,
        recipientName = "Айгуль Сапарова (супруга)",
        recipientIban = trusted1Iban,
        isNewRecipient = false,
        amountTenge = 65_000L,
        transferType = TransferType.REGULAR,
        codeType = TransferCodeType.STANDARD,
        status = TransferStatus.COMPLETED,
        reviewDeadlineMillis = null,
        coolingOffUntilMillis = null,
        riskScore = 0,
        riskFactorsSummary = "Доверенный получатель",
        createdAtMillis = now - 2L * 86_400_000L,
        isDuressOperation = false
      ),
      TransferEntity(
        id = "tr_main_2",
        sourceAccountId = ACC_MAIN,
        recipientName = "ТОО «Алатау Энергосбыт»",
        recipientIban = trusted3Iban,
        isNewRecipient = false,
        amountTenge = 28_400L,
        transferType = TransferType.MERCHANT_PURCHASE,
        codeType = TransferCodeType.STANDARD,
        status = TransferStatus.COMPLETED,
        reviewDeadlineMillis = null,
        coolingOffUntilMillis = null,
        riskScore = 0,
        riskFactorsSummary = "Регулярный платёж",
        createdAtMillis = now - 5L * 86_400_000L,
        isDuressOperation = false
      )
    )

    val genesisGroup = "genesis_${UUID.randomUUID()}"
    val ledgerEntries = listOf(
      LedgerEntryEntity(
        id = "le_init_1",
        transactionGroupId = genesisGroup,
        accountOrDepositId = "SYSTEM_CAPITAL",
        entrySide = "DEBIT",
        amountTenge = 10_663_700L,
        description = "Начальная эмиссия демо-балансов",
        createdAtMillis = now
      ),
      LedgerEntryEntity(
        id = "le_init_2",
        transactionGroupId = genesisGroup,
        accountOrDepositId = ACC_MAIN,
        entrySide = "CREDIT",
        amountTenge = 845_200L,
        description = "Основной текущий счёт",
        createdAtMillis = now
      ),
      LedgerEntryEntity(
        id = "le_init_3",
        transactionGroupId = genesisGroup,
        accountOrDepositId = ACC_ISOLATED_SAFE,
        entrySide = "CREDIT",
        amountTenge = 48_500L,
        description = "Изолированный субсчёт",
        createdAtMillis = now
      ),
      LedgerEntryEntity(
        id = "le_init_4",
        transactionGroupId = genesisGroup,
        accountOrDepositId = DEP_PREMIER,
        entrySide = "CREDIT",
        amountTenge = 8_450_000L,
        description = "Премиальный Money Lock",
        createdAtMillis = now
      ),
      LedgerEntryEntity(
        id = "le_init_5",
        transactionGroupId = genesisGroup,
        accountOrDepositId = DEP_RESERVE,
        entrySide = "CREDIT",
        amountTenge = 1_200_000L,
        description = "Накопительный сейф",
        createdAtMillis = now
      ),
      LedgerEntryEntity(
        id = "le_init_6",
        transactionGroupId = genesisGroup,
        accountOrDepositId = DEP_DECOY,
        entrySide = "CREDIT",
        amountTenge = 120_000L,
        description = "Копилка на отпуск",
        createdAtMillis = now
      )
    )

    val auditInit = AuditLogEntity(
      id = "aud_init_1",
      actorRole = UserRole.CLIENT_STANDARD,
      action = "SEED_INITIALIZED",
      details = "Инициализирован защищённый локальный контур Qalqan Safe",
      createdAtMillis = now
    )

    db.withTransaction {
      db.accountDao().upsertAll(accounts)
      db.depositDao().upsertAll(deposits)
      db.knownRecipientDao().upsertAll(recipients)
      db.securityConfigDao().upsert(config)
      db.transferDao().upsertAll(mainTransfers + decoyTransfers)
      db.ledgerDao().insertAll(ledgerEntries)
      db.auditLogDao().insert(auditInit)
    }
  }

  private fun generateDecoyHistory(nowMillis: Long): List<TransferEntity> {
    val merchants = listOf(
      "Magnum Cash&Carry" to 4_250L,
      "Яндекс Go Алматы" to 1_680L,
      "Кофейня Coffeedelia" to 2_400L,
      "Аптека Europharma" to 3_890L,
      "Small Супермаркет" to 5_120L,
      "АЗС Helios" to 7_500L,
      "Алсеко Коммунальные" to 9_400L,
      "Onay! Транспорт" to 800L,
      "Технодом Экспресс" to 6_300L,
      "Wolt Доставка" to 4_750L
    )
    val rng = Random(202611L)
    val items = mutableListOf<TransferEntity>()
    for (i in 0 until 24) {
      val daysAgo = (i * 2.5).toLong().coerceIn(1L, 59L)
      val (merchant, baseAmount) = merchants[i % merchants.size]
      val amount = baseAmount + rng.nextLong(-200L, 450L)
      items += TransferEntity(
        id = "tr_decoy_${i + 1}",
        sourceAccountId = ACC_ISOLATED_SAFE,
        recipientName = merchant,
        recipientIban = KzIdentifierValidator.buildValidKzIban("722", (1000000000000L + i).toString()),
        isNewRecipient = false,
        amountTenge = amount.coerceAtLeast(350L),
        transferType = TransferType.MERCHANT_PURCHASE,
        codeType = TransferCodeType.NONE,
        status = TransferStatus.COMPLETED,
        reviewDeadlineMillis = null,
        coolingOffUntilMillis = null,
        riskScore = 0,
        riskFactorsSummary = "Повседневная покупка",
        createdAtMillis = nowMillis - daysAgo * 86_400_000L - rng.nextLong(1_000_000L, 28_000_000L),
        isDuressOperation = false
      )
    }
    return items
  }

  companion object {
    const val ACC_MAIN = "acc_main"
    const val ACC_ISOLATED_SAFE = "acc_isolated_safe"
    const val ACC_ESCROW_HOLD = "acc_escrow_hold"

    const val DEP_PREMIER = "dep_premier"
    const val DEP_RESERVE = "dep_reserve"
    const val DEP_DECOY = "dep_decoy"

    private const val DEMO_STANDARD_PIN = "1234"
    private const val DEMO_DURESS_PIN = "9110"
    private const val DEMO_STANDARD_TRANSFER_CODE = "5555"
    private const val DEMO_DURESS_TRANSFER_CODE = "9999"
    private const val DEMO_SECOND_HAND_PIN = "2468"
  }
}
