package com.example.data.model

/**
 * Publicly discoverable user profile representation.
 * Excludes all sensitive authentication details (firebaseUid, OAuth tokens, email, passwordHash, device sessions).
 */
data class PublicUserProfile(
    val userId: String,
    val customId: String,
    val username: String,
    val profilePicUri: String? = null,
    val bio: String = "Hey there! I am using ChatConnect.",
    val isOnline: Boolean = false,
    val lastSeenTimestamp: Long = 0L,
    val isSpecialIdentifier: Boolean = false,
    val specialCategory: String? = null
)
