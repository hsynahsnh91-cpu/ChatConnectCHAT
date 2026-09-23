package com.example.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.data.local.entities.UserSettingsEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface UserSettingsDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateSettings(settings: UserSettingsEntity)

    @Query("SELECT * FROM user_settings WHERE userId = :userId")
    fun observeSettings(userId: String): Flow<UserSettingsEntity?>

    @Query("SELECT * FROM user_settings WHERE userId = :userId")
    suspend fun getSettings(userId: String): UserSettingsEntity?
}
