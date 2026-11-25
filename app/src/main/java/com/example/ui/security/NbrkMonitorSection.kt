package com.example.ui.security

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.local.NbrkIncidentEntity
import com.example.ui.theme.AmberEscrow
import com.example.ui.theme.EmeraldShield
import com.example.ui.theme.MonoCodeStyle
import com.example.ui.theme.RubyRisk
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.VaultOutline
import com.example.ui.theme.VaultSurface
import com.example.ui.theme.VaultSurfaceVariant
import com.qalqan.domain.util.MoneyFormatter
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun NbrkSimulationDisclaimerBanner() {
  Surface(
    color = AmberEscrow.copy(alpha = 0.14f),
    shape = RoundedCornerShape(12.dp),
    border = BorderStroke(1.dp, AmberEscrow.copy(alpha = 0.4f)),
    modifier = Modifier
      .fillMaxWidth()
      .testTag("nbrk_simulation_disclaimer")
  ) {
    Text(
      text = "Демонстрационная симуляция. Не является интеграцией с НБРК/НПК",
      style = MaterialTheme.typography.bodyMedium,
      color = AmberEscrow,
      modifier = Modifier.padding(12.dp)
    )
  }
}

@Composable
fun NbrkIncidentCard(incident: NbrkIncidentEntity) {
  var expanded by remember { mutableStateOf(false) }
  val badgeColor = when {
    incident.riskScore >= 80 -> RubyRisk
    incident.riskScore >= 40 -> AmberEscrow
    else -> EmeraldShield
  }
  val timeStr = remember(incident.createdAtMillis) {
    DateTimeFormatter.ofPattern("dd.MM HH:mm:ss")
      .withZone(ZoneId.of("Asia/Almaty"))
      .format(Instant.ofEpochMilli(incident.createdAtMillis))
  }

  Card(
    modifier = Modifier
      .fillMaxWidth()
      .testTag("nbrk_incident_card_${incident.incidentCode}"),
    colors = CardDefaults.cardColors(containerColor = VaultSurface),
    border = BorderStroke(1.dp, badgeColor.copy(alpha = 0.45f))
  ) {
    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Text(
          text = incident.incidentCode,
          style = MonoCodeStyle,
          color = TextPrimary,
          fontWeight = FontWeight.SemiBold
        )
        Surface(
          color = badgeColor.copy(alpha = 0.18f),
          shape = RoundedCornerShape(8.dp)
        ) {
          Text(
            text = "Риск ${incident.riskScore}/100 · HTTP ${incident.httpCode}",
            style = MonoCodeStyle,
            color = badgeColor,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
          )
        }
      }

      Text(
        text = "Триггер: ${incident.triggerType.name}",
        style = MonoCodeStyle,
        color = AmberEscrow
      )
      Text(
        text = "Получатель: ${incident.recipientIban} · ${MoneyFormatter.formatTenge(incident.amountTenge)}",
        style = MaterialTheme.typography.bodyMedium,
        color = TextSecondary
      )
      Text(
        text = "$timeStr · задержка API: ${incident.latencyMs} мс",
        style = MaterialTheme.typography.bodyMedium,
        color = TextMuted
      )

      TextButton(onClick = { expanded = !expanded }) {
        Text(
          text = if (expanded) "Скрыть JSON и факторы" else "Раскрыть JSON и факторы",
          color = EmeraldShield
        )
      }

      AnimatedVisibility(visible = expanded) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
          JsonBlockCard(title = "Разбивка факторов", jsonText = incident.riskBreakdownJson)
          JsonBlockCard(title = "JSON запроса в НБРК", jsonText = incident.requestJson)
          JsonBlockCard(title = "JSON ответа НБРК", jsonText = incident.responseJson)
        }
      }
    }
  }
}

@Composable
private fun JsonBlockCard(title: String, jsonText: String) {
  Surface(
    color = VaultSurfaceVariant,
    shape = RoundedCornerShape(10.dp),
    border = BorderStroke(1.dp, VaultOutline),
    modifier = Modifier.fillMaxWidth()
  ) {
    Column(modifier = Modifier.padding(10.dp)) {
      Text(text = title, style = MaterialTheme.typography.labelLarge, color = EmeraldShield)
      Spacer(modifier = Modifier.height(4.dp))
      Text(text = jsonText, style = MonoCodeStyle, color = TextPrimary)
    }
  }
}

