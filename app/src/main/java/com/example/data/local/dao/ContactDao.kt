package com.example.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.data.local.entities.ContactEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ContactDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertContact(contact: ContactEntity)

    @Query("SELECT * FROM contacts WHERE ownerUserId = :ownerUserId")
    fun getContactsForOwner(ownerUserId: String): Flow<List<ContactEntity>>

    @Query("SELECT * FROM contacts WHERE ownerUserId = :ownerUserId AND contactUserId = :contactUserId LIMIT 1")
    suspend fun getContact(ownerUserId: String, contactUserId: String): ContactEntity?

    @Query("DELETE FROM contacts WHERE ownerUserId = :ownerUserId AND contactUserId = :contactUserId")
    suspend fun deleteContact(ownerUserId: String, contactUserId: String)

    @Query("UPDATE contacts SET nickname = :nickname WHERE ownerUserId = :ownerUserId AND contactUserId = :contactUserId")
    suspend fun updateNickname(ownerUserId: String, contactUserId: String, nickname: String)
}
