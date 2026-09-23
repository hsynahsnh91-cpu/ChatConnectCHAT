package com.example.service.call

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner

class CallCameraManager(private val context: Context) {
    private var cameraProvider: ProcessCameraProvider? = null
    private var currentCameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA
    private var currentPreview: Preview? = null
    private var isCameraActive = false

    companion object {
        private const val TAG = "CallCameraManager"
    }

    /**
     * Initializes CameraX and safely binds to the given PreviewView and LifecycleOwner.
     * Guaranteed to never crash or throw unhandled exceptions if hardware or permissions are missing.
     */
    fun startCamera(
        lifecycleOwner: LifecycleOwner,
        previewView: PreviewView,
        useFrontCamera: Boolean,
        onCameraReady: () -> Unit = {},
        onError: (String) -> Unit = {}
    ) {
        val hasPermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED

        if (!hasPermission) {
            Log.w(TAG, "Cannot start camera: CAMERA permission not granted.")
            onError("إذن الكاميرا غير ممنوح.")
            return
        }

        currentCameraSelector = if (useFrontCamera) {
            CameraSelector.DEFAULT_FRONT_CAMERA
        } else {
            CameraSelector.DEFAULT_BACK_CAMERA
        }

        try {
            val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
            cameraProviderFuture.addListener({
                try {
                    cameraProvider = cameraProviderFuture.get()
                    val bound = bindCamera(lifecycleOwner, previewView)
                    if (bound) {
                        isCameraActive = true
                        onCameraReady()
                    } else {
                        onError("لا توجد كاميرا متوافقة على هذا الجهاز.")
                    }
                } catch (e: Throwable) {
                    Log.e(TAG, "Failed to initialize CameraX: ${e.message}", e)
                    onError(e.message ?: "فشل تهيئة الكاميرا")
                }
            }, ContextCompat.getMainExecutor(context))
        } catch (e: Throwable) {
            Log.e(TAG, "ProcessCameraProvider error: ${e.message}", e)
            onError(e.message ?: "خطأ في خدمة الكاميرا")
        }
    }

    private fun bindCamera(lifecycleOwner: LifecycleOwner, previewView: PreviewView): Boolean {
        val provider = cameraProvider ?: return false
        return try {
            provider.unbindAll()

            // Safe camera selector fallback: verify camera exists before binding
            val hasFront = try { provider.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA) } catch (_: Throwable) { false }
            val hasBack = try { provider.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA) } catch (_: Throwable) { false }

            val resolvedSelector = when {
                currentCameraSelector == CameraSelector.DEFAULT_FRONT_CAMERA && hasFront -> CameraSelector.DEFAULT_FRONT_CAMERA
                hasBack -> CameraSelector.DEFAULT_BACK_CAMERA
                hasFront -> CameraSelector.DEFAULT_FRONT_CAMERA
                else -> null
            }

            if (resolvedSelector == null) {
                Log.w(TAG, "No compatible camera found on this device.")
                return false
            }

            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(previewView.surfaceProvider)
            }
            currentPreview = preview

            provider.bindToLifecycle(
                lifecycleOwner,
                resolvedSelector,
                preview
            )
            Log.d(TAG, "Camera bound successfully. Resolved Selector: $resolvedSelector")
            true
        } catch (e: Throwable) {
            Log.e(TAG, "Use case binding failed: ${e.message}", e)
            false
        }
    }

    /**
     * Toggles between front and rear cameras safely.
     */
    fun switchCamera(lifecycleOwner: LifecycleOwner, previewView: PreviewView, toFront: Boolean) {
        currentCameraSelector = if (toFront) {
            CameraSelector.DEFAULT_FRONT_CAMERA
        } else {
            CameraSelector.DEFAULT_BACK_CAMERA
        }
        if (isCameraActive) {
            bindCamera(lifecycleOwner, previewView)
        }
    }

    /**
     * Enables or disables the camera stream cleanly.
     */
    fun setCameraEnabled(enabled: Boolean, lifecycleOwner: LifecycleOwner, previewView: PreviewView) {
        isCameraActive = enabled
        val provider = cameraProvider ?: return
        try {
            if (enabled) {
                bindCamera(lifecycleOwner, previewView)
            } else {
                provider.unbindAll()
                Log.d(TAG, "Camera unbound and disabled.")
            }
        } catch (e: Throwable) {
            Log.w(TAG, "Error setting camera enabled=$enabled: ${e.message}")
        }
    }

    /**
     * Cleans up all CameraX resources and unbinds surfaces.
     */
    fun stopCamera() {
        try {
            cameraProvider?.unbindAll()
            cameraProvider = null
            currentPreview = null
            isCameraActive = false
            Log.d(TAG, "CameraX completely stopped and released.")
        } catch (e: Throwable) {
            Log.w(TAG, "Error stopping CameraX: ${e.message}")
        }
    }
}
