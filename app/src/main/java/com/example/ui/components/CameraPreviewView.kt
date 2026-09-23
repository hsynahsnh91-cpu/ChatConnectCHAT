package com.example.ui.components

import android.view.ViewGroup
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.VideocamOff
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.service.call.CallCameraManager

@Composable
fun CameraPreviewView(
    cameraManager: CallCameraManager,
    isFrontCamera: Boolean,
    isCameraOn: Boolean,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var cameraError by remember { mutableStateOf<String?>(null) }

    val previewView = remember {
        PreviewView(context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        }
    }

    DisposableEffect(lifecycleOwner, isFrontCamera, isCameraOn) {
        if (isCameraOn) {
            cameraError = null
            cameraManager.startCamera(
                lifecycleOwner = lifecycleOwner,
                previewView = previewView,
                useFrontCamera = isFrontCamera,
                onCameraReady = {
                    cameraError = null
                },
                onError = { err ->
                    cameraError = err
                }
            )
        } else {
            cameraManager.setCameraEnabled(false, lifecycleOwner, previewView)
        }

        onDispose {
            cameraManager.stopCamera()
        }
    }

    Box(modifier = modifier) {
        if (isCameraOn && cameraError == null) {
            AndroidView(
                factory = { previewView },
                modifier = Modifier.fillMaxSize()
            )
        } else if (cameraError != null) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFF0F172A))
                    .padding(8.dp)
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.VideocamOff,
                        contentDescription = "Camera Error",
                        tint = Color(0xFFEF4444),
                        modifier = Modifier.size(28.dp)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "الكاميرا غير متاحة",
                        color = Color.White.copy(alpha = 0.8f),
                        fontSize = 11.sp
                    )
                }
            }
        } else {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFF1E293B))
            ) {
                Icon(
                    imageVector = Icons.Default.VideocamOff,
                    contentDescription = "Camera Disabled",
                    tint = Color.White.copy(alpha = 0.6f),
                    modifier = Modifier.size(36.dp)
                )
            }
        }
    }
}
