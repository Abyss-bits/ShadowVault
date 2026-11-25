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
import com.example.data.seed.DemoSeeder
import com.example.data.simulator.NbrkAntifraudApiSimulator
import com.example.demo.DemoScenarioBridge
import com.example.presentation.DemoTabTarget
import com.qalqan.domain.clock.BusinessCalendar
import com.qalqan.domain.clock.SimulatedClock
import com.qalqan.domain.model.DepositStatus
import com.qalqan.domain.model.NbrkTrigger
import com.qalqan.domain.model.SessionMode
import com.qalqan.domain.model.TransferStatus
import com.qalqan.domain.model.UserRole
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
class Stage5DemoScenariosAndAuditTest {

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
  fun `scenario 1 robbery runs DURESS isolation silently and reveals NBRK incidents to inspector`() = runTest {
    val outcome = DemoScenarioBridge.runScenario1Robbery(repository)
    assertEquals(DemoTabTarget.HOME, outcome.targetTab)
    assertEquals(SessionMode.DURESS, repository.sessionMode.value)
    assertEquals(28_500L, db.accountDao().getById(DemoSeeder.ACC_ISOLATED_SAFE)!!.balanceTenge)
    assertEquals(20_000L, db.accountDao().getById(DemoSeeder.ACC_ESCROW_HOLD)!!.balanceTenge)
    assertTrue(ledgerService.checkConservationInvariant(initialCapital))

    repository.observeNbrkIncidents().test {
      assertTrue(awaitItem().isEmpty())
      cancelAndIgnoreRemainingEvents()
    }

    val inspectorTab = DemoScenarioBridge.switchRole(repository, UserRole.NBRK_INSPECTOR)
    assertEquals(DemoTabTarget.SECURITY_NBRK, inspectorTab)
    assertEquals(SessionMode.STANDARD, repository.sessionMode.value)
    assertEquals(UserRole.NBRK_INSPECTOR, repository.activeRole.value)

    repository.observeNbrkIncidents().test {
      val incidents = awaitItem()
      assertTrue(incidents.any { it.triggerType == NbrkTrigger.DURESS_LOGIN_SILENT })
      assertTrue(incidents.any { it.triggerType == NbrkTrigger.DURESS_TRANSFER })
      cancelAndIgnoreRemainingEvents()
    }
  }

  @Test
  fun `scenario 2 phone blackmail freezes in Soft-Escrow and returns funds after +5 business days`() = runTest {
    val outcome = DemoScenarioBridge.runScenario2PhoneBlackmail(repository)
    assertEquals(DemoTabTarget.SECURITY_NBRK, outcome.targetTab)
    assertEquals(UserRole.NBRK_INSPECTOR, repository.activeRole.value)
    assertEquals(350_000L, db.accountDao().getById(DemoSeeder.ACC_ESCROW_HOLD)!!.balanceTenge)
    assertTrue(ledgerService.checkConservationInvariant(initialCapital))

    val calDays = calendar.businessDaysToCalendarDays(clock.localDate(), 5)
    assertEquals(7, calDays) // Пт -> Пт следующей недели (7 календ. дней)
    clock.advanceByDays(calDays.toLong())

    val resolved = repository.resolveTimedActions()
    assertEquals(1, resolved)
    assertEquals(0L, db.accountDao().getById(DemoSeeder.ACC_ESCROW_HOLD)!!.balanceTenge)
    assertEquals(845_200L, db.accountDao().getById(DemoSeeder.ACC_MAIN)!!.balanceTenge)
    assertTrue(ledgerService.checkConservationInvariant(initialCapital))
  }

  @Test
  fun `scenario 3 deposit second hand completes after +15m time travel and PIN 2468`() = runTest {
    val outcome = DemoScenarioBridge.runScenario3DepositSecondHand(repository)
    assertEquals(DemoTabTarget.SECURITY_SECOND_HAND, outcome.targetTab)
    assertEquals(UserRole.SECOND_HAND, repository.activeRole.value)

    val pending = db.secondHandDao().getPending().first()
    clock.advanceByMinutes(15)
    repository.resolveTimedActions()

    val approved = repository.approveSecondHandRequest(pending.id, "2468")
    assertTrue(approved is SecondHandActionResult.Success)
    assertEquals(DepositStatus.CLOSED, db.depositDao().getById(DemoSeeder.DEP_RESERVE)!!.status)
    assertEquals(TransferStatus.COMPLETED, db.transferDao().getById(pending.transferId)!!.status)
    assertTrue(ledgerService.checkConservationInvariant(initialCapital))
  }

  @Test
  fun `role switcher transitions between standard, duress, second hand, and inspector`() = runTest {
    assertEquals(DemoTabTarget.HOME, DemoScenarioBridge.switchRole(repository, UserRole.CLIENT_DURESS))
    assertEquals(SessionMode.DURESS, repository.sessionMode.value)

    assertEquals(
      DemoTabTarget.SECURITY_SECOND_HAND,
      DemoScenarioBridge.switchRole(repository, UserRole.SECOND_HAND)
    )
    assertEquals(SessionMode.STANDARD, repository.sessionMode.value)
    assertEquals(UserRole.SECOND_HAND, repository.activeRole.value)

    assertEquals(DemoTabTarget.HOME, DemoScenarioBridge.switchRole(repository, UserRole.CLIENT_STANDARD))
    assertEquals(UserRole.CLIENT_STANDARD, repository.activeRole.value)
  }
}
