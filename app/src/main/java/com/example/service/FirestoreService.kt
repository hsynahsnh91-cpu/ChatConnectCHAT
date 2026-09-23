package com.example.service

import android.util.Log
import com.example.data.local.entities.ChatEntity
import com.example.data.local.entities.DeviceSessionEntity
import com.example.data.local.entities.MessageEntity
import com.example.data.local.entities.SpecialIdentifierEntity
import com.example.data.local.entities.StatusEntity
import com.example.data.local.entities.StatusViewEntity
import com.example.data.local.entities.UserEntity
import com.example.service.call.CallHistoryRecord
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

class FirestoreService {
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()

    private val _isConnected = MutableStateFlow(true)
    val isConnected: StateFlow<Boolean> = _isConnected

    companion object {
        private const val TAG = "FirestoreService"
        private const val USERS_COLLECTION = "users"
        private const val CHATS_COLLECTION = "chats"
        private const val MESSAGES_SUBCOLLECTION = "messages"
        private const val STATUSES_COLLECTION = "statuses"
        private const val STATUS_VIEWS_COLLECTION = "status_views"
        private const val STATUS_MUTES_COLLECTION = "status_mutes"
        private const val SESSIONS_COLLECTION = "device_sessions"
        private const val AUTH_IDENTITIES_COLLECTION = "auth_identities"
        const val CALL_HISTORY_COLLECTION = "call_history"
        const val SPECIAL_IDENTIFIERS_COLLECTION = "SpecialIdentifiers"
        const val LEGACY_SPECIAL_IDENTIFIERS_COLLECTION = "special_identifiers"

        fun mapDocToMessage(id: String, map: Map<String, Any?>): MessageEntity {
            return MessageEntity(
                messageId = (map["messageId"] as? String) ?: id,
                chatId = (map["chatId"] as? String) ?: "",
                senderUserId = (map["senderUserId"] as? String) ?: "",
                type = (map["type"] as? String) ?: "TEXT",
                content = (map["content"] as? String) ?: "",
                mediaUri = map["mediaUri"] as? String,
                mediaFileName = map["mediaFileName"] as? String,
                mediaFileSize = (map["mediaFileSize"] as? Number)?.toLong() ?: 0L,
                mediaDurationMs = (map["mediaDurationMs"] as? Number)?.toLong() ?: 0L,
                caption = map["caption"] as? String,
                timestamp = (map["timestamp"] as? Number)?.toLong() ?: System.currentTimeMillis(),
                status = (map["status"] as? String) ?: "SENT",
                replyToMessageId = map["replyToMessageId"] as? String,
                uploadProgress = (map["uploadProgress"] as? Number)?.toFloat() ?: 1.0f,
                mimeType = map["mimeType"] as? String,
                latitude = (map["latitude"] as? Number)?.toDouble(),
                longitude = (map["longitude"] as? Number)?.toDouble(),
                locationAddress = map["locationAddress"] as? String,
                contactName = map["contactName"] as? String,
                contactPhone = map["contactPhone"] as? String,
                contactEmail = map["contactEmail"] as? String,
                readAt = (map["readAt"] as? Number)?.toLong()
            )
        }
    }

    // --- Device Sessions Persistence ---

    suspend fun saveDeviceSession(session: DeviceSessionEntity): Boolean {
        return try {
            val sessionMap = hashMapOf<String, Any?>(
                "sessionId" to session.sessionId,
                "userId" to session.userId,
                "deviceName" to session.deviceName,
                "deviceType" to session.deviceType,
                "platform" to session.platform,
                "clientApp" to session.clientApp,
                "ipAddress" to session.ipAddress,
                "location" to session.location,
                "isCurrentDevice" to session.isCurrentDevice,
                "createdAt" to session.createdAt,
                "lastActiveAt" to session.lastActiveAt,
                "isActive" to session.isActive
            )
            firestore.collection(USERS_COLLECTION)
                .document(session.userId)
                .collection(SESSIONS_COLLECTION)
                .document(session.sessionId)
                .set(sessionMap, SetOptions.merge())
                .await()
            true
        } catch (e: Exception) {
            Log.w(TAG, "Failed to save device session: ${e.message}")
            false
        }
    }

    suspend fun revokeDeviceSession(userId: String, sessionId: String): Boolean {
        return try {
            firestore.collection(USERS_COLLECTION)
                .document(userId)
                .collection(SESSIONS_COLLECTION)
                .document(sessionId)
                .update("isActive", false)
                .await()
            true
        } catch (e: Exception) {
            Log.w(TAG, "Failed to revoke device session: ${e.message}")
            false
        }
    }

    suspend fun deleteDeviceSession(userId: String, sessionId: String): Boolean {
        return try {
            firestore.collection(USERS_COLLECTION)
                .document(userId)
                .collection(SESSIONS_COLLECTION)
                .document(sessionId)
                .delete()
                .await()
            true
        } catch (e: Exception) {
            Log.w(TAG, "Failed to delete device session: ${e.message}")
            false
        }
    }

