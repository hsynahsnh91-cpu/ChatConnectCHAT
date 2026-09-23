package com.example.service

import android.util.Log
import com.example.data.local.dao.SpecialIdentifierDao
import com.example.data.local.dao.UserDao
import com.example.data.local.entities.SpecialIdentifierEntity
import com.example.data.local.entities.UserEntity
import com.example.data.model.PublicUserProfile
import com.example.service.admin.AdminSpecialIdentifierManager
import com.example.service.admin.SpecialIdentifierGenerator
import com.example.utils.AdminConstants
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.tasks.await

/**
 * SpecialIdentifierManager service:
 * Handles Firestore atomic transactions to reserve, assign, and validate unique vanity identifiers like 'VIP0001'.
 * Ensures that:
 * 1. Write access (reserve, assign, create) is restricted to administrative users.
 * 2. Lookup queries the 'SpecialIdentifiers' collection and returns ONLY public profile data,
 *    never leaking internal OAuth UIDs, tokens, emails, or password hashes to the UI.
 */
class SpecialIdentifierManager(
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance(),
    private val specialIdentifierDao: SpecialIdentifierDao? = null,
    private val userDao: UserDao? = null
) {
    companion object {
        private const val TAG = "SpecialIdentifierMgr"
        const val COLLECTION_NAME = "SpecialIdentifiers"
        const val LEGACY_COLLECTION_NAME = "special_identifiers"
        const val USERS_COLLECTION = "users"
    }

    private val adminManager = AdminSpecialIdentifierManager(firestore, specialIdentifierDao, userDao)

    /**
     * Validates that the identifier complies with vanity identifier format (e.g., 'VIP0001', 'GOLD100', 'SUPER99').
     * Length between 4 and 16 characters, alphanumeric.
     */
    fun validateVanityIdentifier(identifier: String): Result<String> {
        val clean = identifier.trim().uppercase()
        if (clean.length < 4 || clean.length > 16) {
            return Result.failure(IllegalArgumentException("يجب أن يتراوح طول المعرف المميز بين 4 و 16 حرفاً أو رقماً (مثل VIP0001)"))
        }
        val regex = Regex("^[A-Z0-9_]+$")
        if (!regex.matches(clean)) {
            return Result.failure(IllegalArgumentException("يجب أن يحتوي المعرف المميز على أحرف إنجليزية وأرقام فقط"))
        }
        return Result.success(clean)
    }

    fun validateIdentifier(identifier: String): Result<String> = validateVanityIdentifier(identifier)

    suspend fun reserveSpecialIdentifier(
        identifier: String,
        reservedForName: String,
        adminUserId: String
    ): Result<Unit> = reserveIdentifier(identifier, reservedForName, adminUserId)

    suspend fun assignSpecialIdentifier(
        identifier: String,
        targetUser: UserEntity,
        adminUserId: String
    ): Result<Unit> = assignIdentifier(identifier, targetUser, adminUserId)

    suspend fun lookupUserByVanityId(vanityId: String): Result<PublicUserProfile?> =
        lookupPublicProfileByVanityId(vanityId)

    /**
     * Atomically reserves a vanity identifier for a named individual or VIP account.
     * Uses Firestore transactions and verifies administrator authorization.
     */
    suspend fun reserveIdentifier(
        identifier: String,
        reservedForName: String,
        adminUserId: String
    ): Result<Unit> {
        val validation = validateVanityIdentifier(identifier)
        if (validation.isFailure) return Result.failure(validation.exceptionOrNull()!!)
        val cleanId = validation.getOrThrow()

        // Verify administrator credentials
        if (!adminManager.isAuthorizedAdmin(adminUserId)) {
            return Result.failure(SecurityException("غير مصرح: عمليات حجز المعرفات المميزة تتطلب صلاحيات المشرف العام (Admin)."))
        }

        return adminManager.reserveSpecialIdentifier(
            identifier = cleanId,
            reservedForName = reservedForName,
            notes = "محجوز عبر SpecialIdentifierManager",
            adminUserId = adminUserId
        )
    }

    /**
     * Atomically assigns a vanity identifier to a user account using a Firestore transaction.
     * Guarantees exclusivity: no other user can concurrently claim the same vanity ID.
     */
    suspend fun assignIdentifier(
        identifier: String,
        targetUser: UserEntity,
        adminUserId: String
    ): Result<Unit> {
        val validation = validateVanityIdentifier(identifier)
        if (validation.isFailure) return Result.failure(validation.exceptionOrNull()!!)
        val cleanId = validation.getOrThrow()

        // Verify administrator authorization
        if (!adminManager.isAuthorizedAdmin(adminUserId)) {
            return Result.failure(SecurityException("غير مصرح: تعيين المعرفات المميزة مقتصر على المشرفين (Admin)."))
        }

        return adminManager.assignSpecialIdentifierToUser(
            identifier = cleanId,
            targetUserId = targetUser.userId,
            targetUsername = targetUser.username,
            adminUserId = adminUserId
        )
    }

    /**
     * Lookup function for searching users by their assigned vanity ID.
     * Queries the 'SpecialIdentifiers' collection.
     * Returns ONLY public profile data (username, avatar, bio, vanity ID, online state)
     * and strictly DOES NOT expose sensitive authentication tokens, OAuth UIDs, or emails.
     */
    suspend fun lookupPublicProfileByVanityId(vanityId: String): Result<PublicUserProfile?> {
        val cleanId = vanityId.trim().uppercase()
        if (cleanId.isEmpty()) return Result.success(null)

        try {
            // 1. Query Firestore 'SpecialIdentifiers' collection
            val idDocRef = firestore.collection(COLLECTION_NAME).document(cleanId)
            val snapshot = idDocRef.get().await()

            if (snapshot.exists()) {
                val status = snapshot.getString("status")
                val assignedUserId = snapshot.getString("assignedUserId")
                val category = snapshot.getString("category") ?: "VIP"

                if (status == SpecialIdentifierEntity.STATUS_ASSIGNED && !assignedUserId.isNullOrBlank()) {
                    // Fetch public user data
                    val userDoc = firestore.collection(USERS_COLLECTION).document(assignedUserId).get().await()
                    if (userDoc.exists()) {
                        val publicProfile = PublicUserProfile(
                            userId = assignedUserId, // kept for room chat connection, UI displays vanityId
                            customId = cleanId,
                            username = userDoc.getString("username") ?: "مستخدم",
                            profilePicUri = userDoc.getString("profilePicUri"),
                            bio = userDoc.getString("bio") ?: "مرحباً! أنا أستخدم ChatConnect.",
                            isOnline = userDoc.getBoolean("isOnline") ?: false,
                            lastSeenTimestamp = userDoc.getLong("lastSeenTimestamp") ?: 0L,
                            isSpecialIdentifier = true,
                            specialCategory = category
                        )
                        return Result.success(publicProfile)
                    }
                }
            }

            // Fallback: Check legacy collection or standard users if vanity not in primary collection
            val legacyDoc = firestore.collection(LEGACY_COLLECTION_NAME).document(cleanId).get().await()
            if (legacyDoc.exists()) {
                val assignedUserId = legacyDoc.getString("assignedUserId")
                val category = legacyDoc.getString("category") ?: "VIP"
                if (!assignedUserId.isNullOrBlank()) {
                    val userDoc = firestore.collection(USERS_COLLECTION).document(assignedUserId).get().await()
                    if (userDoc.exists()) {
                        val publicProfile = PublicUserProfile(
                            userId = assignedUserId,
                            customId = cleanId,
                            username = userDoc.getString("username") ?: "مستخدم",
                            profilePicUri = userDoc.getString("profilePicUri"),
                            bio = userDoc.getString("bio") ?: "مرحباً! أنا أستخدم ChatConnect.",
                            isOnline = userDoc.getBoolean("isOnline") ?: false,
                            lastSeenTimestamp = userDoc.getLong("lastSeenTimestamp") ?: 0L,
                            isSpecialIdentifier = true,
                            specialCategory = category
                        )
                        return Result.success(publicProfile)
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Firestore vanity ID lookup exception for $cleanId: ${e.message}. Trying local Room DB.")
        }

        // 2. Resilient local fallback from Room Database
        try {
            val localSpecial = specialIdentifierDao?.getByIdentifier(cleanId)
                ?: specialIdentifierDao?.getById(cleanId)

            if (localSpecial != null && localSpecial.status == SpecialIdentifierEntity.STATUS_ASSIGNED && !localSpecial.assignedUserId.isNullOrBlank()) {
                val localUser = userDao?.getUserById(localSpecial.assignedUserId)
                if (localUser != null) {
                    val publicProfile = PublicUserProfile(
                        userId = localUser.userId,
                        customId = cleanId,
                        username = localUser.username,
                        profilePicUri = localUser.profilePicUri,
                        bio = localUser.bio,
                        isOnline = localUser.isOnline,
                        lastSeenTimestamp = localUser.lastSeenTimestamp,
                        isSpecialIdentifier = true,
                        specialCategory = localSpecial.category
                    )
                    return Result.success(publicProfile)
                }
            }

            // Also check standard 10-digit customId in local users
            val userByCustomId = userDao?.getUserByCustomId(cleanId)
            if (userByCustomId != null) {
                val publicProfile = PublicUserProfile(
                    userId = userByCustomId.userId,
                    customId = cleanId,
                    username = userByCustomId.username,
                    profilePicUri = userByCustomId.profilePicUri,
                    bio = userByCustomId.bio,
                    isOnline = userByCustomId.isOnline,
                    lastSeenTimestamp = userByCustomId.lastSeenTimestamp,
                    isSpecialIdentifier = false,
                    specialCategory = null
                )
                return Result.success(publicProfile)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Local vanity ID lookup error: ${e.message}")
        }

        return Result.success(null)
    }
}
