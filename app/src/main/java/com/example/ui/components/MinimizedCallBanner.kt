package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.OpenInFull
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.service.CallState
import com.example.service.CallStatus
import com.example.utils.TimeUtils

@Composable
fun MinimizedCallBanner(
    callState: CallState,
    onRestoreCall: () -> Unit,
    onMuteToggle: () -> Unit,
    onEndCall: () -> Unit,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = callState.isMinimized && callState.status in listOf(CallStatus.CONNECTED, CallStatus.OUTGOING, CallStatus.RECONNECTING),
        enter = slideInVertically { -it },
        exit = slideOutVertically { -it },
        modifier = modifier
    ) {
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = Color(0xFF0F172A),
            tonalElevation = 8.dp,
            shadowElevation = 8.dp,
            border = androidx.compose.foundation.BorderStroke(1.5.dp, Color(0xFF25D366)),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp)
                .clickable { onRestoreCall() }
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 10.dp)
            ) {
                // Left: Avatar and Call Info
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AvatarImage(
                        uri = callState.callerAvatar,
                        name = callState.callerName,
                        size = 40.dp
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = callState.callerName,
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                            color = Color.White
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(if (callState.status == CallStatus.CONNECTED) Color(0xFF22C55E) else Color(0xFFF59E0B))
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = when (callState.status) {
                                    CallStatus.CONNECTED -> TimeUtils.formatDuration(callState.durationSec)
                                    CallStatus.RECONNECTING -> "جاري استعادة الاتصال..."
                                    else -> "جاري الاتصال..."
                                },
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                                color = Color(0xFF94A3B8)
                            )
                        }
                    }
                }

                // Right: Quick actions
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    // Mute button
                    IconButton(
                        onClick = onMuteToggle,
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(if (callState.isMuted) Color(0xFFDC2626) else Color(0xFF334155))
                    ) {
                        Icon(
                            imageVector = if (callState.isMuted) Icons.Default.MicOff else Icons.Default.Mic,
                            contentDescription = "Mute",
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    // Restore to full screen
                    IconButton(
                        onClick = onRestoreCall,
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF334155))
                    ) {
                        Icon(
                            imageVector = Icons.Default.OpenInFull,
                            contentDescription = "Maximize Call",
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    // Hang up
                    IconButton(
                        onClick = onEndCall,
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(Color(0xFFEF4444))
                    ) {
                        Icon(
                            imageVector = Icons.Default.CallEnd,
                            contentDescription = "End Call",
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}
