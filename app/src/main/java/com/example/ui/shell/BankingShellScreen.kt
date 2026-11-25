package com.example.ui.shell

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.CompareArrows
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Savings
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.BuildConfig
import com.example.presentation.DemoTabTarget
import com.example.presentation.MainUiState
import com.example.ui.deposits.DepositsScreen
import com.example.ui.security.SecurityCenterScreen
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
import com.example.ui.transfers.TransfersScreen
import com.qalqan.domain.model.AccountType
import com.qalqan.domain.model.SessionMode
import com.qalqan.domain.model.TransferRecord
import com.qalqan.domain.model.TransferStatus
import com.qalqan.domain.model.UserRole
import com.qalqan.domain.util.MoneyFormatter
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.serialization.Serializable

@Serializable data object HomeRoute
@Serializable data object TransfersRoute
@Serializable data object DepositsRoute
@Serializable data object ProtectionOrProfileRoute

private data class NavTab(
  val route: Any,
  val routeName: String,
  val label: String,
  val selectedIcon: ImageVector,
  val unselectedIcon: ImageVector
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BankingShellScreen(
  state: MainUiState,
  onLockSession: () -> Unit,
  onLogoTripleTap: () -> Unit,
  onConsumeRequestedTabTarget: () -> Unit,
  onSubmitTransfer: (String, String, Long, String, Boolean) -> Unit,
  onClearTransferResult: () -> Unit,
  onSubmitEarlyDepositWithdrawal: (String, String, String, Long, String, Boolean) -> Unit,
  onClearDepositResult: () -> Unit,
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
  val isDuress = state.sessionMode == SessionMode.DURESS
  val navController = rememberNavController()
  val navBackStackEntry by navController.currentBackStackEntryAsState()
  val currentRoute = navBackStackEntry?.destination?.route.orEmpty()
  var selectedTransfer by remember { mutableStateOf<TransferRecord?>(null) }
  var securitySegment by remember { mutableIntStateOf(0) }
  var topTapCount by remember { mutableIntStateOf(0) }
  var lastTopTapTime by remember { mutableLongStateOf(0L) }

  LaunchedEffect(state.requestedTabTarget) {
    val target = state.requestedTabTarget ?: return@LaunchedEffect
    val route: Any = when (target) {
      DemoTabTarget.HOME -> HomeRoute
      DemoTabTarget.TRANSFERS -> TransfersRoute
      DemoTabTarget.DEPOSITS -> DepositsRoute
      DemoTabTarget.SECURITY_SECOND_HAND -> {
        securitySegment = 0
        ProtectionOrProfileRoute
      }
      DemoTabTarget.SECURITY_NBRK -> {
        securitySegment = 1
        ProtectionOrProfileRoute
      }
    }
    navController.navigate(route) {
      popUpTo(navController.graph.findStartDestination().id) {
        saveState = true
      }
      launchSingleTop = true
      restoreState = true
    }
    onConsumeRequestedTabTarget()
  }

  val tabs = remember(isDuress) {
    listOf(
      NavTab(
        route = HomeRoute,
        routeName = HomeRoute::class.qualifiedName.orEmpty(),
        label = "Главная",
        selectedIcon = Icons.Filled.Home,
        unselectedIcon = Icons.Outlined.Home
      ),
      NavTab(
        route = TransfersRoute,
        routeName = TransfersRoute::class.qualifiedName.orEmpty(),
        label = "Переводы",
        selectedIcon = Icons.AutoMirrored.Filled.CompareArrows,
        unselectedIcon = Icons.AutoMirrored.Filled.CompareArrows
      ),
      NavTab(
        route = DepositsRoute,
        routeName = DepositsRoute::class.qualifiedName.orEmpty(),
        label = if (isDuress) "Депозиты" else "Сейф и депозиты",
        selectedIcon = Icons.Filled.Savings,
        unselectedIcon = Icons.Outlined.Savings
      ),
      NavTab(
        route = ProtectionOrProfileRoute,
        routeName = ProtectionOrProfileRoute::class.qualifiedName.orEmpty(),
        label = if (isDuress) "Профиль" else "Центр защиты",
        selectedIcon = if (isDuress) Icons.Filled.Person else Icons.Filled.Security,
        unselectedIcon = if (isDuress) Icons.Outlined.Person else Icons.Outlined.Security
      )
    )
  }

  Scaffold(
    containerColor = ObsidianBackground,
    contentWindowInsets = WindowInsets(0, 0, 0, 0),
    topBar = {
      TopAppBar(
        title = {
          val titleModifier = if (!isDuress && BuildConfig.DEMO) {
            Modifier.clickable(
              interactionSource = remember { MutableInteractionSource() },
              indication = null
            ) {
              val now = System.currentTimeMillis()
              topTapCount = if (now - lastTopTapTime <= 650L) topTapCount + 1 else 1
              lastTopTapTime = now
              if (topTapCount >= 3) {
                topTapCount = 0
                onLogoTripleTap()
              }
            }
          } else {
            Modifier
          }
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = titleModifier.testTag("shell_top_title")
          ) {
            Icon(
              imageVector = if (isDuress) Icons.Filled.AccountBalanceWallet else Icons.Filled.Shield,
              contentDescription = null,
              tint = EmeraldShield,
              modifier = Modifier.size(22.dp)
            )
            Text(
              text = if (isDuress) "Qalqan Банк" else "Qalqan Safe",
              style = MaterialTheme.typography.titleLarge,
              color = TextPrimary
            )
            if (!isDuress && state.activeRole != UserRole.CLIENT_STANDARD) {
              val roleBadge = when (state.activeRole) {
                UserRole.SECOND_HAND -> "Вторая рука"
                UserRole.NBRK_INSPECTOR -> "НБРК"
                else -> ""
              }
              if (roleBadge.isNotEmpty()) {
                Surface(
                  color = AmberEscrow.copy(alpha = 0.18f),
                  shape = RoundedCornerShape(6.dp)
                ) {
                  Text(
                    text = roleBadge,
                    style = MaterialTheme.typography.labelSmall,
                    color = AmberEscrow,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                  )
                }
              }
            }
          }
        },
        actions = {
          IconButton(
            onClick = onLockSession,
            modifier = Modifier.testTag("top_lock_button")
          ) {
            Icon(
              imageVector = Icons.Filled.Lock,
              contentDescription = "Заблокировать приложение",
              tint = TextSecondary
            )
          }
        },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = ObsidianBackground)
      )
    },
    bottomBar = {
      NavigationBar(
        containerColor = VaultSurface,
        tonalElevation = 0.dp
      ) {
        tabs.forEach { tab ->
          val selected = currentRoute.contains(tab.routeName.substringAfterLast('.'))
          NavigationBarItem(
            selected = selected,
            onClick = {
              navController.navigate(tab.route) {
                popUpTo(navController.graph.findStartDestination().id) {
                  saveState = true
                }
                launchSingleTop = true
                restoreState = true
              }
            },
            icon = {
              Icon(
                imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                contentDescription = tab.label
              )
            },
            label = { Text(tab.label, maxLines = 1) },
            colors = NavigationBarItemDefaults.colors(
              selectedIconColor = EmeraldShield,
              selectedTextColor = EmeraldShield,
              indicatorColor = EmeraldShield.copy(alpha = 0.16f),
              unselectedIconColor = TextSecondary,
              unselectedTextColor = TextSecondary
            ),
            modifier = Modifier.testTag("tab_${tab.label}")
          )
        }
      }
    }
  ) { innerPadding ->
    Box(
      modifier = Modifier
        .fillMaxSize()
        .padding(innerPadding),
      contentAlignment = Alignment.TopCenter
    ) {
      NavHost(
        navController = navController,
        startDestination = HomeRoute,
        modifier = Modifier.widthIn(max = 680.dp)
      ) {
        composable<HomeRoute> {
          HomeTab(
            state = state,
            isDuress = isDuress,
            onSelectTransfer = { selectedTransfer = it }
          )
        }
        composable<TransfersRoute> {
          TransfersScreen(
            state = state,
            onSubmitTransfer = onSubmitTransfer,
            onClearResult = onClearTransferResult
          )
        }
        composable<DepositsRoute> {
          DepositsScreen(
            state = state,
            onSubmitEarlyDepositWithdrawal = onSubmitEarlyDepositWithdrawal,
            onClearDepositResult = onClearDepositResult,
            onNavigateToSecondHand = {
              navController.navigate(ProtectionOrProfileRoute) {
                popUpTo(navController.graph.findStartDestination().id) {
                  saveState = true
                }
                launchSingleTop = true
                restoreState = true
              }
            }
          )
        }
        composable<ProtectionOrProfileRoute> {
          if (isDuress) {
            DuressNeutralProfileTab(state = state, onLockSession = onLockSession)
          } else {
            SecurityCenterScreen(
              state = state,
              initialSegment = securitySegment,
              onApproveSecondHand = onApproveSecondHand,
              onRejectSecondHandAsFraud = onRejectSecondHandAsFraud,
              onEscalateSecondHandToBackup = onEscalateSecondHandToBackup,
              onClearSecondHandResult = onClearSecondHandResult,
              onUpdateSecurityPolicy = onUpdateSecurityPolicy,
              onUpdateLoginPins = onUpdateLoginPins,
              onUpdateTransferCodes = onUpdateTransferCodes,
              onUpdateSecondHandPin = onUpdateSecondHandPin,
              onClearSecurityUpdateResult = onClearSecurityUpdateResult,
              onRefreshTimedActions = onRefreshTimedActions
            )
          }
        }
      }
    }
  }

  selectedTransfer?.let { transfer ->
    ModalBottomSheet(
      onDismissRequest = { selectedTransfer = null },
      containerColor = VaultSurface
    ) {
      TransferDetailSheet(transfer = transfer, isDuress = isDuress)
    }
  }
}

