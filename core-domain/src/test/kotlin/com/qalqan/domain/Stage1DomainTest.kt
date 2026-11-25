package com.qalqan.domain

import com.qalqan.domain.clock.BusinessCalendar
import com.qalqan.domain.clock.SimulatedClock
import com.qalqan.domain.validation.KzIdentifierValidator
import com.qalqan.domain.validation.PinPolicyValidator
import com.qalqan.domain.validation.PinValidationResult
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class Stage1DomainTest {

  @Test
  fun `hamming distance and trivial pin rules reject weak or close pins`() {
    assertEquals(1, PinPolicyValidator.hammingDistance("1234", "1235"))
    assertEquals(2, PinPolicyValidator.hammingDistance("1234", "1299"))
    assertTrue(PinPolicyValidator.isTrivialPin("1111"))
    assertTrue(PinPolicyValidator.isTrivialPin("1234"))
    assertTrue(PinPolicyValidator.isTrivialPin("4321"))
    assertFalse(PinPolicyValidator.isTrivialPin("4829"))

    val closeToDuress = PinPolicyValidator.validateNewPin(
      newPin = "9115",
      conflictingPin = "9110"
    )
    assertTrue(closeToDuress is PinValidationResult.Invalid)

    val validCustomPin = PinPolicyValidator.validateNewPin(
      newPin = "4829",
      conflictingPin = "9110"
    )
    assertEquals(PinValidationResult.Valid, validCustomPin)
  }

  @Test
  fun `lockout duration escalates after 5 failed attempts`() {
    assertEquals(0L, PinPolicyValidator.calculateLockoutDurationMillis(4))
    assertEquals(30_000L, PinPolicyValidator.calculateLockoutDurationMillis(5))
    assertEquals(60_000L, PinPolicyValidator.calculateLockoutDurationMillis(6))
    assertEquals(120_000L, PinPolicyValidator.calculateLockoutDurationMillis(7))
  }

  @Test
  fun `kz iban builder creates valid mod97 ibans`() {
    val iban1 = KzIdentifierValidator.buildValidKzIban("601", "0001234567890")
    val iban2 = KzIdentifierValidator.buildValidKzIban("947", "0009876543210")
    assertTrue(KzIdentifierValidator.isValidKzIban(iban1))
    assertTrue(KzIdentifierValidator.isValidKzIban(iban2))
    assertFalse(KzIdentifierValidator.isValidKzIban("KZ006010001234567890"))
    assertTrue(KzIdentifierValidator.isValidKzBin("210440012345"))
  }

  @Test
  fun `business calendar skips weekends and configured holidays`() {
    val friday = LocalDate.of(2026, 11, 20) // Friday
    val holidayMonday = LocalDate.of(2026, 11, 23)
    val defaultCal = BusinessCalendar()
    assertEquals(LocalDate.of(2026, 11, 27), defaultCal.addBusinessDays(friday, 5))

    val holidayCal = BusinessCalendar(holidays = setOf(holidayMonday))
    assertEquals(LocalDate.of(2026, 11, 30), holidayCal.addBusinessDays(friday, 5))

    val clock = SimulatedClock(
      initialMillis = Instant.parse("2026-11-20T10:00:00Z").toEpochMilli(),
      zone = ZoneId.of("UTC")
    )
    clock.advanceByBusinessDays(5, defaultCal)
    assertEquals(LocalDate.of(2026, 11, 27), clock.localDate())
  }
}
