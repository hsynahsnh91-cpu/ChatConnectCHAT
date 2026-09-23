package com.example.service

import android.content.Context
import android.util.Log
import com.example.service.call.AudioOutputDevice
import com.example.service.call.CallAudioManager
import com.example.service.call.CallForegroundService
import com.example.service.call.CallHistoryRecord
import com.example.service.call.CallNetworkMonitor
import com.example.service.call.CallNotificationHelper
import com.example.service.call.CallNotificationManager
import com.example.service.call.CallSignalingRepository
import com.example.service.call.CallSignalingService
import com.example.service.call.CallStateManager
import com.example.service.call.CallSessionState
import com.example.service.call.CallStatusEnum
import com.example.service.call.ICallSignalingRepository
import com.example.service.call.IceCandidateModel
import com.example.service.call.NetworkQualityState
import com.example.service.call.RtcSdpNegotiator
import com.example.service.call.WebRTCManager
import com.example.service.call.WebRtcEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class CallState(
    val callId: String = "",
    val callerUserId: String = "",
    val callerName: String = "",
    val callerAvatar: String? = null,
    val receiverUserId: String = "",
    val receiverName: String = "",
    val isVideo: Boolean = false,
    val status: CallStatus = CallStatus.IDLE,
    val isMuted: Boolean = false,
    val isSpeakerOn: Boolean = false,
    val isCameraOn: Boolean = true,
    val isFrontCamera: Boolean = true,
    val activeAudioDevice: AudioOutputDevice = AudioOutputDevice.EARPIECE,
    val networkQuality: NetworkQualityState = NetworkQualityState.EXCELLENT,
    val durationSec: Int = 0,
    val encryptionFingerprint: String = "4B:7E:99:A1:D8:0C:52:1F:B4:E3:6C:91",
    val isMinimized: Boolean = false
)

enum class CallStatus {
    IDLE,
    OUTGOING,      // Calling/Ringing remote party
    INCOMING,      // Device is ringing for incoming call
    CONNECTING,    // Exchanging SDP & ICE candidates
    CONNECTED,     // Live call session active, media streaming
    RECONNECTING,  // Handover / Network recovery
    REJECTED,      // Remote party declined
    ENDED,         // Terminated normally
    MISSED,        // Timed out without answer
    BUSY,          // Target line is busy
    FAILED         // Irrecoverable network or signaling failure
}

object RealtimeCallDispatcher {
    private const val TAG = "RealtimeCallDispatcher"
    private val scope = CoroutineScope(Dispatchers.Main)

    private var audioManager: CallAudioManager? = null
    private var networkMonitor: CallNetworkMonitor? = null
    private var notificationManager: CallNotificationManager? = null
    private var notificationHelper: CallNotificationHelper? = null
    private var rtcEngine: WebRtcEngine? = null
    private var webRtcManager: WebRTCManager? = null
    private var appContext: Context? = null
    private val signalingService = CallSignalingService()
    private val firestoreService = FirestoreService()
    val signalingRepository: ICallSignalingRepository = CallSignalingRepository()

    private var durationJob: Job? = null
    private var timeoutJob: Job? = null
    private var remoteSessionJob: Job? = null

    private val _currentCallState = MutableStateFlow(CallState())
    val currentCallState: StateFlow<CallState> = _currentCallState.asStateFlow()

    private val _isMinimized = MutableStateFlow(false)
    val isMinimized: StateFlow<Boolean> = _isMinimized.asStateFlow()

    var onCallEndedListener: ((callId: String, callerUserId: String, receiverUserId: String, isVideo: Boolean, finalStatus: String, durationSec: Int) -> Unit)? = null

