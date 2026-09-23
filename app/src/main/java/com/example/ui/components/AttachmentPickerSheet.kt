package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AttachmentPickerSheet(
    onDismiss: () -> Unit,
    onCameraClick: () -> Unit,
    onGalleryClick: () -> Unit,
    onVideoClick: () -> Unit,
    onDocumentClick: () -> Unit,
    onAudioClick: () -> Unit,
    onLocationClick: () -> Unit,
    onContactClick: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = { BottomSheetDefaults.DragHandle() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 16.dp)
        ) {
            Text(
                text = "إرفاق محتوى / Attach Content",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                modifier = Modifier.padding(bottom = 20.dp)
            )

            // Row 1: Camera, Gallery, Video, Document
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceAround
            ) {
                AttachmentOptionItem(
                    icon = Icons.Default.PhotoCamera,
                    label = "الكاميرا",
                    bgColor = Color(0xFFE91E63),
                    onClick = {
                        onDismiss()
                        onCameraClick()
                    }
                )
                AttachmentOptionItem(
                    icon = Icons.Default.Image,
                    label = "الصور",
                    bgColor = Color(0xFF9C27B0),
                    onClick = {
                        onDismiss()
                        onGalleryClick()
                    }
                )
                AttachmentOptionItem(
                    icon = Icons.Default.Videocam,
                    label = "فيديو",
                    bgColor = Color(0xFF673AB7),
                    onClick = {
                        onDismiss()
                        onVideoClick()
                    }
                )
                AttachmentOptionItem(
                    icon = Icons.Default.InsertDriveFile,
                    label = "مستند",
                    bgColor = Color(0xFF2196F3),
                    onClick = {
                        onDismiss()
                        onDocumentClick()
                    }
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Row 2: Audio, Location, Contact
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceAround
            ) {
                AttachmentOptionItem(
                    icon = Icons.Default.Headphones,
                    label = "صوت",
                    bgColor = Color(0xFFFF9800),
                    onClick = {
                        onDismiss()
                        onAudioClick()
                    }
                )
                AttachmentOptionItem(
                    icon = Icons.Default.LocationOn,
                    label = "الموقع",
                    bgColor = Color(0xFF4CAF50),
                    onClick = {
                        onDismiss()
                        onLocationClick()
                    }
                )
                AttachmentOptionItem(
                    icon = Icons.Default.Person,
                    label = "جهة اتصال",
                    bgColor = Color(0xFF009688),
                    onClick = {
                        onDismiss()
                        onContactClick()
                    }
                )
                // Spacer item to balance grid
                Spacer(modifier = Modifier.width(60.dp))
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun AttachmentOptionItem(
    icon: ImageVector,
    label: String,
    bgColor: Color,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(72.dp)
            .clickable(onClick = onClick)
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(56.dp)
                .clip(CircleShape)
                .background(bgColor)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = Color.White,
                modifier = Modifier.size(28.dp)
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = label,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}
