package com.example.ui.deposits

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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Handshake
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockClock
import androidx.compose.material3.AlertDialog
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
import com.qalqan.domain.model.Deposit
import com.qalqan.domain.model.DepositStatus
import com.qalqan.domain.model.SessionMode
import com.qalqan.domain.model.TransferStatus
import com.qalqan.domain.util.MoneyFormatter
import com.qalqan.domain.validation.KzIdentifierValidator

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DepositsScreen(
  state: MainUiState,
  onSubmitEarlyDepositWithdrawal: (String, String, String, Long, String, Boolean) -> Unit,
  onClearDepositResult: () -> Unit,
  onNavigateToSecondHand: () -> Unit
) {
  val isDuress = state.sessionMode == SessionMode.DURESS
  var activeDepositForClosure by remember { mutableStateOf<Deposit?>(null) }
  var pendingRecipientName by remember { mutableStateOf("") }
  var pendingRecipientIban by remember { mutableStateOf("") }
  var pendingAmount by remember { mutableStateOf(0L) }
  var secondConfirmCode by remember { mutableStateOf("") }

  LazyColumn(
    contentPadding = PaddingValues(16.dp),
    verticalArrangement = Arrangement.spacedBy(12.dp),
    modifier = Modifier
      .fillMaxSize()
      .testTag("deposits_screen")
  ) {
    item {
      Text(
        text = if (isDuress) "Депозиты" else "Сейф и депозиты Money Lock",
        style = MaterialTheme.typography.headlineSmall,
        color = TextPrimary
      )
    }

    if (!isDuress && state.nbrkTransmissionStage !is NbrkTransmissionStage.Idle) {
      item {
        NbrkStepAnimationCard(stage = state.nbrkTransmissionStage)
      }
    }

    if (!isDuress) {
      state.depositActionResult?.let { result ->
        if (result !is TransferSubmitResult.NeedsSecondConfirmation) {
          item {
            AnimatedVisibility(
              visible = true,
              enter = fadeIn() + slideInVertically(spring(stiffness = Spring.StiffnessMediumLow))
            ) {
              DepositActionResultBanner(
                result = result,
                onDismiss = onClearDepositResult,
                onOpenSecondHand = onNavigateToSecondHand
              )
            }
          }
        }
      }
    }

    items(state.deposits, key = { it.id }) { dep ->
      DepositCard(
        deposit = dep,
        isDuress = isDuress,
        onEarlyCloseClick = {
          onClearDepositResult()
          activeDepositForClosure = dep
        }
      )
    }
  }

  if (!isDuress && activeDepositForClosure != null) {
    val targetDeposit = activeDepositForClosure!!
    ModalBottomSheet(
      onDismissRequest = { activeDepositForClosure = null },
      containerColor = VaultSurface
    ) {
      EarlyClosureSheetContent(
        deposit = targetDeposit,
        state = state,
        onSubmit = { name, iban, amount, code ->
          pendingRecipientName = name
          pendingRecipientIban = iban
          pendingAmount = amount
          onSubmitEarlyDepositWithdrawal(targetDeposit.id, name, iban, amount, code, false)
          activeDepositForClosure = null
        },
        onCancel = { activeDepositForClosure = null }
      )
    }
  }

  val needsConfirm = if (!isDuress) {
    state.depositActionResult as? TransferSubmitResult.NeedsSecondConfirmation
  } else null
  val lastDeposit = state.deposits.firstOrNull { it.status == DepositStatus.ACTIVE }
  if (needsConfirm != null && lastDeposit != null) {
    AlertDialog(
      onDismissRequest = {
        secondConfirmCode = ""
        onClearDepositResult()
      },
      containerColor = VaultSurface,
      title = { Text("Повторное подтверждение", color = TextPrimary) },
      text = {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
          Text(
            text = "Досрочное снятие крупной суммы на известный счёт. Повторите код подтверждения операции.",
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary
          )
          OutlinedTextField(
            value = secondConfirmCode,
            onValueChange = { if (it.all { c -> c.isDigit() } && it.length <= 4) secondConfirmCode = it },
            label = { Text("Код (4 цифры)") },
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
              .testTag("deposit_second_confirm_input")
          )
        }
      },
      confirmButton = {
        Button(
          onClick = {
            val code = secondConfirmCode
            secondConfirmCode = ""
            onSubmitEarlyDepositWithdrawal(
              lastDeposit.id,
              pendingRecipientName,
              pendingRecipientIban,
              pendingAmount,
              code,
              true
            )
          },
          enabled = secondConfirmCode.length == 4,
          colors = ButtonDefaults.buttonColors(containerColor = EmeraldShield)
        ) {
          Text("Подтвердить", color = ObsidianBackground)
        }
      },
      dismissButton = {
        TextButton(onClick = {
          secondConfirmCode = ""
          onClearDepositResult()
        }) {
          Text("Отмена", color = TextSecondary)
        }
      }
    )
  }
}

