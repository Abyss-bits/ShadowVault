package com.example.crypto

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PinHasher @Inject constructor() {
  private val random = SecureRandom()

  fun hashSecret(rawPin: String): String {
    val salt = ByteArray(SALT_BYTES).also { random.nextBytes(it) }
    val derived = pbkdf2(rawPin.toCharArray(), salt)
    val saltB64 = Base64.getEncoder().encodeToString(salt)
    val hashB64 = Base64.getEncoder().encodeToString(derived)
    return "$saltB64:$hashB64"
  }

  fun verifySecret(rawPin: String, storedValue: String): Boolean {
    val parts = storedValue.split(":")
    if (parts.size != 2) return false
    return try {
      val salt = Base64.getDecoder().decode(parts[0])
      val expected = Base64.getDecoder().decode(parts[1])
      val actual = pbkdf2(rawPin.toCharArray(), salt)
      MessageDigest.isEqual(expected, actual)
    } catch (_: IllegalArgumentException) {
      false
    }
  }

  private fun pbkdf2(chars: CharArray, salt: ByteArray): ByteArray {
    val spec = PBEKeySpec(chars, salt, ITERATIONS, KEY_LENGTH_BITS)
    return try {
      SecretKeyFactory.getInstance(ALGORITHM).generateSecret(spec).encoded
    } finally {
      spec.clearPassword()
    }
  }

  companion object {
    private const val ALGORITHM = "PBKDF2WithHmacSHA256"
    private const val ITERATIONS = 120_000
    private const val KEY_LENGTH_BITS = 256
    private const val SALT_BYTES = 16
  }
}
