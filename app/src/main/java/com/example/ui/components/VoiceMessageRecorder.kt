package com.example.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.example.utils.AppStrings
import com.example.utils.TimeUtils
import kotlinx.coroutines.delay

@Composable
fun VoiceMessageRecorder(
    isRecording: Boolean,
    onStartRecording: () -> Unit,
    onStopAndSend: () -> Unit,
    onCancelRecording: () -> Unit,
    modifier: Modifier = Modifier
) {
    var timerSeconds by remember { mutableIntStateOf(0) }

    LaunchedEffect(isRecording) {
        if (isRecording) {
            timerSeconds = 0
            while (isRecording) {
                delay(1000L)
                timerSeconds++
            }
        }
    }

    val pulseScale by animateFloatAsState(
        targetValue = if (isRecording) 1.25f else 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(600),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )

    if (isRecording) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(12.dp)
                        .clip(CircleShape)
                        .background(Color.Red)
                )

                Spacer(modifier = Modifier.width(8.dp))

                Text(
                    text = TimeUtils.formatDuration(timerSeconds),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.error
                )

                Spacer(modifier = Modifier.width(16.dp))

                Text(
                    text = AppStrings.get("slide_to_cancel"),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Row {
                TextButton(onClick = onCancelRecording) {
                    Text(text = AppStrings.get("cancel"), color = MaterialTheme.colorScheme.error)
                }

                Spacer(modifier = Modifier.width(4.dp))

                IconButton(
                    onClick = onStopAndSend,
                    modifier = Modifier
                        .size(48.dp)
                        .scale(pulseScale)
                        .clip(CircleShape)
                        .background(Color(0xFF00A884))
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Send,
                        contentDescription = "Send Voice Note",
                        tint = Color.White
                    )
                }
            }
        }
    } else {
        IconButton(
            onClick = onStartRecording,
            modifier = modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(Color(0xFF00A884))
        ) {
            Icon(
                imageVector = Icons.Default.Mic,
                contentDescription = "Record Voice Note",
                tint = Color.White
            )
        }
    }
}
