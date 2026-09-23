package com.example.data.local.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Entity representing an Admin-managed unique Special Identifier (المعرفات المميزة).
 * Supported categories: VIP, GOLD, NUMERIC, ADMIN, CUSTOM.
 * Supported states: AVAILABLE, RESERVED, ASSIGNED, DISABLED.
 */
@Entity(
    tableName = "special_identifiers",
    indices = [
        Index(value = ["identifier"], unique = true),
        Index(value = ["assignedUserId"]),
        Index(value = ["status"])
    ]
)
data class SpecialIdentifierEntity(
    @PrimaryKey
    val id: String, // Internal UUID
    val identifier: String, // Unique public format (e.g. VIP000001, 0000000001, GOLD777)
    val category: String = "VIP", // "VIP", "GOLD", "NUMERIC", "ADMIN", "CUSTOM"
    val status: String = STATUS_AVAILABLE, // "AVAILABLE", "RESERVED", "ASSIGNED", "DISABLED"
    val reservedForName: String? = null, // Contact name or note if reserved
    val assignedUserId: String? = null, // Internal UUID of the assigned user
    val assignedCustomId: String? = null, // Snapshot of user's previous customId
    val assignedUsername: String? = null, // Display name of assigned user
    val createdByAdminId: String,
    val createdAt: Long = System.currentTimeMillis(),
    val assignedAt: Long? = null,
    val notes: String? = null
) {
    companion object {
        const val STATUS_AVAILABLE = "AVAILABLE"
        const val STATUS_RESERVED = "RESERVED"
        const val STATUS_ASSIGNED = "ASSIGNED"
        const val STATUS_DISABLED = "DISABLED"

        const val CATEGORY_VIP = "VIP"
        const val CATEGORY_GOLD = "GOLD"
        const val CATEGORY_NUMERIC = "NUMERIC"
        const val CATEGORY_ADMIN = "ADMIN"
        const val CATEGORY_CUSTOM = "CUSTOM"
    }
}
