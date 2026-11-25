package com.example.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.BuildConfig
import com.example.data.local.AuditLogEntity
import com.example.data.local.KnownRecipientEntity
import com.example.data.local.NbrkIncidentEntity
import com.example.data.local.SecondHandRequestEntity
import com.example.data.repository.QalqanRepository
import com.example.data.repository.SecondHandActionResult
import com.example.data.repository.SecurityUpdateResult
import com.example.data.repository.TransferSubmitResult
import com.example.data.simulator.NbrkTransmissionStage
import com.example.demo.DemoScenarioBridge
import com.qalqan.domain.clock.BusinessCalendar
import com.qalqan.domain.clock.SimulatedClock
import com.qalqan.domain.model.Account
import com.qalqan.domain.model.Deposit
import com.qalqan.domain.model.PinAuthResult
import com.qalqan.domain.model.SecuritySummary
import com.qalqan.domain.model.SessionMode
import com.qalqan.domain.model.TransferRecord
import com.qalqan.domain.model.UserRole
import com.qalqan.domain.validation.KzIdentifierValidator
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class MainUiState(
  val isReady: Boolean = false,
  val sessionMode: SessionMode = SessionMode.LOCKED,
  val activeRole: UserRole = UserRole.CLIENT_STANDARD,
  val accounts: List<Account> = emptyList(),
  val deposits: List<Deposit> = emptyList(),
  val transfers: List<TransferRecord> = emptyList(),
  val trustedRecipients: List<KnownRecipientEntity> = emptyList(),
  val securitySummary: SecuritySummary? = null,
  val nbrkIncidents: List<NbrkIncidentEntity> = emptyList(),
  val secondHandRequests: List<SecondHandRequestEntity> = emptyList(),
  val auditLogs: List<AuditLogEntity> = emptyList(),
  val nbrkTransmissionStage: NbrkTransmissionStage = NbrkTransmissionStage.Idle,
  val isTransferSubmitting: Boolean = false,
  val transferResult: TransferSubmitResult? = null,
  val isDepositActionSubmitting: Boolean = false,
  val depositActionResult: TransferSubmitResult? = null,
  val secondHandActionResult: SecondHandActionResult? = null,
  val securityUpdateResult: SecurityUpdateResult? = null,
  val pinDigits: String = "",
  val pinError: String? = null,
  val lockedUntilMillis: Long = 0L,
  val currentClockMillis: Long = 0L,
  val showDemoHint: Boolean = false,
  val showDemoPanel: Boolean = false,
  val demoDropperIban: String? = null,
  val isFlagSecureEnabled: Boolean = !BuildConfig.DEMO,
  val isDemoScenarioRunning: Boolean = false,
  val demoScenarioMessage: String? = null,
  val requestedTabTarget: DemoTabTarget? = null
)

private data class LocalUiSlice(
  val pinDigits: String = "",
  val pinError: String? = null,
  val lockedUntilMillis: Long = 0L,
  val showDemoHint: Boolean = false,
  val showDemoPanel: Boolean = false,
  val isReady: Boolean = false,
  val isTransferSubmitting: Boolean = false,
  val transferResult: TransferSubmitResult? = null,
  val isDepositActionSubmitting: Boolean = false,
  val depositActionResult: TransferSubmitResult? = null,
  val secondHandActionResult: SecondHandActionResult? = null,
  val securityUpdateResult: SecurityUpdateResult? = null,
  val clockVersion: Long = 0L,
  val isFlagSecureEnabled: Boolean = !BuildConfig.DEMO,
  val isDemoScenarioRunning: Boolean = false,
  val demoScenarioMessage: String? = null,
  val requestedTabTarget: DemoTabTarget? = null
)