@Composable
fun NbrkStepAnimationCard(stage: com.example.data.simulator.NbrkTransmissionStage) {
  val (progress, stepText) = when (stage) {
    com.example.data.simulator.NbrkTransmissionStage.Idle -> 0f to ""
    com.example.data.simulator.NbrkTransmissionStage.CollectingIndicators ->
      0.35f to "1/3 · Сбор индикаторов счёта получателя..."
    is com.example.data.simulator.NbrkTransmissionStage.SendingToApi ->
      0.72f to "2/3 · Передача пакета ${stage.incidentCode} в API НБРК..."
    is com.example.data.simulator.NbrkTransmissionStage.VerdictReceived ->
      1f to "3/3 · Вердикт ${stage.incidentCode} (HTTP ${stage.httpCode}, ${stage.latencyMs} мс)"
  }

  Card(
    modifier = Modifier
      .fillMaxWidth()
      .testTag("nbrk_animation_card"),
    colors = CardDefaults.cardColors(containerColor = VaultSurfaceVariant),
    border = BorderStroke(1.dp, AmberEscrow.copy(alpha = 0.55f))
  ) {
    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
      ) {
        Icon(
          imageVector = Icons.Filled.Shield,
          contentDescription = null,
          tint = AmberEscrow
        )
        Text(
          text = "Антифрод-центр НБРК (симулятор)",
          style = MaterialTheme.typography.titleMedium,
          color = TextPrimary
        )
      }
      LinearProgressIndicator(
        progress = { progress },
        color = if (progress >= 1f) EmeraldShield else AmberEscrow,
        trackColor = VaultOutline,
        modifier = Modifier.fillMaxWidth()
      )
      Text(text = stepText, style = MonoCodeStyle, color = TextSecondary)
      if (stage is com.example.data.simulator.NbrkTransmissionStage.VerdictReceived) {
        Text(
          text = stage.statusLabel,
          style = MaterialTheme.typography.bodyMedium,
          color = TextPrimary
        )
      }
    }
  }
}

@Composable
fun AuditLogCard(entry: com.example.data.local.AuditLogEntity) {
  val timeStr = remember(entry.createdAtMillis) {
    DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss")
      .withZone(ZoneId.of("Asia/Almaty"))
      .format(Instant.ofEpochMilli(entry.createdAtMillis))
  }
  val roleColor = when (entry.actorRole) {
    com.qalqan.domain.model.UserRole.SECOND_HAND -> AmberEscrow
    com.qalqan.domain.model.UserRole.NBRK_INSPECTOR -> RubyRisk
    else -> EmeraldShield
  }
  val roleLabel = when (entry.actorRole) {
    com.qalqan.domain.model.UserRole.CLIENT_STANDARD -> "Клиент"
    com.qalqan.domain.model.UserRole.CLIENT_DURESS -> "Тревожный контур"
    com.qalqan.domain.model.UserRole.SECOND_HAND -> "Вторая рука"
    com.qalqan.domain.model.UserRole.NBRK_INSPECTOR -> "Инспектор НБРК"
  }

  Card(
    modifier = Modifier
      .fillMaxWidth()
      .testTag("audit_log_${entry.id}"),
    colors = CardDefaults.cardColors(containerColor = VaultSurface),
    border = BorderStroke(1.dp, VaultOutline)
  ) {
    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Text(
          text = entry.action,
          style = MonoCodeStyle,
          color = TextPrimary,
          fontWeight = FontWeight.SemiBold
        )
        Surface(
          color = roleColor.copy(alpha = 0.16f),
          shape = RoundedCornerShape(6.dp)
        ) {
          Text(
            text = roleLabel,
            style = MaterialTheme.typography.labelSmall,
            color = roleColor,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
          )
        }
      }
      Text(
        text = entry.details,
        style = MaterialTheme.typography.bodyMedium,
        color = TextSecondary
      )
      Text(
        text = timeStr,
        style = MonoCodeStyle,
        color = TextMuted
      )
    }
  }
}
