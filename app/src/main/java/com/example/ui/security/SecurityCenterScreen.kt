package com.example.ui.security

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.data.simulator.NbrkTransmissionStage
import com.example.presentation.MainUiState
import com.example.ui.theme.AmberEscrow
import com.example.ui.theme.EmeraldShield
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.VaultOutline
import com.example.ui.theme.VaultSurface
import com.qalqan.domain.model.SecondHandStatus

@Composable
fun SecurityCenterScreen(
  state: MainUiState,
  initialSegment: Int = 0,
  onApproveSecondHand: (String, String, String) -> Unit,
  onRejectSecondHandAsFraud: (String, String) -> Unit,
  onEscalateSecondHandToBackup: (String) -> Unit,
  onClearSecondHandResult: () -> Unit,
  onUpdateSecurityPolicy: (Long, Long, Int, Int, String, String, String, String) -> Unit,
  onUpdateLoginPins: (String, String, String) -> Unit,
  onUpdateTransferCodes: (String, String, String) -> Unit,
  onUpdateSecondHandPin: (String, String) -> Unit,
  onClearSecurityUpdateResult: () -> Unit,
  onRefreshTimedActions: () -> Unit
) {
  var selectedSegment by remember(initialSegment) { mutableIntStateOf(initialSegment) }
  val pendingSecondHandCount = remember(state.secondHandRequests) {
    state.secondHandRequests.count { it.status == SecondHandStatus.PENDING }
  }

  LaunchedEffect(Unit) {
    onRefreshTimedActions()
  }

  val segmentLabels = listOf(
    if (pendingSecondHandCount > 0) "Вторая рука ($pendingSecondHandCount)" else "Вторая рука",
    "НБРК API (${state.nbrkIncidents.size})",
    "Настройки",
    "Аудит (${state.auditLogs.size})"
  )

  LazyColumn(
    contentPadding = PaddingValues(16.dp),
    verticalArrangement = Arrangement.spacedBy(12.dp),
    modifier = Modifier
      .fillMaxSize()
      .testTag("security_center_tab")
  ) {
    item {
      NbrkSimulationDisclaimerBanner()
    }

    item {
      Text("Центр защиты", style = MaterialTheme.typography.headlineSmall, color = TextPrimary)
      Spacer(modifier = Modifier.height(8.dp))
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
      ) {
        segmentLabels.forEachIndexed { idx, label ->
          val activeColor = if (idx == 0 && pendingSecondHandCount > 0) AmberEscrow else EmeraldShield
          FilterChip(
            selected = selectedSegment == idx,
            onClick = { selectedSegment = idx },
            label = { Text(label, maxLines = 1) },
            colors = FilterChipDefaults.filterChipColors(
              selectedContainerColor = activeColor.copy(alpha = 0.2f),
              selectedLabelColor = activeColor
            ),
            modifier = Modifier.testTag("security_segment_$idx")
          )
        }
      }
    }

    if (state.nbrkTransmissionStage !is NbrkTransmissionStage.Idle) {
      item {
        NbrkStepAnimationCard(stage = state.nbrkTransmissionStage)
      }
    }

    when (selectedSegment) {
      0 -> {
        state.secondHandActionResult?.let { res ->
          item {
            SecondHandActionBanner(result = res, onDismiss = onClearSecondHandResult)
          }
        }
        if (state.secondHandRequests.isEmpty()) {
          item {
            EmptySectionCard(
              text = "Активных и прошлых заявок «Второй руки» нет. При досрочном закрытии депозита Money Lock и переводе крупной суммы новому получателю заявка появится здесь."
            )
          }
        } else {
          items(state.secondHandRequests, key = { it.id }) { req ->
            SecondHandRequestCard(
              request = req,
              currentClockMillis = state.currentClockMillis,
              onApproveWithPin = onApproveSecondHand,
              onRejectAsFraud = onRejectSecondHandAsFraud,
              onEscalateBackup = onEscalateSecondHandToBackup
            )
          }
        }
      }

      1 -> {
        if (state.nbrkIncidents.isEmpty()) {
          item {
            EmptySectionCard(
              text = "Инцидентов пока нет. Отправьте перевод по тревожному коду, на счёт из стоп-листа или отклоните заявку «Второй рукой», чтобы увидеть обмен с симулятором НБРК."
            )
          }
        } else {
          items(state.nbrkIncidents, key = { it.id }) { incident ->
            NbrkIncidentCard(incident = incident)
          }
        }
      }

      2 -> {
        state.securityUpdateResult?.let { res ->
          item {
            SecurityUpdateBanner(result = res, onDismiss = onClearSecurityUpdateResult)
          }
        }
        state.securitySummary?.let { summary ->
          item {
            SecurityPolicyFormCard(
              summary = summary,
              onSavePolicy = onUpdateSecurityPolicy
            )
          }
        }
        item {
          SecurityCredentialsRotationCard(
            onUpdateLoginPins = onUpdateLoginPins,
            onUpdateTransferCodes = onUpdateTransferCodes,
            onUpdateSecondHandPin = onUpdateSecondHandPin
          )
        }
      }

      else -> {
        if (state.auditLogs.isEmpty()) {
          item {
            EmptySectionCard(text = "Записи журнала аудита отсутствуют.")
          }
        } else {
          items(state.auditLogs, key = { it.id }) { entry ->
            AuditLogCard(entry = entry)
          }
        }
      }
    }
  }
}

@Composable
private fun EmptySectionCard(text: String) {
  Card(
    modifier = Modifier.fillMaxWidth(),
    colors = CardDefaults.cardColors(containerColor = VaultSurface),
    border = BorderStroke(1.dp, VaultOutline)
  ) {
    Text(
      text = text,
      style = MaterialTheme.typography.bodyMedium,
      color = TextSecondary,
      modifier = Modifier.padding(16.dp)
    )
  }
}