@Composable
private fun HomeTab(
  state: MainUiState,
  isDuress: Boolean,
  onSelectTransfer: (TransferRecord) -> Unit
) {
  val primaryAccount = remember(state.accounts, isDuress) {
    if (isDuress) {
      state.accounts.firstOrNull { it.type == AccountType.ISOLATED_SAFE }
    } else {
      state.accounts.firstOrNull { it.type == AccountType.MAIN }
    }
  }
  val totalDeposits = remember(state.deposits) { state.deposits.sumOf { it.balanceTenge } }

  LazyColumn(
    contentPadding = PaddingValues(16.dp),
    verticalArrangement = Arrangement.spacedBy(14.dp),
    modifier = Modifier
      .fillMaxSize()
      .testTag("home_feed")
  ) {
    item {
      Card(
        modifier = Modifier
          .fillMaxWidth()
          .testTag("primary_balance_card"),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = VaultSurfaceVariant),
        border = BorderStroke(1.dp, VaultOutline)
      ) {
        Box(
          modifier = Modifier
            .fillMaxWidth()
            .background(
              Brush.linearGradient(listOf(VaultSurfaceVariant, VaultSurface))
            )
            .padding(20.dp)
        ) {
          Column {
            Text(
              text = primaryAccount?.title ?: "Текущий счёт KZT",
              style = MaterialTheme.typography.bodyMedium,
              color = TextSecondary
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
              text = MoneyFormatter.formatTenge(primaryAccount?.balanceTenge ?: 0L),
              style = MonoAmountLarge,
              color = TextPrimary,
              modifier = Modifier.testTag("primary_balance_amount")
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
              text = MoneyFormatter.maskIban(primaryAccount?.iban.orEmpty()),
              style = MonoCodeStyle,
              color = TextMuted
            )
          }
        }
      }
    }

    if (!isDuress) {
      val isolated = state.accounts.firstOrNull { it.type == AccountType.ISOLATED_SAFE }
      val escrow = state.accounts.firstOrNull { it.type == AccountType.ESCROW_HOLD }
      if (isolated != null) {
        item {
          Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = VaultSurface),
            border = BorderStroke(1.dp, EmeraldShield.copy(alpha = 0.35f))
          ) {
            Row(
              modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically
            ) {
              Column {
                Text(
                  text = "Изолированный субсчёт",
                  style = MaterialTheme.typography.titleMedium,
                  color = TextPrimary
                )
                Text(
                  text = "Витрина для тревожного ПИН-кода",
                  style = MaterialTheme.typography.bodyMedium,
                  color = EmeraldShield
                )
              }
              Text(
                text = MoneyFormatter.formatTenge(isolated.balanceTenge),
                style = MonoCodeStyle,
                color = TextPrimary,
                fontWeight = FontWeight.SemiBold
              )
            }
          }
        }
      }
      if (escrow != null && escrow.balanceTenge > 0L) {
        item {
          Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = VaultSurface),
            border = BorderStroke(1.dp, AmberEscrow.copy(alpha = 0.45f))
          ) {
            Row(
              modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically
            ) {
              Column {
                Text(
                  text = "Soft-Escrow (удержание банка)",
                  style = MaterialTheme.typography.titleMedium,
                  color = TextPrimary
                )
                Text(
                  text = "Задержанный клиринг на проверке",
                  style = MaterialTheme.typography.bodyMedium,
                  color = AmberEscrow
                )
              }
              Text(
                text = MoneyFormatter.formatTenge(escrow.balanceTenge),
                style = MonoCodeStyle,
                color = AmberEscrow,
                fontWeight = FontWeight.SemiBold
              )
            }
          }
        }
      }
    }

    item {
      Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = VaultSurface),
        border = BorderStroke(1.dp, VaultOutline)
      ) {
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Column {
            Text(
              text = if (isDuress) "Мои депозиты" else "Скрытые депозиты (Money Lock)",
              style = MaterialTheme.typography.titleMedium,
              color = TextPrimary
            )
            Text(
              text = "${state.deposits.size} активных вклада",
              style = MaterialTheme.typography.bodyMedium,
              color = TextSecondary
            )
          }
          Text(
            text = MoneyFormatter.formatTenge(totalDeposits),
            style = MonoCodeStyle,
            color = EmeraldShield,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.testTag("total_deposits_amount")
          )
        }
      }
    }

    item {
      Text(
        text = "История операций",
        style = MaterialTheme.typography.titleMedium,
        color = TextPrimary,
        modifier = Modifier.padding(top = 4.dp)
      )
    }

    items(state.transfers, key = { it.id }) { transfer ->
      TransferRowCard(
        transfer = transfer,
        isDuress = isDuress,
        onClick = { onSelectTransfer(transfer) }
      )
    }
  }
}

