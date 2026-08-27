package uk.co.cabcomply.app.data.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/** The outcome for one checklist item within one inspection, with the item text snapshotted. */
@Entity(
    tableName = "inspection_results",
    foreignKeys = [
        ForeignKey(
            entity = InspectionEntity::class,
            parentColumns = ["id"],
            childColumns = ["inspectionId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("inspectionId"), Index("checklistItemId")]
)
data class InspectionResultEntity(
    @PrimaryKey val id: String,
    val inspectionId: String,
    val checklistItemId: String,
    val itemNameSnapshot: String,
    val categorySnapshot: String,
    val displayOrderSnapshot: Int,
    val status: InspectionResultStatus
)