    fun initialize(context: Context) {
        if (appContext == null) {
            appContext = context.applicationContext
            audioManager = CallAudioManager(context.applicationContext)
            notificationManager = CallNotificationManager(context.applicationContext)
            notificationHelper = CallNotificationHelper(context.applicationContext)
            rtcEngine = WebRtcEngine(context.applicationContext).apply { initialize() }
            webRtcManager = WebRTCManager(context.applicationContext)
            networkMonitor = CallNetworkMonitor(
                context = context.applicationContext,
                onNetworkQualityChanged = { quality ->
                    _currentCallState.value = _currentCallState.value.copy(networkQuality = quality)
                },
                onNetworkInterruption = {
                    val curr = _currentCallState.value
                    if (curr.status == CallStatus.CONNECTED) {
                        Log.w(TAG, "Network interrupted during call. Entering RECONNECTING state.")
                        _currentCallState.value = curr.copy(status = CallStatus.RECONNECTING)
                    }
                },
                onNetworkRestored = {
                    val curr = _currentCallState.value
                    if (curr.status == CallStatus.RECONNECTING) {
                        Log.i(TAG, "Network restored. Recovering call session to CONNECTED.")
                        _currentCallState.value = curr.copy(status = CallStatus.CONNECTED)
                    }
                }
            )
        }
    }

    /**
     * Initiates a real outgoing VoIP call session.
     */
    fun initiateCall(
        callId: String,
        callerUserId: String,
        callerName: String,
        callerAvatar: String?,
        receiverUserId: String,
        receiverName: String = "مستخدم",
        isVideo: Boolean
    ) {
        cleanupActiveJobs()
        CallStateManager.transitionTo(CallSessionState.CALLING, callId = callId, reason = "Outgoing call initiated")

        val fingerprint = RtcSdpNegotiator.generateDtlsFingerprint(callId)
        val sdpOffer = RtcSdpNegotiator.createOfferSdp(callId, callerUserId, isVideo, fingerprint)

        val initialDevice = if (isVideo) AudioOutputDevice.SPEAKER else AudioOutputDevice.EARPIECE
        _currentCallState.value = CallState(
            callId = callId,
            callerUserId = callerUserId,
            callerName = callerName,
            callerAvatar = callerAvatar,
            receiverUserId = receiverUserId,
            receiverName = receiverName,
            isVideo = isVideo,
            status = CallStatus.OUTGOING,
            isSpeakerOn = isVideo,
            activeAudioDevice = initialDevice,
            encryptionFingerprint = fingerprint
        )

        // Initialize hardware audio session & network monitoring
        audioManager?.startAudioSession(isVideo)
        audioManager?.routeAudioTo(initialDevice)
        networkMonitor?.startMonitoring()

        // Initialize WebRTC PeerConnection and local media tracks
        webRtcManager?.createPeerConnection()
        webRtcManager?.setupLocalMediaTracks(isVideo)
        webRtcManager?.onIceCandidateGenerated = { cand ->
            scope.launch {
                signalingRepository.sendIceCandidate(
                    IceCandidateModel(
                        callId = callId,
                        senderUserId = callerUserId,
                        sdpMid = cand.sdpMid,
                        sdpMLineIndex = cand.sdpMLineIndex,
                        sdpCandidate = cand.sdp
                    )
                )
            }
        }

        // Create the session in Cloud Signaling (Firestore)
        scope.launch {
            signalingService.createCallSession(
                callId = callId,
                callerUserId = callerUserId,
                callerName = callerName,
                callerAvatar = callerAvatar,
                receiverUserId = receiverUserId,
                receiverName = receiverName,
                isVideo = isVideo,
                sdpOffer = sdpOffer,
                securityFingerprint = fingerprint
            )
        }

        // Start 35-second ringing timeout
        timeoutJob = scope.launch {
            delay(35_000L)
            val curr = _currentCallState.value
            if (curr.status == CallStatus.OUTGOING) {
                Log.i(TAG, "Call timed out with no answer from receiver.")
                _currentCallState.value = curr.copy(status = CallStatus.MISSED)
                terminateSession(CallStatus.MISSED, "NO_ANSWER")
            }
        }

        // Observe remote session state in Firestore
        remoteSessionJob = scope.launch {
            signalingService.observeCallSession(callId).collect { remoteSession ->
                if (remoteSession == null) return@collect

                when (remoteSession.status) {
                    CallStatusEnum.RINGING -> {
                        CallStateManager.transitionTo(CallSessionState.RINGING, callId = callId, reason = "Peer device is ringing")
                    }
                    CallStatusEnum.CONNECTED -> {
                        timeoutJob?.cancel()
                        if (_currentCallState.value.status != CallStatus.CONNECTED) {
                            Log.i(TAG, "Remote answered call. Establishing DTLS-SRTP media stream.")
                            CallStateManager.transitionTo(CallSessionState.CONNECTING, callId = callId, reason = "Peer answered call")
                            CallStateManager.transitionTo(CallSessionState.CONNECTED, callId = callId, reason = "Media stream connected")
                            _currentCallState.value = _currentCallState.value.copy(status = CallStatus.CONNECTED)
                            startDurationTimer()
                        }
                    }
                    CallStatusEnum.DECLINED -> {
                        timeoutJob?.cancel()
                        Log.i(TAG, "Call was declined by receiver.")
                        _currentCallState.value = _currentCallState.value.copy(status = CallStatus.REJECTED)
                        terminateSession(CallStatus.REJECTED, "DECLINED_BY_RECEIVER")
                    }
                    CallStatusEnum.BUSY -> {
                        timeoutJob?.cancel()
                        Log.i(TAG, "Receiver line is busy.")
                        _currentCallState.value = _currentCallState.value.copy(status = CallStatus.BUSY)
                        terminateSession(CallStatus.BUSY, "RECEIVER_BUSY")
                    }
                    CallStatusEnum.ENDED -> {
                        timeoutJob?.cancel()
                        Log.i(TAG, "Remote ended the call.")
                        _currentCallState.value = _currentCallState.value.copy(status = CallStatus.ENDED)
                        terminateSession(CallStatus.ENDED, "ENDED_BY_REMOTE")
                    }
                    CallStatusEnum.FAILED -> {
                        timeoutJob?.cancel()
                        _currentCallState.value = _currentCallState.value.copy(status = CallStatus.FAILED)
                        terminateSession(CallStatus.FAILED, "FAILED")
                    }
                    else -> {}
                }
            }
        }
    }

