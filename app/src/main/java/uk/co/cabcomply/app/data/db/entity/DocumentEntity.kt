package uk.co.cabcomply.app.data.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A compliance document belonging to either the driver or a specific vehicle. [ownerId] holds
 * a driverProfileId or vehicleId depending on [ownerType] — a driver document is never attached
 * to a vehicle merely because that vehicle happens to be active (product spec section 31).
 */
@Entity(
    tableName = "documents",
    indices = [Index("ownerType"), Index("ownerId"), Index("expiryDate")]
)
data class DocumentEntity(
    @PrimaryKey val id: String,
    val ownerType: DocumentOwnerType,
    val ownerId: String,
    val documentType: DocumentType,
    val title: String,
    val referenceNumber: String?,
    val issueDate: Long?,
    val expiryDate: Long?,
    val notes: String?,
    val remindersEnabled: Boolean,
    val createdAt: Long,
    val updatedAt: Long
)
