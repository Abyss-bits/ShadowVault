package com.example.ui.demo

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Science
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.presentation.MainUiState
import com.example.ui.theme.AmberEscrow
import com.example.ui.theme.EmeraldShield
import com.example.ui.theme.MonoCodeStyle
import com.example.ui.theme.ObsidianBackground
import com.example.ui.theme.RubyRisk
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.VaultOutline
import com.example.ui.theme.VaultSurface
import com.example.ui.theme.VaultSurfaceVariant
import com.qalqan.domain.model.SessionMode
import com.qalqan.domain.model.UserRole
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
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
) {
  if (!state.showDemoPanel) return

  val clockFormatted = remember(state.currentClockMillis) {
    DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm (EEE)")
      .withZone(ZoneId.of("Asia/Almaty"))
      .format(Instant.ofEpochMilli(state.currentClockMillis))
  }

  ModalBottomSheet(
    onDismissRequest = onDismiss,
    containerColor = VaultSurface
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .verticalScroll(rememberScrollState())
        .padding(horizontal = 20.dp, vertical = 8.dp)
        .testTag("demo_panel_sheet"),
      verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
      ) {
        Icon(Icons.Filled.Science, contentDescription = null, tint = EmeraldShield)
        Text(
          text = "Скрытая демо-панель Qalqan Safe",
          style = MaterialTheme.typography.titleLarge,
          color = TextPrimary
        )
      }

      Text(
        text = "Виртуальные часы: $clockFormatted",
        style = MonoCodeStyle,
        color = EmeraldShield,
        modifier = Modifier.testTag("demo_clock_label")
      )

      state.demoScenarioMessage?.let { msg ->
        Card(
          colors = CardDefaults.cardColors(containerColor = VaultSurfaceVariant),
          border = BorderStroke(1.dp, EmeraldShield.copy(alpha = 0.5f)),
          modifier = Modifier.fillMaxWidth()
        ) {
          Text(
            text = msg,
            style = MaterialTheme.typography.bodyMedium,
            color = TextPrimary,
            modifier = Modifier.padding(12.dp)
          )
        }
      }

      // 1. Перемотка времени (SimulatedClock)
      Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
          Icon(Icons.Filled.FastForward, contentDescription = null, tint = AmberEscrow, modifier = Modifier.size(18.dp))
          Text("Перемотка времени", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
        }
        FlowRow(
          horizontalArrangement = Arrangement.spacedBy(8.dp),
          verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
          OutlinedButton(
            onClick = { onAdvanceMinutes(15) },
            border = BorderStroke(1.dp, EmeraldShield.copy(alpha = 0.6f)),
            modifier = Modifier.testTag("demo_time_15m")
          ) {
            Text("+15 мин", color = EmeraldShield)
          }
          OutlinedButton(
            onClick = { onAdvanceHours(1) },
            border = BorderStroke(1.dp, EmeraldShield.copy(alpha = 0.6f)),
            modifier = Modifier.testTag("demo_time_1h")
          ) {
            Text("+1 час", color = EmeraldShield)
          }
          OutlinedButton(
            onClick = { onAdvanceDays(1) },
            border = BorderStroke(1.dp, AmberEscrow.copy(alpha = 0.6f)),
            modifier = Modifier.testTag("demo_time_1d")
          ) {
            Text("+1 день", color = AmberEscrow)
          }
          OutlinedButton(
            onClick = { onAdvanceBusinessDays(5) },
            border = BorderStroke(1.dp, RubyRisk.copy(alpha = 0.6f)),
            modifier = Modifier.testTag("demo_time_5bd")
          ) {
            Text("+5 раб. дн.", color = RubyRisk)
          }
        }
      }

      // 2. Переключатель ролей
      Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Переключение роли симулятора", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
        FlowRow(
          horizontalArrangement = Arrangement.spacedBy(8.dp),
          verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
          val roles = listOf(
            UserRole.CLIENT_STANDARD to "Клиент",
            UserRole.CLIENT_DURESS to "Тревожный",
            UserRole.SECOND_HAND to "Вторая рука",
            UserRole.NBRK_INSPECTOR to "Инспектор НБРК"
          )
          roles.forEach { (role, label) ->
            val selected = when (role) {
              UserRole.CLIENT_DURESS -> state.sessionMode == SessionMode.DURESS
              else -> state.sessionMode == SessionMode.STANDARD && state.activeRole == role
            }
            FilterChip(
              selected = selected,
              onClick = { onSwitchRole(role) },
              label = { Text(label) },
              colors = FilterChipDefaults.filterChipColors(
                selectedContainerColor = EmeraldShield.copy(alpha = 0.2f),
                selectedLabelColor = EmeraldShield
              ),
              modifier = Modifier.testTag("demo_role_${role.name}")
            )
          }
        }
      }

      // 3. 3 сквозных сценария
      Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Сквозные сценарии (< 2 мин)", style = MaterialTheme.typography.titleMedium, color = TextPrimary)

        ScenarioCard(
          title = "1. Очное ограбление (Duress PIN)",
          description = "Вход по тревожному ПИН (48 500 ₸), скрытый перевод 20 000 ₸ через ESCROW_HOLD и тихий сигнал НБРК.",
          isRunning = state.isDemoScenarioRunning,
          tag = "demo_scenario_1",
          accent = EmeraldShield,
          onClick = onRunScenario1
        )
        ScenarioCard(
          title = "2. Шантаж по телефону (Soft-Escrow)",
          description = "Код 9999 на 350 000 ₸ замораживает средства на 5 раб. дн. и открывает Монитор НБРК API.",
          isRunning = state.isDemoScenarioRunning,
          tag = "demo_scenario_2",
          accent = RubyRisk,
          onClick = onRunScenario2
        )
        ScenarioCard(
          title = "3. Вывод депозита через «Вторую руку»",
          description = "Досрочное снятие 1 200 000 ₸ новому получателю и переход в кабинет «Второй руки».",
          isRunning = state.isDemoScenarioRunning,
          tag = "demo_scenario_3",
          accent = AmberEscrow,
          onClick = onRunScenario3
        )
      }

      // 4. Защита окна и сброс
      Card(
        colors = CardDefaults.cardColors(containerColor = VaultSurfaceVariant),
        border = BorderStroke(1.dp, VaultOutline),
        modifier = Modifier.fillMaxWidth()
      ) {
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 10.dp),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Column(modifier = Modifier.weight(1f)) {
            Text("FLAG_SECURE (защита экрана)", style = MaterialTheme.typography.bodyLarge, color = TextPrimary)
            Text(
              text = "В release включён всегда; в эмуляторе может давать чёрный экран при захвате.",
              style = MaterialTheme.typography.bodySmall,
              color = TextMuted
            )
          }
          Switch(
            checked = state.isFlagSecureEnabled,
            onCheckedChange = onToggleFlagSecure,
            colors = SwitchDefaults.colors(checkedTrackColor = EmeraldShield),
            modifier = Modifier.testTag("demo_flag_secure_switch")
          )
        }
      }

      Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
        Button(
          onClick = onResetDemoData,
          colors = ButtonDefaults.buttonColors(containerColor = VaultSurfaceVariant),
          modifier = Modifier
            .weight(1f)
            .height(48.dp)
            .testTag("demo_reset_button")
        ) {
          Icon(Icons.Filled.Refresh, contentDescription = null, tint = EmeraldShield)
          Spacer(modifier = Modifier.size(6.dp))
          Text("Сбросить БД", color = EmeraldShield)
        }
        TextButton(
          onClick = onDismiss,
          modifier = Modifier
            .height(48.dp)
            .testTag("demo_close_button")
        ) {
          Text("Закрыть", color = TextSecondary)
        }
      }
      Spacer(modifier = Modifier.height(14.dp))
    }
  }
}

@Composable
private fun ScenarioCard(
  title: String,
  description: String,
  isRunning: Boolean,
  tag: String,
  accent: androidx.compose.ui.graphics.Color,
  onClick: () -> Unit
) {
  Card(
    onClick = { if (!isRunning) onClick() },
    colors = CardDefaults.cardColors(containerColor = VaultSurfaceVariant),
    border = BorderStroke(1.dp, accent.copy(alpha = 0.45f)),
    modifier = Modifier
      .fillMaxWidth()
      .testTag(tag)
  ) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(12.dp),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically
    ) {
      Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall, color = TextPrimary, fontWeight = FontWeight.SemiBold)
        Text(description, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
      }
      Spacer(modifier = Modifier.size(8.dp))
      if (isRunning) {
        CircularProgressIndicator(color = accent, modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
      } else {
        Icon(Icons.Filled.PlayArrow, contentDescription = "Запустить сценарий", tint = accent)
      }
    }
  }
}
