package com.example.ui.screens.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import com.example.data.local.entities.ChatEntity
import com.example.data.local.entities.ChatMemberEntity
import com.example.data.local.entities.MessageEntity
import com.example.data.local.entities.UserEntity
import com.example.data.repository.ChatRepository
import com.example.ui.components.AvatarImage
import com.example.utils.AppStrings
import com.example.utils.TimeUtils

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    currentUser: UserEntity,
    chats: List<ChatEntity>,
    allUsers: List<UserEntity>,
    chatMembers: Map<String, List<ChatMemberEntity>> = emptyMap(),
    currentTab: Int,
    onTabSelected: (Int) -> Unit,
    onOpenChat: (String) -> Unit,
    onCreateGroupClick: () -> Unit,
    onJoinGroupClick: (String) -> Unit = {},
    onOpenAccountSwitcher: () -> Unit,
    repository: ChatRepository? = null,
    statusContent: @Composable () -> Unit,
    callsContent: @Composable () -> Unit,
    contactsContent: @Composable () -> Unit,
    settingsContent: @Composable () -> Unit
) {
    val userMap = remember(allUsers) { allUsers.associateBy { it.userId } }
    var searchQuery by remember { mutableStateOf("") }
    var showJoinGroupDialog by remember { mutableStateOf(false) }
    var inviteInput by remember { mutableStateOf("") }

    // Search messages across chats for local content filtering
    val matchingMessages by produceState<List<MessageEntity>>(initialValue = emptyList(), key1 = searchQuery) {
        val trimmed = searchQuery.trim()
        if (trimmed.isBlank() || repository == null) {
            value = emptyList()
        } else {
            repository.searchMessagesAcrossChats(trimmed).collect { list ->
                value = list
            }
        }
    }

    val matchingChatIds = remember(matchingMessages) {
        matchingMessages.map { it.chatId }.toSet()
    }

    val matchingMessageSnippets = remember(matchingMessages) {
        matchingMessages.associate { it.chatId to (if (it.content.isNotBlank()) it.content else it.caption ?: "مرفق") }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = AppStrings.get("app_name"),
                            style = MaterialTheme.typography.titleLarge
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Icon(
                            imageVector = Icons.Default.CloudDone,
                            contentDescription = "Cloud Connected",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                },
                actions = {
                    IconButton(onClick = onCreateGroupClick) {
                        Icon(imageVector = Icons.Default.GroupAdd, contentDescription = "New Group")
                    }
                    IconButton(onClick = { showJoinGroupDialog = true }) {
                        Icon(imageVector = Icons.Default.Link, contentDescription = "Join Group by Link")
                    }
                    IconButton(onClick = onOpenAccountSwitcher) {
                        Icon(imageVector = Icons.Default.SwitchAccount, contentDescription = "Switch Account")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = currentTab == 0,
                    onClick = { onTabSelected(0) },
                    icon = { Icon(Icons.Default.Chat, contentDescription = AppStrings.get("chats")) },
                    label = { Text(AppStrings.get("chats")) }
                )
                NavigationBarItem(
                    selected = currentTab == 1,
                    onClick = { onTabSelected(1) },
                    icon = { Icon(Icons.Default.DonutLarge, contentDescription = AppStrings.get("status")) },
                    label = { Text(AppStrings.get("status")) }
                )
                NavigationBarItem(
                    selected = currentTab == 2,
                    onClick = { onTabSelected(2) },
                    icon = { Icon(Icons.Default.Call, contentDescription = AppStrings.get("calls")) },
                    label = { Text(AppStrings.get("calls")) }
                )
                NavigationBarItem(
                    selected = currentTab == 3,
                    onClick = { onTabSelected(3) },
                    icon = { Icon(Icons.Default.Contacts, contentDescription = AppStrings.get("contacts")) },
                    label = { Text(AppStrings.get("contacts")) }
                )
                NavigationBarItem(
                    selected = currentTab == 4,
                    onClick = { onTabSelected(4) },
                    icon = { Icon(Icons.Default.Settings, contentDescription = AppStrings.get("settings")) },
                    label = { Text(AppStrings.get("settings")) }
                )
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (currentTab) {
                0 -> {
                    // Chats Tab
                    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            placeholder = { Text(AppStrings.get("search")) },
                            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        val filteredChats = remember(chats, searchQuery, chatMembers, userMap, currentUser, matchingChatIds) {
                            if (searchQuery.isBlank()) chats
                            else chats.filter { chat ->
                                val matchesMessageContent = chat.chatId in matchingChatIds
                                if (chat.type == "GROUP") {
                                    matchesMessageContent || (chat.groupName?.contains(searchQuery, ignoreCase = true) == true)
                                } else {
                                    val members = chatMembers[chat.chatId]
                                    val otherMemberId = members?.firstOrNull { it.userId != currentUser.userId }?.userId
                                    val otherUser = if (otherMemberId != null) userMap[otherMemberId] else null
                                    matchesMessageContent ||
                                            (otherUser?.username?.contains(searchQuery, ignoreCase = true) == true) ||
                                            (otherUser?.customId?.contains(searchQuery, ignoreCase = true) == true)
                                }
                            }
                        }

                        if (filteredChats.isEmpty()) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier.fillMaxWidth().weight(1f)
                            ) {
                                Text(
                                    text = if (searchQuery.isBlank()) "لا توجد محادثات نشطة.\nابدأ محادثة جديدة عبر تبويب جهات الاتصال."
                                           else "لا توجد نتائج تطابق \"$searchQuery\" في الأسماء أو محتوى الرسائل.",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center
                                )
                            }
                        } else {
                            LazyColumn(
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.fillMaxSize()
                            ) {
                                items(filteredChats) { chat ->
                                    val isGroup = chat.type == "GROUP"
                                    val title = if (isGroup) chat.groupName ?: "Group" else {
                                        val members = chatMembers[chat.chatId]
                                        val otherMemberId = members?.firstOrNull { it.userId != currentUser.userId }?.userId
                                        val otherUser = if (otherMemberId != null) userMap[otherMemberId] else null
                                        otherUser?.username ?: "User"
                                    }
                                    val icon = if (isGroup) chat.groupIconUri else {
                                        val members = chatMembers[chat.chatId]
                                        val otherMemberId = members?.firstOrNull { it.userId != currentUser.userId }?.userId
                                        val otherUser = if (otherMemberId != null) userMap[otherMemberId] else null
                                        otherUser?.profilePicUri
                                    }
                                    val matchingSnippet = if (searchQuery.isNotBlank()) matchingMessageSnippets[chat.chatId] else null

                                    Surface(
                                        shape = RoundedCornerShape(12.dp),
                                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable { onOpenChat(chat.chatId) }
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.padding(12.dp)
                                        ) {
                                            AvatarImage(
                                                uri = icon,
                                                name = title,
                                                size = 52.dp,
                                                isGroup = isGroup
                                            )

                                            Spacer(modifier = Modifier.width(12.dp))

                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = title,
                                                    style = MaterialTheme.typography.titleMedium
                                                )
                                                if (matchingSnippet != null) {
                                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                                        Icon(
                                                            imageVector = Icons.Default.ChatBubbleOutline,
                                                            contentDescription = null,
                                                            tint = MaterialTheme.colorScheme.primary,
                                                            modifier = Modifier.size(13.dp)
                                                        )
                                                        Spacer(modifier = Modifier.width(4.dp))
                                                        Text(
                                                            text = matchingSnippet,
                                                            style = MaterialTheme.typography.bodySmall,
                                                            color = MaterialTheme.colorScheme.primary,
                                                            maxLines = 1,
                                                            overflow = TextOverflow.Ellipsis
                                                        )
                                                    }
                                                } else {
                                                    Text(
                                                        text = if (isGroup) "مجموعة" else "محادثة فردية",
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                }
                                            }

                                            Text(
                                                text = TimeUtils.formatDateOrTime(chat.lastMessageTimestamp),
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                1 -> statusContent()
                2 -> callsContent()
                3 -> contactsContent()
                4 -> settingsContent()
            }
        }
    }

    if (showJoinGroupDialog) {
        AlertDialog(
            onDismissRequest = {
                showJoinGroupDialog = false
                inviteInput = ""
            },
            title = { Text("الانضمام لمجموعة عبر الرابط") },
            text = {
                Column {
                    Text(
                        text = "الصق رابط الدعوة أو رمز المجموعة أدناه:",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = inviteInput,
                        onValueChange = { inviteInput = it },
                        placeholder = { Text("chatconnect://invite/... أو الرمز") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val cleaned = inviteInput.trim()
                        val token = if (cleaned.contains("chatconnect://invite/")) {
                            cleaned.substringAfter("chatconnect://invite/").trim()
                        } else {
                            cleaned
                        }
                        if (token.isNotBlank()) {
                            onJoinGroupClick(token)
                            showJoinGroupDialog = false
                            inviteInput = ""
                        }
                    },
                    enabled = inviteInput.isNotBlank()
                ) {
                    Text("انضمام")
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showJoinGroupDialog = false
                    inviteInput = ""
                }) {
                    Text("إلغاء")
                }
            }
        )
    }
}
