package uk.co.cabcomply.app.data.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import uk.co.cabcomply.app.data.db.dao.MileageDao
import uk.co.cabcomply.app.data.db.dao.VehicleDao
import uk.co.cabcomply.app.data.db.entity.MileageEntryEntity
import uk.co.cabcomply.app.data.db.entity.MileagePurpose
import uk.co.cabcomply.app.util.AppClock
import uk.co.cabcomply.app.util.Ids
import javax.inject.Inject
import javax.inject.Singleton

private const val LARGE_JUMP_MILES = 500

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
        val previous = dao.getLatestCompletedForVehicle(vehicleId)
        val flags = mutableListOf<String>()

        if (endMileage != null && endMileage < startMileage) {
            flags += "End mileage ($endMileage) is lower than start mileage ($startMileage)."
        }
        if (previous != null && previous.endMileage != null && id != previous.id && startMileage < previous.endMileage) {
            flags += "Start mileage ($startMileage) is lower than the last recorded end mileage (${previous.endMileage}) for this vehicle."
        }
        if (endMileage != null) {
            val distance = endMileage - startMileage
            if (distance > LARGE_JUMP_MILES) {
                flags += "This entry covers $distance miles, which is unusually large for one segment."
            }
        }

        val now = clock.nowMillis()
        val existing = id?.let { dao.getById(it) }
        val entry = existing?.copy(
            vehicleId = vehicleId,
            startMileage = startMileage,
            endMileage = endMileage,
            entryDate = entryDate,
            startedAt = startedAt,
            endedAt = endedAt,
            purpose = purpose,
            notes = notes,
            isFlagged = flags.isNotEmpty(),
            flagReason = flags.joinToString(" ").ifBlank { null }
        ) ?: MileageEntryEntity(
            id = Ids.newId(),
            vehicleId = vehicleId,
            startMileage = startMileage,
            endMileage = endMileage,
            entryDate = entryDate,
            startedAt = startedAt,
            endedAt = endedAt,
            purpose = purpose,
            notes = notes,
            isFlagged = flags.isNotEmpty(),
            flagReason = flags.joinToString(" ").ifBlank { null },
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

        return entry
    }
}
