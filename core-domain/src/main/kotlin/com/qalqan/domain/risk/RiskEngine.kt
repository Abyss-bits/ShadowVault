package com.qalqan.domain.risk

import com.qalqan.domain.model.NbrkTrigger
import com.qalqan.domain.model.SessionMode
import com.qalqan.domain.model.TransferCodeType
import com.qalqan.domain.model.TransferStatus
import com.qalqan.domain.model.TransferType
import java.time.LocalTime

enum class RiskFactor(val labelRu: String, val points: Int) {
  NEW_RECIPIENT("Новый получатель", 25),
  LARGE_AMOUNT("Сумма ≥ порога безопасности", 20),
  HIGH_BALANCE_SHARE("Сумма > 70% доступного остатка", 15),
  NIGHT_TIME("Ночное время (23:00–06:00)", 10),
  DROPPER_LIST("Получатель в списке дропперов", 30),
  RECENT_SECURITY_CHANGE("Недавняя смена ПИН или устройства", 15),
  HIGH_FREQUENCY("3 и более переводов за последний час", 15),
  COERCION_CODE("Введён тревожный код шантажа", 100)
}

enum class RiskRuleId {
  RULE_INSUFFICIENT_FUNDS,
  RULE_DURESS_SESSION_TRANSFER,
  RULE_COERCION_CODE_9999,
  RULE_SCORE_CRITICAL_80_PLUS,
  RULE_EARLY_DEPOSIT_NEW_RECIPIENT_LARGE,
  RULE_SCORE_MEDIUM_COOLING_OFF,
  RULE_LARGE_KNOWN_RECIPIENT_CONFIRMATION,
  RULE_NEW_RECIPIENT_LOW_RISK,
  RULE_STANDARD_TRUSTED
}

enum class LedgerRouting {
  NONE,
  EXTERNAL_SETTLEMENT,
  ESCROW_HOLD
}

data class RiskContext(
  val sessionMode: SessionMode,
  val transferType: TransferType,
  val codeType: TransferCodeType,
  val amountTenge: Long,
  val availableBalanceTenge: Long,
  val thresholdTenge: Long = 300_000L,
  val isNewRecipient: Boolean,
  val isRecipientDropper: Boolean,
  val localTime: LocalTime,
  val recentPinOrDeviceChange: Boolean,
  val transfersInLastHour: Int,
  val secondConfirmationProvided: Boolean = false
)

data class RiskDecision(
  val ruleId: RiskRuleId,
  val accepted: Boolean,
  val rejectionMessage: String?,
  val uiStatus: TransferStatus?,
  val internalStatus: TransferStatus?,
  val ledgerRouting: LedgerRouting,
  val riskScore: Int,
  val triggeredFactors: List<RiskFactor>,
  val requiresSecondConfirmationPrompt: Boolean = false,
  val notifySecondHand: Boolean = false,
  val nbrkTrigger: NbrkTrigger? = null,
  val backgroundScoringLogged: Boolean = false
)

object RiskEngine {

  private val NIGHT_START: LocalTime = LocalTime.of(23, 0)
  private val NIGHT_END: LocalTime = LocalTime.of(6, 0)

