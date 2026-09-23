package com.example.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "statuses")
data class StatusEntity(
    @PrimaryKey
    val statusId: String,
    val userId: String,
    val type: String, // "TEXT", "IMAGE", "VIDEO"
    val contentText: String? = null,
    val mediaUri: String? = null,
    val caption: String? = null,
    val bgColorHex: String = "#075E54",
    val textStyle: String = "NORMAL", // "NORMAL", "BOLD", "SERIF", "MONO"
    val textAlignment: String = "CENTER", // "CENTER", "START", "END"
    val privacyType: String = "MY_CONTACTS", // "MY_CONTACTS", "CONTACTS_EXCEPT", "ONLY_SHARE_WITH"
    val privacyTargetIds: String = "", // Comma-separated user IDs for exclusions/inclusions
    val isDeleted: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val expiresAt: Long = System.currentTimeMillis() + (24 * 60 * 60 * 1000L) // 24 Hours
)
