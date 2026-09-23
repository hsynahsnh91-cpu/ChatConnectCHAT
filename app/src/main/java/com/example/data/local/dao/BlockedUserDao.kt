package com.example.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.data.local.entities.BlockedUserEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface BlockedUserDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun blockUser(blocked: BlockedUserEntity)

    @Query("DELETE FROM blocked_users WHERE blockerUserId = :blockerUserId AND blockedUserId = :blockedUserId")
    suspend fun unblockUser(blockerUserId: String, blockedUserId: String)

    @Query("SELECT * FROM blocked_users WHERE blockerUserId = :blockerUserId")
    fun getBlockedUsers(blockerUserId: String): Flow<List<BlockedUserEntity>>

    @Query("SELECT EXISTS(SELECT 1 FROM blocked_users WHERE blockerUserId = :blockerUserId AND blockedUserId = :blockedUserId)")
    suspend fun isBlocked(blockerUserId: String, blockedUserId: String): Boolean
}