@Composable
private fun TransferRowCard(
  transfer: TransferRecord,
  isDuress: Boolean,
  onClick: () -> Unit
) {
  val neutralStatus = when {
    isDuress && transfer.isDuressOperation -> "Выполнен"
    transfer.status == TransferStatus.COMPLETED -> "Выполнен"
    transfer.status == TransferStatus.COOLING_OFF ||
      transfer.status == TransferStatus.SOFT_ESCROW_HOLD ||
      transfer.status == TransferStatus.AWAITING_SECOND_HAND -> "В обработке"
    transfer.status == TransferStatus.BLOCKED_FRAUD -> if (isDuress) "В обработке" else "Отклонён"
    else -> "В обработке"
  }
  val dateStr = remember(transfer.createdAtMillis) {
    DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm")
      .withZone(ZoneId.of("Asia/Almaty"))
      .format(Instant.ofEpochMilli(transfer.createdAtMillis))
  }

  Card(
    onClick = onClick,
    modifier = Modifier.fillMaxWidth(),
    colors = CardDefaults.cardColors(containerColor = VaultSurface),
    border = BorderStroke(1.dp, VaultOutline)
  ) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(14.dp),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically
    ) {
      Column(modifier = Modifier.weight(1f)) {
        Text(
          text = transfer.recipientName,
          style = MaterialTheme.typography.bodyLarge,
          color = TextPrimary,
          fontWeight = FontWeight.Medium
        )
        Text(
          text = "$dateStr · $neutralStatus",
          style = MaterialTheme.typography.bodyMedium,
          color = TextSecondary
        )
      }
      Text(
        text = MoneyFormatter.formatTenge(-transfer.amountTenge),
        style = MonoCodeStyle,
        color = TextPrimary
      )
    }
  }
}

