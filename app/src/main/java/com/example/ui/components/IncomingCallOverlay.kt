package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.service.CallState
import com.example.utils.AppStrings

@Composable
fun IncomingCallOverlay(
    callState: CallState,
    onAccept: () -> Unit,
    onReject: () -> Unit
) {
    Dialog(
        onDismissRequest = { /* Modal call requirement */ },
        properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false)
    ) {
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
            modifier = Modifier.fillMaxWidth().padding(16.dp)
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(24.dp)
            ) {
                AvatarImage(
                    uri = callState.callerAvatar,
                    name = callState.callerName,
                    size = 84.dp
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = callState.callerName,
                    style = MaterialTheme.typography.titleLarge
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = if (callState.isVideo) "${AppStrings.get("video_call")} - ${AppStrings.get("incoming_call")}" else "${AppStrings.get("audio_call")} - ${AppStrings.get("incoming_call")}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Encrypted badge
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = Color(0xFF25D366).copy(alpha = 0.12f)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Call,
                            contentDescription = null,
                            tint = Color(0xFF1E8E3E),
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "مكالمة واردة مشفرة (E2EE)",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFF1E8E3E)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(28.dp))

                Row(
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    // Reject Button
                    IconButton(
                        onClick = onReject,
                        modifier = Modifier
                            .size(56.dp)
                            .clip(CircleShape)
                            .background(Color.Red)
                    ) {
                        Icon(
                            imageVector = Icons.Default.CallEnd,
                            contentDescription = "Reject",
                            tint = Color.White
                        )
                    }

                    // Accept Button
                    IconButton(
                        onClick = onAccept,
                        modifier = Modifier
                            .size(56.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF25D366))
                    ) {
                        Icon(
                            imageVector = if (callState.isVideo) Icons.Default.Videocam else Icons.Default.Call,
                            contentDescription = "Accept",
                            tint = Color.White
                        )
                    }
                }
            }
        }
    }
}
