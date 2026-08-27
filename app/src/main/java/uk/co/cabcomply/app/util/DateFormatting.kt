package uk.co.cabcomply.app.util

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** UK-friendly display formatting. Internally every timestamp is stored as epoch millis (Long). */
object DateFormatting {
    private val zone = ZoneId.systemDefault()
    private val dateFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy")
    private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm")
    private val dateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")
    private val dayMonthFormatter = DateTimeFormatter.ofPattern("EEE d MMM")

    fun formatDate(millis: Long): String = Instant.ofEpochMilli(millis).atZone(zone).format(dateFormatter)
    fun formatTime(millis: Long): String = Instant.ofEpochMilli(millis).atZone(zone).format(timeFormatter)
    fun formatDateTime(millis: Long): String = Instant.ofEpochMilli(millis).atZone(zone).format(dateTimeFormatter)
    fun formatDayMonth(millis: Long): String = Instant.ofEpochMilli(millis).atZone(zone).format(dayMonthFormatter)
}
