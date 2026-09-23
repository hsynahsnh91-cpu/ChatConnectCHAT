package com.example.service

import android.content.Context
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.os.Build
import android.media.PlaybackParams
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import java.io.File

class AudioRecorderPlayer(private val context: Context) {

    private var mediaRecorder: MediaRecorder? = null
    private var mediaPlayer: MediaPlayer? = null
    private var currentRecordingFile: File? = null
    private var recordingStartTime = 0L
    private var recordingJob: kotlinx.coroutines.Job? = null
    private var playbackProgressJob: kotlinx.coroutines.Job? = null
    private val scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main + kotlinx.coroutines.SupervisorJob())

    private val _isRecording = MutableStateFlow(false)
    val isRecording: StateFlow<Boolean> = _isRecording

    private val _recordingDurationSeconds = MutableStateFlow(0)
    val recordingDurationSeconds: StateFlow<Int> = _recordingDurationSeconds

    private val _recordingAmplitude = MutableStateFlow(0)
    val recordingAmplitude: StateFlow<Int> = _recordingAmplitude

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying

    private val _playbackProgress = MutableStateFlow(0f)
    val playbackProgress: StateFlow<Float> = _playbackProgress

    private val _playbackPositionMs = MutableStateFlow(0L)
    val playbackPositionMs: StateFlow<Long> = _playbackPositionMs

    private val _totalDurationMs = MutableStateFlow(0L)
    val totalDurationMs: StateFlow<Long> = _totalDurationMs

    private val _currentPlayingUri = MutableStateFlow<String?>(null)
    val currentPlayingUri: StateFlow<String?> = _currentPlayingUri

    private val _playbackSpeed = MutableStateFlow(1.0f)
    val playbackSpeed: StateFlow<Float> = _playbackSpeed

    fun startRecording(): File? {
        try {
            val file = File(context.cacheDir, "voice_record_${System.currentTimeMillis()}.amr")
            mediaRecorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(context)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.AMR_NB)
                setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB)
                setOutputFile(file.absolutePath)
                prepare()
                start()
            }
            currentRecordingFile = file
            recordingStartTime = System.currentTimeMillis()
            _recordingDurationSeconds.value = 0
            _recordingAmplitude.value = 0
            _isRecording.value = true

            recordingJob?.cancel()
            recordingJob = scope.launch {
                while (_isRecording.value) {
                    kotlinx.coroutines.delay(200)
                    val elapsedSec = ((System.currentTimeMillis() - recordingStartTime) / 1000).toInt()
                    _recordingDurationSeconds.value = elapsedSec
                    try {
                        val amp = mediaRecorder?.maxAmplitude ?: 0
                        _recordingAmplitude.value = amp
                    } catch (e: Exception) {
                        // ignore amplitude error
                    }
                }
            }

            return file
        } catch (e: Exception) {
            Log.e("AudioRecorderPlayer", "Error starting recording", e)
            mediaRecorder?.release()
            mediaRecorder = null
            _isRecording.value = false
            return null
        }
    }

    fun stopRecording(): File? {
        recordingJob?.cancel()
        recordingJob = null
        return try {
            mediaRecorder?.apply {
                stop()
                release()
            }
            mediaRecorder = null
            _isRecording.value = false
            currentRecordingFile
        } catch (e: Exception) {
            Log.e("AudioRecorderPlayer", "Error stopping recording", e)
            mediaRecorder?.release()
            mediaRecorder = null
            _isRecording.value = false
            null
        }
    }

    fun cancelRecording() {
        recordingJob?.cancel()
        recordingJob = null
        try {
            mediaRecorder?.apply {
                stop()
                release()
            }
        } catch (e: Exception) {
            // Ignore error on cancel
        } finally {
            mediaRecorder = null
            _isRecording.value = false
            _recordingDurationSeconds.value = 0
            _recordingAmplitude.value = 0
            currentRecordingFile?.delete()
            currentRecordingFile = null
        }
    }

    fun playAudio(uri: String, speed: Float = 1.0f) {
        stopAudio()
        try {
            mediaPlayer = MediaPlayer().apply {
                setDataSource(uri)
                prepare()
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    val params = android.media.PlaybackParams()
                    params.speed = speed
                    playbackParams = params
                }
                start()
                _totalDurationMs.value = duration.toLong()
                _playbackPositionMs.value = 0L
                setOnCompletionListener {
                    playbackProgressJob?.cancel()
                    playbackProgressJob = null
                    _isPlaying.value = false
                    _currentPlayingUri.value = null
                    _playbackProgress.value = 0f
                    _playbackPositionMs.value = 0L
                }
            }
            _currentPlayingUri.value = uri
            _isPlaying.value = true
            _playbackSpeed.value = speed

            playbackProgressJob?.cancel()
            playbackProgressJob = scope.launch {
                while (_isPlaying.value) {
                    kotlinx.coroutines.delay(100)
                    mediaPlayer?.let { player ->
                        if (player.isPlaying) {
                            val current = player.currentPosition
                            val dur = player.duration
                            _playbackPositionMs.value = current.toLong()
                            if (dur > 0) {
                                _playbackProgress.value = current.toFloat() / dur.toFloat()
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("AudioRecorderPlayer", "Error playing audio", e)
            stopAudio()
        }
    }

    fun seekTo(positionMs: Long) {
        mediaPlayer?.seekTo(positionMs.toInt())
        _playbackPositionMs.value = positionMs
    }

    fun setSpeed(speed: Float) {
        _playbackSpeed.value = speed
        mediaPlayer?.let { player ->
            if (player.isPlaying && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val params = player.playbackParams
                params.speed = speed
                player.playbackParams = params
            }
        }
    }

    fun pauseAudio() {
        playbackProgressJob?.cancel()
        playbackProgressJob = null
        mediaPlayer?.let {
            if (it.isPlaying) {
                it.pause()
                _isPlaying.value = false
            }
        }
    }

    fun resumeAudio() {
        mediaPlayer?.let {
            if (!it.isPlaying) {
                it.start()
                _isPlaying.value = true
                playbackProgressJob?.cancel()
                playbackProgressJob = scope.launch {
                    while (_isPlaying.value) {
                        kotlinx.coroutines.delay(100)
                        mediaPlayer?.let { player ->
                            if (player.isPlaying) {
                                val current = player.currentPosition
                                val dur = player.duration
                                _playbackPositionMs.value = current.toLong()
                                if (dur > 0) {
                                    _playbackProgress.value = current.toFloat() / dur.toFloat()
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    fun stopAudio() {
        playbackProgressJob?.cancel()
        playbackProgressJob = null
        mediaPlayer?.apply {
            if (isPlaying) {
                stop()
            }
            release()
        }
        mediaPlayer = null
        _isPlaying.value = false
        _currentPlayingUri.value = null
        _playbackProgress.value = 0f
        _playbackPositionMs.value = 0L
    }

    fun release() {
        cancelRecording()
        stopAudio()
    }
}
