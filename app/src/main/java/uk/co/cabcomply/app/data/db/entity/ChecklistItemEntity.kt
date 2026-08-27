package uk.co.cabcomply.app.data.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "checklist_items",
    foreignKeys = [
        ForeignKey(
            entity = ChecklistEntity::class,
            parentColumns = ["id"],
            childColumns = ["checklistId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("checklistId")]
)
data class ChecklistItemEntity(
    @PrimaryKey val id: String,
    val checklistId: String,
    val category: String,
    val displayOrder: Int,
    val name: String,
    val helpText: String?,
    val isRequired: Boolean
)
