package com.example.ui.security

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import com.example.data.repository.SecurityUpdateResult
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
import com.qalqan.domain.model.SecuritySummary

@Composable
fun SecurityUpdateBanner(
  result: SecurityUpdateResult,
  onDismiss: () -> Unit
) {
  val isOk = result is SecurityUpdateResult.Success
  val color = if (isOk) EmeraldShield else RubyRisk
  val msg = when (result) {
    is SecurityUpdateResult.Success -> result.message
    is SecurityUpdateResult.Rejected -> result.reason
  }

  Card(
    modifier = Modifier
      .fillMaxWidth()
      .testTag("security_update_banner"),
    colors = CardDefaults.cardColors(containerColor = VaultSurface),
    border = BorderStroke(1.dp, color.copy(alpha = 0.65f))
  ) {
    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
      ) {
        Icon(
          imageVector = if (isOk) Icons.Filled.CheckCircle else Icons.Filled.ErrorOutline,
          contentDescription = null,
          tint = color
        )
        Text(
          text = if (isOk) "Настройки обновлены" else "Ошибка проверки",
          style = MaterialTheme.typography.titleMedium,
          color = TextPrimary
        )
      }
      Text(msg, style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
      TextButton(onClick = onDismiss) {
        Text("Скрыть", color = TextMuted)
      }
    }
  }
}

@Composable
fun SecurityPolicyFormCard(
  summary: SecuritySummary,
  onSavePolicy: (Long, Long, Int, Int, String, String, String, String) -> Unit
) {
  var thresholdText by remember(summary.thresholdTenge) {
    mutableStateOf(summary.thresholdTenge.toString())
  }
  var isolatedTargetText by remember(summary.isolatedSafeTargetTenge) {
    mutableStateOf(summary.isolatedSafeTargetTenge.toString())
  }
  var coolingOffText by remember(summary.coolingOffMinutes) {
    mutableStateOf(summary.coolingOffMinutes.toString())
  }
  var escrowDaysText by remember(summary.escrowBusinessDays) {
    mutableStateOf(summary.escrowBusinessDays.toString())
  }
  var primaryName by remember(summary.primaryTrustedName) {
    mutableStateOf(summary.primaryTrustedName)
  }
  var primaryPhone by remember(summary.primaryTrustedPhone) {
    mutableStateOf(summary.primaryTrustedPhone)
  }
  var backupName by remember(summary.backupTrustedName) {
    mutableStateOf(summary.backupTrustedName)
  }
  var backupPhone by remember(summary.backupTrustedPhone) {
    mutableStateOf(summary.backupTrustedPhone)
  }

  val textFieldColors = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = EmeraldShield,
    unfocusedBorderColor = VaultOutline,
    focusedTextColor = TextPrimary,
    unfocusedTextColor = TextPrimary,
    focusedLabelColor = EmeraldShield,
    unfocusedLabelColor = TextSecondary
  )

  Card(
    modifier = Modifier
      .fillMaxWidth()
      .testTag("security_policy_card"),
    colors = CardDefaults.cardColors(containerColor = VaultSurface),
    border = BorderStroke(1.dp, VaultOutline)
  ) {
    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
      ) {
        Icon(Icons.Filled.Tune, contentDescription = null, tint = EmeraldShield)
        Text("Лимиты и доверенные лица", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
      }

      OutlinedTextField(
        value = thresholdText,
        onValueChange = { if (it.all { c -> c.isDigit() } && it.length <= 9) thresholdText = it },
        label = { Text("Порог крупной операции (₸)") },
        textStyle = MonoCodeStyle,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        singleLine = true,
        colors = textFieldColors,
        modifier = Modifier
          .fillMaxWidth()
          .testTag("policy_threshold_input")
      )

      OutlinedTextField(
        value = isolatedTargetText,
        onValueChange = { if (it.all { c -> c.isDigit() } && it.length <= 9) isolatedTargetText = it },
        label = { Text("Целевой лимит тревожного субсчёта (₸)") },
        textStyle = MonoCodeStyle,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        singleLine = true,
        colors = textFieldColors,
        modifier = Modifier
          .fillMaxWidth()
          .testTag("policy_safe_target_input")
      )

      Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        OutlinedTextField(
          value = coolingOffText,
          onValueChange = { if (it.all { c -> c.isDigit() } && it.length <= 3) coolingOffText = it },
          label = { Text("Cooling-Off (мин)") },
          textStyle = MonoCodeStyle,
          keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
          singleLine = true,
          colors = textFieldColors,
          modifier = Modifier
            .weight(1f)
            .testTag("policy_cooling_minutes_input")
        )
        OutlinedTextField(
          value = escrowDaysText,
          onValueChange = { if (it.all { c -> c.isDigit() } && it.length <= 2) escrowDaysText = it },
          label = { Text("Soft-Escrow (раб. дн.)") },
          textStyle = MonoCodeStyle,
          keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
          singleLine = true,
          colors = textFieldColors,
          modifier = Modifier
            .weight(1f)
            .testTag("policy_escrow_days_input")
        )
      }

      OutlinedTextField(
        value = primaryName,
        onValueChange = { primaryName = it },
        label = { Text("Основная «Вторая рука» (имя)") },
        singleLine = true,
        colors = textFieldColors,
        modifier = Modifier
          .fillMaxWidth()
          .testTag("policy_primary_name_input")
      )
      OutlinedTextField(
        value = primaryPhone,
        onValueChange = { primaryPhone = it },
        label = { Text("Телефон основной руки") },
        textStyle = MonoCodeStyle,
        singleLine = true,
        colors = textFieldColors,
        modifier = Modifier
          .fillMaxWidth()
          .testTag("policy_primary_phone_input")
      )
      OutlinedTextField(
        value = backupName,
        onValueChange = { backupName = it },
        label = { Text("Резервная «Вторая рука» (имя)") },
        singleLine = true,
        colors = textFieldColors,
        modifier = Modifier
          .fillMaxWidth()
          .testTag("policy_backup_name_input")
      )
      OutlinedTextField(
        value = backupPhone,
        onValueChange = { backupPhone = it },
        label = { Text("Телефон резервной руки") },
        textStyle = MonoCodeStyle,
        singleLine = true,
        colors = textFieldColors,
        modifier = Modifier
          .fillMaxWidth()
          .testTag("policy_backup_phone_input")
      )

      Button(
        onClick = {
          onSavePolicy(
            thresholdText.toLongOrNull() ?: 0L,
            isolatedTargetText.toLongOrNull() ?: 0L,
            coolingOffText.toIntOrNull() ?: 0,
            escrowDaysText.toIntOrNull() ?: 0,
            primaryName,
            primaryPhone,
            backupName,
            backupPhone
          )
        },
        colors = ButtonDefaults.buttonColors(containerColor = EmeraldShield),
        modifier = Modifier
          .fillMaxWidth()
          .height(48.dp)
          .testTag("save_policy_button")
      ) {
        Text("Сохранить параметры политики", color = ObsidianBackground, fontWeight = FontWeight.SemiBold)
      }
    }
  }
}

