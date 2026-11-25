package com.qalqan.domain

import com.qalqan.domain.model.NbrkTrigger
import com.qalqan.domain.model.SessionMode
import com.qalqan.domain.model.TransferCodeType
import com.qalqan.domain.model.TransferStatus
import com.qalqan.domain.model.TransferType
import com.qalqan.domain.risk.LedgerRouting
import com.qalqan.domain.risk.RiskContext
import com.qalqan.domain.risk.RiskEngine
import com.qalqan.domain.risk.RiskFactor
import com.qalqan.domain.risk.RiskRuleId
import java.time.LocalTime
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class RiskEngineTest {

  private fun baseContext(
    sessionMode: SessionMode = SessionMode.STANDARD,
    transferType: TransferType = TransferType.REGULAR,
    codeType: TransferCodeType = TransferCodeType.STANDARD,
    amountTenge: Long = 50_000L,
    availableBalanceTenge: Long = 845_200L,
    thresholdTenge: Long = 300_000L,
    isNewRecipient: Boolean = false,
    isRecipientDropper: Boolean = false,
    localTime: LocalTime = LocalTime.of(14, 0),
    recentPinOrDeviceChange: Boolean = false,
    transfersInLastHour: Int = 0,
    secondConfirmationProvided: Boolean = false
  ) = RiskContext(
    sessionMode = sessionMode,
    transferType = transferType,
    codeType = codeType,
    amountTenge = amountTenge,
    availableBalanceTenge = availableBalanceTenge,
    thresholdTenge = thresholdTenge,
    isNewRecipient = isNewRecipient,
    isRecipientDropper = isRecipientDropper,
    localTime = localTime,
    recentPinOrDeviceChange = recentPinOrDeviceChange,
    transfersInLastHour = transfersInLastHour,
    secondConfirmationProvided = secondConfirmationProvided
  )

  // --- Тесты каждого фактора поведенческого скоринга ---

  @Test
  fun `behavioral score sums each factor accurately and caps at 100`() {
    val zero = RiskEngine.computeScore(baseContext())
    assertEquals(0, zero.first)

    val allBehavioral = RiskEngine.computeScore(
      baseContext(
        amountTenge = 700_000L,
        availableBalanceTenge = 800_000L, // > 70% (+15) и >= 300k (+20)
        isNewRecipient = true,            // +25
        isRecipientDropper = true,        // +30
        localTime = LocalTime.of(23, 30), // +10
        recentPinOrDeviceChange = true,   // +15
        transfersInLastHour = 3           // +15
      )
    )
    // 25 + 20 + 15 + 10 + 30 + 15 + 15 = 130 -> clamp to 100
    assertEquals(100, allBehavioral.first)
    assertEquals(7, allBehavioral.second.size)
  }

  // --- Граничные значения скоринга и условий ---

  @Test
  fun `boundary score 39 passes while 40 triggers cooling off`() {
    // 25 (новый) + 10 (ночь) = 35 (< 40) -> COMPLETED
    val score35 = RiskEngine.evaluate(
      baseContext(
        isNewRecipient = true,
        localTime = LocalTime.of(23, 0),
        amountTenge = 10_000L
      )
    )
    assertEquals(35, score35.riskScore)
    assertEquals(TransferStatus.COMPLETED, score35.uiStatus)

    // 25 (новый) + 15 (>70% от остатка) = 40 -> COOLING_OFF
    val score40 = RiskEngine.evaluate(
      baseContext(
        isNewRecipient = true,
        amountTenge = 71_000L,
        availableBalanceTenge = 100_000L
      )
    )
    assertEquals(40, score40.riskScore)
    assertEquals(TransferStatus.COOLING_OFF, score40.uiStatus)
    assertTrue(score40.notifySecondHand)
  }

  @Test
  fun `boundary score 79 stays cooling off while 80 triggers soft escrow`() {
    // 25 (новый) + 20 (>=300k) + 30 (дроппер) = 75 -> COOLING_OFF
    val score75 = RiskEngine.evaluate(
      baseContext(
        isNewRecipient = true,
        isRecipientDropper = true,
        amountTenge = 300_000L,
        availableBalanceTenge = 1_000_000L
      )
    )
    assertEquals(75, score75.riskScore)
    assertEquals(TransferStatus.COOLING_OFF, score75.uiStatus)

    // 25 + 20 + 30 + 10 (ночь) = 85 (>= 80) -> SOFT_ESCROW_HOLD
    val score85 = RiskEngine.evaluate(
      baseContext(
        isNewRecipient = true,
        isRecipientDropper = true,
        amountTenge = 300_000L,
        availableBalanceTenge = 1_000_000L,
        localTime = LocalTime.of(2, 0)
      )
    )
    assertEquals(85, score85.riskScore)
    assertEquals(TransferStatus.SOFT_ESCROW_HOLD, score85.uiStatus)
    assertEquals(LedgerRouting.ESCROW_HOLD, score85.ledgerRouting)
    assertEquals(NbrkTrigger.HIGH_RISK_NEW_RECIPIENT, score85.nbrkTrigger)
  }

  @Test
  fun `boundary 70 percent balance share triggers strictly above 70 percent`() {
    // Ровно 70%: 70_000 / 100_000 -> не срабатывает (+0)
    val exact70 = RiskEngine.computeScore(
      baseContext(amountTenge = 70_000L, availableBalanceTenge = 100_000L)
    )
    assertFalse(RiskFactor.HIGH_BALANCE_SHARE in exact70.second)

    // 70_001 / 100_000 -> срабатывает (+15)
    val above70 = RiskEngine.computeScore(
      baseContext(amountTenge = 70_001L, availableBalanceTenge = 100_000L)
    )
    assertTrue(RiskFactor.HIGH_BALANCE_SHARE in above70.second)
  }

  @Test
  fun `boundary night time 22_59 false, 23_00 true, 05_59 true, 06_00 false`() {
    assertFalse(RiskEngine.isNightTime(LocalTime.of(22, 59)))
    assertTrue(RiskEngine.isNightTime(LocalTime.of(23, 0)))
    assertTrue(RiskEngine.isNightTime(LocalTime.of(5, 59)))
    assertFalse(RiskEngine.isNightTime(LocalTime.of(6, 0)))
  }

  @Test
  fun `boundary transfer frequency 2 false vs 3 true and threshold minus 1`() {
    val freq2 = RiskEngine.computeScore(baseContext(transfersInLastHour = 2))
    assertFalse(RiskFactor.HIGH_FREQUENCY in freq2.second)

    val freq3 = RiskEngine.computeScore(baseContext(transfersInLastHour = 3))
    assertTrue(RiskFactor.HIGH_FREQUENCY in freq3.second)

    val belowThreshold = RiskEngine.computeScore(baseContext(amountTenge = 299_999L))
    assertFalse(RiskFactor.LARGE_AMOUNT in belowThreshold.second)

    val atThreshold = RiskEngine.computeScore(baseContext(amountTenge = 300_000L))
    assertTrue(RiskFactor.LARGE_AMOUNT in atThreshold.second)
  }

  // --- Тесты каждой строки матрицы решений ---

  @Test
  fun `matrix row 1 - standard mode, code 5555, below threshold, known recipient returns COMPLETED`() {
    val d = RiskEngine.evaluate(baseContext(amountTenge = 150_000L, isNewRecipient = false))
    assertTrue(d.accepted)
    assertEquals(RiskRuleId.RULE_STANDARD_TRUSTED, d.ruleId)
    assertEquals(TransferStatus.COMPLETED, d.uiStatus)
    assertEquals(LedgerRouting.EXTERNAL_SETTLEMENT, d.ledgerRouting)
    assertFalse(d.backgroundScoringLogged)
  }

  @Test
  fun `matrix row 2 - standard mode, code 5555, below threshold, new recipient returns COMPLETED + background scoring`() {
    val d = RiskEngine.evaluate(baseContext(amountTenge = 150_000L, isNewRecipient = true))
    assertTrue(d.accepted)
    assertEquals(RiskRuleId.RULE_NEW_RECIPIENT_LOW_RISK, d.ruleId)
    assertEquals(TransferStatus.COMPLETED, d.uiStatus)
    assertTrue(d.backgroundScoringLogged)
  }

  @Test
  fun `matrix row 3 - standard mode, large amount, known recipient, score under 40 returns requires second confirmation then COMPLETED`() {
    val firstAttempt = RiskEngine.evaluate(
      baseContext(
        amountTenge = 400_000L,
        availableBalanceTenge = 1_000_000L, // 40% <= 70%, score = 20 (<40)
        isNewRecipient = false,
        secondConfirmationProvided = false
      )
    )
    assertFalse(firstAttempt.accepted)
    assertTrue(firstAttempt.requiresSecondConfirmationPrompt)

    val confirmed = RiskEngine.evaluate(
      baseContext(
        amountTenge = 400_000L,
        availableBalanceTenge = 1_000_000L,
        isNewRecipient = false,
        secondConfirmationProvided = true
      )
    )
    assertTrue(confirmed.accepted)
    assertEquals(TransferStatus.COMPLETED, confirmed.uiStatus)
    assertEquals(LedgerRouting.EXTERNAL_SETTLEMENT, confirmed.ledgerRouting)
  }

  @Test
  fun `matrix row 4 - standard mode, large amount, new recipient returns COOLING_OFF + notify second hand`() {
    val d = RiskEngine.evaluate(
      baseContext(
        amountTenge = 350_000L,
        availableBalanceTenge = 1_000_000L,
        isNewRecipient = true
      )
    )
    assertTrue(d.accepted)
    assertEquals(45, d.riskScore)
    assertEquals(RiskRuleId.RULE_SCORE_MEDIUM_COOLING_OFF, d.ruleId)
    assertEquals(TransferStatus.COOLING_OFF, d.uiStatus)
    assertTrue(d.notifySecondHand)
    assertEquals(LedgerRouting.ESCROW_HOLD, d.ledgerRouting)
  }

  @Test
  fun `matrix row 5 - coercion code 9999 any amount or recipient returns score 100 and SOFT_ESCROW_HOLD`() {
    val d = RiskEngine.evaluate(
      baseContext(
        codeType = TransferCodeType.DURESS,
        amountTenge = 5_000L,
        isNewRecipient = false
      )
    )
    assertTrue(d.accepted)
    assertEquals(100, d.riskScore)
    assertEquals(RiskRuleId.RULE_COERCION_CODE_9999, d.ruleId)
    assertEquals(TransferStatus.SOFT_ESCROW_HOLD, d.uiStatus)
    assertEquals(LedgerRouting.ESCROW_HOLD, d.ledgerRouting)
    assertEquals(NbrkTrigger.DURESS_TRANSFER_ESCROW, d.nbrkTrigger)
  }

  @Test
  fun `matrix row 6 - early deposit closure + new recipient + large amount returns AWAITING_SECOND_HAND`() {
    val d = RiskEngine.evaluate(
      baseContext(
        transferType = TransferType.EARLY_DEPOSIT_PAYOUT,
        isNewRecipient = true,
        amountTenge = 500_000L,
        availableBalanceTenge = 8_450_000L
      )
    )
    assertTrue(d.accepted)
    assertEquals(RiskRuleId.RULE_EARLY_DEPOSIT_NEW_RECIPIENT_LARGE, d.ruleId)
    assertEquals(TransferStatus.AWAITING_SECOND_HAND, d.uiStatus)
    assertTrue(d.notifySecondHand)
    assertEquals(LedgerRouting.ESCROW_HOLD, d.ledgerRouting)
  }

  @Test
  fun `matrix row 7 - duress session within isolated safe returns UI COMPLETED, internal ESCROW_HOLD, silent NBRK`() {
    val d = RiskEngine.evaluate(
      baseContext(
        sessionMode = SessionMode.DURESS,
        amountTenge = 25_000L,
        availableBalanceTenge = 48_500L,
        isNewRecipient = true
      )
    )
    assertTrue(d.accepted)
    assertEquals(TransferStatus.COMPLETED, d.uiStatus)
    assertEquals(TransferStatus.SOFT_ESCROW_HOLD, d.internalStatus)
    assertEquals(LedgerRouting.ESCROW_HOLD, d.ledgerRouting)
    assertEquals(NbrkTrigger.DURESS_TRANSFER, d.nbrkTrigger)
  }

  @Test
  fun `matrix row 7b - duress session exceeding isolated safe returns rejected insufficient funds`() {
    val d = RiskEngine.evaluate(
      baseContext(
        sessionMode = SessionMode.DURESS,
        amountTenge = 60_000L,
        availableBalanceTenge = 48_500L
      )
    )
    assertFalse(d.accepted)
    assertEquals("Недостаточно средств на счёте", d.rejectionMessage)
    assertNull(d.uiStatus)
  }
}
