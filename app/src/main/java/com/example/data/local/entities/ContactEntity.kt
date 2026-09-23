package com.example.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "contacts")
data class ContactEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val ownerUserId: String, // The user who owns this contact entry
    val contactUserId: String, // The user ID being added
    val nickname: String, // Custom nickname given by owner
    val addedAt: Long = System.currentTimeMillis()
)
