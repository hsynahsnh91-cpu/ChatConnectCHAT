package com.example.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "chat_members")
data class ChatMemberEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val chatId: String,
    val userId: String,
    val role: String = "MEMBER", // "OWNER", "ADMIN", "MEMBER"
    val joinedAt: Long = System.currentTimeMillis(),
    val addedBy: String = "",
    val isMuted: Boolean = false,
    val isArchived: Boolean = false
)
