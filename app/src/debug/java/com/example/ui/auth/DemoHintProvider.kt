package com.example.ui.auth

object DemoHintProvider {
  const val IS_DEMO_ENABLED: Boolean = true
  val hintLines: List<String> = listOf(
    "Обычный ПИН / Тревожный ПИН задаются демо-сидером (1234 / 9110)",
    "Тройной тап по логотипу — Демо-панель и «Вторая рука»"
  )
}
