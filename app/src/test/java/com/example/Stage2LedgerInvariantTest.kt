package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.crypto.PinHasher
import com.example.data.ledger.LedgerService
import com.example.data.local.AppDatabase
import com.example.data.seed.DemoSeeder
import com.qalqan.domain.clock.SimulatedClock
import com.qalqan.domain.ledger.LedgerRules
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class Stage2LedgerInvariantTest {

  private lateinit var db: AppDatabase
  private lateinit var clock: SimulatedClock
  private lateinit var seeder: DemoSeeder
  private lateinit var ledgerService: LedgerService

  private val initialCapital = 10_663_700L

  @Before
  fun setUp() = runTest {
    val context = ApplicationProvider.getApplicationContext<Context>()
    db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
      .allowMainThreadQueries()
      .build()
    clock = SimulatedClock(initialMillis = 1_760_000_000_000L)
    seeder = DemoSeeder(db, PinHasher(), clock)
    seeder.ensureSeeded()
    ledgerService = LedgerService(db, clock)
  }

  @After
  fun tearDown() {
    db.close()
  }

  @Test
  fun `escrow hold, release, deposit closure and refund preserve total conservation invariant`() = runTest {
    assertTrue(ledgerService.checkConservationInvariant(initialCapital))

    // 1. Заморозка в Soft-Escrow с основного счёта
    ledgerService.postTransferMovement(
      fromNodeId = DemoSeeder.ACC_MAIN,
      toNodeId = DemoSeeder.ACC_ESCROW_HOLD,
      amountTenge = 350_000L,
      description = "Soft-Escrow удержание"
    )
    assertEquals(495_200L, db.accountDao().getById(DemoSeeder.ACC_MAIN)!!.balanceTenge)
    assertEquals(350_000L, db.accountDao().getById(DemoSeeder.ACC_ESCROW_HOLD)!!.balanceTenge)
    assertTrue(ledgerService.checkConservationInvariant(initialCapital))

    // 2. Временное удержание депозита под замок «Второй руки»
    ledgerService.postTransferMovement(
      fromNodeId = DemoSeeder.DEP_RESERVE,
      toNodeId = DemoSeeder.ACC_ESCROW_HOLD,
      amountTenge = 500_000L,
      description = "Заморозка при досрочном закрытии депозита"
    )
    assertEquals(700_000L, db.depositDao().getById(DemoSeeder.DEP_RESERVE)!!.balanceTenge)
    assertEquals(850_000L, db.accountDao().getById(DemoSeeder.ACC_ESCROW_HOLD)!!.balanceTenge)
    assertTrue(ledgerService.checkConservationInvariant(initialCapital))

    // 3. Возврат депозитных средств после отклонения «Второй рукой»
    ledgerService.postTransferMovement(
      fromNodeId = DemoSeeder.ACC_ESCROW_HOLD,
      toNodeId = DemoSeeder.DEP_RESERVE,
      amountTenge = 500_000L,
      description = "Возврат под Money Lock"
    )
    assertEquals(1_200_000L, db.depositDao().getById(DemoSeeder.DEP_RESERVE)!!.balanceTenge)
    assertTrue(ledgerService.checkConservationInvariant(initialCapital))

    // 4. Завершение клиринга эскроу во внешний расчёт
    ledgerService.postTransferMovement(
      fromNodeId = DemoSeeder.ACC_ESCROW_HOLD,
      toNodeId = LedgerRules.EXTERNAL_SETTLEMENT_ID,
      amountTenge = 350_000L,
      description = "Внешний клиринг после проверки"
    )
    assertEquals(0L, db.accountDao().getById(DemoSeeder.ACC_ESCROW_HOLD)!!.balanceTenge)
    assertTrue(ledgerService.checkConservationInvariant(initialCapital))
  }

  @Test
  fun `mid-transaction crash rolls back debit and preserves invariant`() = runTest {
    val balanceBefore = db.accountDao().getById(DemoSeeder.ACC_MAIN)!!.balanceTenge

    try {
      ledgerService.postTransferMovement(
        fromNodeId = DemoSeeder.ACC_MAIN,
        toNodeId = DemoSeeder.ACC_ESCROW_HOLD,
        amountTenge = 200_000L,
        description = "Сбой посреди транзакции",
        beforeCreditHook = { throw IllegalStateException("Симуляция сбоя процесса") }
      )
      fail("Ожидалось исключение при сбое посреди транзакции")
    } catch (_: IllegalStateException) {
      // Ожидаемый сбой
    }

    assertEquals(balanceBefore, db.accountDao().getById(DemoSeeder.ACC_MAIN)!!.balanceTenge)
    assertEquals(0L, db.accountDao().getById(DemoSeeder.ACC_ESCROW_HOLD)!!.balanceTenge)
    assertTrue(ledgerService.checkConservationInvariant(initialCapital))
  }
}
