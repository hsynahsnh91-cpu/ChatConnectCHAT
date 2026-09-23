package com.example.service.call

import android.content.Context
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.webrtc.*
import java.util.concurrent.atomic.AtomicBoolean

/**
 * WebRTCManager encapsulates PeerConnection initialization, handles local and remote
 * media tracks, and manages STUN/TURN server configurations for robust NAT traversal.
 */
class WebRTCManager(private val context: Context) {

    companion object {
        private const val TAG = "WebRTCManager"

        /**
         * Production STUN and TURN server configurations for NAT traversal and firewall bypass.
         */
        val DEFAULT_STUN_SERVERS = listOf(
            PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer(),
            PeerConnection.IceServer.builder("stun:stun1.l.google.com:19302").createIceServer(),
            PeerConnection.IceServer.builder("stun:stun2.l.google.com:19302").createIceServer(),
            PeerConnection.IceServer.builder("stun:stun.cloudflare.com:3478").createIceServer()
        )

        val DEFAULT_TURN_SERVERS = listOf(
            PeerConnection.IceServer.builder("turn:openrelay.metered.ca:80")
                .setUsername("openrelayproject")
                .setPassword("openrelayproject")
                .createIceServer(),
            PeerConnection.IceServer.builder("turn:openrelay.metered.ca:443")
                .setUsername("openrelayproject")
                .setPassword("openrelayproject")
                .createIceServer(),
            PeerConnection.IceServer.builder("turns:openrelay.metered.ca:443?transport=tcp")
                .setUsername("openrelayproject")
                .setPassword("openrelayproject")
                .createIceServer()
        )
    }

    private var peerConnectionFactory: PeerConnectionFactory? = null
    private var peerConnection: PeerConnection? = null

    // Local media tracks and senders
    private var localAudioSource: AudioSource? = null
    private var localAudioTrack: AudioTrack? = null
    private var localAudioSender: RtpSender? = null
    private var localVideoSource: VideoSource? = null
    private var localVideoTrack: VideoTrack? = null
    private var localVideoSender: RtpSender? = null
    private var localMediaStream: MediaStream? = null

    // Remote media tracks
    private var remoteAudioTrack: AudioTrack? = null
    private var remoteVideoTrack: VideoTrack? = null
    private val isDisposed = AtomicBoolean(false)

    // State flows
    private val _connectionState = MutableStateFlow(PeerConnection.IceConnectionState.NEW)
    val connectionState: StateFlow<PeerConnection.IceConnectionState> = _connectionState.asStateFlow()

    private val _isAudioEnabled = MutableStateFlow(true)
    val isAudioEnabled: StateFlow<Boolean> = _isAudioEnabled.asStateFlow()

    private val _isVideoEnabled = MutableStateFlow(true)
    val isVideoEnabled: StateFlow<Boolean> = _isVideoEnabled.asStateFlow()

    // Listeners
    var onIceCandidateGenerated: ((IceCandidate) -> Unit)? = null
    var onRemoteStreamAdded: ((MediaStream) -> Unit)? = null
    var onRemoteTrackReceived: ((VideoTrack?, AudioTrack?) -> Unit)? = null
    var onConnectionStateChanged: ((PeerConnection.IceConnectionState) -> Unit)? = null

    init {
        initializePeerConnectionFactory()
    }

    /**
     * Initializes the WebRTC factory with Android hardware acceleration settings.
     */
    private fun initializePeerConnectionFactory() {
        if (peerConnectionFactory != null) return

        try {
            val initOptions = PeerConnectionFactory.InitializationOptions.builder(context.applicationContext)
                .setEnableInternalTracer(true)
                .createInitializationOptions()
            PeerConnectionFactory.initialize(initOptions)

            val options = PeerConnectionFactory.Options()
            peerConnectionFactory = PeerConnectionFactory.builder()
                .setOptions(options)
                .createPeerConnectionFactory()

            Log.i(TAG, "PeerConnectionFactory initialized successfully.")
        } catch (e: Throwable) {
            Log.e(TAG, "Failed to initialize PeerConnectionFactory: ${e.message}", e)
        }
    }

