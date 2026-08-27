package uk.co.cabcomply.app.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A driver's own compliance identity. CabComply Basic supports one active profile;
 * the id is stable so future multi-driver/fleet support does not require a data migration.
 */
@Entity(tableName = "driver_profiles")
data class DriverProfileEntity(
    @PrimaryKey val id: String,
    val name: String,
    val licensingAuthorityId: String?,
    val badgeNumber: String?,
    val createdAt: Long,
    val updatedAt: Long
)
