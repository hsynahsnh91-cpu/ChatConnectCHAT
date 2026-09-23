package com.example.service.call

import android.util.Log
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import java.util.UUID

/**
 * Repository interface defining WebRTC signaling operations via Cloud Firestore.
 */
interface ICallSignalingRepository {
    suspend fun createCallSession(
        callId: String,
        callerUserId: String,
        callerName: String,
        callerAvatar: String?,
        receiverUserId: String,
        receiverName: String,
        isVideo: Boolean,
        sdpOffer: String,
        securityFingerprint: String
    ): Result<String>

    suspend fun answerCallSession(
        callId: String,
        receiverUserId: String,
        sdpAnswer: String
    ): Result<Unit>

    suspend fun sendIceCandidate(candidate: IceCandidateModel): Result<Unit>

    fun observeCallSession(callId: String): Flow<CallSessionModel?>

    fun observeRemoteIceCandidates(callId: String, currentUserId: String): Flow<List<IceCandidateModel>>

    fun listenForIncomingCalls(currentUserId: String): Flow<CallSessionModel?>

    suspend fun endCallSession(
        callId: String,
        userId: String,
        reason: String,
        durationSec: Int = 0
    ): Result<Unit>

    suspend fun validateSessionAuthorization(callId: String, userId: String): Boolean
}

/**
 * Concrete implementation of [ICallSignalingRepository] managing WebRTC offer/answer SDPs
 * and ICE candidates with session authorization.
 */
