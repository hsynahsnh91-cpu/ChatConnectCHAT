package com.example

import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.data.local.entities.*
import com.example.data.model.SendMessagePayload
import com.example.data.repository.ChatRepository
import com.example.service.AudioRecorderPlayer
import com.example.service.CallStatus
import com.example.service.RealtimeCallDispatcher
import com.example.service.TypingStateDispatcher
import com.example.service.call.CallNotificationManager
import com.example.service.call.CallPermissionHandler
import com.example.ui.components.*
import com.example.ui.screens.auth.AuthScreen
import com.example.ui.screens.call.CallHistoryScreen
import com.example.ui.screens.chat.ChatDetailScreen
import com.example.ui.screens.contacts.AddContactDialog
import com.example.ui.screens.contacts.ContactsScreen
import com.example.ui.screens.group.CreateGroupScreen
import com.example.ui.screens.admin.AdminSpecialIdentifierDialog
import com.example.ui.screens.group.GroupDetailScreen
import com.example.ui.screens.home.HomeScreen
import com.example.ui.screens.settings.SettingsScreen
import com.example.ui.screens.status.CreateStatusScreen
import com.example.ui.screens.status.StatusTabContent
import com.example.ui.screens.status.StatusViewerScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.utils.AppStrings
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private lateinit var repository: ChatRepository
    private lateinit var audioRecorderPlayer: AudioRecorderPlayer

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setupLockScreenFlags()
        enableEdgeToEdge()

        repository = ChatRepository(applicationContext)
        audioRecorderPlayer = AudioRecorderPlayer(applicationContext)

        handleCallIntent(intent)
        handleDeepLink(intent)

        setContent {
            val currentUser by repository.currentUser.collectAsState()
            val allUsers by repository.allRegisteredUsers.collectAsState(initial = emptyList())
            val deviceLoggedInUsers by repository.deviceLoggedInUsers.collectAsState(initial = emptyList())
            val chats by repository.getMyChats().collectAsState(initial = emptyList())
            val myContacts by repository.getMyContacts().collectAsState(initial = emptyList())
            val allStatuses by repository.observeAllActiveStatuses().collectAsState(initial = emptyList())
            val mutedUserIds by repository.observeMutedUserIds().collectAsState(initial = emptyList())
            val viewedStatusIds by repository.observeViewedStatusIds().collectAsState(initial = emptyList())
            val blockedUsers by repository.observeBlockedUsers().collectAsState(initial = emptyList())
            val blockedUserIds = remember(blockedUsers) { blockedUsers.map { it.blockedUserId } }
            val callLogs by repository.observeCallLogs().collectAsState(initial = emptyList())
            val userSettings by repository.observeUserSettings().collectAsState(initial = null)
            val isFirestoreConnected by repository.isFirestoreConnected.collectAsState()
            val specialIdentifiers by repository.observeSpecialIdentifiers().collectAsState(initial = emptyList())

            val callState by RealtimeCallDispatcher.currentCallState.collectAsState()
            val typingMap by TypingStateDispatcher.typingMap.collectAsState()

            val isRecording by audioRecorderPlayer.isRecording.collectAsState()
            val isPlaying by audioRecorderPlayer.isPlaying.collectAsState()
            val currentPlayingUri by audioRecorderPlayer.currentPlayingUri.collectAsState()
            val playbackSpeed by audioRecorderPlayer.playbackSpeed.collectAsState()
            val playbackProgress by audioRecorderPlayer.playbackProgress.collectAsState()

            val scope = rememberCoroutineScope()

            val isDark = userSettings?.isDarkMode ?: isSystemInDarkTheme()

            var pendingCallAction by remember { mutableStateOf<(() -> Unit)?>(null) }
            var permissionRationaleText by remember { mutableStateOf<String?>(null) }
            var showSettingsRedirectDialog by remember { mutableStateOf(false) }

            val callPermissionLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.RequestMultiplePermissions()
            ) { perms ->
                val audioGranted = perms[CallPermissionHandler.PERMISSION_AUDIO] ?: false
                if (audioGranted) {
                    pendingCallAction?.invoke()
                } else {
                    val shouldShowRationale = CallPermissionHandler.shouldShowRationale(
                        this@MainActivity,
                        CallPermissionHandler.PERMISSION_AUDIO
                    )
                    if (shouldShowRationale) {
                        permissionRationaleText = CallPermissionHandler.getRationaleMessage(CallPermissionHandler.PERMISSION_AUDIO)
                    } else {
                        showSettingsRedirectDialog = true
                    }
                }
                pendingCallAction = null
            }

            fun requestCallPermissionsAndStart(isVideo: Boolean, onGranted: () -> Unit) {
                val missing = CallPermissionHandler.getMissingPermissions(this@MainActivity, isVideo)
                if (missing.isNotEmpty()) {
                    pendingCallAction = onGranted
                    callPermissionLauncher.launch(missing.toTypedArray())
                } else {
                    onGranted()
                }
            }

            MyApplicationTheme(darkTheme = isDark) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    if (currentUser == null) {
                        AuthScreen(
                            allUsers = deviceLoggedInUsers,
                            isFirestoreConnected = isFirestoreConnected,
                            onRegister = { username, password, picUri ->
                                scope.launch {
                                    val user = repository.registerUser(username, password, picUri)
                                    Toast.makeText(
                                        this@MainActivity,
                                        "تم إنشاء الحساب! المعرف الخاص بك: ${user.customId}",
                                        Toast.LENGTH_LONG
                                    ).show()
                                }
                            },
                            onLoginCustomId = { customId, password ->
                                scope.launch {
                                    val result = repository.loginUserByCustomId(customId, password)
                                    if (result.isSuccess) {
                                        val user = result.getOrNull()!!
                                        Toast.makeText(this@MainActivity, "مرحباً ${user.username}!", Toast.LENGTH_SHORT).show()
                                    } else {
                                        val err = result.exceptionOrNull()?.message ?: "خطأ في تسجيل الدخول"
                                        Toast.makeText(this@MainActivity, err, Toast.LENGTH_LONG).show()
                                    }
                                }
                            },
                            onQuickSwitch = { user, password ->
                                scope.launch {
                                    val result = repository.switchAccount(user, password)
                                    if (result.isFailure) {
                                        val err = result.exceptionOrNull()?.message ?: "كلمة المرور غير صحيحة"
                                        Toast.makeText(this@MainActivity, err, Toast.LENGTH_LONG).show()
                                    }
                                }
                            },
                            onGoogleSignIn = { onFinished ->
                                scope.launch {
                                    val result = repository.loginWithGoogle(this@MainActivity)
                                    if (result.isSuccess) {
                                        val user = result.getOrNull()!!
                                        Toast.makeText(this@MainActivity, "مرحباً ${user.username}! تم تسجيل الدخول بنجاح عبر Google وربط الحساب بـ Firebase", Toast.LENGTH_LONG).show()
                                        onFinished(true, null)
                                    } else {
                                        val err = result.exceptionOrNull()?.message ?: "فشل تسجيل الدخول عبر Google"
                                        Toast.makeText(this@MainActivity, err, Toast.LENGTH_LONG).show()
                                        onFinished(false, err)
                                    }
                                }
                            },
                            onAnonymousFirebaseSignIn = { customName ->
                                scope.launch {
                                    val result = repository.loginWithAnonymousFirebase(customName)
                                    if (result.isSuccess) {
                                        val user = result.getOrNull()!!
                                        Toast.makeText(this@MainActivity, "تم الدخول السريع عبر Firebase! ID: ${user.customId}", Toast.LENGTH_SHORT).show()
                                    } else {
                                        val err = result.exceptionOrNull()?.message ?: "فشل الدخول في Firebase"
                                        Toast.makeText(this@MainActivity, err, Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }
                        )
                    } else {
                        // Main Application Navigation State
                        var currentScreen by remember { mutableStateOf("HOME") }
                        var activeChatId by remember { mutableStateOf<String?>(null) }
                        var statusViewerUserId by remember { mutableStateOf<String?>(null) }
                        var isStatusViewerOwner by remember { mutableStateOf(false) }
                        var homeTab by remember { mutableIntStateOf(0) }

                        var showAddContactDialog by remember { mutableStateOf(false) }
                        var showAccountSwitcher by remember { mutableStateOf(false) }
                        var showAdminSpecialIdentifiersDialog by remember { mutableStateOf(false) }

                        val activeChat = remember(chats, activeChatId) {
                            chats.firstOrNull { it.chatId == activeChatId }
                        }

                        val activeMessages by produceState<List<MessageEntity>>(initialValue = emptyList(), key1 = activeChatId) {
                            if (activeChatId != null) {
                                repository.observeMessages(activeChatId!!).collect { value = it }
                            } else {
                                value = emptyList()
                            }
                        }

                        val activeChatReactions by produceState<List<com.example.data.local.entities.MessageReactionEntity>>(
                            initialValue = emptyList(),
                            key1 = activeChatId
                        ) {
                            if (activeChatId != null) {
                                repository.observeReactionsForChat(activeChatId!!).collect { value = it }
                            } else {
                                value = emptyList()
                            }
                        }

                        LaunchedEffect(activeChatId, activeMessages.size, currentScreen) {
                            if (activeChatId != null && currentScreen == "CHAT_DETAIL") {
                                repository.markChatAsRead(activeChatId!!)
                            }
                        }

                        val allChatsMembers by produceState<Map<String, List<ChatMemberEntity>>>(initialValue = emptyMap(), key1 = chats) {
                            val chatIds = chats.map { it.chatId }
                            if (chatIds.isNotEmpty()) {
                                repository.observeMembersForChats(chatIds).collect { membersList ->
                                    value = membersList.groupBy { it.chatId }
                                }
                            } else {
                                value = emptyMap()
                            }
                        }

                        val groupMembers by produceState<List<ChatMemberEntity>>(initialValue = emptyList(), key1 = activeChatId) {
                            if (activeChatId != null && activeChat?.type == "GROUP") {
                                repository.observeChatMembers(activeChatId!!).collect { value = it }
                            } else {
                                value = emptyList()
                            }
                        }

                        val otherUserInChat = remember(activeChat, allChatsMembers, groupMembers, allUsers, currentUser) {
                            if (activeChat?.type == "INDIVIDUAL") {
                                val members = allChatsMembers[activeChat.chatId] ?: groupMembers
                                val otherUserId = members.firstOrNull { it.userId != currentUser!!.userId }?.userId
                                allUsers.firstOrNull { it.userId == otherUserId }
                            } else null
                        }

                        val activeSessions by produceState<List<DeviceSessionEntity>>(initialValue = emptyList(), key1 = currentUser?.userId) {
                            if (currentUser != null) {
                                repository.getActiveSessionsForUser(currentUser!!.userId).collect { value = it }
                            } else {
                                value = emptyList()
                            }
                        }

                        // Handle incoming call overlay
                        if (callState.status == CallStatus.INCOMING) {
                            IncomingCallOverlay(
                                callState = callState,
                                onAccept = { RealtimeCallDispatcher.acceptCall() },
                                onReject = {
                                    RealtimeCallDispatcher.rejectCall()
                                    RealtimeCallDispatcher.clearCall()
                                }
                            )
                        } else if (!callState.isMinimized && callState.status in listOf(
                                CallStatus.OUTGOING,
                                CallStatus.CONNECTING,
                                CallStatus.CONNECTED,
                                CallStatus.RECONNECTING
                            )
                        ) {
                            ActiveCallScreen(
                                callState = callState,
                                onMuteToggle = { RealtimeCallDispatcher.toggleMute() },
                                onCameraToggle = { RealtimeCallDispatcher.toggleCamera() },
                                onFlipCamera = { RealtimeCallDispatcher.flipCamera() },
                                onSpeakerToggle = { RealtimeCallDispatcher.toggleSpeaker() },
                                onEndCall = {
                                    RealtimeCallDispatcher.endCall()
                                    RealtimeCallDispatcher.clearCall()
                                }
                            )
                        } else {
                            Box(modifier = Modifier.fillMaxSize()) {
                                // Primary Screen Router
                                when (currentScreen) {
                                "HOME" -> {
                                    HomeScreen(
                                        currentUser = currentUser!!,
                                        chats = chats,
                                        allUsers = allUsers,
                                        chatMembers = allChatsMembers,
                                        currentTab = homeTab,
                                        onTabSelected = { homeTab = it },
                                        onOpenChat = { chatId ->
                                            activeChatId = chatId
                                            currentScreen = "CHAT_DETAIL"
                                        },
                                        onCreateGroupClick = { currentScreen = "CREATE_GROUP" },
                                        repository = repository,
                                        onJoinGroupClick = { token ->
                                            scope.launch {
                                                val result = repository.joinGroupByInviteToken(token)
                                                if (result.isSuccess) {
                                                    val joinedChat = result.getOrNull()!!
                                                    activeChatId = joinedChat.chatId
                                                    currentScreen = "CHAT_DETAIL"
                                                    Toast.makeText(this@MainActivity, "تم الانضمام إلى المجموعة بنجاح!", Toast.LENGTH_SHORT).show()
                                                } else {
                                                    val err = result.exceptionOrNull()?.message ?: "رمز الدعوة غير صالح أو منتهي"
                                                    Toast.makeText(this@MainActivity, err, Toast.LENGTH_LONG).show()
                                                }
                                            }
                                        },
                                        onOpenAccountSwitcher = { showAccountSwitcher = true },
                                        statusContent = {
                                            StatusTabContent(
                                                currentUser = currentUser!!,
                                                allStatuses = allStatuses,
                                                allUsers = allUsers,
                                                contacts = myContacts,
                                                userSettings = userSettings,
                                                mutedUserIds = mutedUserIds,
                                                viewedStatusIds = viewedStatusIds,
                                                blockedUserIds = blockedUserIds,
                                                onCreateStatusClick = { currentScreen = "CREATE_STATUS" },
                                                onOpenStatusViewer = { targetUserId, isOwner ->
                                                    statusViewerUserId = targetUserId
                                                    isStatusViewerOwner = isOwner
                                                    currentScreen = "STATUS_VIEWER"
                                                },
                                                onMuteToggle = { targetUserId ->
                                                    scope.launch {
                                                        if (mutedUserIds.contains(targetUserId)) {
                                                            repository.unmuteUserStatus(targetUserId)
                                                            Toast.makeText(this@MainActivity, "تم إلغاء كتم الحالة", Toast.LENGTH_SHORT).show()
                                                        } else {
                                                            repository.muteUserStatus(targetUserId)
                                                            Toast.makeText(this@MainActivity, "تم كتم الحالة", Toast.LENGTH_SHORT).show()
                                                        }
                                                    }
                                                },
                                                onSavePrivacySettings = { newType, excluded, included ->
                                                    scope.launch {
                                                        repository.updateStatusPrivacySettings(newType, excluded, included)
                                                        Toast.makeText(this@MainActivity, "تم حفظ إعدادات خصوصية الحالة", Toast.LENGTH_SHORT).show()
                                                    }
                                                }
                                            )
                                        },
                                        callsContent = {
                                            CallHistoryScreen(
                                                callLogs = callLogs,
                                                allUsers = allUsers,
                                                currentUser = currentUser!!,
                                                onStartCall = { targetUserId, isVideo ->
                                                    requestCallPermissionsAndStart(isVideo) {
                                                        scope.launch {
                                                            repository.initiateCall(targetUserId, isVideo)
                                                        }
                                                    }
                                                }
                                            )
                                        },
                                        contactsContent = {
                                            ContactsScreen(
                                                contacts = myContacts,
                                                allUsers = allUsers,
                                                onOpenChat = { contactUserId ->
                                                    scope.launch {
                                                        val chat = repository.getOrCreateIndividualChat(contactUserId)
                                                        activeChatId = chat.chatId
                                                        currentScreen = "CHAT_DETAIL"
                                                    }
                                                },
                                                onAudioCall = { contactUserId ->
                                                    requestCallPermissionsAndStart(false) {
                                                        scope.launch { repository.initiateCall(contactUserId, false) }
                                                    }
                                                },
                                                onVideoCall = { contactUserId ->
                                                    requestCallPermissionsAndStart(true) {
                                                        scope.launch { repository.initiateCall(contactUserId, true) }
                                                    }
                                                },
                                                onAddContactClick = { showAddContactDialog = true },
                                                onDeleteContact = { contactUserId ->
                                                    scope.launch { repository.deleteContact(contactUserId) }
                                                },
                                                onBlockUser = { targetUserId ->
                                                    scope.launch { repository.blockUser(targetUserId) }
                                                }
                                            )
                                        },
                                        settingsContent = {
                                            SettingsScreen(
                                                currentUser = currentUser!!,
                                                settings = userSettings,
                                                activeSessions = activeSessions,
                                                onUpdateProfile = { name, bio, photo ->
                                                    scope.launch { repository.updateUserProfile(name, bio, photo) }
                                                },
                                                onDeletePhoto = {
                                                    scope.launch { repository.deleteProfilePhoto() }
                                                },
                                                onUpdateSettings = { newSettings ->
                                                    scope.launch { repository.updateSettings(newSettings) }
                                                },
                                                onOpenAccountSwitcher = { showAccountSwitcher = true },
                                                onLogout = {
                                                    scope.launch { repository.logout() }
                                                },
                                                onDeleteAccount = {
                                                    scope.launch { repository.deleteAccount() }
                                                },
                                                onChangePassword = { currentPass, newPass, onResult ->
                                                    scope.launch {
                                                        val res = repository.changePassword(currentPass, newPass)
                                                        if (res.isSuccess) {
                                                            onResult(true, null)
                                                        } else {
                                                            onResult(false, res.exceptionOrNull()?.message)
                                                        }
                                                    }
                                                },
                                                onRevokeSession = { session ->
                                                    scope.launch {
                                                        repository.revokeDeviceSession(session)
                                                    }
                                                },
                                                onRevokeAllOtherSessions = {
                                                    scope.launch {
                                                        repository.revokeAllOtherSessions(currentUser!!.userId)
                                                    }
                                                },
                                                onLinkNewDevice = { name, type, platform ->
                                                    scope.launch {
                                                        repository.linkNewDevice(currentUser!!.userId, name, type, platform)
                                                    }
                                                },
                                                onOpenAdminIdentifiers = {
                                                    showAdminSpecialIdentifiersDialog = true
                                                }
                                            )
                                        }
                                    )
                                }

                                "CHAT_DETAIL" -> {
                                    if (activeChat != null) {
                                        val isTargetTyping = typingMap[activeChat!!.chatId]?.contains(otherUserInChat?.userId) == true
                                        val isUserAdminOrOwner = remember(groupMembers, currentUser, activeChat) {
                                            if (activeChat?.type == "GROUP") {
                                                val myRole = groupMembers.firstOrNull { it.userId == currentUser?.userId }?.role
                                                myRole == "OWNER" || myRole == "ADMIN" || activeChat?.groupOwnerId == currentUser?.userId
                                            } else true
                                        }

                                        ChatDetailScreen(
                                            chat = activeChat!!,
                                            messages = activeMessages,
                                            currentUser = currentUser!!,
                                            otherUser = otherUserInChat,
                                            isTyping = isTargetTyping,
                                            isRecording = isRecording,
                                            isPlaying = isPlaying,
                                            currentPlayingUri = currentPlayingUri,
                                            playbackSpeed = playbackSpeed,
                                            playbackProgress = playbackProgress,
                                            isCurrentUserAdminOrOwner = isUserAdminOrOwner,
                                            reactions = activeChatReactions,
                                            onReactToMessage = { msgId, emoji ->
                                                scope.launch {
                                                    repository.toggleMessageReaction(activeChat!!.chatId, msgId, emoji)
                                                }
                                            },
                                            onBackClick = { currentScreen = "HOME" },
                                            onSendMessage = { payload ->
                                                scope.launch {
                                                    try {
                                                        repository.sendMessage(
                                                            chatId = activeChat!!.chatId,
                                                            type = payload.type,
                                                            content = payload.content,
                                                            mediaUri = payload.mediaUri,
                                                            mediaFileName = payload.fileName,
                                                            mediaFileSize = payload.fileSize,
                                                            mediaDurationMs = payload.durationMs,
                                                            caption = payload.caption,
                                                            mimeType = payload.mimeType,
                                                            latitude = payload.latitude,
                                                            longitude = payload.longitude,
                                                            locationAddress = payload.locationAddress,
                                                            contactName = payload.contactName,
                                                            contactPhone = payload.contactPhone,
                                                            contactEmail = payload.contactEmail
                                                        )
                                                    } catch (e: Exception) {
                                                        Toast.makeText(this@MainActivity, e.message ?: "Error sending message", Toast.LENGTH_SHORT).show()
                                                    }
                                                }
                                            },
                                            onRetrySendMessage = { msgId ->
                                                scope.launch {
                                                    repository.retrySendMessage(msgId)
                                                }
                                            },
                                            onCancelSendMessage = { msgId ->
                                                scope.launch {
                                                    repository.cancelSendMessage(msgId)
                                                }
                                            },
                                            onStartRecording = { audioRecorderPlayer.startRecording() },
                                            onStopRecordingAndSend = {
                                                val file = audioRecorderPlayer.stopRecording()
                                                if (file != null) {
                                                    scope.launch {
                                                        repository.sendMessage(
                                                            chatId = activeChat!!.chatId,
                                                            type = "VOICE",
                                                            mediaUri = file.absolutePath,
                                                            mediaDurationMs = 0L,
                                                            mimeType = "audio/amr"
                                                        )
                                                    }
                                                }
                                            },
                                            onCancelRecording = { audioRecorderPlayer.cancelRecording() },
                                            onPlayPauseVoice = { uri, speed ->
                                                if (isPlaying && currentPlayingUri == uri) {
                                                    audioRecorderPlayer.pauseAudio()
                                                } else {
                                                    audioRecorderPlayer.playAudio(uri, speed)
                                                }
                                            },
                                            onSpeedChange = { speed -> audioRecorderPlayer.setSpeed(speed) },
                                            onAudioCall = {
                                                val targetId = otherUserInChat?.userId ?: return@ChatDetailScreen
                                                requestCallPermissionsAndStart(false) {
                                                    scope.launch { repository.initiateCall(targetId, false) }
                                                }
                                            },
                                            onVideoCall = {
                                                val targetId = otherUserInChat?.userId ?: return@ChatDetailScreen
                                                requestCallPermissionsAndStart(true) {
                                                    scope.launch { repository.initiateCall(targetId, true) }
                                                }
                                            },
                                            onTypingChanged = { isTypingNow ->
                                                TypingStateDispatcher.setTyping(activeChat!!.chatId, currentUser!!.userId, isTypingNow)
                                            },
                                            onGroupInfoClick = {
                                                currentScreen = "GROUP_DETAIL"
                                            },
                                            onPinMessage = { msgId ->
                                                scope.launch {
                                                    repository.pinMessage(activeChat!!.chatId, msgId)
                                                    Toast.makeText(this@MainActivity, if (msgId != null) "تم تثبيت الرسالة" else "تم إلغاء تثبيت الرسالة", Toast.LENGTH_SHORT).show()
                                                }
                                            }
                                        )
                                    }
                                }

                                "GROUP_DETAIL" -> {
                                    if (activeChat != null && currentUser != null) {
                                        GroupDetailScreen(
                                            chat = activeChat!!,
                                            members = groupMembers,
                                            allUsers = allUsers,
                                            contacts = myContacts,
                                            currentUser = currentUser!!,
                                            onBackClick = { currentScreen = "CHAT_DETAIL" },
                                            onUpdateGroupInfo = { name, iconUri, desc ->
                                                scope.launch {
                                                    repository.updateGroupDetails(activeChat!!.chatId, name, iconUri, desc)
                                                }
                                            },
                                            onUpdatePermissions = { editInfo, sendMsg, addMembers ->
                                                scope.launch {
                                                    repository.updateGroupPermissions(activeChat!!.chatId, editInfo, sendMsg, addMembers)
                                                }
                                            },
                                            onResetInviteToken = {
                                                scope.launch {
                                                    repository.resetGroupInviteToken(activeChat!!.chatId)
                                                    Toast.makeText(this@MainActivity, "تم تحديث رمز الدعوة بنجاح", Toast.LENGTH_SHORT).show()
                                                }
                                            },
                                            onAddMembers = { userIds ->
                                                scope.launch {
                                                    repository.addGroupMembers(activeChat!!.chatId, userIds)
                                                }
                                            },
                                            onMakeAdmin = { memberUserId ->
                                                scope.launch {
                                                    repository.updateMemberRole(activeChat!!.chatId, memberUserId, "ADMIN")
                                                }
                                            },
                                            onRemoveAdmin = { memberUserId ->
                                                scope.launch {
                                                    repository.updateMemberRole(activeChat!!.chatId, memberUserId, "MEMBER")
                                                }
                                            },
                                            onTransferOwnership = { newOwnerUserId ->
                                                scope.launch {
                                                    repository.transferGroupOwnership(activeChat!!.chatId, newOwnerUserId)
                                                }
                                            },
                                            onRemoveMember = { memberUserId ->
                                                scope.launch {
                                                    repository.removeGroupMember(activeChat!!.chatId, memberUserId)
                                                }
                                            },
                                            onLeaveGroup = {
                                                scope.launch {
                                                    repository.leaveGroup(activeChat!!.chatId)
                                                    activeChatId = null
                                                    currentScreen = "HOME"
                                                }
                                            },
                                            onDeleteGroup = {
                                                scope.launch {
                                                    repository.deleteGroup(activeChat!!.chatId)
                                                    activeChatId = null
                                                    currentScreen = "HOME"
                                                }
                                            }
                                        )
                                    }
                                }

                                "CREATE_GROUP" -> {
                                    CreateGroupScreen(
                                        contacts = myContacts,
                                        allUsers = allUsers,
                                        onBackClick = { currentScreen = "HOME" },
                                        onCreateGroup = { groupName, groupIcon, memberIds ->
                                            scope.launch {
                                                val groupChat = repository.createGroupChat(groupName, groupIcon, memberIds)
                                                activeChatId = groupChat.chatId
                                                currentScreen = "CHAT_DETAIL"
                                            }
                                        }
                                    )
                                }

                                "CREATE_STATUS" -> {
                                    CreateStatusScreen(
                                        currentSettings = userSettings,
                                        contacts = myContacts,
                                        allUsers = allUsers,
                                        onPostStatus = { type, text, uri, caption, bg, style, align, priv, targets ->
                                            scope.launch {
                                                repository.postStatus(
                                                    type = type,
                                                    contentText = text,
                                                    mediaUri = uri,
                                                    caption = caption,
                                                    bgColorHex = bg,
                                                    textStyle = style,
                                                    textAlignment = align,
                                                    privacyType = priv,
                                                    privacyTargetIds = targets
                                                )
                                                Toast.makeText(this@MainActivity, "تم نشر الحالة بنجاح!", Toast.LENGTH_SHORT).show()
                                                currentScreen = "HOME"
                                            }
                                        },
                                        onSavePrivacySettings = { newType, excluded, included ->
                                            scope.launch {
                                                repository.updateStatusPrivacySettings(newType, excluded, included)
                                            }
                                        },
                                        onDismiss = { currentScreen = "HOME" }
                                    )
                                }

                                "STATUS_VIEWER" -> {
                                    val targetUser = allUsers.firstOrNull { it.userId == statusViewerUserId }
                                    val targetStatuses = remember(allStatuses, statusViewerUserId) {
                                        allStatuses.filter {
                                            it.userId == statusViewerUserId &&
                                                    !it.isDeleted &&
                                                    it.expiresAt > System.currentTimeMillis()
                                        }
                                    }

                                    var activeStatusIdForViews by remember(targetStatuses) {
                                        mutableStateOf(targetStatuses.firstOrNull()?.statusId)
                                    }

                                    val currentStatusViews by produceState<List<StatusViewEntity>>(
                                        initialValue = emptyList(),
                                        key1 = activeStatusIdForViews,
                                        key2 = isStatusViewerOwner
                                    ) {
                                        if (isStatusViewerOwner && activeStatusIdForViews != null) {
                                            repository.observeStatusViews(activeStatusIdForViews!!).collect { value = it }
                                        } else {
                                            value = emptyList()
                                        }
                                    }

                                    val isMuted = mutedUserIds.contains(statusViewerUserId)

                                    StatusViewerScreen(
                                        user = targetUser,
                                        statuses = targetStatuses,
                                        isOwner = isStatusViewerOwner,
                                        currentStatusViews = currentStatusViews,
                                        isMuted = isMuted,
                                        onStatusViewed = { statusId ->
                                            activeStatusIdForViews = statusId
                                            scope.launch { repository.recordStatusView(statusId) }
                                        },
                                        onDeleteStatus = { statusId ->
                                            scope.launch {
                                                repository.deleteStatus(statusId)
                                                Toast.makeText(this@MainActivity, "تم حذف الحالة", Toast.LENGTH_SHORT).show()
                                            }
                                        },
                                        onReplyToStatus = { statusId, text ->
                                            scope.launch {
                                                repository.replyToStatus(statusId, text)
                                                Toast.makeText(this@MainActivity, "تم إرسال الرد في المحادثة", Toast.LENGTH_SHORT).show()
                                            }
                                        },
                                        onReactToStatus = { statusId, emoji ->
                                            scope.launch {
                                                repository.reactToStatus(statusId, emoji)
                                                Toast.makeText(this@MainActivity, "تم التفاعل $emoji", Toast.LENGTH_SHORT).show()
                                            }
                                        },
                                        onMuteToggle = { targetUserId ->
                                            scope.launch {
                                                if (mutedUserIds.contains(targetUserId)) {
                                                    repository.unmuteUserStatus(targetUserId)
                                                    Toast.makeText(this@MainActivity, "تم إلغاء كتم الحالة", Toast.LENGTH_SHORT).show()
                                                } else {
                                                    repository.muteUserStatus(targetUserId)
                                                    Toast.makeText(this@MainActivity, "تم كتم الحالة", Toast.LENGTH_SHORT).show()
                                                }
                                            }
                                        },
                                        onDismiss = { currentScreen = "HOME" }
                                    )
                                }
                            }

                            if (currentScreen != "HOME") {
                                BackHandler {
                                    when (currentScreen) {
                                        "GROUP_DETAIL" -> currentScreen = "CHAT_DETAIL"
                                        else -> {
                                            activeChatId = null
                                            currentScreen = "HOME"
                                        }
                                    }
                                }
                            }

                            if (callState.isMinimized && callState.status in listOf(
                                    CallStatus.CONNECTED,
                                    CallStatus.OUTGOING,
                                    CallStatus.RECONNECTING
                                )
                            ) {
                                MinimizedCallBanner(
                                    callState = callState,
                                    onRestoreCall = { RealtimeCallDispatcher.restoreCall() },
                                    onMuteToggle = { RealtimeCallDispatcher.toggleMute() },
                                    onEndCall = {
                                        RealtimeCallDispatcher.endCall()
                                        RealtimeCallDispatcher.clearCall()
                                    },
                                    modifier = Modifier
                                        .align(Alignment.TopCenter)
                                        .padding(top = 28.dp)
                                )
                            }
                        }
                    }

                        if (showAddContactDialog) {
                            AddContactDialog(
                                onAdd = { customId, nickname, onError ->
                                    scope.launch {
                                        val err = repository.addContactByCustomId(customId, nickname)
                                        if (err != null) {
                                            onError(err)
                                        } else {
                                            showAddContactDialog = false
                                            Toast.makeText(this@MainActivity, "تمت إضافة جهة الاتصال بنجاح!", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                },
                                onDismiss = { showAddContactDialog = false }
                            )
                        }

                        if (showAccountSwitcher) {
                            AccountSwitcherBottomSheet(
                                currentUser = currentUser,
                                allUsers = allUsers,
                                onSelectUser = { targetUser, password ->
                                    scope.launch {
                                        val res = repository.switchAccount(targetUser, password)
                                        if (res.isSuccess) {
                                            showAccountSwitcher = false
                                        } else {
                                            val err = res.exceptionOrNull()?.message ?: "كلمة المرور غير صحيحة"
                                            Toast.makeText(this@MainActivity, err, Toast.LENGTH_LONG).show()
                                        }
                                    }
                                },
                                onAddNewUser = {
                                    scope.launch { repository.logout() }
                                },
                                onLogout = {
                                    scope.launch { repository.logout() }
                                },
                                onDismiss = { showAccountSwitcher = false }
                            )
                        }

                        val currentRationale = permissionRationaleText
                        if (currentRationale != null) {
                            AlertDialog(
                                onDismissRequest = { permissionRationaleText = null },
                                title = { Text("أذونات المكالمة مطلوبة") },
                                text = { Text(currentRationale) },
                                confirmButton = {
                                    TextButton(onClick = {
                                        permissionRationaleText = null
                                        callPermissionLauncher.launch(
                                            arrayOf(CallPermissionHandler.PERMISSION_AUDIO, CallPermissionHandler.PERMISSION_CAMERA)
                                        )
                                    }) {
                                        Text("متابعة")
                                    }
                                },
                                dismissButton = {
                                    TextButton(onClick = { permissionRationaleText = null }) {
                                        Text("إلغاء")
                                    }
                                }
                            )
                        }

                        if (showSettingsRedirectDialog) {
                            AlertDialog(
                                onDismissRequest = { showSettingsRedirectDialog = false },
                                title = { Text("الأذونات معطلة") },
                                text = { Text(CallPermissionHandler.getSettingsRedirectMessage(false)) },
                                confirmButton = {
                                    TextButton(onClick = {
                                        showSettingsRedirectDialog = false
                                        CallPermissionHandler.openAppSettings(this@MainActivity)
                                    }) {
                                        Text("فتح الإعدادات")
                                    }
                                },
                                dismissButton = {
                                    TextButton(onClick = { showSettingsRedirectDialog = false }) {
                                        Text("إلغاء")
                                    }
                                }
                            )
                        }

                        if (showAdminSpecialIdentifiersDialog) {
                            AdminSpecialIdentifierDialog(
                                identifiers = specialIdentifiers,
                                allUsers = allUsers,
                                onCreateIdentifier = { idText, category, notes, onResult ->
                                    scope.launch {
                                        val res = repository.createSpecialIdentifier(idText, category, notes)
                                        if (res.isSuccess) {
                                            onResult(true, null)
                                            Toast.makeText(this@MainActivity, "تم إنشاء المعرف المميز بنجاح!", Toast.LENGTH_SHORT).show()
                                        } else {
                                            val err = res.exceptionOrNull()?.message ?: "خطأ أثناء إنشاء المعرف"
                                            onResult(false, err)
                                        }
                                    }
                                },
                                onReserveIdentifier = { id, reservedFor, notes, onResult ->
                                    scope.launch {
                                        val res = repository.reserveSpecialIdentifier(id, reservedFor, notes)
                                        if (res.isSuccess) {
                                            onResult(true, null)
                                            Toast.makeText(this@MainActivity, "تم حجز المعرف بنجاح!", Toast.LENGTH_SHORT).show()
                                        } else {
                                            val err = res.exceptionOrNull()?.message ?: "خطأ أثناء الحجز"
                                            onResult(false, err)
                                        }
                                    }
                                },
                                onAssignIdentifier = { id, targetUserId, onResult ->
                                    scope.launch {
                                        val res = repository.assignSpecialIdentifierToUser(id, targetUserId)
                                        if (res.isSuccess) {
                                            onResult(true, null)
                                            Toast.makeText(this@MainActivity, "تم تعيين المعرف المميز للمستخدم بنجاح!", Toast.LENGTH_SHORT).show()
                                        } else {
                                            val err = res.exceptionOrNull()?.message ?: "خطأ أثناء تعيين المعرف"
                                            onResult(false, err)
                                        }
                                    }
                                },
                                onReleaseIdentifier = { id, onResult ->
                                    scope.launch {
                                        val res = repository.releaseSpecialIdentifier(id)
                                        if (res.isSuccess) {
                                            onResult(true, null)
                                            Toast.makeText(this@MainActivity, "تم فك ارتباط المعرف بنجاح وإعادته متاحاً!", Toast.LENGTH_SHORT).show()
                                        } else {
                                            val err = res.exceptionOrNull()?.message ?: "خطأ أثناء فك الارتباط"
                                            onResult(false, err)
                                        }
                                    }
                                },
                                onDisableIdentifier = { id, reason, onResult ->
                                    scope.launch {
                                        val res = repository.disableSpecialIdentifier(id, reason)
                                        if (res.isSuccess) {
                                            onResult(true, null)
                                            Toast.makeText(this@MainActivity, "تم تعطيل المعرف المميز بنجاح!", Toast.LENGTH_SHORT).show()
                                        } else {
                                            val err = res.exceptionOrNull()?.message ?: "خطأ أثناء التعطيل"
                                            onResult(false, err)
                                        }
                                    }
                                },
                                onDeleteIdentifier = { id, onResult ->
                                    scope.launch {
                                        val res = repository.deleteSpecialIdentifier(id)
                                        if (res.isSuccess) {
                                            onResult(true, null)
                                            Toast.makeText(this@MainActivity, "تم حذف المعرف المميز نهائياً!", Toast.LENGTH_SHORT).show()
                                        } else {
                                            val err = res.exceptionOrNull()?.message ?: "خطأ أثناء الحذف"
                                            onResult(false, err)
                                        }
                                    }
                                },
                                onDismiss = { showAdminSpecialIdentifiersDialog = false }
                            )
                        }
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleCallIntent(intent)
        handleDeepLink(intent)
    }

    private fun handleDeepLink(intent: Intent?) {
        val data = intent?.data ?: return
        if (data.scheme == "chatconnect" && data.host == "invite") {
            val token = data.lastPathSegment ?: return
            lifecycleScope.launch {
                val res = repository.joinGroupByInviteToken(token)
                if (res.isSuccess) {
                    val chat = res.getOrNull()
                    Toast.makeText(this@MainActivity, "تم الانضمام إلى ${chat?.groupName ?: "المجموعة"} بنجاح!", Toast.LENGTH_SHORT).show()
                } else {
                    val err = res.exceptionOrNull()?.message ?: "رابط الدعوة غير صالح"
                    Toast.makeText(this@MainActivity, err, Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun handleCallIntent(intent: Intent?) {
        when (intent?.action) {
            CallNotificationManager.ACTION_ACCEPT_CALL -> {
                RealtimeCallDispatcher.acceptCall()
            }
            CallNotificationManager.ACTION_DECLINE_CALL -> {
                RealtimeCallDispatcher.rejectCall()
                RealtimeCallDispatcher.clearCall()
            }
            CallNotificationManager.ACTION_END_CALL -> {
                RealtimeCallDispatcher.endCall()
                RealtimeCallDispatcher.clearCall()
            }
        }
    }

    private fun setupLockScreenFlags() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
            )
        }
        val keyguardManager = getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            keyguardManager?.requestDismissKeyguard(this, null)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        audioRecorderPlayer.release()
    }
}
