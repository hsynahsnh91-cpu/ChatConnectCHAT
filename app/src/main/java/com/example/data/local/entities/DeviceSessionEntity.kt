package com.example.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "device_sessions")
data class DeviceSessionEntity(
    @PrimaryKey
    val sessionId: String,
    val userId: String,
    val deviceName: String,
    val deviceType: String,      // "PHONE", "TABLET", "DESKTOP", "WEB"
    val platform: String,        // e.g. "Android 14", "Windows 11", "macOS Sonoma"
    val clientApp: String,       // e.g. "ChatConnect Android v2.4", "Chrome Browser", "Desktop App"
    val ipAddress: String,
    val location: String,
    val isCurrentDevice: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val lastActiveAt: Long = System.currentTimeMillis(),
    val isActive: Boolean = true
)
