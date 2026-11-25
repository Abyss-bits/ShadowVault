package com.example.data.repository

import com.example.crypto.PinHasher
import com.example.data.ledger.LedgerService
import com.example.data.local.AccountEntity
import com.example.data.local.AppDatabase
import com.example.data.local.AuditLogEntity
import com.example.data.local.DepositEntity
import com.example.data.local.KnownRecipientEntity
import com.example.data.local.NbrkIncidentEntity
import com.example.data.local.SecondHandRequestEntity
import com.example.data.local.TransferEntity
import com.example.data.seed.DemoSeeder
import com.example.data.simulator.NbrkAntifraudApiSimulator
import com.example.data.simulator.NbrkTransmissionStage
import com.example.data.simulator.NbrkFactorScoreDto
import com.qalqan.domain.clock.AppClock
import com.qalqan.domain.clock.BusinessCalendar
import com.qalqan.domain.ledger.LedgerRules
import com.qalqan.domain.model.Account
import com.qalqan.domain.model.AccountType
import com.qalqan.domain.model.Deposit
import com.qalqan.domain.model.DepositStatus
import com.qalqan.domain.model.NbrkTrigger
import com.qalqan.domain.model.PinAuthResult
import com.qalqan.domain.model.SecondHandStatus
import com.qalqan.domain.model.SecuritySummary
import com.qalqan.domain.model.SessionMode
import com.qalqan.domain.model.TransferCodeType
import com.qalqan.domain.model.TransferRecord
import com.qalqan.domain.model.TransferStatus
import com.qalqan.domain.model.TransferType
import com.qalqan.domain.model.UserRole
import com.qalqan.domain.risk.LedgerRouting
import com.qalqan.domain.risk.RiskContext
import com.qalqan.domain.risk.RiskEngine
import com.qalqan.domain.risk.RiskFactor
import com.qalqan.domain.validation.KzIdentifierValidator
import com.qalqan.domain.validation.PinPolicyValidator
import com.qalqan.domain.validation.PinValidationResult
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

sealed interface TransferSubmitResult {
  data class Success(
    val transferId: String,
    val uiStatus: TransferStatus,
    val riskScore: Int,
    val message: String,
    val incidentCode: String? = null
  ) : TransferSubmitResult

  data class NeedsSecondConfirmation(
    val riskScore: Int,
    val factors: List<RiskFactor>
  ) : TransferSubmitResult

  data class Rejected(val reason: String) : TransferSubmitResult
}

sealed interface SecondHandActionResult {
  data class Success(val message: String, val incidentCode: String? = null) : SecondHandActionResult
  data class Rejected(val reason: String) : SecondHandActionResult
}

sealed interface SecurityUpdateResult {
  data class Success(val message: String) : SecurityUpdateResult
  data class Rejected(val reason: String) : SecurityUpdateResult
}

