package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

private val ObsidianVaultColorScheme = darkColorScheme(
  primary = EmeraldShield,
  onPrimary = ObsidianBackground,
  primaryContainer = EmeraldShieldDark,
  onPrimaryContainer = TextPrimary,
  secondary = AmberEscrow,
  onSecondary = ObsidianBackground,
  secondaryContainer = AmberEscrowDark,
  onSecondaryContainer = TextPrimary,
  tertiary = AmberEscrow,
  onTertiary = ObsidianBackground,
  background = ObsidianBackground,
  onBackground = TextPrimary,
  surface = VaultSurface,
  onSurface = TextPrimary,
  surfaceVariant = VaultSurfaceVariant,
  onSurfaceVariant = TextSecondary,
  outline = VaultOutline,
  error = RubyRisk,
  onError = TextPrimary,
  errorContainer = RubyRiskDark,
  onErrorContainer = TextPrimary
)

private val QalqanShapes = Shapes(
  small = RoundedCornerShape(10.dp),
  medium = RoundedCornerShape(16.dp),
  large = RoundedCornerShape(22.dp),
  extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun QalqanVaultTheme(content: @Composable () -> Unit) {
  MaterialTheme(
    colorScheme = ObsidianVaultColorScheme,
    typography = QalqanTypography,
    shapes = QalqanShapes,
    content = content
  )
}
