package com.example.data.model

data class SendMessagePayload(
    val type: String = "TEXT",
    val content: String = "",
    val mediaUri: String? = null,
    val fileName: String? = null,
    val fileSize: Long = 0L,
    val durationMs: Long = 0L,
    val caption: String? = null,
    val mimeType: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val locationAddress: String? = null,
    val contactName: String? = null,
    val contactPhone: String? = null,
    val contactEmail: String? = null
)