    /**
     * Receives an incoming call and triggers ringing & notification.
     */
    fun receiveCall(
        callId: String,
        callerUserId: String,
        callerName: String,
        callerAvatar: String?,
        receiverUserId: String,
        isVideo: Boolean
    ) {
        cleanupActiveJobs()

        val fingerprint = RtcSdpNegotiator.generateDtlsFingerprint(callId)
        val initialDevice = if (isVideo) AudioOutputDevice.SPEAKER else AudioOutputDevice.EARPIECE

        CallStateManager.transitionTo(CallSessionState.RINGING, callId = callId, reason = "Incoming call alert")

        _currentCallState.value = CallState(
            callId = callId,
            callerUserId = callerUserId,
            callerName = callerName,
            callerAvatar = callerAvatar,
            receiverUserId = receiverUserId,
            isVideo = isVideo,
            status = CallStatus.INCOMING,
            isSpeakerOn = isVideo,
            activeAudioDevice = initialDevice,
            encryptionFingerprint = fingerprint
        )

        // Show Incoming Call Notification and start Foreground Service for high priority ringing
        appContext?.let { ctx ->
            CallForegroundService.startIncomingCall(
                context = ctx,
                callId = callId,
                callerName = callerName,
                callerAvatar = callerAvatar,
                isVideo = isVideo
            )
        }
        notificationManager?.showIncomingCallNotification(callId, callerName, isVideo)

        // 35s incoming ring timeout
        timeoutJob = scope.launch {
            delay(35_000L)
            val curr = _currentCallState.value
            if (curr.status == CallStatus.INCOMING) {
                Log.i(TAG, "Incoming call timed out.")
                _currentCallState.value = curr.copy(status = CallStatus.MISSED)
                terminateSession(CallStatus.MISSED, "TIMEOUT")
            }
        }

        // Observe caller cancellation
        remoteSessionJob = scope.launch {
            signalingService.observeCallSession(callId).collect { remote ->
                if (remote != null && remote.status == CallStatusEnum.ENDED) {
                    Log.i(TAG, "Caller canceled before answer.")
                    terminateSession(CallStatus.MISSED, "CANCELED_BY_CALLER")
                }
            }
        }
    }

