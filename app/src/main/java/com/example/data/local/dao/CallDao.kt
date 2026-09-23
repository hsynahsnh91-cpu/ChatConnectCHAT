package com.example.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.data.local.entities.CallLogEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CallDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCallLog(callLog: CallLogEntity)

    @Query("SELECT * FROM call_logs WHERE callerUserId = :userId OR receiverUserId = :userId ORDER BY timestamp DESC")
    fun getCallLogsForUser(userId: String): Flow<List<CallLogEntity>>
}
