package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.cash.turbine.test
import com.example.crypto.PinHasher
import com.example.data.ledger.LedgerService
import com.example.data.local.AppDatabase
import com.example.data.repository.QalqanRepository
import com.example.data.seed.DemoSeeder
import com.example.data.simulator.NbrkAntifraudApiSimulator
import com.qalqan.domain.clock.BusinessCalendar
import com.qalqan.domain.clock.SimulatedClock
import com.qalqan.domain.model.AccountType
import com.qalqan.domain.model.NbrkTrigger
import com.qalqan.domain.model.PinAuthResult
import com.qalqan.domain.model.SessionMode
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class Stage1RepositoryAndSessionIsolationTest {

  private lateinit var db: AppDatabase
  private lateinit var clock: SimulatedClock
  private lateinit var pinHasher: PinHasher
  private lateinit var seeder: DemoSeeder
  private lateinit var repository: QalqanRepository

  @Before
  fun setUp() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
      .allowMainThreadQueries()
      .build()
    clock = SimulatedClock(initialMillis = 1_760_000_000_000L)
    pinHasher = PinHasher()
    seeder = DemoSeeder(db, pinHasher, clock)
    val ledgerService = LedgerService(db, clock)
    val nbrkSimulator = NbrkAntifraudApiSimulator(db, clock)
    repository = QalqanRepository(db, pinHasher, clock, BusinessCalendar(), seeder, ledgerService, nbrkSimulator)
  }

  @After
  fun tearDown() {
    db.close()
  }

  @Test
  fun `standard pin unlocks full portfolio and hashes secrets`() = runTest {
    repository.initializeDemoData()
    val storedConfig = db.securityConfigDao().getConfig()!!
    assertFalse(storedConfig.standardPinHash.contains("1234"))
    assertFalse(storedConfig.duressPinHash.contains("9110"))
    assertTrue(pinHasher.verifySecret("1234", storedConfig.standardPinHash))
    assertTrue(pinHasher.verifySecret("9110", storedConfig.duressPinHash))

    val auth = repository.authenticatePin("1234")
    assertEquals(PinAuthResult.Success(SessionMode.STANDARD), auth)

    repository.observeVisibleAccounts().test {
      val accounts = awaitItem()
      assertTrue(accounts.any { it.type == AccountType.MAIN && it.balanceTenge == 845_200L })
      assertTrue(accounts.any { it.type == AccountType.ISOLATED_SAFE && it.balanceTenge == 48_500L })
      cancelAndIgnoreRemainingEvents()
    }

    repository.observeVisibleDeposits().test {
      val deposits = awaitItem()
      assertEquals(2, deposits.size)
      assertEquals(9_650_000L, deposits.sumOf { it.balanceTenge })
      assertTrue(deposits.none { it.isDecoy })
      cancelAndIgnoreRemainingEvents()
    }
  }

  @Test
  fun `duress pin strictly isolates repository flows and logs silent nbrk incident`() = runTest {
    val auth = repository.authenticatePin("9110")
    assertEquals(PinAuthResult.Success(SessionMode.DURESS), auth)

    repository.observeVisibleAccounts().test {
      val accounts = awaitItem()
      assertEquals(1, accounts.size)
      assertEquals(AccountType.ISOLATED_SAFE, accounts.first().type)
      assertEquals(48_500L, accounts.first().balanceTenge)
      cancelAndIgnoreRemainingEvents()
    }

    repository.observeVisibleDeposits().test {
      val deposits = awaitItem()
      assertEquals(1, deposits.size)
      assertTrue(deposits.first().isDecoy)
      assertEquals(120_000L, deposits.first().balanceTenge)
      cancelAndIgnoreRemainingEvents()
    }

    repository.observeVisibleTransfers().test {
      val transfers = awaitItem()
      assertEquals(24, transfers.size)
      assertTrue(transfers.all { it.sourceAccountId == DemoSeeder.ACC_ISOLATED_SAFE })
      cancelAndIgnoreRemainingEvents()
    }

    repository.observeSecuritySummary().test {
      assertNull(awaitItem())
      cancelAndIgnoreRemainingEvents()
    }

    repository.observeNbrkIncidents().test {
      assertTrue(awaitItem().isEmpty())
      cancelAndIgnoreRemainingEvents()
    }

    // Возврат под обычным ПИН должен показать тихо зафиксированный инцидент DURESS_LOGIN_SILENT
    repository.lockSession()
    assertEquals(PinAuthResult.Success(SessionMode.STANDARD), repository.authenticatePin("1234"))

    repository.observeNbrkIncidents().test {
      val incidents = awaitItem()
      assertEquals(1, incidents.size)
      assertEquals(NbrkTrigger.DURESS_LOGIN_SILENT, incidents.first().triggerType)
      cancelAndIgnoreRemainingEvents()
    }
  }

  @Test
  fun `five wrong pins trigger escalating lockout`() = runTest {
    repeat(4) { attempt ->
      val res = repository.authenticatePin("000$attempt")
      assertTrue(res is PinAuthResult.Invalid)
    }

    val fifth = repository.authenticatePin("0009")
    assertTrue(fifth is PinAuthResult.LockedOut)

    // Даже верный ПИН отклоняется во время блокировки
    val blocked = repository.authenticatePin("1234")
    assertTrue(blocked is PinAuthResult.LockedOut)

    // После перемотки часов на 1 минуту блокировка снимается
    clock.advanceByMinutes(1)
    val unlocked = repository.authenticatePin("1234")
    assertEquals(PinAuthResult.Success(SessionMode.STANDARD), unlocked)
  }
}
