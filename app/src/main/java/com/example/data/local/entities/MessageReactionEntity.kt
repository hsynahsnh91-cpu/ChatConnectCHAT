package com.example.data.local.entities

import androidx.room.Entity

@Entity(
    tableName = "message_reactions",
    primaryKeys = ["messageId", "userId"]
)
data class MessageReactionEntity(
    val messageId: String,
    val chatId: String,
    val userId: String,
    val username: String,
    val emoji: String,
    val timestamp: Long = System.currentTimeMillis()
)
