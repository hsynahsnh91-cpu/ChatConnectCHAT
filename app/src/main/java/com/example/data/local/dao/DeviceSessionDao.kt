package com.example.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.local.entities.DeviceSessionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface DeviceSessionDao {
    @Query("SELECT * FROM device_sessions WHERE userId = :userId AND isActive = 1 ORDER BY isCurrentDevice DESC, lastActiveAt DESC")
    fun getActiveSessionsForUser(userId: String): Flow<List<DeviceSessionEntity>>

    @Query("SELECT * FROM device_sessions WHERE userId = :userId ORDER BY isCurrentDevice DESC, lastActiveAt DESC")
    fun getAllSessionsForUser(userId: String): Flow<List<DeviceSessionEntity>>

    @Query("SELECT * FROM device_sessions WHERE sessionId = :sessionId LIMIT 1")
    suspend fun getSessionById(sessionId: String): DeviceSessionEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSession(session: DeviceSessionEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSessions(sessions: List<DeviceSessionEntity>)

    @Update
    suspend fun updateSession(session: DeviceSessionEntity)

    @Query("UPDATE device_sessions SET isActive = 0 WHERE sessionId = :sessionId")
    suspend fun revokeSession(sessionId: String)

    @Query("UPDATE device_sessions SET isActive = 0 WHERE userId = :userId AND isCurrentDevice = 0")
    suspend fun revokeAllOtherSessions(userId: String)

    @Query("DELETE FROM device_sessions WHERE sessionId = :sessionId")
    suspend fun deleteSession(sessionId: String)

    @Query("DELETE FROM device_sessions WHERE userId = :userId")
    suspend fun deleteAllForUser(userId: String)
}
