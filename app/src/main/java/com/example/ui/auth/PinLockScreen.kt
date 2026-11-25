package com.example.ui.auth

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.BuildConfig
import com.example.presentation.MainUiState
import com.example.ui.theme.EmeraldShield
import com.example.ui.theme.ObsidianBackground
import com.example.ui.theme.RubyRisk
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.VaultOutline
import com.example.ui.theme.VaultSurface
import com.example.ui.theme.VaultSurfaceVariant

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PinLockScreen(
  state: MainUiState,
  onDigit: (Char) -> Unit,
  onBackspace: () -> Unit,
  onLogoLongPress: () -> Unit,
  onLogoTripleTap: () -> Unit
) {
  val haptic = LocalHapticFeedback.current
  var tapCount by remember { mutableIntStateOf(0) }
  var lastTapTime by remember { mutableLongStateOf(0L) }

  Box(
    modifier = Modifier
      .fillMaxSize()
      .background(
        Brush.verticalGradient(
          colors = listOf(ObsidianBackground, VaultSurface, ObsidianBackground)
        )
      )
      .safeDrawingPadding(),
    contentAlignment = Alignment.Center
  ) {
    Column(
      modifier = Modifier
        .widthIn(max = 460.dp)
        .fillMaxSize()
        .padding(horizontal = 24.dp, vertical = 20.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.SpaceBetween
    ) {
      // Header + Emblem
      Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.padding(top = 12.dp)
      ) {
        Surface(
          modifier = Modifier
            .size(72.dp)
            .clip(RoundedCornerShape(22.dp))
            .testTag("pin_logo")
            .combinedClickable(
              onClick = {
                if (BuildConfig.DEMO) {
                  val now = System.currentTimeMillis()
                  if (now - lastTapTime <= 650L) {
                    tapCount += 1
                  } else {
                    tapCount = 1
                  }
                  lastTapTime = now
                  if (tapCount >= 3) {
                    tapCount = 0
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onLogoTripleTap()
                  }
                }
              },
              onLongClick = {
                if (BuildConfig.DEMO) {
                  haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                  onLogoLongPress()
                }
              }
            ),
          color = VaultSurfaceVariant,
          shape = RoundedCornerShape(22.dp),
          border = BorderStroke(1.dp, EmeraldShield.copy(alpha = 0.45f))
        ) {
          Box(contentAlignment = Alignment.Center) {
            Icon(
              imageVector = Icons.Filled.Shield,
              contentDescription = "Qalqan Safe",
              tint = EmeraldShield,
              modifier = Modifier.size(36.dp)
            )
          }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
          text = "Qalqan Safe",
          style = MaterialTheme.typography.headlineMedium,
          color = TextPrimary
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
          text = "Введите ПИН-код для входа в банк",
          style = MaterialTheme.typography.bodyMedium,
          color = TextSecondary,
          textAlign = TextAlign.Center
        )
      }

      // PIN dots & error / demo hint
      Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth()
      ) {
        Row(
          horizontalArrangement = Arrangement.spacedBy(18.dp),
          verticalAlignment = Alignment.CenterVertically,
          modifier = Modifier.testTag("pin_dots_row")
        ) {
          repeat(4) { index ->
            val filled = index < state.pinDigits.length
            Box(
              modifier = Modifier
                .size(16.dp)
                .clip(CircleShape)
                .background(
                  when {
                    state.pinError != null -> RubyRisk.copy(alpha = 0.25f)
                    filled -> EmeraldShield
                    else -> VaultSurfaceVariant
                  }
                )
                .border(
                  width = 1.5.dp,
                  color = when {
                    state.pinError != null -> RubyRisk
                    filled -> EmeraldShield
                    else -> VaultOutline
                  },
                  shape = CircleShape
                )
            )
          }
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (state.pinError != null) {
          Text(
            text = state.pinError,
            style = MaterialTheme.typography.bodyMedium,
            color = RubyRisk,
            textAlign = TextAlign.Center,
            modifier = Modifier
              .padding(horizontal = 16.dp)
              .testTag("pin_error_text")
          )
        } else {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
          ) {
            Icon(
              imageVector = Icons.Filled.Lock,
              contentDescription = null,
              tint = TextMuted,
              modifier = Modifier.size(14.dp)
            )
            Text(
              text = "Локальное шифрование AES-256",
              style = MaterialTheme.typography.bodyMedium,
              color = TextMuted
            )
          }
        }

        AnimatedVisibility(
          visible = BuildConfig.DEMO && DemoHintProvider.IS_DEMO_ENABLED && state.showDemoHint,
          enter = fadeIn() + slideInVertically(spring(stiffness = Spring.StiffnessMediumLow)),
          exit = fadeOut()
        ) {
          Card(
            modifier = Modifier
              .fillMaxWidth()
              .padding(top = 14.dp)
              .testTag("demo_pin_hint_card"),
            colors = CardDefaults.cardColors(containerColor = VaultSurfaceVariant),
            border = BorderStroke(1.dp, VaultOutline)
          ) {
            Column(modifier = Modifier.padding(12.dp)) {
              Text(
                text = "Демо-подсказка (только debug)",
                style = MaterialTheme.typography.labelLarge,
                color = EmeraldShield
              )
              Spacer(modifier = Modifier.height(4.dp))
              DemoHintProvider.hintLines.forEach { line ->
                Text(
                  text = line,
                  style = MaterialTheme.typography.bodyMedium,
                  color = TextSecondary
                )
              }
            }
          }
        }
      }

      // Keypad
      Column(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.padding(bottom = 12.dp)
      ) {
        val rows = listOf(
          listOf('1', '2', '3'),
          listOf('4', '5', '6'),
          listOf('7', '8', '9')
        )
        rows.forEach { row ->
          Row(
            horizontalArrangement = Arrangement.spacedBy(20.dp),
            verticalAlignment = Alignment.CenterVertically
          ) {
            row.forEach { digit ->
              PinKeyButton(
                label = digit.toString(),
                testTag = "pin_key_$digit",
                onClick = {
                  haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                  onDigit(digit)
                }
              )
            }
          }
        }

        Row(
          horizontalArrangement = Arrangement.spacedBy(20.dp),
          verticalAlignment = Alignment.CenterVertically
        ) {
          Spacer(modifier = Modifier.size(72.dp))
          PinKeyButton(
            label = "0",
            testTag = "pin_key_0",
            onClick = {
              haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
              onDigit('0')
            }
          )
          Surface(
            onClick = {
              haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
              onBackspace()
            },
            shape = CircleShape,
            color = VaultSurfaceVariant.copy(alpha = 0.6f),
            modifier = Modifier
              .size(72.dp)
              .testTag("pin_backspace")
          ) {
            Box(contentAlignment = Alignment.Center) {
              Icon(
                imageVector = Icons.AutoMirrored.Filled.Backspace,
                contentDescription = "Удалить цифру",
                tint = TextSecondary,
                modifier = Modifier.size(24.dp)
              )
            }
          }
        }
      }
    }
  }
}

@Composable
private fun PinKeyButton(
  label: String,
  testTag: String,
  onClick: () -> Unit
) {
  Surface(
    onClick = onClick,
    shape = CircleShape,
    color = VaultSurface,
    border = BorderStroke(1.dp, VaultOutline),
    modifier = Modifier
      .size(72.dp)
      .testTag(testTag)
  ) {
    Box(contentAlignment = Alignment.Center) {
      Text(
        text = label,
        style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.SemiBold),
        color = TextPrimary
      )
    }
  }
}
