package com.example.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "blocked_users")
data class BlockedUserEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val blockerUserId: String,
    val blockedUserId: String,
    val blockedAt: Long = System.currentTimeMillis()
)
