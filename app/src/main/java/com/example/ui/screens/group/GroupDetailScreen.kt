package com.example.ui.screens.group

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.entities.ChatEntity
import com.example.data.local.entities.ChatMemberEntity
import com.example.data.local.entities.ContactEntity
import com.example.data.local.entities.UserEntity
import com.example.ui.components.AvatarImage
import com.example.utils.AppStrings

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupDetailScreen(
    chat: ChatEntity,
    members: List<ChatMemberEntity>,
    allUsers: List<UserEntity>,
    contacts: List<ContactEntity>,
    currentUser: UserEntity,
    onBackClick: () -> Unit,
    onUpdateGroupInfo: (name: String, iconUri: String?, description: String?) -> Unit,
    onUpdatePermissions: (editInfo: String, sendMsg: String, addMembers: String) -> Unit,
    onResetInviteToken: () -> Unit,
    onAddMembers: (List<String>) -> Unit,
    onMakeAdmin: (String) -> Unit,
    onRemoveAdmin: (String) -> Unit,
    onTransferOwnership: (String) -> Unit,
    onRemoveMember: (String) -> Unit,
    onLeaveGroup: () -> Unit,
    onDeleteGroup: () -> Unit
) {
    val context = LocalContext.current
    val userMap = remember(allUsers) { allUsers.associateBy { it.userId } }
    val myMember = members.firstOrNull { it.userId == currentUser.userId }
    val myRole = myMember?.role ?: "MEMBER"
    val isOwner = myRole == "OWNER" || chat.groupOwnerId == currentUser.userId
    val isAdmin = myRole == "ADMIN"
    val isPrivileged = isOwner || isAdmin

    val canEditInfo = isPrivileged || chat.editGroupInfoPermission == "ALL"
    val canAddMembers = isPrivileged || chat.addMembersPermission == "ALL"

    var showEditInfoDialog by remember { mutableStateOf(false) }
    var showPermissionsDialog by remember { mutableStateOf(false) }
    var showAddMembersDialog by remember { mutableStateOf(false) }
    var showLeaveConfirm by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var memberToConfirmTransfer by remember { mutableStateOf<UserEntity?>(null) }

    // Edit Info State
    var editName by remember(chat) { mutableStateOf(chat.groupName ?: "") }
    var editDescription by remember(chat) { mutableStateOf(chat.groupDescription ?: "") }
    var editIconUri by remember(chat) { mutableStateOf(chat.groupIconUri) }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            editIconUri = uri.toString()
        }
    }

    // Permissions State
    var permEditInfo by remember(chat) { mutableStateOf(chat.editGroupInfoPermission) }
    var permSendMsg by remember(chat) { mutableStateOf(chat.sendMessagesPermission) }
    var permAddMembers by remember(chat) { mutableStateOf(chat.addMembersPermission) }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                title = { Text(chat.groupName ?: "معلومات المجموعة") },
                actions = {
                    if (isPrivileged) {
                        IconButton(onClick = { showPermissionsDialog = true }) {
                            Icon(Icons.Default.AdminPanelSettings, contentDescription = "إعدادات الصلاحيات")
                        }
                    }
                    if (canEditInfo) {
                        IconButton(onClick = { showEditInfoDialog = true }) {
                            Icon(Icons.Default.Edit, contentDescription = "تعديل المجموعة")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Group Avatar & Title Card
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(20.dp).fillMaxWidth()
                ) {
                    Box(contentAlignment = Alignment.BottomEnd) {
                        AvatarImage(
                            uri = chat.groupIconUri,
                            name = chat.groupName ?: "G",
                            size = 88.dp,
                            isGroup = true
                        )
                        if (canEditInfo) {
                            Box(
                                modifier = Modifier
                                    .size(28.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primary)
                                    .clickable { showEditInfoDialog = true },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Default.CameraAlt,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onPrimary,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = chat.groupName ?: "مجموعة",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )

                    Text(
                        text = "${members.size} عضو",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    if (!chat.groupDescription.isNull_or_blank()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = chat.groupDescription!!,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Invite via Link Section
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Link, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "رابط الدعوة للمجموعة",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    val inviteLink = "chatconnect://invite/${chat.inviteToken ?: ""}"
                    Text(
                        text = inviteLink,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                            .padding(8.dp)
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                val clip = ClipData.newPlainText("Group Invite Link", inviteLink)
                                clipboard.setPrimaryClip(clip)
                                Toast.makeText(context, "تم نسخ الرابط للحافظة", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("نسخ الرابط", style = MaterialTheme.typography.labelMedium)
                        }

                        OutlinedButton(
                            onClick = {
                                val sendIntent = Intent().apply {
                                    action = Intent.ACTION_SEND
                                    putExtra(Intent.EXTRA_TEXT, "انضم إلى مجموعتنا \"${chat.groupName}\" على تطبيق ChatConnect عبر الرابط:\n$inviteLink")
                                    type = "text/plain"
                                }
                                context.startActivity(Intent.createChooser(sendIntent, "مشاركة رابط الدعوة"))
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("مشاركة", style = MaterialTheme.typography.labelMedium)
                        }

                        if (isPrivileged) {
                            IconButton(onClick = onResetInviteToken) {
                                Icon(Icons.Default.Refresh, contentDescription = "إعادة تعيين الرابط", tint = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Group Members Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "أعضاء المجموعة (${members.size})",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )

                if (canAddMembers) {
                    FilledTonalButton(
                        onClick = { showAddMembersDialog = true },
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Icon(Icons.Default.PersonAdd, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("إضافة أعضاء", style = MaterialTheme.typography.labelMedium)
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Member List
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                members.forEach { member ->
                    val user = userMap[member.userId]
                    var showMenu by remember { mutableStateOf(false) }

                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(12.dp)
                        ) {
                            AvatarImage(
                                uri = user?.profilePicUri,
                                name = user?.username ?: "User",
                                size = 44.dp
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = user?.username ?: "مستخدم",
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    text = when (member.role) {
                                        "OWNER" -> "👑 المالك"
                                        "ADMIN" -> "⭐ مشرف"
                                        else -> "عضو"
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = when (member.role) {
                                        "OWNER" -> MaterialTheme.colorScheme.primary
                                        "ADMIN" -> MaterialTheme.colorScheme.secondary
                                        else -> MaterialTheme.colorScheme.outline
                                    },
                                    fontWeight = FontWeight.SemiBold
                                )
                            }

                            // Actions for Privileged users
                            if (isPrivileged && member.userId != currentUser.userId && member.role != "OWNER") {
                                Box {
                                    IconButton(onClick = { showMenu = true }) {
                                        Icon(Icons.Default.MoreVert, contentDescription = "خيارات العضو")
                                    }
                                    DropdownMenu(
                                        expanded = showMenu,
                                        onDismissRequest = { showMenu = false }
                                    ) {
                                        if (isOwner && member.role == "MEMBER") {
                                            DropdownMenuItem(
                                                text = { Text("تعيين كمشرف") },
                                                leadingIcon = { Icon(Icons.Default.AdminPanelSettings, contentDescription = null) },
                                                onClick = {
                                                    showMenu = false
                                                    onMakeAdmin(member.userId)
                                                }
                                            )
                                        } else if (isOwner && member.role == "ADMIN") {
                                            DropdownMenuItem(
                                                text = { Text("إعفاء من الإشراف") },
                                                leadingIcon = { Icon(Icons.Default.RemoveModerator, contentDescription = null) },
                                                onClick = {
                                                    showMenu = false
                                                    onRemoveAdmin(member.userId)
                                                }
                                            )
                                        }

                                        if (isOwner) {
                                            DropdownMenuItem(
                                                text = { Text("نقل ملكية المجموعة") },
                                                leadingIcon = { Icon(Icons.Default.Stars, contentDescription = null) },
                                                onClick = {
                                                    showMenu = false
                                                    memberToConfirmTransfer = user
                                                }
                                            )
                                        }

                                        DropdownMenuItem(
                                            text = { Text("إزالة من المجموعة", color = MaterialTheme.colorScheme.error) },
                                            leadingIcon = { Icon(Icons.Default.PersonRemove, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                                            onClick = {
                                                showMenu = false
                                                onRemoveMember(member.userId)
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Actions: Leave & Delete
            OutlinedButton(
                onClick = { showLeaveConfirm = true },
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.ExitToApp, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("مغادرة المجموعة")
            }

            if (isOwner) {
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = { showDeleteConfirm = true },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.DeleteForever, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("حذف المجموعة نهائياً")
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }

    // Edit Group Info Dialog
    if (showEditInfoDialog) {
        AlertDialog(
            onDismissRequest = { showEditInfoDialog = false },
            title = { Text("تعديل معلومات المجموعة") },
            text = {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        modifier = Modifier
                            .size(72.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.secondaryContainer)
                            .clickable {
                                photoPickerLauncher.launch(
                                    androidx.activity.result.PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        if (editIconUri != null) {
                            AvatarImage(uri = editIconUri, name = editName, size = 72.dp, isGroup = true)
                        } else {
                            Icon(Icons.Default.AddAPhoto, contentDescription = null)
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = editName,
                        onValueChange = { editName = it },
                        label = { Text("اسم المجموعة") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = editDescription,
                        onValueChange = { editDescription = it },
                        label = { Text("وصف المجموعة (اختياري)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (editName.isNotBlank()) {
                            onUpdateGroupInfo(editName.trim(), editIconUri, editDescription.trim())
                            showEditInfoDialog = false
                            Toast.makeText(context, "تم حفظ التعديلات", Toast.LENGTH_SHORT).show()
                        }
                    }
                ) {
                    Text("حفظ")
                }
            },
            dismissButton = {
                TextButton(onClick = { showEditInfoDialog = false }) {
                    Text("إلغاء")
                }
            }
        )
    }

    // Permissions Dialog
    if (showPermissionsDialog) {
        AlertDialog(
            onDismissRequest = { showPermissionsDialog = false },
            title = { Text("صلاحيات المجموعة") },
            text = {
                Column {
                    Text("من يمكنه تعديل معلومات المجموعة؟", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = permEditInfo == "ALL", onClick = { permEditInfo = "ALL" })
                        Text("الجميع")
                        Spacer(modifier = Modifier.width(16.dp))
                        RadioButton(selected = permEditInfo == "ADMIN_ONLY", onClick = { permEditInfo = "ADMIN_ONLY" })
                        Text("المشرفون فقط")
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Text("من يمكنه إرسال الرسائل؟", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = permSendMsg == "ALL", onClick = { permSendMsg = "ALL" })
                        Text("الجميع")
                        Spacer(modifier = Modifier.width(16.dp))
                        RadioButton(selected = permSendMsg == "ADMIN_ONLY", onClick = { permSendMsg = "ADMIN_ONLY" })
                        Text("المشرفون فقط")
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Text("من يمكنه إضافة أعضاء جدد؟", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = permAddMembers == "ALL", onClick = { permAddMembers = "ALL" })
                        Text("الجميع")
                        Spacer(modifier = Modifier.width(16.dp))
                        RadioButton(selected = permAddMembers == "ADMIN_ONLY", onClick = { permAddMembers = "ADMIN_ONLY" })
                        Text("المشرفون فقط")
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onUpdatePermissions(permEditInfo, permSendMsg, permAddMembers)
                        showPermissionsDialog = false
                        Toast.makeText(context, "تم حفظ الصلاحيات", Toast.LENGTH_SHORT).show()
                    }
                ) {
                    Text("تطبيق")
                }
            },
            dismissButton = {
                TextButton(onClick = { showPermissionsDialog = false }) {
                    Text("إلغاء")
                }
            }
        )
    }

    // Add Members Dialog
    if (showAddMembersDialog) {
        val existingMemberIds = remember(members) { members.map { it.userId }.toSet() }
        val availableContacts = remember(contacts, existingMemberIds) {
            contacts.filter { it.contactUserId !in existingMemberIds }
        }
        val selectedToAdd = remember { mutableStateListOf<String>() }

        AlertDialog(
            onDismissRequest = { showAddMembersDialog = false },
            title = { Text("إضافة أعضاء إلى المجموعة") },
            text = {
                if (availableContacts.isEmpty()) {
                    Text("جميع جهات اتصالك مضافة بالفعل في هذه المجموعة.")
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth().heightIn(max = 280.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(availableContacts) { contact ->
                            val user = userMap[contact.contactUserId]
                            val isSelected = selectedToAdd.contains(contact.contactUserId)
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        if (isSelected) selectedToAdd.remove(contact.contactUserId)
                                        else selectedToAdd.add(contact.contactUserId)
                                    }
                                    .padding(vertical = 4.dp)
                            ) {
                                AvatarImage(uri = user?.profilePicUri, name = contact.nickname, size = 36.dp)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(contact.nickname, modifier = Modifier.weight(1f))
                                Checkbox(
                                    checked = isSelected,
                                    onCheckedChange = { checked ->
                                        if (checked) selectedToAdd.add(contact.contactUserId)
                                        else selectedToAdd.remove(contact.contactUserId)
                                    }
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                if (availableContacts.isNotEmpty()) {
                    Button(
                        onClick = {
                            if (selectedToAdd.isNotEmpty()) {
                                onAddMembers(selectedToAdd.toList())
                                showAddMembersDialog = false
                                Toast.makeText(context, "تمت إضافة الأعضاء بنجاح", Toast.LENGTH_SHORT).show()
                            }
                        },
                        enabled = selectedToAdd.isNotEmpty()
                    ) {
                        Text("إضافة (${selectedToAdd.size})")
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddMembersDialog = false }) {
                    Text("إغلاق")
                }
            }
        )
    }

    // Leave Group Confirm Dialog
    if (showLeaveConfirm) {
        AlertDialog(
            onDismissRequest = { showLeaveConfirm = false },
            title = { Text("تأكيد مغادرة المجموعة") },
            text = { Text("هل أنت متأكد من رغبتك في مغادرة المجموعة \"${chat.groupName}\"؟") },
            confirmButton = {
                Button(
                    onClick = {
                        showLeaveConfirm = false
                        onLeaveGroup()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("مغادرة")
                }
            },
            dismissButton = {
                TextButton(onClick = { showLeaveConfirm = false }) {
                    Text("إلغاء")
                }
            }
        )
    }

    // Delete Group Confirm Dialog
    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("تأكيد حذف المجموعة") },
            text = { Text("هل أنت متأكد من حذف هذه المجموعة نهائياً؟ سيتم حذف جميع المحادثات والبيانات المرتبطة بها لجميع الأعضاء.") },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteConfirm = false
                        onDeleteGroup()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("حذف نهائي")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("إلغاء")
                }
            }
        )
    }

    // Transfer Ownership Confirm Dialog
    memberToConfirmTransfer?.let { targetUser ->
        AlertDialog(
            onDismissRequest = { memberToConfirmTransfer = null },
            title = { Text("نقل ملكية المجموعة") },
            text = { Text("هل أنت متأكد من نقل ملكية المجموعة إلى ${targetUser.username}؟ ستصبح أنت مشرفاً في المجموعة ولن يمكنك التراجع عن هذا الإجراء إلا بموافقة المالك الجديد.") },
            confirmButton = {
                Button(
                    onClick = {
                        onTransferOwnership(targetUser.userId)
                        memberToConfirmTransfer = null
                        Toast.makeText(context, "تم نقل الملكية بنجاح", Toast.LENGTH_SHORT).show()
                    }
                ) {
                    Text("تأكيد النقل")
                }
            },
            dismissButton = {
                TextButton(onClick = { memberToConfirmTransfer = null }) {
                    Text("إلغاء")
                }
            }
        )
    }
}

private fun String?.isNull_or_blank(): Boolean = this == null || this.trim().isEmpty()
