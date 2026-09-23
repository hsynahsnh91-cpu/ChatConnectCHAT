package com.example.data.local.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "status_mutes",
    indices = [Index(value = ["userId", "mutedUserId"], unique = true)]
)
data class StatusMuteEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val userId: String, // Authenticated user who performed the mute
    val mutedUserId: String, // Target contact whose status updates are muted
    val mutedAt: Long = System.currentTimeMillis()
)
