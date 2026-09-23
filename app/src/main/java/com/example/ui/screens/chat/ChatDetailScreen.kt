package com.example.ui.screens.chat

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.ContactsContract
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import android.widget.VideoView
import android.widget.MediaController
import coil.compose.AsyncImage
import com.example.data.local.entities.ChatEntity
import com.example.data.local.entities.MessageEntity
import com.example.data.local.entities.MessageReactionEntity
import com.example.data.local.entities.UserEntity
import com.example.data.model.SendMessagePayload
import com.example.service.ContactsService
import com.example.service.LocationService
import com.example.service.StorageService
import com.example.ui.components.AttachmentPickerSheet
import com.example.ui.components.AvatarImage
import com.example.ui.components.EmojiPickerBottomSheet
import com.example.ui.components.VoiceMessagePlayer
import com.example.ui.components.VoiceMessageRecorder
import com.example.utils.AppStrings
import com.example.utils.TimeUtils
import kotlinx.coroutines.launch
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatDetailScreen(
    chat: ChatEntity,
    messages: List<MessageEntity>,
    currentUser: UserEntity,
    otherUser: UserEntity?,
    isTyping: Boolean,
    isRecording: Boolean,
    isPlaying: Boolean,
    currentPlayingUri: String?,
    playbackSpeed: Float,
    playbackProgress: Float = 0f,
    isCurrentUserAdminOrOwner: Boolean = true,
    onBackClick: () -> Unit,
    onSendMessage: (SendMessagePayload) -> Unit,
    onRetrySendMessage: (String) -> Unit = {},
    onCancelSendMessage: (String) -> Unit = {},
    onStartRecording: () -> Unit,
    onStopRecordingAndSend: () -> Unit,
    onCancelRecording: () -> Unit,
    onPlayPauseVoice: (String, Float) -> Unit,
    onSpeedChange: (Float) -> Unit,
    onAudioCall: () -> Unit,
    onVideoCall: () -> Unit,
    onTypingChanged: (Boolean) -> Unit,
    onGroupInfoClick: () -> Unit = {},
    onPinMessage: (String?) -> Unit = {},
    reactions: List<MessageReactionEntity> = emptyList(),
    onReactToMessage: (String, String) -> Unit = { _, _ -> }
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    val storageService = remember { StorageService(context) }
    val locationService = remember { LocationService(context) }
    val contactsService = remember { ContactsService(context) }

    var textInput by remember { mutableStateOf("") }
    var showEmojiPicker by remember { mutableStateOf(false) }
    var showAttachSheet by remember { mutableStateOf(false) }
    var showCameraTypeDialog by remember { mutableStateOf(false) }

    var fullScreenImageUri by remember { mutableStateOf<String?>(null) }
    var videoPlayingUri by remember { mutableStateOf<String?>(null) }
    var selectedMessageForOptions by remember { mutableStateOf<MessageEntity?>(null) }
    var selectedMessageForReaction by remember { mutableStateOf<MessageEntity?>(null) }

    // Camera temp storage
    var pendingCameraPhotoUri by remember { mutableStateOf<Uri?>(null) }
    var pendingCameraVideoUri by remember { mutableStateOf<Uri?>(null) }

    // 1. Camera - Photo
    val takePhotoLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        if (success && pendingCameraPhotoUri != null) {
            scope.launch {
                val result = storageService.saveImageAttachment(
                    uri = pendingCameraPhotoUri!!,
                    messageId = java.util.UUID.randomUUID().toString(),
                    chatId = chat.chatId
                )
                if (result.isSuccess) {
                    val att = result.getOrNull()!!
                    onSendMessage(
                        SendMessagePayload(
                            type = "IMAGE",
                            mediaUri = att.storagePath,
                            fileName = att.fileName,
                            fileSize = att.fileSize,
                            mimeType = att.mimeType
                        )
                    )
                } else {
                    Toast.makeText(context, "تعذر معالجة الصورة الملتقطة", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    // 2. Camera - Video
    val recordVideoLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CaptureVideo()
    ) { success ->
        if (success && pendingCameraVideoUri != null) {
            scope.launch {
                val result = storageService.saveVideoAttachment(
                    uri = pendingCameraVideoUri!!,
                    messageId = java.util.UUID.randomUUID().toString(),
                    chatId = chat.chatId
                )
                if (result.isSuccess) {
                    val att = result.getOrNull()!!
                    onSendMessage(
                        SendMessagePayload(
                            type = "VIDEO",
                            mediaUri = att.storagePath,
                            fileName = att.fileName,
                            fileSize = att.fileSize,
                            durationMs = att.durationMs,
                            mimeType = att.mimeType
                        )
                    )
                } else {
                    Toast.makeText(context, "تعذر معالجة الفيديو المسجل", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    // 3. Gallery - Single or visual media
    val galleryPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val mimeType = context.contentResolver.getType(uri) ?: ""
                if (mimeType.startsWith("video/")) {
                    val res = storageService.saveVideoAttachment(uri, java.util.UUID.randomUUID().toString(), chat.chatId)
                    if (res.isSuccess) {
                        val att = res.getOrNull()!!
                        onSendMessage(SendMessagePayload(type = "VIDEO", mediaUri = att.storagePath, fileName = att.fileName, fileSize = att.fileSize, durationMs = att.durationMs, mimeType = att.mimeType))
                    }
                } else {
                    val res = storageService.saveImageAttachment(uri, java.util.UUID.randomUUID().toString(), chat.chatId)
                    if (res.isSuccess) {
                        val att = res.getOrNull()!!
                        onSendMessage(SendMessagePayload(type = "IMAGE", mediaUri = att.storagePath, fileName = att.fileName, fileSize = att.fileSize, mimeType = att.mimeType))
                    }
                }
            }
        }
    }

    // 4. Video picker specifically
    val videoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val res = storageService.saveVideoAttachment(uri, java.util.UUID.randomUUID().toString(), chat.chatId)
                if (res.isSuccess) {
                    val att = res.getOrNull()!!
                    onSendMessage(SendMessagePayload(type = "VIDEO", mediaUri = att.storagePath, fileName = att.fileName, fileSize = att.fileSize, durationMs = att.durationMs, mimeType = att.mimeType))
                } else {
                    Toast.makeText(context, res.exceptionOrNull()?.message ?: "فشل تحميل الفيديو", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    // 5. Document/File picker
    val documentPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val res = storageService.saveDocumentAttachment(uri, java.util.UUID.randomUUID().toString(), chat.chatId)
                if (res.isSuccess) {
                    val att = res.getOrNull()!!
                    onSendMessage(SendMessagePayload(type = "FILE", mediaUri = att.storagePath, fileName = att.fileName, fileSize = att.fileSize, mimeType = att.mimeType))
                } else {
                    Toast.makeText(context, res.exceptionOrNull()?.message ?: "فشل إرفاق الملف", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    // 6. Audio file picker
    val audioPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val res = storageService.saveAudioAttachment(uri, java.util.UUID.randomUUID().toString(), chat.chatId)
                if (res.isSuccess) {
                    val att = res.getOrNull()!!
                    onSendMessage(SendMessagePayload(type = "AUDIO", mediaUri = att.storagePath, fileName = att.fileName, fileSize = att.fileSize, durationMs = att.durationMs, mimeType = att.mimeType))
                } else {
                    Toast.makeText(context, res.exceptionOrNull()?.message ?: "فشل إرفاق الملف الصوتي", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    // 7. Location permissions & fetcher
    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val granted = permissions[android.Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                permissions[android.Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (granted) {
            Toast.makeText(context, "جاري تحديد موقعك الجغرافي...", Toast.LENGTH_SHORT).show()
            scope.launch {
                val locRes = locationService.getCurrentLocation()
                if (locRes.isSuccess) {
                    val loc = locRes.getOrNull()!!
                    onSendMessage(
                        SendMessagePayload(
                            type = "LOCATION",
                            content = loc.address,
                            latitude = loc.latitude,
                            longitude = loc.longitude,
                            locationAddress = loc.address
                        )
                    )
                } else {
                    Toast.makeText(context, locRes.exceptionOrNull()?.message ?: "تعذر تحديد الموقع", Toast.LENGTH_LONG).show()
                }
            }
        } else {
            Toast.makeText(context, "يرجى منح إذن الموقع لمشاركة إحداثياتك", Toast.LENGTH_SHORT).show()
        }
    }

    // 8. Contact picker
    val contactPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickContact()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val contactRes = contactsService.getContactFromUri(uri)
                if (contactRes.isSuccess) {
                    val contact = contactRes.getOrNull()!!
                    onSendMessage(
                        SendMessagePayload(
                            type = "CONTACT",
                            content = "${contact.name}: ${contact.phoneNumber}",
                            contactName = contact.name,
                            contactPhone = contact.phoneNumber,
                            contactEmail = contact.email
                        )
                    )
                } else {
                    Toast.makeText(context, contactRes.exceptionOrNull()?.message ?: "تعذر جلب بيانات جهة الاتصال", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    val contactsPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            contactPickerLauncher.launch(null)
        } else {
            Toast.makeText(context, "إذن جهات الاتصال مطلوب لاختيار جهة اتصال", Toast.LENGTH_SHORT).show()
        }
    }

    // Auto-scroll when messages update
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    val title = if (chat.type == "GROUP") chat.groupName ?: "Group" else otherUser?.username ?: "User"
    val avatarUri = if (chat.type == "GROUP") chat.groupIconUri else otherUser?.profilePicUri
    val isOnline = if (chat.type == "GROUP") false else otherUser?.isOnline == true

    Scaffold(
        contentWindowInsets = WindowInsets.statusBars,
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable {
                                if (chat.type == "GROUP") {
                                    onGroupInfoClick()
                                }
                            }
                            .padding(4.dp)
                    ) {
                        AvatarImage(
                            uri = avatarUri,
                            name = title,
                            size = 40.dp,
                            isOnline = isOnline,
                            showOnlineBadge = chat.type != "GROUP"
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = title,
                                style = MaterialTheme.typography.titleMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = when {
                                    isTyping -> AppStrings.get("typing")
                                    chat.type == "GROUP" -> "انقر لمعلومات المجموعة"
                                    isOnline -> AppStrings.get("online")
                                    else -> AppStrings.get("last_seen")
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = if (isTyping) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                actions = {
                    if (chat.type == "GROUP") {
                        IconButton(onClick = onGroupInfoClick) {
                            Icon(imageVector = Icons.Default.Info, contentDescription = "معلومات المجموعة")
                        }
                    } else {
                        IconButton(onClick = onAudioCall) {
                            Icon(imageVector = Icons.Default.Call, contentDescription = "Audio Call")
                        }
                        IconButton(onClick = onVideoCall) {
                            Icon(imageVector = Icons.Default.Videocam, contentDescription = "Video Call")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            )
        }
    ) { innerPadding ->
        val density = LocalDensity.current
        val imeBottom = WindowInsets.ime.getBottom(density)
        LaunchedEffect(imeBottom) {
            if (imeBottom > 0 && messages.isNotEmpty()) {
                listState.animateScrollToItem(messages.size - 1)
            }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = innerPadding.calculateTopPadding())
                .windowInsetsPadding(WindowInsets.navigationBars.union(WindowInsets.ime))
                .background(MaterialTheme.colorScheme.background)
        ) {
            // Pinned Message Banner
            if (!chat.pinnedMessageId.isNullOrBlank()) {
                val pinnedMessage = remember(messages, chat.pinnedMessageId) {
                    messages.firstOrNull { it.messageId == chat.pinnedMessageId }
                }
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            val index = messages.indexOfFirst { it.messageId == chat.pinnedMessageId }
                            if (index >= 0) {
                                scope.launch { listState.animateScrollToItem(index) }
                            }
                        }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.PushPin,
                            contentDescription = "Pinned",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "رسالة مثبتة",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = pinnedMessage?.content?.takeIf { it.isNotBlank() }
                                    ?: when (pinnedMessage?.type) {
                                        "IMAGE" -> "📷 صورة"
                                        "VIDEO" -> "🎥 فيديو"
                                        "FILE" -> "📄 مستند"
                                        "AUDIO", "VOICE" -> "🎤 رسالة صوتية"
                                        "LOCATION" -> "📍 موقع جغرافي"
                                        "CONTACT" -> "👤 جهة اتصال"
                                        else -> "رسالة مثبتة"
                                    },
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        if (isCurrentUserAdminOrOwner || chat.type != "GROUP") {
                            IconButton(
                                onClick = { onPinMessage(null) },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(Icons.Default.Close, contentDescription = "إلغاء التثبيت", modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }
            }

            // Messages List
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(messages, key = { it.messageId }) { msg ->
                    val isMe = msg.senderUserId == currentUser.userId
                    val bubbleShape = if (isMe) {
                        RoundedCornerShape(18.dp, 18.dp, 4.dp, 18.dp)
                    } else {
                        RoundedCornerShape(18.dp, 18.dp, 18.dp, 4.dp)
                    }

                    val bgColor = if (isMe) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
                    val textColor = if (isMe) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant

                    val msgReactions = remember(reactions, msg.messageId) {
                        reactions.filter { it.messageId == msg.messageId }
                    }
                    val groupedReactions = remember(msgReactions) {
                        msgReactions.groupBy { it.emoji }
                    }

                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = if (isMe) Alignment.CenterEnd else Alignment.CenterStart
                    ) {
                        Column(
                            horizontalAlignment = if (isMe) Alignment.End else Alignment.Start
                        ) {
                            @OptIn(ExperimentalFoundationApi::class)
                            Surface(
                                shape = bubbleShape,
                                color = bgColor,
                                modifier = Modifier
                                    .widthIn(max = 290.dp)
                                    .combinedClickable(
                                        onClick = { selectedMessageForOptions = msg },
                                        onLongClick = { selectedMessageForReaction = msg }
                                    )
                            ) {
                            Column(modifier = Modifier.padding(10.dp)) {

                                // 1. TEXT
                                if (msg.type == "TEXT") {
                                    Text(
                                        text = msg.content,
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = textColor
                                    )
                                }

                                // 2. IMAGE
                                else if (msg.type == "IMAGE") {
                                    if (msg.mediaUri != null) {
                                        AsyncImage(
                                            model = msg.mediaUri,
                                            contentDescription = "Image",
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(200.dp)
                                                .clip(RoundedCornerShape(12.dp))
                                                .clickable { fullScreenImageUri = msg.mediaUri },
                                            contentScale = ContentScale.Crop
                                        )
                                    }
                                    if (!msg.caption.isNullOrBlank()) {
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(text = msg.caption ?: "", color = textColor)
                                    }
                                }

                                // 3. VIDEO
                                else if (msg.type == "VIDEO") {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(180.dp)
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(Color.Black)
                                            .clickable { videoPlayingUri = msg.mediaUri },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (msg.mediaUri != null) {
                                            AsyncImage(
                                                model = msg.mediaUri,
                                                contentDescription = "Video Thumbnail",
                                                modifier = Modifier.fillMaxSize(),
                                                contentScale = ContentScale.Crop
                                            )
                                        }
                                        Box(
                                            modifier = Modifier
                                                .size(48.dp)
                                                .clip(CircleShape)
                                                .background(Color.Black.copy(alpha = 0.6f)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.PlayArrow,
                                                contentDescription = "Play Video",
                                                tint = Color.White,
                                                modifier = Modifier.size(32.dp)
                                            )
                                        }

                                        if (msg.mediaDurationMs > 0) {
                                            Surface(
                                                shape = RoundedCornerShape(4.dp),
                                                color = Color.Black.copy(alpha = 0.7f),
                                                modifier = Modifier
                                                    .align(Alignment.BottomEnd)
                                                    .padding(6.dp)
                                            ) {
                                                Text(
                                                    text = TimeUtils.formatMsDuration(msg.mediaDurationMs),
                                                    fontSize = 10.sp,
                                                    color = Color.White,
                                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                                )
                                            }
                                        }
                                    }
                                }

                                // 4. FILE / DOCUMENT
                                else if (msg.type == "FILE") {
                                    Surface(
                                        shape = RoundedCornerShape(12.dp),
                                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.6f),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                if (msg.mediaUri != null) {
                                                    try {
                                                        val sharableUri = storageService.getSharableFileUri(msg.mediaUri)
                                                        val intent = Intent(Intent.ACTION_VIEW).apply {
                                                            setDataAndType(sharableUri, msg.mimeType ?: "*/*")
                                                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                                        }
                                                        context.startActivity(intent)
                                                    } catch (e: Exception) {
                                                        Toast.makeText(context, "لا يوجد تطبيق مناسب لفتح هذا الملف", Toast.LENGTH_SHORT).show()
                                                    }
                                                }
                                            }
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.padding(10.dp)
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(40.dp)
                                                    .clip(RoundedCornerShape(8.dp))
                                                    .background(MaterialTheme.colorScheme.primary),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(
                                                    imageVector = Icons.AutoMirrored.Filled.InsertDriveFile,
                                                    contentDescription = "Document",
                                                    tint = MaterialTheme.colorScheme.onPrimary,
                                                    modifier = Modifier.size(24.dp)
                                                )
                                            }
                                            Spacer(modifier = Modifier.width(10.dp))
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = msg.mediaFileName ?: "Document",
                                                    style = MaterialTheme.typography.titleSmall,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis,
                                                    color = MaterialTheme.colorScheme.onSurface
                                                )
                                                Text(
                                                    text = formatFileSize(msg.mediaFileSize),
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                            Icon(
                                                imageVector = Icons.Default.Download,
                                                contentDescription = "Open",
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                    }
                                }

                                // 5. AUDIO / VOICE
                                else if (msg.type == "AUDIO" || msg.type == "VOICE") {
                                    VoiceMessagePlayer(
                                        mediaUri = msg.mediaUri ?: "",
                                        durationMs = msg.mediaDurationMs,
                                        isPlaying = isPlaying,
                                        currentPlayingUri = currentPlayingUri,
                                        playbackSpeed = playbackSpeed,
                                        playbackProgress = playbackProgress,
                                        onPlayPauseToggle = onPlayPauseVoice,
                                        onSpeedChange = onSpeedChange
                                    )
                                }

                                // 6. LOCATION
                                else if (msg.type == "LOCATION") {
                                    Surface(
                                        shape = RoundedCornerShape(12.dp),
                                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.6f),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                if (msg.latitude != null && msg.longitude != null) {
                                                    val uri = Uri.parse("geo:${msg.latitude},${msg.longitude}?q=${msg.latitude},${msg.longitude}(${Uri.encode(msg.locationAddress ?: "الموقع")})")
                                                    val mapIntent = Intent(Intent.ACTION_VIEW, uri)
                                                    try {
                                                        context.startActivity(mapIntent)
                                                    } catch (e: Exception) {
                                                        Toast.makeText(context, "لا يوجد تطبيق خرائط متاح", Toast.LENGTH_SHORT).show()
                                                    }
                                                }
                                            }
                                    ) {
                                        Column(modifier = Modifier.padding(10.dp)) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Icon(
                                                    imageVector = Icons.Default.LocationOn,
                                                    contentDescription = "Location",
                                                    tint = Color(0xFFE53935),
                                                    modifier = Modifier.size(24.dp)
                                                )
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Text(
                                                    text = "موقع جغرافي مباشر",
                                                    style = MaterialTheme.typography.titleSmall,
                                                    fontWeight = FontWeight.Bold,
                                                    color = MaterialTheme.colorScheme.onSurface
                                                )
                                            }
                                            Spacer(modifier = Modifier.height(6.dp))
                                            Text(
                                                text = msg.locationAddress ?: msg.content.ifBlank { "الإحداثيات: ${msg.latitude}, ${msg.longitude}" },
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                maxLines = 3,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Spacer(modifier = Modifier.height(8.dp))
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.End,
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                Text(
                                                    text = "عرض على الخريطة",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.primary,
                                                    fontWeight = FontWeight.Bold
                                                )
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Icon(
                                                    imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                                                    contentDescription = null,
                                                    tint = MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier.size(14.dp)
                                                )
                                            }
                                        }
                                    }
                                }

                                // 7. CONTACT
                                else if (msg.type == "CONTACT") {
                                    Surface(
                                        shape = RoundedCornerShape(12.dp),
                                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.6f),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Column(modifier = Modifier.padding(10.dp)) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(42.dp)
                                                        .clip(CircleShape)
                                                        .background(Color(0xFF009688)),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.Person,
                                                        contentDescription = null,
                                                        tint = Color.White
                                                    )
                                                }
                                                Spacer(modifier = Modifier.width(10.dp))
                                                Column {
                                                    Text(
                                                        text = msg.contactName ?: "جهة اتصال",
                                                        style = MaterialTheme.typography.titleSmall,
                                                        fontWeight = FontWeight.Bold,
                                                        color = MaterialTheme.colorScheme.onSurface
                                                    )
                                                    Text(
                                                        text = msg.contactPhone ?: "",
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                }
                                            }

                                            Spacer(modifier = Modifier.height(10.dp))

                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                if (!msg.contactPhone.isNullOrBlank()) {
                                                    OutlinedButton(
                                                        onClick = {
                                                            val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:${msg.contactPhone}"))
                                                            context.startActivity(intent)
                                                        },
                                                        modifier = Modifier.weight(1f),
                                                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp)
                                                    ) {
                                                        Icon(Icons.Default.Phone, contentDescription = null, modifier = Modifier.size(16.dp))
                                                        Spacer(modifier = Modifier.width(4.dp))
                                                        Text("اتصال", fontSize = 12.sp)
                                                    }
                                                }

                                                Button(
                                                    onClick = {
                                                        val intent = Intent(Intent.ACTION_INSERT).apply {
                                                            type = ContactsContract.Contacts.CONTENT_TYPE
                                                            putExtra(ContactsContract.Intents.Insert.NAME, msg.contactName ?: "")
                                                            putExtra(ContactsContract.Intents.Insert.PHONE, msg.contactPhone ?: "")
                                                            if (!msg.contactEmail.isNullOrBlank()) {
                                                                putExtra(ContactsContract.Intents.Insert.EMAIL, msg.contactEmail)
                                                            }
                                                        }
                                                        try {
                                                            context.startActivity(intent)
                                                        } catch (e: Exception) {
                                                            Toast.makeText(context, "تعذر فتح تطبيق جهات الاتصال", Toast.LENGTH_SHORT).show()
                                                        }
                                                    },
                                                    modifier = Modifier.weight(1f),
                                                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp)
                                                ) {
                                                    Icon(Icons.Default.PersonAdd, contentDescription = null, modifier = Modifier.size(16.dp))
                                                    Spacer(modifier = Modifier.width(4.dp))
                                                    Text("حفظ", fontSize = 12.sp)
                                                }
                                            }
                                        }
                                    }
                                }

                                // Upload Progress or Failure Retry Bar
                                if (isMe && msg.status == "UPLOADING") {
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Column(modifier = Modifier.fillMaxWidth()) {
                                        LinearProgressIndicator(
                                            progress = { msg.uploadProgress.coerceIn(0f, 1f) },
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(4.dp)
                                                .clip(RoundedCornerShape(2.dp)),
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                        Text(
                                            text = "جاري الرفع... ${(msg.uploadProgress * 100).toInt()}%",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontSize = 9.sp,
                                            color = textColor.copy(alpha = 0.7f)
                                        )
                                    }
                                } else if (isMe && msg.status == "FAILED") {
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(
                                                imageVector = Icons.Default.ErrorOutline,
                                                contentDescription = "Failed",
                                                tint = MaterialTheme.colorScheme.error,
                                                modifier = Modifier.size(14.dp)
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(
                                                text = "فشل الإرسال",
                                                color = MaterialTheme.colorScheme.error,
                                                fontSize = 10.sp
                                            )
                                        }
                                        Row {
                                            Text(
                                                text = "إعادة المحاولة",
                                                color = MaterialTheme.colorScheme.primary,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier
                                                    .clickable { onRetrySendMessage(msg.messageId) }
                                                    .padding(horizontal = 4.dp)
                                            )
                                            Text(
                                                text = "إلغاء",
                                                color = MaterialTheme.colorScheme.error,
                                                fontSize = 11.sp,
                                                modifier = Modifier
                                                    .clickable { onCancelSendMessage(msg.messageId) }
                                                    .padding(horizontal = 4.dp)
                                            )
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(4.dp))

                                // Timestamp & Delivery Status
                                Row(
                                    horizontalArrangement = Arrangement.End,
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.align(Alignment.End)
                                ) {
                                    Text(
                                        text = TimeUtils.formatTime(msg.timestamp),
                                        fontSize = 10.sp,
                                        color = textColor.copy(alpha = 0.6f)
                                    )
                                    if (isMe) {
                                        Spacer(modifier = Modifier.width(4.dp))
                                        val isRead = msg.status == "READ" || msg.readAt != null
                                        Icon(
                                            imageVector = when {
                                                isRead -> Icons.Default.DoneAll
                                                msg.status == "DELIVERED" -> Icons.Default.DoneAll
                                                msg.status == "UPLOADING" -> Icons.Default.Schedule
                                                msg.status == "FAILED" -> Icons.Default.Error
                                                else -> Icons.Default.Done
                                            },
                                            contentDescription = if (isRead) "READ" else msg.status,
                                            tint = when {
                                                isRead -> Color(0xFF34B7F1)
                                                msg.status == "FAILED" -> MaterialTheme.colorScheme.error
                                                else -> textColor.copy(alpha = 0.6f)
                                            },
                                            modifier = Modifier.size(14.dp)
                                        )
                                    }
                                }
                            }
                        }

                        // Display emoji reactions below bubble
                        if (groupedReactions.isNotEmpty()) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                modifier = Modifier
                                    .padding(top = 2.dp)
                                    .padding(horizontal = 4.dp)
                            ) {
                                groupedReactions.forEach { (emoji, list) ->
                                    val hasReacted = list.any { it.userId == currentUser.userId }
                                    Surface(
                                        shape = RoundedCornerShape(12.dp),
                                        color = if (hasReacted) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceVariant,
                                        border = BorderStroke(1.dp, if (hasReacted) MaterialTheme.colorScheme.primary else Color.Transparent),
                                        modifier = Modifier.clickable {
                                            onReactToMessage(msg.messageId, emoji)
                                        }
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        ) {
                                            Text(text = emoji, fontSize = 13.sp)
                                            if (list.size > 1) {
                                                Spacer(modifier = Modifier.width(3.dp))
                                                Text(
                                                    text = "${list.size}",
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Bottom Input Bar or Read-Only Notice
            val canSendMessages = chat.type != "GROUP" || chat.sendMessagesPermission != "ADMIN_ONLY" || isCurrentUserAdminOrOwner

            if (!canSendMessages) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "فقط المشرفون يمكنهم إرسال الرسائل في هذه المجموعة",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                if (isRecording) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 6.dp, vertical = 6.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(26.dp),
                            color = MaterialTheme.colorScheme.surface,
                            shadowElevation = 1.dp,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            VoiceMessageRecorder(
                                isRecording = true,
                                onStartRecording = onStartRecording,
                                onStopAndSend = onStopRecordingAndSend,
                                onCancelRecording = onCancelRecording,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                } else {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 6.dp, vertical = 6.dp)
                    ) {
                        // WhatsApp-Style Rounded Input Capsule
                        Surface(
                            shape = RoundedCornerShape(26.dp),
                            color = MaterialTheme.colorScheme.surface,
                            shadowElevation = 1.dp,
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 48.dp, max = 130.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                            ) {
                                IconButton(
                                    onClick = { showEmojiPicker = true },
                                    modifier = Modifier.size(40.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.SentimentSatisfiedAlt,
                                        contentDescription = "Emoji",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                        modifier = Modifier.size(24.dp)
                                    )
                                }

                                BasicTextField(
                                    value = textInput,
                                    onValueChange = {
                                        textInput = it
                                        onTypingChanged(it.isNotBlank())
                                    },
                                    modifier = Modifier
                                        .weight(1f)
                                        .padding(horizontal = 6.dp, vertical = 8.dp),
                                    textStyle = MaterialTheme.typography.bodyLarge.copy(
                                        color = MaterialTheme.colorScheme.onSurface
                                    ),
                                    maxLines = 5,
                                    decorationBox = { innerTextField ->
                                        Box(contentAlignment = Alignment.CenterStart) {
                                            if (textInput.isEmpty()) {
                                                Text(
                                                    text = AppStrings.get("type_message"),
                                                    style = MaterialTheme.typography.bodyLarge,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                                )
                                            }
                                            innerTextField()
                                        }
                                    }
                                )

                                IconButton(
                                    onClick = { showAttachSheet = true },
                                    modifier = Modifier.size(40.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.AttachFile,
                                        contentDescription = "Attach",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                        modifier = Modifier
                                            .size(24.dp)
                                            .rotate(-45f)
                                    )
                                }

                                IconButton(
                                    onClick = { showCameraTypeDialog = true },
                                    modifier = Modifier.size(40.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.CameraAlt,
                                        contentDescription = "Camera",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.width(6.dp))

                        // WhatsApp-Style Floating Round Action Button (Mic or Send)
                        if (textInput.isBlank()) {
                            VoiceMessageRecorder(
                                isRecording = false,
                                onStartRecording = onStartRecording,
                                onStopAndSend = onStopRecordingAndSend,
                                onCancelRecording = onCancelRecording,
                                modifier = Modifier.size(48.dp)
                            )
                        } else {
                            IconButton(
                                onClick = {
                                    if (textInput.isNotBlank()) {
                                        onSendMessage(SendMessagePayload(type = "TEXT", content = textInput.trim()))
                                        textInput = ""
                                        onTypingChanged(false)
                                    }
                                },
                                modifier = Modifier
                                    .size(48.dp)
                                    .shadow(1.5.dp, CircleShape)
                                    .clip(CircleShape)
                                    .background(Color(0xFF00A884))
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.Send,
                                    contentDescription = "Send",
                                    tint = Color.White,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // Attachment Picker Modal Bottom Sheet
    if (showAttachSheet) {
        AttachmentPickerSheet(
            onDismiss = { showAttachSheet = false },
            onCameraClick = {
                showAttachSheet = false
                showCameraTypeDialog = true
            },
            onGalleryClick = {
                showAttachSheet = false
                galleryPickerLauncher.launch(
                    androidx.activity.result.PickVisualMediaRequest(
                        ActivityResultContracts.PickVisualMedia.ImageAndVideo
                    )
                )
            },
            onVideoClick = {
                showAttachSheet = false
                videoPickerLauncher.launch(
                    androidx.activity.result.PickVisualMediaRequest(
                        ActivityResultContracts.PickVisualMedia.VideoOnly
                    )
                )
            },
            onDocumentClick = {
                showAttachSheet = false
                documentPickerLauncher.launch("*/*")
            },
            onAudioClick = {
                showAttachSheet = false
                audioPickerLauncher.launch("audio/*")
            },
            onLocationClick = {
                showAttachSheet = false
                locationPermissionLauncher.launch(
                    arrayOf(
                        android.Manifest.permission.ACCESS_FINE_LOCATION,
                        android.Manifest.permission.ACCESS_COARSE_LOCATION
                    )
                )
            },
            onContactClick = {
                showAttachSheet = false
                contactsPermissionLauncher.launch(android.Manifest.permission.READ_CONTACTS)
            }
        )
    }

    // Camera Option Dialog (Take Photo vs Record Video)
    if (showCameraTypeDialog) {
        AlertDialog(
            onDismissRequest = { showCameraTypeDialog = false },
            title = { Text("الكاميرا / Camera") },
            text = {
                Column {
                    TextButton(
                        onClick = {
                            showCameraTypeDialog = false
                            val (file, uri) = storageService.createCameraTempImageUri()
                            pendingCameraPhotoUri = uri
                            takePhotoLauncher.launch(uri)
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.PhotoCamera, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("التقاط صورة / Take Photo")
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    TextButton(
                        onClick = {
                            showCameraTypeDialog = false
                            val (file, uri) = storageService.createCameraTempVideoUri()
                            pendingCameraVideoUri = uri
                            recordVideoLauncher.launch(uri)
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Videocam, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("تسجيل فيديو / Record Video")
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showCameraTypeDialog = false }) {
                    Text("إلغاء")
                }
            }
        )
    }

    // Message Options Dialog (Reactions, Pin, Copy)
    selectedMessageForOptions?.let { msg ->
        val quickEmojis = listOf("👍", "❤️", "😂", "😮", "😢", "🙏", "🔥", "👏")
        AlertDialog(
            onDismissRequest = { selectedMessageForOptions = null },
            title = { Text("خيارات الرسالة") },
            text = {
                Column {
                    // Quick Emoji Reactions Row
                    Text(
                        text = "التفاعل:",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 6.dp)
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        quickEmojis.forEach { emoji ->
                            val isMyReaction = reactions.any { it.messageId == msg.messageId && it.userId == currentUser.userId && it.emoji == emoji }
                            Surface(
                                shape = CircleShape,
                                color = if (isMyReaction) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                                modifier = Modifier
                                    .size(36.dp)
                                    .clickable {
                                        onReactToMessage(msg.messageId, emoji)
                                        selectedMessageForOptions = null
                                    }
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(text = emoji, fontSize = 18.sp)
                                }
                            }
                        }
                    }

                    HorizontalDivider(modifier = Modifier.padding(bottom = 8.dp))

                    // Read Receipt Details for sender
                    if (msg.senderUserId == currentUser.userId) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 8.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                            ) {
                                val isRead = msg.status == "READ" || msg.readAt != null
                                Icon(
                                    imageVector = when {
                                        isRead -> Icons.Default.DoneAll
                                        msg.status == "DELIVERED" -> Icons.Default.DoneAll
                                        else -> Icons.Default.Done
                                    },
                                    contentDescription = null,
                                    tint = if (isRead) Color(0xFF34B7F1) else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text(
                                        text = when {
                                            isRead -> "تمت القراءة"
                                            msg.status == "DELIVERED" -> "تم الاستلام"
                                            else -> "تم الإرسال"
                                        },
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isRead) Color(0xFF34B7F1) else MaterialTheme.colorScheme.onSurface
                                    )
                                    if (isRead && msg.readAt != null) {
                                        Text(
                                            text = "وقت القراءة: ${TimeUtils.formatDateOrTime(msg.readAt)}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }

                    if (msg.type == "TEXT" && msg.content.isNotBlank()) {
                        TextButton(
                            onClick = {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                                val clip = android.content.ClipData.newPlainText("Chat Message", msg.content)
                                clipboard.setPrimaryClip(clip)
                                Toast.makeText(context, "تم نسخ الرسالة", Toast.LENGTH_SHORT).show()
                                selectedMessageForOptions = null
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.ContentCopy, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("نسخ النص")
                        }
                    }

                    if (isCurrentUserAdminOrOwner || chat.type != "GROUP") {
                        val isPinned = chat.pinnedMessageId == msg.messageId
                        TextButton(
                            onClick = {
                                onPinMessage(if (isPinned) null else msg.messageId)
                                selectedMessageForOptions = null
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(if (isPinned) Icons.Default.Close else Icons.Default.PushPin, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(if (isPinned) "إلغاء تثبيت الرسالة" else "تثبيت الرسالة في المحادثة")
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { selectedMessageForOptions = null }) {
                    Text("إغلاق")
                }
            }
        )
    }

    // Direct Long-Press Emoji Reaction Dialog
    selectedMessageForReaction?.let { msg ->
        val commonEmojis = listOf("👍", "❤️", "😂", "😮", "😢", "🙏", "🔥", "👏", "🎉", "😍")
        AlertDialog(
            onDismissRequest = { selectedMessageForReaction = null },
            title = { Text("اختر تفاعلاً", style = MaterialTheme.typography.titleMedium) },
            text = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    commonEmojis.forEach { emoji ->
                        val isMyReaction = reactions.any { it.messageId == msg.messageId && it.userId == currentUser.userId && it.emoji == emoji }
                        Surface(
                            shape = CircleShape,
                            color = if (isMyReaction) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier
                                .size(40.dp)
                                .clickable {
                                    onReactToMessage(msg.messageId, emoji)
                                    selectedMessageForReaction = null
                                }
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(text = emoji, fontSize = 20.sp)
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { selectedMessageForReaction = null }) {
                    Text("إلغاء")
                }
            }
        )
    }

    if (showEmojiPicker) {
        EmojiPickerBottomSheet(
            onEmojiSelected = { emoji -> textInput += emoji },
            onDismiss = { showEmojiPicker = false }
        )
    }

    // Full Screen Image Viewer
    if (fullScreenImageUri != null) {
        AlertDialog(
            onDismissRequest = { fullScreenImageUri = null },
            modifier = Modifier.fillMaxWidth(),
            text = {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    AsyncImage(
                        model = fullScreenImageUri,
                        contentDescription = "Full Image",
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(350.dp)
                            .clip(RoundedCornerShape(8.dp)),
                        contentScale = ContentScale.Fit
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { fullScreenImageUri = null }) {
                    Text("إغلاق")
                }
            }
        )
    }

    // Video Player Dialog
    if (videoPlayingUri != null) {
        AlertDialog(
            onDismissRequest = { videoPlayingUri = null },
            modifier = Modifier.fillMaxWidth(),
            text = {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(320.dp)
                        .background(Color.Black),
                    contentAlignment = Alignment.Center
                ) {
                    AndroidView(
                        factory = { ctx ->
                            VideoView(ctx).apply {
                                val mc = MediaController(ctx)
                                mc.setAnchorView(this)
                                setMediaController(mc)
                                val uriToPlay = if (videoPlayingUri!!.startsWith("/")) {
                                    storageService.getSharableFileUri(videoPlayingUri!!)
                                } else {
                                    Uri.parse(videoPlayingUri)
                                }
                                setVideoURI(uriToPlay)
                                setOnPreparedListener { start() }
                            }
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { videoPlayingUri = null }) {
                    Text("إغلاق")
                }
            }
        )
    }
}

private fun formatFileSize(sizeBytes: Long): String {
    if (sizeBytes <= 0) return "0 KB"
    val kb = sizeBytes / 1024.0
    val mb = kb / 1024.0
    return if (mb >= 1.0) {
        String.format(java.util.Locale.US, "%.1f MB", mb)
    } else {
        String.format(java.util.Locale.US, "%.0f KB", kb)
    }
}
