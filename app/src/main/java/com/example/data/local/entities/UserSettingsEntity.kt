package com.example.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "user_settings")
data class UserSettingsEntity(
    @PrimaryKey
    val userId: String,
    val lastSeenPrivacy: String = "EVERYONE", // "EVERYONE", "CONTACTS", "NOBODY"
    val profilePicPrivacy: String = "EVERYONE",
    val statusPrivacy: String = "MY_CONTACTS", // "MY_CONTACTS", "CONTACTS_EXCEPT", "ONLY_SHARE_WITH"
    val statusPrivacyExcludedIds: String = "",
    val statusPrivacyIncludedIds: String = "",
    val isDarkMode: Boolean = false,
    val language: String = "ar" // "ar" or "en"
)