@Composable
private fun TransferDetailSheet(transfer: TransferRecord, isDuress: Boolean) {
  val formatter = remember {
    DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm").withZone(ZoneId.of("Asia/Almaty"))
  }
  Column(
    modifier = Modifier
      .fillMaxWidth()
      .padding(horizontal = 20.dp, vertical = 16.dp),
    verticalArrangement = Arrangement.spacedBy(10.dp)
  ) {
    Text(
      text = transfer.recipientName,
      style = MaterialTheme.typography.titleLarge,
      color = TextPrimary
    )
    Text(
      text = MoneyFormatter.formatTenge(-transfer.amountTenge),
      style = MonoAmountLarge,
      color = TextPrimary
    )
    Text(
      text = "Счёт получателя: ${transfer.recipientIban}",
      style = MonoCodeStyle,
      color = TextSecondary
    )
    if (!isDuress) {
      val statusColor = when (transfer.status) {
        TransferStatus.COMPLETED -> EmeraldShield
        TransferStatus.BLOCKED_FRAUD, TransferStatus.SOFT_ESCROW_HOLD -> RubyRisk
        else -> AmberEscrow
      }
      Text(
        text = "Внутренний статус: ${transfer.status.name} · Риск: ${transfer.riskScore}/100",
        style = MaterialTheme.typography.bodyMedium,
        color = statusColor
      )
      transfer.coolingOffUntilMillis?.let { until ->
        Text(
          text = "Пауза Cooling-Off до: ${formatter.format(Instant.ofEpochMilli(until))}",
          style = MonoCodeStyle,
          color = AmberEscrow
        )
      }
      transfer.reviewDeadlineMillis?.let { deadline ->
        Text(
          text = "Срок проверки Soft-Escrow до: ${formatter.format(Instant.ofEpochMilli(deadline))}",
          style = MonoCodeStyle,
          color = RubyRisk
        )
      }
      Text(
        text = "Факторы риска: ${transfer.riskFactorsSummary}",
        style = MaterialTheme.typography.bodyMedium,
        color = TextSecondary
      )
    } else {
      Text(
        text = "Статус операции: Выполнен",
        style = MaterialTheme.typography.bodyMedium,
        color = EmeraldShield
      )
    }
    Spacer(modifier = Modifier.height(16.dp))
  }
}