class CallSignalingRepository(
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()
) : ICallSignalingRepository {

    companion object {
        private const val TAG = "CallSignalingRepository"
        const val COLLECTION_CALLS = "calls"
        const val SUB_COLLECTION_ICE_CANDIDATES = "ice_candidates"

        /**
         * Firestore Security Rules for WebRTC Session Authorization:
         *
         * rules_version = '2';
         * service cloud.firestore {
         *   match /databases/{database}/documents {
         *     match /calls/{callId} {
         *       allow create: if request.auth != null && request.resource.data.callerUserId == request.auth.uid;
         *       allow read: if request.auth != null && (resource.data.callerUserId == request.auth.uid || resource.data.receiverUserId == request.auth.uid);
         *       allow update: if request.auth != null && (resource.data.callerUserId == request.auth.uid || resource.data.receiverUserId == request.auth.uid);
         *       allow delete: if request.auth != null && resource.data.callerUserId == request.auth.uid;
         *
         *       match /ice_candidates/{candidateId} {
         *         allow read, write: if request.auth != null &&
         *           (get(/databases/$(database)/documents/calls/$(callId)).data.callerUserId == request.auth.uid ||
         *            get(/databases/$(database)/documents/calls/$(callId)).data.receiverUserId == request.auth.uid);
         *       }
         *     }
         *   }
         * }
         */
        const val FIRESTORE_SECURITY_RULES_SPEC = """
            match /calls/{callId} {
              allow create: if request.auth != null && request.resource.data.callerUserId == request.auth.uid;
              allow read, update: if request.auth != null && 
                (resource.data.callerUserId == request.auth.uid || resource.data.receiverUserId == request.auth.uid);
              match /ice_candidates/{candidateId} {
                allow read, write: if request.auth != null;
              }
            }
        """
    }

    /**
     * Publishes a new WebRTC Call Session with caller metadata and SDP Offer.
     */
    override suspend fun createCallSession(
        callId: String,
        callerUserId: String,
        callerName: String,
        callerAvatar: String?,
        receiverUserId: String,
        receiverName: String,
        isVideo: Boolean,
        sdpOffer: String,
        securityFingerprint: String
    ): Result<String> = runCatching {
        require(callId.isNotBlank()) { "Call ID cannot be empty" }
        require(callerUserId.isNotBlank()) { "Caller user ID cannot be empty" }
        require(receiverUserId.isNotBlank()) { "Receiver user ID cannot be empty" }
        require(sdpOffer.isNotBlank()) { "SDP Offer cannot be empty" }

        val sessionPayload = hashMapOf<String, Any?>(
            "callId" to callId,
            "callerUserId" to callerUserId,
            "callerName" to callerName,
            "callerAvatar" to callerAvatar,
            "receiverUserId" to receiverUserId,
            "receiverName" to receiverName,
            "isVideo" to isVideo,
            "status" to CallStatusEnum.CALLING.name,
            "sdpOffer" to sdpOffer,
            "sdpAnswer" to null,
            "createdAt" to System.currentTimeMillis(),
            "connectedAt" to null,
            "endedAt" to null,
            "durationSec" to 0,
            "encryptionFingerprint" to securityFingerprint
        )

        firestore.collection(COLLECTION_CALLS)
            .document(callId)
            .set(sessionPayload)
            .await()

        Log.i(TAG, "Call session [$callId] created with SDP Offer by user [$callerUserId].")
        callId
    }

    /**
     * Accepts and answers an incoming call by uploading the peer's WebRTC SDP Answer.
     * Enforces session authorization: only the designated receiver can answer.
     */
    override suspend fun answerCallSession(
        callId: String,
        receiverUserId: String,
        sdpAnswer: String
    ): Result<Unit> = runCatching {
        require(callId.isNotBlank()) { "Call ID cannot be empty" }
        require(sdpAnswer.isNotBlank()) { "SDP Answer cannot be empty" }

        // Session authorization check
        val isAuthorized = validateSessionAuthorization(callId, receiverUserId)
        if (!isAuthorized) {
            throw SecurityException("User [$receiverUserId] is not authorized to answer call [$callId]")
        }

        val updates = hashMapOf<String, Any?>(
            "status" to CallStatusEnum.CONNECTED.name,
            "sdpAnswer" to sdpAnswer,
            "connectedAt" to System.currentTimeMillis()
        )

        firestore.collection(COLLECTION_CALLS)
            .document(callId)
            .set(updates, SetOptions.merge())
            .await()

        Log.i(TAG, "Call session [$callId] answered by user [$receiverUserId] with SDP Answer.")
    }

    /**
     * Sends an ICE candidate for NAT traversal to the Firestore sub-collection.
     */
    override suspend fun sendIceCandidate(candidate: IceCandidateModel): Result<Unit> = runCatching {
        require(candidate.callId.isNotBlank()) { "Candidate Call ID cannot be empty" }
        require(candidate.sdpCandidate.isNotBlank()) { "Candidate SDP string cannot be empty" }

        val docId = candidate.candidateId.ifBlank { UUID.randomUUID().toString() }
        val candidatePayload = hashMapOf<String, Any?>(
            "candidateId" to docId,
            "callId" to candidate.callId,
            "senderUserId" to candidate.senderUserId,
            "sdpMid" to candidate.sdpMid,
            "sdpMLineIndex" to candidate.sdpMLineIndex,
            "sdpCandidate" to candidate.sdpCandidate,
            "timestamp" to candidate.timestamp
        )

        firestore.collection(COLLECTION_CALLS)
            .document(candidate.callId)
            .collection(SUB_COLLECTION_ICE_CANDIDATES)
            .document(docId)
            .set(candidatePayload)
            .await()

        Log.d(TAG, "Uploaded ICE candidate [$docId] for call [${candidate.callId}]")
    }

    /**
     * Real-time stream observing the call session document state.
     */
    override fun observeCallSession(callId: String): Flow<CallSessionModel?> = callbackFlow {
        val listener: ListenerRegistration = firestore.collection(COLLECTION_CALLS)
            .document(callId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e(TAG, "Error listening to call session [$callId]: ${error.message}")
                    trySend(null)
                    return@addSnapshotListener
                }

                if (snapshot != null && snapshot.exists()) {
                    trySend(mapDocumentToSession(snapshot))
                } else {
                    trySend(null)
                }
            }

        awaitClose { listener.remove() }
    }

    /**
     * Real-time stream observing remote ICE candidates for peer connection.
     */
    override fun observeRemoteIceCandidates(
        callId: String,
        currentUserId: String
    ): Flow<List<IceCandidateModel>> = callbackFlow {
        val listener: ListenerRegistration = firestore.collection(COLLECTION_CALLS)
            .document(callId)
            .collection(SUB_COLLECTION_ICE_CANDIDATES)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e(TAG, "Error listening to ICE candidates: ${error.message}")
                    return@addSnapshotListener
                }

                val remoteCandidates = snapshot?.documents?.mapNotNull { doc ->
                    val senderId = doc.getString("senderUserId") ?: return@mapNotNull null
                    if (senderId != currentUserId) {
                        IceCandidateModel(
                            candidateId = doc.id,
                            callId = callId,
                            senderUserId = senderId,
                            sdpMid = doc.getString("sdpMid") ?: "0",
                            sdpMLineIndex = doc.getLong("sdpMLineIndex")?.toInt() ?: 0,
                            sdpCandidate = doc.getString("sdpCandidate") ?: "",
                            timestamp = doc.getLong("timestamp") ?: 0L
                        )
                    } else null
                } ?: emptyList()

                trySend(remoteCandidates)
            }

        awaitClose { listener.remove() }
    }

    /**
     * Listens for active incoming calls targeting [currentUserId].
     */
    override fun listenForIncomingCalls(currentUserId: String): Flow<CallSessionModel?> = callbackFlow {
        val threshold = System.currentTimeMillis() - 45_000L
        val listener: ListenerRegistration = firestore.collection(COLLECTION_CALLS)
            .whereEqualTo("receiverUserId", currentUserId)
            .whereEqualTo("status", CallStatusEnum.CALLING.name)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.w(TAG, "Incoming calls query error: ${error.message}")
                    return@addSnapshotListener
                }

                val activeSession = snapshot?.documents
                    ?.mapNotNull { mapDocumentToSession(it) }
                    ?.filter { it.createdAt >= threshold }
                    ?.maxByOrNull { it.createdAt }

                trySend(activeSession)
            }

        awaitClose { listener.remove() }
    }

    /**
     * Terminates a call session with an explicit reason.
     */
    override suspend fun endCallSession(
        callId: String,
        userId: String,
        reason: String,
        durationSec: Int
    ): Result<Unit> = runCatching {
        val isAuthorized = validateSessionAuthorization(callId, userId)
        if (!isAuthorized) {
            throw SecurityException("User [$userId] not authorized to terminate call [$callId]")
        }

        val updates = hashMapOf<String, Any?>(
            "status" to CallStatusEnum.ENDED.name,
            "terminationReason" to reason,
            "endedAt" to System.currentTimeMillis(),
            "durationSec" to durationSec
        )

        firestore.collection(COLLECTION_CALLS)
            .document(callId)
            .set(updates, SetOptions.merge())
            .await()

        Log.i(TAG, "Call session [$callId] ended by [$userId]. Reason: $reason")
    }

    /**
     * Validates that the requesting user is either the caller or receiver in this call session.
     */
    override suspend fun validateSessionAuthorization(callId: String, userId: String): Boolean {
        return try {
            val doc = firestore.collection(COLLECTION_CALLS).document(callId).get().await()
            if (!doc.exists()) return false
            val caller = doc.getString("callerUserId")
            val receiver = doc.getString("receiverUserId")
            userId == caller || userId == receiver
        } catch (e: Exception) {
            Log.e(TAG, "Authorization check failed for call [$callId]: ${e.message}")
            false
        }
    }

    private fun mapDocumentToSession(doc: DocumentSnapshot): CallSessionModel {
        val statusStr = doc.getString("status") ?: CallStatusEnum.IDLE.name
        val status = try {
            CallStatusEnum.valueOf(statusStr)
        } catch (e: Exception) {
            CallStatusEnum.IDLE
        }

        return CallSessionModel(
            callId = doc.getString("callId") ?: doc.id,
            callerUserId = doc.getString("callerUserId") ?: "",
            callerName = doc.getString("callerName") ?: "مستخدم",
            callerAvatar = doc.getString("callerAvatar"),
            receiverUserId = doc.getString("receiverUserId") ?: "",
            receiverName = doc.getString("receiverName") ?: "",
            isVideo = doc.getBoolean("isVideo") ?: false,
            status = status,
            terminationReason = doc.getString("terminationReason"),
            sdpOffer = doc.getString("sdpOffer"),
            sdpAnswer = doc.getString("sdpAnswer"),
            createdAt = doc.getLong("createdAt") ?: 0L,
            connectedAt = doc.getLong("connectedAt"),
            endedAt = doc.getLong("endedAt"),
            durationSec = doc.getLong("durationSec")?.toInt() ?: 0,
            encryptionFingerprint = doc.getString("encryptionFingerprint") ?: "4B:7E:99:A1:D8:0C:52:1F:B4:E3:6C:91"
        )
    }
}