@HiltViewModel
class MainViewModel @Inject constructor(
  private val repository: QalqanRepository,
  private val clock: SimulatedClock,
  private val businessCalendar: BusinessCalendar
) : ViewModel() {

  private val localUiState = MutableStateFlow(LocalUiSlice())

  private val domainCombined = combine(
    repository.sessionMode,
    repository.activeRole,
    repository.observeVisibleAccounts(),
    repository.observeVisibleDeposits(),
    repository.observeVisibleTransfers()
  ) { mode, role, accounts, deposits, transfers ->
    DomainSlice(mode, role, accounts, deposits, transfers)
  }

  private val protectionCombined = combine(
    repository.observeSecuritySummary(),
    repository.observeNbrkIncidents(),
    repository.observeSecondHandRequests(),
    repository.observeTrustedRecipients(),
    repository.observeAuditLogs(),
    repository.nbrkTransmissionStage
  ) { values ->
    @Suppress("UNCHECKED_CAST")
    ProtectionSlice(
      security = values[0] as SecuritySummary?,
      incidents = values[1] as List<NbrkIncidentEntity>,
      requests = values[2] as List<SecondHandRequestEntity>,
      trusted = values[3] as List<KnownRecipientEntity>,
      auditLogs = values[4] as List<AuditLogEntity>,
      nbrkStage = values[5] as NbrkTransmissionStage
    )
  }

  val uiState: StateFlow<MainUiState> = combine(
    domainCombined,
    protectionCombined,
    localUiState
  ) { domain, protection, local ->
    val dropperIban = if (BuildConfig.DEMO && domain.mode == SessionMode.STANDARD) {
      KzIdentifierValidator.buildValidKzIban("965", "0006660006661")
    } else null

    MainUiState(
      isReady = local.isReady,
      sessionMode = domain.mode,
      activeRole = domain.role,
      accounts = domain.accounts,
      deposits = domain.deposits,
      transfers = domain.transfers,
      trustedRecipients = protection.trusted,
      securitySummary = protection.security,
      nbrkIncidents = protection.incidents,
      secondHandRequests = protection.requests,
      auditLogs = protection.auditLogs,
      nbrkTransmissionStage = if (domain.mode == SessionMode.STANDARD) {
        protection.nbrkStage
      } else {
        NbrkTransmissionStage.Idle
      },
      isTransferSubmitting = local.isTransferSubmitting,
      transferResult = local.transferResult,
      isDepositActionSubmitting = local.isDepositActionSubmitting,
      depositActionResult = local.depositActionResult,
      secondHandActionResult = local.secondHandActionResult,
      securityUpdateResult = local.securityUpdateResult,
      pinDigits = local.pinDigits,
      pinError = local.pinError,
      lockedUntilMillis = local.lockedUntilMillis,
      currentClockMillis = clock.nowMillis(),
      showDemoHint = BuildConfig.DEMO && local.showDemoHint,
      showDemoPanel = BuildConfig.DEMO && local.showDemoPanel,
      demoDropperIban = dropperIban,
      isFlagSecureEnabled = if (!BuildConfig.DEMO) true else local.isFlagSecureEnabled,
      isDemoScenarioRunning = BuildConfig.DEMO && local.isDemoScenarioRunning,
      demoScenarioMessage = if (BuildConfig.DEMO) local.demoScenarioMessage else null,
      requestedTabTarget = if (BuildConfig.DEMO) local.requestedTabTarget else null
    )
  }.stateIn(
    scope = viewModelScope,
    started = SharingStarted.WhileSubscribed(5_000L),
    initialValue = MainUiState()
  )

  init {
    viewModelScope.launch {
      repository.initializeDemoData()
      localUiState.update { it.copy(isReady = true) }
    }
  }

  fun onPinDigit(digit: Char) {
    if (!digit.isDigit()) return
    val current = localUiState.value
    if (current.lockedUntilMillis > clock.nowMillis()) return
    if (current.pinDigits.length >= 4) return

    val updatedDigits = current.pinDigits + digit
    localUiState.update { it.copy(pinDigits = updatedDigits, pinError = null) }
    if (updatedDigits.length == 4) {
      submitPin(updatedDigits)
    }
  }

  fun onPinBackspace() {
    localUiState.update { state ->
      if (state.pinDigits.isEmpty()) state
      else state.copy(pinDigits = state.pinDigits.dropLast(1), pinError = null)
    }
  }

  private fun submitPin(rawPin: String) {
    viewModelScope.launch {
      when (val result = repository.authenticatePin(rawPin)) {
        is PinAuthResult.Success -> {
          localUiState.update {
            it.copy(
              pinDigits = "",
              pinError = null,
              lockedUntilMillis = 0L,
              showDemoHint = false,
              transferResult = null
            )
          }
        }
        is PinAuthResult.Invalid -> {
          localUiState.update {
            it.copy(
              pinDigits = "",
              pinError = "Неверный ПИН-код. Осталось попыток: ${result.remainingAttempts}"
            )
          }
        }
        is PinAuthResult.LockedOut -> {
          val secondsLeft = ((result.lockedUntilMillis - clock.nowMillis()) / 1_000L).coerceAtLeast(1L)
          localUiState.update {
            it.copy(
              pinDigits = "",
              lockedUntilMillis = result.lockedUntilMillis,
              pinError = "Превышен лимит попыток. Повторите через $secondsLeft сек."
            )
          }
        }
      }
    }
  }

  fun onSubmitTransfer(
    recipientName: String,
    recipientIban: String,
    amountTenge: Long,
    transferCode: String,
    secondConfirmationProvided: Boolean = false
  ) {
    if (localUiState.value.isTransferSubmitting) return
    viewModelScope.launch {
      localUiState.update { it.copy(isTransferSubmitting = true, transferResult = null) }
      repository.resetNbrTransmissionUi()
      val result = repository.submitTransfer(
        recipientName = recipientName,
        recipientIban = recipientIban,
        amountTenge = amountTenge,
        transferCode = transferCode,
        secondConfirmationProvided = secondConfirmationProvided
      )
      localUiState.update {
        it.copy(isTransferSubmitting = false, transferResult = result)
      }
    }
  }

  fun clearTransferResult() {
    repository.resetNbrTransmissionUi()
    localUiState.update { it.copy(transferResult = null) }
  }

  fun onSubmitEarlyDepositWithdrawal(
    depositId: String,
    recipientName: String,
    recipientIban: String,
    amountTenge: Long,
    transferCode: String,
    secondConfirmationProvided: Boolean = false
  ) {
    if (localUiState.value.isDepositActionSubmitting) return
    viewModelScope.launch {
      localUiState.update { it.copy(isDepositActionSubmitting = true, depositActionResult = null) }
      repository.resetNbrTransmissionUi()
      val result = repository.submitEarlyDepositWithdrawal(
        depositId = depositId,
        recipientName = recipientName,
        recipientIban = recipientIban,
        amountTenge = amountTenge,
        transferCode = transferCode,
        secondConfirmationProvided = secondConfirmationProvided
      )
      localUiState.update {
        it.copy(isDepositActionSubmitting = false, depositActionResult = result)
      }
    }
  }

  fun clearDepositActionResult() {
    repository.resetNbrTransmissionUi()
    localUiState.update { it.copy(depositActionResult = null) }
  }

  fun onApproveSecondHand(requestId: String, secondHandPin: String, comment: String = "") {
    viewModelScope.launch {
      val result = repository.approveSecondHandRequest(requestId, secondHandPin, comment)
      localUiState.update {
        it.copy(secondHandActionResult = result, clockVersion = it.clockVersion + 1)
      }
    }
  }

  fun onRejectSecondHandAsFraud(requestId: String, comment: String = "") {
    viewModelScope.launch {
      repository.resetNbrTransmissionUi()
      val result = repository.rejectSecondHandRequestAsFraud(requestId, comment)
      localUiState.update {
        it.copy(secondHandActionResult = result, clockVersion = it.clockVersion + 1)
      }
    }
  }

  fun onEscalateSecondHandToBackup(requestId: String) {
    viewModelScope.launch {
      val result = repository.escalateSecondHandToBackup(requestId)
      localUiState.update { it.copy(secondHandActionResult = result) }
    }
  }

  fun clearSecondHandActionResult() {
    repository.resetNbrTransmissionUi()
    localUiState.update { it.copy(secondHandActionResult = null) }
  }

  fun onUpdateSecurityPolicy(
    thresholdTenge: Long,
    isolatedSafeTargetTenge: Long,
    coolingOffMinutes: Int,
    escrowBusinessDays: Int,
    primaryTrustedName: String,
    primaryTrustedPhone: String,
    backupTrustedName: String,
    backupTrustedPhone: String
  ) {
    viewModelScope.launch {
      val result = repository.updateSecurityPolicy(
        thresholdTenge = thresholdTenge,
        isolatedSafeTargetTenge = isolatedSafeTargetTenge,
        coolingOffMinutes = coolingOffMinutes,
        escrowBusinessDays = escrowBusinessDays,
        primaryTrustedName = primaryTrustedName,
        primaryTrustedPhone = primaryTrustedPhone,
        backupTrustedName = backupTrustedName,
        backupTrustedPhone = backupTrustedPhone
      )
      localUiState.update { it.copy(securityUpdateResult = result) }
    }
  }

  fun onUpdateLoginPins(currentPin: String, newStandardPin: String, newDuressPin: String) {
    viewModelScope.launch {
      val result = repository.updateLoginPins(currentPin, newStandardPin, newDuressPin)
      localUiState.update { it.copy(securityUpdateResult = result) }
    }
  }

  fun onUpdateTransferCodes(currentPin: String, newStandardCode: String, newDuressCode: String) {
    viewModelScope.launch {
      val result = repository.updateTransferCodes(currentPin, newStandardCode, newDuressCode)
      localUiState.update { it.copy(securityUpdateResult = result) }
    }
  }

  fun onUpdateSecondHandPin(currentPin: String, newSecondHandPin: String) {
    viewModelScope.launch {
      val result = repository.updateSecondHandPin(currentPin, newSecondHandPin)
      localUiState.update { it.copy(securityUpdateResult = result) }
    }
  }

  fun clearSecurityUpdateResult() {
    localUiState.update { it.copy(securityUpdateResult = null) }
  }

  fun onRefreshTimedActions() {
    viewModelScope.launch {
      repository.resolveTimedActions()
      localUiState.update { it.copy(clockVersion = it.clockVersion + 1) }
    }
  }

  fun onLogoLongPress() {
    if (!BuildConfig.DEMO) return
    localUiState.update { it.copy(showDemoHint = !it.showDemoHint) }
  }

  fun onLogoTripleTap() {
    if (!BuildConfig.DEMO) return
    localUiState.update { it.copy(showDemoPanel = !it.showDemoPanel) }
  }

  fun dismissDemoPanel() {
    localUiState.update { it.copy(showDemoPanel = false) }
  }

  fun onLockSession() {
    repository.lockSession()
    localUiState.update {
      it.copy(
        pinDigits = "",
        pinError = null,
        showDemoPanel = false,
        transferResult = null,
        depositActionResult = null,
        secondHandActionResult = null,
        securityUpdateResult = null
      )
    }
  }

  fun onToggleFlagSecure(enabled: Boolean) {
    if (!BuildConfig.DEMO) return
    localUiState.update { it.copy(isFlagSecureEnabled = enabled) }
  }

  fun onAdvanceTimeMinutes(minutes: Long) {
    if (!BuildConfig.DEMO) return
    viewModelScope.launch {
      clock.advanceByMinutes(minutes)
      val resolved = repository.resolveTimedActions()
      localUiState.update {
        it.copy(
          clockVersion = it.clockVersion + 1,
          demoScenarioMessage = "Время сдвинуто на +$minutes мин. Завершено ожиданий: $resolved."
        )
      }
    }
  }

  fun onAdvanceTimeHours(hours: Long) {
    if (!BuildConfig.DEMO) return
    viewModelScope.launch {
      clock.advanceByHours(hours)
      val resolved = repository.resolveTimedActions()
      localUiState.update {
        it.copy(
          clockVersion = it.clockVersion + 1,
          demoScenarioMessage = "Время сдвинуто на +$hours ч. Завершено ожиданий: $resolved."
        )
      }
    }
  }

  fun onAdvanceTimeDays(days: Long) {
    if (!BuildConfig.DEMO) return
    viewModelScope.launch {
      clock.advanceByDays(days)
      val resolved = repository.resolveTimedActions()
      localUiState.update {
        it.copy(
          clockVersion = it.clockVersion + 1,
          demoScenarioMessage = "Время сдвинуто на +$days дн. Завершено ожиданий: $resolved."
        )
      }
    }
  }

  fun onAdvanceTimeBusinessDays(businessDays: Int) {
    if (!BuildConfig.DEMO) return
    viewModelScope.launch {
      val calDays = businessCalendar.businessDaysToCalendarDays(clock.localDate(), businessDays)
      clock.advanceByDays(calDays.toLong())
      val resolved = repository.resolveTimedActions()
      localUiState.update {
        it.copy(
          clockVersion = it.clockVersion + 1,
          demoScenarioMessage = "Прошло +$businessDays раб. дн. ($calDays календ.). Завершено ожиданий: $resolved."
        )
      }
    }
  }

  fun onSwitchDemoRole(role: UserRole) {
    if (!BuildConfig.DEMO || !DemoScenarioBridge.IS_AVAILABLE) return
    viewModelScope.launch {
      val target = DemoScenarioBridge.switchRole(repository, role)
      localUiState.update {
        it.copy(
          pinDigits = "",
          pinError = null,
          showDemoPanel = false,
          requestedTabTarget = target,
          clockVersion = it.clockVersion + 1
        )
      }
    }
  }

  fun onRunScenario1Robbery() = runScenario {
    DemoScenarioBridge.runScenario1Robbery(repository)
  }

  fun onRunScenario2PhoneBlackmail() = runScenario {
    DemoScenarioBridge.runScenario2PhoneBlackmail(repository)
  }

  fun onRunScenario3DepositSecondHand() = runScenario {
    DemoScenarioBridge.runScenario3DepositSecondHand(repository)
  }

  private fun runScenario(block: suspend () -> DemoScenarioOutcome?) {
    if (!BuildConfig.DEMO || !DemoScenarioBridge.IS_AVAILABLE) return
    if (localUiState.value.isDemoScenarioRunning) return
    viewModelScope.launch {
      localUiState.update { it.copy(isDemoScenarioRunning = true, demoScenarioMessage = null) }
      val outcome = block()
      localUiState.update {
        it.copy(
          isDemoScenarioRunning = false,
          pinDigits = "",
          pinError = null,
          lockedUntilMillis = 0L,
          showDemoPanel = false,
          demoScenarioMessage = outcome?.summaryMessage,
          requestedTabTarget = outcome?.targetTab,
          clockVersion = it.clockVersion + 1
        )
      }
    }
  }

  fun consumeRequestedTabTarget() {
    localUiState.update { it.copy(requestedTabTarget = null) }
  }

  fun clearDemoScenarioMessage() {
    localUiState.update { it.copy(demoScenarioMessage = null) }
  }

  fun onResetDemoData() {
    if (!BuildConfig.DEMO) return
    viewModelScope.launch {
      repository.resetDemoData()
      localUiState.update {
        it.copy(
          pinDigits = "",
          pinError = null,
          lockedUntilMillis = 0L,
          showDemoPanel = false,
          transferResult = null,
          depositActionResult = null,
          secondHandActionResult = null,
          securityUpdateResult = null,
          demoScenarioMessage = "Демо-данные сброшены к исходному состоянию.",
          requestedTabTarget = null,
          clockVersion = it.clockVersion + 1
        )
      }
    }
  }

  private data class DomainSlice(
    val mode: SessionMode,
    val role: UserRole,
    val accounts: List<Account>,
    val deposits: List<Deposit>,
    val transfers: List<TransferRecord>
  )

  private data class ProtectionSlice(
    val security: SecuritySummary?,
    val incidents: List<NbrkIncidentEntity>,
    val requests: List<SecondHandRequestEntity>,
    val trusted: List<KnownRecipientEntity>,
    val auditLogs: List<AuditLogEntity>,
    val nbrkStage: NbrkTransmissionStage
  )
}
