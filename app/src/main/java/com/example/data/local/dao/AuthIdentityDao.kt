package com.example.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.local.entities.AuthIdentityEntity

@Dao
interface AuthIdentityDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateIdentity(identity: AuthIdentityEntity)

    @Query("SELECT * FROM auth_identities WHERE provider = :provider AND providerUserId = :providerUserId LIMIT 1")
    suspend fun getIdentity(provider: String, providerUserId: String): AuthIdentityEntity?

    @Query("SELECT * FROM auth_identities WHERE userId = :userId")
    suspend fun getIdentitiesForUser(userId: String): List<AuthIdentityEntity>

    @Query("SELECT * FROM auth_identities WHERE userId = :userId AND provider = :provider LIMIT 1")
    suspend fun getIdentityForUserAndProvider(userId: String, provider: String): AuthIdentityEntity?

    @Query("SELECT * FROM auth_identities WHERE email = :email LIMIT 1")
    suspend fun getIdentityByEmail(email: String): AuthIdentityEntity?

    @Query("UPDATE auth_identities SET lastLoginAt = :timestamp WHERE provider = :provider AND providerUserId = :providerUserId")
    suspend fun updateLastLogin(provider: String, providerUserId: String, timestamp: Long = System.currentTimeMillis())

    @Query("DELETE FROM auth_identities WHERE provider = :provider AND providerUserId = :providerUserId")
    suspend fun deleteIdentity(provider: String, providerUserId: String)
}
