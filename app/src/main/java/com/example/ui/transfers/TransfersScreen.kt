package com.example.ui.transfers

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material.icons.filled.LockClock
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import com.example.data.repository.TransferSubmitResult
import com.example.data.simulator.NbrkTransmissionStage
import com.example.presentation.MainUiState
import com.example.ui.security.NbrkStepAnimationCard
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
import com.qalqan.domain.model.AccountType
import com.qalqan.domain.model.SessionMode
import com.qalqan.domain.model.TransferStatus
import com.qalqan.domain.util.MoneyFormatter
import com.qalqan.domain.validation.KzIdentifierValidator

@Composable
fun TransfersScreen(
  state: MainUiState,
  onSubmitTransfer: (String, String, Long, String, Boolean) -> Unit,
  onClearResult: () -> Unit
) {
  val isDuress = state.sessionMode == SessionMode.DURESS
  val sourceAccount = remember(state.accounts, isDuress) {
    val target = if (isDuress) AccountType.ISOLATED_SAFE else AccountType.MAIN
    state.accounts.firstOrNull { it.type == target }
  }

  var recipientName by remember { mutableStateOf("") }
  var recipientIban by remember { mutableStateOf("") }
  var amountInput by remember { mutableStateOf("") }
  var codeInput by remember { mutableStateOf("") }
  var secondConfirmCode by remember { mutableStateOf("") }

  val textFieldColors = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = EmeraldShield,
    unfocusedBorderColor = VaultOutline,
    focusedTextColor = TextPrimary,
    unfocusedTextColor = TextPrimary,
    focusedLabelColor = EmeraldShield,
    unfocusedLabelColor = TextSecondary
  )

  LazyColumn(
    contentPadding = PaddingValues(16.dp),
    verticalArrangement = Arrangement.spacedBy(14.dp),
    modifier = Modifier
      .fillMaxSize()
      .testTag("transfers_screen")
  ) {
    item {
      Text(
        text = "Переводы",
        style = MaterialTheme.typography.headlineSmall,
        color = TextPrimary
      )
    }

    item {
      Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = VaultSurfaceVariant),
        border = BorderStroke(1.dp, VaultOutline)
      ) {
        Column(modifier = Modifier.padding(16.dp)) {
          Text(
            text = sourceAccount?.title ?: "Счёт списания",
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary
          )
          Spacer(modifier = Modifier.height(4.dp))
          Text(
            text = MoneyFormatter.formatTenge(sourceAccount?.balanceTenge ?: 0L),
            style = MonoAmountLarge,
            color = TextPrimary
          )
        }
      }
    }

    item {
      Text(
        text = "Быстрый выбор получателя",
        style = MaterialTheme.typography.labelLarge,
        color = TextSecondary
      )
      Spacer(modifier = Modifier.height(8.dp))
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
      ) {
        state.trustedRecipients.forEach { rec ->
          FilterChip(
            selected = recipientIban == rec.iban,
            onClick = {
              recipientName = rec.name
              recipientIban = rec.iban
            },
            label = { Text(rec.name) },
            colors = FilterChipDefaults.filterChipColors(
              selectedContainerColor = EmeraldShield.copy(alpha = 0.2f),
              selectedLabelColor = EmeraldShield
            )
          )
        }

        // Новый произвольный получатель с валидным KZ IBAN
        val sampleNewIban = remember {
          KzIdentifierValidator.buildValidKzIban("947", "0005556667771")
        }
        FilterChip(
          selected = recipientIban == sampleNewIban,
          onClick = {
            recipientName = "Новый контрагент"
            recipientIban = sampleNewIban
          },
          label = { Text("Новый получатель") }
        )

        if (!isDuress && state.demoDropperIban != null) {
          FilterChip(
            selected = recipientIban == state.demoDropperIban,
            onClick = {
              recipientName = "Неизвестный счёт (фрод-тест)"
              recipientIban = state.demoDropperIban
            },
            label = { Text("Стоп-лист дропперов") },
            colors = FilterChipDefaults.filterChipColors(
              selectedContainerColor = RubyRisk.copy(alpha = 0.2f),
              selectedLabelColor = RubyRisk
            )
          )
        }
      }
    }

    item {
      Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = VaultSurface),
        border = BorderStroke(1.dp, VaultOutline)
      ) {
        Column(
          modifier = Modifier.padding(16.dp),
          verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
          OutlinedTextField(
            value = recipientName,
            onValueChange = { recipientName = it },
            label = { Text("Имя получателя") },
            singleLine = true,
            colors = textFieldColors,
            modifier = Modifier
              .fillMaxWidth()
              .testTag("transfer_recipient_name")
          )

          OutlinedTextField(
            value = recipientIban,
            onValueChange = { recipientIban = it.uppercase() },
            label = { Text("IBAN получателя (KZ...)") },
            textStyle = MonoCodeStyle,
            singleLine = true,
            colors = textFieldColors,
            modifier = Modifier
              .fillMaxWidth()
              .testTag("transfer_recipient_iban")
          )

          OutlinedTextField(
            value = amountInput,
            onValueChange = { input ->
              if (input.all { it.isDigit() } && input.length <= 10) {
                amountInput = input
              }
            },
            label = { Text("Сумма перевода (₸)") },
            textStyle = MonoCodeStyle,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            singleLine = true,
            colors = textFieldColors,
            modifier = Modifier
              .fillMaxWidth()
              .testTag("transfer_amount_input")
          )

          OutlinedTextField(
            value = codeInput,
            onValueChange = { input ->
              if (input.all { it.isDigit() } && input.length <= 4) {
                codeInput = input
              }
            },
            label = { Text("Код подтверждения операции (4 цифры)") },
            textStyle = MonoCodeStyle,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
            singleLine = true,
            colors = textFieldColors,
            modifier = Modifier
              .fillMaxWidth()
              .testTag("transfer_code_input")
          )

          Button(
            onClick = {
              val amount = amountInput.toLongOrNull() ?: 0L
              onSubmitTransfer(recipientName, recipientIban, amount, codeInput, false)
            },
            enabled = !state.isTransferSubmitting && codeInput.length == 4,
            colors = ButtonDefaults.buttonColors(containerColor = EmeraldShield),
            modifier = Modifier
              .fillMaxWidth()
              .height(52.dp)
              .testTag("transfer_submit_button")
          ) {
            if (state.isTransferSubmitting) {
              CircularProgressIndicator(
                color = ObsidianBackground,
                modifier = Modifier.size(20.dp),
                strokeWidth = 2.dp
              )
              Spacer(modifier = Modifier.size(8.dp))
              Text("Проверка операции...", color = ObsidianBackground)
            } else {
              Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null, tint = ObsidianBackground)
              Spacer(modifier = Modifier.size(8.dp))
              Text("Отправить перевод", color = ObsidianBackground, fontWeight = FontWeight.SemiBold)
            }
          }
        }
      }
    }

    // Пошаговая анимация отправки в симулятор НБРК (только в обычном режиме!)
    if (!isDuress && state.nbrkTransmissionStage !is NbrkTransmissionStage.Idle) {
      item {
        NbrkStepAnimationCard(stage = state.nbrkTransmissionStage)
      }
    }

    // Результат перевода
    state.transferResult?.let { result ->
      if (result !is TransferSubmitResult.NeedsSecondConfirmation) {
        item {
          AnimatedVisibility(
            visible = true,
            enter = fadeIn() + slideInVertically(spring(stiffness = Spring.StiffnessMediumLow))
          ) {
            TransferResultBanner(
              result = result,
              isDuress = isDuress,
              onDismiss = onClearResult
            )
          }
        }
      }
    }
  }

  // Диалог повторного подтверждения для крупного перевода известному получателю (< 40 риск)
  val needsConfirm = state.transferResult as? TransferSubmitResult.NeedsSecondConfirmation
  if (needsConfirm != null) {
    AlertDialog(
      onDismissRequest = {
        secondConfirmCode = ""
        onClearResult()
      },
      containerColor = VaultSurface,
      title = {
        Text(
          text = "Дополнительное подтверждение",
          color = TextPrimary
        )
      },
      text = {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
          Text(
            text = "Сумма превышает порог безопасности. Повторите код подтверждения для завершения перевода известному получателю.",
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary
          )
          OutlinedTextField(
            value = secondConfirmCode,
            onValueChange = { if (it.all { c -> c.isDigit() } && it.length <= 4) secondConfirmCode = it },
            label = { Text("Повторите код (4 цифры)") },
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
            singleLine = true,
            colors = textFieldColors,
            modifier = Modifier
              .fillMaxWidth()
              .testTag("second_confirm_code_input")
          )
        }
      },
      confirmButton = {
        Button(
          onClick = {
            val amount = amountInput.toLongOrNull() ?: 0L
            val codeToSend = secondConfirmCode
            secondConfirmCode = ""
            onSubmitTransfer(recipientName, recipientIban, amount, codeToSend, true)
          },
          enabled = secondConfirmCode.length == 4,
          colors = ButtonDefaults.buttonColors(containerColor = EmeraldShield),
          modifier = Modifier.testTag("second_confirm_submit")
        ) {
          Text("Подтвердить", color = ObsidianBackground)
        }
      },
      dismissButton = {
        TextButton(onClick = {
          secondConfirmCode = ""
          onClearResult()
        }) {
          Text("Отмена", color = TextSecondary)
        }
      }
    )
  }
}

