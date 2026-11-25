package com.example.demo

import com.example.data.repository.QalqanRepository
import com.example.data.seed.DemoSeeder
import com.example.presentation.DemoScenarioOutcome
import com.example.presentation.DemoTabTarget
import com.qalqan.domain.model.SessionMode
import com.qalqan.domain.model.UserRole
import com.qalqan.domain.validation.KzIdentifierValidator

object DemoScenarioBridge {
  const val IS_AVAILABLE: Boolean = true

  suspend fun runScenario1Robbery(repository: QalqanRepository): DemoScenarioOutcome {
    repository.resetDemoData()
    repository.authenticatePin("9110")
    repository.submitTransfer(
      recipientName = "Срочный перевод",
      recipientIban = KzIdentifierValidator.buildValidKzIban("947", "0001234567890"),
      amountTenge = 20_000L,
      transferCode = "5555"
    )
    return DemoScenarioOutcome(
      summaryMessage = "Сценарий 1 выполнен: вход по тревожному ПИН (48 500 ₸) и тихий перевод 20 000 ₸ через ESCROW_HOLD.",
      targetTab = DemoTabTarget.HOME
    )
  }

  suspend fun runScenario2PhoneBlackmail(repository: QalqanRepository): DemoScenarioOutcome {
    repository.resetDemoData()
    repository.authenticatePin("1234")
    repository.submitTransfer(
      recipientName = "Лже-служба безопасности",
      recipientIban = KzIdentifierValidator.buildValidKzIban("722", "0009998887771"),
      amountTenge = 350_000L,
      transferCode = "9999"
    )
    repository.switchRoleInStandardMode(UserRole.NBRK_INSPECTOR)
    return DemoScenarioOutcome(
      summaryMessage = "Сценарий 2 выполнен: код шантажа заморозил 350 000 ₸ в Soft-Escrow (5 раб. дн.), инцидент отправлен в симулятор НБРК.",
      targetTab = DemoTabTarget.SECURITY_NBRK
    )
  }

  suspend fun runScenario3DepositSecondHand(repository: QalqanRepository): DemoScenarioOutcome {
    repository.resetDemoData()
    repository.authenticatePin("1234")
    repository.submitEarlyDepositWithdrawal(
      depositId = DemoSeeder.DEP_RESERVE,
      recipientName = "Новый контрагент (тест)",
      recipientIban = KzIdentifierValidator.buildValidKzIban("856", "0001122334455"),
      amountTenge = 1_200_000L,
      transferCode = "5555"
    )
    repository.switchRoleInStandardMode(UserRole.SECOND_HAND)
    return DemoScenarioOutcome(
      summaryMessage = "Сценарий 3 выполнен: создана заявка «Второй руки» по депозиту «Накопительный сейф» (1 200 000 ₸).",
      targetTab = DemoTabTarget.SECURITY_SECOND_HAND
    )
  }

  suspend fun switchRole(repository: QalqanRepository, role: UserRole): DemoTabTarget {
    if (role == UserRole.CLIENT_DURESS) {
      if (repository.sessionMode.value != SessionMode.DURESS) {
        repository.lockSession()
        repository.authenticatePin("9110")
      }
      return DemoTabTarget.HOME
    }
    if (repository.sessionMode.value != SessionMode.STANDARD) {
      repository.lockSession()
      repository.authenticatePin("1234")
    }
    repository.switchRoleInStandardMode(role)
    return when (role) {
      UserRole.SECOND_HAND -> DemoTabTarget.SECURITY_SECOND_HAND
      UserRole.NBRK_INSPECTOR -> DemoTabTarget.SECURITY_NBRK
      else -> DemoTabTarget.HOME
    }
  }
}