@Composable
fun SecurityCredentialsRotationCard(
  onUpdateLoginPins: (String, String, String) -> Unit,
  onUpdateTransferCodes: (String, String, String) -> Unit,
  onUpdateSecondHandPin: (String, String) -> Unit
) {
  var currentPin by remember { mutableStateOf("") }
  var newStandardPin by remember { mutableStateOf("") }
  var newDuressPin by remember { mutableStateOf("") }
  var newStandardCode by remember { mutableStateOf("") }
  var newDuressCode by remember { mutableStateOf("") }
  var newSecondHandPin by remember { mutableStateOf("") }

  val textFieldColors = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = AmberEscrow,
    unfocusedBorderColor = VaultOutline,
    focusedTextColor = TextPrimary,
    unfocusedTextColor = TextPrimary,
    focusedLabelColor = AmberEscrow,
    unfocusedLabelColor = TextSecondary
  )

  Card(
    modifier = Modifier
      .fillMaxWidth()
      .testTag("security_credentials_card"),
    colors = CardDefaults.cardColors(containerColor = VaultSurface),
    border = BorderStroke(1.dp, VaultOutline)
  ) {
    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
      ) {
        Icon(Icons.Filled.Key, contentDescription = null, tint = AmberEscrow)
        Text("Смена ПИН-кодов и кодов подтверждения", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
      }
      Text(
        text = "Запрещены простые комбинации (1234, 0000, 1111 и т.п.). Между основным и тревожным кодом должно отличаться минимум 2 цифры.",
        style = MaterialTheme.typography.bodyMedium,
        color = TextSecondary
      )

      OutlinedTextField(
        value = currentPin,
        onValueChange = { if (it.all { c -> c.isDigit() } && it.length <= 4) currentPin = it },
        label = { Text("Текущий основной ПИН входа (4 цифры)") },
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
        singleLine = true,
        colors = textFieldColors,
        modifier = Modifier
          .fillMaxWidth()
          .testTag("rotation_current_pin_input")
      )

      Spacer(modifier = Modifier.height(2.dp))
      Text("1. ПИН-коды входа", style = MaterialTheme.typography.labelLarge, color = TextPrimary)
      Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        OutlinedTextField(
          value = newStandardPin,
          onValueChange = { if (it.all { c -> c.isDigit() } && it.length <= 4) newStandardPin = it },
          label = { Text("Новый основной") },
          visualTransformation = PasswordVisualTransformation(),
          keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
          singleLine = true,
          colors = textFieldColors,
          modifier = Modifier
            .weight(1f)
            .testTag("rotation_new_std_pin_input")
        )
        OutlinedTextField(
          value = newDuressPin,
          onValueChange = { if (it.all { c -> c.isDigit() } && it.length <= 4) newDuressPin = it },
          label = { Text("Новый тревожный") },
          visualTransformation = PasswordVisualTransformation(),
          keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
          singleLine = true,
          colors = textFieldColors,
          modifier = Modifier
            .weight(1f)
            .testTag("rotation_new_duress_pin_input")
        )
      }
      OutlinedButton(
        onClick = {
          onUpdateLoginPins(currentPin, newStandardPin, newDuressPin)
          newStandardPin = ""
          newDuressPin = ""
        },
        enabled = currentPin.length == 4 && newStandardPin.length == 4 && newDuressPin.length == 4,
        border = BorderStroke(1.dp, AmberEscrow.copy(alpha = 0.65f)),
        modifier = Modifier
          .fillMaxWidth()
          .height(48.dp)
          .testTag("save_login_pins_button")
      ) {
        Text("Обновить ПИН-коды входа", color = AmberEscrow)
      }

      Spacer(modifier = Modifier.height(2.dp))
      Text("2. Коды подтверждения переводов", style = MaterialTheme.typography.labelLarge, color = TextPrimary)
      Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        OutlinedTextField(
          value = newStandardCode,
          onValueChange = { if (it.all { c -> c.isDigit() } && it.length <= 4) newStandardCode = it },
          label = { Text("Обычный код") },
          visualTransformation = PasswordVisualTransformation(),
          keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
          singleLine = true,
          colors = textFieldColors,
          modifier = Modifier
            .weight(1f)
            .testTag("rotation_new_std_code_input")
        )
        OutlinedTextField(
          value = newDuressCode,
          onValueChange = { if (it.all { c -> c.isDigit() } && it.length <= 4) newDuressCode = it },
          label = { Text("Код шантажа") },
          visualTransformation = PasswordVisualTransformation(),
          keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
          singleLine = true,
          colors = textFieldColors,
          modifier = Modifier
            .weight(1f)
            .testTag("rotation_new_duress_code_input")
        )
      }
      OutlinedButton(
        onClick = {
          onUpdateTransferCodes(currentPin, newStandardCode, newDuressCode)
          newStandardCode = ""
          newDuressCode = ""
        },
        enabled = currentPin.length == 4 && newStandardCode.length == 4 && newDuressCode.length == 4,
        border = BorderStroke(1.dp, AmberEscrow.copy(alpha = 0.65f)),
        modifier = Modifier
          .fillMaxWidth()
          .height(48.dp)
          .testTag("save_transfer_codes_button")
      ) {
        Text("Обновить коды переводов", color = AmberEscrow)
      }

      Spacer(modifier = Modifier.height(2.dp))
      Text("3. ПИН доверенного лица («Вторая рука»)", style = MaterialTheme.typography.labelLarge, color = TextPrimary)
      OutlinedTextField(
        value = newSecondHandPin,
        onValueChange = { if (it.all { c -> c.isDigit() } && it.length <= 4) newSecondHandPin = it },
        label = { Text("Новый ПИН «Второй руки» (4 цифры)") },
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
        singleLine = true,
        colors = textFieldColors,
        modifier = Modifier
          .fillMaxWidth()
          .testTag("rotation_new_second_hand_pin_input")
      )
      OutlinedButton(
        onClick = {
          onUpdateSecondHandPin(currentPin, newSecondHandPin)
          newSecondHandPin = ""
        },
        enabled = currentPin.length == 4 && newSecondHandPin.length == 4,
        border = BorderStroke(1.dp, EmeraldShield.copy(alpha = 0.65f)),
        modifier = Modifier
          .fillMaxWidth()
          .height(48.dp)
          .testTag("save_second_hand_pin_button")
      ) {
        Text("Обновить ПИН «Второй руки»", color = EmeraldShield)
      }
    }
  }
}
