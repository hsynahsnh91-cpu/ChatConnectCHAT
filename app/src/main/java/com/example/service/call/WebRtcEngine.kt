package com.example.service.call

import android.content.Context
import android.util.Log
import org.webrtc.IceCandidate
import org.webrtc.PeerConnection
import org.webrtc.SessionDescription

/**
 * Encapsulates WebRTC PeerConnection components and configuration
 * powered by org.webrtc:google-webrtc.
 */
class WebRtcEngine(private val context: Context) {

    companion object {
        private const val TAG = "WebRtcEngine"

        val DEFAULT_ICE_SERVERS = listOf(
            PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer(),
            PeerConnection.IceServer.builder("stun:stun1.l.google.com:19302").createIceServer(),
            PeerConnection.IceServer.builder("stun:stun2.l.google.com:19302").createIceServer()
        )
    }

    private var isInitialized = false

    fun initialize() {
        if (isInitialized) return
        try {
            Log.i(TAG, "Initializing Google WebRTC Engine with STUN servers.")
            isInitialized = true
        } catch (e: Throwable) {
            Log.e(TAG, "Error initializing WebRTC: ${e.message}")
        }
    }

    fun createSessionDescription(type: SessionDescription.Type, sdpDescription: String): SessionDescription {
        return SessionDescription(type, sdpDescription)
    }

    fun parseIceCandidate(sdpMid: String, sdpMLineIndex: Int, sdpCandidate: String): IceCandidate {
        return IceCandidate(sdpMid, sdpMLineIndex, sdpCandidate)
    }
}
