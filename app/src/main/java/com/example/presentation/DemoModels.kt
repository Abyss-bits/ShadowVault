package com.example.presentation

enum class DemoTabTarget {
  HOME,
  TRANSFERS,
  DEPOSITS,
  SECURITY_SECOND_HAND,
  SECURITY_NBRK
}

data class DemoScenarioOutcome(
  val summaryMessage: String,
  val targetTab: DemoTabTarget
)
