package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.cash.turbine.test
import com.example.crypto.PinHasher
import com.example.data.ledger.LedgerService
import com.example.data.local.AppDatabase
import com.example.data.repository.QalqanRepository
import com.example.data.repository.SecondHandActionResult
import com.example.data.repository.SecurityUpdateResult
import com.example.data.repository.TransferSubmitResult
import com.example.data.seed.DemoSeeder
import com.example.data.simulator.NbrkAntifraudApiSimulator
import com.qalqan.domain.clock.BusinessCalendar
import com.qalqan.domain.clock.SimulatedClock
import com.qalqan.domain.model.DepositStatus
import com.qalqan.domain.model.NbrkTrigger
import com.qalqan.domain.model.PinAuthResult
import com.qalqan.domain.model.SecondHandStatus
import com.qalqan.domain.model.SessionMode
import com.qalqan.domain.model.TransferStatus
import com.qalqan.domain.validation.KzIdentifierValidator
import java.time.Instant
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class Stage4SecondHandAndSecurityTest {

  private lateinit var db: AppDatabase
  private lateinit var clock: SimulatedClock
  private lateinit var calendar: BusinessCalendar
  private lateinit var pinHasher: PinHasher
  private lateinit var seeder: DemoSeeder
  private lateinit var ledgerService: LedgerService
  private lateinit var nbrkSimulator: NbrkAntifraudApiSimulator
  private lateinit var repository: QalqanRepository

  private val initialCapital = 10_663_700L

  @Before
  fun setUp() = runTest {
    val context = ApplicationProvider.getApplicationContext<Context>()
    db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
      .allowMainThreadQueries()
      .build()
    clock = SimulatedClock(initialMillis = Instant.parse("2026-11-20T09:00:00Z").toEpochMilli())
    calendar = BusinessCalendar()
    pinHasher = PinHasher()
    seeder = DemoSeeder(db, pinHasher, clock)
    ledgerService = LedgerService(db, clock)
    nbrkSimulator = NbrkAntifraudApiSimulator(db, clock)
    repository = QalqanRepository(db, pinHasher, clock, calendar, seeder, ledgerService, nbrkSimulator)
    repository.initializeDemoData()
  }

  @After
  fun tearDown() {
    db.close()
  }

  @Test
  fun `early deposit closure awaits Second Hand, enforces 10-min pause and PIN, then settles`() = runTest {
    repository.authenticatePin("1234")
    val newIban = KzIdentifierValidator.buildValidKzIban("947", "0003332221118")

    val submit = repository.submitEarlyDepositWithdrawal(
      depositId = DemoSeeder.DEP_RESERVE,
      recipientName = "Новый контрагент",
      recipientIban = newIban,
      amountTenge = 1_200_000L,
      transferCode = "5555"
    )
    assertTrue(submit is TransferSubmitResult.Success)
    val ok = submit as TransferSubmitResult.Success
    assertEquals(TransferStatus.AWAITING_SECOND_HAND, ok.uiStatus)

    val dep = db.depositDao().getById(DemoSeeder.DEP_RESERVE)!!
    assertEquals(DepositStatus.PENDING_EARLY_CLOSURE, dep.status)
    assertEquals(0L, dep.balanceTenge)
    assertEquals(1_200_000L, db.accountDao().getById(DemoSeeder.ACC_ESCROW_HOLD)!!.balanceTenge)
    assertTrue(ledgerService.checkConservationInvariant(initialCapital))

    val requests = db.secondHandDao().getPending()
    assertEquals(1, requests.size)
    val req = requests.first()

    // До истечения 10 минут подтверждение заблокировано
    val tooEarly = repository.approveSecondHandRequest(req.id, "2468")
    assertTrue(tooEarly is SecondHandActionResult.Rejected)

    // После 11 минут неверный ПИН отклоняется
    clock.advanceByMinutes(11)
    val wrongPin = repository.approveSecondHandRequest(req.id, "0000")
    assertTrue(wrongPin is SecondHandActionResult.Rejected)

    // Верный ПИН Второй руки завершает операцию и закрывает депозит
    val approved = repository.approveSecondHandRequest(req.id, "2468", "Проверено лично")
    assertTrue(approved is SecondHandActionResult.Success)

    assertEquals(DepositStatus.CLOSED, db.depositDao().getById(DemoSeeder.DEP_RESERVE)!!.status)
    assertEquals(TransferStatus.COMPLETED, db.transferDao().getById(ok.transferId)!!.status)
    assertEquals(SecondHandStatus.APPROVED, db.secondHandDao().getById(req.id)!!.status)
    assertEquals(0L, db.accountDao().getById(DemoSeeder.ACC_ESCROW_HOLD)!!.balanceTenge)
    assertTrue(ledgerService.checkConservationInvariant(initialCapital))
  }

  @Test
  fun `second hand rejection restores Money Lock deposit and triggers urgent NBRK incident`() = runTest {
    repository.authenticatePin("1234")
    val newIban = KzIdentifierValidator.buildValidKzIban("722", "0009998887771")

    val submit = repository.submitEarlyDepositWithdrawal(
      depositId = DemoSeeder.DEP_RESERVE,
      recipientName = "Лже-служба безопасности",
      recipientIban = newIban,
      amountTenge = 1_200_000L,
      transferCode = "5555"
    ) as TransferSubmitResult.Success

    val req = db.secondHandDao().getPending().first()
    val rejected = repository.rejectSecondHandRequestAsFraud(req.id, "Клиент под давлением звонящего")
    assertTrue(rejected is SecondHandActionResult.Success)
    val rejectedOk = rejected as SecondHandActionResult.Success
    assertTrue(rejectedOk.incidentCode != null)

    val restoredDeposit = db.depositDao().getById(DemoSeeder.DEP_RESERVE)!!
    assertEquals(DepositStatus.ACTIVE, restoredDeposit.status)
    assertTrue(restoredDeposit.isMoneyLocked)
    assertEquals(1_200_000L, restoredDeposit.balanceTenge)
    assertEquals(0L, db.accountDao().getById(DemoSeeder.ACC_ESCROW_HOLD)!!.balanceTenge)
    assertEquals(TransferStatus.BLOCKED_FRAUD, db.transferDao().getById(submit.transferId)!!.status)
    assertEquals(SecondHandStatus.REJECTED_FRAUD, db.secondHandDao().getById(req.id)!!.status)
    assertTrue(ledgerService.checkConservationInvariant(initialCapital))

    repository.observeNbrkIncidents().test {
      val incidents = awaitItem()
      assertTrue(incidents.any { it.triggerType == NbrkTrigger.SECOND_HAND_REJECTED })
      cancelAndIgnoreRemainingEvents()
    }
  }

  @Test
  fun `backup contact escalation and 24h timeout auto-rollback preserve Money Lock and invariant`() = runTest {
    repository.authenticatePin("1234")
    val newIban = KzIdentifierValidator.buildValidKzIban("856", "0001122334455")

    val submit = repository.submitEarlyDepositWithdrawal(
      depositId = DemoSeeder.DEP_RESERVE,
      recipientName = "Новый получатель",
      recipientIban = newIban,
      amountTenge = 1_200_000L,
      transferCode = "5555"
    ) as TransferSubmitResult.Success

    val req = db.secondHandDao().getPending().first()
    val escalated = repository.escalateSecondHandToBackup(req.id)
    assertTrue(escalated is SecondHandActionResult.Success)
    val updatedReq = db.secondHandDao().getById(req.id)!!
    assertTrue(updatedReq.isBackupAssigned)
    assertEquals("Тимур Есенов", updatedReq.assignedContactName)

    // Проходят 24+ часа без ответа -> автооткат под Money Lock
    clock.advanceByHours(25)
    val expiredCount = repository.resolveExpiredSecondHandRequests()
    assertEquals(1, expiredCount)

    val restoredDeposit = db.depositDao().getById(DemoSeeder.DEP_RESERVE)!!
    assertEquals(DepositStatus.ACTIVE, restoredDeposit.status)
    assertEquals(1_200_000L, restoredDeposit.balanceTenge)
    assertEquals(SecondHandStatus.EXPIRED, db.secondHandDao().getById(req.id)!!.status)
    assertEquals(TransferStatus.BLOCKED_FRAUD, db.transferDao().getById(submit.transferId)!!.status)
    assertTrue(ledgerService.checkConservationInvariant(initialCapital))
  }

  @Test
  fun `security center validates PIN policy, rotates hashes without secret logs, and isolates DURESS`() = runTest {
    repository.authenticatePin("1234")

    // Тривиальный или слишком близкий ПИН отклоняется
    val trivial = repository.updateLoginPins("1234", "0000", "4826")
    assertTrue(trivial is SecurityUpdateResult.Rejected)

    val closeHamming = repository.updateLoginPins("1234", "7391", "7395")
    assertTrue(closeHamming is SecurityUpdateResult.Rejected)

    // Валидная смена с Хэммингом >= 2
    val updated = repository.updateLoginPins("1234", "7391", "4826")
    assertTrue(updated is SecurityUpdateResult.Success)

    // Политика обновляется и фиксируется в журнале аудита без утечки секретов
    val policyOk = repository.updateSecurityPolicy(
      thresholdTenge = 250_000L,
      isolatedSafeTargetTenge = 50_000L,
      coolingOffMinutes = 45,
      escrowBusinessDays = 7,
      primaryTrustedName = "Айгуль Сапарова",
      primaryTrustedPhone = "+7 701 555 12 34",
      backupTrustedName = "Тимур Есенов",
      backupTrustedPhone = "+7 707 888 45 67"
    )
    assertTrue(policyOk is SecurityUpdateResult.Success)

    repository.observeAuditLogs().test {
      val logs = awaitItem()
      assertTrue(logs.any { it.action == "LOGIN_PINS_ROTATED" })
      assertTrue(logs.any { it.action == "SECURITY_POLICY_UPDATED" })
      assertFalse(logs.any { it.details.contains("7391") || it.details.contains("4826") })
      cancelAndIgnoreRemainingEvents()
    }

    // Вход под новым тревожным ПИН 4826 изолирует аудит и заявки Второй руки (Zero-Tell)
    repository.lockSession()
    assertEquals(PinAuthResult.Success(SessionMode.DURESS), repository.authenticatePin("4826"))

    repository.observeAuditLogs().test {
      assertTrue(awaitItem().isEmpty())
      cancelAndIgnoreRemainingEvents()
    }
    repository.observeSecondHandRequests().test {
      assertTrue(awaitItem().isEmpty())
      cancelAndIgnoreRemainingEvents()
    }
  }
}