    /**
     * Sets up the PeerConnection instance with STUN and TURN configurations for NAT traversal.
     */
    fun createPeerConnection(
        customIceServers: List<PeerConnection.IceServer>? = null
    ): Boolean {
        val factory = peerConnectionFactory ?: return false

        val iceServers = ArrayList<PeerConnection.IceServer>().apply {
            addAll(customIceServers ?: (DEFAULT_STUN_SERVERS + DEFAULT_TURN_SERVERS))
        }

        val rtcConfig = PeerConnection.RTCConfiguration(iceServers).apply {
            sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
            continualGatheringPolicy = PeerConnection.ContinualGatheringPolicy.GATHER_CONTINUALLY
            iceTransportsType = PeerConnection.IceTransportsType.ALL
            bundlePolicy = PeerConnection.BundlePolicy.MAXBUNDLE
            rtcpMuxPolicy = PeerConnection.RtcpMuxPolicy.REQUIRE
            tcpCandidatePolicy = PeerConnection.TcpCandidatePolicy.ENABLED
        }

        val observer = object : PeerConnection.Observer {
            override fun onSignalingChange(state: PeerConnection.SignalingState?) {
                Log.d(TAG, "WebRTC SignalingState: $state")
            }

            override fun onIceConnectionChange(state: PeerConnection.IceConnectionState?) {
                Log.i(TAG, "WebRTC IceConnectionState: $state")
                state?.let {
                    _connectionState.value = it
                    onConnectionStateChanged?.invoke(it)
                }
            }

            override fun onIceConnectionReceivingChange(receiving: Boolean) {
                Log.d(TAG, "WebRTC IceConnectionReceiving: $receiving")
            }

            override fun onIceGatheringChange(state: PeerConnection.IceGatheringState?) {
                Log.d(TAG, "WebRTC IceGatheringState: $state")
            }

            override fun onIceCandidate(candidate: IceCandidate?) {
                if (candidate != null) {
                    Log.d(TAG, "Generated ICE Candidate: sdpMid=${candidate.sdpMid}, index=${candidate.sdpMLineIndex}")
                    onIceCandidateGenerated?.invoke(candidate)
                }
            }

            override fun onIceCandidatesRemoved(candidates: Array<out IceCandidate>?) {
                Log.d(TAG, "ICE Candidates removed: ${candidates?.size}")
            }

            override fun onAddStream(stream: MediaStream?) {
                Log.i(TAG, "Remote MediaStream added: ${stream?.id}")
                if (stream != null) {
                    if (stream.audioTracks.isNotEmpty()) {
                        remoteAudioTrack = stream.audioTracks[0]
                    }
                    if (stream.videoTracks.isNotEmpty()) {
                        remoteVideoTrack = stream.videoTracks[0]
                    }
                    onRemoteStreamAdded?.invoke(stream)
                    onRemoteTrackReceived?.invoke(remoteVideoTrack, remoteAudioTrack)
                }
            }

            override fun onRemoveStream(stream: MediaStream?) {
                Log.i(TAG, "Remote MediaStream removed: ${stream?.id}")
            }

            override fun onDataChannel(channel: DataChannel?) {
                Log.d(TAG, "DataChannel received: ${channel?.label()}")
            }

            override fun onRenegotiationNeeded() {
                Log.d(TAG, "Renegotiation needed.")
            }

            override fun onAddTrack(receiver: RtpReceiver?, streams: Array<out MediaStream>?) {
                Log.d(TAG, "WebRTC Track added via RtpReceiver: ${receiver?.id()}")
                val track = receiver?.track()
                if (track is VideoTrack) {
                    remoteVideoTrack = track
                } else if (track is AudioTrack) {
                    remoteAudioTrack = track
                }
                onRemoteTrackReceived?.invoke(remoteVideoTrack, remoteAudioTrack)
                streams?.firstOrNull()?.let { stream ->
                    onRemoteStreamAdded?.invoke(stream)
                }
            }
        }

        peerConnection = factory.createPeerConnection(rtcConfig, observer)
        Log.i(TAG, "PeerConnection created with ${iceServers.size} ICE servers for NAT traversal.")
        return peerConnection != null
    }