@Composable
private fun DuressNeutralProfileTab(
  state: MainUiState,
  onLockSession: () -> Unit
) {
  val safeAccount = state.accounts.firstOrNull { it.type == AccountType.ISOLATED_SAFE }
  Column(
    modifier = Modifier
      .fillMaxSize()
      .padding(16.dp),
    verticalArrangement = Arrangement.spacedBy(14.dp)
  ) {
    Text("Профиль", style = MaterialTheme.typography.headlineSmall, color = TextPrimary)
    Card(
      modifier = Modifier.fillMaxWidth(),
      colors = CardDefaults.cardColors(containerColor = VaultSurface),
      border = BorderStroke(1.dp, VaultOutline)
    ) {
      Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("Счёт обслуживания", style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
        Text(safeAccount?.iban.orEmpty(), style = MonoCodeStyle, color = TextPrimary)
        Text("Тариф: Стандарт KZT", style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
      }
    }
    Button(
      onClick = onLockSession,
      colors = ButtonDefaults.buttonColors(containerColor = VaultSurfaceVariant),
      modifier = Modifier
        .fillMaxWidth()
        .height(52.dp)
        .testTag("lock_session_button")
    ) {
      Icon(Icons.Filled.Lock, contentDescription = null, tint = TextPrimary)
      Spacer(modifier = Modifier.size(8.dp))
      Text("Выйти и заблокировать", color = TextPrimary)
    }
  }
}
