package com.example.utils

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object TimeUtils {
    fun formatTime(timestamp: Long): String {
        val sdf = SimpleDateFormat("hh:mm a", Locale.getDefault())
        return sdf.format(Date(timestamp))
    }

    fun formatDateOrTime(timestamp: Long): String {
        val now = System.currentTimeMillis()
        val diff = now - timestamp
        val oneDayMs = 24 * 60 * 60 * 1000L

        return when {
            diff < oneDayMs -> formatTime(timestamp)
            diff < 2 * oneDayMs -> "Yesterday"
            else -> {
                val sdf = SimpleDateFormat("dd/MM/yy", Locale.getDefault())
                sdf.format(Date(timestamp))
            }
        }
    }

    fun formatDuration(seconds: Int): String {
        val mins = seconds / 60
        val secs = seconds % 60
        return String.format(Locale.US, "%02d:%02d", mins, secs)
    }

    fun formatMsDuration(durationMs: Long): String {
        val totalSecs = (durationMs / 1000).toInt()
        return formatDuration(totalSecs)
    }

    fun formatTimestamp(timestamp: Long): String {
        val sdf = SimpleDateFormat("yyyy/MM/dd hh:mm a", Locale.getDefault())
        return sdf.format(Date(timestamp))
    }

    fun formatLastSeen(timestamp: Long): String {
        val now = System.currentTimeMillis()
        val diffMs = now - timestamp
        val diffMin = diffMs / (60 * 1000L)
        val diffHours = diffMs / (60 * 60 * 1000L)

        return when {
            diffMin < 2 -> if (AppStrings.currentLanguage == "ar") "متصل الآن" else "Active now"
            diffMin < 60 -> if (AppStrings.currentLanguage == "ar") "منذ $diffMin دقيقة" else "$diffMin mins ago"
            diffHours < 24 -> if (AppStrings.currentLanguage == "ar") "اليوم ${formatTime(timestamp)}" else "Today ${formatTime(timestamp)}"
            diffHours < 48 -> if (AppStrings.currentLanguage == "ar") "أمس ${formatTime(timestamp)}" else "Yesterday ${formatTime(timestamp)}"
            else -> {
                val sdf = SimpleDateFormat("yyyy/MM/dd hh:mm a", Locale.getDefault())
                sdf.format(Date(timestamp))
            }
        }
    }
}