  fun evaluate(context: RiskContext): RiskDecision {
    val (score, factors) = computeScore(context)

    // 1. Проверка достаточности средств (одинакова для обычного и тревожного режимов)
    if (context.amountTenge <= 0L || context.amountTenge > context.availableBalanceTenge) {
      return RiskDecision(
        ruleId = RiskRuleId.RULE_INSUFFICIENT_FUNDS,
        accepted = false,
        rejectionMessage = if (context.amountTenge <= 0L) {
          "Введите корректную сумму перевода"
        } else {
          "Недостаточно средств на счёте"
        },
        uiStatus = null,
        internalStatus = null,
        ledgerRouting = LedgerRouting.NONE,
        riskScore = score,
        triggeredFactors = factors
      )
    }

    // 2. Тревожный режим (Zero-Tell): для UI выглядит как COMPLETED, в Ledger уходит в ESCROW_HOLD
    if (context.sessionMode == SessionMode.DURESS) {
      val duressTrigger = if (context.codeType == TransferCodeType.DURESS || score >= 80) {
        NbrkTrigger.DURESS_TRANSFER_ESCROW
      } else {
        NbrkTrigger.DURESS_TRANSFER
      }
      return RiskDecision(
        ruleId = RiskRuleId.RULE_DURESS_SESSION_TRANSFER,
        accepted = true,
        rejectionMessage = null,
        uiStatus = TransferStatus.COMPLETED,
        internalStatus = TransferStatus.SOFT_ESCROW_HOLD,
        ledgerRouting = LedgerRouting.ESCROW_HOLD,
        riskScore = score,
        triggeredFactors = factors,
        nbrkTrigger = duressTrigger,
        backgroundScoringLogged = true
      )
    }

    // 3. Код шантажа (9999) в обычном режиме -> принудительно скоринг 100 и Soft-Escrow
    if (context.codeType == TransferCodeType.DURESS) {
      return RiskDecision(
        ruleId = RiskRuleId.RULE_COERCION_CODE_9999,
        accepted = true,
        rejectionMessage = null,
        uiStatus = TransferStatus.SOFT_ESCROW_HOLD,
        internalStatus = TransferStatus.SOFT_ESCROW_HOLD,
        ledgerRouting = LedgerRouting.ESCROW_HOLD,
        riskScore = 100,
        triggeredFactors = factors,
        nbrkTrigger = NbrkTrigger.DURESS_TRANSFER_ESCROW
      )
    }

    // 4. Любой сценарий с критическим риском >= 80 -> Soft-Escrow + НБРК
    if (score >= 80) {
      return RiskDecision(
        ruleId = RiskRuleId.RULE_SCORE_CRITICAL_80_PLUS,
        accepted = true,
        rejectionMessage = null,
        uiStatus = TransferStatus.SOFT_ESCROW_HOLD,
        internalStatus = TransferStatus.SOFT_ESCROW_HOLD,
        ledgerRouting = LedgerRouting.ESCROW_HOLD,
        riskScore = score,
        triggeredFactors = factors,
        nbrkTrigger = NbrkTrigger.HIGH_RISK_NEW_RECIPIENT
      )
    }

    // 5. Досрочное закрытие депозита + новый получатель + сумма >= порога -> «Вторая рука»
    if (
      context.transferType == TransferType.EARLY_DEPOSIT_PAYOUT &&
      context.isNewRecipient &&
      context.amountTenge >= context.thresholdTenge
    ) {
      return RiskDecision(
        ruleId = RiskRuleId.RULE_EARLY_DEPOSIT_NEW_RECIPIENT_LARGE,
        accepted = true,
        rejectionMessage = null,
        uiStatus = TransferStatus.AWAITING_SECOND_HAND,
        internalStatus = TransferStatus.AWAITING_SECOND_HAND,
        ledgerRouting = LedgerRouting.ESCROW_HOLD,
        riskScore = score,
        triggeredFactors = factors,
        notifySecondHand = true
      )
    }

    // 6. Средний риск (40..79) -> Cooling-Off (по умолчанию 30 мин) + уведомление «второй руке»
    if (score in 40..79) {
      return RiskDecision(
        ruleId = RiskRuleId.RULE_SCORE_MEDIUM_COOLING_OFF,
        accepted = true,
        rejectionMessage = null,
        uiStatus = TransferStatus.COOLING_OFF,
        internalStatus = TransferStatus.COOLING_OFF,
        ledgerRouting = LedgerRouting.ESCROW_HOLD,
        riskScore = score,
        triggeredFactors = factors,
        notifySecondHand = true
      )
    }

    // 7. Сумма >= порога + известный получатель + риск < 40 -> COMPLETED после повторного ввода кода
    if (context.amountTenge >= context.thresholdTenge && !context.isNewRecipient) {
      if (!context.secondConfirmationProvided) {
        return RiskDecision(
          ruleId = RiskRuleId.RULE_LARGE_KNOWN_RECIPIENT_CONFIRMATION,
          accepted = false,
          rejectionMessage = "Для крупного перевода подтвердите операцию повторным вводом кода",
          uiStatus = null,
          internalStatus = null,
          ledgerRouting = LedgerRouting.NONE,
          riskScore = score,
          triggeredFactors = factors,
          requiresSecondConfirmationPrompt = true
        )
      }
      return RiskDecision(
        ruleId = RiskRuleId.RULE_LARGE_KNOWN_RECIPIENT_CONFIRMATION,
        accepted = true,
        rejectionMessage = null,
        uiStatus = TransferStatus.COMPLETED,
        internalStatus = TransferStatus.COMPLETED,
        ledgerRouting = LedgerRouting.EXTERNAL_SETTLEMENT,
        riskScore = score,
        triggeredFactors = factors
      )
    }

    // 8. Обычный перевод, сумма < порога, новый получатель, риск < 40 -> COMPLETED + фоновый скоринг
    if (context.isNewRecipient) {
      return RiskDecision(
        ruleId = RiskRuleId.RULE_NEW_RECIPIENT_LOW_RISK,
        accepted = true,
        rejectionMessage = null,
        uiStatus = TransferStatus.COMPLETED,
        internalStatus = TransferStatus.COMPLETED,
        ledgerRouting = LedgerRouting.EXTERNAL_SETTLEMENT,
        riskScore = score,
        triggeredFactors = factors,
        backgroundScoringLogged = true
      )
    }

    // 9. Обычный перевод, сумма < порога, известный получатель -> COMPLETED
    return RiskDecision(
      ruleId = RiskRuleId.RULE_STANDARD_TRUSTED,
      accepted = true,
      rejectionMessage = null,
      uiStatus = TransferStatus.COMPLETED,
      internalStatus = TransferStatus.COMPLETED,
      ledgerRouting = LedgerRouting.EXTERNAL_SETTLEMENT,
      riskScore = score,
      triggeredFactors = factors
    )
  }

