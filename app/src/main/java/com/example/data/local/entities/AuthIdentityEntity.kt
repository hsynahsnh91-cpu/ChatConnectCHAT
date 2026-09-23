package com.example.data.local.entities

import androidx.room.Entity

@Entity(
    tableName = "auth_identities",
    primaryKeys = ["provider", "providerUserId"]
)
data class AuthIdentityEntity(
    val provider: String, // "google", "email", "anonymous"
    val providerUserId: String, // Unique Google Subject ID / Firebase UID
    val userId: String, // Internal User ID
    val email: String? = null,
    val displayName: String? = null,
    val photoUrl: String? = null,
    val linkedAt: Long = System.currentTimeMillis(),
    val lastLoginAt: Long = System.currentTimeMillis()
)
