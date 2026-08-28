package uk.co.tripassistant.app.util

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Day boundaries in the driver's own time zone.
 *
 * "Today" has to mean the driver's today, not UTC's: a shift that ends at 02:00 belongs to the
 * night it started for the driver, and a stats screen that disagrees with the clock in the car is
 * worse than no stats screen.
 */
object DayRange {

    fun today(zone: ZoneId = ZoneId.systemDefault()): LongRange = day(LocalDate.now(zone), zone)

    fun day(date: LocalDate, zone: ZoneId = ZoneId.systemDefault()): LongRange {
        val start = date.atStartOfDay(zone).toInstant().toEpochMilli()
        val end = date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli() - 1
        return start..end
    }

    /** The last [days] days including today. */
    fun lastDays(days: Int, zone: ZoneId = ZoneId.systemDefault()): LongRange {
        val today = LocalDate.now(zone)
        val start = today.minusDays((days - 1).toLong()).atStartOfDay(zone).toInstant().toEpochMilli()
        val end = today.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli() - 1
        return start..end
    }

    fun between(from: LocalDate, to: LocalDate, zone: ZoneId = ZoneId.systemDefault()): LongRange {
        val start = from.atStartOfDay(zone).toInstant().toEpochMilli()
        val end = to.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli() - 1
        return start..end
    }
}

/** Consistent date and time formatting across history, detail and diagnostics. */
object Timestamps {

    private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm", Locale.UK)
    private val dateFormatter = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.UK)
    private val dateTimeFormatter = DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm", Locale.UK)

    fun time(millis: Long, zone: ZoneId = ZoneId.systemDefault()): String =
        timeFormatter.format(Instant.ofEpochMilli(millis).atZone(zone))

    fun date(millis: Long, zone: ZoneId = ZoneId.systemDefault()): String =
        dateFormatter.format(Instant.ofEpochMilli(millis).atZone(zone))

    fun dateTime(millis: Long, zone: ZoneId = ZoneId.systemDefault()): String =
        dateTimeFormatter.format(Instant.ofEpochMilli(millis).atZone(zone))
}
