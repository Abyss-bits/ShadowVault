package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.cash.turbine.test
import com.example.crypto.PinHasher
import com.example.data.ledger.LedgerService
import com.example.data.local.AppDatabase
import com.example.data.repository.QalqanRepository
import com.example.data.repository.TransferSubmitResult
import com.example.data.seed.DemoSeeder
import com.example.data.simulator.NbrkAntifraudApiSimulator
import com.example.data.simulator.NbrkTransmissionStage
import com.qalqan.domain.clock.BusinessCalendar
import com.qalqan.domain.clock.SimulatedClock
import com.qalqan.domain.model.AccountType
import com.qalqan.domain.model.NbrkTrigger
import com.qalqan.domain.model.SessionMode
import com.qalqan.domain.model.TransferStatus
import com.qalqan.domain.validation.KzIdentifierValidator
import java.time.Instant
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class Stage3TransfersAndNbrkTest {

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
    // Пятница 14:00 по Алматы (UTC+5 -> 09:00 UTC), дневное время
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
  fun `coercion code 9999 freezes funds in Soft-Escrow and emits NBRK incident with step progress`() = runTest {
    repository.authenticatePin("1234")
    val newIban = KzIdentifierValidator.buildValidKzIban("947", "0001112223334")

    repository.nbrkTransmissionStage.test {
      assertEquals(NbrkTransmissionStage.Idle, awaitItem())

      val result = repository.submitTransfer(
        recipientName = "Шантажист",
        recipientIban = newIban,
        amountTenge = 150_000L,
        transferCode = "9999"
      )

      assertTrue(result is TransferSubmitResult.Success)
      val success = result as TransferSubmitResult.Success
      assertEquals(TransferStatus.SOFT_ESCROW_HOLD, success.uiStatus)
      assertEquals(100, success.riskScore)

      assertEquals(NbrkTransmissionStage.CollectingIndicators, awaitItem())
      assertTrue(awaitItem() is NbrkTransmissionStage.SendingToApi)
      val verdict = awaitItem() as NbrkTransmissionStage.VerdictReceived
      assertTrue(verdict.latencyMs in 800L..1400L)
      assertEquals(success.incidentCode, verdict.incidentCode)

      cancelAndIgnoreRemainingEvents()
    }

    val escrow = db.accountDao().getById(DemoSeeder.ACC_ESCROW_HOLD)!!
    assertEquals(150_000L, escrow.balanceTenge)
    assertTrue(ledgerService.checkConservationInvariant(initialCapital))

    repository.observeNbrkIncidents().test {
      val list = awaitItem()
      assertEquals(1, list.size)
      assertEquals(NbrkTrigger.DURESS_TRANSFER_ESCROW, list.first().triggerType)
      assertTrue(list.first().requestJson.contains(newIban))
      cancelAndIgnoreRemainingEvents()
    }
  }

  @Test
  fun `large transfer to new recipient enters COOLING_OFF and settles after clock advance`() = runTest {
    repository.authenticatePin("1234")
    val newIban = KzIdentifierValidator.buildValidKzIban("947", "0008887776665")

    val result = repository.submitTransfer(
      recipientName = "Новый контрагент",
      recipientIban = newIban,
      amountTenge = 350_000L,
      transferCode = "5555"
    )
    assertTrue(result is TransferSubmitResult.Success)
    assertEquals(TransferStatus.COOLING_OFF, (result as TransferSubmitResult.Success).uiStatus)
    assertEquals(350_000L, db.accountDao().getById(DemoSeeder.ACC_ESCROW_HOLD)!!.balanceTenge)

    // Перемотка на 31 минуту -> Cooling-Off завершается
    clock.advanceByMinutes(31)
    val settledCount = repository.resolveMaturedCoolingOffTransfers()
    assertEquals(1, settledCount)
    assertEquals(0L, db.accountDao().getById(DemoSeeder.ACC_ESCROW_HOLD)!!.balanceTenge)
    assertEquals(TransferStatus.COMPLETED, db.transferDao().getById(result.transferId)!!.status)
    assertTrue(ledgerService.checkConservationInvariant(initialCapital))
  }

  @Test
  fun `large transfer to known trusted recipient prompts second confirmation then completes`() = runTest {
    repository.authenticatePin("1234")
    val trustedIban = KzIdentifierValidator.buildValidKzIban("947", "0002211334455")

    val first = repository.submitTransfer(
      recipientName = "Айгуль Сапарова (супруга)",
      recipientIban = trustedIban,
      amountTenge = 400_000L,
      transferCode = "5555",
      secondConfirmationProvided = false
    )
    assertTrue(first is TransferSubmitResult.NeedsSecondConfirmation)

    val confirmed = repository.submitTransfer(
      recipientName = "Айгуль Сапарова (супруга)",
      recipientIban = trustedIban,
      amountTenge = 400_000L,
      transferCode = "5555",
      secondConfirmationProvided = true
    )
    assertTrue(confirmed is TransferSubmitResult.Success)
    assertEquals(TransferStatus.COMPLETED, (confirmed as TransferSubmitResult.Success).uiStatus)
    assertTrue(ledgerService.checkConservationInvariant(initialCapital))
  }

  @Test
  fun `duress mode transfer within subaccount looks COMPLETED, holds in ESCROW_HOLD, rejects over-limit`() = runTest {
    repository.authenticatePin("9110")
    val newIban = KzIdentifierValidator.buildValidKzIban("947", "0004443332221")

    val overLimit = repository.submitTransfer(
      recipientName = "Грабитель",
      recipientIban = newIban,
      amountTenge = 60_000L,
      transferCode = "5555"
    )
    assertTrue(overLimit is TransferSubmitResult.Rejected)
    assertEquals("Недостаточно средств на счёте", (overLimit as TransferSubmitResult.Rejected).reason)

    val withinSafe = repository.submitTransfer(
      recipientName = "Перевод",
      recipientIban = newIban,
      amountTenge = 20_000L,
      transferCode = "5555"
    )
    assertTrue(withinSafe is TransferSubmitResult.Success)
    val ok = withinSafe as TransferSubmitResult.Success
    assertEquals(TransferStatus.COMPLETED, ok.uiStatus)
    assertEquals(null, ok.incidentCode) // В тревожном режиме клиенту ничего не раскрывается

    // Деньги физически удержаны в ESCROW_HOLD, а в UI субсчёта остаток уменьшен
    assertEquals(28_500L, db.accountDao().getById(DemoSeeder.ACC_ISOLATED_SAFE)!!.balanceTenge)
    assertEquals(20_000L, db.accountDao().getById(DemoSeeder.ACC_ESCROW_HOLD)!!.balanceTenge)
    assertTrue(ledgerService.checkConservationInvariant(initialCapital))

    // При переключении обратно в обычный режим виден тихий инцидент DURESS_TRANSFER
    repository.lockSession()
    repository.authenticatePin("1234")
    repository.observeNbrkIncidents().test {
      val incidents = awaitItem()
      assertTrue(incidents.any { it.triggerType == NbrkTrigger.DURESS_TRANSFER })
      assertTrue(incidents.any { it.triggerType == NbrkTrigger.DURESS_LOGIN_SILENT })
      cancelAndIgnoreRemainingEvents()
    }
  }
}
