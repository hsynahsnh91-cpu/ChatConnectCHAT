package com.example.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "attachments")
data class AttachmentEntity(
    @PrimaryKey
    val attachmentId: String,
    val messageId: String,
    val chatId: String,
    val type: String, // "IMAGE", "VIDEO", "FILE", "AUDIO", "VOICE", "LOCATION", "CONTACT"
    val fileName: String? = null,
    val mimeType: String? = null,
    val fileSize: Long = 0L,
    val storagePath: String = "",
    val remoteUrl: String? = null,
    val thumbnailUri: String? = null,
    val durationMs: Long = 0L,
    val width: Int = 0,
    val height: Int = 0,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val address: String? = null,
    val contactName: String? = null,
    val contactPhone: String? = null,
    val contactEmail: String? = null,
    val uploadProgress: Float = 1.0f,
    val uploadStatus: String = "SUCCESS", // "UPLOADING", "SUCCESS", "FAILED", "CANCELLED"
    val createdAt: Long = System.currentTimeMillis()
)
