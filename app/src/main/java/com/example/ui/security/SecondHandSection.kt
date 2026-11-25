package com.example.ui.security

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Handshake
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.example.data.local.SecondHandRequestEntity
import com.example.data.repository.SecondHandActionResult
import com.example.data.simulator.NbrkFactorScoreDto
import com.example.ui.theme.AmberEscrow
import com.example.ui.theme.EmeraldShield
import com.example.ui.theme.MonoAmountLarge
import com.example.ui.theme.MonoCodeStyle
import com.example.ui.theme.ObsidianBackground
import com.example.ui.theme.RubyRisk
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.VaultOutline
import com.example.ui.theme.VaultSurface
import com.example.ui.theme.VaultSurfaceVariant
import com.qalqan.domain.model.SecondHandStatus
import com.qalqan.domain.util.MoneyFormatter
import kotlinx.serialization.json.Json

@Composable
fun SecondHandActionBanner(
  result: SecondHandActionResult,
  onDismiss: () -> Unit
) {
  val isSuccess = result is SecondHandActionResult.Success
  val color = if (isSuccess) EmeraldShield else RubyRisk
  val title = if (isSuccess) "Решение «Второй руки»" else "Действие отклонено"
  val message = when (result) {
    is SecondHandActionResult.Success -> result.message
    is SecondHandActionResult.Rejected -> result.reason
  }

  Card(
    modifier = Modifier
      .fillMaxWidth()
      .testTag("second_hand_result_banner"),
    colors = CardDefaults.cardColors(containerColor = VaultSurface),
    border = BorderStroke(1.dp, color.copy(alpha = 0.65f))
  ) {
    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
      ) {
        Icon(
          imageVector = if (isSuccess) Icons.Filled.CheckCircle else Icons.Filled.ErrorOutline,
          contentDescription = null,
          tint = color
        )
        Text(title, style = MaterialTheme.typography.titleMedium, color = TextPrimary)
      }
      Text(message, style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
      TextButton(onClick = onDismiss) {
        Text("Скрыть", color = TextMuted)
      }
    }
  }
}

