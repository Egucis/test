package uk.co.cabcomply.app.data.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A photo/file linked to exactly one owning record (a defect, a defect resolution, a document,
 * or an inspection). [filePath] is stored relative to the app's private files directory so a
 * restored backup can be relocated to a new install without broken absolute paths.
 */
@Entity(
    tableName = "attachments",
    indices = [Index("ownerType"), Index("ownerId")]
)
data class AttachmentEntity(
    @PrimaryKey val id: String,
    val ownerType: AttachmentOwnerType,
    val ownerId: String,
    val filePath: String,
    val thumbnailPath: String?,
    val createdAt: Long
)
