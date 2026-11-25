package com.example.ui.demo

import androidx.compose.runtime.Composable
import com.example.presentation.MainUiState
import com.qalqan.domain.model.UserRole

@Composable
fun DemoPanelHost(
  state: MainUiState,
  onDismiss: () -> Unit,
  onAdvanceMinutes: (Long) -> Unit,
  onAdvanceHours: (Long) -> Unit,
  onAdvanceDays: (Long) -> Unit,
  onAdvanceBusinessDays: (Int) -> Unit,
  onSwitchRole: (UserRole) -> Unit,
  onRunScenario1: () -> Unit,
  onRunScenario2: () -> Unit,
  onRunScenario3: () -> Unit,
  onToggleFlagSecure: (Boolean) -> Unit,
  onResetDemoData: () -> Unit
) = Unit
