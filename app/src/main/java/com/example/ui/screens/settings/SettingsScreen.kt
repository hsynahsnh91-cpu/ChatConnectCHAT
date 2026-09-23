package com.example.ui.screens.settings

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.entities.DeviceSessionEntity
import com.example.data.local.entities.UserEntity
import com.example.data.local.entities.UserSettingsEntity
import com.example.ui.components.AvatarImage
import com.example.utils.AdminConstants
import com.example.utils.AppStrings
import com.example.utils.TimeUtils

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    currentUser: UserEntity,
    settings: UserSettingsEntity?,
    activeSessions: List<DeviceSessionEntity> = emptyList(),
    onUpdateProfile: (username: String, bio: String, photoUri: String?) -> Unit,
    onDeletePhoto: () -> Unit,
    onUpdateSettings: (UserSettingsEntity) -> Unit,
    onOpenAccountSwitcher: () -> Unit,
    onLogout: () -> Unit = {},
    onDeleteAccount: () -> Unit,
    onChangePassword: (currentPass: String, newPass: String, onResult: (Boolean, String?) -> Unit) -> Unit = { _, _, _ -> },
    onRevokeSession: (DeviceSessionEntity) -> Unit = {},
    onRevokeAllOtherSessions: () -> Unit = {},
    onLinkNewDevice: (deviceName: String, deviceType: String, platform: String) -> Unit = { _, _, _ -> },
    onOpenAdminIdentifiers: () -> Unit = {}
) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    var isEditingProfile by remember { mutableStateOf(false) }
    var usernameInput by remember(currentUser) { mutableStateOf(currentUser.username) }
    var bioInput by remember(currentUser) { mutableStateOf(currentUser.bio) }
    var photoUriInput by remember(currentUser) { mutableStateOf(currentUser.profilePicUri) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    var sessionToRevoke by remember { mutableStateOf<DeviceSessionEntity?>(null) }
    var showRevokeAllConfirm by remember { mutableStateOf(false) }
    var showLinkDeviceDialog by remember { mutableStateOf(false) }

    val currentSession = remember(activeSessions) {
        activeSessions.firstOrNull { it.isCurrentDevice }
    }
    val otherSessions = remember(activeSessions) {
        activeSessions.filter { !it.isCurrentDevice }
    }

    var showChangePasswordDialog by remember { mutableStateOf(false) }
    var currentPassInput by remember { mutableStateOf("") }
    var newPassInput by remember { mutableStateOf("") }
    var confirmNewPassInput by remember { mutableStateOf("") }
    var isCurrentPassVisible by remember { mutableStateOf(false) }
    var isNewPassVisible by remember { mutableStateOf(false) }
    var isConfirmNewPassVisible by remember { mutableStateOf(false) }
    var changePassError by remember { mutableStateOf<String?>(null) }
    var isChangingPass by remember { mutableStateOf(false) }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            photoUriInput = uri.toString()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        // User Profile Card
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(20.dp)
            ) {
                AvatarImage(
                    uri = if (isEditingProfile) photoUriInput else currentUser.profilePicUri,
                    name = currentUser.username,
                    size = 80.dp
                )

                if (isEditingProfile) {
                    Row(modifier = Modifier.padding(top = 8.dp)) {
                        TextButton(
                            onClick = {
                                photoPickerLauncher.launch(
                                    androidx.activity.result.PickVisualMediaRequest(
                                        ActivityResultContracts.PickVisualMedia.ImageOnly
                                    )
                                )
                            }
                        ) {
                            Text(AppStrings.get("change_photo"))
                        }
                        TextButton(
                            onClick = {
                                photoUriInput = null
                                onDeletePhoto()
                            }
                        ) {
                            Text(AppStrings.get("delete_photo"), color = MaterialTheme.colorScheme.error)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                if (isEditingProfile) {
                    OutlinedTextField(
                        value = usernameInput,
                        onValueChange = { usernameInput = it },
                        label = { Text(AppStrings.get("username")) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = bioInput,
                        onValueChange = { bioInput = it },
                        label = { Text(AppStrings.get("bio")) },
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Button(
                        onClick = {
                            onUpdateProfile(usernameInput.trim(), bioInput.trim(), photoUriInput)
                            isEditingProfile = false
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(AppStrings.get("save"))
                    }
                } else {
                    Text(text = currentUser.username, style = MaterialTheme.typography.titleLarge)
                    Text(text = currentUser.bio, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)

                    if (currentUser.customId == AdminConstants.ADMIN_CUSTOM_ID || currentUser.userId == AdminConstants.ADMIN_USER_ID) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Surface(
                            color = MaterialTheme.colorScheme.primaryContainer,
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.AdminPanelSettings,
                                    contentDescription = "Admin Badge",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "حساب الإدارة والمسؤول العام (Administrator)",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        Card(
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onOpenAdminIdentifiers() }
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(14.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Stars,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onTertiaryContainer,
                                    modifier = Modifier.size(28.dp)
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "إدارة المعرفات المميزة (VIP)",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onTertiaryContainer
                                    )
                                    Text(
                                        text = "إنشاء، حجز، تعيين المعرفات الحصرية للمستخدمين",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.8f)
                                    )
                                }
                                Icon(
                                    imageVector = Icons.Default.ChevronRight,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onTertiaryContainer
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Unique 10-Digit ID Card Box
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                val clip = ClipData.newPlainText("User ID", currentUser.customId)
                                clipboard.setPrimaryClip(clip)
                                Toast.makeText(context, AppStrings.get("id_copied"), Toast.LENGTH_SHORT).show()
                            }
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.padding(12.dp)
                        ) {
                            Column {
                                Text(
                                    text = AppStrings.get("my_id"),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                                )
                                Text(
                                    text = currentUser.customId,
                                    style = MaterialTheme.typography.headlineSmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                            IconButton(onClick = {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                val clip = ClipData.newPlainText("User ID", currentUser.customId)
                                clipboard.setPrimaryClip(clip)
                                Toast.makeText(context, AppStrings.get("id_copied"), Toast.LENGTH_SHORT).show()
                            }) {
                                Icon(Icons.Default.ContentCopy, contentDescription = "Copy ID", tint = MaterialTheme.colorScheme.onPrimaryContainer)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedButton(
                        onClick = { isEditingProfile = true },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Edit, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(AppStrings.get("edit_profile"))
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Language & Preference Settings
        Text(text = AppStrings.get("settings"), style = MaterialTheme.typography.titleMedium)

        Spacer(modifier = Modifier.height(8.dp))

        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(2.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                // Language
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Language, contentDescription = null)
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(text = AppStrings.get("language"))
                    }

                    Row {
                        FilterChip(
                            selected = AppStrings.currentLanguage == "ar",
                            onClick = {
                                AppStrings.currentLanguage = "ar"
                                onUpdateSettings(settings?.copy(language = "ar") ?: UserSettingsEntity(userId = currentUser.userId, language = "ar"))
                            },
                            label = { Text("العربية") }
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        FilterChip(
                            selected = AppStrings.currentLanguage == "en",
                            onClick = {
                                AppStrings.currentLanguage = "en"
                                onUpdateSettings(settings?.copy(language = "en") ?: UserSettingsEntity(userId = currentUser.userId, language = "en"))
                            },
                            label = { Text("English") }
                        )
                    }
                }

                Divider(modifier = Modifier.padding(vertical = 12.dp))

                // Switch Account
                TextButton(
                    onClick = onOpenAccountSwitcher,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.SwitchAccount, contentDescription = null)
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(text = AppStrings.get("switch_account"), modifier = Modifier.weight(1f))
                }

                Divider(modifier = Modifier.padding(vertical = 12.dp))

                // Logout
                TextButton(
                    onClick = onLogout,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(text = AppStrings.get("logout"), modifier = Modifier.weight(1f))
                }

                Divider(modifier = Modifier.padding(vertical = 12.dp))

                // Delete Account
                TextButton(
                    onClick = { showDeleteConfirm = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.DeleteForever, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(text = AppStrings.get("delete_account"), color = MaterialTheme.colorScheme.error, modifier = Modifier.weight(1f))
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Dedicated Security & Firebase Auth Module Card
        Text(
            text = AppStrings.get("security_and_password"),
            style = MaterialTheme.typography.titleMedium
        )

        Spacer(modifier = Modifier.height(8.dp))

        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(2.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .background(
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                                shape = CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Security,
                            contentDescription = "Security",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "حماية الحساب وكلمة المرور",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.primaryContainer,
                                modifier = Modifier.padding(2.dp)
                            ) {
                                Text(
                                    text = if (currentUser.passwordHash.isNotBlank()) "محمي ومؤمن" else "غير محمي",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                )
                            }
                        }
                        Text(
                            text = AppStrings.get("verify_old_password_notice"),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                OutlinedButton(
                    onClick = {
                        currentPassInput = ""
                        newPassInput = ""
                        confirmNewPassInput = ""
                        changePassError = null
                        showChangePasswordDialog = true
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.LockReset, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (currentUser.passwordHash.isNotBlank()) AppStrings.get("change_password") else "تعيين كلمة مرور للحساب",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Linked Devices & Active Authentication Sessions Manager Card
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(2.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                // Header
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .background(
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                                shape = CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Devices,
                            contentDescription = "Linked Devices",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = AppStrings.get("linked_devices"),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.primaryContainer,
                                modifier = Modifier.padding(2.dp)
                            ) {
                                Text(
                                    text = "${activeSessions.size} نشطة",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                )
                            }
                        }
                        Text(
                            text = AppStrings.get("linked_devices_desc"),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Section: This Device (Current Session)
                Text(
                    text = AppStrings.get("this_device"),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(6.dp))

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .background(
                                    color = MaterialTheme.colorScheme.primaryContainer,
                                    shape = CircleShape
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.PhoneAndroid,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = currentSession?.deviceName ?: "هاتف Android الحالي",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = MaterialTheme.colorScheme.tertiaryContainer
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(6.dp)
                                                .background(MaterialTheme.colorScheme.primary, CircleShape)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = AppStrings.get("active_now"),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onTertiaryContainer
                                        )
                                    }
                                }
                            }
                            Text(
                                text = "${currentSession?.platform ?: "Android"} • ${currentSession?.clientApp ?: "ChatConnect v2.4"}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "IP: ${currentSession?.ipAddress ?: "192.168.1.108"} • ${currentSession?.location ?: "الرياض، المملكة العربية السعودية"}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.outline
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Section: Other Linked Devices
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "${AppStrings.get("other_devices")} (${otherSessions.size})",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                if (otherSessions.isEmpty()) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(14.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.CheckCircleOutline,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = AppStrings.get("no_other_devices"),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        otherSessions.forEach { session ->
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(38.dp)
                                                .background(
                                                    color = MaterialTheme.colorScheme.secondaryContainer,
                                                    shape = CircleShape
                                                ),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            val icon = when (session.deviceType.uppercase()) {
                                                "WEB" -> Icons.Default.Language
                                                "DESKTOP" -> Icons.Default.Computer
                                                "TABLET" -> Icons.Default.Tablet
                                                else -> Icons.Default.DevicesOther
                                            }
                                            Icon(
                                                imageVector = icon,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = session.deviceName,
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                            Text(
                                                text = "${session.platform} • ${session.clientApp}",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                            Text(
                                                text = "${TimeUtils.formatLastSeen(session.lastActiveAt)} • IP: ${session.ipAddress}",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.outline
                                            )
                                        }
                                        Spacer(modifier = Modifier.width(6.dp))
                                        IconButton(
                                            onClick = { sessionToRevoke = session },
                                            colors = IconButtonDefaults.iconButtonColors(
                                                contentColor = MaterialTheme.colorScheme.error
                                            )
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.LinkOff,
                                                contentDescription = AppStrings.get("revoke_session")
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Actions: Link Device & Log Out All Other Devices
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilledTonalButton(
                        onClick = { showLinkDeviceDialog = true },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.QrCodeScanner, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(AppStrings.get("link_device"), style = MaterialTheme.typography.labelLarge)
                    }

                    if (otherSessions.isNotEmpty()) {
                        OutlinedButton(
                            onClick = { showRevokeAllConfirm = true },
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = MaterialTheme.colorScheme.error
                            ),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.DeleteSweep, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(AppStrings.get("revoke_all_other"), style = MaterialTheme.typography.labelLarge)
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Encrypted Cloud Sync Card
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(2.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = Icons.Default.CloudSync,
                        contentDescription = "Cloud Persistence",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "المزامنة السحابية والتشفير",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = if (!currentUser.email.isNullOrBlank()) "حساب Google: ${currentUser.email}" else "معرف سحابي: ${currentUser.customId}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = "متصل",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "جميع الرسائل والحسابات والملفات الشخصية يتم حفظها ومزامنتها في السحابة مع تشفير تام للبيانات.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // About App (حول التطبيق) Card - Version 1.0 & Details
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(2.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .background(
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                                shape = CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = "About App",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = AppStrings.get("about_app"),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "تطبيق تواصل فوري آمن ومحمي",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = "1.0",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                Spacer(modifier = Modifier.height(12.dp))

                // App Information Details
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "اسم التطبيق",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "ChatConnect",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = AppStrings.get("app_version_label"),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "1.0 (Build 100)",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "بروتوكول التشفير",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "E2EE (AES-256 + DTLS-SRTP)",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF1E8E3E)
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "المكالمات والمحادثات",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "صوت وفيديو مشفرة حقيقية",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "فريق التطوير",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "ChatConnect Team",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = "جميع الحقوق محفوظة © 2026 ChatConnect. صُمم التطبيق ليوفر أقصى درجات الخصوصية والأمان لمحادثاتك ومكالماتك.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 16.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Security / Encryption Disclaimer Box
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(12.dp)
            ) {
                Icon(Icons.Default.Lock, contentDescription = "Security", tint = MaterialTheme.colorScheme.secondary)
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = AppStrings.get("encrypted_disclaimer"),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }
        }

        // Generous bottom spacer ensures entire content can be scrolled fully above navigation bar
        Spacer(modifier = Modifier.height(72.dp))
        Spacer(modifier = Modifier.navigationBarsPadding())
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text(AppStrings.get("delete_account")) },
            text = { Text(AppStrings.get("delete_account_confirm")) },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteConfirm = false
                        onDeleteAccount()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text(AppStrings.get("delete_account"))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text(AppStrings.get("cancel"))
                }
            }
        )
    }

    if (showChangePasswordDialog) {
        val hasExistingPassword = currentUser.passwordHash.isNotBlank()
        AlertDialog(
            onDismissRequest = {
                if (!isChangingPass) {
                    focusManager.clearFocus()
                    showChangePasswordDialog = false
                }
            },
            icon = {
                Icon(
                    imageVector = Icons.Default.LockReset,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(36.dp)
                )
            },
            title = {
                Text(
                    text = if (hasExistingPassword) AppStrings.get("change_password") else "تعيين كلمة مرور الحساب",
                    style = MaterialTheme.typography.titleLarge
                )
            },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    // Security verification notice box
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.VerifiedUser,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = AppStrings.get("verify_old_password_notice"),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    if (hasExistingPassword) {
                        OutlinedTextField(
                            value = currentPassInput,
                            onValueChange = {
                                currentPassInput = it
                                changePassError = null
                            },
                            label = { Text(AppStrings.get("current_password")) },
                            placeholder = { Text("أدخل كلمة المرور القديمة للتحقق") },
                            singleLine = true,
                            leadingIcon = { Icon(Icons.Default.VpnKey, contentDescription = null) },
                            trailingIcon = {
                                IconButton(onClick = { isCurrentPassVisible = !isCurrentPassVisible }) {
                                    Icon(
                                        imageVector = if (isCurrentPassVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                        contentDescription = null
                                    )
                                }
                            },
                            visualTransformation = if (isCurrentPassVisible) VisualTransformation.None else PasswordVisualTransformation(),
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                    }

                    OutlinedTextField(
                        value = newPassInput,
                        onValueChange = {
                            newPassInput = it
                            changePassError = null
                        },
                        label = { Text(AppStrings.get("new_password")) },
                        supportingText = {
                            Text(
                                text = "6 أحرف على الأقل (متطلبات أمان الحساب)",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        },
                        singleLine = true,
                        leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
                        trailingIcon = {
                            IconButton(onClick = { isNewPassVisible = !isNewPassVisible }) {
                                Icon(
                                    imageVector = if (isNewPassVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                    contentDescription = null
                                )
                            }
                        },
                        visualTransformation = if (isNewPassVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    OutlinedTextField(
                        value = confirmNewPassInput,
                        onValueChange = {
                            confirmNewPassInput = it
                            changePassError = null
                        },
                        label = { Text(AppStrings.get("confirm_password")) },
                        singleLine = true,
                        leadingIcon = { Icon(Icons.Default.LockReset, contentDescription = null) },
                        trailingIcon = {
                            IconButton(onClick = { isConfirmNewPassVisible = !isConfirmNewPassVisible }) {
                                Icon(
                                    imageVector = if (isConfirmNewPassVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                    contentDescription = null
                                )
                            }
                        },
                        visualTransformation = if (isConfirmNewPassVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth()
                    )

                    if (changePassError != null) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Surface(
                            color = MaterialTheme.colorScheme.errorContainer,
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ErrorOutline,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = changePassError!!,
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    }

                    if (isChangingPass) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = AppStrings.get("verifying_and_updating"),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        changePassError = null
                        if (hasExistingPassword && currentPassInput.isBlank()) {
                            changePassError = "الرجاء إدخال كلمة المرور القديمة للتحقق منها أولاً"
                        } else if (newPassInput.length < 6) {
                            changePassError = AppStrings.get("password_min_length")
                        } else if (hasExistingPassword && newPassInput == currentPassInput) {
                            changePassError = "يجب أن تكون كلمة المرور الجديدة مختلفة عن كلمة المرور القديمة"
                        } else if (newPassInput != confirmNewPassInput) {
                            changePassError = AppStrings.get("passwords_do_not_match")
                        } else {
                            focusManager.clearFocus()
                            isChangingPass = true
                            onChangePassword(currentPassInput, newPassInput) { success, errMsg ->
                                isChangingPass = false
                                if (success) {
                                    Toast.makeText(context, AppStrings.get("password_changed_success"), Toast.LENGTH_LONG).show()
                                    showChangePasswordDialog = false
                                    currentPassInput = ""
                                    newPassInput = ""
                                    confirmNewPassInput = ""
                                } else {
                                    changePassError = errMsg ?: AppStrings.get("incorrect_password")
                                }
                            }
                        }
                    },
                    enabled = !isChangingPass
                ) {
                    Text(if (isChangingPass) "جارٍ التحديث..." else "التحقق وتحديث الحساب")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        focusManager.clearFocus()
                        showChangePasswordDialog = false
                    },
                    enabled = !isChangingPass
                ) {
                    Text(AppStrings.get("cancel"))
                }
            }
        )
    }

    // Revoke Single Device Session Dialog
    sessionToRevoke?.let { session ->
        AlertDialog(
            onDismissRequest = { sessionToRevoke = null },
            icon = {
                Icon(
                    imageVector = Icons.Default.LinkOff,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(32.dp)
                )
            },
            title = {
                Text(
                    text = AppStrings.get("revoke_confirm_title"),
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Text(
                                text = session.deviceName,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "${session.platform} • ${session.clientApp}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "IP: ${session.ipAddress} • ${session.location}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.outline
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = AppStrings.get("revoke_confirm_msg"),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            },
            confirmButton = {
                Button(
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    onClick = {
                        val deviceName = session.deviceName
                        onRevokeSession(session)
                        sessionToRevoke = null
                        Toast.makeText(context, "${AppStrings.get("session_revoked_success")} ($deviceName)", Toast.LENGTH_SHORT).show()
                    }
                ) {
                    Text(AppStrings.get("revoke_session"))
                }
            },
            dismissButton = {
                TextButton(onClick = { sessionToRevoke = null }) {
                    Text(AppStrings.get("cancel"))
                }
            }
        )
    }

    // Revoke All Other Sessions Dialog
    if (showRevokeAllConfirm) {
        AlertDialog(
            onDismissRequest = { showRevokeAllConfirm = false },
            icon = {
                Icon(
                    imageVector = Icons.Default.DeleteSweep,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(32.dp)
                )
            },
            title = {
                Text(
                    text = AppStrings.get("revoke_all_confirm_title"),
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = AppStrings.get("revoke_all_confirm_msg"),
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                Button(
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    onClick = {
                        onRevokeAllOtherSessions()
                        showRevokeAllConfirm = false
                        Toast.makeText(context, AppStrings.get("all_sessions_revoked_success"), Toast.LENGTH_SHORT).show()
                    }
                ) {
                    Text(AppStrings.get("revoke_all_other"))
                }
            },
            dismissButton = {
                TextButton(onClick = { showRevokeAllConfirm = false }) {
                    Text(AppStrings.get("cancel"))
                }
            }
        )
    }

    // Link New Device Dialog
    if (showLinkDeviceDialog) {
        AlertDialog(
            onDismissRequest = { showLinkDeviceDialog = false },
            icon = {
                Icon(
                    imageVector = Icons.Default.QrCodeScanner,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(36.dp)
                )
            },
            title = {
                Text(
                    text = AppStrings.get("link_device"),
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    // QR Code simulation box
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        modifier = Modifier.padding(vertical = 8.dp)
                    ) {
                        Box(
                            modifier = Modifier.padding(20.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.QrCode2,
                                contentDescription = "QR Code",
                                tint = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.size(130.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = AppStrings.get("link_device_instruction"),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(14.dp))
                    Text(
                        text = "أو اختر ربط تجريبي فوري للتحقق من الميزة:",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    Button(
                        onClick = {
                            onLinkNewDevice("ChatConnect Web (Chrome)", "WEB", "Windows 11")
                            showLinkDeviceDialog = false
                            Toast.makeText(context, AppStrings.get("device_linked_success"), Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Default.Language, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(AppStrings.get("simulate_link_web"), style = MaterialTheme.typography.bodySmall)
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedButton(
                        onClick = {
                            onLinkNewDevice("ChatConnect Desktop (macOS)", "DESKTOP", "macOS Sonoma")
                            showLinkDeviceDialog = false
                            Toast.makeText(context, AppStrings.get("device_linked_success"), Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Default.Computer, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(AppStrings.get("simulate_link_desktop"), style = MaterialTheme.typography.bodySmall)
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showLinkDeviceDialog = false }) {
                    Text(AppStrings.get("close"))
                }
            }
        )
    }
}
