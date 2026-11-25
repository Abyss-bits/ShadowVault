package com.qalqan.domain.util

import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale

object MoneyFormatter {
  private val symbols = DecimalFormatSymbols(Locale.forLanguageTag("ru-KZ")).apply {
    groupingSeparator = ' '
  }

  fun formatTenge(amount: Long, showPlus: Boolean = false): String {
    val absValue = kotlin.math.abs(amount)
    val formatted = DecimalFormat("#,##0", symbols).format(absValue)
    val prefix = when {
      amount < 0 -> "−"
      showPlus && amount > 0 -> "+"
      else -> ""
    }
    return "$prefix$formatted ₸"
  }

  fun maskIban(iban: String): String {
    val clean = iban.replace(" ", "")
    if (clean.length < 8) return clean
    return "${clean.take(4)} •••• ${clean.takeLast(4)}"
  }
}
