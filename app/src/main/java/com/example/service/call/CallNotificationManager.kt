package com.example.service.call

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import androidx.core.app.NotificationCompat
import com.example.MainActivity

class CallNotificationManager(private val context: Context) {
    private val notificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    companion object {
        const val CHANNEL_ID = "chatconnect_calls_channel"
        const val NOTIFICATION_ID_INCOMING = 9001
        const val NOTIFICATION_ID_ACTIVE = 9002

        const val ACTION_ACCEPT_CALL = "com.example.ACTION_ACCEPT_CALL"
        const val ACTION_DECLINE_CALL = "com.example.ACTION_DECLINE_CALL"
        const val ACTION_END_CALL = "com.example.ACTION_END_CALL"
    }

    init {
        createNotificationChannel()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "مكالمات ChatConnect"
            val descriptionText = "إشعارات المكالمات الصوتية والمرئية الواردة"
            val importance = NotificationManager.IMPORTANCE_HIGH
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 500, 200, 500)
                val ringtoneUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
                setSound(
                    ringtoneUri,
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    /**
     * Displays a Heads-up notification for an incoming call.
     */
    fun showIncomingCallNotification(
        callId: String,
        callerName: String,
        isVideo: Boolean
    ) {
        val fullScreenIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("CALL_ID", callId)
            putExtra("INCOMING_CALL", true)
        }
        val fullScreenPendingIntent = PendingIntent.getActivity(
            context,
            0,
            fullScreenIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Accept Action Intent
        val acceptIntent = Intent(context, MainActivity::class.java).apply {
            action = ACTION_ACCEPT_CALL
            putExtra("CALL_ID", callId)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val acceptPendingIntent = PendingIntent.getActivity(
            context,
            1,
            acceptIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Decline Action Intent
        val declineIntent = Intent(context, MainActivity::class.java).apply {
            action = ACTION_DECLINE_CALL
            putExtra("CALL_ID", callId)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val declinePendingIntent = PendingIntent.getActivity(
            context,
            2,
            declineIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val callTypeStr = if (isVideo) "مكالمة فيديو واردة" else "مكالمة صوتية واردة"

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_call)
            .setContentTitle(callerName)
            .setContentText(callTypeStr)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setAutoCancel(true)
            .setOngoing(true)
            .setFullScreenIntent(fullScreenPendingIntent, true)
            .setContentIntent(fullScreenPendingIntent)
            .addAction(android.R.drawable.ic_menu_call, "قبول", acceptPendingIntent)
            .addAction(android.R.drawable.ic_delete, "رفض", declinePendingIntent)
            .build()

        try {
            notificationManager.notify(NOTIFICATION_ID_INCOMING, notification)
        } catch (e: Exception) {
            // Permission or system error
        }
    }

    /**
     * Shows notification for an active ongoing call.
     */
    fun showActiveCallNotification(callerName: String, durationText: String) {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            3,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_phone_call)
            .setContentTitle("مكالمة جارية مع $callerName")
            .setContentText("المدة: $durationText • مشفرة DTLS-SRTP")
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .build()

        try {
            notificationManager.notify(NOTIFICATION_ID_ACTIVE, notification)
        } catch (e: Exception) {
            // Log ignored
        }
    }

    fun dismissNotifications() {
        notificationManager.cancel(NOTIFICATION_ID_INCOMING)
        notificationManager.cancel(NOTIFICATION_ID_ACTIVE)
    }
}