@Composable
private fun TransferResultBanner(
  result: TransferSubmitResult,
  isDuress: Boolean,
  onDismiss: () -> Unit
) {
  val (borderColor, icon, title, body) = when (result) {
    is TransferSubmitResult.Success -> {
      val effectiveStatus = if (isDuress) TransferStatus.COMPLETED else result.uiStatus
      when (effectiveStatus) {
        TransferStatus.COMPLETED -> Quad(
          EmeraldShield,
          Icons.Filled.CheckCircle,
          "Перевод выполнен",
          if (isDuress) "Средства отправлены получателю." else result.message
        )
        TransferStatus.COOLING_OFF -> Quad(
          AmberEscrow,
          Icons.Filled.HourglassTop,
          "Задержка Cooling-Off",
          "${result.message} (скоринг: ${result.riskScore}/100)"
        )
        TransferStatus.SOFT_ESCROW_HOLD -> Quad(
          RubyRisk,
          Icons.Filled.LockClock,
          "Soft-Escrow: удержание средств",
          "${result.message} Заявка: ${result.incidentCode.orEmpty()}"
        )
        else -> Quad(AmberEscrow, Icons.Filled.HourglassTop, "В обработке", result.message)
      }
    }
    is TransferSubmitResult.Rejected -> Quad(
      RubyRisk,
      Icons.Filled.ErrorOutline,
      "Операция не выполнена",
      result.reason
    )
    is TransferSubmitResult.NeedsSecondConfirmation -> return
  }

  Card(
    modifier = Modifier
      .fillMaxWidth()
      .testTag("transfer_result_banner"),
    shape = RoundedCornerShape(16.dp),
    colors = CardDefaults.cardColors(containerColor = VaultSurface),
    border = BorderStroke(1.dp, borderColor.copy(alpha = 0.65f))
  ) {
    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
      ) {
        Icon(icon, contentDescription = null, tint = borderColor)
        Text(text = title, style = MaterialTheme.typography.titleMedium, color = TextPrimary)
      }
      Text(text = body, style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
      TextButton(onClick = onDismiss) {
        Text("Скрыть", color = TextMuted)
      }
    }
  }
}

private data class Quad<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)