    /**
     * Initializes local media tracks (Microphone audio and optional camera video).
     */
    fun setupLocalMediaTracks(enableVideo: Boolean) {
        val factory = peerConnectionFactory ?: return

        try {
            // Local Audio Track
            val audioConstraints = MediaConstraints().apply {
                mandatory.add(MediaConstraints.KeyValuePair("googEchoCancellation", "true"))
                mandatory.add(MediaConstraints.KeyValuePair("googAutoGainControl", "true"))
                mandatory.add(MediaConstraints.KeyValuePair("googHighpassFilter", "true"))
                mandatory.add(MediaConstraints.KeyValuePair("googNoiseSuppression", "true"))
            }
            localAudioSource = factory.createAudioSource(audioConstraints)
            localAudioTrack = factory.createAudioTrack("ARDAMSa0", localAudioSource).apply {
                setEnabled(true)
            }

            localMediaStream = factory.createLocalMediaStream("ARDAMS")
            localMediaStream?.addTrack(localAudioTrack)

            // Local Video Track if video call
            if (enableVideo) {
                localVideoSource = factory.createVideoSource(false)
                localVideoTrack = factory.createVideoTrack("ARDAMSv0", localVideoSource).apply {
                    setEnabled(true)
                }
                localMediaStream?.addTrack(localVideoTrack)
            }

            // Attach local media tracks to PeerConnection using Unified Plan addTrack
            val streamIds = listOf("ARDAMS")
            localAudioTrack?.let { track ->
                localAudioSender = peerConnection?.addTrack(track, streamIds)
            }
            if (enableVideo) {
                localVideoTrack?.let { track ->
                    localVideoSender = peerConnection?.addTrack(track, streamIds)
                }
            }

            _isAudioEnabled.value = true
            _isVideoEnabled.value = enableVideo
            Log.i(TAG, "Local media tracks established with Unified Plan (Audio: true, Video: $enableVideo).")
        } catch (e: Throwable) {
            Log.e(TAG, "Failed to setup local media tracks: ${e.message}", e)
        }
    }

    /**
     * Toggles local microphone mute/unmute.
     */
    fun setAudioEnabled(enabled: Boolean) {
        localAudioTrack?.setEnabled(enabled)
        _isAudioEnabled.value = enabled
        Log.d(TAG, "Local audio track enabled: $enabled")
    }

    /**
     * Toggles local camera track on/off.
     */
    fun setVideoEnabled(enabled: Boolean) {
        localVideoTrack?.setEnabled(enabled)
        _isVideoEnabled.value = enabled
        Log.d(TAG, "Local video track enabled: $enabled")
    }

    /**
     * Creates an SDP Offer to initiate peer communication.
     */
    fun createOffer(
        mediaConstraints: MediaConstraints = MediaConstraints(),
        onSuccess: (SessionDescription) -> Unit,
        onError: (String) -> Unit
    ) {
        val pc = peerConnection ?: run {
            onError("PeerConnection is null")
            return
        }

        pc.createOffer(object : SdpObserver {
            override fun onCreateSuccess(desc: SessionDescription?) {
                if (desc != null) {
                    pc.setLocalDescription(object : SdpObserver {
                        override fun onCreateSuccess(p0: SessionDescription?) {}
                        override fun onSetSuccess() {
                            Log.i(TAG, "Local SDP Offer applied successfully.")
                            onSuccess(desc)
                        }
                        override fun onCreateFailure(err: String?) {}
                        override fun onSetFailure(err: String?) {
                            onError("Failed to set local SDP Offer: $err")
                        }
                    }, desc)
                } else {
                    onError("SDP Offer was null")
                }
            }

            override fun onSetSuccess() {}
            override fun onCreateFailure(err: String?) {
                onError("Failed to create SDP Offer: $err")
            }
            override fun onSetFailure(err: String?) {}
        }, mediaConstraints)
    }

