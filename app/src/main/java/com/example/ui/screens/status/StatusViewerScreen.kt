package com.example.ui.screens.status

import android.net.Uri
import android.widget.VideoView
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.RemoveRedEye
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import coil.compose.AsyncImage
import com.example.data.local.entities.StatusEntity
import com.example.data.local.entities.StatusViewEntity
import com.example.data.local.entities.UserEntity
import com.example.ui.components.AvatarImage
import com.example.utils.AppStrings
import com.example.utils.TimeUtils
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatusViewerScreen(
    user: UserEntity?,
    statuses: List<StatusEntity>,
    isOwner: Boolean,
    currentStatusViews: List<StatusViewEntity>,
    onStatusViewed: (String) -> Unit,
    onDeleteStatus: (String) -> Unit,
    onReplyToStatus: (statusId: String, replyText: String) -> Unit,
    onReactToStatus: (statusId: String, reactionEmoji: String) -> Unit,
    onMuteToggle: (userId: String) -> Unit,
    isMuted: Boolean = false,
    onDismiss: () -> Unit
) {
    var currentIndex by remember { mutableIntStateOf(0) }
    var isPaused by remember { mutableStateOf(false) }
    var currentProgress by remember { mutableFloatStateOf(0f) }
    var showViewsSheet by remember { mutableStateOf(false) }
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }
    var replyText by remember { mutableStateOf("") }
    var showQuickReactions by remember { mutableStateOf(false) }

    val currentStatus = statuses.getOrNull(currentIndex)

    LaunchedEffect(currentIndex) {
        currentProgress = 0f
        if (currentStatus != null) {
            onStatusViewed(currentStatus.statusId)
        }
    }

    // Playback loop with pausing support
    LaunchedEffect(currentIndex, isPaused, showViewsSheet, showDeleteConfirmDialog) {
        if (isPaused || showViewsSheet || showDeleteConfirmDialog || currentStatus == null) return@LaunchedEffect
        val durationMs = if (currentStatus.type == "VIDEO") 12000L else 5000L
        val stepMs = 50L
        while (currentProgress < 1f) {
            delay(stepMs)
            if (!isPaused && !showViewsSheet && !showDeleteConfirmDialog) {
                currentProgress += (stepMs.toFloat() / durationMs.toFloat())
            }
        }
        if (currentIndex < statuses.size - 1) {
            currentIndex++
            currentProgress = 0f
        } else {
            onDismiss()
        }
    }

    if (currentStatus == null) return

    val bgColor = try {
        Color(android.graphics.Color.parseColor(currentStatus.bgColorHex))
    } catch (e: Exception) {
        Color(0xFF075E54)
    }

    val textFontFamily = when (currentStatus.textStyle.uppercase()) {
        "SERIF" -> FontFamily.Serif
        "MONOSPACE" -> FontFamily.Monospace
        else -> FontFamily.Default
    }

    val textFontWeight = when (currentStatus.textStyle.uppercase()) {
        "BOLD" -> FontWeight.Bold
        else -> FontWeight.Normal
    }

    val textAlign = when (currentStatus.textAlignment.uppercase()) {
        "START" -> TextAlign.Start
        "END" -> TextAlign.End
        else -> TextAlign.Center
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(if (currentStatus.type == "TEXT") bgColor else Color.Black)
            .pointerInput(currentIndex) {
                detectTapGestures(
                    onPress = {
                        isPaused = true
                        tryAwaitRelease()
                        isPaused = false
                    },
                    onTap = { offset ->
                        if (offset.x > size.width / 2) {
                            if (currentIndex < statuses.size - 1) {
                                currentIndex++
                                currentProgress = 0f
                            } else {
                                onDismiss()
                            }
                        } else {
                            if (currentIndex > 0) {
                                currentIndex--
                                currentProgress = 0f
                            }
                        }
                    }
                )
            }
    ) {
        // --- Content Area ---
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.fillMaxSize()
        ) {
            when (currentStatus.type) {
                "IMAGE" -> {
                    if (currentStatus.mediaUri != null) {
                        AsyncImage(
                            model = currentStatus.mediaUri,
                            contentDescription = "Status Image",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Fit
                        )
                    }
                }
                "VIDEO" -> {
                    if (currentStatus.mediaUri != null) {
                        AndroidView(
                            factory = { ctx ->
                                VideoView(ctx).apply {
                                    setVideoURI(Uri.parse(currentStatus.mediaUri))
                                    setOnPreparedListener { mp ->
                                        mp.isLooping = true
                                        start()
                                    }
                                }
                            },
                            update = { videoView ->
                                if (isPaused || showViewsSheet || showDeleteConfirmDialog) {
                                    if (videoView.isPlaying) videoView.pause()
                                } else {
                                    if (!videoView.isPlaying) videoView.start()
                                }
                            },
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
                else -> {
                    // TEXT Status
                    Text(
                        text = currentStatus.contentText ?: "",
                        style = MaterialTheme.typography.headlineMedium.copy(
                            fontFamily = textFontFamily,
                            fontWeight = textFontWeight,
                            textAlign = textAlign,
                            fontSize = 28.sp,
                            lineHeight = 36.sp
                        ),
                        color = Color.White,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp)
                    )
                }
            }

            // Caption overlay
            if (!currentStatus.caption.isNull_or_blank()) {
                Surface(
                    color = Color(0xBB000000),
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .padding(bottom = if (isOwner) 76.dp else 90.dp)
                ) {
                    Text(
                        text = currentStatus.caption ?: "",
                        color = Color.White,
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.padding(16.dp),
                        textAlign = TextAlign.Center
                    )
                }
            }
        }

        // --- Top Header with Segmented Progress Bar ---
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 40.dp, start = 16.dp, end = 16.dp)
        ) {
            // Segmented Progress Bar
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                statuses.forEachIndexed { index, _ ->
                    val segmentProgress = when {
                        index < currentIndex -> 1f
                        index == currentIndex -> currentProgress
                        else -> 0f
                    }
                    LinearProgressIndicator(
                        progress = { segmentProgress },
                        modifier = Modifier
                            .weight(1f)
                            .height(3.5.dp)
                            .clip(RoundedCornerShape(2.dp)),
                        color = Color.White,
                        trackColor = Color.White.copy(alpha = 0.35f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // User Info & Controls Header
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    AvatarImage(
                        uri = user?.profilePicUri,
                        name = user?.username ?: "User",
                        size = 42.dp
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = user?.username ?: "User",
                            style = MaterialTheme.typography.titleMedium,
                            color = Color.White
                        )
                        Text(
                            text = TimeUtils.formatTime(currentStatus.createdAt),
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.8f)
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (isOwner) {
                        IconButton(onClick = { showDeleteConfirmDialog = true }) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = AppStrings.get("delete_status"),
                                tint = Color.White
                            )
                        }
                    } else {
                        Box {
                            IconButton(onClick = { showMenu = true }) {
                                Icon(
                                    imageVector = Icons.Default.MoreVert,
                                    contentDescription = "Menu",
                                    tint = Color.White
                                )
                            }
                            DropdownMenu(
                                expanded = showMenu,
                                onDismissRequest = { showMenu = false }
                            ) {
                                DropdownMenuItem(
                                    text = {
                                        Text(if (isMuted) AppStrings.get("unmute") else AppStrings.get("mute"))
                                    },
                                    leadingIcon = {
                                        Icon(Icons.Default.VolumeOff, contentDescription = null)
                                    },
                                    onClick = {
                                        showMenu = false
                                        user?.let { onMuteToggle(it.userId) }
                                    }
                                )
                            }
                        }
                    }

                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = Color.White
                        )
                    }
                }
            }
        }

        // --- Bottom Controls ---
        if (isOwner) {
            // Viewers count pill for status owner
            Surface(
                color = Color(0x99000000),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 20.dp)
                    .clip(CircleShape)
                    .clickable {
                        isPaused = true
                        showViewsSheet = true
                    }
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.RemoveRedEye,
                        contentDescription = "Views",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "${currentStatusViews.size} ${AppStrings.get("views_count")}",
                        color = Color.White,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        } else {
            // Reply & Quick Reactions for contacts
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(Color(0xCC000000))
                    .padding(horizontal = 16.dp, vertical = 10.dp)
            ) {
                // Quick emoji reaction bar
                val reactions = listOf("❤️", "😂", "😮", "😢", "😡", "👍")
                Row(
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp)
                ) {
                    reactions.forEach { emoji ->
                        Surface(
                            shape = CircleShape,
                            color = Color(0x33FFFFFF),
                            modifier = Modifier
                                .size(36.dp)
                                .clickable {
                                    onReactToStatus(currentStatus.statusId, emoji)
                                }
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(text = emoji, fontSize = 20.sp)
                            }
                        }
                    }
                }

                // Reply input text field
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedTextField(
                        value = replyText,
                        onValueChange = {
                            replyText = it
                            isPaused = it.isNotBlank()
                        },
                        placeholder = {
                            Text(
                                text = AppStrings.get("reply_to_status"),
                                color = Color.White.copy(alpha = 0.6f)
                            )
                        },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = Color.White.copy(alpha = 0.5f),
                            unfocusedBorderColor = Color.White.copy(alpha = 0.3f)
                        ),
                        shape = RoundedCornerShape(24.dp),
                        modifier = Modifier.weight(1f)
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    IconButton(
                        onClick = {
                            if (replyText.isNotBlank()) {
                                onReplyToStatus(currentStatus.statusId, replyText.trim())
                                replyText = ""
                                isPaused = false
                            }
                        },
                        enabled = replyText.isNotBlank()
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Send,
                            contentDescription = "Send",
                            tint = if (replyText.isNotBlank()) MaterialTheme.colorScheme.primary else Color.Gray
                        )
                    }
                }
            }
        }
    }

    // Owner's Viewers List Sheet
    if (showViewsSheet) {
        StatusViewersSheet(
            views = currentStatusViews,
            onDeleteStatus = {
                onDeleteStatus(currentStatus.statusId)
                if (statuses.size <= 1) {
                    onDismiss()
                } else if (currentIndex >= statuses.size - 1) {
                    currentIndex--
                }
            },
            onDismiss = {
                showViewsSheet = false
                isPaused = false
            }
        )
    }

    // Delete Confirmation Dialog
    if (showDeleteConfirmDialog) {
        AlertDialog(
            onDismissRequest = {
                showDeleteConfirmDialog = false
                isPaused = false
            },
            title = { Text(AppStrings.get("delete_status")) },
            text = { Text(AppStrings.get("confirm_delete_status")) },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteConfirmDialog = false
                        onDeleteStatus(currentStatus.statusId)
                        if (statuses.size <= 1) {
                            onDismiss()
                        } else if (currentIndex >= statuses.size - 1) {
                            currentIndex--
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text(AppStrings.get("delete"))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showDeleteConfirmDialog = false
                        isPaused = false
                    }
                ) {
                    Text(AppStrings.get("cancel"))
                }
            }
        )
    }
}

private fun String?.isNull_or_blank(): Boolean = this == null || this.trim().isEmpty()