@Composable
private fun DepositCard(
  deposit: Deposit,
  isDuress: Boolean,
  onEarlyCloseClick: () -> Unit
) {
  val statusColor = when (deposit.status) {
    DepositStatus.ACTIVE -> EmeraldShield
    DepositStatus.PENDING_EARLY_CLOSURE -> AmberEscrow
    DepositStatus.CLOSED -> TextMuted
  }
  val statusLabel = when (deposit.status) {
    DepositStatus.ACTIVE -> "Активен"
    DepositStatus.PENDING_EARLY_CLOSURE -> "Ожидает проверки"
    DepositStatus.CLOSED -> "Закрыт"
  }

  Card(
    modifier = Modifier
      .fillMaxWidth()
      .testTag("deposit_card_${deposit.id}"),
    colors = CardDefaults.cardColors(containerColor = VaultSurface),
    border = BorderStroke(
      1.dp,
      if (!isDuress && deposit.isMoneyLocked) statusColor.copy(alpha = 0.45f) else VaultOutline
    )
  ) {
    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Text(deposit.title, style = MaterialTheme.typography.titleMedium, color = TextPrimary)
        Text(
          text = "${deposit.annualRatePercent}% годовых",
          style = MonoCodeStyle,
          color = EmeraldShield
        )
      }

      Text(
        text = MoneyFormatter.formatTenge(deposit.balanceTenge),
        style = MonoAmountLarge,
        color = TextPrimary
      )

      if (!isDuress) {
        Row(
          horizontalArrangement = Arrangement.spacedBy(8.dp),
          verticalAlignment = Alignment.CenterVertically
        ) {
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
          if (deposit.isMoneyLocked) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
              Icon(
                imageVector = Icons.Filled.Lock,
                contentDescription = null,
                tint = EmeraldShield,
                modifier = Modifier.size(14.dp)
              )
              Text(
                text = "Money Lock",
                style = MaterialTheme.typography.labelMedium,
                color = EmeraldShield
              )
            }
          }
        }

        if (deposit.isMoneyLocked && deposit.status == DepositStatus.ACTIVE) {
          Text(
            text = "Досрочный вывод крупной суммы новому получателю требует подтверждения «Второй рукой» и 10-минутной паузы.",
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary
          )
        }

        if (deposit.status == DepositStatus.ACTIVE && deposit.balanceTenge > 0L) {
          OutlinedButton(
            onClick = onEarlyCloseClick,
            border = BorderStroke(1.dp, AmberEscrow.copy(alpha = 0.65f)),
            modifier = Modifier
              .fillMaxWidth()
              .height(48.dp)
              .testTag("early_close_btn_${deposit.id}")
          ) {
            Text("Досрочно закрыть и перевести", color = AmberEscrow)
          }
        }
      }
    }
  }
}

