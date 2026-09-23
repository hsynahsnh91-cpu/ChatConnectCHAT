package com.example.service.call

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.util.Log

class CallAudioManager(private val context: Context) {
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    private var previousAudioMode: Int = AudioManager.MODE_NORMAL
    private var previousSpeakerState: Boolean = false
    private var previousMicrophoneMuteState: Boolean = false
    private var audioFocusRequest: AudioFocusRequest? = null
    private var isCallActive = false

    companion object {
        private const val TAG = "CallAudioManager"
    }

    /**
     * Prepares the audio hardware for a VoIP communication session:
     * - Requests Voice Communication Audio Focus
     * - Sets Mode to MODE_IN_COMMUNICATION
     * - Configures initial speaker / earpiece routing
     */
    fun startAudioSession(isVideo: Boolean) {
        if (isCallActive) return
        isCallActive = true

        try {
            previousAudioMode = audioManager.mode
            previousSpeakerState = audioManager.isSpeakerphoneOn
            previousMicrophoneMuteState = audioManager.isMicrophoneMute

            // Request Audio Focus
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val playbackAttributes = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()

                val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE)
                    .setAudioAttributes(playbackAttributes)
                    .setAcceptsDelayedFocusGain(false)
                    .setOnAudioFocusChangeListener { focusChange ->
                        when (focusChange) {
                            AudioManager.AUDIOFOCUS_LOSS,
                            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                                Log.w(TAG, "Audio focus lost or interrupted")
                            }
                            AudioManager.AUDIOFOCUS_GAIN -> {
                                Log.d(TAG, "Audio focus gained")
                            }
                        }
                    }
                    .build()

                audioFocusRequest = request
                audioManager.requestAudioFocus(request)
            } else {
                @Suppress("DEPRECATION")
                audioManager.requestAudioFocus(
                    null,
                    AudioManager.STREAM_VOICE_CALL,
                    AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE
                )
            }

            // Set communication mode
            audioManager.mode = AudioManager.MODE_IN_COMMUNICATION

            // Default to speaker for video calls, earpiece for voice calls
            setSpeakerphone(isVideo)
            setMicrophoneMute(false)

            Log.d(TAG, "Audio session initialized. Mode: IN_COMMUNICATION, Speaker: $isVideo")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize call audio session: ${e.message}", e)
        }
    }

    /**
     * Sets speakerphone on or off.
     */
    fun setSpeakerphone(on: Boolean) {
        try {
            audioManager.isSpeakerphoneOn = on
            Log.d(TAG, "Speakerphone set to: $on")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to set speakerphone: ${e.message}")
        }
    }

    fun isSpeakerphoneOn(): Boolean = audioManager.isSpeakerphoneOn

    /**
     * Sets microphone mute state.
     */
    fun setMicrophoneMute(muted: Boolean) {
        try {
            audioManager.isMicrophoneMute = muted
            Log.d(TAG, "Microphone mute set to: $muted")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to set microphone mute: ${e.message}")
        }
    }

    fun isMicrophoneMuted(): Boolean = audioManager.isMicrophoneMute

    /**
     * Enables or disables Bluetooth SCO (headset audio).
     */
    fun setBluetoothSco(enabled: Boolean) {
        try {
            if (enabled) {
                audioManager.startBluetoothSco()
                audioManager.isBluetoothScoOn = true
                Log.d(TAG, "Bluetooth SCO started")
            } else {
                audioManager.stopBluetoothSco()
                audioManager.isBluetoothScoOn = false
                Log.d(TAG, "Bluetooth SCO stopped")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Bluetooth SCO toggle error: ${e.message}")
        }
    }

    /**
     * Switches audio output device between SPEAKER, EARPIECE, and BLUETOOTH.
     */
    fun routeAudioTo(device: AudioOutputDevice) {
        when (device) {
            AudioOutputDevice.SPEAKER -> {
                setBluetoothSco(false)
                setSpeakerphone(true)
            }
            AudioOutputDevice.EARPIECE -> {
                setBluetoothSco(false)
                setSpeakerphone(false)
            }
            AudioOutputDevice.BLUETOOTH -> {
                setSpeakerphone(false)
                setBluetoothSco(true)
            }
        }
    }

    /**
     * Gracefully restores audio hardware back to its original state before the call.
     */
    fun stopAudioSession() {
        if (!isCallActive) return
        isCallActive = false

        try {
            if (audioManager.isBluetoothScoOn) {
                audioManager.stopBluetoothSco()
                audioManager.isBluetoothScoOn = false
            }

            audioManager.isSpeakerphoneOn = previousSpeakerState
            audioManager.isMicrophoneMute = previousMicrophoneMuteState
            audioManager.mode = previousAudioMode

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                audioFocusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
            } else {
                @Suppress("DEPRECATION")
                audioManager.abandonAudioFocus(null)
            }

            Log.d(TAG, "Audio session cleanly terminated and restored.")
        } catch (e: Exception) {
            Log.w(TAG, "Error restoring audio hardware: ${e.message}")
        }
    }
}