    /**
     * Answers an incoming call.
     */
    fun acceptCall() {
        timeoutJob?.cancel()
        val curr = _currentCallState.value
        if (curr.status != CallStatus.INCOMING) return

        notificationManager?.dismissNotifications()

        // Start hardware audio session & network monitor
        audioManager?.startAudioSession(curr.isVideo)
        audioManager?.routeAudioTo(curr.activeAudioDevice)
        networkMonitor?.startMonitoring()

        // Setup WebRTC PeerConnection for answering party
        CallStateManager.transitionTo(CallSessionState.CONNECTING, callId = curr.callId, reason = "Answering call")
        webRtcManager?.createPeerConnection()
        webRtcManager?.setupLocalMediaTracks(curr.isVideo)
        webRtcManager?.onIceCandidateGenerated = { cand ->
            scope.launch {
                signalingRepository.sendIceCandidate(
                    IceCandidateModel(
                        callId = curr.callId,
                        senderUserId = curr.receiverUserId,
                        sdpMid = cand.sdpMid,
                        sdpMLineIndex = cand.sdpMLineIndex,
                        sdpCandidate = cand.sdp
                    )
                )
            }
        }

        val fingerprint = RtcSdpNegotiator.generateDtlsFingerprint(curr.callId)
        val sdpAnswer = RtcSdpNegotiator.createAnswerSdp(curr.callId, curr.receiverUserId, curr.isVideo, fingerprint)

        CallStateManager.transitionTo(CallSessionState.CONNECTED, callId = curr.callId, reason = "Media stream connected")
        _currentCallState.value = curr.copy(
            status = CallStatus.CONNECTED,
            encryptionFingerprint = fingerprint
        )

        // Upload SDP Answer and transition status in Firestore
        scope.launch {
            signalingService.answerCallSession(curr.callId, sdpAnswer)
        }

        appContext?.let { ctx ->
            CallForegroundService.startActiveCall(
                context = ctx,
                callId = curr.callId,
                callerName = curr.callerName,
                isVideo = curr.isVideo
            )
        }

        startDurationTimer()
    }

    /**
     * Rejects an incoming call.
     */
    fun rejectCall() {
        timeoutJob?.cancel()
        val curr = _currentCallState.value
        _currentCallState.value = curr.copy(status = CallStatus.REJECTED)

        scope.launch {
            signalingService.endCallSession(curr.callId, CallStatusEnum.DECLINED, "DECLINED", curr.durationSec)
        }
        terminateSession(CallStatus.REJECTED, "DECLINED")
    }

    /**
     * Ends an ongoing active call.
     */
    fun endCall() {
        timeoutJob?.cancel()
        val curr = _currentCallState.value
        _currentCallState.value = curr.copy(status = CallStatus.ENDED)

        scope.launch {
            signalingService.endCallSession(curr.callId, CallStatusEnum.ENDED, "NORMAL", curr.durationSec)
        }
        terminateSession(CallStatus.ENDED, "NORMAL")
    }

