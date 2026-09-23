package com.example.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "users")
data class UserEntity(
    @PrimaryKey
    val userId: String, // Internal UUID or Unique ID
    val customId: String, // Unique 10-digit public ID (e.g. 7392846152)
    val username: String,
    val email: String? = null,
    val firebaseUid: String? = null,
    val passwordHash: String = "",
    val profilePicUri: String? = null,
    val bio: String = "Hey there! I am using ChatConnect.",
    val isOnline: Boolean = true,
    val lastSeenTimestamp: Long = System.currentTimeMillis(),
    val createdAt: Long = System.currentTimeMillis()
)