@Composable
private fun EarlyClosureSheetContent(
  deposit: Deposit,
  state: MainUiState,
  onSubmit: (String, String, Long, String) -> Unit,
  onCancel: () -> Unit
) {
  var recipientName by remember { mutableStateOf("") }
  var recipientIban by remember { mutableStateOf("") }
  var amountInput by remember { mutableStateOf(deposit.balanceTenge.toString()) }
  var codeInput by remember { mutableStateOf("") }

  val textFieldColors = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = EmeraldShield,
    unfocusedBorderColor = VaultOutline,
    focusedTextColor = TextPrimary,
    unfocusedTextColor = TextPrimary,
    focusedLabelColor = EmeraldShield,
    unfocusedLabelColor = TextSecondary
  )

  val sampleNewIban = remember {
    KzIdentifierValidator.buildValidKzIban("947", "0003332221118")
  }

  Column(
    modifier = Modifier
      .fillMaxWidth()
      .padding(horizontal = 20.dp, vertical = 12.dp),
    verticalArrangement = Arrangement.spacedBy(12.dp)
  ) {
    Text(
      text = "Досрочное закрытие: ${deposit.title}",
      style = MaterialTheme.typography.titleLarge,
      color = TextPrimary
    )
    Text(
      text = "Доступно на вкладе: ${MoneyFormatter.formatTenge(deposit.balanceTenge)}",
      style = MonoCodeStyle,
      color = EmeraldShield
    )

    Row(
      modifier = Modifier
        .fillMaxWidth()
        .horizontalScroll(rememberScrollState()),
      horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
      FilterChip(
        selected = recipientIban == sampleNewIban,
        onClick = {
          recipientName = "Новый получатель (тест Второй руки)"
          recipientIban = sampleNewIban
        },
        label = { Text("Новый получатель") }
      )
      state.trustedRecipients.forEach { rec ->
        FilterChip(
          selected = recipientIban == rec.iban,
          onClick = {
            recipientName = rec.name
            recipientIban = rec.iban
          },
          label = { Text(rec.name) }
        )
      }
      if (state.demoDropperIban != null) {
        FilterChip(
          selected = recipientIban == state.demoDropperIban,
          onClick = {
            recipientName = "Подозрительный счёт"
            recipientIban = state.demoDropperIban
          },
          label = { Text("Стоп-лист") },
          colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = RubyRisk.copy(alpha = 0.2f),
            selectedLabelColor = RubyRisk
          )
        )
      }
    }

    OutlinedTextField(
      value = recipientName,
      onValueChange = { recipientName = it },
      label = { Text("Имя получателя") },
      singleLine = true,
      colors = textFieldColors,
      modifier = Modifier
        .fillMaxWidth()
        .testTag("deposit_recipient_name")
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
        .testTag("deposit_recipient_iban")
    )

    OutlinedTextField(
      value = amountInput,
      onValueChange = { if (it.all { c -> c.isDigit() } && it.length <= 10) amountInput = it },
      label = { Text("Сумма вывода (₸)") },
      textStyle = MonoCodeStyle,
      keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
      singleLine = true,
      colors = textFieldColors,
      modifier = Modifier
        .fillMaxWidth()
        .testTag("deposit_amount_input")
    )

    OutlinedTextField(
      value = codeInput,
      onValueChange = { if (it.all { c -> c.isDigit() } && it.length <= 4) codeInput = it },
      label = { Text("Код подтверждения (4 цифры)") },
      textStyle = MonoCodeStyle,
      visualTransformation = PasswordVisualTransformation(),
      keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
      singleLine = true,
      colors = textFieldColors,
      modifier = Modifier
        .fillMaxWidth()
        .testTag("deposit_code_input")
    )

    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
      TextButton(
        onClick = onCancel,
        modifier = Modifier
          .weight(1f)
          .height(48.dp)
      ) {
        Text("Отмена", color = TextSecondary)
      }
      Button(
        onClick = {
          onSubmit(
            recipientName,
            recipientIban,
            amountInput.toLongOrNull() ?: 0L,
            codeInput
          )
        },
        enabled = !state.isDepositActionSubmitting && codeInput.length == 4,
        colors = ButtonDefaults.buttonColors(containerColor = EmeraldShield),
        modifier = Modifier
          .weight(2f)
          .height(48.dp)
          .testTag("deposit_withdraw_submit")
      ) {
        if (state.isDepositActionSubmitting) {
          CircularProgressIndicator(
            color = ObsidianBackground,
            modifier = Modifier.size(18.dp),
            strokeWidth = 2.dp
          )
        } else {
          Text("Закрыть и перевести", color = ObsidianBackground, fontWeight = FontWeight.SemiBold)
        }
      }
    }
    Spacer(modifier = Modifier.height(12.dp))
  }
}

@Composable
private fun DepositActionResultBanner(
  result: TransferSubmitResult,
  onDismiss: () -> Unit,
  onOpenSecondHand: () -> Unit
) {
  val (borderColor, icon, title, body) = when (result) {
    is TransferSubmitResult.Success -> when (result.uiStatus) {
      TransferStatus.AWAITING_SECOND_HAND -> QuadDeposit(
        AmberEscrow,
        Icons.Filled.Handshake,
        "Ожидается подтверждение «Второй руки»",
        result.message
      )
      TransferStatus.SOFT_ESCROW_HOLD -> QuadDeposit(
        RubyRisk,
        Icons.Filled.LockClock,
        "Soft-Escrow: средства заморожены",
        result.message
      )
      TransferStatus.COMPLETED -> QuadDeposit(
        EmeraldShield,
        Icons.Filled.CheckCircle,
        "Досрочное снятие выполнено",
        result.message
      )
      else -> QuadDeposit(AmberEscrow, Icons.Filled.LockClock, "На паузе Cooling-Off", result.message)
    }
    is TransferSubmitResult.Rejected -> QuadDeposit(
      RubyRisk,
      Icons.Filled.ErrorOutline,
      "Операция отклонена",
      result.reason
    )
    is TransferSubmitResult.NeedsSecondConfirmation -> return
  }

  Card(
    modifier = Modifier
      .fillMaxWidth()
      .testTag("deposit_result_banner"),
    colors = CardDefaults.cardColors(containerColor = VaultSurface),
    border = BorderStroke(1.dp, borderColor.copy(alpha = 0.65f))
  ) {
    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
      ) {
        Icon(icon, contentDescription = null, tint = borderColor)
        Text(title, style = MaterialTheme.typography.titleMedium, color = TextPrimary)
      }
      Text(body, style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
      Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        if (result is TransferSubmitResult.Success && result.uiStatus == TransferStatus.AWAITING_SECOND_HAND) {
          Button(
            onClick = onOpenSecondHand,
            colors = ButtonDefaults.buttonColors(containerColor = AmberEscrow),
            modifier = Modifier.testTag("goto_second_hand_cabinet_btn")
          ) {
            Text("Открыть «Вторую руку»", color = ObsidianBackground)
          }
        }
        TextButton(onClick = onDismiss) {
          Text("Скрыть", color = TextMuted)
        }
      }
    }
  }
}

private data class QuadDeposit<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)
