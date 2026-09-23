package com.example.ui.screens.call

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.local.entities.CallLogEntity
import com.example.data.local.entities.UserEntity
import com.example.ui.components.AvatarImage
import com.example.utils.TimeUtils

@Composable
fun CallHistoryScreen(
    callLogs: List<CallLogEntity>,
    allUsers: List<UserEntity>,
    currentUser: UserEntity,
    onStartCall: (targetUserId: String, isVideo: Boolean) -> Unit
) {
    val userMap = remember(allUsers) { allUsers.associateBy { it.userId } }
    var showNewCallDialog by remember { mutableStateOf(false) }
    val availableContacts = remember(allUsers, currentUser) {
        allUsers.filter { it.userId != currentUser.userId }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        if (callLogs.isEmpty()) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp)
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.Call,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                        modifier = Modifier.size(64.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "سجل المكالمات فارغ",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "جميع المكالمات الصوتية والمرئية المشفرة ستظهر هنا.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = { showNewCallDialog = true },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF25D366))
                    ) {
                        Icon(imageVector = Icons.Default.Call, contentDescription = null, tint = Color.White)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("بدء مكالمة جديدة", color = Color.White)
                    }
                }
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                items(callLogs) { log ->
                    val isOutgoing = log.callerUserId == currentUser.userId
                    val otherUserId = if (isOutgoing) log.receiverUserId else log.callerUserId
                    val targetUser = userMap[otherUserId]
                    val isMissed = log.status == "MISSED" || (log.status == "REJECTED" && !isOutgoing)

                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(14.dp)
                        ) {
                            AvatarImage(
                                uri = targetUser?.profilePicUri,
                                name = targetUser?.username ?: "مستخدم",
                                size = 48.dp
                            )

                            Spacer(modifier = Modifier.width(14.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = targetUser?.username ?: "جهة اتصال",
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                                    color = if (isMissed) Color(0xFFEF4444) else MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = if (isOutgoing) Icons.Default.CallMade else Icons.Default.CallReceived,
                                        contentDescription = null,
                                        tint = if (isMissed) Color(0xFFEF4444) else Color(0xFF22C55E),
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Icon(
                                        imageVector = if (log.isVideo) Icons.Default.Videocam else Icons.Default.Call,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    val statusDesc = when (log.status) {
                                        "MISSED" -> "مكالمة فائتة"
                                        "REJECTED" -> "مكالمة مرفوضة"
                                        "BUSY" -> "الخط مشغول"
                                        else -> if (log.durationSec > 0) TimeUtils.formatDuration(log.durationSec) else "مكتملة"
                                    }
                                    Text(
                                        text = "${TimeUtils.formatDateOrTime(log.timestamp)} • $statusDesc",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            // Call back button
                            IconButton(
                                onClick = { onStartCall(otherUserId, log.isVideo) },
                                modifier = Modifier.size(42.dp)
                            ) {
                                Icon(
                                    imageVector = if (log.isVideo) Icons.Default.Videocam else Icons.Default.Call,
                                    contentDescription = "معاودة الاتصال",
                                    tint = Color(0xFF25D366),
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        // WhatsApp style Floating Action Button for initiating calls
        FloatingActionButton(
            onClick = { showNewCallDialog = true },
            containerColor = Color(0xFF25D366),
            contentColor = Color.White,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(24.dp)
        ) {
            Icon(imageVector = Icons.Default.Call, contentDescription = "بدء مكالمة جديدة")
        }

        // Dialog to select contact and call type (Voice or Video)
        if (showNewCallDialog) {
            AlertDialog(
                onDismissRequest = { showNewCallDialog = false },
                title = {
                    Text(
                        text = "بدء مكالمة جديدة",
                        fontWeight = FontWeight.Bold
                    )
                },
                text = {
                    if (availableContacts.isEmpty()) {
                        Text(
                            text = "لا توجد جهات اتصال متاحة حالياً. أضف جهات اتصال لبدء مكالمات مشفرة معهم.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 350.dp)
                        ) {
                            items(availableContacts) { contact ->
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.padding(10.dp)
                                    ) {
                                        AvatarImage(
                                            uri = contact.profilePicUri,
                                            name = contact.username,
                                            size = 40.dp
                                        )
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = contact.username,
                                                fontWeight = FontWeight.SemiBold,
                                                style = MaterialTheme.typography.bodyMedium
                                            )
                                            Text(
                                                text = "ID: ${contact.customId}",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        IconButton(
                                            onClick = {
                                                showNewCallDialog = false
                                                onStartCall(contact.userId, false)
                                            }
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Call,
                                                contentDescription = "مكالمة صوتية",
                                                tint = Color(0xFF25D366)
                                            )
                                        }
                                        IconButton(
                                            onClick = {
                                                showNewCallDialog = false
                                                onStartCall(contact.userId, true)
                                            }
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Videocam,
                                                contentDescription = "مكالمة مرئية",
                                                tint = Color(0xFF25D366)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                },
                confirmButton = {},
                dismissButton = {
                    TextButton(onClick = { showNewCallDialog = false }) {
                        Text("إغلاق")
                    }
                }
            )
        }
    }
}