@OptIn(ExperimentalCoroutinesApi::class)
@Singleton
class QalqanRepository @Inject constructor(
  private val db: AppDatabase,
  private val pinHasher: PinHasher,
  private val clock: AppClock,
  private val businessCalendar: BusinessCalendar,
  private val seeder: DemoSeeder,
  private val ledgerService: LedgerService,
  private val nbrkSimulator: NbrkAntifraudApiSimulator
) {
  private val _sessionMode = MutableStateFlow(SessionMode.LOCKED)
  val sessionMode: StateFlow<SessionMode> = _sessionMode.asStateFlow()

  private val _activeRole = MutableStateFlow(UserRole.CLIENT_STANDARD)
  val activeRole: StateFlow<UserRole> = _activeRole.asStateFlow()

  val nbrkTransmissionStage: StateFlow<NbrkTransmissionStage> = nbrkSimulator.transmissionStage

  suspend fun initializeDemoData() {
    seeder.ensureSeeded()
  }

  suspend fun resetDemoData() {
    seeder.resetDemoData()
    nbrkSimulator.resetTransmissionStage()
    _sessionMode.value = SessionMode.LOCKED
    _activeRole.value = UserRole.CLIENT_STANDARD
  }

  suspend fun authenticatePin(rawPin: String): PinAuthResult =
    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
      seeder.ensureSeeded()
      val config = db.securityConfigDao().getConfig()
        ?: return@withContext PinAuthResult.Invalid(remainingAttempts = MAX_ATTEMPTS)

      val now = clock.nowMillis()
      if (config.lockedUntilMillis > now) {
        return@withContext PinAuthResult.LockedOut(config.lockedUntilMillis)
      }

      when {
        pinHasher.verifySecret(rawPin, config.standardPinHash) -> {
          db.securityConfigDao().updateAttemptState(attempts = 0, lockedUntil = 0L)
          _sessionMode.value = SessionMode.STANDARD
          _activeRole.value = UserRole.CLIENT_STANDARD
          db.auditLogDao().insert(
            AuditLogEntity(
              id = "aud_${UUID.randomUUID()}",
              actorRole = UserRole.CLIENT_STANDARD,
              action = "STANDARD_LOGIN",
              details = "Успешный вход в основной контур",
              createdAtMillis = now
            )
          )
          resolveTimedActions()
          PinAuthResult.Success(SessionMode.STANDARD)
        }

        pinHasher.verifySecret(rawPin, config.duressPinHash) -> {
          db.securityConfigDao().updateAttemptState(attempts = 0, lockedUntil = 0L)
          _sessionMode.value = SessionMode.DURESS
          _activeRole.value = UserRole.CLIENT_DURESS
          nbrkSimulator.submitIncident(
            trigger = NbrkTrigger.DURESS_LOGIN_SILENT,
            recipientIban = "INTERNAL_AUTH_EVENT",
            recipientName = "Системная авторизация",
            isNewRecipient = false,
            isDropperMatch = false,
            socialEngineeringMarker = true,
            amountTenge = 0L,
            riskScore = 95,
            factors = listOf(RiskFactor.COERCION_CODE),
            seed = config.seed,
            emitUiProgress = false
          )
          PinAuthResult.Success(SessionMode.DURESS)
        }

        else -> {
          val nextAttempts = config.failedPinAttempts + 1
          val lockoutMs = PinPolicyValidator.calculateLockoutDurationMillis(nextAttempts)
          val lockedUntil = if (lockoutMs > 0L) now + lockoutMs else 0L
          db.securityConfigDao().updateAttemptState(attempts = nextAttempts, lockedUntil = lockedUntil)
          if (lockedUntil > 0L) {
            PinAuthResult.LockedOut(lockedUntil)
          } else {
            PinAuthResult.Invalid(remainingAttempts = (MAX_ATTEMPTS - nextAttempts).coerceAtLeast(0))
          }
        }
      }
    }

  fun lockSession() {
    _sessionMode.value = SessionMode.LOCKED
    _activeRole.value = UserRole.CLIENT_STANDARD
    nbrkSimulator.resetTransmissionStage()
  }

  fun switchRoleInStandardMode(role: UserRole) {
    if (_sessionMode.value == SessionMode.STANDARD) {
      _activeRole.value = role
    }
  }

  fun resetNbrTransmissionUi() {
    nbrkSimulator.resetTransmissionStage()
  }

  suspend fun submitTransfer(
    recipientName: String,
    recipientIban: String,
    amountTenge: Long,
    transferCode: String,
    secondConfirmationProvided: Boolean = false,
    transferType: TransferType = TransferType.REGULAR
  ): TransferSubmitResult = withContext(Dispatchers.Default) {
    val mode = _sessionMode.value
    if (mode == SessionMode.LOCKED) {
      return@withContext TransferSubmitResult.Rejected("Сессия заблокирована")
    }

    val cleanIban = recipientIban.replace("\\s+".toRegex(), "").uppercase()
    if (!KzIdentifierValidator.isValidKzIban(cleanIban)) {
      return@withContext TransferSubmitResult.Rejected("Укажите корректный KZ IBAN получателя (20 символов)")
    }
    if (recipientName.isBlank()) {
      return@withContext TransferSubmitResult.Rejected("Укажите имя или наименование получателя")
    }

    val config = db.securityConfigDao().getConfig()
      ?: return@withContext TransferSubmitResult.Rejected("Конфигурация безопасности не найдена")

    val codeType = when {
      pinHasher.verifySecret(transferCode, config.standardCodeHash) -> TransferCodeType.STANDARD
      pinHasher.verifySecret(transferCode, config.duressCodeHash) -> TransferCodeType.DURESS
      else -> return@withContext TransferSubmitResult.Rejected("Неверный код подтверждения перевода")
    }

    val sourceAccountId = if (mode == SessionMode.DURESS) {
      DemoSeeder.ACC_ISOLATED_SAFE
    } else {
      DemoSeeder.ACC_MAIN
    }
    val sourceAccount = db.accountDao().getById(sourceAccountId)
      ?: return@withContext TransferSubmitResult.Rejected("Счёт списания не найден")

    val now = clock.nowMillis()
    val knownRecipient = db.knownRecipientDao().findByIban(cleanIban)
    val isDropper = knownRecipient?.isDropperFlag == true
    val isNewRecipient = knownRecipient == null || isDropper
    val recentSecChange = (now - config.lastSecurityChangeMillis) <= 86_400_000L
    val transfersLastHour = db.transferDao().countOutgoingSince(sourceAccountId, now - 3_600_000L)

    val riskContext = RiskContext(
      sessionMode = mode,
      transferType = transferType,
      codeType = codeType,
      amountTenge = amountTenge,
      availableBalanceTenge = sourceAccount.balanceTenge,
      thresholdTenge = config.thresholdTenge,
      isNewRecipient = isNewRecipient,
      isRecipientDropper = isDropper,
      localTime = clock.localDateTime().toLocalTime(),
      recentPinOrDeviceChange = recentSecChange,
      transfersInLastHour = transfersLastHour,
      secondConfirmationProvided = secondConfirmationProvided
    )

    val decision = RiskEngine.evaluate(riskContext)
    if (!decision.accepted) {
      if (decision.requiresSecondConfirmationPrompt) {
        return@withContext TransferSubmitResult.NeedsSecondConfirmation(
          riskScore = decision.riskScore,
          factors = decision.triggeredFactors
        )
      }
      return@withContext TransferSubmitResult.Rejected(decision.rejectionMessage ?: "Операция отклонена")
    }

    val targetLedgerNode = when (decision.ledgerRouting) {
      LedgerRouting.EXTERNAL_SETTLEMENT -> LedgerRules.EXTERNAL_SETTLEMENT_ID
      LedgerRouting.ESCROW_HOLD -> DemoSeeder.ACC_ESCROW_HOLD
      LedgerRouting.NONE -> return@withContext TransferSubmitResult.Rejected("Некорректный маршрут проводки")
    }

    val transferId = "tr_${UUID.randomUUID()}"
    val factorsText = if (decision.triggeredFactors.isEmpty()) {
      "Факторы риска не выявлены"
    } else {
      decision.triggeredFactors.joinToString(", ") { "${it.labelRu} (+${it.points})" }
    }

    val coolingOffUntil = if (decision.internalStatus == TransferStatus.COOLING_OFF) {
      now + config.coolingOffMinutes * 60_000L
    } else null

    val reviewDeadline = if (decision.internalStatus == TransferStatus.SOFT_ESCROW_HOLD) {
      val calDays = businessCalendar.businessDaysToCalendarDays(
        clock.localDate(),
        config.escrowBusinessDays
      )
      now + calDays * 86_400_000L
    } else null

    ledgerService.postTransferMovement(
      fromNodeId = sourceAccountId,
      toNodeId = targetLedgerNode,
      amountTenge = amountTenge,
      description = "Перевод -> $recipientName"
    )

    val entity = TransferEntity(
      id = transferId,
      sourceAccountId = sourceAccountId,
      recipientName = recipientName.trim(),
      recipientIban = cleanIban,
      isNewRecipient = isNewRecipient,
      amountTenge = amountTenge,
      transferType = transferType,
      codeType = codeType,
      status = decision.internalStatus!!,
      reviewDeadlineMillis = reviewDeadline,
      coolingOffUntilMillis = coolingOffUntil,
      riskScore = decision.riskScore,
      riskFactorsSummary = factorsText,
      createdAtMillis = now,
      isDuressOperation = (mode == SessionMode.DURESS)
    )
    db.transferDao().upsert(entity)

    var createdIncidentCode: String? = null
    if (decision.nbrkTrigger != null) {
      val incident = nbrkSimulator.submitIncident(
        trigger = decision.nbrkTrigger!!,
        recipientIban = cleanIban,
        recipientName = recipientName.trim(),
        isNewRecipient = isNewRecipient,
        isDropperMatch = isDropper,
        socialEngineeringMarker = (codeType == TransferCodeType.DURESS || mode == SessionMode.DURESS),
        amountTenge = amountTenge,
        riskScore = decision.riskScore,
        factors = decision.triggeredFactors,
        seed = config.seed,
        emitUiProgress = (mode == SessionMode.STANDARD)
      )
      createdIncidentCode = incident.incidentCode
    } else if (mode == SessionMode.STANDARD) {
      db.auditLogDao().insert(
        AuditLogEntity(
          id = "aud_${UUID.randomUUID()}",
          actorRole = UserRole.CLIENT_STANDARD,
          action = "TRANSFER_${decision.internalStatus?.name}",
          details = "Перевод оформлен ($transferId), скоринг ${decision.riskScore}",
          createdAtMillis = now
        )
      )
    }

    val resultMessage = when {
      mode == SessionMode.DURESS -> "Перевод выполнен"
      decision.uiStatus == TransferStatus.SOFT_ESCROW_HOLD ->
        "Перевод заморожен на эскроу-счёте (Soft-Escrow). Индикаторы переданы в Антифрод-центр НБРК."
      decision.uiStatus == TransferStatus.COOLING_OFF ->
        "Перевод на паузе Cooling-Off (${config.coolingOffMinutes} мин). Уведомление отправлено «второй руке»."
      else -> "Перевод успешно выполнен"
    }

    TransferSubmitResult.Success(
      transferId = transferId,
      uiStatus = decision.uiStatus!!,
      riskScore = decision.riskScore,
      message = resultMessage,
      incidentCode = if (mode == SessionMode.STANDARD) createdIncidentCode else null
    )
  }

  suspend fun submitEarlyDepositWithdrawal(
    depositId: String,
    recipientName: String,
    recipientIban: String,
    amountTenge: Long,
    transferCode: String,
    secondConfirmationProvided: Boolean = false
  ): TransferSubmitResult = withContext(Dispatchers.Default) {
    if (_sessionMode.value != SessionMode.STANDARD) {
      return@withContext TransferSubmitResult.Rejected("Операция недоступна в текущем режиме")
    }
    val cleanIban = recipientIban.replace("\\s+".toRegex(), "").uppercase()
    if (!KzIdentifierValidator.isValidKzIban(cleanIban)) {
      return@withContext TransferSubmitResult.Rejected("Укажите корректный KZ IBAN получателя (20 символов)")
    }
    if (recipientName.isBlank()) {
      return@withContext TransferSubmitResult.Rejected("Укажите имя или наименование получателя")
    }

    val config = db.securityConfigDao().getConfig()
      ?: return@withContext TransferSubmitResult.Rejected("Конфигурация безопасности не найдена")

    val deposit = db.depositDao().getById(depositId)
      ?: return@withContext TransferSubmitResult.Rejected("Депозит не найден")
    if (deposit.isDecoy || deposit.status != DepositStatus.ACTIVE) {
      return@withContext TransferSubmitResult.Rejected(
        "Депозит недоступен для досрочного закрытия (уже закрыт или на проверке)"
      )
    }

    val codeType = when {
      pinHasher.verifySecret(transferCode, config.standardCodeHash) -> TransferCodeType.STANDARD
      pinHasher.verifySecret(transferCode, config.duressCodeHash) -> TransferCodeType.DURESS
      else -> return@withContext TransferSubmitResult.Rejected("Неверный код подтверждения операции")
    }

    val now = clock.nowMillis()
    val knownRecipient = db.knownRecipientDao().findByIban(cleanIban)
    val isDropper = knownRecipient?.isDropperFlag == true
    val isNewRecipient = knownRecipient == null || isDropper
    val recentSecChange = (now - config.lastSecurityChangeMillis) <= 86_400_000L
    val transfersLastHour = db.transferDao().countOutgoingSince(DemoSeeder.ACC_MAIN, now - 3_600_000L)

    val decision = RiskEngine.evaluate(
      RiskContext(
        sessionMode = SessionMode.STANDARD,
        transferType = TransferType.EARLY_DEPOSIT_PAYOUT,
        codeType = codeType,
        amountTenge = amountTenge,
        availableBalanceTenge = deposit.balanceTenge,
        thresholdTenge = config.thresholdTenge,
        isNewRecipient = isNewRecipient,
        isRecipientDropper = isDropper,
        localTime = clock.localDateTime().toLocalTime(),
        recentPinOrDeviceChange = recentSecChange,
        transfersInLastHour = transfersLastHour,
        secondConfirmationProvided = secondConfirmationProvided
      )
    )

    if (!decision.accepted) {
      if (decision.requiresSecondConfirmationPrompt) {
        return@withContext TransferSubmitResult.NeedsSecondConfirmation(
          riskScore = decision.riskScore,
          factors = decision.triggeredFactors
        )
      }
      return@withContext TransferSubmitResult.Rejected(decision.rejectionMessage ?: "Операция отклонена")
    }

    val targetLedgerNode = when (decision.ledgerRouting) {
      LedgerRouting.EXTERNAL_SETTLEMENT -> LedgerRules.EXTERNAL_SETTLEMENT_ID
      LedgerRouting.ESCROW_HOLD -> DemoSeeder.ACC_ESCROW_HOLD
      LedgerRouting.NONE -> return@withContext TransferSubmitResult.Rejected("Некорректный маршрут проводки")
    }

    val transferId = "tr_${UUID.randomUUID()}"
    val factorsText = if (decision.triggeredFactors.isEmpty()) {
      "Факторы риска не выявлены"
    } else {
      decision.triggeredFactors.joinToString(", ") { "${it.labelRu} (+${it.points})" }
    }

    val coolingOffUntil = if (decision.internalStatus == TransferStatus.COOLING_OFF) {
      now + config.coolingOffMinutes * 60_000L
    } else null

    val reviewDeadline = if (decision.internalStatus == TransferStatus.SOFT_ESCROW_HOLD) {
      val calDays = businessCalendar.businessDaysToCalendarDays(
        clock.localDate(),
        config.escrowBusinessDays
      )
      now + calDays * 86_400_000L
    } else null

    ledgerService.postTransferMovement(
      fromNodeId = deposit.id,
      toNodeId = targetLedgerNode,
      amountTenge = amountTenge,
      description = "Досрочное закрытие «${deposit.title}» -> $recipientName"
    )

    val remainingOnDeposit = deposit.balanceTenge - amountTenge
    if (decision.internalStatus == TransferStatus.COMPLETED) {
      if (remainingOnDeposit > 0L) {
        ledgerService.postTransferMovement(
          fromNodeId = deposit.id,
          toNodeId = DemoSeeder.ACC_MAIN,
          amountTenge = remainingOnDeposit,
          description = "Остаток закрытого депозита «${deposit.title}» на основной счёт"
        )
      }
      db.depositDao().updateDepositState(deposit.id, DepositStatus.CLOSED, false, 0L)
    } else {
      db.depositDao().updateDepositState(
        id = deposit.id,
        status = DepositStatus.PENDING_EARLY_CLOSURE,
        isLocked = deposit.isMoneyLocked,
        balance = remainingOnDeposit
      )
    }

    db.transferDao().upsert(
      TransferEntity(
        id = transferId,
        sourceAccountId = deposit.id,
        recipientName = recipientName.trim(),
        recipientIban = cleanIban,
        isNewRecipient = isNewRecipient,
        amountTenge = amountTenge,
        transferType = TransferType.EARLY_DEPOSIT_PAYOUT,
        codeType = codeType,
        status = decision.internalStatus!!,
        reviewDeadlineMillis = reviewDeadline,
        coolingOffUntilMillis = coolingOffUntil,
        riskScore = decision.riskScore,
        riskFactorsSummary = factorsText,
        createdAtMillis = now,
        isDuressOperation = false
      )
    )

    if (decision.internalStatus == TransferStatus.AWAITING_SECOND_HAND) {
      val factorDtos = decision.triggeredFactors.map {
        NbrkFactorScoreDto(code = it.name, label = it.labelRu, points = it.points)
      }
      val secondHandReq = SecondHandRequestEntity(
        id = "sh_${UUID.randomUUID()}",
        transferId = transferId,
        depositId = deposit.id,
        depositTitle = deposit.title,
        initiatorName = "Владелец счёта",
        recipientName = recipientName.trim(),
        recipientIban = cleanIban,
        amountTenge = amountTenge,
        riskScore = decision.riskScore,
        riskFactorsJson = prettyJson.encodeToString(factorDtos),
        status = SecondHandStatus.PENDING,
        createdAtMillis = now,
        unlockAfterMillis = now + SECOND_HAND_PAUSE_MILLIS,
        expiresAtMillis = now + SECOND_HAND_EXPIRY_MILLIS,
        assignedContactName = config.primaryTrustedName,
        assignedContactPhone = config.primaryTrustedPhone,
        isBackupAssigned = false,
        comment = ""
      )
      db.secondHandDao().upsert(secondHandReq)
      db.auditLogDao().insert(
        AuditLogEntity(
          id = "aud_${UUID.randomUUID()}",
          actorRole = UserRole.CLIENT_STANDARD,
          action = "SECOND_HAND_REQUEST_CREATED",
          details = "Создана заявка на подтверждение «Второй рукой» для депозита «${deposit.title}» (скоринг ${decision.riskScore})",
          createdAtMillis = now
        )
      )
    }

    val internalStatus = decision.internalStatus!!
    var createdIncidentCode: String? = null
    if (decision.nbrkTrigger != null) {
      val incident = nbrkSimulator.submitIncident(
        trigger = decision.nbrkTrigger!!,
        recipientIban = cleanIban,
        recipientName = recipientName.trim(),
        isNewRecipient = isNewRecipient,
        isDropperMatch = isDropper,
        socialEngineeringMarker = (codeType == TransferCodeType.DURESS),
        amountTenge = amountTenge,
        riskScore = decision.riskScore,
        factors = decision.triggeredFactors,
        seed = config.seed,
        emitUiProgress = true
      )
      createdIncidentCode = incident.incidentCode
    } else if (internalStatus != TransferStatus.AWAITING_SECOND_HAND) {
      db.auditLogDao().insert(
        AuditLogEntity(
          id = "aud_${UUID.randomUUID()}",
          actorRole = UserRole.CLIENT_STANDARD,
          action = "EARLY_DEPOSIT_${internalStatus.name}",
          details = "Досрочная операция по депозиту «${deposit.title}», статус ${internalStatus.name}",
          createdAtMillis = now
        )
      )
    }

    val resultMessage = when (decision.uiStatus) {
      TransferStatus.AWAITING_SECOND_HAND ->
        "Операция приостановлена до подтверждения «Второй рукой» (${config.primaryTrustedName}). Пауза безопасности — 10 минут."
      TransferStatus.SOFT_ESCROW_HOLD ->
        "Средства заморожены на эскроу-счёте (Soft-Escrow). Индикаторы переданы в Антифрод-центр НБРК."
      TransferStatus.COOLING_OFF ->
        "Выплата на паузе Cooling-Off (${config.coolingOffMinutes} мин)."
      else -> "Досрочное снятие с депозита выполнено."
    }

    TransferSubmitResult.Success(
      transferId = transferId,
      uiStatus = decision.uiStatus!!,
      riskScore = decision.riskScore,
      message = resultMessage,
      incidentCode = createdIncidentCode
    )
  }

  suspend fun approveSecondHandRequest(
    requestId: String,
    secondHandPin: String,
    comment: String = ""
  ): SecondHandActionResult = withContext(Dispatchers.Default) {
    if (_sessionMode.value != SessionMode.STANDARD) {
      return@withContext SecondHandActionResult.Rejected("Операция недоступна")
    }
    val config = db.securityConfigDao().getConfig()
      ?: return@withContext SecondHandActionResult.Rejected("Конфигурация не найдена")
    val req = db.secondHandDao().getById(requestId)
      ?: return@withContext SecondHandActionResult.Rejected("Заявка не найдена")
    if (req.status != SecondHandStatus.PENDING) {
      return@withContext SecondHandActionResult.Rejected("Заявка уже обработана (${req.status.name})")
    }

    val now = clock.nowMillis()
    if (now >= req.expiresAtMillis) {
      expireSingleRequest(req, now)
      return@withContext SecondHandActionResult.Rejected(
        "Истёк срок ожидания (24 часа) — заявка отменена, средства возвращены под Money Lock"
      )
    }
    if (now < req.unlockAfterMillis) {
      val remainingSec = ((req.unlockAfterMillis - now) / 1_000L).coerceAtLeast(1L)
      val mins = remainingSec / 60L
      val secs = remainingSec % 60L
      return@withContext SecondHandActionResult.Rejected(
        "Обязательная 10-минутная пауза не завершена (осталось ${mins} мин ${secs} сек)"
      )
    }

    if (!pinHasher.verifySecret(secondHandPin, config.secondHandPinHash)) {
      return@withContext SecondHandActionResult.Rejected("Неверный ПИН-код «Второй руки»")
    }

    ledgerService.postTransferMovement(
      fromNodeId = DemoSeeder.ACC_ESCROW_HOLD,
      toNodeId = LedgerRules.EXTERNAL_SETTLEMENT_ID,
      amountTenge = req.amountTenge,
      description = "Подтверждено Второй рукой -> ${req.recipientName}"
    )

    val deposit = db.depositDao().getById(req.depositId)
    if (deposit != null) {
      if (deposit.balanceTenge > 0L) {
        ledgerService.postTransferMovement(
          fromNodeId = deposit.id,
          toNodeId = DemoSeeder.ACC_MAIN,
          amountTenge = deposit.balanceTenge,
          description = "Остаток закрытого депозита «${deposit.title}» на основной счёт"
        )
      }
      db.depositDao().updateDepositState(deposit.id, DepositStatus.CLOSED, false, 0L)
    }

    db.transferDao().updateStatus(req.transferId, TransferStatus.COMPLETED)
    db.secondHandDao().upsert(
      req.copy(
        status = SecondHandStatus.APPROVED,
        comment = comment.trim().ifBlank { "Подтверждено доверенным лицом (${req.assignedContactName})" }
      )
    )
    db.auditLogDao().insert(
      AuditLogEntity(
        id = "aud_${UUID.randomUUID()}",
        actorRole = UserRole.SECOND_HAND,
        action = "SECOND_HAND_APPROVED",
        details = "Подтверждён вывод по заявке ${req.id} доверенным лицом (${req.assignedContactName}), депозит закрыт",
        createdAtMillis = now
      )
    )

    SecondHandActionResult.Success("Перевод подтверждён «Второй рукой», депозит закрыт.")
  }

  suspend fun rejectSecondHandRequestAsFraud(
    requestId: String,
    comment: String = ""
  ): SecondHandActionResult = withContext(Dispatchers.Default) {
    if (_sessionMode.value != SessionMode.STANDARD) {
      return@withContext SecondHandActionResult.Rejected("Операция недоступна")
    }
    val config = db.securityConfigDao().getConfig()
      ?: return@withContext SecondHandActionResult.Rejected("Конфигурация не найдена")
    val req = db.secondHandDao().getById(requestId)
      ?: return@withContext SecondHandActionResult.Rejected("Заявка не найдена")
    if (req.status != SecondHandStatus.PENDING) {
      return@withContext SecondHandActionResult.Rejected("Заявка уже обработана (${req.status.name})")
    }

    val now = clock.nowMillis()
    ledgerService.postTransferMovement(
      fromNodeId = DemoSeeder.ACC_ESCROW_HOLD,
      toNodeId = req.depositId,
      amountTenge = req.amountTenge,
      description = "Отклонено Второй рукой -> возврат под Money Lock (${req.depositTitle})"
    )

    val restoredDeposit = db.depositDao().getById(req.depositId)
    if (restoredDeposit != null) {
      db.depositDao().updateDepositState(
        id = restoredDeposit.id,
        status = DepositStatus.ACTIVE,
        isLocked = true,
        balance = restoredDeposit.balanceTenge
      )
    }

    db.transferDao().updateStatus(req.transferId, TransferStatus.BLOCKED_FRAUD)
    db.secondHandDao().upsert(
      req.copy(
        status = SecondHandStatus.REJECTED_FRAUD,
        comment = comment.trim().ifBlank { "Отклонено доверенным лицом: подозрение на мошенничество" }
      )
    )

    val isDropper = db.knownRecipientDao().findByIban(req.recipientIban)?.isDropperFlag == true
    val incident = nbrkSimulator.submitIncident(
      trigger = NbrkTrigger.SECOND_HAND_REJECTED,
      recipientIban = req.recipientIban,
      recipientName = req.recipientName,
      isNewRecipient = true,
      isDropperMatch = isDropper,
      socialEngineeringMarker = true,
      amountTenge = req.amountTenge,
      riskScore = maxOf(req.riskScore, 85),
      factors = listOf(RiskFactor.NEW_RECIPIENT, RiskFactor.LARGE_AMOUNT),
      seed = config.seed,
      emitUiProgress = true
    )

    db.auditLogDao().insert(
      AuditLogEntity(
        id = "aud_${UUID.randomUUID()}",
        actorRole = UserRole.SECOND_HAND,
        action = "SECOND_HAND_REJECTED_FRAUD",
        details = "Заявка ${req.id} отклонена как мошенничество, средства возвращены под Money Lock, инцидент ${incident.incidentCode}",
        createdAtMillis = now
      )
    )

    SecondHandActionResult.Success(
      message = "Операция заблокирована, средства возвращены под защиту Money Lock. Срочный инцидент ${incident.incidentCode} направлен в НБРК.",
      incidentCode = incident.incidentCode
    )
  }

  suspend fun escalateSecondHandToBackup(requestId: String): SecondHandActionResult =
    withContext(Dispatchers.Default) {
      if (_sessionMode.value != SessionMode.STANDARD) {
        return@withContext SecondHandActionResult.Rejected("Операция недоступна")
      }
      val config = db.securityConfigDao().getConfig()
        ?: return@withContext SecondHandActionResult.Rejected("Конфигурация не найдена")
      val req = db.secondHandDao().getById(requestId)
        ?: return@withContext SecondHandActionResult.Rejected("Заявка не найдена")
      if (req.status != SecondHandStatus.PENDING) {
        return@withContext SecondHandActionResult.Rejected("Заявка уже обработана")
      }
      if (req.isBackupAssigned) {
        return@withContext SecondHandActionResult.Rejected("Резервное доверенное лицо уже назначено")
      }

      val now = clock.nowMillis()
      db.secondHandDao().upsert(
        req.copy(
          assignedContactName = config.backupTrustedName,
          assignedContactPhone = config.backupTrustedPhone,
          isBackupAssigned = true
        )
      )
      db.auditLogDao().insert(
        AuditLogEntity(
          id = "aud_${UUID.randomUUID()}",
          actorRole = UserRole.CLIENT_STANDARD,
          action = "SECOND_HAND_ESCALATED_BACKUP",
          details = "Заявка ${req.id} переведена на резервное доверенное лицо (${config.backupTrustedName})",
          createdAtMillis = now
        )
      )
      SecondHandActionResult.Success("Запрос передан резервной «Второй руке»: ${config.backupTrustedName}")
    }

  suspend fun resolveTimedActions(): Int {
    val cooling = resolveMaturedCoolingOffTransfers()
    val expired = resolveExpiredSecondHandRequests()
    val escrowSettled = resolveMaturedSoftEscrowTransfers()
    return cooling + expired + escrowSettled
  }

  suspend fun resolveMaturedSoftEscrowTransfers(): Int = withContext(Dispatchers.Default) {
    val now = clock.nowMillis()
    val held = db.transferDao().getByStatuses(listOf(TransferStatus.SOFT_ESCROW_HOLD))
    var resolvedCount = 0
    for (item in held) {
      if (item.isDuressOperation) continue
      val deadline = item.reviewDeadlineMillis ?: continue
      if (deadline <= now) {
        ledgerService.postTransferMovement(
          fromNodeId = DemoSeeder.ACC_ESCROW_HOLD,
          toNodeId = item.sourceAccountId,
          amountTenge = item.amountTenge,
          description = "Возврат после 5-дневной проверки Soft-Escrow -> ${item.recipientName}"
        )
        if (item.transferType == TransferType.EARLY_DEPOSIT_PAYOUT) {
          val dep = db.depositDao().getById(item.sourceAccountId)
          if (dep != null) {
            db.depositDao().updateDepositState(
              id = dep.id,
              status = DepositStatus.ACTIVE,
              isLocked = true,
              balance = dep.balanceTenge
            )
          }
        }
        db.transferDao().updateStatus(item.id, TransferStatus.BLOCKED_FRAUD)
        db.auditLogDao().insert(
          AuditLogEntity(
            id = "aud_${UUID.randomUUID()}",
            actorRole = UserRole.NBRK_INSPECTOR,
            action = "SOFT_ESCROW_REVIEW_BLOCKED",
            details = "Завершён срок проверки Soft-Escrow (${item.id}): операция заблокирована, средства возвращены",
            createdAtMillis = now
          )
        )
        resolvedCount++
      }
    }
    resolvedCount
  }

  suspend fun resolveExpiredSecondHandRequests(): Int = withContext(Dispatchers.Default) {
    val now = clock.nowMillis()
    val pending = db.secondHandDao().getPending()
    var expiredCount = 0
    for (req in pending) {
      if (req.expiresAtMillis <= now) {
        expireSingleRequest(req, now)
        expiredCount++
      }
    }
    expiredCount
  }

  private suspend fun expireSingleRequest(req: SecondHandRequestEntity, now: Long) {
    ledgerService.postTransferMovement(
      fromNodeId = DemoSeeder.ACC_ESCROW_HOLD,
      toNodeId = req.depositId,
      amountTenge = req.amountTenge,
      description = "Автооткат по таймауту 24 ч -> ${req.depositTitle}"
    )
    val restoredDeposit = db.depositDao().getById(req.depositId)
    if (restoredDeposit != null) {
      db.depositDao().updateDepositState(
        id = restoredDeposit.id,
        status = DepositStatus.ACTIVE,
        isLocked = true,
        balance = restoredDeposit.balanceTenge
      )
    }
    db.transferDao().updateStatus(req.transferId, TransferStatus.BLOCKED_FRAUD)
    db.secondHandDao().upsert(
      req.copy(
        status = SecondHandStatus.EXPIRED,
        comment = "Автоотмена: истёк 24-часовой срок ожидания «Второй руки»"
      )
    )
    db.auditLogDao().insert(
      AuditLogEntity(
        id = "aud_${UUID.randomUUID()}",
        actorRole = UserRole.SECOND_HAND,
        action = "SECOND_HAND_EXPIRED",
        details = "Таймаут 24 ч по заявке ${req.id}: средства возвращены под Money Lock",
        createdAtMillis = now
      )
    )
  }

  suspend fun resolveMaturedCoolingOffTransfers(): Int = withContext(Dispatchers.Default) {
    val now = clock.nowMillis()
    val pending = db.transferDao().getByStatuses(listOf(TransferStatus.COOLING_OFF))
    var settled = 0
    for (item in pending) {
      val deadline = item.coolingOffUntilMillis ?: continue
      if (deadline <= now) {
        ledgerService.postTransferMovement(
          fromNodeId = DemoSeeder.ACC_ESCROW_HOLD,
          toNodeId = LedgerRules.EXTERNAL_SETTLEMENT_ID,
          amountTenge = item.amountTenge,
          description = "Завершение Cooling-Off -> ${item.recipientName}"
        )
        if (item.transferType == TransferType.EARLY_DEPOSIT_PAYOUT) {
          val dep = db.depositDao().getById(item.sourceAccountId)
          if (dep != null) {
            if (dep.balanceTenge > 0L) {
              ledgerService.postTransferMovement(
                fromNodeId = dep.id,
                toNodeId = DemoSeeder.ACC_MAIN,
                amountTenge = dep.balanceTenge,
                description = "Остаток закрытого депозита «${dep.title}» на основной счёт"
              )
            }
            db.depositDao().updateDepositState(dep.id, DepositStatus.CLOSED, false, 0L)
          }
        }
        db.transferDao().updateStatus(item.id, TransferStatus.COMPLETED)
        settled++
      }
    }
    settled
  }

  suspend fun updateSecurityPolicy(
    thresholdTenge: Long,
    isolatedSafeTargetTenge: Long,
    coolingOffMinutes: Int,
    escrowBusinessDays: Int,
    primaryTrustedName: String,
    primaryTrustedPhone: String,
    backupTrustedName: String,
    backupTrustedPhone: String
  ): SecurityUpdateResult = withContext(Dispatchers.Default) {
    if (_sessionMode.value != SessionMode.STANDARD) {
      return@withContext SecurityUpdateResult.Rejected("Настройки недоступны в текущем режиме")
    }
    if (thresholdTenge < 10_000L) {
      return@withContext SecurityUpdateResult.Rejected("Порог крупной операции не может быть ниже 10 000 ₸")
    }
    if (isolatedSafeTargetTenge < 0L) {
      return@withContext SecurityUpdateResult.Rejected("Целевой лимит субсчёта не может быть отрицательным")
    }
    if (coolingOffMinutes !in 5..180) {
      return@withContext SecurityUpdateResult.Rejected("Пауза Cooling-Off должна составлять от 5 до 180 минут")
    }
    if (escrowBusinessDays !in 1..14) {
      return@withContext SecurityUpdateResult.Rejected("Срок Soft-Escrow должен составлять от 1 до 14 рабочих дней")
    }
    if (primaryTrustedName.isBlank() || primaryTrustedPhone.isBlank() ||
      backupTrustedName.isBlank() || backupTrustedPhone.isBlank()
    ) {
      return@withContext SecurityUpdateResult.Rejected("Заполните имя и телефон основного и резервного доверенного лица")
    }

    val config = db.securityConfigDao().getConfig()
      ?: return@withContext SecurityUpdateResult.Rejected("Конфигурация не найдена")

    val now = clock.nowMillis()
    db.securityConfigDao().upsert(
      config.copy(
        thresholdTenge = thresholdTenge,
        isolatedSafeTargetTenge = isolatedSafeTargetTenge,
        coolingOffMinutes = coolingOffMinutes,
        escrowBusinessDays = escrowBusinessDays,
        primaryTrustedName = primaryTrustedName.trim(),
        primaryTrustedPhone = primaryTrustedPhone.trim(),
        backupTrustedName = backupTrustedName.trim(),
        backupTrustedPhone = backupTrustedPhone.trim()
      )
    )
    db.auditLogDao().insert(
      AuditLogEntity(
        id = "aud_${UUID.randomUUID()}",
        actorRole = UserRole.CLIENT_STANDARD,
        action = "SECURITY_POLICY_UPDATED",
        details = "Обновлены параметры антифрод-политики (охлаждение ${coolingOffMinutes} мин, Soft-Escrow ${escrowBusinessDays} раб. дн.) и контакты «Второй руки»",
        createdAtMillis = now
      )
    )
    SecurityUpdateResult.Success("Параметры Центра защиты сохранены")
  }

  suspend fun updateLoginPins(
    currentStandardPin: String,
    newStandardPin: String,
    newDuressPin: String
  ): SecurityUpdateResult = withContext(Dispatchers.Default) {
    if (_sessionMode.value != SessionMode.STANDARD) {
      return@withContext SecurityUpdateResult.Rejected("Смена ПИН недоступна в текущем режиме")
    }
    val config = db.securityConfigDao().getConfig()
      ?: return@withContext SecurityUpdateResult.Rejected("Конфигурация не найдена")
    if (!pinHasher.verifySecret(currentStandardPin, config.standardPinHash)) {
      return@withContext SecurityUpdateResult.Rejected("Неверный текущий основной ПИН-код")
    }
    val stdCheck = PinPolicyValidator.validateNewPin(newStandardPin, newDuressPin)
    if (stdCheck is PinValidationResult.Invalid) {
      return@withContext SecurityUpdateResult.Rejected("Основной ПИН: ${stdCheck.reason}")
    }
    val duressCheck = PinPolicyValidator.validateNewPin(newDuressPin, newStandardPin)
    if (duressCheck is PinValidationResult.Invalid) {
      return@withContext SecurityUpdateResult.Rejected("Тревожный ПИН: ${duressCheck.reason}")
    }

    val now = clock.nowMillis()
    db.securityConfigDao().upsert(
      config.copy(
        standardPinHash = pinHasher.hashSecret(newStandardPin),
        duressPinHash = pinHasher.hashSecret(newDuressPin),
        lastSecurityChangeMillis = now
      )
    )
    db.auditLogDao().insert(
      AuditLogEntity(
        id = "aud_${UUID.randomUUID()}",
        actorRole = UserRole.CLIENT_STANDARD,
        action = "LOGIN_PINS_ROTATED",
        details = "Обновлены основной и тревожный ПИН-коды входа (активирован 24ч маркер недавней смены)",
        createdAtMillis = now
      )
    )
    SecurityUpdateResult.Success("ПИН-коды входа успешно обновлены")
  }

  suspend fun updateTransferCodes(
    currentStandardPin: String,
    newStandardCode: String,
    newDuressCode: String
  ): SecurityUpdateResult = withContext(Dispatchers.Default) {
    if (_sessionMode.value != SessionMode.STANDARD) {
      return@withContext SecurityUpdateResult.Rejected("Смена кодов недоступна в текущем режиме")
    }
    val config = db.securityConfigDao().getConfig()
      ?: return@withContext SecurityUpdateResult.Rejected("Конфигурация не найдена")
    if (!pinHasher.verifySecret(currentStandardPin, config.standardPinHash)) {
      return@withContext SecurityUpdateResult.Rejected("Неверный текущий основной ПИН-код")
    }
    val stdCheck = PinPolicyValidator.validateNewPin(newStandardCode, newDuressCode)
    if (stdCheck is PinValidationResult.Invalid) {
      return@withContext SecurityUpdateResult.Rejected("Обычный код: ${stdCheck.reason}")
    }
    val duressCheck = PinPolicyValidator.validateNewPin(newDuressCode, newStandardCode)
    if (duressCheck is PinValidationResult.Invalid) {
      return@withContext SecurityUpdateResult.Rejected("Тревожный код: ${duressCheck.reason}")
    }

    val now = clock.nowMillis()
    db.securityConfigDao().upsert(
      config.copy(
        standardCodeHash = pinHasher.hashSecret(newStandardCode),
        duressCodeHash = pinHasher.hashSecret(newDuressCode),
        lastSecurityChangeMillis = now
      )
    )
    db.auditLogDao().insert(
      AuditLogEntity(
        id = "aud_${UUID.randomUUID()}",
        actorRole = UserRole.CLIENT_STANDARD,
        action = "TRANSFER_CODES_ROTATED",
        details = "Обновлены коды подтверждения операций",
        createdAtMillis = now
      )
    )
    SecurityUpdateResult.Success("Коды подтверждения переводов обновлены")
  }

  suspend fun updateSecondHandPin(
    currentStandardPin: String,
    newSecondHandPin: String
  ): SecurityUpdateResult = withContext(Dispatchers.Default) {
    if (_sessionMode.value != SessionMode.STANDARD) {
      return@withContext SecurityUpdateResult.Rejected("Настройка недоступна в текущем режиме")
    }
    val config = db.securityConfigDao().getConfig()
      ?: return@withContext SecurityUpdateResult.Rejected("Конфигурация не найдена")
    if (!pinHasher.verifySecret(currentStandardPin, config.standardPinHash)) {
      return@withContext SecurityUpdateResult.Rejected("Неверный текущий основной ПИН-код")
    }
    val check = PinPolicyValidator.validateNewPin(newSecondHandPin, currentStandardPin)
    if (check is PinValidationResult.Invalid) {
      return@withContext SecurityUpdateResult.Rejected("ПИН «Второй руки»: ${check.reason}")
    }
    if (pinHasher.verifySecret(newSecondHandPin, config.duressPinHash)) {
      return@withContext SecurityUpdateResult.Rejected("ПИН «Второй руки» не должен совпадать с тревожным ПИН-кодом")
    }

    val now = clock.nowMillis()
    db.securityConfigDao().upsert(
      config.copy(
        secondHandPinHash = pinHasher.hashSecret(newSecondHandPin),
        lastSecurityChangeMillis = now
      )
    )
    db.auditLogDao().insert(
      AuditLogEntity(
        id = "aud_${UUID.randomUUID()}",
        actorRole = UserRole.CLIENT_STANDARD,
        action = "SECOND_HAND_PIN_ROTATED",
        details = "Обновлён ПИН-код доверенного лица («Вторая рука»)",
        createdAtMillis = now
      )
    )
    SecurityUpdateResult.Success("ПИН-код «Второй руки» обновлён")
  }

  fun observeVisibleAccounts(): Flow<List<Account>> =
    sessionMode.flatMapLatest { mode ->
      when (mode) {
        SessionMode.LOCKED -> flowOf(emptyList())
        SessionMode.STANDARD ->
          db.accountDao()
            .observeByTypes(listOf(AccountType.MAIN, AccountType.ISOLATED_SAFE, AccountType.ESCROW_HOLD))
            .map { list -> list.map { it.toDomain() } }
        SessionMode.DURESS ->
          db.accountDao()
            .observeByTypes(listOf(AccountType.ISOLATED_SAFE))
            .map { list -> list.map { it.toDomain() } }
      }
    }

  fun observeVisibleDeposits(): Flow<List<Deposit>> =
    sessionMode.flatMapLatest { mode ->
      when (mode) {
        SessionMode.LOCKED -> flowOf(emptyList())
        SessionMode.STANDARD ->
          db.depositDao().observeByDecoyFlag(isDecoy = false).map { list -> list.map { it.toDomain() } }
        SessionMode.DURESS ->
          db.depositDao().observeByDecoyFlag(isDecoy = true).map { list -> list.map { it.toDomain() } }
      }
    }

  fun observeVisibleTransfers(): Flow<List<TransferRecord>> =
    sessionMode.flatMapLatest { mode ->
      when (mode) {
        SessionMode.LOCKED -> flowOf(emptyList())
        SessionMode.STANDARD ->
          db.transferDao()
            .observeByAccountIds(
              listOf(
                DemoSeeder.ACC_MAIN,
                DemoSeeder.ACC_ISOLATED_SAFE,
                DemoSeeder.DEP_PREMIER,
                DemoSeeder.DEP_RESERVE
              )
            )
            .map { list -> list.map { it.toDomain() } }
        SessionMode.DURESS ->
          db.transferDao()
            .observeByAccountIds(listOf(DemoSeeder.ACC_ISOLATED_SAFE))
            .map { list -> list.map { it.toDomain() } }
      }
    }

  fun observeSecuritySummary(): Flow<SecuritySummary?> =
    sessionMode.flatMapLatest { mode ->
      if (mode != SessionMode.STANDARD) {
        flowOf(null)
      } else {
        db.securityConfigDao().observeConfig().map { cfg ->
          cfg?.let {
            SecuritySummary(
              thresholdTenge = it.thresholdTenge,
              isolatedSafeTargetTenge = it.isolatedSafeTargetTenge,
              coolingOffMinutes = it.coolingOffMinutes,
              escrowBusinessDays = it.escrowBusinessDays,
              primaryTrustedName = it.primaryTrustedName,
              primaryTrustedPhone = it.primaryTrustedPhone,
              backupTrustedName = it.backupTrustedName,
              backupTrustedPhone = it.backupTrustedPhone,
              failedPinAttempts = it.failedPinAttempts,
              lockedUntilMillis = it.lockedUntilMillis
            )
          }
        }
      }
    }

  fun observeNbrkIncidents(): Flow<List<NbrkIncidentEntity>> =
    sessionMode.flatMapLatest { mode ->
      if (mode != SessionMode.STANDARD) flowOf(emptyList())
      else db.nbrkIncidentDao().observeAll()
    }

  fun observeSecondHandRequests(): Flow<List<SecondHandRequestEntity>> =
    sessionMode.flatMapLatest { mode ->
      if (mode != SessionMode.STANDARD) flowOf(emptyList())
      else db.secondHandDao().observeAll()
    }

  fun observeAuditLogs(): Flow<List<AuditLogEntity>> =
    sessionMode.flatMapLatest { mode ->
      if (mode != SessionMode.STANDARD) flowOf(emptyList())
      else db.auditLogDao().observeRecent()
    }

  fun observeTrustedRecipients(): Flow<List<KnownRecipientEntity>> =
    sessionMode.flatMapLatest { mode ->
      if (mode == SessionMode.LOCKED) flowOf(emptyList())
      else db.knownRecipientDao().observeTrusted()
    }

  suspend fun getDropperPresetsForStandardMode(): List<KnownRecipientEntity> {
    if (_sessionMode.value != SessionMode.STANDARD) return emptyList()
    // Исключительно для стандартного режима при тестировании правил стоп-листа
    val all = db.knownRecipientDao().findByIban(
      KzIdentifierValidator.buildValidKzIban("965", "0006660006661")
    )
    return listOfNotNull(all)
  }

  private fun AccountEntity.toDomain() = Account(
    id = id,
    title = title,
    iban = iban,
    type = type,
    balanceTenge = balanceTenge
  )

  private fun DepositEntity.toDomain() = Deposit(
    id = id,
    title = title,
    balanceTenge = balanceTenge,
    annualRatePercent = annualRatePercent,
    isMoneyLocked = isMoneyLocked,
    isDecoy = isDecoy,
    status = status
  )

  private fun TransferEntity.toDomain() = TransferRecord(
    id = id,
    sourceAccountId = sourceAccountId,
    recipientName = recipientName,
    recipientIban = recipientIban,
    isNewRecipient = isNewRecipient,
    amountTenge = amountTenge,
    transferType = transferType,
    codeType = codeType,
    status = status,
    reviewDeadlineMillis = reviewDeadlineMillis,
    coolingOffUntilMillis = coolingOffUntilMillis,
    riskScore = riskScore,
    riskFactorsSummary = riskFactorsSummary,
    createdAtMillis = createdAtMillis,
    isDuressOperation = isDuressOperation
  )

  companion object {
    const val MAX_ATTEMPTS = 5
    const val SECOND_HAND_PAUSE_MILLIS = 10L * 60_000L
    const val SECOND_HAND_EXPIRY_MILLIS = 24L * 3_600_000L
    private val prettyJson = Json { prettyPrint = true }
  }
}