    private fun terminateSession(finalStatus: CallStatus, reason: String) {
        cleanupActiveJobs()
        val curr = _currentCallState.value

        audioManager?.stopAudioSession()
        networkMonitor?.stopMonitoring()
        notificationManager?.dismissNotifications()
        notificationHelper?.cancelAllCallNotifications()
        webRtcManager?.dispose()
        appContext?.let { ctx ->
            CallForegroundService.stopService(ctx)
        }

        // Notify repository to log into Room database
        if (curr.callId.isNotBlank()) {
            val statusStr = when (finalStatus) {
                CallStatus.REJECTED -> "REJECTED"
                CallStatus.MISSED -> "MISSED"
                CallStatus.BUSY -> "BUSY"
                CallStatus.FAILED -> "FAILED"
                else -> if (curr.durationSec > 0) "ENDED" else "MISSED"
            }

            CallStateManager.transitionTo(CallSessionState.ENDED, callId = curr.callId, reason = reason)
            CallStateManager.reset()

            onCallEndedListener?.invoke(
                curr.callId,
                curr.callerUserId,
                curr.receiverUserId,
                curr.isVideo,
                statusStr,
                curr.durationSec
            )

            // Persist to Cloud Firestore 'call_history' collection schema
            scope.launch {
                firestoreService.saveCallHistoryRecord(
                    CallHistoryRecord(
                        callId = curr.callId,
                        callerUserId = curr.callerUserId,
                        callerName = curr.callerName,
                        callerAvatar = curr.callerAvatar,
                        receiverUserId = curr.receiverUserId,
                        receiverName = curr.receiverName,
                        receiverAvatar = null,
                        participants = listOf(curr.callerUserId, curr.receiverUserId).filter { it.isNotBlank() },
                        isVideo = curr.isVideo,
                        status = statusStr,
                        durationSec = curr.durationSec,
                        timestamp = System.currentTimeMillis()
                    )
                )
            }
        } else {
            CallStateManager.reset()
        }
    }

    fun clearCall() {
        cleanupActiveJobs()
        _isMinimized.value = false
        _currentCallState.value = CallState()
    }

    private fun startDurationTimer() {
        durationJob?.cancel()
        durationJob = scope.launch {
            var sec = 0
            while (true) {
                delay(1000L)
                sec++
                _currentCallState.value = _currentCallState.value.copy(durationSec = sec)
                if (_isMinimized.value) {
                    notificationManager?.showActiveCallNotification(
                        _currentCallState.value.callerName,
                        formatSec(sec)
                    )
                }
            }
        }
    }

    private fun formatSec(sec: Int): String {
        val m = sec / 60
        val s = sec % 60
        return String.format("%02d:%02d", m, s)
    }

    private fun cleanupActiveJobs() {
        durationJob?.cancel()
        durationJob = null
        timeoutJob?.cancel()
        timeoutJob = null
        remoteSessionJob?.cancel()
        remoteSessionJob = null
    }

    // --- Audio and Media Controls ---

    fun toggleMute() {
        val curr = _currentCallState.value
        val newMuted = !curr.isMuted
        audioManager?.setMicrophoneMute(newMuted)
        _currentCallState.value = curr.copy(isMuted = newMuted)
    }

    fun toggleSpeaker() {
        val curr = _currentCallState.value
        val newDevice = if (curr.activeAudioDevice == AudioOutputDevice.SPEAKER) {
            AudioOutputDevice.EARPIECE
        } else {
            AudioOutputDevice.SPEAKER
        }
        setAudioOutputDevice(newDevice)
    }

    fun setAudioOutputDevice(device: AudioOutputDevice) {
        audioManager?.routeAudioTo(device)
        val isSpeaker = device == AudioOutputDevice.SPEAKER
        _currentCallState.value = _currentCallState.value.copy(
            activeAudioDevice = device,
            isSpeakerOn = isSpeaker
        )
    }

    fun toggleCamera() {
        val curr = _currentCallState.value
        _currentCallState.value = curr.copy(isCameraOn = !curr.isCameraOn)
    }

    fun flipCamera() {
        val curr = _currentCallState.value
        _currentCallState.value = curr.copy(isFrontCamera = !curr.isFrontCamera)
    }

    fun minimizeCall() {
        _isMinimized.value = true
        _currentCallState.value = _currentCallState.value.copy(isMinimized = true)
        val curr = _currentCallState.value
        notificationManager?.showActiveCallNotification(curr.callerName, formatSec(curr.durationSec))
    }

    fun restoreCall() {
        _isMinimized.value = false
        _currentCallState.value = _currentCallState.value.copy(isMinimized = false)
        notificationManager?.dismissNotifications()
    }

    fun updateDuration(seconds: Int) {
        val curr = _currentCallState.value
        if (curr.status == CallStatus.CONNECTED) {
            _currentCallState.value = curr.copy(durationSec = seconds)
        }
    }
}
