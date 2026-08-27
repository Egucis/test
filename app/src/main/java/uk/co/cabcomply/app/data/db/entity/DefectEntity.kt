package uk.co.cabcomply.app.data.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A defect found against one checklist item during one inspection. Resolving a defect never
 * deletes it — resolution fields are added alongside the original report so evidence remains
 * available (product spec section 24).
 */
@Entity(
    tableName = "defects",
    foreignKeys = [
        ForeignKey(
            entity = InspectionEntity::class,
            parentColumns = ["id"],
            childColumns = ["inspectionId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = VehicleEntity::class,
            parentColumns = ["id"],
            childColumns = ["vehicleId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("inspectionId"), Index("vehicleId"), Index("status")]
)
data class DefectEntity(
    @PrimaryKey val id: String,
    val inspectionId: String,
    val inspectionResultId: String,
    val vehicleId: String,
    val checklistItemNameSnapshot: String,
    val description: String,
    val status: DefectStatus,
    val reportedAt: Long,
    val resolvedAt: Long?,
    val resolutionNote: String?
)
