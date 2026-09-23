package com.example.ui.screens.status

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.RemoveRedEye
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.data.local.entities.StatusViewEntity
import com.example.ui.components.AvatarImage
import com.example.utils.AppStrings
import com.example.utils.TimeUtils

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatusViewersSheet(
    views: List<StatusViewEntity>,
    onDeleteStatus: () -> Unit,
    onDismiss: () -> Unit
) {
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.RemoveRedEye,
                        contentDescription = "Viewers",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "${AppStrings.get("viewers_list")} (${views.size})",
                        style = MaterialTheme.typography.titleLarge
                    )
                }

                IconButton(onClick = { showDeleteConfirmDialog = true }) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = AppStrings.get("delete_status"),
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (views.isEmpty()) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(160.dp)
                ) {
                    Text(
                        text = AppStrings.get("no_views_yet"),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 360.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(views, key = { it.viewerUserId + it.viewedAt }) { viewItem ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            AvatarImage(
                                uri = viewItem.viewerProfilePic,
                                name = viewItem.viewerName.ifBlank { "User" },
                                size = 44.dp
                            )
                            Spacer(modifier = Modifier.width(14.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = viewItem.viewerName.ifBlank { "User" },
                                    style = MaterialTheme.typography.bodyLarge
                                )
                                Text(
                                    text = TimeUtils.formatDateOrTime(viewItem.viewedAt),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))
        }
    }

    if (showDeleteConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmDialog = false },
            title = { Text(AppStrings.get("delete_status")) },
            text = { Text(AppStrings.get("confirm_delete_status")) },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteConfirmDialog = false
                        onDeleteStatus()
                        onDismiss()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text(AppStrings.get("delete"))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmDialog = false }) {
                    Text(AppStrings.get("cancel"))
                }
            }
        )
    }
}
