package com.example.demo

import com.example.data.repository.QalqanRepository
import com.example.presentation.DemoScenarioOutcome
import com.example.presentation.DemoTabTarget
import com.qalqan.domain.model.UserRole

object DemoScenarioBridge {
  const val IS_AVAILABLE: Boolean = false

  suspend fun runScenario1Robbery(repository: QalqanRepository): DemoScenarioOutcome? = null

  suspend fun runScenario2PhoneBlackmail(repository: QalqanRepository): DemoScenarioOutcome? = null

  suspend fun runScenario3DepositSecondHand(repository: QalqanRepository): DemoScenarioOutcome? = null

  suspend fun switchRole(repository: QalqanRepository, role: UserRole): DemoTabTarget =
    DemoTabTarget.HOME
}
