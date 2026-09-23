package com.example.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "call_logs")
data class CallLogEntity(
    @PrimaryKey
    val callId: String,
    val callerUserId: String,
    val receiverUserId: String,
    val isVideo: Boolean,
    val status: String, // "MISSED", "ACCEPTED", "REJECTED", "ENDED"
    val durationSec: Int = 0,
    val timestamp: Long = System.currentTimeMillis()
)