    fun observeDeviceSessions(userId: String): Flow<List<DeviceSessionEntity>> = callbackFlow {
        val registration = firestore.collection(USERS_COLLECTION)
            .document(userId)
            .collection(SESSIONS_COLLECTION)
            .whereEqualTo("isActive", true)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.w(TAG, "Listen to sessions failed: ${error.message}")
                    return@addSnapshotListener
                }
                if (snapshot != null) {
                    val sessions = snapshot.documents.mapNotNull { doc ->
                        mapDocToDeviceSession(doc.id, doc.data ?: emptyMap())
                    }
                    trySend(sessions)
                }
            }
        awaitClose { registration.remove() }
    }

    // --- Users Persistence ---

    suspend fun saveUser(user: UserEntity): Boolean {
        return try {
            val userMap = hashMapOf<String, Any?>(
                "userId" to user.userId,
                "customId" to user.customId,
                "username" to user.username,
                "email" to user.email,
                "firebaseUid" to user.firebaseUid,
                "passwordHash" to user.passwordHash,
                "profilePicUri" to user.profilePicUri,
                "bio" to user.bio,
                "isOnline" to user.isOnline,
                "lastSeenTimestamp" to user.lastSeenTimestamp,
                "createdAt" to user.createdAt
            )
            firestore.collection(USERS_COLLECTION)
                .document(user.userId)
                .set(userMap, SetOptions.merge())
                .await()
            _isConnected.value = true
            Log.d(TAG, "User saved to Firestore: ${user.userId}")
            true
        } catch (e: Exception) {
            Log.w(TAG, "Failed to save user to Firestore: ${e.message}")
            false
        }
    }

    suspend fun findUserByCustomId(customId: String): UserEntity? {
        return try {
            val snapshot = firestore.collection(USERS_COLLECTION)
                .whereEqualTo("customId", customId)
                .limit(1)
                .get()
                .await()

            if (!snapshot.isEmpty) {
                val doc = snapshot.documents[0]
                mapDocToUser(doc.id, doc.data ?: emptyMap())
            } else {
                null
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error finding user by custom ID: ${e.message}")
            null
        }
    }

    suspend fun findUserByEmail(email: String): UserEntity? {
        return try {
            val snapshot = firestore.collection(USERS_COLLECTION)
                .whereEqualTo("email", email)
                .limit(1)
                .get()
                .await()

            if (!snapshot.isEmpty) {
                val doc = snapshot.documents[0]
                mapDocToUser(doc.id, doc.data ?: emptyMap())
            } else {
                null
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error finding user by email: ${e.message}")
            null
        }
    }

    suspend fun getUser(userId: String): UserEntity? {
        return try {
            val doc = firestore.collection(USERS_COLLECTION)
                .document(userId)
                .get()
                .await()
            if (doc.exists()) {
                mapDocToUser(doc.id, doc.data ?: emptyMap())
            } else null
        } catch (e: Exception) {
            Log.w(TAG, "Error fetching user $userId: ${e.message}")
            null
        }
    }

    suspend fun getAllUsers(): List<UserEntity> {
        return try {
            val snapshot = firestore.collection(USERS_COLLECTION)
                .get()
                .await()
            snapshot.documents.mapNotNull { doc ->
                mapDocToUser(doc.id, doc.data ?: emptyMap())
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error fetching all users: ${e.message}")
            emptyList()
        }
    }

    fun observeUsers(): Flow<List<UserEntity>> = callbackFlow {
        val registration = firestore.collection(USERS_COLLECTION)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.w(TAG, "Listen to users failed: ${error.message}")
                    return@addSnapshotListener
                }
                if (snapshot != null) {
                    val users = snapshot.documents.mapNotNull { doc ->
                        mapDocToUser(doc.id, doc.data ?: emptyMap())
                    }
                    trySend(users)
                }
            }
        awaitClose { registration.remove() }
    }

    // --- Chats & Groups Persistence ---

    suspend fun saveChat(chat: ChatEntity, memberIds: List<String>): Boolean {
        return try {
            val chatMap = hashMapOf<String, Any?>(
                "chatId" to chat.chatId,
                "type" to chat.type,
                "groupName" to chat.groupName,
                "groupIconUri" to chat.groupIconUri,
                "groupDescription" to chat.groupDescription,
                "createdByUserId" to chat.createdByUserId,
                "groupOwnerId" to (chat.groupOwnerId ?: chat.createdByUserId),
                "editGroupInfoPermission" to chat.editGroupInfoPermission,
                "sendMessagesPermission" to chat.sendMessagesPermission,
                "addMembersPermission" to chat.addMembersPermission,
                "inviteToken" to chat.inviteToken,
                "pinnedMessageId" to chat.pinnedMessageId,
                "createdAt" to chat.createdAt,
                "updatedAt" to chat.updatedAt,
                "lastMessageTimestamp" to chat.lastMessageTimestamp,
                "isActive" to chat.isActive,
                "memberIds" to memberIds
            )
            firestore.collection(CHATS_COLLECTION)
                .document(chat.chatId)
                .set(chatMap, SetOptions.merge())
                .await()
            true
        } catch (e: Exception) {
            Log.w(TAG, "Failed to save chat: ${e.message}")
            false
        }
    }

    suspend fun updateGroupFirestoreInfo(chatId: String, name: String, iconUri: String?, description: String?): Boolean {
        return try {
            val updates = hashMapOf<String, Any?>(
                "groupName" to name,
                "groupIconUri" to iconUri,
                "groupDescription" to description,
                "updatedAt" to System.currentTimeMillis()
            )
            firestore.collection(CHATS_COLLECTION).document(chatId).update(updates).await()
            true
        } catch (e: Exception) {
            Log.w(TAG, "Failed to update group info in Firestore: ${e.message}")
            false
        }
    }

    suspend fun updateGroupPermissionsFirestore(chatId: String, editInfo: String, sendMessages: String, addMembers: String): Boolean {
        return try {
            val updates = hashMapOf<String, Any?>(
                "editGroupInfoPermission" to editInfo,
                "sendMessagesPermission" to sendMessages,
                "addMembersPermission" to addMembers,
                "updatedAt" to System.currentTimeMillis()
            )
            firestore.collection(CHATS_COLLECTION).document(chatId).update(updates).await()
            true
        } catch (e: Exception) {
            Log.w(TAG, "Failed to update group permissions in Firestore: ${e.message}")
            false
        }
    }

    suspend fun updateGroupOwnerFirestore(chatId: String, newOwnerId: String): Boolean {
        return try {
            firestore.collection(CHATS_COLLECTION).document(chatId).update(
                mapOf(
                    "groupOwnerId" to newOwnerId,
                    "updatedAt" to System.currentTimeMillis()
                )
            ).await()
            true
        } catch (e: Exception) {
            Log.w(TAG, "Failed to update group owner in Firestore: ${e.message}")
            false
        }
    }

    suspend fun addMemberToGroupFirestore(chatId: String, userId: String): Boolean {
        return try {
            firestore.collection(CHATS_COLLECTION).document(chatId)
                .update("memberIds", com.google.firebase.firestore.FieldValue.arrayUnion(userId))
                .await()
            true
        } catch (e: Exception) {
            Log.w(TAG, "Failed to add member to group in Firestore: ${e.message}")
            false
        }
    }

    suspend fun removeMemberFromGroupFirestore(chatId: String, userId: String): Boolean {
        return try {
            firestore.collection(CHATS_COLLECTION).document(chatId)
                .update("memberIds", com.google.firebase.firestore.FieldValue.arrayRemove(userId))
                .await()
            true
        } catch (e: Exception) {
            Log.w(TAG, "Failed to remove member from group in Firestore: ${e.message}")
            false
        }
    }

    suspend fun updateGroupInviteTokenFirestore(chatId: String, token: String?): Boolean {
        return try {
            firestore.collection(CHATS_COLLECTION).document(chatId).update(
                mapOf(
                    "inviteToken" to token,
                    "updatedAt" to System.currentTimeMillis()
                )
            ).await()
            true
        } catch (e: Exception) {
            Log.w(TAG, "Failed to update invite token in Firestore: ${e.message}")
            false
        }
    }

    suspend fun pinMessageInGroupFirestore(chatId: String, messageId: String?): Boolean {
        return try {
            firestore.collection(CHATS_COLLECTION).document(chatId).update(
                mapOf(
                    "pinnedMessageId" to messageId,
                    "updatedAt" to System.currentTimeMillis()
                )
            ).await()
            true
        } catch (e: Exception) {
            Log.w(TAG, "Failed to update pinned message in Firestore: ${e.message}")
            false
        }
    }

    suspend fun findGroupByInviteToken(token: String): ChatEntity? {
        return try {
            val snapshot = firestore.collection(CHATS_COLLECTION)
                .whereEqualTo("inviteToken", token)
                .whereEqualTo("isActive", true)
                .limit(1)
                .get()
                .await()
            if (!snapshot.isEmpty) {
                val doc = snapshot.documents[0]
                mapDocToChat(doc.id, doc.data ?: emptyMap())
            } else null
        } catch (e: Exception) {
            Log.w(TAG, "Failed to find group by invite token: ${e.message}")
            null
        }
    }

    suspend fun deleteGroupFirestore(chatId: String): Boolean {
        return try {
            firestore.collection(CHATS_COLLECTION).document(chatId).delete().await()
            true
        } catch (e: Exception) {
            Log.w(TAG, "Failed to delete group in Firestore: ${e.message}")
            false
        }
    }

    fun mapDocToChat(id: String, map: Map<String, Any?>): ChatEntity {
        return ChatEntity(
            chatId = (map["chatId"] as? String) ?: id,
            type = (map["type"] as? String) ?: "GROUP",
            groupName = map["groupName"] as? String,
            groupIconUri = map["groupIconUri"] as? String,
            groupDescription = map["groupDescription"] as? String,
            createdByUserId = (map["createdByUserId"] as? String) ?: "",
            groupOwnerId = map["groupOwnerId"] as? String,
            editGroupInfoPermission = (map["editGroupInfoPermission"] as? String) ?: "ALL",
            sendMessagesPermission = (map["sendMessagesPermission"] as? String) ?: "ALL",
            addMembersPermission = (map["addMembersPermission"] as? String) ?: "ALL",
            inviteToken = map["inviteToken"] as? String,
            pinnedMessageId = map["pinnedMessageId"] as? String,
            createdAt = (map["createdAt"] as? Number)?.toLong() ?: System.currentTimeMillis(),
            updatedAt = (map["updatedAt"] as? Number)?.toLong() ?: System.currentTimeMillis(),
            lastMessageTimestamp = (map["lastMessageTimestamp"] as? Number)?.toLong() ?: System.currentTimeMillis(),
            isActive = (map["isActive"] as? Boolean) ?: true
        )
    }

    // --- Messages Persistence ---

    suspend fun saveMessage(message: MessageEntity): Boolean {
        return try {
            val msgMap = hashMapOf<String, Any?>(
                "messageId" to message.messageId,
                "chatId" to message.chatId,
                "senderUserId" to message.senderUserId,
                "type" to message.type,
                "content" to message.content,
                "mediaUri" to message.mediaUri,
                "mediaFileName" to message.mediaFileName,
                "mediaFileSize" to message.mediaFileSize,
                "mediaDurationMs" to message.mediaDurationMs,
                "caption" to message.caption,
                "timestamp" to message.timestamp,
                "status" to message.status,
                "replyToMessageId" to message.replyToMessageId,
                "uploadProgress" to message.uploadProgress,
                "mimeType" to message.mimeType,
                "latitude" to message.latitude,
                "longitude" to message.longitude,
                "locationAddress" to message.locationAddress,
                "contactName" to message.contactName,
                "contactPhone" to message.contactPhone,
                "contactEmail" to message.contactEmail,
                "readAt" to message.readAt
            )
            firestore.collection(CHATS_COLLECTION)
                .document(message.chatId)
                .collection(MESSAGES_SUBCOLLECTION)
                .document(message.messageId)
                .set(msgMap, SetOptions.merge())
                .await()

            // Update chat's lastMessageTimestamp
            firestore.collection(CHATS_COLLECTION)
                .document(message.chatId)
                .update("lastMessageTimestamp", message.timestamp)

            true
        } catch (e: Exception) {
            Log.w(TAG, "Failed to save message to Firestore: ${e.message}")
            false
        }
    }

    suspend fun markMessageAsRead(chatId: String, messageId: String, readAtTimestamp: Long = System.currentTimeMillis()): Boolean {
        return try {
            firestore.collection(CHATS_COLLECTION)
                .document(chatId)
                .collection(MESSAGES_SUBCOLLECTION)
                .document(messageId)
                .update(
                    mapOf(
                        "status" to "READ",
                        "readAt" to readAtTimestamp
                    )
                ).await()
            true
        } catch (e: Exception) {
            Log.w(TAG, "Failed to mark message as read in Firestore: ${e.message}")
            false
        }
    }

    suspend fun markChatAsReadInFirestore(
        chatId: String,
        currentUserId: String,
        readAtTimestamp: Long = System.currentTimeMillis(),
        unreadMessageIds: List<String> = emptyList()
    ): Boolean {
        return try {
            val targetIds = unreadMessageIds.toMutableSet()
            try {
                val snapshot = firestore.collection(CHATS_COLLECTION)
                    .document(chatId)
                    .collection(MESSAGES_SUBCOLLECTION)
                    .whereNotEqualTo("senderUserId", currentUserId)
                    .get()
                    .await()

                for (doc in snapshot.documents) {
                    val status = doc.getString("status")
                    val readAt = doc.getLong("readAt")
                    if (status != "READ" || readAt == null) {
                        targetIds.add(doc.id)
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Direct Firestore query unread check fallback: ${e.message}")
            }

            if (targetIds.isEmpty()) return true

            val batches = targetIds.chunked(400)
            for (chunk in batches) {
                val batch = firestore.batch()
                for (msgId in chunk) {
                    val docRef = firestore.collection(CHATS_COLLECTION)
                        .document(chatId)
                        .collection(MESSAGES_SUBCOLLECTION)
                        .document(msgId)
                    batch.update(
                        docRef,
                        mapOf(
                            "status" to "READ",
                            "readAt" to readAtTimestamp
                        )
                    )
                }
                batch.commit().await()
            }
            true
        } catch (e: Exception) {
            Log.w(TAG, "Failed to batch mark chat as read in Firestore: ${e.message}")
            for (msgId in unreadMessageIds) {
                markMessageAsRead(chatId, msgId, readAtTimestamp)
            }
            false
        }
    }

    // --- Message Reactions Persistence (Sub-collection) ---

    suspend fun saveMessageReaction(
        chatId: String,
        messageId: String,
        userId: String,
        username: String,
        emoji: String
    ): Boolean {
        return try {
            val reactionMap = hashMapOf<String, Any?>(
                "messageId" to messageId,
                "chatId" to chatId,
                "userId" to userId,
                "username" to username,
                "emoji" to emoji,
                "timestamp" to System.currentTimeMillis()
            )
            firestore.collection(CHATS_COLLECTION)
                .document(chatId)
                .collection(MESSAGES_SUBCOLLECTION)
                .document(messageId)
                .collection("reactions")
                .document(userId)
                .set(reactionMap, SetOptions.merge())
                .await()
            true
        } catch (e: Exception) {
            Log.w(TAG, "Failed to save message reaction in Firestore: ${e.message}")
            false
        }
    }

    suspend fun removeMessageReaction(
        chatId: String,
        messageId: String,
        userId: String
    ): Boolean {
        return try {
            firestore.collection(CHATS_COLLECTION)
                .document(chatId)
                .collection(MESSAGES_SUBCOLLECTION)
                .document(messageId)
                .collection("reactions")
                .document(userId)
                .delete()
                .await()
            true
        } catch (e: Exception) {
            Log.w(TAG, "Failed to delete message reaction in Firestore: ${e.message}")
            false
        }
    }

    fun observeMessageReactions(chatId: String, messageId: String): Flow<List<com.example.data.local.entities.MessageReactionEntity>> = callbackFlow {
        val registration = firestore.collection(CHATS_COLLECTION)
            .document(chatId)
            .collection(MESSAGES_SUBCOLLECTION)
            .document(messageId)
            .collection("reactions")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    return@addSnapshotListener
                }
                if (snapshot != null) {
                    val reactions = snapshot.documents.mapNotNull { doc ->
                        val data = doc.data ?: return@mapNotNull null
                        com.example.data.local.entities.MessageReactionEntity(
                            messageId = data["messageId"] as? String ?: messageId,
                            chatId = data["chatId"] as? String ?: chatId,
                            userId = doc.id,
                            username = data["username"] as? String ?: "",
                            emoji = data["emoji"] as? String ?: "",
                            timestamp = (data["timestamp"] as? Number)?.toLong() ?: 0L
                        )
                    }
                    trySend(reactions)
                }
            }
        awaitClose { registration.remove() }
    }

    fun observeMessages(chatId: String): Flow<List<MessageEntity>> = callbackFlow {
        val registration = firestore.collection(CHATS_COLLECTION)
            .document(chatId)
            .collection(MESSAGES_SUBCOLLECTION)
            .orderBy("timestamp", Query.Direction.ASCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.w(TAG, "Listen to messages failed: ${error.message}")
                    return@addSnapshotListener
                }
                if (snapshot != null) {
                    val messages = snapshot.documents.mapNotNull { doc ->
                        mapDocToMessage(doc.id, doc.data ?: emptyMap())
                    }
                    trySend(messages)
                }
            }
        awaitClose { registration.remove() }
    }

    // --- Statuses Persistence ---

    suspend fun saveStatus(status: StatusEntity): Boolean {
        return try {
            val statusMap = hashMapOf<String, Any?>(
                "statusId" to status.statusId,
                "userId" to status.userId,
                "type" to status.type,
                "contentText" to status.contentText,
                "mediaUri" to status.mediaUri,
                "caption" to status.caption,
                "bgColorHex" to status.bgColorHex,
                "textStyle" to status.textStyle,
                "textAlignment" to status.textAlignment,
                "privacyType" to status.privacyType,
                "privacyTargetIds" to status.privacyTargetIds,
                "isDeleted" to status.isDeleted,
                "createdAt" to status.createdAt,
                "expiresAt" to status.expiresAt
            )
            firestore.collection(STATUSES_COLLECTION)
                .document(status.statusId)
                .set(statusMap, SetOptions.merge())
                .await()
            true
        } catch (e: Exception) {
            Log.w(TAG, "Failed to save status: ${e.message}")
            false
        }
    }

    suspend fun deleteStatus(statusId: String): Boolean {
        return try {
            firestore.collection(STATUSES_COLLECTION)
                .document(statusId)
                .update("isDeleted", true)
                .await()
            true
        } catch (e: Exception) {
            Log.w(TAG, "Failed to mark status deleted: ${e.message}")
            try {
                firestore.collection(STATUSES_COLLECTION).document(statusId).delete().await()
                true
            } catch (ex: Exception) {
                false
            }
        }
    }

    fun observeActiveStatuses(): Flow<List<StatusEntity>> = callbackFlow {
        val now = System.currentTimeMillis()
        val registration = firestore.collection(STATUSES_COLLECTION)
            .whereGreaterThan("expiresAt", now)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.w(TAG, "Listen to statuses failed: ${error.message}")
                    return@addSnapshotListener
                }
                if (snapshot != null) {
                    val statuses = snapshot.documents.mapNotNull { doc ->
                        mapDocToStatus(doc.id, doc.data ?: emptyMap())
                    }
                    trySend(statuses)
                }
            }
        awaitClose { registration.remove() }
    }

    // --- Status Views Persistence (Strictly authorized per owner) ---

    suspend fun recordStatusView(view: StatusViewEntity): Boolean {
        return try {
            val docId = "${view.statusId}_${view.viewerUserId}"
            val map = hashMapOf<String, Any?>(
                "statusId" to view.statusId,
                "viewerUserId" to view.viewerUserId,
                "viewerName" to view.viewerName,
                "viewerProfilePic" to view.viewerProfilePic,
                "statusOwnerUserId" to view.statusOwnerUserId,
                "viewedAt" to view.viewedAt
            )
            firestore.collection(STATUS_VIEWS_COLLECTION)
                .document(docId)
                .set(map, SetOptions.merge())
                .await()
            true
        } catch (e: Exception) {
            Log.w(TAG, "Failed to record status view: ${e.message}")
            false
        }
    }

    fun observeStatusViewsForStatus(statusId: String): Flow<List<StatusViewEntity>> = callbackFlow {
        val registration = firestore.collection(STATUS_VIEWS_COLLECTION)
            .whereEqualTo("statusId", statusId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.w(TAG, "Listen to status views failed: ${error.message}")
                    return@addSnapshotListener
                }
                if (snapshot != null) {
                    val views = snapshot.documents.mapNotNull { doc ->
                        val data = doc.data ?: return@mapNotNull null
                        StatusViewEntity(
                            id = 0,
                            statusId = (data["statusId"] as? String) ?: "",
                            viewerUserId = (data["viewerUserId"] as? String) ?: "",
                            viewerName = (data["viewerName"] as? String) ?: "",
                            viewerProfilePic = data["viewerProfilePic"] as? String,
                            statusOwnerUserId = (data["statusOwnerUserId"] as? String) ?: "",
                            viewedAt = (data["viewedAt"] as? Number)?.toLong() ?: System.currentTimeMillis()
                        )
                    }
                    trySend(views)
                }
            }
        awaitClose { registration.remove() }
    }

    // --- Status Mutes Persistence ---

    suspend fun saveStatusMute(userId: String, mutedUserId: String): Boolean {
        return try {
            val docId = "${userId}_${mutedUserId}"
            val map = hashMapOf<String, Any?>(
                "userId" to userId,
                "mutedUserId" to mutedUserId,
                "mutedAt" to System.currentTimeMillis()
            )
            firestore.collection(STATUS_MUTES_COLLECTION)
                .document(docId)
                .set(map, SetOptions.merge())
                .await()
            true
        } catch (e: Exception) {
            Log.w(TAG, "Failed to save status mute: ${e.message}")
            false
        }
    }

    suspend fun deleteStatusMute(userId: String, mutedUserId: String): Boolean {
        return try {
            val docId = "${userId}_${mutedUserId}"
            firestore.collection(STATUS_MUTES_COLLECTION)
                .document(docId)
                .delete()
                .await()
            true
        } catch (e: Exception) {
            Log.w(TAG, "Failed to remove status mute: ${e.message}")
            false
        }
    }

    fun observeUserStatusMutes(userId: String): Flow<List<String>> = callbackFlow {
        val registration = firestore.collection(STATUS_MUTES_COLLECTION)
            .whereEqualTo("userId", userId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.w(TAG, "Listen to status mutes failed: ${error.message}")
                    return@addSnapshotListener
                }
                if (snapshot != null) {
                    val mutedIds = snapshot.documents.mapNotNull { doc ->
                        doc.getString("mutedUserId")
                    }
                    trySend(mutedIds)
                }
            }
        awaitClose { registration.remove() }
    }

    // --- Mappers ---

    private fun mapDocToUser(id: String, map: Map<String, Any?>): UserEntity {
        return UserEntity(
            userId = (map["userId"] as? String) ?: id,
            customId = (map["customId"] as? String) ?: "",
            username = (map["username"] as? String) ?: "User",
            email = map["email"] as? String,
            firebaseUid = (map["firebaseUid"] as? String) ?: id,
            passwordHash = (map["passwordHash"] as? String) ?: "",
            profilePicUri = map["profilePicUri"] as? String,
            bio = (map["bio"] as? String) ?: "Hey there! I am using ChatConnect.",
            isOnline = (map["isOnline"] as? Boolean) ?: true,
            lastSeenTimestamp = (map["lastSeenTimestamp"] as? Number)?.toLong() ?: System.currentTimeMillis(),
            createdAt = (map["createdAt"] as? Number)?.toLong() ?: System.currentTimeMillis()
        )
    }

    // --- Auth Identities Persistence ---

    suspend fun saveAuthIdentity(identity: com.example.data.local.entities.AuthIdentityEntity): Boolean {
        return try {
            val docId = "${identity.provider}_${identity.providerUserId}"
            val map = hashMapOf<String, Any?>(
                "provider" to identity.provider,
                "providerUserId" to identity.providerUserId,
                "userId" to identity.userId,
                "email" to identity.email,
                "displayName" to identity.displayName,
                "photoUrl" to identity.photoUrl,
                "linkedAt" to identity.linkedAt,
                "lastLoginAt" to identity.lastLoginAt
            )
            firestore.collection(AUTH_IDENTITIES_COLLECTION)
                .document(docId)
                .set(map, SetOptions.merge())
                .await()
            true
        } catch (e: Exception) {
            Log.w(TAG, "Failed to save auth identity: ${e.message}")
            false
        }
    }

    suspend fun getAuthIdentity(provider: String, providerUserId: String): com.example.data.local.entities.AuthIdentityEntity? {
        return try {
            val docId = "${provider}_${providerUserId}"
            val doc = firestore.collection(AUTH_IDENTITIES_COLLECTION)
                .document(docId)
                .get()
                .await()
            if (doc.exists()) {
                val data = doc.data ?: return null
                com.example.data.local.entities.AuthIdentityEntity(
                    provider = (data["provider"] as? String) ?: provider,
                    providerUserId = (data["providerUserId"] as? String) ?: providerUserId,
                    userId = (data["userId"] as? String) ?: "",
                    email = data["email"] as? String,
                    displayName = data["displayName"] as? String,
                    photoUrl = data["photoUrl"] as? String,
                    linkedAt = (data["linkedAt"] as? Number)?.toLong() ?: System.currentTimeMillis(),
                    lastLoginAt = (data["lastLoginAt"] as? Number)?.toLong() ?: System.currentTimeMillis()
                )
            } else null
        } catch (e: Exception) {
            Log.w(TAG, "Failed to get auth identity: ${e.message}")
            null
        }
    }

    private fun mapDocToStatus(id: String, map: Map<String, Any?>): StatusEntity? {
        val isDeleted = (map["isDeleted"] as? Boolean) ?: false
        if (isDeleted) return null
        return StatusEntity(
            statusId = (map["statusId"] as? String) ?: id,
            userId = (map["userId"] as? String) ?: "",
            type = (map["type"] as? String) ?: "TEXT",
            contentText = map["contentText"] as? String,
            mediaUri = map["mediaUri"] as? String,
            caption = map["caption"] as? String,
            bgColorHex = (map["bgColorHex"] as? String) ?: "#075E54",
            textStyle = (map["textStyle"] as? String) ?: "NORMAL",
            textAlignment = (map["textAlignment"] as? String) ?: "CENTER",
            privacyType = (map["privacyType"] as? String) ?: "MY_CONTACTS",
            privacyTargetIds = (map["privacyTargetIds"] as? String) ?: "",
            isDeleted = isDeleted,
            createdAt = (map["createdAt"] as? Number)?.toLong() ?: System.currentTimeMillis(),
            expiresAt = (map["expiresAt"] as? Number)?.toLong() ?: (System.currentTimeMillis() + 86400000L)
        )
    }

    // --- Call History Collection Persistence & Indexing ---

    /**
     * Saves a call history record into Firestore 'call_history' collection.
     * Schema includes: callId, duration, status, participants, isVideo, timestamp, caller/receiver details.
     */
    suspend fun saveCallHistoryRecord(record: CallHistoryRecord): Boolean {
        return try {
            val recordMap = hashMapOf<String, Any?>(
                "callId" to record.callId,
                "callerUserId" to record.callerUserId,
                "callerName" to record.callerName,
                "callerAvatar" to record.callerAvatar,
                "receiverUserId" to record.receiverUserId,
                "receiverName" to record.receiverName,
                "receiverAvatar" to record.receiverAvatar,
                "participants" to if (record.participants.isNotEmpty()) record.participants else listOf(record.callerUserId, record.receiverUserId),
                "isVideo" to record.isVideo,
                "status" to record.status,
                "durationSec" to record.durationSec,
                "timestamp" to record.timestamp,
                "startedAt" to record.startedAt,
                "endedAt" to record.endedAt
            )
            firestore.collection(CALL_HISTORY_COLLECTION)
                .document(record.callId)
                .set(recordMap, SetOptions.merge())
                .await()
            Log.i(TAG, "Successfully recorded call history for callId: ${record.callId}")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save call history record: ${e.message}")
            false
        }
    }

    /**
     * Observes real-time call history list for a user.
     * Uses composite index on:
     * - Collection: 'call_history'
     * - Field 1: 'participants' (Arrays)
     * - Field 2: 'timestamp' (Descending)
     */
    fun observeUserCallHistory(userId: String): Flow<List<CallHistoryRecord>> = callbackFlow {
        val registration = firestore.collection(CALL_HISTORY_COLLECTION)
            .whereArrayContains("participants", userId)
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .limit(100)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.w(TAG, "Listen to call history failed: ${error.message}")
                    return@addSnapshotListener
                }
                if (snapshot != null) {
                    val list = snapshot.documents.mapNotNull { doc ->
                        mapDocToCallHistoryRecord(doc.id, doc.data ?: emptyMap())
                    }
                    trySend(list)
                }
            }
        awaitClose { registration.remove() }
    }

    /**
     * Deletes a single call history record.
     */
    suspend fun deleteCallHistoryRecord(callId: String): Boolean {
        return try {
            firestore.collection(CALL_HISTORY_COLLECTION)
                .document(callId)
                .delete()
                .await()
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to delete call history record: ${e.message}")
            false
        }
    }

    private fun mapDocToCallHistoryRecord(id: String, map: Map<String, Any?>): CallHistoryRecord {
        @Suppress("UNCHECKED_CAST")
        val participantsList = (map["participants"] as? List<String>) ?: emptyList()
        return CallHistoryRecord(
            callId = (map["callId"] as? String) ?: id,
            callerUserId = (map["callerUserId"] as? String) ?: "",
            callerName = (map["callerName"] as? String) ?: "",
            callerAvatar = map["callerAvatar"] as? String,
            receiverUserId = (map["receiverUserId"] as? String) ?: "",
            receiverName = (map["receiverName"] as? String) ?: "",
            receiverAvatar = map["receiverAvatar"] as? String,
            participants = participantsList,
            isVideo = (map["isVideo"] as? Boolean) ?: false,
            status = (map["status"] as? String) ?: "MISSED",
            durationSec = (map["durationSec"] as? Number)?.toInt() ?: 0,
            timestamp = (map["timestamp"] as? Number)?.toLong() ?: System.currentTimeMillis(),
            startedAt = (map["startedAt"] as? Number)?.toLong(),
            endedAt = (map["endedAt"] as? Number)?.toLong()
        )
    }

    private fun mapDocToDeviceSession(id: String, map: Map<String, Any?>): DeviceSessionEntity {
        return DeviceSessionEntity(
            sessionId = (map["sessionId"] as? String) ?: id,
            userId = (map["userId"] as? String) ?: "",
            deviceName = (map["deviceName"] as? String) ?: "Unknown Device",
            deviceType = (map["deviceType"] as? String) ?: "PHONE",
            platform = (map["platform"] as? String) ?: "Unknown OS",
            clientApp = (map["clientApp"] as? String) ?: "ChatConnect",
            ipAddress = (map["ipAddress"] as? String) ?: "192.168.1.1",
            location = (map["location"] as? String) ?: "Active Network",
            isCurrentDevice = (map["isCurrentDevice"] as? Boolean) ?: false,
            createdAt = (map["createdAt"] as? Number)?.toLong() ?: System.currentTimeMillis(),
            lastActiveAt = (map["lastActiveAt"] as? Number)?.toLong() ?: System.currentTimeMillis(),
            isActive = (map["isActive"] as? Boolean) ?: true
        )
    }

    // --- Special Identifiers Management ---

    suspend fun saveSpecialIdentifier(item: SpecialIdentifierEntity): Boolean {
        return try {
            val map = hashMapOf<String, Any?>(
                "id" to item.id,
                "identifier" to item.identifier,
                "category" to item.category,
                "status" to item.status,
                "reservedForName" to item.reservedForName,
                "assignedUserId" to item.assignedUserId,
                "assignedCustomId" to item.assignedCustomId,
                "assignedUsername" to item.assignedUsername,
                "createdByAdminId" to item.createdByAdminId,
                "createdAt" to item.createdAt,
                "assignedAt" to item.assignedAt,
                "notes" to item.notes
            )
            firestore.collection(SPECIAL_IDENTIFIERS_COLLECTION)
                .document(item.id)
                .set(map, SetOptions.merge())
                .await()
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error saving special identifier: ${e.message}", e)
            false
        }
    }

    suspend fun deleteSpecialIdentifier(id: String): Boolean {
        return try {
            firestore.collection(SPECIAL_IDENTIFIERS_COLLECTION)
                .document(id)
                .delete()
                .await()
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting special identifier: ${e.message}", e)
            false
        }
    }

    suspend fun getSpecialIdentifier(identifier: String): SpecialIdentifierEntity? {
        return try {
            val snapshot = firestore.collection(SPECIAL_IDENTIFIERS_COLLECTION)
                .whereEqualTo("identifier", identifier)
                .limit(1)
                .get()
                .await()
            if (!snapshot.isEmpty) {
                val doc = snapshot.documents[0]
                mapDocToSpecialIdentifier(doc.id, doc.data ?: emptyMap())
            } else {
                val legacySnap = firestore.collection(LEGACY_SPECIAL_IDENTIFIERS_COLLECTION)
                    .whereEqualTo("identifier", identifier)
                    .limit(1)
                    .get()
                    .await()
                if (!legacySnap.isEmpty) {
                    val doc = legacySnap.documents[0]
                    mapDocToSpecialIdentifier(doc.id, doc.data ?: emptyMap())
                } else null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error finding special identifier: ${e.message}", e)
            null
        }
    }

    fun observeSpecialIdentifiers(): Flow<List<SpecialIdentifierEntity>> = callbackFlow {
        var listener: ListenerRegistration? = null
        try {
            listener = firestore.collection(SPECIAL_IDENTIFIERS_COLLECTION)
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        Log.e(TAG, "Error observing special identifiers: ${error.message}")
                        return@addSnapshotListener
                    }
                    if (snapshot != null) {
                        val items = snapshot.documents.mapNotNull { doc ->
                            doc.data?.let { mapDocToSpecialIdentifier(doc.id, it) }
                        }
                        trySend(items)
                    }
                }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to register special identifiers listener: ${e.message}")
        }
        awaitClose { listener?.remove() }
    }

    private fun mapDocToSpecialIdentifier(id: String, map: Map<String, Any?>): SpecialIdentifierEntity {
        return SpecialIdentifierEntity(
            id = (map["id"] as? String) ?: id,
            identifier = (map["identifier"] as? String) ?: "",
            category = (map["category"] as? String) ?: "VIP",
            status = (map["status"] as? String) ?: SpecialIdentifierEntity.STATUS_AVAILABLE,
            reservedForName = map["reservedForName"] as? String,
            assignedUserId = map["assignedUserId"] as? String,
            assignedCustomId = map["assignedCustomId"] as? String,
            assignedUsername = map["assignedUsername"] as? String,
            createdByAdminId = (map["createdByAdminId"] as? String) ?: "",
            createdAt = (map["createdAt"] as? Number)?.toLong() ?: System.currentTimeMillis(),
            assignedAt = (map["assignedAt"] as? Number)?.toLong(),
            notes = map["notes"] as? String
        )
    }
}
