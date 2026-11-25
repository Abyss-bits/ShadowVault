package com.qalqan.domain.validation

sealed interface PinValidationResult {
  data object Valid : PinValidationResult
  data class Invalid(val reason: String) : PinValidationResult
}

object PinPolicyValidator {
  private val trivialPins = setOf(
    "0000", "1111", "2222", "3333", "4444",
    "5555", "6666", "7777", "8888", "9999",
    "1234", "4321", "0123", "3210", "1212",
    "2121", "1122", "2211", "1010", "2020"
  )

  fun hammingDistance(pinA: String, pinB: String): Int {
    if (pinA.length != pinB.length) return Int.MAX_VALUE
    var diff = 0
    for (i in pinA.indices) {
      if (pinA[i] != pinB[i]) diff++
    }
    return diff
  }

  fun isTrivialPin(pin: String): Boolean {
    if (pin.length != 4 || !pin.all { it.isDigit() }) return true
    if (pin in trivialPins) return true
    val digits = pin.map { it.digitToInt() }
    val allSame = digits.distinct().size == 1
    val ascending = (0..2).all { digits[it + 1] - digits[it] == 1 }
    val descending = (0..2).all { digits[it] - digits[it + 1] == 1 }
    return allSame || ascending || descending
  }

  fun validateNewPin(
    newPin: String,
    conflictingPin: String? = null
  ): PinValidationResult {
    if (newPin.length != 4 || !newPin.all { it.isDigit() }) {
      return PinValidationResult.Invalid("ПИН-код должен состоять ровно из 4 цифр")
    }
    if (isTrivialPin(newPin)) {
      return PinValidationResult.Invalid("Выберите более сложный ПИН-код (запрещены простые комбинации)")
    }
    if (conflictingPin != null && hammingDistance(newPin, conflictingPin) < 2) {
      return PinValidationResult.Invalid(
        "ПИН-код слишком похож на тревожный/основной (должно отличаться минимум 2 цифры)"
      )
    }
    return PinValidationResult.Valid
  }

  fun calculateLockoutDurationMillis(failedAttempts: Int): Long {
    if (failedAttempts < 5) return 0L
    val step = failedAttempts - 5
    // 30s, 60s, 120s, 240s, capped at 15 min
    val seconds = (30L shl step.coerceAtMost(5)).coerceAtMost(900L)
    return seconds * 1_000L
  }
}

object KzIdentifierValidator {
  private val kzIbanRegex = Regex("^KZ\\d{2}[A-Z0-9]{16}$")
  private val kzBinRegex = Regex("^\\d{12}$")

  fun isValidKzIban(rawIban: String): Boolean {
    val clean = rawIban.replace("\\s+".toRegex(), "").uppercase()
    if (!kzIbanRegex.matches(clean)) return false
    val rearranged = clean.drop(4) + clean.take(4)
    val numeric = buildString {
      for (ch in rearranged) {
        if (ch.isDigit()) append(ch) else append(ch.code - 'A'.code + 10)
      }
    }
    var remainder = 0
    for (ch in numeric) {
      remainder = (remainder * 10 + (ch - '0')) % 97
    }
    return remainder == 1
  }

  fun isValidKzBin(rawBin: String): Boolean = kzBinRegex.matches(rawBin.trim())

  fun buildValidKzIban(bankCode3Digits: String, accountTail13Digits: String): String {
    require(bankCode3Digits.length == 3 && bankCode3Digits.all { it.isDigit() })
    require(accountTail13Digits.length == 13 && accountTail13Digits.all { it.isDigit() })
    val bban = bankCode3Digits + accountTail13Digits
    val rearranged = bban + "KZ00"
    val numeric = buildString {
      for (ch in rearranged) {
        if (ch.isDigit()) append(ch) else append(ch.code - 'A'.code + 10)
      }
    }
    var remainder = 0
    for (ch in numeric) {
      remainder = (remainder * 10 + (ch - '0')) % 97
    }
    val checkDigits = (98 - remainder).toString().padStart(2, '0')
    return "KZ$checkDigits$bban"
  }
}
