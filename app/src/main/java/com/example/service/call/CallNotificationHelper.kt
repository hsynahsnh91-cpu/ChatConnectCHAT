package com.example.service.call

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.Person
import com.example.MainActivity

/**
 * Helper class responsible for creating and dispatching high-priority heads-up
 * notifications for incoming and active calls that trigger the call screen even
 * when the screen is locked.
 */
class CallNotificationHelper(private val context: Context) {

    private val notificationManager: NotificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    companion object {
        const val HIGH_PRIORITY_CHANNEL_ID = "chatconnect_high_priority_calls"
        const val ONGOING_CALL_CHANNEL_ID = "chatconnect_ongoing_calls"

        const val NOTIFICATION_ID_INCOMING = 9999
        const val NOTIFICATION_ID_ONGOING = 9998

        const val ACTION_ACCEPT_CALL = "com.example.service.ACTION_ACCEPT_CALL"
        const val ACTION_DECLINE_CALL = "com.example.service.ACTION_DECLINE_CALL"
        const val ACTION_END_CALL = "com.example.service.ACTION_END_CALL"
    }

    init {
        createNotificationChannels()
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val ringtoneUri: Uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
            val audioAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()

            // 1. High-Priority Incoming Call Channel
            val incomingChannel = NotificationChannel(
                HIGH_PRIORITY_CHANNEL_ID,
                "إشعارات المكالمات الواردة (أولوية قصوى)",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "إشعار تفاعلي كامل الرؤية يظهر فوق شاشة القفل عند ورود مكالمة"
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 800, 400, 800)
                setSound(ringtoneUri, audioAttributes)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                setShowBadge(true)
                setBypassDnd(true)
            }

            // 2. Ongoing Active Call Channel
            val ongoingChannel = NotificationChannel(
                ONGOING_CALL_CHANNEL_ID,
                "المكالمات الجارية",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "إشعار مستمر يوضح مدة المكالمة الحالية وضوابط التحكم"
                setShowBadge(false)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }

            notificationManager.createNotificationChannel(incomingChannel)
            notificationManager.createNotificationChannel(ongoingChannel)
        }
    }

    /**
     * Builds and displays a high-priority Heads-up notification that wakes and triggers
     * the call screen even when the device is locked.
     */
    fun showIncomingCallNotification(
        callId: String,
        callerName: String,
        isVideo: Boolean
    ): Notification {
        // Full screen Intent targeting MainActivity with lockscreen flags
        val fullScreenIntent = Intent(context, MainActivity::class.java).apply {
            action = Intent.ACTION_MAIN
            addCategory(Intent.CATEGORY_LAUNCHER)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra("CALL_ID", callId)
            putExtra("INCOMING_CALL", true)
        }
        val fullScreenPendingIntent = PendingIntent.getActivity(
            context,
            1001,
            fullScreenIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Accept Action Intent
        val acceptIntent = Intent(context, MainActivity::class.java).apply {
            action = CallNotificationManager.ACTION_ACCEPT_CALL
            putExtra("CALL_ID", callId)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val acceptPendingIntent = PendingIntent.getActivity(
            context,
            1002,
            acceptIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Decline Action Intent
        val declineIntent = Intent(context, CallForegroundService::class.java).apply {
            action = CallForegroundService.ACTION_DECLINE_CALL
            putExtra(CallForegroundService.EXTRA_CALL_ID, callId)
        }
        val declinePendingIntent = PendingIntent.getService(
            context,
            1003,
            declineIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val callerPerson = Person.Builder()
            .setName(callerName)
            .setImportant(true)
            .build()

        val callTypeLabel = if (isVideo) "مكالمة فيديو واردة" else "مكالمة صوتية واردة"

        val style = NotificationCompat.CallStyle.forIncomingCall(
            callerPerson,
            declinePendingIntent,
            acceptPendingIntent
        ).setIsVideo(isVideo)

        val notification = NotificationCompat.Builder(context, HIGH_PRIORITY_CHANNEL_ID)
            .setSmallIcon(if (isVideo) android.R.drawable.presence_video_online else android.R.drawable.ic_menu_call)
            .setContentTitle(callerName)
            .setContentText(callTypeLabel)
            .setStyle(style)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setAutoCancel(false)
            .setFullScreenIntent(fullScreenPendingIntent, true)
            .setContentIntent(fullScreenPendingIntent)
            .build()

        notificationManager.notify(NOTIFICATION_ID_INCOMING, notification)
        return notification
    }

    /**
     * Builds and displays notification for ongoing active calls with live duration.
     */
    fun showOngoingCallNotification(
        callId: String,
        callerName: String,
        durationText: String,
        isVideo: Boolean
    ): Notification {
        val clickIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra("CALL_ID", callId)
        }
        val clickPendingIntent = PendingIntent.getActivity(
            context,
            1004,
            clickIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val hangupIntent = Intent(context, MainActivity::class.java).apply {
            action = CallNotificationManager.ACTION_END_CALL
            putExtra("CALL_ID", callId)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val hangupPendingIntent = PendingIntent.getActivity(
            context,
            1005,
            hangupIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val callerPerson = Person.Builder()
            .setName(callerName)
            .setImportant(true)
            .build()

        val style = NotificationCompat.CallStyle.forOngoingCall(
            callerPerson,
            hangupPendingIntent
        ).setIsVideo(isVideo)

        val notification = NotificationCompat.Builder(context, ONGOING_CALL_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_phone_call)
            .setContentTitle("مكالمة جارية: $callerName")
            .setContentText("المدة: $durationText • مشفرة DTLS-SRTP")
            .setStyle(style)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setContentIntent(clickPendingIntent)
            .build()

        notificationManager.notify(NOTIFICATION_ID_ONGOING, notification)
        return notification
    }

    fun cancelIncomingCallNotification() {
        notificationManager.cancel(NOTIFICATION_ID_INCOMING)
    }

    fun cancelOngoingCallNotification() {
        notificationManager.cancel(NOTIFICATION_ID_ONGOING)
    }

    fun cancelAllCallNotifications() {
        notificationManager.cancel(NOTIFICATION_ID_INCOMING)
        notificationManager.cancel(NOTIFICATION_ID_ONGOING)
    }
}
