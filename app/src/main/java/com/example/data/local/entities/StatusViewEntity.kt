package com.example.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "status_views")
data class StatusViewEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val statusId: String,
    val viewerUserId: String,
    val viewerName: String = "",
    val viewerProfilePic: String? = null,
    val statusOwnerUserId: String = "",
    val viewedAt: Long = System.currentTimeMillis()
)
