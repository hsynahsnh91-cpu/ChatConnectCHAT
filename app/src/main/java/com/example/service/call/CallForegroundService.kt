package com.example.service.call

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.Ringtone
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.Person
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.service.RealtimeCallDispatcher

class CallForegroundService : Service() {

    private var ringtone: Ringtone? = null
    private var vibrator: Vibrator? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var currentCallId: String = ""
    private var isRinging: Boolean = false
    private lateinit var notificationHelper: CallNotificationHelper

    companion object {
        private const val TAG = "CallForegroundService"
        const val CHANNEL_ID = "chatconnect_high_priority_calls"
        const val NOTIFICATION_ID = 9999

        const val ACTION_START_INCOMING_CALL = "com.example.service.START_INCOMING_CALL"
        const val ACTION_START_ACTIVE_CALL = "com.example.service.START_ACTIVE_CALL"
        const val ACTION_STOP_CALL = "com.example.service.STOP_CALL"
        const val ACTION_DECLINE_CALL = "com.example.service.DECLINE_CALL"

        const val EXTRA_CALL_ID = "EXTRA_CALL_ID"
        const val EXTRA_CALLER_NAME = "EXTRA_CALLER_NAME"
        const val EXTRA_CALLER_AVATAR = "EXTRA_CALLER_AVATAR"
        const val EXTRA_IS_VIDEO = "EXTRA_IS_VIDEO"

        fun startIncomingCall(
            context: Context,
            callId: String,
            callerName: String,
            callerAvatar: String?,
            isVideo: Boolean
        ) {
            val intent = Intent(context, CallForegroundService::class.java).apply {
                action = ACTION_START_INCOMING_CALL
                putExtra(EXTRA_CALL_ID, callId)
                putExtra(EXTRA_CALLER_NAME, callerName)
                putExtra(EXTRA_CALLER_AVATAR, callerAvatar)
                putExtra(EXTRA_IS_VIDEO, isVideo)
            }
            ContextCompat.startForegroundService(context, intent)
        }

        fun startActiveCall(
            context: Context,
            callId: String,
            callerName: String,
            isVideo: Boolean
        ) {
            val intent = Intent(context, CallForegroundService::class.java).apply {
                action = ACTION_START_ACTIVE_CALL
                putExtra(EXTRA_CALL_ID, callId)
                putExtra(EXTRA_CALLER_NAME, callerName)
                putExtra(EXTRA_IS_VIDEO, isVideo)
            }
            ContextCompat.startForegroundService(context, intent)
        }

        fun stopService(context: Context) {
            val intent = Intent(context, CallForegroundService::class.java).apply {
                action = ACTION_STOP_CALL
            }
            context.startService(intent)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        notificationHelper = CallNotificationHelper(applicationContext)
        createNotificationChannel()
        initVibrator()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_INCOMING_CALL -> {
                val callId = intent.getStringExtra(EXTRA_CALL_ID) ?: ""
                val callerName = intent.getStringExtra(EXTRA_CALLER_NAME) ?: "جهة اتصال"
                val isVideo = intent.getBooleanExtra(EXTRA_IS_VIDEO, false)
                currentCallId = callId

                handleIncomingCall(callId, callerName, isVideo)
            }
            ACTION_START_ACTIVE_CALL -> {
                val callId = intent.getStringExtra(EXTRA_CALL_ID) ?: currentCallId
                val callerName = intent.getStringExtra(EXTRA_CALLER_NAME) ?: "جهة اتصال"
                val isVideo = intent.getBooleanExtra(EXTRA_IS_VIDEO, false)

                handleActiveCall(callId, callerName, isVideo)
            }
            ACTION_DECLINE_CALL -> {
                Log.i(TAG, "Decline call triggered from notification action.")
                stopRingingAndVibration()
                RealtimeCallDispatcher.rejectCall()
                RealtimeCallDispatcher.clearCall()
                stopForegroundService()
            }
            ACTION_STOP_CALL -> {
                Log.i(TAG, "Stopping CallForegroundService.")
                stopForegroundService()
            }
            else -> {
                stopForegroundService()
            }
        }
        return START_NOT_STICKY
    }

    private fun handleIncomingCall(callId: String, callerName: String, isVideo: Boolean) {
        acquireWakeLock()
        startRingingAndVibration()

        val notification = notificationHelper.showIncomingCallNotification(callId, callerName, isVideo)
        startServiceInForeground(notification)
    }

    private fun handleActiveCall(callId: String, callerName: String, isVideo: Boolean) {
        stopRingingAndVibration()
        releaseWakeLock()

        val notification = notificationHelper.showOngoingCallNotification(callId, callerName, "متصلة", isVideo)
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(CallNotificationHelper.NOTIFICATION_ID_ONGOING, notification)
    }

