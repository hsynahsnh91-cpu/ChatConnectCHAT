package com.example.service.admin

import android.util.Log
import com.example.data.local.dao.SpecialIdentifierDao
import com.example.data.local.dao.UserDao
import com.example.data.local.entities.SpecialIdentifierEntity
import com.example.data.local.entities.UserEntity
import com.example.data.model.PublicUserProfile
import com.example.utils.AdminConstants
import com.example.utils.IdGenerator
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

/**
 * Service for managing Special Identifiers (المعرفات المميزة) in Firebase Firestore and Room.
 * Uses atomic transactions with local-first resilience to guarantee IDs are unique, atomic,
 * and safely linked to user profiles without exposing sensitive OAuth or Auth credentials.
 */
class AdminSpecialIdentifierManager(
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance(),
    private val specialIdentifierDao: SpecialIdentifierDao? = null,
    private val userDao: UserDao? = null
) {

    companion object {
        private const val TAG = "AdminSpecialIdentifier"
        const val COLLECTION_NAME = "SpecialIdentifiers"
        const val LEGACY_COLLECTION_NAME = "special_identifiers"
        const val USERS_COLLECTION = "users"
    }

    /**
     * Ensures an active Firebase Auth session so that Firestore operations have authentication.
     */
    private suspend fun ensureFirebaseAuth() {
        if (!com.example.service.FirebaseAuthService.isApiKeyConfigured()) {
            return
        }
        try {
            val auth = FirebaseAuth.getInstance()
            if (auth.currentUser == null) {
                auth.signInAnonymously().await()
                Log.d(TAG, "Anonymous Firebase Auth session initialized for Firestore access")
            }
        } catch (e: Exception) {
            val msg = e.message.orEmpty()
            if (msg.contains("API key not valid", ignoreCase = true)) {
                com.example.service.FirebaseAuthService.markRemoteAuthUnavailable()
            }
            Log.w(TAG, "Notice initializing Firebase Auth session: ${e.message}")
        }
    }

    /**
     * Checks if the given user matches the System Administrator.
     */
    fun isAuthorizedAdmin(userId: String?, email: String? = null, customId: String? = null): Boolean {
        if (userId == AdminConstants.ADMIN_USER_ID) return true
        if (email == AdminConstants.ADMIN_EMAIL) return true
        if (customId == AdminConstants.ADMIN_CUSTOM_ID) return true
        return false
    }

    /**
     * Atomically creates a unique vanity identifier using Firestore transactions with local Room fallback.
     * Guarantees that duplicate IDs cannot be created concurrently.
     */
    suspend fun createSpecialIdentifier(
        identifier: String,
        category: String = SpecialIdentifierEntity.CATEGORY_VIP,
        notes: String? = null,
        adminUserId: String = AdminConstants.ADMIN_USER_ID
    ): Result<SpecialIdentifierEntity> {
        val validation = SpecialIdentifierGenerator.validateVanityId(identifier)
        if (validation.isFailure) {
            return Result.failure(validation.exceptionOrNull() ?: IllegalArgumentException("معرف غير صالح"))
        }
        val cleanId = validation.getOrThrow()

        // Local duplicate check
        val existingLocal = specialIdentifierDao?.getByIdentifier(cleanId) ?: specialIdentifierDao?.getById(cleanId)
        if (existingLocal != null) {
            return Result.failure(IllegalStateException("المعرف المميز '$cleanId' موجود مسبقاً في قاعدة البيانات."))
        }

        val entity = SpecialIdentifierEntity(
            id = cleanId,
            identifier = cleanId,
            category = category.trim().uppercase().ifEmpty { SpecialIdentifierEntity.CATEGORY_VIP },
            status = SpecialIdentifierEntity.STATUS_AVAILABLE,
            reservedForName = null,
            assignedUserId = null,
            assignedCustomId = null,
            assignedUsername = null,
            createdByAdminId = adminUserId,
            createdAt = System.currentTimeMillis(),
            assignedAt = null,
            notes = notes?.trim()
        )

        // Attempt Firestore atomic transaction
        var cloudSuccess = false
        try {
            ensureFirebaseAuth()
            val docRef = firestore.collection(COLLECTION_NAME).document(cleanId)
            val legacyDocRef = firestore.collection(LEGACY_COLLECTION_NAME).document(cleanId)

            firestore.runTransaction { transaction ->
                val snapshot = transaction.get(docRef)
                if (snapshot.exists()) {
                    throw IllegalStateException("المعرف المميز '$cleanId' موجود مسبقاً في قاعدة البيانات.")
                }

                val map = mapEntityToFirestore(entity)
                transaction.set(docRef, map)
                transaction.set(legacyDocRef, map, SetOptions.merge())
            }.await()
            cloudSuccess = true
            Log.i(TAG, "Successfully created atomic special identifier in Firestore: $cleanId")
        } catch (e: Exception) {
            if (e is IllegalStateException) {
                return Result.failure(e)
            }
            Log.w(TAG, "Firestore transaction notice for $cleanId (${e.message}). Applying local persistence fallback.")
            // Best effort non-blocking Firestore write
            try {
                val map = mapEntityToFirestore(entity)
                firestore.collection(COLLECTION_NAME).document(cleanId).set(map, SetOptions.merge())
                firestore.collection(LEGACY_COLLECTION_NAME).document(cleanId).set(map, SetOptions.merge())
            } catch (_: Exception) {}
        }

        // Guaranteed Room persistence
        specialIdentifierDao?.insertOrUpdate(entity)
        Log.i(TAG, "Successfully saved special identifier: $cleanId (Cloud synced: $cloudSuccess)")
        return Result.success(entity)
    }

    /**
     * Atomically reserves an identifier for a specific user or VIP client.
     */
    suspend fun reserveSpecialIdentifier(
        identifier: String,
        reservedForName: String,
        notes: String? = null,
        adminUserId: String = AdminConstants.ADMIN_USER_ID
    ): Result<Unit> {
        val cleanId = identifier.trim().uppercase()
        if (cleanId.isEmpty()) return Result.failure(IllegalArgumentException("المعرف فارغ."))

        val localEntity = specialIdentifierDao?.getById(cleanId) ?: specialIdentifierDao?.getByIdentifier(cleanId)
        if (localEntity != null && localEntity.status == SpecialIdentifierEntity.STATUS_ASSIGNED) {
            return Result.failure(IllegalStateException("لا يمكن حجز معرف تم تعيينه لمستخدم بالفعل."))
        }

        var cloudEntity: SpecialIdentifierEntity? = null
        try {
            ensureFirebaseAuth()
            val docRef = firestore.collection(COLLECTION_NAME).document(cleanId)
            val legacyDocRef = firestore.collection(LEGACY_COLLECTION_NAME).document(cleanId)

            cloudEntity = firestore.runTransaction { transaction ->
                val snapshot = transaction.get(docRef)
                if (!snapshot.exists() && localEntity == null) {
                    throw IllegalArgumentException("المعرف المميز '$cleanId' غير موجود.")
                }

                val currentStatus = if (snapshot.exists()) snapshot.getString("status") else localEntity?.status
                if (currentStatus == SpecialIdentifierEntity.STATUS_ASSIGNED) {
                    throw IllegalStateException("لا يمكن حجز معرف تم تعيينه لمستخدم بالفعل.")
                }

                val existingData = if (snapshot.exists()) snapshot.data ?: emptyMap() else emptyMap()
                val entity = (if (snapshot.exists()) mapDocToEntity(cleanId, existingData) else localEntity!!).copy(
                    status = SpecialIdentifierEntity.STATUS_RESERVED,
                    reservedForName = reservedForName.trim(),
                    notes = notes?.trim() ?: snapshot.getString("notes") ?: localEntity?.notes
                )

                val map = mapEntityToFirestore(entity)
                transaction.set(docRef, map, SetOptions.merge())
                transaction.set(legacyDocRef, map, SetOptions.merge())
                entity
            }.await()
            Log.i(TAG, "Successfully reserved identifier in Firestore: $cleanId")
        } catch (e: Exception) {
            if (e is IllegalStateException) return Result.failure(e)
            Log.w(TAG, "Firestore transaction notice reserving $cleanId (${e.message}). Applying local persistence.")
        }

        val targetEntity = cloudEntity ?: localEntity?.copy(
            status = SpecialIdentifierEntity.STATUS_RESERVED,
            reservedForName = reservedForName.trim(),
            notes = notes?.trim() ?: localEntity.notes
        ) ?: SpecialIdentifierEntity(
            id = cleanId,
            identifier = cleanId,
            status = SpecialIdentifierEntity.STATUS_RESERVED,
            reservedForName = reservedForName.trim(),
            notes = notes?.trim(),
            createdByAdminId = adminUserId,
            createdAt = System.currentTimeMillis()
        )

        specialIdentifierDao?.insertOrUpdate(targetEntity)

        try {
            val map = mapEntityToFirestore(targetEntity)
            firestore.collection(COLLECTION_NAME).document(cleanId).set(map, SetOptions.merge())
            firestore.collection(LEGACY_COLLECTION_NAME).document(cleanId).set(map, SetOptions.merge())
        } catch (_: Exception) {}

        Log.i(TAG, "Successfully reserved special identifier $cleanId for $reservedForName")
        return Result.success(Unit)
    }

    /**
     * Atomically assigns a special identifier to a target user profile.
     * Updates both the identifier document and the user's profile without exposing sensitive OAuth UIDs.
     */
    suspend fun assignSpecialIdentifierToUser(
        identifier: String,
        targetUserId: String,
        targetUsername: String? = null,
        adminUserId: String = AdminConstants.ADMIN_USER_ID
    ): Result<Unit> {
        val cleanId = identifier.trim().uppercase()
        if (cleanId.isEmpty() || targetUserId.isEmpty()) {
            return Result.failure(IllegalArgumentException("معلومات التعيين غير مكتملة."))
        }

        val localUser = userDao?.getUserById(targetUserId)
        val localIdEntity = specialIdentifierDao?.getById(cleanId) ?: specialIdentifierDao?.getByIdentifier(cleanId)

        if (localIdEntity != null && localIdEntity.status == SpecialIdentifierEntity.STATUS_DISABLED) {
            return Result.failure(IllegalStateException("لا يمكن تعيين معرف مميز معطل."))
        }

        var cloudAssignedEntity: SpecialIdentifierEntity? = null
        try {
            ensureFirebaseAuth()
            val idDocRef = firestore.collection(COLLECTION_NAME).document(cleanId)
            val legacyIdDocRef = firestore.collection(LEGACY_COLLECTION_NAME).document(cleanId)
            val userDocRef = firestore.collection(USERS_COLLECTION).document(targetUserId)

            val (updatedIdentifier, _) = firestore.runTransaction { transaction ->
                val idSnapshot = transaction.get(idDocRef)
                val userSnapshot = transaction.get(userDocRef)

                val idData = if (idSnapshot.exists()) idSnapshot.data ?: emptyMap() else emptyMap()
                val currentStatus = if (idSnapshot.exists()) idSnapshot.getString("status") else localIdEntity?.status
                if (currentStatus == SpecialIdentifierEntity.STATUS_DISABLED) {
                    throw IllegalStateException("لا يمكن تعيين معرف مميز معطل.")
                }

                val existingCustomId = if (userSnapshot.exists()) {
                    userSnapshot.getString("customId")
                } else localUser?.customId

                val finalUsername = targetUsername
                    ?: (if (userSnapshot.exists()) userSnapshot.getString("username") else null)
                    ?: localUser?.username
                    ?: "User"

                val baseEntity = if (idSnapshot.exists()) mapDocToEntity(cleanId, idData)
                else (localIdEntity ?: SpecialIdentifierEntity(id = cleanId, identifier = cleanId, createdByAdminId = adminUserId, createdAt = System.currentTimeMillis()))

                val updatedIdEntity = baseEntity.copy(
                    status = SpecialIdentifierEntity.STATUS_ASSIGNED,
                    assignedUserId = targetUserId,
                    assignedCustomId = existingCustomId,
                    assignedUsername = finalUsername,
                    assignedAt = System.currentTimeMillis()
                )

                val idMap = mapEntityToFirestore(updatedIdEntity)
                transaction.set(idDocRef, idMap, SetOptions.merge())
                transaction.set(legacyIdDocRef, idMap, SetOptions.merge())

                if (userSnapshot.exists()) {
                    transaction.update(
                        userDocRef,
                        mapOf(
                            "customId" to cleanId,
                            "lastSeenTimestamp" to System.currentTimeMillis()
                        )
                    )
                }

                Pair(updatedIdEntity, existingCustomId)
            }.await()
            cloudAssignedEntity = updatedIdentifier
            Log.i(TAG, "Successfully assigned identifier in Firestore: $cleanId to user $targetUserId")
        } catch (e: Exception) {
            if (e is IllegalStateException) return Result.failure(e)
            Log.w(TAG, "Firestore transaction notice assigning $cleanId (${e.message}). Applying local persistence.")
        }

        // Room Local Persistence
        val finalIdEntity = cloudAssignedEntity ?: (localIdEntity ?: SpecialIdentifierEntity(
            id = cleanId,
            identifier = cleanId,
            createdByAdminId = adminUserId,
            createdAt = System.currentTimeMillis()
        )).copy(
            status = SpecialIdentifierEntity.STATUS_ASSIGNED,
            assignedUserId = targetUserId,
            assignedCustomId = localUser?.customId,
            assignedUsername = targetUsername ?: localUser?.username ?: "User",
            assignedAt = System.currentTimeMillis()
        )

        specialIdentifierDao?.insertOrUpdate(finalIdEntity)
        if (localUser != null) {
            userDao?.updateUser(localUser.copy(customId = cleanId))
        }

        try {
            val idMap = mapEntityToFirestore(finalIdEntity)
            firestore.collection(COLLECTION_NAME).document(cleanId).set(idMap, SetOptions.merge())
            firestore.collection(USERS_COLLECTION).document(targetUserId).update("customId", cleanId)
        } catch (_: Exception) {}

        Log.i(TAG, "Successfully assigned special identifier $cleanId to user $targetUserId")
        return Result.success(Unit)
    }

    /**
     * Atomically releases an assigned or reserved identifier back to AVAILABLE status.
     * Restores the user's previous customId or generates a new 10-digit public ID.
     */
    suspend fun releaseSpecialIdentifier(
        identifier: String,
        adminUserId: String = AdminConstants.ADMIN_USER_ID
    ): Result<Unit> {
        val cleanId = identifier.trim().uppercase()
        val localEntity = specialIdentifierDao?.getById(cleanId) ?: specialIdentifierDao?.getByIdentifier(cleanId)

        var cloudEntity: SpecialIdentifierEntity? = null
        var previousAssignedUserId: String? = localEntity?.assignedUserId
        var previousCustomId: String? = localEntity?.assignedCustomId

        try {
            ensureFirebaseAuth()
            val idDocRef = firestore.collection(COLLECTION_NAME).document(cleanId)
            val legacyIdDocRef = firestore.collection(LEGACY_COLLECTION_NAME).document(cleanId)

            val updatedIdentifier = firestore.runTransaction { transaction ->
                val idSnapshot = transaction.get(idDocRef)
                val assignedUserId = (if (idSnapshot.exists()) idSnapshot.getString("assignedUserId") else null) ?: localEntity?.assignedUserId
                val assignedCustomId = (if (idSnapshot.exists()) idSnapshot.getString("assignedCustomId") else null) ?: localEntity?.assignedCustomId
                var userDocRef: DocumentReference? = null
                var userExists = false

                if (!assignedUserId.isNullOrBlank()) {
                    val ref = firestore.collection(USERS_COLLECTION).document(assignedUserId)
                    val userSnapshot = transaction.get(ref)
                    if (userSnapshot.exists()) {
                        userDocRef = ref
                        userExists = true
                    }
                }

                val data = if (idSnapshot.exists()) idSnapshot.data ?: emptyMap() else emptyMap()
                val base = if (idSnapshot.exists()) mapDocToEntity(cleanId, data)
                else (localEntity ?: SpecialIdentifierEntity(id = cleanId, identifier = cleanId, createdByAdminId = adminUserId, createdAt = System.currentTimeMillis()))

                val releasedEntity = base.copy(
                    status = SpecialIdentifierEntity.STATUS_AVAILABLE,
                    assignedUserId = null,
                    assignedCustomId = null,
                    assignedUsername = null,
                    assignedAt = null,
                    reservedForName = null
                )

                val idMap = mapEntityToFirestore(releasedEntity)
                transaction.set(idDocRef, idMap)
                transaction.set(legacyIdDocRef, idMap)

                if (userExists && userDocRef != null) {
                    val restoredId = assignedCustomId?.takeIf { it.isNotBlank() && it != cleanId }
                        ?: IdGenerator.generate10DigitId()
                    transaction.update(userDocRef, mapOf("customId" to restoredId))
                }

                Triple(releasedEntity, assignedUserId, assignedCustomId)
            }.await()
            cloudEntity = updatedIdentifier.first
            previousAssignedUserId = updatedIdentifier.second
            previousCustomId = updatedIdentifier.third
            Log.i(TAG, "Successfully released identifier in Firestore: $cleanId")
        } catch (e: Exception) {
            Log.w(TAG, "Firestore transaction notice releasing $cleanId (${e.message}). Applying local persistence.")
        }

        val targetEntity = cloudEntity ?: (localEntity ?: SpecialIdentifierEntity(id = cleanId, identifier = cleanId, createdByAdminId = adminUserId, createdAt = System.currentTimeMillis())).copy(
            status = SpecialIdentifierEntity.STATUS_AVAILABLE,
            assignedUserId = null,
            assignedCustomId = null,
            assignedUsername = null,
            assignedAt = null,
            reservedForName = null
        )

        specialIdentifierDao?.insertOrUpdate(targetEntity)

        if (!previousAssignedUserId.isNullOrBlank()) {
            val localUser = userDao?.getUserById(previousAssignedUserId)
            if (localUser != null) {
                val restoredId = previousCustomId?.takeIf { it.isNotBlank() && it != cleanId }
                    ?: IdGenerator.generate10DigitId()
                userDao?.updateUser(localUser.copy(customId = restoredId))
            }
        }

        try {
            val idMap = mapEntityToFirestore(targetEntity)
            firestore.collection(COLLECTION_NAME).document(cleanId).set(idMap, SetOptions.merge())
            firestore.collection(LEGACY_COLLECTION_NAME).document(cleanId).set(idMap, SetOptions.merge())
        } catch (_: Exception) {}

        Log.i(TAG, "Successfully released special identifier $cleanId")
        return Result.success(Unit)
    }

    /**
     * Atomically disables an identifier so it cannot be assigned or used.
     */
    suspend fun disableSpecialIdentifier(
        identifier: String,
        reason: String? = null,
        adminUserId: String = AdminConstants.ADMIN_USER_ID
    ): Result<Unit> {
        val cleanId = identifier.trim().uppercase()
        val localEntity = specialIdentifierDao?.getById(cleanId) ?: specialIdentifierDao?.getByIdentifier(cleanId)

        var cloudEntity: SpecialIdentifierEntity? = null
        var previousAssignedUserId: String? = localEntity?.assignedUserId
        var previousCustomId: String? = localEntity?.assignedCustomId

        try {
            ensureFirebaseAuth()
            val idDocRef = firestore.collection(COLLECTION_NAME).document(cleanId)
            val legacyIdDocRef = firestore.collection(LEGACY_COLLECTION_NAME).document(cleanId)

            val resultEntity = firestore.runTransaction { transaction ->
                val idSnapshot = transaction.get(idDocRef)
                val assignedUserId = (if (idSnapshot.exists()) idSnapshot.getString("assignedUserId") else null) ?: localEntity?.assignedUserId
                val assignedCustomId = (if (idSnapshot.exists()) idSnapshot.getString("assignedCustomId") else null) ?: localEntity?.assignedCustomId
                var userDocRef: DocumentReference? = null

                if (!assignedUserId.isNullOrBlank()) {
                    val ref = firestore.collection(USERS_COLLECTION).document(assignedUserId)
                    if (transaction.get(ref).exists()) {
                        userDocRef = ref
                    }
                }

                val noteSuffix = if (!reason.isNullOrBlank()) " [معطل: $reason]" else ""
                val data = if (idSnapshot.exists()) idSnapshot.data ?: emptyMap() else emptyMap()
                val existingNotes = if (idSnapshot.exists()) idSnapshot.getString("notes") else localEntity?.notes

                val base = if (idSnapshot.exists()) mapDocToEntity(cleanId, data)
                else (localEntity ?: SpecialIdentifierEntity(id = cleanId, identifier = cleanId, createdByAdminId = adminUserId, createdAt = System.currentTimeMillis()))

                val disabledEntity = base.copy(
                    status = SpecialIdentifierEntity.STATUS_DISABLED,
                    assignedUserId = null,
                    assignedCustomId = null,
                    assignedUsername = null,
                    assignedAt = null,
                    notes = "${existingNotes.orEmpty()}$noteSuffix".trim()
                )

                val map = mapEntityToFirestore(disabledEntity)
                transaction.set(idDocRef, map)
                transaction.set(legacyIdDocRef, map)

                if (userDocRef != null) {
                    val restoredId = assignedCustomId?.takeIf { it.isNotBlank() && it != cleanId }
                        ?: IdGenerator.generate10DigitId()
                    transaction.update(userDocRef, mapOf("customId" to restoredId))
                }

                Triple(disabledEntity, assignedUserId, assignedCustomId)
            }.await()
            cloudEntity = resultEntity.first
            previousAssignedUserId = resultEntity.second
            previousCustomId = resultEntity.third
            Log.i(TAG, "Successfully disabled identifier in Firestore: $cleanId")
        } catch (e: Exception) {
            Log.w(TAG, "Firestore transaction notice disabling $cleanId (${e.message}). Applying local persistence.")
        }

        val noteSuffix = if (!reason.isNullOrBlank()) " [معطل: $reason]" else ""
        val targetEntity = cloudEntity ?: (localEntity ?: SpecialIdentifierEntity(id = cleanId, identifier = cleanId, createdByAdminId = adminUserId, createdAt = System.currentTimeMillis())).copy(
            status = SpecialIdentifierEntity.STATUS_DISABLED,
            assignedUserId = null,
            assignedCustomId = null,
            assignedUsername = null,
            assignedAt = null,
            notes = "${localEntity?.notes.orEmpty()}$noteSuffix".trim()
        )

        specialIdentifierDao?.insertOrUpdate(targetEntity)

        if (!previousAssignedUserId.isNullOrBlank()) {
            val localUser = userDao?.getUserById(previousAssignedUserId)
            if (localUser != null) {
                val restoredId = previousCustomId?.takeIf { it.isNotBlank() && it != cleanId }
                    ?: IdGenerator.generate10DigitId()
                userDao?.updateUser(localUser.copy(customId = restoredId))
            }
        }

        try {
            val map = mapEntityToFirestore(targetEntity)
            firestore.collection(COLLECTION_NAME).document(cleanId).set(map, SetOptions.merge())
            firestore.collection(LEGACY_COLLECTION_NAME).document(cleanId).set(map, SetOptions.merge())
        } catch (_: Exception) {}

        Log.i(TAG, "Successfully disabled special identifier $cleanId")
        return Result.success(Unit)
    }

    /**
     * Deletes an identifier if it is not currently assigned.
     */
    suspend fun deleteSpecialIdentifier(identifier: String): Result<Unit> {
        val cleanId = identifier.trim().uppercase()
        val localEntity = specialIdentifierDao?.getById(cleanId) ?: specialIdentifierDao?.getByIdentifier(cleanId)
        if (localEntity?.status == SpecialIdentifierEntity.STATUS_ASSIGNED) {
            return Result.failure(IllegalStateException("يرجى فك ارتباط المعرف بالمستخدم أولاً قبل حذفه."))
        }

        try {
            ensureFirebaseAuth()
            val idDocRef = firestore.collection(COLLECTION_NAME).document(cleanId)
            val legacyIdDocRef = firestore.collection(LEGACY_COLLECTION_NAME).document(cleanId)

            firestore.runTransaction { transaction ->
                val snapshot = transaction.get(idDocRef)
                if (snapshot.exists()) {
                    val status = snapshot.getString("status")
                    if (status == SpecialIdentifierEntity.STATUS_ASSIGNED) {
                        throw IllegalStateException("يرجى فك ارتباط المعرف بالمستخدم أولاً قبل حذفه.")
                    }
                    transaction.delete(idDocRef)
                    transaction.delete(legacyIdDocRef)
                }
            }.await()
        } catch (e: Exception) {
            if (e is IllegalStateException) return Result.failure(e)
            Log.w(TAG, "Firestore delete notice for $cleanId (${e.message}). Applying local deletion.")
            try {
                firestore.collection(COLLECTION_NAME).document(cleanId).delete()
                firestore.collection(LEGACY_COLLECTION_NAME).document(cleanId).delete()
            } catch (_: Exception) {}
        }

        specialIdentifierDao?.deleteById(cleanId)
        Log.i(TAG, "Successfully deleted special identifier $cleanId")
        return Result.success(Unit)
    }

    /**
     * Contact Discovery Lookup function:
     * Resolves a public user profile using a Special Identifier (or fallback customId)
     * WITHOUT exposing internal OAuth UIDs (firebaseUid), email, phone numbers, or tokens.
     */
    suspend fun lookupContactByIdentifier(identifier: String): Result<PublicUserProfile?> {
        val cleanId = identifier.trim().uppercase()
        if (cleanId.isEmpty()) return Result.success(null)

        // 1. Cloud Firestore Lookup
        try {
            ensureFirebaseAuth()
            val idSnapshot = firestore.collection(COLLECTION_NAME).document(cleanId).get().await()
            if (idSnapshot.exists()) {
                val assignedUserId = idSnapshot.getString("assignedUserId")
                val category = idSnapshot.getString("category") ?: "VIP"
                val status = idSnapshot.getString("status")

                if (status == SpecialIdentifierEntity.STATUS_ASSIGNED && !assignedUserId.isNullOrBlank()) {
                    val userDoc = firestore.collection(USERS_COLLECTION).document(assignedUserId).get().await()
                    if (userDoc.exists()) {
                        val publicProfile = PublicUserProfile(
                            userId = assignedUserId,
                            customId = cleanId,
                            username = userDoc.getString("username") ?: "User",
                            profilePicUri = userDoc.getString("profilePicUri"),
                            bio = userDoc.getString("bio") ?: "Hey there! I am using ChatConnect.",
                            isOnline = userDoc.getBoolean("isOnline") ?: false,
                            lastSeenTimestamp = userDoc.getLong("lastSeenTimestamp") ?: 0L,
                            isSpecialIdentifier = true,
                            specialCategory = category
                        )
                        return Result.success(publicProfile)
                    }
                }
            }

            // Fallback: Search users collection by customId
            val userByCustomIdSnap = firestore.collection(USERS_COLLECTION)
                .whereEqualTo("customId", cleanId)
                .limit(1)
                .get()
                .await()

            if (!userByCustomIdSnap.isEmpty) {
                val doc = userByCustomIdSnap.documents[0]
                val publicProfile = PublicUserProfile(
                    userId = doc.id,
                    customId = cleanId,
                    username = doc.getString("username") ?: "User",
                    profilePicUri = doc.getString("profilePicUri"),
                    bio = doc.getString("bio") ?: "Hey there! I am using ChatConnect.",
                    isOnline = doc.getBoolean("isOnline") ?: false,
                    lastSeenTimestamp = doc.getLong("lastSeenTimestamp") ?: 0L,
                    isSpecialIdentifier = false,
                    specialCategory = null
                )
                return Result.success(publicProfile)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Firestore contact lookup notice for $cleanId (${e.message}). Checking local Room database.")
        }

        // 2. Local Room Database Lookup (Offline & resilient fallback)
        try {
            val specialIdEntity = specialIdentifierDao?.getByIdentifier(cleanId)
                ?: specialIdentifierDao?.getById(cleanId)

            if (specialIdEntity != null && specialIdEntity.status == SpecialIdentifierEntity.STATUS_ASSIGNED && !specialIdEntity.assignedUserId.isNullOrBlank()) {
                val user = userDao?.getUserById(specialIdEntity.assignedUserId)
                if (user != null) {
                    val profile = PublicUserProfile(
                        userId = user.userId,
                        customId = cleanId,
                        username = user.username,
                        profilePicUri = user.profilePicUri,
                        bio = user.bio,
                        isOnline = user.isOnline,
                        lastSeenTimestamp = user.lastSeenTimestamp,
                        isSpecialIdentifier = true,
                        specialCategory = specialIdEntity.category
                    )
                    return Result.success(profile)
                }
            }

            val userByCustomId = userDao?.getUserByCustomId(cleanId)
            if (userByCustomId != null) {
                val isSpecial = specialIdEntity != null && specialIdEntity.status == SpecialIdentifierEntity.STATUS_ASSIGNED
                val profile = PublicUserProfile(
                    userId = userByCustomId.userId,
                    customId = cleanId,
                    username = userByCustomId.username,
                    profilePicUri = userByCustomId.profilePicUri,
                    bio = userByCustomId.bio,
                    isOnline = userByCustomId.isOnline,
                    lastSeenTimestamp = userByCustomId.lastSeenTimestamp,
                    isSpecialIdentifier = isSpecial,
                    specialCategory = specialIdEntity?.category
                )
                return Result.success(profile)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Local contact lookup error: ${e.message}", e)
        }

        return Result.success(null)
    }

    /**
     * Real-time flow of all special identifiers from Firestore.
     */
    fun observeSpecialIdentifiers(): Flow<List<SpecialIdentifierEntity>> = callbackFlow {
        var listener: ListenerRegistration? = null
        try {
            ensureFirebaseAuth()
            listener = firestore.collection(COLLECTION_NAME)
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        Log.w(TAG, "Notice observing SpecialIdentifiers: ${error.message}")
                        return@addSnapshotListener
                    }
                    if (snapshot != null) {
                        val items = snapshot.documents.mapNotNull { doc ->
                            doc.data?.let { mapDocToEntity(doc.id, it) }
                        }
                        trySend(items)
                    }
                }
        } catch (e: Exception) {
            Log.w(TAG, "Notice registering snapshot listener: ${e.message}")
        }
        awaitClose { listener?.remove() }
    }

    private fun mapEntityToFirestore(entity: SpecialIdentifierEntity): Map<String, Any?> {
        return hashMapOf(
            "id" to entity.identifier,
            "identifier" to entity.identifier,
            "category" to entity.category,
            "status" to entity.status,
            "reservedForName" to entity.reservedForName,
            "assignedUserId" to entity.assignedUserId,
            "assignedCustomId" to entity.assignedCustomId,
            "assignedUsername" to entity.assignedUsername,
            "createdByAdminId" to entity.createdByAdminId,
            "createdAt" to entity.createdAt,
            "assignedAt" to entity.assignedAt,
            "notes" to entity.notes
        )
    }

    private fun mapDocToEntity(id: String, map: Map<String, Any?>): SpecialIdentifierEntity {
        return SpecialIdentifierEntity(
            id = (map["identifier"] as? String)?.ifEmpty { id } ?: id,
            identifier = (map["identifier"] as? String) ?: id,
            category = (map["category"] as? String) ?: SpecialIdentifierEntity.CATEGORY_VIP,
            status = (map["status"] as? String) ?: SpecialIdentifierEntity.STATUS_AVAILABLE,
            reservedForName = map["reservedForName"] as? String,
            assignedUserId = map["assignedUserId"] as? String,
            assignedCustomId = map["assignedCustomId"] as? String,
            assignedUsername = map["assignedUsername"] as? String,
            createdByAdminId = (map["createdByAdminId"] as? String) ?: AdminConstants.ADMIN_USER_ID,
            createdAt = (map["createdAt"] as? Number)?.toLong() ?: System.currentTimeMillis(),
            assignedAt = (map["assignedAt"] as? Number)?.toLong(),
            notes = map["notes"] as? String
        )
    }
}
