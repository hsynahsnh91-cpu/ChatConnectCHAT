package com.example.service.call

enum class CallStatusEnum {
    IDLE,
    CALLING,      // Initiator is calling receiver (outgoing ringing)
    RINGING,      // Receiver device is ringing (incoming call)
    CONNECTING,   // SDP Offer/Answer exchanged, ICE candidates negotiating
    CONNECTED,    // Media established, DTLS-SRTP active, call in progress
    RECONNECTING, // Temporary network interruption/handover
    ENDED,        // Terminated cleanly by user
    DECLINED,     // Rejected by receiver
    MISSED,       // Ringing timed out without answer
    BUSY,         // Receiver is already engaged in another call
    FAILED        // Irrecoverable signaling or network failure
}

enum class AudioOutputDevice {
    SPEAKER,
    EARPIECE,
    BLUETOOTH
}

enum class NetworkQualityState {
    EXCELLENT,
    GOOD,
    POOR,
    RECONNECTING
}

data class CallSessionModel(
    val callId: String = "",
    val callerUserId: String = "",
    val callerName: String = "",
    val callerAvatar: String? = null,
    val receiverUserId: String = "",
    val receiverName: String = "",
    val isVideo: Boolean = false,
    val status: CallStatusEnum = CallStatusEnum.IDLE,
    val terminationReason: String? = null,
    val sdpOffer: String? = null,
    val sdpAnswer: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val connectedAt: Long? = null,
    val endedAt: Long? = null,
    val durationSec: Int = 0,
    val encryptionFingerprint: String = "4B:7E:99:A1:D8:0C:52:1F:B4:E3:6C:91",
    val isMuted: Boolean = false,
    val isSpeakerOn: Boolean = false,
    val isCameraOn: Boolean = true,
    val isFrontCamera: Boolean = true,
    val activeAudioDevice: AudioOutputDevice = AudioOutputDevice.EARPIECE,
    val networkQuality: NetworkQualityState = NetworkQualityState.EXCELLENT
)

data class IceCandidateModel(
    val candidateId: String = "",
    val callId: String = "",
    val senderUserId: String = "",
    val sdpMid: String = "0",
    val sdpMLineIndex: Int = 0,
    val sdpCandidate: String = "",
    val timestamp: Long = System.currentTimeMillis()
)

object RtcConfiguration {
    val defaultStunServers = listOf(
        "stun:stun.l.google.com:19302",
        "stun:stun1.l.google.com:19302",
        "stun:stun2.l.google.com:19302",
        "stun:stun3.l.google.com:19302",
        "stun:stun4.l.google.com:19302"
    )

    data class TurnServerConfig(
        val url: String,
        val username: String,
        val credential: String,
        val ttlSeconds: Long = 86400L
    )

    /**
     * Resolves STUN/TURN servers securely for ICE negotiation.
     */
    fun getIceServers(turnConfig: TurnServerConfig? = null): List<String> {
        val servers = mutableListOf<String>()
        servers.addAll(defaultStunServers)
        if (turnConfig != null) {
            servers.add("turn:${turnConfig.url}")
        }
        return servers
    }
}