@Composable
fun SecondHandRequestCard(
  request: SecondHandRequestEntity,
  currentClockMillis: Long,
  onApproveWithPin: (String, String, String) -> Unit,
  onRejectAsFraud: (String, String) -> Unit,
  onEscalateBackup: (String) -> Unit
) {
  var showApproveDialog by remember { mutableStateOf(false) }
  var showRejectDialog by remember { mutableStateOf(false) }
  var pinInput by remember { mutableStateOf("") }
  var commentInput by remember { mutableStateOf("") }

  val isPending = request.status == SecondHandStatus.PENDING
  val pauseRemainingMs = (request.unlockAfterMillis - currentClockMillis).coerceAtLeast(0L)
  val expiryRemainingMs = (request.expiresAtMillis - currentClockMillis).coerceAtLeast(0L)
  val isPauseFinished = pauseRemainingMs == 0L

  val statusColor = when (request.status) {
    SecondHandStatus.PENDING -> AmberEscrow
    SecondHandStatus.APPROVED -> EmeraldShield
    SecondHandStatus.REJECTED_FRAUD, SecondHandStatus.EXPIRED -> RubyRisk
  }
  val statusLabel = when (request.status) {
    SecondHandStatus.PENDING -> "Ожидает решения"
    SecondHandStatus.APPROVED -> "Подтверждено"
    SecondHandStatus.REJECTED_FRAUD -> "Отклонено (мошенничество)"
    SecondHandStatus.EXPIRED -> "Истекло (24 ч автооткат)"
  }

  val factors = remember(request.riskFactorsJson) {
    runCatching {
      Json.decodeFromString<List<NbrkFactorScoreDto>>(request.riskFactorsJson)
    }.getOrDefault(emptyList())
  }

  Card(
    modifier = Modifier
      .fillMaxWidth()
      .testTag("second_hand_card_${request.id}"),
    colors = CardDefaults.cardColors(containerColor = VaultSurface),
    border = BorderStroke(1.dp, statusColor.copy(alpha = 0.55f))
  ) {
    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
          Icon(Icons.Filled.Handshake, contentDescription = null, tint = statusColor)
          Text(request.depositTitle, style = MaterialTheme.typography.titleMedium, color = TextPrimary)
        }
        Surface(
          color = statusColor.copy(alpha = 0.16f),
          shape = RoundedCornerShape(8.dp)
        ) {
          Text(
            text = statusLabel,
            style = MaterialTheme.typography.labelMedium,
            color = statusColor,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
          )
        }
      }

      Text(
        text = MoneyFormatter.formatTenge(request.amountTenge),
        style = MonoAmountLarge,
        color = TextPrimary
      )

      Text(
        text = "Инициатор: ${request.initiatorName} · Получатель: ${request.recipientName}",
        style = MaterialTheme.typography.bodyMedium,
        color = TextSecondary
      )
      Text(
        text = "IBAN: ${request.recipientIban}",
        style = MonoCodeStyle,
        color = TextMuted
      )
      Text(
        text = "Активное доверенное лицо: ${request.assignedContactName} (${request.assignedContactPhone})" +
          if (request.isBackupAssigned) " [Резерв]" else "",
        style = MaterialTheme.typography.bodyMedium,
        color = EmeraldShield
      )

      Surface(
        color = VaultSurfaceVariant,
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, VaultOutline),
        modifier = Modifier.fillMaxWidth()
      ) {
        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
          Text(
            text = "Скоринг риска: ${request.riskScore}/100",
            style = MonoCodeStyle,
            color = AmberEscrow,
            fontWeight = FontWeight.SemiBold
          )
          factors.forEach { f ->
            Text(
              text = "• ${f.label} (+${f.points})",
              style = MaterialTheme.typography.bodyMedium,
              color = TextSecondary
            )
          }
        }
      }

      if (isPending) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
          Icon(
            imageVector = Icons.Filled.Schedule,
            contentDescription = null,
            tint = if (isPauseFinished) EmeraldShield else AmberEscrow,
            modifier = Modifier.size(16.dp)
          )
          Text(
            text = if (isPauseFinished) {
              "10-минутная пауза завершена — подтверждение разблокировано"
            } else {
              "Пауза безопасности: ещё ${formatDuration(pauseRemainingMs)}"
            },
            style = MaterialTheme.typography.bodyMedium,
            color = if (isPauseFinished) EmeraldShield else AmberEscrow
          )
        }
        Text(
          text = "Автооткат без ответа через: ${formatDuration(expiryRemainingMs)}",
          style = MonoCodeStyle,
          color = TextMuted
        )

        Spacer(modifier = Modifier.height(2.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
          Button(
            onClick = { showApproveDialog = true },
            colors = ButtonDefaults.buttonColors(
              containerColor = if (isPauseFinished) EmeraldShield else VaultSurfaceVariant
            ),
            modifier = Modifier
              .weight(1f)
              .height(48.dp)
              .testTag("second_hand_approve_btn_${request.id}")
          ) {
            Text(
              text = if (isPauseFinished) "Подтвердить" else "Пауза...",
              color = if (isPauseFinished) ObsidianBackground else TextSecondary,
              fontWeight = FontWeight.SemiBold
            )
          }

          Button(
            onClick = { showRejectDialog = true },
            colors = ButtonDefaults.buttonColors(containerColor = RubyRisk),
            modifier = Modifier
              .weight(1f)
              .height(48.dp)
              .testTag("second_hand_reject_btn_${request.id}")
          ) {
            Text("Отклонить", color = TextPrimary, fontWeight = FontWeight.SemiBold)
          }
        }

        if (!request.isBackupAssigned) {
          OutlinedButton(
            onClick = { onEscalateBackup(request.id) },
            border = BorderStroke(1.dp, VaultOutline),
            modifier = Modifier
              .fillMaxWidth()
              .height(48.dp)
              .testTag("second_hand_backup_btn_${request.id}")
          ) {
            Icon(Icons.Filled.PersonAdd, contentDescription = null, tint = TextSecondary)
            Spacer(modifier = Modifier.size(8.dp))
            Text("Передать резервной «Второй руке»", color = TextSecondary)
          }
        }
      } else if (request.comment.isNotBlank()) {
        Text(
          text = "Комментарий: ${request.comment}",
          style = MaterialTheme.typography.bodyMedium,
          color = TextSecondary
        )
      }
    }
  }

  if (showApproveDialog) {
    AlertDialog(
      onDismissRequest = {
        pinInput = ""
        commentInput = ""
        showApproveDialog = false
      },
      containerColor = VaultSurface,
      title = { Text("Подтверждение «Второй руки»", color = TextPrimary) },
      text = {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
          Text(
            text = "Введите 4-значный ПИН доверенного лица для подтверждения досрочного закрытия «${request.depositTitle}» на сумму ${MoneyFormatter.formatTenge(request.amountTenge)}.",
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary
          )
          OutlinedTextField(
            value = pinInput,
            onValueChange = { if (it.all { c -> c.isDigit() } && it.length <= 4) pinInput = it },
            label = { Text("ПИН «Второй руки» (4 цифры)") },
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
              focusedBorderColor = EmeraldShield,
              unfocusedBorderColor = VaultOutline,
              focusedTextColor = TextPrimary,
              unfocusedTextColor = TextPrimary
            ),
            modifier = Modifier
              .fillMaxWidth()
              .testTag("second_hand_pin_input")
          )
          OutlinedTextField(
            value = commentInput,
            onValueChange = { commentInput = it },
            label = { Text("Комментарий (необязательно)") },
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
              focusedBorderColor = EmeraldShield,
              unfocusedBorderColor = VaultOutline,
              focusedTextColor = TextPrimary,
              unfocusedTextColor = TextPrimary
            ),
            modifier = Modifier.fillMaxWidth()
          )
        }
      },
      confirmButton = {
        Button(
          onClick = {
            val pin = pinInput
            val comment = commentInput
            pinInput = ""
            commentInput = ""
            showApproveDialog = false
            onApproveWithPin(request.id, pin, comment)
          },
          enabled = pinInput.length == 4,
          colors = ButtonDefaults.buttonColors(containerColor = EmeraldShield),
          modifier = Modifier.testTag("second_hand_confirm_approve")
        ) {
          Text("Разрешить вывод", color = ObsidianBackground)
        }
      },
      dismissButton = {
        TextButton(onClick = {
          pinInput = ""
          commentInput = ""
          showApproveDialog = false
        }) {
          Text("Отмена", color = TextSecondary)
        }
      }
    )
  }

  if (showRejectDialog) {
    AlertDialog(
      onDismissRequest = {
        commentInput = ""
        showRejectDialog = false
      },
      containerColor = VaultSurface,
      icon = { Icon(Icons.Filled.Warning, contentDescription = null, tint = RubyRisk) },
      title = { Text("Заблокировать как мошенничество?", color = TextPrimary) },
      text = {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
          Text(
            text = "Средства вернутся под защиту Money Lock, операция будет заблокирована, а срочный инцидент уйдёт в Антифрод-центр НБРК.",
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary
          )
          OutlinedTextField(
            value = commentInput,
            onValueChange = { commentInput = it },
            label = { Text("Причина отклонения") },
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
              focusedBorderColor = RubyRisk,
              unfocusedBorderColor = VaultOutline,
              focusedTextColor = TextPrimary,
              unfocusedTextColor = TextPrimary
            ),
            modifier = Modifier.fillMaxWidth()
          )
        }
      },
      confirmButton = {
        Button(
          onClick = {
            val comment = commentInput
            commentInput = ""
            showRejectDialog = false
            onRejectAsFraud(request.id, comment)
          },
          colors = ButtonDefaults.buttonColors(containerColor = RubyRisk),
          modifier = Modifier.testTag("second_hand_confirm_reject")
        ) {
          Text("Заблокировать", color = TextPrimary)
        }
      },
      dismissButton = {
        TextButton(onClick = {
          commentInput = ""
          showRejectDialog = false
        }) {
          Text("Отмена", color = TextSecondary)
        }
      }
    )
  }
}

private fun formatDuration(millis: Long): String {
  val totalSec = (millis / 1_000L).coerceAtLeast(0L)
  val hours = totalSec / 3_600L
  val minutes = (totalSec % 3_600L) / 60L
  val seconds = totalSec % 60L
  return if (hours > 0L) {
    "${hours} ч ${minutes} мин"
  } else {
    "${minutes} мин ${seconds} сек"
  }
}
