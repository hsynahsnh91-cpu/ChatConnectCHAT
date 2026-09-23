package com.example.ui.screens.contacts

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.entities.ContactEntity
import com.example.data.local.entities.UserEntity
import com.example.ui.components.AvatarImage
import com.example.utils.AppStrings

@Composable
fun ContactsScreen(
    contacts: List<ContactEntity>,
    allUsers: List<UserEntity>,
    onOpenChat: (String) -> Unit,
    onAudioCall: (String) -> Unit,
    onVideoCall: (String) -> Unit,
    onAddContactClick: () -> Unit,
    onDeleteContact: (String) -> Unit,
    onBlockUser: (String) -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    var selectedContactForAction by remember { mutableStateOf<ContactEntity?>(null) }
    val focusManager = LocalFocusManager.current

    val userMap = remember(allUsers) { allUsers.associateBy { it.userId } }

    val filteredContacts = remember(contacts, searchQuery, userMap) {
        if (searchQuery.isBlank()) contacts
        else contacts.filter { contact ->
            val user = userMap[contact.contactUserId]
            contact.nickname.contains(searchQuery, ignoreCase = true) ||
                    (user?.customId?.contains(searchQuery) == true)
        }
    }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    focusManager.clearFocus()
                    onAddContactClick()
                },
                containerColor = MaterialTheme.colorScheme.primary
            ) {
                Icon(
                    imageVector = Icons.Default.PersonAdd,
                    contentDescription = AppStrings.get("add_contact"),
                    tint = MaterialTheme.colorScheme.onPrimary
                )
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp)
        ) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text(AppStrings.get("search_by_id")) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(16.dp))

            if (filteredContacts.isEmpty()) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(24.dp)
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                            modifier = Modifier.size(72.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Contacts,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(36.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "لا توجد جهات اتصال حتى الآن",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "اضغط على زر + بالأسفل لإضافة جهة اتصال عبر المعرف (ID) المكون من 10 أرقام.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(filteredContacts, key = { it.contactUserId }) { contact ->
                        val targetUser = userMap[contact.contactUserId]
                        Card(
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    focusManager.clearFocus()
                                    selectedContactForAction = contact
                                }
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(14.dp)
                            ) {
                                AvatarImage(
                                    uri = targetUser?.profilePicUri,
                                    name = contact.nickname,
                                    size = 52.dp,
                                    isOnline = targetUser?.isOnline == true,
                                    showOnlineBadge = true
                                )

                                Spacer(modifier = Modifier.width(14.dp))

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = contact.nickname,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = "ID: ${targetUser?.customId ?: "---"}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.Medium
                                    )
                                }

                                // Quick Call & Chat Action Icons on Card
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    IconButton(
                                        onClick = { onAudioCall(contact.contactUserId) },
                                        modifier = Modifier
                                            .size(40.dp)
                                            .clip(CircleShape)
                                            .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f))
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Call,
                                            contentDescription = "مكالمة صوتية",
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(8.dp))
                                    IconButton(
                                        onClick = { onVideoCall(contact.contactUserId) },
                                        modifier = Modifier
                                            .size(40.dp)
                                            .clip(CircleShape)
                                            .background(MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f))
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Videocam,
                                            contentDescription = "مكالمة فيديو",
                                            tint = MaterialTheme.colorScheme.secondary,
                                            modifier = Modifier.size(20.dp)
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

    // Modal Action Sheet / Dialog upon clicking on any contact:
    // Shows Avatar Picture, Name, ID, and buttons: "محادثة" and "مكالمة"
    if (selectedContactForAction != null) {
        val contact = selectedContactForAction!!
        val targetUser = userMap[contact.contactUserId]

        AlertDialog(
            onDismissRequest = { selectedContactForAction = null },
            shape = RoundedCornerShape(24.dp),
            title = null,
            text = {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                ) {
                    // Contact's Large Profile Picture / Avatar Icon
                    AvatarImage(
                        uri = targetUser?.profilePicUri,
                        name = contact.nickname,
                        size = 96.dp,
                        isOnline = targetUser?.isOnline == true,
                        showOnlineBadge = true
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    Text(
                        text = contact.nickname,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "معرف المستخدم (ID): ${targetUser?.customId ?: "---"}",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }

                    if (!targetUser?.bio.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = targetUser!!.bio!!,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // E2EE Encryption Badge
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0xFF25D366).copy(alpha = 0.12f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center,
                            modifier = Modifier.padding(vertical = 6.dp, horizontal = 10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Lock,
                                contentDescription = null,
                                tint = Color(0xFF1E8E3E),
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "محادثات ومكالمات مشفرة بين الطرفين (E2EE)",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color(0xFF1E8E3E),
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    // The requested Action Buttons: "محادثة" - "مكالمة"
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        // Button: "محادثة" (Chat)
                        Button(
                            onClick = {
                                val cId = contact.contactUserId
                                selectedContactForAction = null
                                onOpenChat(cId)
                            },
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                            modifier = Modifier
                                .weight(1f)
                                .height(56.dp)
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.Chat,
                                    contentDescription = "محادثة",
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "محادثة",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        // Button: "مكالمة صوتية" (Voice Call)
                        FilledTonalButton(
                            onClick = {
                                val cId = contact.contactUserId
                                selectedContactForAction = null
                                onAudioCall(cId)
                            },
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(56.dp)
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Call,
                                    contentDescription = "مكالمة صوتية",
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "مكالمة",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        // Button: "مكالمة فيديو" (Video Call)
                        FilledTonalButton(
                            onClick = {
                                val cId = contact.contactUserId
                                selectedContactForAction = null
                                onVideoCall(cId)
                            },
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(56.dp)
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Videocam,
                                    contentDescription = "مكالمة فيديو",
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "فيديو",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { selectedContactForAction = null }) {
                    Text("إغلاق")
                }
            }
        )
    }
}

