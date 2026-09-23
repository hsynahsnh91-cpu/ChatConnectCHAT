package com.example.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.data.local.entities.StatusEntity
import com.example.data.local.entities.StatusMuteEntity
import com.example.data.local.entities.StatusViewEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface StatusDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertStatus(status: StatusEntity)

    @Query("SELECT * FROM statuses WHERE statusId = :statusId LIMIT 1")
    suspend fun getStatusById(statusId: String): StatusEntity?

    @Query("DELETE FROM statuses WHERE statusId = :statusId")
    suspend fun deleteStatus(statusId: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertStatusView(view: StatusViewEntity)

    @Query("SELECT * FROM statuses WHERE isDeleted = 0 AND expiresAt > :now ORDER BY createdAt DESC")
    fun getAllActiveStatuses(now: Long = System.currentTimeMillis()): Flow<List<StatusEntity>>

    @Query("SELECT * FROM statuses WHERE userId = :userId AND isDeleted = 0 AND expiresAt > :now ORDER BY createdAt DESC")
    fun getActiveStatusesForUser(userId: String, now: Long = System.currentTimeMillis()): Flow<List<StatusEntity>>

    @Query("SELECT * FROM status_views WHERE statusId = :statusId ORDER BY viewedAt DESC")
    fun getViewsForStatus(statusId: String): Flow<List<StatusViewEntity>>

    @Query("SELECT COUNT(*) FROM status_views WHERE statusId = :statusId")
    fun getViewsCountForStatus(statusId: String): Flow<Int>

    @Query("SELECT * FROM status_views WHERE statusId = :statusId AND viewerUserId = :viewerUserId LIMIT 1")
    suspend fun getViewByViewer(statusId: String, viewerUserId: String): StatusViewEntity?

    @Query("SELECT DISTINCT statusId FROM status_views WHERE viewerUserId = :viewerUserId")
    fun getViewedStatusIdsForUser(viewerUserId: String): Flow<List<String>>

    @Query("DELETE FROM statuses WHERE expiresAt <= :now OR isDeleted = 1")
    suspend fun purgeExpiredStatuses(now: Long = System.currentTimeMillis())

    // --- Status Mutes ---
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertStatusMute(mute: StatusMuteEntity)

    @Query("DELETE FROM status_mutes WHERE userId = :userId AND mutedUserId = :mutedUserId")
    suspend fun deleteStatusMute(userId: String, mutedUserId: String)

    @Query("SELECT mutedUserId FROM status_mutes WHERE userId = :userId")
    fun observeMutedUserIds(userId: String): Flow<List<String>>

    @Query("SELECT EXISTS(SELECT 1 FROM status_mutes WHERE userId = :userId AND mutedUserId = :mutedUserId)")
    suspend fun isUserMuted(userId: String, mutedUserId: String): Boolean
}
