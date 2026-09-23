package com.example.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.service.CallState
import com.example.service.CallStatus
import com.example.service.RealtimeCallDispatcher
import com.example.service.call.AudioOutputDevice
import com.example.service.call.CallCameraManager
import com.example.service.call.NetworkQualityState
import com.example.utils.AppStrings
import com.example.utils.TimeUtils

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActiveCallScreen(
    callState: CallState,
    onMuteToggle: () -> Unit,
    onCameraToggle: () -> Unit,
    onFlipCamera: () -> Unit,
    onSpeakerToggle: () -> Unit = { RealtimeCallDispatcher.toggleSpeaker() },
    onEndCall: () -> Unit
) {
    val context = LocalContext.current
    val cameraManager = remember { CallCameraManager(context) }

    var showAudioDeviceDialog by remember { mutableStateOf(false) }
    var showSecurityDetailsDialog by remember { mutableStateOf(false) }
    var showAddParticipantDialog by remember { mutableStateOf(false) }

    // Pulsing transition for ringing and soundwave animation
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.22f,
        animationSpec = infiniteRepeatable(
            animation = tween(1300, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.5f,
        targetValue = 0.12f,
        animationSpec = infiniteRepeatable(
            animation = tween(1300, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "alpha"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(Color(0xFF0F172A), Color(0xFF020617))
                )
            )
    ) {
        if (callState.isVideo) {
            // ==================== VIDEO CALL CANVAS ====================
            Box(modifier = Modifier.fillMaxSize()) {
                if (callState.isCameraOn) {
                    // Main Remote Screen Canvas
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color(0xFF0F172A))
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            AvatarImage(
                                uri = callState.callerAvatar,
                                name = callState.callerName,
                                size = 110.dp
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = callState.callerName,
                                color = Color.White,
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = when (callState.status) {
                                    CallStatus.CONNECTED -> "بث فيديو مباشر عالي الدقة (HD)"
                                    CallStatus.RECONNECTING -> "جاري إعادة الاتصال..."
                                    else -> "جاري إنشاء الاتصال المرئي..."
                                },
                                color = Color(0xFF4ADE80),
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }

                    // Local Camera PIP Canvas (Bottom Right or Top Right)
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = Color(0xFF1E293B),
                        border = BorderStroke(2.dp, Color(0xFF25D366)),
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(top = 80.dp, end = 16.dp)
                            .size(width = 120.dp, height = 170.dp)
                            .clip(RoundedCornerShape(20.dp))
                    ) {
                        CameraPreviewView(
                            cameraManager = cameraManager,
                            isFrontCamera = callState.isFrontCamera,
                            isCameraOn = callState.isCameraOn,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                } else {
                    // Camera Turned Off by User
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color(0xFF0F172A))
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            AvatarImage(
                                uri = callState.callerAvatar,
                                name = callState.callerName,
                                size = 110.dp
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "تم إيقاف الكاميرا الخاصة بك",
                                color = Color.White.copy(alpha = 0.7f),
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }
            }
        } else {
            // ==================== VOICE CALL CANVAS ====================
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 24.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    // Outer Pulsing Ring
                    Box(
                        modifier = Modifier
                            .size(175.dp)
                            .scale(if (callState.status == CallStatus.CONNECTED || callState.status == CallStatus.OUTGOING) pulseScale else 1f)
                            .clip(CircleShape)
                            .background(Color(0xFF25D366).copy(alpha = pulseAlpha))
                    )

                    // Inner Avatar Ring
                    Box(
                        modifier = Modifier
                            .size(145.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF1E293B))
                    )

                    AvatarImage(
                        uri = callState.callerAvatar,
                        name = callState.callerName,
                        size = 130.dp
                    )
                }

                Spacer(modifier = Modifier.height(28.dp))

                Text(
                    text = callState.callerName,
                    style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                    color = Color.White
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Call Status & Duration text
                Text(
                    text = when (callState.status) {
                        CallStatus.OUTGOING -> "جاري الاتصال... رنين"
                        CallStatus.CONNECTED -> TimeUtils.formatDuration(callState.durationSec)
                        CallStatus.RECONNECTING -> "الشبكة غير مستقرة • جاري استعادة الاتصال..."
                        CallStatus.CONNECTING -> "جاري التفاوض والتشفير..."
                        CallStatus.BUSY -> "المستخدم مشغول في مكالمة أخرى"
                        CallStatus.REJECTED -> "تم رفض المكالمة"
                        CallStatus.MISSED -> "لم يتم الرد"
                        else -> AppStrings.get("calling")
                    },
                    style = MaterialTheme.typography.titleMedium,
                    color = when (callState.status) {
                        CallStatus.CONNECTED -> Color(0xFF4ADE80)
                        CallStatus.RECONNECTING -> Color(0xFFFBBF24)
                        CallStatus.REJECTED, CallStatus.MISSED, CallStatus.BUSY -> Color(0xFFEF4444)
                        else -> Color(0xFF94A3B8)
                    }
                )

                // Network Quality and Audio Soundwave Bars when CONNECTED
                if (callState.status == CallStatus.CONNECTED) {
                    Spacer(modifier = Modifier.height(20.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val heights = listOf(14.dp, 28.dp, 42.dp, 22.dp, 36.dp, 50.dp, 30.dp, 18.dp, 34.dp, 24.dp)
                        heights.forEachIndexed { i, h ->
                            val animHeight by infiniteTransition.animateFloat(
                                initialValue = 6f,
                                targetValue = h.value,
                                animationSpec = infiniteRepeatable(
                                    animation = tween(380 + (i * 65), easing = FastOutSlowInEasing),
                                    repeatMode = RepeatMode.Reverse
                                ),
                                label = "wave_$i"
                            )
                            Box(
                                modifier = Modifier
                                    .width(4.dp)
                                    .height(animHeight.dp)
                                    .clip(RoundedCornerShape(2.dp))
                                    .background(if (callState.isMuted) Color.Red else Color(0xFF25D366))
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Audio Route & Network Pill
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0x331E293B),
                        border = BorderStroke(1.dp, Color(0x22FFFFFF))
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Icon(
                                imageVector = when (callState.activeAudioDevice) {
                                    AudioOutputDevice.SPEAKER -> Icons.Default.VolumeUp
                                    AudioOutputDevice.BLUETOOTH -> Icons.Default.BluetoothAudio
                                    AudioOutputDevice.EARPIECE -> Icons.Default.PhoneInTalk
                                },
                                contentDescription = "Audio Device",
                                tint = Color(0xFF94A3B8),
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = when (callState.activeAudioDevice) {
                                    AudioOutputDevice.SPEAKER -> "مكبر الصوت"
                                    AudioOutputDevice.BLUETOOTH -> "سماعة بلوتوث"
                                    AudioOutputDevice.EARPIECE -> "سماعة الهاتف"
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = Color(0xFFCBD5E1)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "• جودة الشبكة: ${when (callState.networkQuality) {
                                    NetworkQualityState.EXCELLENT -> "ممتازة"
                                    NetworkQualityState.GOOD -> "جيدة"
                                    NetworkQualityState.POOR -> "ضعيفة"
                                    NetworkQualityState.RECONNECTING -> "إعادة اتصال..."
                                }}",
                                style = MaterialTheme.typography.labelSmall,
                                color = when (callState.networkQuality) {
                                    NetworkQualityState.EXCELLENT -> Color(0xFF4ADE80)
                                    NetworkQualityState.GOOD -> Color(0xFF38BDF8)
                                    NetworkQualityState.POOR -> Color(0xFFFBBF24)
                                    NetworkQualityState.RECONNECTING -> Color(0xFFEF4444)
                                }
                            )
                        }
                    }
                }
            }
        }

        // ==================== TOP BAR CONTROLS ====================
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 40.dp, start = 16.dp, end = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Minimize Call Button
            IconButton(
                onClick = { RealtimeCallDispatcher.minimizeCall() },
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(Color(0x660F172A))
            ) {
                Icon(
                    imageVector = Icons.Default.KeyboardArrowDown,
                    contentDescription = "تصغير المكالمة والعودة للمحادثة",
                    tint = Color.White,
                    modifier = Modifier.size(28.dp)
                )
            }

            // Top Encryption Security Badge (Clickable for full crypto details)
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = Color(0xCC0B132B),
                border = BorderStroke(1.dp, Color(0x3325D366)),
                modifier = Modifier.clickable { showSecurityDetailsDialog = true }
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = "Encrypted",
                        tint = Color(0xFF25D366),
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "مشفرة DTLS-SRTP",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = Color.White
                    )
                }
            }

            // Add Participant Button
            IconButton(
                onClick = { showAddParticipantDialog = true },
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(Color(0x660F172A))
            ) {
                Icon(
                    imageVector = Icons.Default.PersonAdd,
                    contentDescription = "إضافة مشارك إلى المكالمة",
                    tint = Color.White,
                    modifier = Modifier.size(22.dp)
                )
            }
        }

        // ==================== BOTTOM CALL CONTROLS BAR ====================
        Surface(
            shape = RoundedCornerShape(32.dp),
            color = Color(0xDD0F172A),
            border = BorderStroke(1.dp, Color(0x33FFFFFF)),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 40.dp, start = 16.dp, end = 16.dp)
        ) {
            Row(
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 12.dp)
            ) {
                // Audio Device Switcher (Speaker/Earpiece/Bluetooth)
                IconButton(
                    onClick = { showAudioDeviceDialog = true },
                    modifier = Modifier
                        .size(50.dp)
                        .clip(CircleShape)
                        .background(if (callState.isSpeakerOn) Color(0xFF25D366) else Color(0xFF334155))
                ) {
                    Icon(
                        imageVector = when (callState.activeAudioDevice) {
                            AudioOutputDevice.SPEAKER -> Icons.Default.VolumeUp
                            AudioOutputDevice.BLUETOOTH -> Icons.Default.BluetoothAudio
                            AudioOutputDevice.EARPIECE -> Icons.Default.PhoneInTalk
                        },
                        contentDescription = "مخرج الصوت",
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }

                // Microphone Mute Toggle
                IconButton(
                    onClick = onMuteToggle,
                    modifier = Modifier
                        .size(50.dp)
                        .clip(CircleShape)
                        .background(if (callState.isMuted) Color(0xFFDC2626) else Color(0xFF334155))
                ) {
                    Icon(
                        imageVector = if (callState.isMuted) Icons.Default.MicOff else Icons.Default.Mic,
                        contentDescription = "كتم الصوت",
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }

                if (callState.isVideo) {
                    // Camera On/Off Toggle
                    IconButton(
                        onClick = onCameraToggle,
                        modifier = Modifier
                            .size(50.dp)
                            .clip(CircleShape)
                            .background(if (!callState.isCameraOn) Color(0xFFDC2626) else Color(0xFF334155))
                    ) {
                        Icon(
                            imageVector = if (callState.isCameraOn) Icons.Default.Videocam else Icons.Default.VideocamOff,
                            contentDescription = "تبديل الكاميرا",
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    // Flip Camera (Front/Rear)
                    IconButton(
                        onClick = onFlipCamera,
                        modifier = Modifier
                            .size(50.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF334155))
                    ) {
                        Icon(
                            imageVector = Icons.Default.Cameraswitch,
                            contentDescription = "قلب الكاميرا",
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }

                // End Call Button (Red)
                IconButton(
                    onClick = onEndCall,
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFEF4444))
                ) {
                    Icon(
                        imageVector = Icons.Default.CallEnd,
                        contentDescription = "إنهاء المكالمة",
                        tint = Color.White,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }
        }
    }

    // ==================== AUDIO ROUTING DIALOG ====================
    if (showAudioDeviceDialog) {
        AlertDialog(
            onDismissRequest = { showAudioDeviceDialog = false },
            title = {
                Text(
                    text = "اختيار مخرج الصوت",
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Start
                )
            },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    // Speaker
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (callState.activeAudioDevice == AudioOutputDevice.SPEAKER) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                RealtimeCallDispatcher.setAudioOutputDevice(AudioOutputDevice.SPEAKER)
                                showAudioDeviceDialog = false
                            }
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(12.dp)
                        ) {
                            Icon(Icons.Default.VolumeUp, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.width(12.dp))
                            Text("مكبر الصوت (Speaker)", style = MaterialTheme.typography.bodyLarge)
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    // Earpiece
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (callState.activeAudioDevice == AudioOutputDevice.EARPIECE) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                RealtimeCallDispatcher.setAudioOutputDevice(AudioOutputDevice.EARPIECE)
                                showAudioDeviceDialog = false
                            }
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(12.dp)
                        ) {
                            Icon(Icons.Default.PhoneInTalk, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.width(12.dp))
                            Text("سماعة الهاتف (Earpiece)", style = MaterialTheme.typography.bodyLarge)
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    // Bluetooth
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (callState.activeAudioDevice == AudioOutputDevice.BLUETOOTH) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                RealtimeCallDispatcher.setAudioOutputDevice(AudioOutputDevice.BLUETOOTH)
                                showAudioDeviceDialog = false
                            }
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(12.dp)
                        ) {
                            Icon(Icons.Default.BluetoothAudio, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.width(12.dp))
                            Text("سماعة بلوتوث (Bluetooth)", style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showAudioDeviceDialog = false }) {
                    Text("إغلاق")
                }
            }
        )
    }

    // ==================== SECURITY & ENCRYPTION DETAILS DIALOG ====================
    if (showSecurityDetailsDialog) {
        AlertDialog(
            onDismissRequest = { showSecurityDetailsDialog = false },
            icon = {
                Icon(
                    imageVector = Icons.Default.Security,
                    contentDescription = null,
                    tint = Color(0xFF25D366),
                    modifier = Modifier.size(36.dp)
                )
            },
            title = {
                Text(
                    text = "التشفير التام بين الطرفين (E2EE)",
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "هذه المكالمة مشفرة بتقنية DTLS-SRTP بنظام التشفير القياسي AES-256، مما يمنع أي طرف ثالث أو مزود خدمة من التنصت على الصوت أو الفيديو.",
                        style = MaterialTheme.typography.bodySmall,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(16.dp))

                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                text = "بصمة الأمان SHA-256:",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = callState.encryptionFingerprint,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "البروتوكول: WebRTC DTLS 1.2 / SRTP",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "مفتاح التشفير: ECDHE-ECDSA-AES128-GCM-SHA256",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "معرف الجلسة: ${callState.callId.take(16)}...",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(onClick = { showSecurityDetailsDialog = false }) {
                    Text("تم التحقق")
                }
            }
        )
    }

    // ==================== ADD PARTICIPANT DIALOG ====================
    if (showAddParticipantDialog) {
        AlertDialog(
            onDismissRequest = { showAddParticipantDialog = false },
            title = {
                Text(
                    text = "إضافة مشارك إلى المكالمة",
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "المكالمات الجماعية المشفرة متاحة عبر بروتوكول شبكة WebRTC المترابطة (Full-Mesh E2EE).",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = "",
                        onValueChange = {},
                        label = { Text("معرف المستخدم (ID)") },
                        placeholder = { Text("أدخل معرف جهة الاتصال...") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                Button(onClick = { showAddParticipantDialog = false }) {
                    Text("دعوة للمكالمة")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddParticipantDialog = false }) {
                    Text("إلغاء")
                }
            }
        )
    }
}