    /**
     * Creates an SDP Answer responding to the remote offer.
     */
    fun createAnswer(
        mediaConstraints: MediaConstraints = MediaConstraints(),
        onSuccess: (SessionDescription) -> Unit,
        onError: (String) -> Unit
    ) {
        val pc = peerConnection ?: run {
            onError("PeerConnection is null")
            return
        }

        pc.createAnswer(object : SdpObserver {
            override fun onCreateSuccess(desc: SessionDescription?) {
                if (desc != null) {
                    pc.setLocalDescription(object : SdpObserver {
                        override fun onCreateSuccess(p0: SessionDescription?) {}
                        override fun onSetSuccess() {
                            Log.i(TAG, "Local SDP Answer applied successfully.")
                            onSuccess(desc)
                        }
                        override fun onCreateFailure(err: String?) {}
                        override fun onSetFailure(err: String?) {
                            onError("Failed to set local SDP Answer: $err")
                        }
                    }, desc)
                } else {
                    onError("SDP Answer was null")
                }
            }

            override fun onSetSuccess() {}
            override fun onCreateFailure(err: String?) {
                onError("Failed to create SDP Answer: $err")
            }
            override fun onSetFailure(err: String?) {}
        }, mediaConstraints)
    }

    /**
     * Sets the remote SessionDescription (Offer or Answer).
     */
    fun setRemoteDescription(
        sdp: SessionDescription,
        onSuccess: () -> Unit = {},
        onError: (String) -> Unit = {}
    ) {
        peerConnection?.setRemoteDescription(object : SdpObserver {
            override fun onCreateSuccess(desc: SessionDescription?) {}
            override fun onSetSuccess() {
                Log.i(TAG, "Remote SessionDescription applied (${sdp.type}).")
                onSuccess()
            }
            override fun onCreateFailure(err: String?) {}
            override fun onSetFailure(err: String?) {
                Log.e(TAG, "Failed to apply remote SessionDescription: $err")
                onError(err ?: "Unknown error")
            }
        }, sdp)
    }

    /**
     * Injects a remote ICE candidate discovered through signaling into the PeerConnection.
     */
    fun addIceCandidate(candidate: IceCandidate): Boolean {
        return try {
            val added = peerConnection?.addIceCandidate(candidate) ?: false
            Log.d(TAG, "Added remote ICE candidate: $added (${candidate.sdpMid})")
            added
        } catch (e: Exception) {
            Log.e(TAG, "Failed adding ICE candidate: ${e.message}")
            false
        }
    }

    /**
     * Releases hardware resources and closes the WebRTC PeerConnection.
     */
    fun dispose() {
        if (!isDisposed.compareAndSet(false, true)) {
            Log.d(TAG, "WebRTCManager already disposed, skipping duplicate call.")
            return
        }

        try {
            localAudioSender?.let { sender ->
                try { peerConnection?.removeTrack(sender) } catch (e: Throwable) {}
                try { sender.dispose() } catch (e: Throwable) {}
            }
            localAudioSender = null

            localVideoSender?.let { sender ->
                try { peerConnection?.removeTrack(sender) } catch (e: Throwable) {}
                try { sender.dispose() } catch (e: Throwable) {}
            }
            localVideoSender = null

            try {
                localAudioTrack?.setEnabled(false)
            } catch (e: Throwable) {}
            try {
                localAudioTrack?.dispose()
            } catch (e: Throwable) {}
            try {
                localAudioSource?.dispose()
            } catch (e: Throwable) {}
            localAudioTrack = null
            localAudioSource = null

            try {
                localVideoTrack?.setEnabled(false)
            } catch (e: Throwable) {}
            try {
                localVideoTrack?.dispose()
            } catch (e: Throwable) {}
            try {
                localVideoSource?.dispose()
            } catch (e: Throwable) {}
            localVideoTrack = null
            localVideoSource = null

            try {
                localMediaStream?.dispose()
            } catch (e: Throwable) {}
            localMediaStream = null

            try {
                peerConnection?.close()
            } catch (e: Throwable) {}
            try {
                peerConnection?.dispose()
            } catch (e: Throwable) {}
            peerConnection = null

            try {
                peerConnectionFactory?.dispose()
            } catch (e: Throwable) {}
            peerConnectionFactory = null

            Log.i(TAG, "WebRTCManager resources disposed cleanly.")
        } catch (e: Throwable) {
            Log.e(TAG, "Error disposing WebRTCManager: ${e.message}")
        }
    }
}
