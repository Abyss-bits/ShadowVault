package com.example

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.presentation.MainViewModel
import com.example.ui.auth.PinLockScreen
import com.example.ui.demo.DemoPanelHost
import com.example.ui.shell.BankingShellScreen
import com.example.ui.theme.QalqanVaultTheme
import com.qalqan.domain.model.SessionMode
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    if (!BuildConfig.DEMO) {
      window.setFlags(
        WindowManager.LayoutParams.FLAG_SECURE,
        WindowManager.LayoutParams.FLAG_SECURE
      )
    }
    enableEdgeToEdge()
    setContent {
      QalqanVaultTheme {
        val viewModel: MainViewModel = hiltViewModel()
        val state by viewModel.uiState.collectAsStateWithLifecycle()

        LaunchedEffect(state.isFlagSecureEnabled) {
          if (!BuildConfig.DEMO || state.isFlagSecureEnabled) {
            window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
          } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
          }
        }

        Crossfade(
          targetState = state.sessionMode,
          animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
          label = "session_mode_crossfade"
        ) { mode ->
          if (mode == SessionMode.LOCKED) {
            PinLockScreen(
              state = state,
              onDigit = viewModel::onPinDigit,
              onBackspace = viewModel::onPinBackspace,
              onLogoLongPress = viewModel::onLogoLongPress,
              onLogoTripleTap = viewModel::onLogoTripleTap
            )
          } else {
            BankingShellScreen(
              state = state,
              onLockSession = viewModel::onLockSession,
              onLogoTripleTap = viewModel::onLogoTripleTap,
              onConsumeRequestedTabTarget = viewModel::consumeRequestedTabTarget,
              onSubmitTransfer = viewModel::onSubmitTransfer,
              onClearTransferResult = viewModel::clearTransferResult,
              onSubmitEarlyDepositWithdrawal = viewModel::onSubmitEarlyDepositWithdrawal,
              onClearDepositResult = viewModel::clearDepositActionResult,
              onApproveSecondHand = viewModel::onApproveSecondHand,
              onRejectSecondHandAsFraud = viewModel::onRejectSecondHandAsFraud,
              onEscalateSecondHandToBackup = viewModel::onEscalateSecondHandToBackup,
              onClearSecondHandResult = viewModel::clearSecondHandActionResult,
              onUpdateSecurityPolicy = viewModel::onUpdateSecurityPolicy,
              onUpdateLoginPins = viewModel::onUpdateLoginPins,
              onUpdateTransferCodes = viewModel::onUpdateTransferCodes,
              onUpdateSecondHandPin = viewModel::onUpdateSecondHandPin,
              onClearSecurityUpdateResult = viewModel::clearSecurityUpdateResult,
              onRefreshTimedActions = viewModel::onRefreshTimedActions
            )
          }
        }

        DemoPanelHost(
          state = state,
          onDismiss = viewModel::dismissDemoPanel,
          onAdvanceMinutes = viewModel::onAdvanceTimeMinutes,
          onAdvanceHours = viewModel::onAdvanceTimeHours,
          onAdvanceDays = viewModel::onAdvanceTimeDays,
          onAdvanceBusinessDays = viewModel::onAdvanceTimeBusinessDays,
          onSwitchRole = viewModel::onSwitchDemoRole,
          onRunScenario1 = viewModel::onRunScenario1Robbery,
          onRunScenario2 = viewModel::onRunScenario2PhoneBlackmail,
          onRunScenario3 = viewModel::onRunScenario3DepositSecondHand,
          onToggleFlagSecure = viewModel::onToggleFlagSecure,
          onResetDemoData = viewModel::onResetDemoData
        )
      }
    }
  }
}
