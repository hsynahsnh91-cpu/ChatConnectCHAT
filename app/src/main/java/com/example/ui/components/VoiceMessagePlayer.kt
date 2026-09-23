package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.utils.TimeUtils

@Composable
fun VoiceMessagePlayer(
    mediaUri: String,
    durationMs: Long,
    isPlaying: Boolean,
    currentPlayingUri: String?,
    playbackSpeed: Float,
    onPlayPauseToggle: (String, Float) -> Unit,
    onSpeedChange: (Float) -> Unit,
    playbackProgress: Float = 0f,
    modifier: Modifier = Modifier
) {
    val isThisPlaying = isPlaying && currentPlayingUri == mediaUri
    val speeds = listOf(1.0f, 1.5f, 2.0f)

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
        ) {
            IconButton(
                onClick = { onPlayPauseToggle(mediaUri, playbackSpeed) },
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary)
            ) {
                Icon(
                    imageVector = if (isThisPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = if (isThisPlaying) "Pause" else "Play",
                    tint = MaterialTheme.colorScheme.onPrimary
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            Column(modifier = Modifier.weight(1f)) {
                LinearProgressIndicator(
                    progress = { if (isThisPlaying) playbackProgress.coerceIn(0f, 1f) else 0f },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp)),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                )

                Spacer(modifier = Modifier.height(4.dp))

                val displayedTime = if (isThisPlaying && durationMs > 0) {
                    val currentMs = (durationMs * playbackProgress).toLong()
                    "${TimeUtils.formatMsDuration(currentMs)} / ${TimeUtils.formatMsDuration(durationMs)}"
                } else {
                    TimeUtils.formatMsDuration(if (durationMs > 0) durationMs else 3000L)
                }

                Text(
                    text = displayedTime,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Speed Chip selector
            val nextSpeedIndex = (speeds.indexOf(playbackSpeed) + 1) % speeds.size
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.clickable {
                    onSpeedChange(speeds[nextSpeedIndex])
                }
            ) {
                Text(
                    text = "${playbackSpeed}x",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        }
    }
}
