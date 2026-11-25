package com.qalqan.domain.clock

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.concurrent.atomic.AtomicLong

interface AppClock {
  fun nowMillis(): Long
  fun zoneId(): ZoneId
  fun nowInstant(): Instant = Instant.ofEpochMilli(nowMillis())
  fun localDateTime(): LocalDateTime = LocalDateTime.ofInstant(nowInstant(), zoneId())
  fun localDate(): LocalDate = localDateTime().toLocalDate()
}

class BusinessCalendar(
  private val holidays: Set<LocalDate> = emptySet()
) {
  fun isBusinessDay(date: LocalDate): Boolean {
    val dow = date.dayOfWeek
    if (dow == DayOfWeek.SATURDAY || dow == DayOfWeek.SUNDAY) return false
    return date !in holidays
  }

  fun addBusinessDays(start: LocalDate, businessDays: Int): LocalDate {
    require(businessDays >= 0) { "Количество рабочих дней не может быть отрицательным" }
    var current = start
    var remaining = businessDays
    while (remaining > 0) {
      current = current.plusDays(1)
      if (isBusinessDay(current)) {
        remaining--
      }
    }
    return current
  }

  fun businessDaysToCalendarDays(start: LocalDate, businessDays: Int): Long {
    val target = addBusinessDays(start, businessDays)
    return java.time.temporal.ChronoUnit.DAYS.between(start, target)
  }
}

class SimulatedClock(
  initialMillis: Long = System.currentTimeMillis(),
  private val zone: ZoneId = ZoneId.of("Asia/Almaty")
) : AppClock {
  private val currentMillis = AtomicLong(initialMillis)

  override fun nowMillis(): Long = currentMillis.get()

  override fun zoneId(): ZoneId = zone

  fun advanceByMillis(deltaMillis: Long) {
    require(deltaMillis >= 0) { "Перемотка времени назад не поддерживается" }
    currentMillis.addAndGet(deltaMillis)
  }

  fun advanceByMinutes(minutes: Long) = advanceByMillis(minutes * 60_000L)

  fun advanceByHours(hours: Long) = advanceByMillis(hours * 3_600_000L)

  fun advanceByDays(days: Long) = advanceByMillis(days * 86_400_000L)

  fun advanceByBusinessDays(businessDays: Int, calendar: BusinessCalendar = BusinessCalendar()) {
    val startDate = localDate()
    val daysToAdd = calendar.businessDaysToCalendarDays(startDate, businessDays)
    advanceByDays(daysToAdd)
  }

  fun setMillis(epochMillis: Long) {
    currentMillis.set(epochMillis)
  }
}