    private fun startServiceInForeground(notification: Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val serviceType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_PHONE_CALL
            } else {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_PHONE_CALL
            }
            startForeground(NOTIFICATION_ID, notification, serviceType)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun buildIncomingCallNotification(
        callId: String,
        callerName: String,
        isVideo: Boolean
    ): Notification {
        // Full screen Intent to launch MainActivity and display call interface over lockscreen
        val fullScreenIntent = Intent(this, MainActivity::class.java).apply {
            action = Intent.ACTION_MAIN
            addCategory(Intent.CATEGORY_LAUNCHER)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra("CALL_ID", callId)
            putExtra("INCOMING_CALL", true)
        }
        val fullScreenPendingIntent = PendingIntent.getActivity(
            this,
            101,
            fullScreenIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Accept Action Intent -> Launches MainActivity to answer
        val acceptIntent = Intent(this, MainActivity::class.java).apply {
            action = CallNotificationManager.ACTION_ACCEPT_CALL
            putExtra("CALL_ID", callId)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val acceptPendingIntent = PendingIntent.getActivity(
            this,
            102,
            acceptIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Decline Action Intent -> Direct service action to decline without launching activity
        val declineIntent = Intent(this, CallForegroundService::class.java).apply {
            action = ACTION_DECLINE_CALL
            putExtra(EXTRA_CALL_ID, callId)
        }
        val declinePendingIntent = PendingIntent.getService(
            this,
            103,
            declineIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val caller = Person.Builder()
            .setName(callerName)
            .setImportant(true)
            .build()

        val callTypeTitle = if (isVideo) "مكالمة فيديو واردة" else "مكالمة صوتية واردة"

        val style = NotificationCompat.CallStyle.forIncomingCall(
            caller,
            declinePendingIntent,
            acceptPendingIntent
        ).setIsVideo(isVideo)

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(if (isVideo) android.R.drawable.presence_video_online else android.R.drawable.ic_menu_call)
            .setContentTitle(callerName)
            .setContentText(callTypeTitle)
            .setStyle(style)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setAutoCancel(false)
            .setFullScreenIntent(fullScreenPendingIntent, true)
            .setContentIntent(fullScreenPendingIntent)
            .build()
    }

    private fun buildActiveCallNotification(
        callId: String,
        callerName: String,
        isVideo: Boolean
    ): Notification {
        val clickIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val clickPendingIntent = PendingIntent.getActivity(
            this,
            104,
            clickIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Hang up Action
        val hangupIntent = Intent(this, MainActivity::class.java).apply {
            action = CallNotificationManager.ACTION_END_CALL
            putExtra("CALL_ID", callId)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val hangupPendingIntent = PendingIntent.getActivity(
            this,
            105,
            hangupIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val caller = Person.Builder()
            .setName(callerName)
            .setImportant(true)
            .build()

        val style = NotificationCompat.CallStyle.forOngoingCall(
            caller,
            hangupPendingIntent
        ).setIsVideo(isVideo)

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_phone_call)
            .setContentTitle("مكالمة جارية: $callerName")
            .setContentText("المكالمة متصلة ومشفرة DTLS-SRTP")
            .setStyle(style)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setContentIntent(clickPendingIntent)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "مكالمات ChatConnect ذات الأولوية"
            val descriptionText = "إشعارات المكالمات الواردة ذات الأولوية العالية مع شاشة القفل"
            val importance = NotificationManager.IMPORTANCE_HIGH
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 800, 500, 800)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                val ringtoneUri: Uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
                setSound(
                    ringtoneUri,
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
            }
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun initVibrator() {
        vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
            vibratorManager?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
    }

    @SuppressLint("WakelockTimeout")
    private fun acquireWakeLock() {
        try {
            if (wakeLock == null) {
                val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
                @Suppress("DEPRECATION")
                val flags = PowerManager.SCREEN_BRIGHT_WAKE_LOCK or
                        PowerManager.ACQUIRE_CAUSES_WAKEUP or
                        PowerManager.ON_AFTER_RELEASE
                wakeLock = powerManager.newWakeLock(flags, "ChatConnect:IncomingCallLock")
            }
            if (wakeLock?.isHeld == false) {
                wakeLock?.acquire(35_000L)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error acquiring wake lock: ${e.message}")
        }
    }

    private fun releaseWakeLock() {
        try {
            if (wakeLock?.isHeld == true) {
                wakeLock?.release()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error releasing wake lock: ${e.message}")
        }
    }

    private fun startRingingAndVibration() {
        if (isRinging) return
        isRinging = true

        try {
            val ringtoneUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
            ringtone = RingtoneManager.getRingtone(applicationContext, ringtoneUri).apply {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    isLooping = true
                }
                play()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start ringtone: ${e.message}")
        }

        try {
            val pattern = longArrayOf(0, 800, 600)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator?.vibrate(VibrationEffect.createWaveform(pattern, 0))
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(pattern, 0)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start vibration: ${e.message}")
        }
    }

    private fun stopRingingAndVibration() {
        isRinging = false
        try {
            ringtone?.stop()
            ringtone = null
        } catch (e: Exception) {
            Log.e(TAG, "Failed to stop ringtone: ${e.message}")
        }

        try {
            vibrator?.cancel()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to stop vibrator: ${e.message}")
        }
    }

    private fun stopForegroundService() {
        stopRingingAndVibration()
        releaseWakeLock()
        try {
            notificationHelper.cancelAllCallNotifications()
        } catch (e: Exception) {
            Log.w(TAG, "Error cancelling notifications: ${e.message}")
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
        stopSelf()
    }

    override fun onDestroy() {
        super.onDestroy()
        stopRingingAndVibration()
        releaseWakeLock()
    }
}
