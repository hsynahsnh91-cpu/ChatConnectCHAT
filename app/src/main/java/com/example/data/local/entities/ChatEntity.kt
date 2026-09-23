package com.example.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "chats")
data class ChatEntity(
    @PrimaryKey
    val chatId: String,
    val type: String, // "INDIVIDUAL" or "GROUP"
    val groupName: String? = null,
    val groupIconUri: String? = null,
    val groupDescription: String? = null,
    val createdByUserId: String,
    val groupOwnerId: String? = null,
    val editGroupInfoPermission: String = "ALL", // "ALL" or "ADMIN_ONLY"
    val sendMessagesPermission: String = "ALL",  // "ALL" or "ADMIN_ONLY"
    val addMembersPermission: String = "ALL",    // "ALL" or "ADMIN_ONLY"
    val inviteToken: String? = null,
    val pinnedMessageId: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val lastMessageTimestamp: Long = System.currentTimeMillis(),
    val isActive: Boolean = true
)
