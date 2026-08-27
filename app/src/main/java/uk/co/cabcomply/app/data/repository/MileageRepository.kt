package uk.co.cabcomply.app.data.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import uk.co.cabcomply.app.data.db.dao.MileageDao
import uk.co.cabcomply.app.data.db.dao.VehicleDao
import uk.co.cabcomply.app.data.db.entity.MileageEntryEntity
import uk.co.cabcomply.app.data.db.entity.MileagePurpose
import uk.co.cabcomply.app.util.AppClock
import uk.co.cabcomply.app.util.Ids
import uk.co.cabcomply.app.util.MileageConsistency
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MileageRepository @Inject constructor(
    private val dao: MileageDao,
    private val vehicleDao: VehicleDao,
    private val clock: AppClock
) {
    fun observeFiltered(vehicleId: String?, fromDate: Long?, toDate: Long?): Flow<List<MileageEntryEntity>> =
        dao.observeFiltered(vehicleId, fromDate, toDate)

    suspend fun getFilteredSnapshot(vehicleId: String?, fromDate: Long?, toDate: Long?): List<MileageEntryEntity> =
        observeFiltered(vehicleId, fromDate, toDate).first()

    fun observeFlagged(): Flow<List<MileageEntryEntity>> = dao.observeFlagged()
    suspend fun getById(id: String): MileageEntryEntity? = dao.getById(id)
    fun observeById(id: String): Flow<MileageEntryEntity?> = dao.observeById(id)

    /**
     * The best known mileage for this vehicle only, whichever is more recent: the last completed
     * mileage entry's end reading, or the vehicle's own odometer (kept in sync by both this
     * repository and a completed daily check). Never borrowed from another vehicle.
     */
    suspend fun getSuggestedStartMileage(vehicleId: String): Int? {
        val lastEntryEnd = dao.getLatestCompletedForVehicle(vehicleId)?.endMileage ?: 0
        val vehicleOdometer = vehicleDao.getById(vehicleId)?.currentOdometer ?: 0
        val suggestion = maxOf(lastEntryEnd, vehicleOdometer)
        return if (suggestion > 0) suggestion else null
    }

    suspend fun saveEntry(
        id: String?,
        vehicleId: String,
        startMileage: Int,
        endMileage: Int?,
        entryDate: Long,
        startedAt: Long,
        endedAt: Long?,
        purpose: MileagePurpose,
        notes: String?
    ): MileageEntryEntity {
        val now = clock.nowMillis()
        val existing = id?.let { dao.getById(it) }
        val savedId = existing?.id ?: Ids.newId()
        val entry = existing?.copy(
            vehicleId = vehicleId,
            startMileage = startMileage,
            endMileage = endMileage,
            entryDate = entryDate,
            startedAt = startedAt,
            endedAt = endedAt,
            purpose = purpose,
            notes = notes
        ) ?: MileageEntryEntity(
            id = savedId,
            vehicleId = vehicleId,
            startMileage = startMileage,
            endMileage = endMileage,
            entryDate = entryDate,
            startedAt = startedAt,
            endedAt = endedAt,
            purpose = purpose,
            notes = notes,
            isFlagged = false,
            flagReason = null,
            createdAt = now
        )
        dao.upsert(entry)

        // Keep the vehicle's own odometer in step with mileage entries too, so Daily Check's
        // odometer prefill and this screen's start-mileage prefill never drift apart.
        if (endMileage != null) {
            vehicleDao.getById(vehicleId)?.let { vehicle ->
                if (endMileage > vehicle.currentOdometer) {
                    vehicleDao.upsert(vehicle.copy(currentOdometer = endMileage, updatedAt = now))
                }
            }
        }

        // Re-derive flags for the whole vehicle, not just this entry: editing an earlier
        // entry can create (or resolve) an overlap with entries that come after it.
        reconcileFlags(vehicleId)
        return dao.getById(savedId) ?: entry
    }

    private suspend fun reconcileFlags(vehicleId: String) {
        val all = dao.getAllForVehicle(vehicleId)
        val reasonsByEntryId = MileageConsistency.analyse(all)
        all.forEach { entry ->
            val reason = reasonsByEntryId[entry.id]
            if (entry.isFlagged != (reason != null) || entry.flagReason != reason) {
                dao.upsert(entry.copy(isFlagged = reason != null, flagReason = reason))
            }
        }
    }

    /**
     * The odometer reading a driver enters during a Daily Check is a real mileage reading and
     * must show up in Mileage records too, not just live on the inspection. Creates an open
     * (no end mileage yet) entry starting at that reading, but only if there isn't already a
     * mileage entry for this vehicle today — never duplicates one the driver already logged
     * manually.
     */
    suspend fun ensureDailyCheckStartEntry(vehicleId: String, odometer: Int, dayStart: Long, timestamp: Long) {
        val dayEnd = dayStart + 86_400_000L - 1
        if (dao.countForVehicleInDay(vehicleId, dayStart, dayEnd) > 0) return
        dao.upsert(
            MileageEntryEntity(
                id = Ids.newId(),
                vehicleId = vehicleId,
                startMileage = odometer,
                endMileage = null,
                entryDate = dayStart,
                startedAt = timestamp,
                endedAt = null,
                purpose = MileagePurpose.BUSINESS,
                notes = "Recorded from Daily Vehicle Check",
                isFlagged = false,
                flagReason = null,
                createdAt = timestamp
            )
        )
        reconcileFlags(vehicleId)
    }
}
