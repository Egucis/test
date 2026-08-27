package uk.co.cabcomply.app.util

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** UK tax year runs 6 April to 5 April. Kept separate from mileage records so a future HMRC
 *  mileage-rate change never needs to touch historical [uk.co.cabcomply.app.data.db.entity.MileageEntryEntity] rows. */
data class UkTaxYear(val startYear: Int) {
    val label: String get() = "$startYear/${(startYear + 1).toString().takeLast(2)}"
    val start: LocalDate get() = LocalDate.of(startYear, 4, 6)
    val endExclusive: LocalDate get() = LocalDate.of(startYear + 1, 4, 6)

    fun startMillis(zone: ZoneId): Long = start.atStartOfDay(zone).toInstant().toEpochMilli()
    fun endMillisExclusive(zone: ZoneId): Long = endExclusive.atStartOfDay(zone).toInstant().toEpochMilli()

    companion object {
        fun forDate(date: LocalDate): UkTaxYear {
            val boundary = LocalDate.of(date.year, 4, 6)
            val startYear = if (date.isBefore(boundary)) date.year - 1 else date.year
            return UkTaxYear(startYear)
        }

        fun forMillis(millis: Long, zone: ZoneId): UkTaxYear =
            forDate(Instant.ofEpochMilli(millis).atZone(zone).toLocalDate())
    }
}
