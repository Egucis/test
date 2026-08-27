package uk.co.cabcomply.app.data.db.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow
import uk.co.cabcomply.app.data.db.entity.DocumentEntity
import uk.co.cabcomply.app.data.db.entity.DocumentOwnerType

@Dao
interface DocumentDao {
    @Upsert
    suspend fun upsert(document: DocumentEntity)

    @Query("DELETE FROM documents WHERE id = :id")
    suspend fun delete(id: String)

    @Query("SELECT * FROM documents WHERE id = :id")
    suspend fun getById(id: String): DocumentEntity?

    @Query("SELECT * FROM documents WHERE ownerType = :ownerType AND ownerId = :ownerId ORDER BY expiryDate ASC")
    fun observeForOwner(ownerType: DocumentOwnerType, ownerId: String): Flow<List<DocumentEntity>>

    @Query("SELECT * FROM documents ORDER BY expiryDate ASC")
    fun observeAll(): Flow<List<DocumentEntity>>

    @Query("SELECT * FROM documents WHERE expiryDate IS NOT NULL AND expiryDate <= :beforeTimestamp ORDER BY expiryDate ASC")
    suspend fun getExpiringBefore(beforeTimestamp: Long): List<DocumentEntity>

    @Query("SELECT * FROM documents")
    suspend fun getAll(): List<DocumentEntity>

    @Query("DELETE FROM documents")
    suspend fun deleteAll()
}
