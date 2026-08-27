package uk.co.cabcomply.app.util

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

/** Thin wrapper around system time so "now" is consistent across one operation and swappable in tests. */
@Singleton
class AppClock @Inject constructor() {
    fun nowMillis(): Long = System.currentTimeMillis()

    fun zoneId(): ZoneId = ZoneId.systemDefault()

    /** Start-of-day epoch millis for [millis], in the device's local zone — the stable key used for "today". */
    fun startOfDay(millis: Long = nowMillis()): Long =
        Instant.ofEpochMilli(millis).atZone(zoneId()).toLocalDate().atStartOfDay(zoneId()).toInstant().toEpochMilli()

    fun today(): LocalDate = Instant.ofEpochMilli(nowMillis()).atZone(zoneId()).toLocalDate()
}
