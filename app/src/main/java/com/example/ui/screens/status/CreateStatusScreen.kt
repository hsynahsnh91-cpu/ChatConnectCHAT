package com.example.ui.screens.status

import android.content.Context
import android.net.Uri
import android.widget.VideoView
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.FormatAlignLeft
import androidx.compose.material.icons.automirrored.filled.FormatAlignRight
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.FileProvider
import coil.compose.AsyncImage
import com.example.data.local.entities.ContactEntity
import com.example.data.local.entities.UserEntity
import com.example.data.local.entities.UserSettingsEntity
import com.example.utils.AppStrings
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateStatusScreen(
    currentSettings: UserSettingsEntity?,
    contacts: List<ContactEntity>,
    allUsers: List<UserEntity>,
    onPostStatus: (
        type: String,
        contentText: String?,
        mediaUri: String?,
        caption: String?,
        bgColorHex: String,
        textStyle: String,
        textAlignment: String,
        privacyType: String,
        privacyTargetIds: String
    ) -> Unit,
    onSavePrivacySettings: (privacyType: String, excludedIds: String, includedIds: String) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current

    // Palette of vibrant, modern status background colors
    val colorHexes = listOf(
        "#075E54", // Classic Teal
        "#128C7E", // Emerald
        "#25D366", // WhatsApp Green
        "#34B7F1", // Sky Blue
        "#1E88E5", // Deep Blue
        "#5E35B1", // Deep Purple
        "#8E24AA", // Violet
        "#D81B60", // Rose
        "#E53935", // Crimson
        "#FB8C00", // Orange
        "#546E7A"  // Slate Blue
    )

    var selectedColorIndex by remember { mutableIntStateOf(0) }
    var textInput by remember { mutableStateOf("") }
    var captionInput by remember { mutableStateOf("") }
    var mediaUri by remember { mutableStateOf<String?>(null) }
    var mediaType by remember { mutableStateOf("TEXT") } // "TEXT", "IMAGE", "VIDEO"

    // Text custom styling
    var textStyleIndex by remember { mutableIntStateOf(0) } // 0: NORMAL, 1: BOLD, 2: SERIF, 3: MONOSPACE
    val textStyles = listOf("NORMAL", "BOLD", "SERIF", "MONOSPACE")

    var textAlignIndex by remember { mutableIntStateOf(0) } // 0: CENTER, 1: START, 2: END
    val alignments = listOf("CENTER", "START", "END")

    // Privacy
    var privacyType by remember {
        mutableStateOf(currentSettings?.statusPrivacy ?: "MY_CONTACTS")
    }
    var privacyExcludedIds by remember {
        mutableStateOf(currentSettings?.statusPrivacyExcludedIds ?: "")
    }
    var privacyIncludedIds by remember {
        mutableStateOf(currentSettings?.statusPrivacyIncludedIds ?: "")
    }

    var showPrivacyDialog by remember { mutableStateOf(false) }
    var showDiscardDialog by remember { mutableStateOf(false) }
    var tempCameraUri by remember { mutableStateOf<Uri?>(null) }

    // Intercept hardware back button to show discard dialog if modified
    BackHandler {
        if (textInput.isNotBlank() || mediaUri != null || captionInput.isNotBlank()) {
            showDiscardDialog = true
        } else {
            onDismiss()
        }
    }

    // Media pickers
    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            mediaUri = uri.toString()
            mediaType = "IMAGE"
        }
    }

    val videoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            mediaUri = uri.toString()
            mediaType = "VIDEO"
        }
    }

    // Camera photo capture
    val cameraPhotoLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        if (success && tempCameraUri != null) {
            mediaUri = tempCameraUri.toString()
            mediaType = "IMAGE"
        }
    }

    // Camera video recording
    val cameraVideoLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CaptureVideo()
    ) { success ->
        if (success && tempCameraUri != null) {
            mediaUri = tempCameraUri.toString()
            mediaType = "VIDEO"
        }
    }

    fun launchCameraPhoto() {
        try {
            val file = File(context.cacheDir, "photo_${System.currentTimeMillis()}.jpg")
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            tempCameraUri = uri
            cameraPhotoLauncher.launch(uri)
        } catch (e: Exception) {
            imagePickerLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        }
    }

    fun launchCameraVideo() {
        try {
            val file = File(context.cacheDir, "video_${System.currentTimeMillis()}.mp4")
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            tempCameraUri = uri
            cameraVideoLauncher.launch(uri)
        } catch (e: Exception) {
            videoPickerLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly))
        }
    }

    val currentBgColor = try {
        Color(android.graphics.Color.parseColor(colorHexes[selectedColorIndex]))
    } catch (e: Exception) {
        Color(0xFF075E54)
    }

    val textFontFamily = when (textStyles[textStyleIndex]) {
        "SERIF" -> FontFamily.Serif
        "MONOSPACE" -> FontFamily.Monospace
        else -> FontFamily.Default
    }
    val textFontWeight = if (textStyles[textStyleIndex] == "BOLD") FontWeight.Bold else FontWeight.Normal
    val currentAlignment = when (alignments[textAlignIndex]) {
        "START" -> TextAlign.Start
        "END" -> TextAlign.End
        else -> TextAlign.Center
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(if (mediaUri == null) currentBgColor else Color.Black)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(16.dp)
        ) {
            // --- Top Bar: Cancel, Mode Controls, Privacy Pill ---
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                IconButton(
                    onClick = {
                        if (textInput.isNotBlank() || mediaUri != null || captionInput.isNotBlank()) {
                            showDiscardDialog = true
                        } else {
                            onDismiss()
                        }
                    }
                ) {
                    Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
                }

                // Privacy Indicator Pill
                Surface(
                    shape = CircleShape,
                    color = Color(0x55000000),
                    modifier = Modifier.clickable { showPrivacyDialog = true }
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = "Privacy",
                            tint = Color.White,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = when (privacyType) {
                                "CONTACTS_EXCEPT" -> AppStrings.get("my_contacts_except")
                                "ONLY_SHARE_WITH" -> AppStrings.get("only_share_with")
                                else -> AppStrings.get("my_contacts")
                            },
                            color = Color.White,
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                }

                // Top right tool actions
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (mediaUri == null) {
                        // Text styling buttons
                        IconButton(
                            onClick = {
                                textStyleIndex = (textStyleIndex + 1) % textStyles.size
                            }
                        ) {
                            Icon(Icons.Default.TextFields, contentDescription = "Font Style", tint = Color.White)
                        }

                        IconButton(
                            onClick = {
                                textAlignIndex = (textAlignIndex + 1) % alignments.size
                            }
                        ) {
                            Icon(
                                imageVector = when (alignments[textAlignIndex]) {
                                    "START" -> Icons.AutoMirrored.Filled.FormatAlignLeft
                                    "END" -> Icons.AutoMirrored.Filled.FormatAlignRight
                                    else -> Icons.Default.FormatAlignCenter
                                },
                                contentDescription = "Text Alignment",
                                tint = Color.White
                            )
                        }

                        IconButton(
                            onClick = {
                                selectedColorIndex = (selectedColorIndex + 1) % colorHexes.size
                            }
                        ) {
                            Icon(Icons.Default.Palette, contentDescription = "Color Palette", tint = Color.White)
                        }
                    } else {
                        // Discard media button
                        IconButton(
                            onClick = {
                                mediaUri = null
                                mediaType = "TEXT"
                                captionInput = ""
                            }
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = "Remove Media", tint = Color.White)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.weight(0.5f))

            // --- Main Preview / Input Content ---
            if (mediaUri == null) {
                // Text status input
                OutlinedTextField(
                    value = textInput,
                    onValueChange = { textInput = it },
                    placeholder = {
                        Text(
                            text = AppStrings.get("write_status_hint"),
                            color = Color.White.copy(alpha = 0.6f),
                            style = MaterialTheme.typography.headlineSmall.copy(textAlign = currentAlignment),
                            modifier = Modifier.fillMaxWidth()
                        )
                    },
                    textStyle = MaterialTheme.typography.headlineMedium.copy(
                        color = Color.White,
                        fontFamily = textFontFamily,
                        fontWeight = textFontWeight,
                        textAlign = currentAlignment,
                        fontSize = 28.sp,
                        lineHeight = 36.sp
                    ),
                    colors = OutlinedTextFieldDefaults.colors(
                        unfocusedBorderColor = Color.Transparent,
                        focusedBorderColor = Color.Transparent
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .wrapContentHeight()
                )
            } else {
                // Media preview (Image or Video)
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(4f)
                        .clip(RoundedCornerShape(16.dp))
                ) {
                    if (mediaType == "IMAGE") {
                        AsyncImage(
                            model = mediaUri,
                            contentDescription = "Selected Photo",
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else if (mediaType == "VIDEO") {
                        AndroidView(
                            factory = { ctx ->
                                VideoView(ctx).apply {
                                    setVideoURI(Uri.parse(mediaUri))
                                    setOnPreparedListener { mp ->
                                        mp.isLooping = true
                                        start()
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Caption Field for media
                OutlinedTextField(
                    value = captionInput,
                    onValueChange = { captionInput = it },
                    placeholder = {
                        Text(
                            text = AppStrings.get("caption"),
                            color = Color.White.copy(alpha = 0.6f)
                        )
                    },
                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = Color.White),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = Color.White.copy(alpha = 0.5f),
                        unfocusedBorderColor = Color.White.copy(alpha = 0.2f)
                    ),
                    shape = RoundedCornerShape(24.dp),
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Spacer(modifier = Modifier.weight(0.5f))

            // Color Palette Selector Row (if text mode)
            if (mediaUri == null) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(vertical = 8.dp)
                ) {
                    colorHexes.forEachIndexed { index, hex ->
                        val swatchColor = try {
                            Color(android.graphics.Color.parseColor(hex))
                        } catch (e: Exception) {
                            Color.Gray
                        }
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(swatchColor)
                                .border(
                                    width = if (index == selectedColorIndex) 3.dp else 1.dp,
                                    color = if (index == selectedColorIndex) Color.White else Color.White.copy(alpha = 0.3f),
                                    shape = CircleShape
                                )
                                .clickable { selectedColorIndex = index }
                        )
                    }
                }
            }

            // --- Bottom Bar: Media Pickers & Send FAB ---
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                // Media Buttons (Gallery Image, Camera Photo, Gallery Video, Camera Video)
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Pick Photo
                    IconButton(
                        onClick = {
                            imagePickerLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        }
                    ) {
                        Icon(Icons.Default.Image, contentDescription = "Pick Image", tint = Color.White)
                    }

                    // Camera Photo
                    IconButton(onClick = { launchCameraPhoto() }) {
                        Icon(Icons.Default.PhotoCamera, contentDescription = "Camera Photo", tint = Color.White)
                    }

                    // Pick Video
                    IconButton(
                        onClick = {
                            videoPickerLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)
                            )
                        }
                    ) {
                        Icon(Icons.Default.VideoLibrary, contentDescription = "Pick Video", tint = Color.White)
                    }

                    // Camera Video
                    IconButton(onClick = { launchCameraVideo() }) {
                        Icon(Icons.Default.Videocam, contentDescription = "Camera Video", tint = Color.White)
                    }
                }

                // Send / Post FAB
                val canPost = (mediaUri != null) || textInput.isNotBlank()
                FloatingActionButton(
                    onClick = {
                        if (!canPost) return@FloatingActionButton
                        val targetIds = if (privacyType == "CONTACTS_EXCEPT") {
                            privacyExcludedIds
                        } else if (privacyType == "ONLY_SHARE_WITH") {
                            privacyIncludedIds
                        } else ""

                        if (mediaUri != null) {
                            onPostStatus(
                                mediaType,
                                null,
                                mediaUri,
                                captionInput.trim().ifEmpty { null },
                                colorHexes[selectedColorIndex],
                                textStyles[textStyleIndex],
                                alignments[textAlignIndex],
                                privacyType,
                                targetIds
                            )
                        } else {
                            onPostStatus(
                                "TEXT",
                                textInput.trim(),
                                null,
                                null,
                                colorHexes[selectedColorIndex],
                                textStyles[textStyleIndex],
                                alignments[textAlignIndex],
                                privacyType,
                                targetIds
                            )
                        }
                    },
                    containerColor = if (canPost) Color(0xFF25D366) else Color.Gray,
                    contentColor = Color.White
                ) {
                    Icon(Icons.Default.Send, contentDescription = "Post Status")
                }
            }
        }
    }

    // Status Privacy Dialog
    if (showPrivacyDialog) {
        StatusPrivacyDialog(
            currentSettings = currentSettings?.copy(
                statusPrivacy = privacyType,
                statusPrivacyExcludedIds = privacyExcludedIds,
                statusPrivacyIncludedIds = privacyIncludedIds
            ),
            contacts = contacts,
            allUsers = allUsers,
            onSavePrivacy = { newType, excluded, included ->
                privacyType = newType
                privacyExcludedIds = excluded
                privacyIncludedIds = included
                onSavePrivacySettings(newType, excluded, included)
                showPrivacyDialog = false
            },
            onDismiss = { showPrivacyDialog = false }
        )
    }

    // Discard Confirmation Dialog
    if (showDiscardDialog) {
        AlertDialog(
            onDismissRequest = { showDiscardDialog = false },
            title = { Text(AppStrings.get("discard_status")) },
            text = { Text(AppStrings.get("confirm_discard_status")) },
            confirmButton = {
                Button(
                    onClick = {
                        showDiscardDialog = false
                        onDismiss()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text(AppStrings.get("discard"))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDiscardDialog = false }) {
                    Text(AppStrings.get("cancel"))
                }
            }
        )
    }
}
