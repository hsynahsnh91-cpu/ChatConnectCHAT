package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage

@Composable
fun AvatarImage(
    uri: String?,
    name: String,
    size: Dp = 48.dp,
    isOnline: Boolean = false,
    showOnlineBadge: Boolean = false,
    isGroup: Boolean = false,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.size(size),
        contentAlignment = Alignment.Center
    ) {
        if (!uri.isNull_or_blank()) {
            AsyncImage(
                model = uri,
                contentDescription = name,
                modifier = Modifier
                    .size(size)
                    .clip(CircleShape),
                contentScale = ContentScale.Crop
            )
        } else {
            val initial = name.firstOrNull()?.uppercase() ?: "?"
            val bgColor = if (isGroup) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.primaryContainer
            val textColor = if (isGroup) MaterialTheme.colorScheme.onTertiaryContainer else MaterialTheme.colorScheme.onPrimaryContainer

            Box(
                modifier = Modifier
                    .size(size)
                    .clip(CircleShape)
                    .background(bgColor),
                contentAlignment = Alignment.Center
            ) {
                if (isGroup && name.isBlank()) {
                    Icon(
                        imageVector = Icons.Default.Group,
                        contentDescription = "Group",
                        tint = textColor,
                        modifier = Modifier.size(size * 0.5f)
                    )
                } else if (name.isBlank()) {
                    Icon(
                        imageVector = Icons.Default.Person,
                        contentDescription = "Person",
                        tint = textColor,
                        modifier = Modifier.size(size * 0.5f)
                    )
                } else {
                    Text(
                        text = initial,
                        fontSize = (size.value * 0.45f).sp,
                        fontWeight = FontWeight.Bold,
                        color = textColor
                    )
                }
            }
        }

        if (showOnlineBadge) {
            val badgeColor = if (isOnline) Color(0xFF25D366) else Color.Gray
            Box(
                modifier = Modifier
                    .size(size * 0.28f)
                    .align(Alignment.BottomEnd)
                    .clip(CircleShape)
                    .background(badgeColor)
                    .border(2.dp, MaterialTheme.colorScheme.surface, CircleShape)
            )
        }
    }
}

private fun String?.isNull_or_blank(): Boolean = this == null || this.trim().isEmpty()
