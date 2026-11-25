package com.example.data.simulator

import com.example.data.local.AppDatabase
import com.example.data.local.NbrkIncidentEntity
import com.qalqan.domain.clock.AppClock
import com.qalqan.domain.model.NbrkTrigger
import com.qalqan.domain.risk.RiskFactor
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.random.Random
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
data class NbrkFactorScoreDto(
  val code: String,
  val label: String,
  val points: Int
)

@Serializable
data class NbrkAntifraudRequestDto(
  val incidentCode: String,
  val triggerType: String,
  val recipientIban: String,
  val recipientName: String,
  val isNewRecipient: Boolean,
  val isDropperMatch: Boolean,
  val socialEngineeringMarker: Boolean,
  val amountTenge: Long,
  val timestampMillis: Long,
  val riskScore: Int,
  val riskFactors: List<NbrkFactorScoreDto>
)

@Serializable
data class NbrkAntifraudResponseDto(
  val incidentCode: String,
  val httpCode: Int,
  val status: String,
  val latencyMs: Long,
  val recommendedHoldBusinessDays: Int,
  val message: String
)

sealed interface NbrkTransmissionStage {
  data object Idle : NbrkTransmissionStage
  data object CollectingIndicators : NbrkTransmissionStage
  data class SendingToApi(val incidentCode: String) : NbrkTransmissionStage
  data class VerdictReceived(
    val incidentCode: String,
    val httpCode: Int,
    val latencyMs: Long,
    val statusLabel: String
  ) : NbrkTransmissionStage
}

@Singleton
class NbrkAntifraudApiSimulator @Inject constructor(
  private val db: AppDatabase,
  private val clock: AppClock
) {
  private val json = Json { prettyPrint = true }
  private val sequenceCounter = AtomicInteger(100)

  private val _transmissionStage = MutableStateFlow<NbrkTransmissionStage>(NbrkTransmissionStage.Idle)
  val transmissionStage: StateFlow<NbrkTransmissionStage> = _transmissionStage.asStateFlow()

  fun resetTransmissionStage() {
    _transmissionStage.value = NbrkTransmissionStage.Idle
  }

  suspend fun submitIncident(
    trigger: NbrkTrigger,
    recipientIban: String,
    recipientName: String,
    isNewRecipient: Boolean,
    isDropperMatch: Boolean,
    socialEngineeringMarker: Boolean,
    amountTenge: Long,
    riskScore: Int,
    factors: List<RiskFactor>,
    seed: Long = 42L,
    emitUiProgress: Boolean = true
  ): NbrkIncidentEntity {
    val now = clock.nowMillis()
    val seq = sequenceCounter.incrementAndGet()
    val rng = Random(seed + seq * 31L + amountTenge)
    val latencyMs = rng.nextLong(800L, 1401L)
    val incidentCode = "NBRK-${(10000 + rng.nextInt(89999))}"

    if (emitUiProgress) {
      _transmissionStage.value = NbrkTransmissionStage.CollectingIndicators
      delay(280L)
      _transmissionStage.value = NbrkTransmissionStage.SendingToApi(incidentCode)
      delay(latencyMs - 280L)
    }

    val factorDtos = factors.map {
      NbrkFactorScoreDto(code = it.name, label = it.labelRu, points = it.points)
    }

    val requestDto = NbrkAntifraudRequestDto(
      incidentCode = incidentCode,
      triggerType = trigger.name,
      recipientIban = recipientIban,
      recipientName = recipientName,
      isNewRecipient = isNewRecipient,
      isDropperMatch = isDropperMatch,
      socialEngineeringMarker = socialEngineeringMarker,
      amountTenge = amountTenge,
      timestampMillis = now,
      riskScore = riskScore,
      riskFactors = factorDtos
    )

    val httpCode = when {
      isDropperMatch -> 403
      trigger == NbrkTrigger.DURESS_LOGIN_SILENT || trigger == NbrkTrigger.DURESS_TRANSFER -> 202
      else -> 200
    }

    val responseStatus = when (trigger) {
      NbrkTrigger.DURESS_LOGIN_SILENT,
      NbrkTrigger.DURESS_TRANSFER -> "SILENT_DURESS_LOGGED"
      NbrkTrigger.DURESS_TRANSFER_ESCROW -> "SOFT_ESCROW_DIRECTIVE"
      NbrkTrigger.SECOND_HAND_REJECTED -> "BLOCKED_CONFIRMED_FRAUD"
      NbrkTrigger.HIGH_RISK_NEW_RECIPIENT -> if (isDropperMatch) "DROPPER_MATCH_HOLD" else "HIGH_RISK_ESCROW_HOLD"
    }

    val responseMessage = when {
      isDropperMatch -> "Реквизиты получателя совпали с базой дропперов. Средства заморожены (до 5 рабочих дней)."
      trigger == NbrkTrigger.DURESS_TRANSFER_ESCROW -> "Перевод поставлен на задержанный клиринг Soft-Escrow (5 рабочих дней)."
      trigger == NbrkTrigger.SECOND_HAND_REJECTED -> "Операция отклонена доверенным лицом; инцидент передан в Антифрод-центр."
      trigger == NbrkTrigger.DURESS_LOGIN_SILENT || trigger == NbrkTrigger.DURESS_TRANSFER -> "Зафиксирован скрытый сигнал принуждения."
      else -> "Индикаторы приняты Антифрод-центром НБРК для дополнительной проверки."
    }

    val responseDto = NbrkAntifraudResponseDto(
      incidentCode = incidentCode,
      httpCode = httpCode,
      status = responseStatus,
      latencyMs = latencyMs,
      recommendedHoldBusinessDays = 5,
      message = responseMessage
    )

    val requestJson = json.encodeToString(requestDto)
    val responseJson = json.encodeToString(responseDto)
    val breakdownJson = json.encodeToString(factorDtos)

    val entity = NbrkIncidentEntity(
      id = "inc_${UUID.randomUUID()}",
      incidentCode = incidentCode,
      triggerType = trigger,
      recipientIban = recipientIban,
      amountTenge = amountTenge,
      riskScore = riskScore,
      riskBreakdownJson = breakdownJson,
      requestJson = requestJson,
      responseJson = responseJson,
      latencyMs = latencyMs,
      httpCode = httpCode,
      createdAtMillis = now
    )

    db.nbrkIncidentDao().insert(entity)

    if (emitUiProgress) {
      _transmissionStage.value = NbrkTransmissionStage.VerdictReceived(
        incidentCode = incidentCode,
        httpCode = httpCode,
        latencyMs = latencyMs,
        statusLabel = responseMessage
      )
    }

    return entity
  }
}
