package com.example.service.call

import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import java.util.UUID

class CallSignalingService {
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()

    companion object {
        private const val TAG = "CallSignalingService"
        private const val CALLS_COLLECTION = "calls"
        private const val ICE_CANDIDATES_COLLECTION = "ice_candidates"
    }

    /**
     * Creates a new call session document in Firestore with DTLS-SRTP SDP Offer.
     */
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
    ): Boolean {
        return try {
            val sessionData = hashMapOf<String, Any?>(
                "callId" to callId,
                "callerUserId" to callerUserId,
                "callerName" to callerName,
                "callerAvatar" to callerAvatar,
                "receiverUserId" to receiverUserId,
                "receiverName" to receiverName,
                "isVideo" to isVideo,
                "status" to CallStatusEnum.CALLING.name,
                "terminationReason" to null,
                "sdpOffer" to sdpOffer,
                "sdpAnswer" to null,
                "createdAt" to System.currentTimeMillis(),
                "connectedAt" to null,
                "endedAt" to null,
                "durationSec" to 0,
                "encryptionFingerprint" to securityFingerprint
            )

            firestore.collection(CALLS_COLLECTION)
                .document(callId)
                .set(sessionData)
                .await()

            Log.d(TAG, "Call session created successfully: $callId")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create call session: ${e.message}", e)
            false
        }
    }

    /**
     * Answers an incoming call by uploading the SDP Answer and transitioning to CONNECTED.
     */
    suspend fun answerCallSession(
        callId: String,
        sdpAnswer: String
    ): Boolean {
        return try {
            val updates = hashMapOf<String, Any?>(
                "status" to CallStatusEnum.CONNECTED.name,
                "sdpAnswer" to sdpAnswer,
                "connectedAt" to System.currentTimeMillis()
            )
            firestore.collection(CALLS_COLLECTION)
                .document(callId)
                .set(updates, SetOptions.merge())
                .await()

            Log.d(TAG, "Call session answered: $callId")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to answer call session: ${e.message}", e)
            false
        }
    }

    /**
     * Updates call session status (e.g. RINGING, RECONNECTING).
     */
    suspend fun updateCallStatus(callId: String, newStatus: CallStatusEnum): Boolean {
        return try {
            firestore.collection(CALLS_COLLECTION)
                .document(callId)
                .update("status", newStatus.name)
                .await()
            true
        } catch (e: Exception) {
            Log.w(TAG, "Failed to update call status: ${e.message}")
            false
        }
    }

    /**
     * Terminates a call session with an explicit reason.
     */
    suspend fun endCallSession(
        callId: String,
        status: CallStatusEnum,
        reason: String,
        durationSec: Int = 0
    ): Boolean {
        return try {
            val updates = hashMapOf<String, Any?>(
                "status" to status.name,
                "terminationReason" to reason,
                "endedAt" to System.currentTimeMillis(),
                "durationSec" to durationSec
            )
            firestore.collection(CALLS_COLLECTION)
                .document(callId)
                .set(updates, SetOptions.merge())
                .await()

            Log.d(TAG, "Call session terminated: $callId with reason: $reason, status: $status")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to terminate call session: ${e.message}", e)
            false
        }
    }

    /**
     * Sends an ICE candidate to the peer.
     */
    suspend fun sendIceCandidate(candidate: IceCandidateModel): Boolean {
        return try {
            val candidateMap = hashMapOf<String, Any?>(
                "candidateId" to (candidate.candidateId.ifBlank { UUID.randomUUID().toString() }),
                "callId" to candidate.callId,
                "senderUserId" to candidate.senderUserId,
                "sdpMid" to candidate.sdpMid,
                "sdpMLineIndex" to candidate.sdpMLineIndex,
                "sdpCandidate" to candidate.sdpCandidate,
                "timestamp" to candidate.timestamp
            )
            firestore.collection(CALLS_COLLECTION)
                .document(candidate.callId)
                .collection(ICE_CANDIDATES_COLLECTION)
                .document(candidate.candidateId)
                .set(candidateMap)
                .await()
            true
        } catch (e: Exception) {
            Log.w(TAG, "Failed to upload ICE candidate: ${e.message}")
            false
        }
    }

    /**
     * Realtime observation of a specific call session document.
     */
    fun observeCallSession(callId: String): Flow<CallSessionModel?> = callbackFlow {
        val registration: ListenerRegistration = firestore.collection(CALLS_COLLECTION)
            .document(callId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e(TAG, "Error listening to call session: ${error.message}")
                    trySend(null)
                    return@addSnapshotListener
                }

                if (snapshot != null && snapshot.exists()) {
                    val model = mapSnapshotToCallSession(snapshot.data ?: emptyMap())
                    trySend(model)
                } else {
                    trySend(null)
                }
            }

        awaitClose { registration.remove() }
    }

    /**
     * Realtime observation of remote ICE candidates.
     */
    fun observeRemoteIceCandidates(callId: String, currentUserId: String): Flow<List<IceCandidateModel>> = callbackFlow {
        val registration: ListenerRegistration = firestore.collection(CALLS_COLLECTION)
            .document(callId)
            .collection(ICE_CANDIDATES_COLLECTION)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e(TAG, "Error observing ICE candidates: ${error.message}")
                    return@addSnapshotListener
                }

                val list = snapshot?.documents?.mapNotNull { doc ->
                    val data = doc.data ?: return@mapNotNull null
                    val sender = data["senderUserId"] as? String ?: return@mapNotNull null
                    // Only collect candidates originating from the remote party
                    if (sender != currentUserId) {
                        IceCandidateModel(
                            candidateId = doc.id,
                            callId = callId,
                            senderUserId = sender,
                            sdpMid = (data["sdpMid"] as? String) ?: "0",
                            sdpMLineIndex = (data["sdpMLineIndex"] as? Number)?.toInt() ?: 0,
                            sdpCandidate = (data["sdpCandidate"] as? String) ?: "",
                            timestamp = (data["timestamp"] as? Number)?.toLong() ?: 0L
                        )
                    } else null
                } ?: emptyList()

                trySend(list)
            }

        awaitClose { registration.remove() }
    }

    /**
     * Listens for incoming calls targeting [currentUserId].
     */
    fun listenForIncomingCalls(currentUserId: String): Flow<CallSessionModel?> = callbackFlow {
        // Query recent incoming calls within last 45 seconds to avoid replaying old expired calls
        val recentThreshold = System.currentTimeMillis() - 45_000L

        val registration: ListenerRegistration = firestore.collection(CALLS_COLLECTION)
            .whereEqualTo("receiverUserId", currentUserId)
            .whereEqualTo("status", CallStatusEnum.CALLING.name)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.w(TAG, "Incoming calls listener error: ${error.message}")
                    return@addSnapshotListener
                }

                val activeIncoming = snapshot?.documents?.mapNotNull { doc ->
                    val data = doc.data ?: return@mapNotNull null
                    val createdAt = (data["createdAt"] as? Number)?.toLong() ?: 0L
                    if (createdAt >= recentThreshold) {
                        mapSnapshotToCallSession(data)
                    } else null
                }?.maxByOrNull { it.createdAt }

                trySend(activeIncoming)
            }

        awaitClose { registration.remove() }
    }

    private fun mapSnapshotToCallSession(data: Map<String, Any?>): CallSessionModel {
        val statusStr = data["status"] as? String ?: CallStatusEnum.IDLE.name
        val statusEnum = try {
            CallStatusEnum.valueOf(statusStr)
        } catch (e: Exception) {
            CallStatusEnum.IDLE
        }

        return CallSessionModel(
            callId = (data["callId"] as? String) ?: "",
            callerUserId = (data["callerUserId"] as? String) ?: "",
            callerName = (data["callerName"] as? String) ?: "مستخدم",
            callerAvatar = data["callerAvatar"] as? String,
            receiverUserId = (data["receiverUserId"] as? String) ?: "",
            receiverName = (data["receiverName"] as? String) ?: "",
            isVideo = (data["isVideo"] as? Boolean) ?: false,
            status = statusEnum,
            terminationReason = data["terminationReason"] as? String,
            sdpOffer = data["sdpOffer"] as? String,
            sdpAnswer = data["sdpAnswer"] as? String,
            createdAt = (data["createdAt"] as? Number)?.toLong() ?: System.currentTimeMillis(),
            connectedAt = (data["connectedAt"] as? Number)?.toLong(),
            endedAt = (data["endedAt"] as? Number)?.toLong(),
            durationSec = (data["durationSec"] as? Number)?.toInt() ?: 0,
            encryptionFingerprint = (data["encryptionFingerprint"] as? String) ?: "4B:7E:99:A1:D8:0C:52:1F:B4:E3:6C:91"
        )
    }
}
