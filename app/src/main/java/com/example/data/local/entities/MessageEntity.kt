package com.example.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "messages")
data class MessageEntity(
    @PrimaryKey
    val messageId: String,
    val chatId: String,
    val senderUserId: String,
    val type: String, // "TEXT", "IMAGE", "VIDEO", "FILE", "AUDIO", "VOICE", "LOCATION", "CONTACT"
    val content: String = "",
    val mediaUri: String? = null,
    val mediaFileName: String? = null,
    val mediaFileSize: Long = 0,
    val mediaDurationMs: Long = 0,
    val caption: String? = null,
    val timestamp: Long = System.currentTimeMillis(),
    val status: String = "SENT", // "UPLOADING", "SENDING", "SENT", "DELIVERED", "READ", "FAILED"
    val replyToMessageId: String? = null,
    val uploadProgress: Float = 1.0f,
    val mimeType: String? = null,
    // Location metadata
    val latitude: Double? = null,
    val longitude: Double? = null,
    val locationAddress: String? = null,
    // Contact metadata
    val contactName: String? = null,
    val contactPhone: String? = null,
    val contactEmail: String? = null,
    // Read Receipts metadata
    val readAt: Long? = null
)
