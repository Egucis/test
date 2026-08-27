package uk.co.cabcomply.app.data.repository

import kotlinx.coroutines.flow.Flow
import uk.co.cabcomply.app.data.db.dao.AttachmentDao
import uk.co.cabcomply.app.data.db.dao.DocumentDao
import uk.co.cabcomply.app.data.db.entity.AttachmentEntity
import uk.co.cabcomply.app.data.db.entity.AttachmentOwnerType
import uk.co.cabcomply.app.data.db.entity.DocumentEntity
import uk.co.cabcomply.app.data.db.entity.DocumentOwnerType
import uk.co.cabcomply.app.data.db.entity.DocumentType
import uk.co.cabcomply.app.util.AppClock
import uk.co.cabcomply.app.util.Ids
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DocumentRepository @Inject constructor(
    private val documentDao: DocumentDao,
    private val attachmentDao: AttachmentDao,
    private val clock: AppClock
) {
    fun observeForOwner(ownerType: DocumentOwnerType, ownerId: String): Flow<List<DocumentEntity>> =
        documentDao.observeForOwner(ownerType, ownerId)

    fun observeAll(): Flow<List<DocumentEntity>> = documentDao.observeAll()
    suspend fun getById(id: String): DocumentEntity? = documentDao.getById(id)
    suspend fun getExpiringBefore(beforeTimestamp: Long): List<DocumentEntity> = documentDao.getExpiringBefore(beforeTimestamp)

    fun observeAttachment(documentId: String): Flow<List<AttachmentEntity>> =
        attachmentDao.observeForOwner(AttachmentOwnerType.DOCUMENT, documentId)

    suspend fun saveDocument(
        id: String?,
        ownerType: DocumentOwnerType,
        ownerId: String,
        documentType: DocumentType,
        title: String,
        referenceNumber: String?,
        issueDate: Long?,
        expiryDate: Long?,
        notes: String?,
        remindersEnabled: Boolean,
        attachmentRelativePath: String? = null
    ): DocumentEntity {
        require(title.isNotBlank()) { "Enter a name for this document before saving." }
        val now = clock.nowMillis()
        val existing = id?.let { documentDao.getById(it) }
        val document = existing?.copy(
            documentType = documentType,
            title = title.trim(),
            referenceNumber = referenceNumber?.trim()?.ifBlank { null },
            issueDate = issueDate,
            expiryDate = expiryDate,
            notes = notes?.trim()?.ifBlank { null },
            remindersEnabled = remindersEnabled,
            updatedAt = now
        ) ?: DocumentEntity(
            id = Ids.newId(),
            ownerType = ownerType,
            ownerId = ownerId,
            documentType = documentType,
            title = title.trim(),
            referenceNumber = referenceNumber?.trim()?.ifBlank { null },
            issueDate = issueDate,
            expiryDate = expiryDate,
            notes = notes?.trim()?.ifBlank { null },
            remindersEnabled = remindersEnabled,
            createdAt = now,
            updatedAt = now
        )
        documentDao.upsert(document)
        if (attachmentRelativePath != null) {
            attachmentDao.insertAll(
                listOf(
                    AttachmentEntity(
                        id = Ids.newId(),
                        ownerType = AttachmentOwnerType.DOCUMENT,
                        ownerId = document.id,
                        filePath = attachmentRelativePath,
                        thumbnailPath = null,
                        createdAt = now
                    )
                )
            )
        }
        return document
    }

    suspend fun deleteDocument(id: String) = documentDao.delete(id)
}
