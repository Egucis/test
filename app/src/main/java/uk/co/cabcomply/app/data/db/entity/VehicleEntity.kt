package uk.co.cabcomply.app.data.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A vehicle profile. [id] is the stable identity used by every historical record — editing
 * [registration] or any other display field must never change which vehicle historical
 * inspections/mileage/documents belong to. [isActive] marks the single vehicle new daily
 * checks and mileage default to; [isArchived] retires a vehicle without deleting its history.
 */
@Entity(
    tableName = "vehicles",
    foreignKeys = [
        ForeignKey(
            entity = LicensingAuthorityEntity::class,
            parentColumns = ["id"],
            childColumns = ["licensingAuthorityId"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [Index("licensingAuthorityId"), Index("isActive"), Index("isArchived")]
)
data class VehicleEntity(
    @PrimaryKey val id: String,
    val registration: String,
    val make: String,
    val model: String,
    val licensingAuthorityId: String?,
    val plateNumber: String?,
    val licenceExpiryDate: Long?,
    val currentOdometer: Int,
    val isActive: Boolean,
    val isArchived: Boolean,
    val createdAt: Long,
    val updatedAt: Long
)
