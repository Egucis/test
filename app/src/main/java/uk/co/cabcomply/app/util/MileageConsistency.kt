package uk.co.cabcomply.app.util

import uk.co.cabcomply.app.data.db.entity.MileageEntryEntity

private const val LARGE_JUMP_MILES = 500

/**
 * Re-derives every mileage-consistency flag for one vehicle from its whole entry list, rather
 * than only checking the entry just saved against whichever entry happened to be "latest" at
 * save time. That means editing an earlier entry correctly re-flags (or un-flags) entries that
 * come after it too, instead of leaving stale flags behind.
 */
object MileageConsistency {
    fun analyse(entries: List<MileageEntryEntity>): Map<String, String> {
        val ordered = entries.sortedWith(compareBy({ it.entryDate }, { it.startedAt }))
        val flags = mutableMapOf<String, MutableList<String>>()

        fun flag(id: String, message: String) {
            flags.getOrPut(id) { mutableListOf() } += message
        }

        ordered.forEach { entry ->
            val end = entry.endMileage
            if (end != null && end < entry.startMileage) {
                flag(entry.id, "End mileage ($end) is lower than start mileage (${entry.startMileage}).")
            }
            if (end != null) {
                val distance = end - entry.startMileage
                if (distance > LARGE_JUMP_MILES) {
                    flag(entry.id, "This entry covers $distance miles, which is unusually large for one segment.")
                }
            }
        }

        var previousCompleted: MileageEntryEntity? = null
        ordered.forEach { entry ->
            val end = entry.endMileage
            if (end == null || end < entry.startMileage) return@forEach
            val previous = previousCompleted
            if (previous != null && entry.startMileage < previous.endMileage!!) {
                flag(
                    entry.id,
                    "Start mileage (${entry.startMileage}) is lower than the last recorded end mileage (${previous.endMileage})."
                )
            }
            previousCompleted = entry
        }

        return flags.mapValues { it.value.joinToString(" ") }
    }
}
