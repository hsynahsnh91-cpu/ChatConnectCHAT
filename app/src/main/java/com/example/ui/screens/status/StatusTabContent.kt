package com.example.ui.screens.status

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.data.local.entities.ContactEntity
import com.example.data.local.entities.StatusEntity
import com.example.data.local.entities.UserEntity
import com.example.data.local.entities.UserSettingsEntity
import com.example.utils.AppStrings
import com.example.utils.TimeUtils

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatusTabContent(
    currentUser: UserEntity,
    allStatuses: List<StatusEntity>,
    allUsers: List<UserEntity>,
    contacts: List<ContactEntity>,
    userSettings: UserSettingsEntity?,
    mutedUserIds: List<String>,
    viewedStatusIds: List<String>,
    blockedUserIds: List<String>,
    onCreateStatusClick: () -> Unit,
    onOpenStatusViewer: (userId: String, isOwner: Boolean) -> Unit,
    onMuteToggle: (userId: String) -> Unit,
    onSavePrivacySettings: (privacyType: String, excludedIds: String, includedIds: String) -> Unit
) {
    var showPrivacyDialog by remember { mutableStateOf(false) }
    var isMutedSectionExpanded by remember { mutableStateOf(false) }

    val userMap = remember(allUsers) { allUsers.associateBy { it.userId } }
    val contactUserIds = remember(contacts) { contacts.map { it.contactUserId }.toSet() }

    // Filter active, non-deleted statuses that respect privacy & block rules
    val activeStatuses = remember(allStatuses, blockedUserIds, currentUser.userId) {
        val now = System.currentTimeMillis()
        allStatuses.filter { status ->
            if (status.isDeleted) return@filter false
            if (status.expiresAt <= now) return@filter false
            // Check block list
            if (blockedUserIds.contains(status.userId)) return@filter false

            // Privacy checks for other users' statuses
            if (status.userId != currentUser.userId) {
                when (status.privacyType) {
                    "CONTACTS_EXCEPT" -> {
                        val excluded = status.privacyTargetIds.split(",").map { it.trim() }
                        if (excluded.contains(currentUser.userId)) return@filter false
                    }
                    "ONLY_SHARE_WITH" -> {
                        val included = status.privacyTargetIds.split(",").map { it.trim() }
                        if (!included.contains(currentUser.userId)) return@filter false
                    }
                    else -> {
                        // "MY_CONTACTS" is default
                    }
                }
            }
            true
        }
    }

    val groupedStatuses = remember(activeStatuses) {
        activeStatuses.groupBy { it.userId }
    }

    val myStatuses = groupedStatuses[currentUser.userId] ?: emptyList()
    val otherUserStatuses = groupedStatuses.filterKeys { it != currentUser.userId }

    // Partition other users into:
    // 1. Muted updates
    // 2. Recent (unviewed) updates
    // 3. Viewed updates
    val mutedUpdates = remember(otherUserStatuses, mutedUserIds) {
        otherUserStatuses.filterKeys { mutedUserIds.contains(it) }
    }

    val nonMutedUpdates = remember(otherUserStatuses, mutedUserIds) {
        otherUserStatuses.filterKeys { !mutedUserIds.contains(it) }
    }

    val recentUpdates = remember(nonMutedUpdates, viewedStatusIds) {
        nonMutedUpdates.filter { (_, userStatuses) ->
            // If any status of this user is unviewed
            userStatuses.any { !viewedStatusIds.contains(it.statusId) }
        }
    }

    val viewedUpdates = remember(nonMutedUpdates, viewedStatusIds) {
        nonMutedUpdates.filter { (_, userStatuses) ->
            // All statuses of this user have been viewed
            userStatuses.isNotEmpty() && userStatuses.all { viewedStatusIds.contains(it.statusId) }
        }
    }

    Scaffold(
        floatingActionButton = {
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Secondary FAB for Text Status
                SmallFloatingActionButton(
                    onClick = onCreateStatusClick,
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                ) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = AppStrings.get("create_status")
                    )
                }

                // Primary FAB for Camera/Media Status
                FloatingActionButton(
                    onClick = onCreateStatusClick,
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ) {
                    Icon(
                        imageVector = Icons.Default.PhotoCamera,
                        contentDescription = AppStrings.get("create_status")
                    )
                }
            }
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Header with Status Privacy shortcut
            item {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp)
                ) {
                    Text(
                        text = AppStrings.get("status"),
                        style = MaterialTheme.typography.titleLarge
                    )

                    FilledTonalButton(
                        onClick = { showPrivacyDialog = true },
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                        modifier = Modifier.height(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Security,
                            contentDescription = "Privacy",
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = AppStrings.get("status_privacy"),
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                }
            }

            // --- My Status Card ---
            item {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            if (myStatuses.isNotEmpty()) {
                                onOpenStatusViewer(currentUser.userId, true)
                            } else {
                                onCreateStatusClick()
                            }
                        }
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(14.dp)
                    ) {
                        Box {
                            StatusRingAvatar(
                                uri = currentUser.profilePicUri,
                                name = currentUser.username,
                                statusCount = myStatuses.size,
                                isAllViewed = false,
                                avatarSize = 52.dp
                            )

                            if (myStatuses.isEmpty()) {
                                Box(
                                    modifier = Modifier
                                        .size(22.dp)
                                        .align(Alignment.BottomEnd)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.primary)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Add,
                                        contentDescription = "Add",
                                        tint = MaterialTheme.colorScheme.onPrimary,
                                        modifier = Modifier
                                            .size(16.dp)
                                            .align(Alignment.Center)
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.width(16.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = AppStrings.get("my_status"),
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                text = if (myStatuses.isNotEmpty()) {
                                    "${myStatuses.size} ${AppStrings.get("updates_count")} • ${TimeUtils.formatDateOrTime(myStatuses.first().createdAt)}"
                                } else {
                                    AppStrings.get("add_status")
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        if (myStatuses.isNotEmpty()) {
                            IconButton(onClick = onCreateStatusClick) {
                                Icon(
                                    imageVector = Icons.Default.AddCircleOutline,
                                    contentDescription = "Add another status",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
            }

            // --- Recent Updates Section ---
            item {
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = AppStrings.get("recent_updates"),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            if (recentUpdates.isEmpty() && viewedUpdates.isEmpty() && mutedUpdates.isEmpty()) {
                item {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 24.dp)
                    ) {
                        Text(
                            text = "لا توجد تحديثات حالات من جهات الاتصال حالياً.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else if (recentUpdates.isEmpty()) {
                item {
                    Text(
                        text = "لا توجد تحديثات جديدة غير مشاهدة.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                }
            } else {
                items(recentUpdates.keys.toList(), key = { "recent_$it" }) { userId ->
                    val user = userMap[userId]
                    val userStatusList = recentUpdates[userId] ?: emptyList()
                    val latest = userStatusList.firstOrNull()

                    StatusContactItem(
                        user = user,
                        statusList = userStatusList,
                        latestTimestamp = latest?.createdAt ?: System.currentTimeMillis(),
                        isAllViewed = false,
                        isMuted = false,
                        onClick = { onOpenStatusViewer(userId, false) },
                        onMuteToggle = { onMuteToggle(userId) }
                    )
                }
            }

            // --- Viewed Updates Section ---
            if (viewedUpdates.isNotEmpty()) {
                item {
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = AppStrings.get("viewed_updates"),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                items(viewedUpdates.keys.toList(), key = { "viewed_$it" }) { userId ->
                    val user = userMap[userId]
                    val userStatusList = viewedUpdates[userId] ?: emptyList()
                    val latest = userStatusList.firstOrNull()

                    StatusContactItem(
                        user = user,
                        statusList = userStatusList,
                        latestTimestamp = latest?.createdAt ?: System.currentTimeMillis(),
                        isAllViewed = true,
                        isMuted = false,
                        onClick = { onOpenStatusViewer(userId, false) },
                        onMuteToggle = { onMuteToggle(userId) }
                    )
                }
            }

            // --- Muted Updates Section ---
            if (mutedUpdates.isNotEmpty()) {
                item {
                    Spacer(modifier = Modifier.height(16.dp))
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { isMutedSectionExpanded = !isMutedSectionExpanded }
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                        ) {
                            Text(
                                text = "${AppStrings.get("muted_updates")} (${mutedUpdates.size})",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Icon(
                                imageVector = if (isMutedSectionExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                contentDescription = "Expand",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                if (isMutedSectionExpanded) {
                    items(mutedUpdates.keys.toList(), key = { "muted_$it" }) { userId ->
                        val user = userMap[userId]
                        val userStatusList = mutedUpdates[userId] ?: emptyList()
                        val latest = userStatusList.firstOrNull()

                        StatusContactItem(
                            user = user,
                            statusList = userStatusList,
                            latestTimestamp = latest?.createdAt ?: System.currentTimeMillis(),
                            isAllViewed = true,
                            isMuted = true,
                            onClick = { onOpenStatusViewer(userId, false) },
                            onMuteToggle = { onMuteToggle(userId) }
                        )
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(80.dp))
            }
        }
    }

    // Status Privacy Settings Dialog
    if (showPrivacyDialog) {
        StatusPrivacyDialog(
            currentSettings = userSettings,
            contacts = contacts,
            allUsers = allUsers,
            onSavePrivacy = { newPrivacy, excludedIds, includedIds ->
                onSavePrivacySettings(newPrivacy, excludedIds, includedIds)
                showPrivacyDialog = false
            },
            onDismiss = { showPrivacyDialog = false }
        )
    }
}

@Composable
private fun StatusContactItem(
    user: UserEntity?,
    statusList: List<StatusEntity>,
    latestTimestamp: Long,
    isAllViewed: Boolean,
    isMuted: Boolean,
    onClick: () -> Unit,
    onMuteToggle: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = if (isMuted) 0.2f else 0.4f),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(12.dp)
        ) {
            StatusRingAvatar(
                uri = user?.profilePicUri,
                name = user?.username ?: "User",
                statusCount = statusList.size,
                isAllViewed = isAllViewed || isMuted,
                avatarSize = 50.dp
            )

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = user?.username ?: "User",
                    style = MaterialTheme.typography.titleMedium,
                    color = if (isMuted) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f) else MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "${statusList.size} ${AppStrings.get("updates_count")} • ${TimeUtils.formatDateOrTime(latestTimestamp)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Box {
                IconButton(onClick = { showMenu = true }) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = "Options",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
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
                            Icon(
                                imageVector = if (isMuted) Icons.Default.VolumeUp else Icons.Default.VolumeOff,
                                contentDescription = null
                            )
                        },
                        onClick = {
                            showMenu = false
                            onMuteToggle()
                        }
                    )
                }
            }
        }
    }
}