  fun computeScore(context: RiskContext): Pair<Int, List<RiskFactor>> {
    val factors = mutableListOf<RiskFactor>()
    var rawScore = 0

    if (context.isNewRecipient) {
      factors += RiskFactor.NEW_RECIPIENT
      rawScore += RiskFactor.NEW_RECIPIENT.points
    }
    if (context.amountTenge >= context.thresholdTenge) {
      factors += RiskFactor.LARGE_AMOUNT
      rawScore += RiskFactor.LARGE_AMOUNT.points
    }
    if (context.availableBalanceTenge > 0L && context.amountTenge * 100L > context.availableBalanceTenge * 70L) {
      factors += RiskFactor.HIGH_BALANCE_SHARE
      rawScore += RiskFactor.HIGH_BALANCE_SHARE.points
    }
    if (isNightTime(context.localTime)) {
      factors += RiskFactor.NIGHT_TIME
      rawScore += RiskFactor.NIGHT_TIME.points
    }
    if (context.isRecipientDropper) {
      factors += RiskFactor.DROPPER_LIST
      rawScore += RiskFactor.DROPPER_LIST.points
    }
    if (context.recentPinOrDeviceChange) {
      factors += RiskFactor.RECENT_SECURITY_CHANGE
      rawScore += RiskFactor.RECENT_SECURITY_CHANGE.points
    }
    if (context.transfersInLastHour >= 3) {
      factors += RiskFactor.HIGH_FREQUENCY
      rawScore += RiskFactor.HIGH_FREQUENCY.points
    }

    if (context.codeType == TransferCodeType.DURESS) {
      factors += RiskFactor.COERCION_CODE
      return 100 to factors
    }

    return rawScore.coerceIn(0, 100) to factors
  }

  fun isNightTime(time: LocalTime): Boolean =
    !time.isBefore(NIGHT_START) || time.isBefore(NIGHT_END)
}
