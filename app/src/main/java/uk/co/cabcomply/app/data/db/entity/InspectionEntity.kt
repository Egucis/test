package uk.co.cabcomply.app.data.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A completed (or in-progress) daily vehicle check. Carries snapshots of vehicle/driver/
 * authority/checklist identity so a report generated years later reads correctly even if the
 * live profile has since changed (product spec section 50). [inspectionDate] is the calendar
 * day this check represents; [completedAt] is the real save timestamp and is what duplicate-
 * check protection keys off, never the calendar date alone.
 */
@Entity(
    tableName = "inspections",
    indices = [
        Index("vehicleId"),
        Index("driverProfileId"),
        Index("inspectionDate"),
        Index(value = ["vehicleId", "inspectionDate"])
    ]
)
data class InspectionEntity(
    @PrimaryKey val id: String,
    val vehicleId: String,
    val vehicleRegistrationSnapshot: String,
    val driverProfileId: String,
    val driverNameSnapshot: String,
    val licensingAuthorityId: String?,
    val licensingAuthorityNameSnapshot: String?,
    val checklistId: String,
    val checklistNameSnapshot: String,
    val checklistVersionSnapshot: Int,
    val inspectionDate: Long,
    val startedAt: Long,
    val completedAt: Long?,
    val odometer: Int,
    val notes: String?,
    val driverConfirmed: Boolean,
    val confirmationTimestamp: Long?,
    val isQuickCheck: Boolean,
    val modifiedAt: Long?,
    val modificationReason: String?
)
