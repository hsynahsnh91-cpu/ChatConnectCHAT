package com.example.service.call

/**
 * Data model for Call History stored in Cloud Firestore under 'call_history' collection.
 * Includes call ID, duration, status, and participants indexed for user querying.
 */
data class CallHistoryRecord(
    val callId: String = "",
    val callerUserId: String = "",
    val callerName: String = "",
    val callerAvatar: String? = null,
    val receiverUserId: String = "",
    val receiverName: String = "",
    val receiverAvatar: String? = null,
    val participants: List<String> = emptyList(), // [callerUserId, receiverUserId] for Array-Contains queries
    val isVideo: Boolean = false,
    val status: String = "MISSED", // "MISSED", "COMPLETED", "REJECTED", "BUSY", "FAILED"
    val durationSec: Int = 0,
    val timestamp: Long = System.currentTimeMillis(),
    val startedAt: Long? = null,
    val endedAt: Long? = null
)
