package uk.co.cabcomply.app.data.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * One mileage segment for one vehicle. Identity is a UUID, never the calendar date, so multiple
 * segments can exist on the same day without overwriting each other (product spec section 26).
 */
@Entity(
    tableName = "mileage_entries",
    foreignKeys = [
        ForeignKey(
            entity = VehicleEntity::class,
            parentColumns = ["id"],
            childColumns = ["vehicleId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("vehicleId"), Index("entryDate"), Index(value = ["vehicleId", "entryDate"])]
)
data class MileageEntryEntity(
    @PrimaryKey val id: String,
    val vehicleId: String,
    val startMileage: Int,
    val endMileage: Int?,
    val entryDate: Long,
    val startedAt: Long,
    val endedAt: Long?,
    val purpose: MileagePurpose,
    val notes: String?,
    val isFlagged: Boolean,
    val flagReason: String?,
    val createdAt: Long
)
