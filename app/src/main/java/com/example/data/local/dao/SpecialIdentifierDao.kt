package com.example.data.local.dao

import androidx.room.*
import com.example.data.local.entities.SpecialIdentifierEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SpecialIdentifierDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(identifier: SpecialIdentifierEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(identifiers: List<SpecialIdentifierEntity>)

    @Query("SELECT * FROM special_identifiers WHERE identifier = :identifier LIMIT 1")
    suspend fun getByIdentifier(identifier: String): SpecialIdentifierEntity?

    @Query("SELECT * FROM special_identifiers WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): SpecialIdentifierEntity?

    @Query("SELECT * FROM special_identifiers WHERE assignedUserId = :userId LIMIT 1")
    suspend fun getByAssignedUserId(userId: String): SpecialIdentifierEntity?

    @Query("SELECT * FROM special_identifiers ORDER BY createdAt DESC")
    fun getAll(): Flow<List<SpecialIdentifierEntity>>

    @Query("SELECT * FROM special_identifiers ORDER BY createdAt DESC")
    suspend fun getAllList(): List<SpecialIdentifierEntity>

    @Query("SELECT * FROM special_identifiers WHERE status = :status ORDER BY createdAt DESC")
    fun getByStatus(status: String): Flow<List<SpecialIdentifierEntity>>

    @Query("SELECT * FROM special_identifiers WHERE identifier LIKE '%' || :query || '%' OR assignedUsername LIKE '%' || :query || '%' OR notes LIKE '%' || :query || '%' ORDER BY createdAt DESC")
    fun searchIdentifiers(query: String): Flow<List<SpecialIdentifierEntity>>

    @Query("UPDATE special_identifiers SET status = :status WHERE id = :id")
    suspend fun updateStatus(id: String, status: String)

    @Query("DELETE FROM special_identifiers WHERE id = :id")
    suspend fun deleteById(id: String)
}
