package uk.co.cabcomply.app.data.repository

import kotlinx.coroutines.flow.Flow
import uk.co.cabcomply.app.data.db.dao.VehicleDao
import uk.co.cabcomply.app.data.db.entity.VehicleEntity
import uk.co.cabcomply.app.util.AppClock
import uk.co.cabcomply.app.util.Ids
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class VehicleRepository @Inject constructor(
    private val dao: VehicleDao,
    private val clock: AppClock
) {
    fun observeActiveVehicles(): Flow<List<VehicleEntity>> = dao.observeActiveVehicles()
    fun observeArchivedVehicles(): Flow<List<VehicleEntity>> = dao.observeArchivedVehicles()
    fun observeAllVehicles(): Flow<List<VehicleEntity>> = dao.observeAllVehicles()
    fun observeActiveVehicle(): Flow<VehicleEntity?> = dao.observeActiveVehicle()
    suspend fun getActiveVehicle(): VehicleEntity? = dao.getActiveVehicle()
    suspend fun getById(id: String): VehicleEntity? = dao.getById(id)
    fun observeById(id: String): Flow<VehicleEntity?> = dao.observeById(id)
    suspend fun countActiveVehicles(): Int = dao.countActiveVehicles()

    /**
     * Creates or updates a vehicle. The very first vehicle in the system is made active
     * automatically; otherwise activeness is left untouched unless [makeActive] is set.
     */
    suspend fun saveVehicle(
        id: String?,
        registration: String,
        make: String,
        model: String,
        licensingAuthorityId: String?,
        plateNumber: String?,
        licenceExpiryDate: Long?,
        currentOdometer: Int,
        makeActive: Boolean = false
    ): VehicleEntity {
        require(registration.isNotBlank()) { "Vehicle registration is required." }
        val now = clock.nowMillis()
        val existing = id?.let { dao.getById(it) }
        val isFirstVehicle = existing == null && dao.countActiveVehicles() == 0
        val vehicle = existing?.copy(
            registration = registration.trim().uppercase(),
            make = make.trim(),
            model = model.trim(),
            licensingAuthorityId = licensingAuthorityId,
            plateNumber = plateNumber?.trim()?.ifBlank { null },
            licenceExpiryDate = licenceExpiryDate,
            currentOdometer = currentOdometer,
            updatedAt = now
        ) ?: VehicleEntity(
            id = Ids.newId(),
            registration = registration.trim().uppercase(),
            make = make.trim(),
            model = model.trim(),
            licensingAuthorityId = licensingAuthorityId,
            plateNumber = plateNumber?.trim()?.ifBlank { null },
            licenceExpiryDate = licenceExpiryDate,
            currentOdometer = currentOdometer,
            isActive = false,
            isArchived = false,
            createdAt = now,
            updatedAt = now
        )
        dao.upsert(vehicle)
        if (makeActive || isFirstVehicle) {
            dao.setActiveVehicle(vehicle.id)
        }
        return vehicle
    }

    suspend fun setActiveVehicle(id: String) = dao.setActiveVehicle(id)

    /**
     * Archiving retires a vehicle without deleting its history; it can never remain the active
     * vehicle. No replacement is chosen automatically — the driver must deliberately pick a new
     * active vehicle from the Vehicles screen.
     */
    suspend fun archiveVehicle(id: String) {
        dao.archive(id, clock.nowMillis())
    }
}
