package com.example.service.call

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

/**
 * Utility class managing Android runtime permissions for WebRTC calling (RECORD_AUDIO & CAMERA).
 * Handles checks, rationales, permanently denied scenarios, and runtime revocations during active calls.
 */
object CallPermissionHandler {

    private const val TAG = "CallPermissionHandler"

    const val PERMISSION_AUDIO = Manifest.permission.RECORD_AUDIO
    const val PERMISSION_CAMERA = Manifest.permission.CAMERA

    /**
     * Returns true if audio recording permission is granted.
     */
    fun hasAudioPermission(context: Context): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            PERMISSION_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * Returns true if camera permission is granted.
     */
    fun hasCameraPermission(context: Context): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            PERMISSION_CAMERA
        ) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * Checks if all required permissions for the call type are granted.
     * Voice calls require RECORD_AUDIO.
     * Video calls require RECORD_AUDIO and CAMERA.
     */
    fun hasRequiredPermissions(context: Context, isVideo: Boolean): Boolean {
        val audioOk = hasAudioPermission(context)
        return if (isVideo) {
            audioOk && hasCameraPermission(context)
        } else {
            audioOk
        }
    }

    /**
     * Returns list of permissions required for the given call mode.
     */
    fun getRequiredPermissions(isVideo: Boolean): Array<String> {
        return if (isVideo) {
            arrayOf(PERMISSION_AUDIO, PERMISSION_CAMERA)
        } else {
            arrayOf(PERMISSION_AUDIO)
        }
    }

    /**
     * Returns any permissions that are currently missing.
     */
    fun getMissingPermissions(context: Context, isVideo: Boolean): List<String> {
        val missing = mutableListOf<String>()
        if (!hasAudioPermission(context)) {
            missing.add(PERMISSION_AUDIO)
        }
        if (isVideo && !hasCameraPermission(context)) {
            missing.add(PERMISSION_CAMERA)
        }
        return missing
    }

    /**
     * Determines whether the user should see an educational rationale for why the permission is needed.
     */
    fun shouldShowRationale(activity: Activity, permission: String): Boolean {
        return ActivityCompat.shouldShowRequestPermissionRationale(activity, permission)
    }

    /**
     * Friendly localized Arabic rationale text for permission explanation.
     */
    fun getRationaleMessage(permission: String): String {
        return when (permission) {
            PERMISSION_AUDIO -> "يحتاج التطبيق إلى إذن الميكروفون لتمكين إرسال صوتك بوضوح أثناء المكالمات."
            PERMISSION_CAMERA -> "يحتاج التطبيق إلى إذن الكاميرا لتمكين إرسال صورتك في مكالمات الفيديو."
            else -> "يلزم منح الأذونات المطلوبة لإجراء المكالمة."
        }
    }

    /**
     * Explanation when permissions are permanently denied (direct user to system settings).
     */
    fun getSettingsRedirectMessage(isVideo: Boolean): String {
        return if (isVideo) {
            "تم رفض أذونات الميكروفون أو الكاميرا سابقاً. يرجى تفعيلها من إعدادات التطبيق لإجراء مكالمات الفيديو."
        } else {
            "تم رفض إذن الميكروفون سابقاً. يرجى تفعيله من إعدادات التطبيق لإجراء المكالمات الصوتية."
        }
    }

    /**
     * Opens the application details settings screen so the user can grant permanently denied permissions.
     */
    fun openAppSettings(context: Context) {
        try {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.fromParts("package", context.packageName, null)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to open app settings: ${e.message}")
        }
    }

    /**
     * Detects if any active permission was revoked during an active call session
     * (e.g., via Android quick settings privacy toggles or system settings).
     */
    fun checkPermissionsRevoked(
        context: Context,
        isVideo: Boolean,
        onPermissionRevoked: (revokedPermission: String) -> Unit
    ): Boolean {
        if (!hasAudioPermission(context)) {
            Log.w(TAG, "Audio permission was revoked during active call session!")
            onPermissionRevoked(PERMISSION_AUDIO)
            return true
        }

        if (isVideo && !hasCameraPermission(context)) {
            Log.w(TAG, "Camera permission was revoked during active video call session!")
            onPermissionRevoked(PERMISSION_CAMERA)
            return true
        }

        return false
    }
}
