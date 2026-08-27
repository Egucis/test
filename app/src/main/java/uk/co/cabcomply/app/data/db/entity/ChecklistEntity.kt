package uk.co.cabcomply.app.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * One immutable version of a checklist. [checklistGroupId] is the stable identity that persists
 * across versions (e.g. "default_uk_taxi"); [id] identifies this specific version and is what
 * completed inspections reference, so an authority updating its requirements never alters the
 * checklist an old inspection recorded against (see product spec section 14).
 */
@Entity(tableName = "checklists")
data class ChecklistEntity(
    @PrimaryKey val id: String,
    val checklistGroupId: String,
    val licensingAuthorityId: String?,
    val name: String,
    val version: Int,
    val effectiveDate: Long,
    val isCustom: Boolean,
    val isActive: Boolean
)
