package com.example.ui.screens.auth

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
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.entities.UserEntity
import com.example.ui.components.AvatarImage
import com.example.utils.AppStrings

@Composable
fun AuthScreen(
    allUsers: List<UserEntity>,
    onRegister: (String, String, String?) -> Unit,
    onLoginCustomId: (String, String) -> Unit,
    onQuickSwitch: (UserEntity, String) -> Unit,
    onGoogleSignIn: (onResult: (Boolean, String?) -> Unit) -> Unit = { it(false, null) },
    onAnonymousFirebaseSignIn: (String) -> Unit = {},
    isFirestoreConnected: Boolean = true
) {
    var isRegisterTab by remember { mutableStateOf(true) }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var isPasswordVisible by remember { mutableStateOf(false) }
    var isConfirmPasswordVisible by remember { mutableStateOf(false) }

    var customIdInput by remember { mutableStateOf("") }
    var loginPassword by remember { mutableStateOf("") }
    var isLoginPasswordVisible by remember { mutableStateOf(false) }

    var userToUnlock by remember { mutableStateOf<UserEntity?>(null) }
    var unlockPassword by remember { mutableStateOf("") }
    var isUnlockPasswordVisible by remember { mutableStateOf(false) }
    var unlockError by remember { mutableStateOf<String?>(null) }

    var selectedImageUri by remember { mutableStateOf<String?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isAuthenticating by remember { mutableStateOf(false) }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            selectedImageUri = uri.toString()
        }
    }

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .imePadding()
            .padding(16.dp)
    ) {
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 480.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(24.dp)
            ) {
                // App Brand Header
                Icon(
                    imageVector = Icons.Default.Forum,
                    contentDescription = "App Logo",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(52.dp)
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = AppStrings.get("app_name"),
                    style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface
                )

                Spacer(modifier = Modifier.height(4.dp))

                // End-to-End Encrypted Secure Connection Status Badge
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.padding(top = 4.dp, bottom = 16.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = "Encrypted",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "محادثات ومكالمات مشفرة وتواصل آمن",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Primary Google Sign-In Button
                Button(
                    onClick = {
                        errorMessage = null
                        isAuthenticating = true
                        onGoogleSignIn { success, err ->
                            isAuthenticating = false
                            if (!success && !err.isNullOrBlank()) {
                                errorMessage = err
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.White,
                        contentColor = Color(0xFF1F1F1F)
                    ),
                    shape = RoundedCornerShape(12.dp),
                    elevation = ButtonDefaults.buttonElevation(defaultElevation = 2.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .testTag("google_sign_in_button"),
                    enabled = !isAuthenticating
                ) {
                    if (isAuthenticating) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(22.dp),
                            color = MaterialTheme.colorScheme.primary,
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "جارٍ التحقق...",
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFF1F1F1F)
                        )
                    } else {
                        // Google "G" visual badge
                        Surface(
                            shape = CircleShape,
                            color = Color(0xFF4285F4),
                            modifier = Modifier.size(26.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = "G",
                                    color = Color.White,
                                    fontWeight = FontWeight.Black,
                                    fontSize = 15.sp,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "تسجيل الدخول عبر Google",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 15.sp,
                            color = Color(0xFF1F1F1F)
                        )
                    }
                }

                if (errorMessage != null) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.8f),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = errorMessage!!,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            style = MaterialTheme.typography.bodySmall,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Divider with text
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    HorizontalDivider(modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.outlineVariant)
                    Text(
                        text = "  أو تسجيل تقليدي  ",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    HorizontalDivider(modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.outlineVariant)
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Tab Switcher (Register / Login)
                Row(modifier = Modifier.fillMaxWidth()) {
                    FilterChip(
                        selected = isRegisterTab,
                        onClick = { isRegisterTab = true },
                        label = { Text(AppStrings.get("register")) },
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    FilterChip(
                        selected = !isRegisterTab,
                        onClick = { isRegisterTab = false },
                        label = { Text(AppStrings.get("login")) },
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                if (isRegisterTab) {
                    // Profile photo picker
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(88.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primaryContainer)
                            .clickable {
                                photoPickerLauncher.launch(
                                    androidx.activity.result.PickVisualMediaRequest(
                                        ActivityResultContracts.PickVisualMedia.ImageOnly
                                    )
                                )
                            }
                    ) {
                        if (selectedImageUri != null) {
                            AvatarImage(uri = selectedImageUri, name = username, size = 88.dp)
                        } else {
                            Icon(
                                imageVector = Icons.Default.AddAPhoto,
                                contentDescription = "Pick Photo",
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.size(32.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    OutlinedTextField(
                        value = username,
                        onValueChange = { username = it },
                        label = { Text(AppStrings.get("enter_username")) },
                        singleLine = true,
                        leadingIcon = {
                            Icon(Icons.Default.Person, contentDescription = null)
                        },
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = { Text(AppStrings.get("enter_password")) },
                        singleLine = true,
                        leadingIcon = {
                            Icon(Icons.Default.Lock, contentDescription = null)
                        },
                        trailingIcon = {
                            IconButton(onClick = { isPasswordVisible = !isPasswordVisible }) {
                                Icon(
                                    imageVector = if (isPasswordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                    contentDescription = if (isPasswordVisible) "Hide password" else "Show password"
                                )
                            }
                        },
                        visualTransformation = if (isPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = confirmPassword,
                        onValueChange = { confirmPassword = it },
                        label = { Text(AppStrings.get("confirm_password")) },
                        singleLine = true,
                        leadingIcon = {
                            Icon(Icons.Default.LockReset, contentDescription = null)
                        },
                        trailingIcon = {
                            IconButton(onClick = { isConfirmPasswordVisible = !isConfirmPasswordVisible }) {
                                Icon(
                                    imageVector = if (isConfirmPasswordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                    contentDescription = if (isConfirmPasswordVisible) "Hide password" else "Show password"
                                )
                            }
                        },
                        visualTransformation = if (isConfirmPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(20.dp))

                    Button(
                        onClick = {
                            errorMessage = null
                            if (username.isBlank()) {
                                errorMessage = "الرجاء إدخال اسمك"
                            } else if (password.length < 4) {
                                errorMessage = AppStrings.get("password_min_length")
                            } else if (password != confirmPassword) {
                                errorMessage = AppStrings.get("passwords_do_not_match")
                            } else {
                                onRegister(username.trim(), password, selectedImageUri)
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("register_button")
                    ) {
                        Icon(imageVector = Icons.Default.PersonAdd, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(text = AppStrings.get("register"))
                    }
                } else {
                    OutlinedTextField(
                        value = customIdInput,
                        onValueChange = { customIdInput = it },
                        label = { Text(AppStrings.get("enter_custom_id")) },
                        placeholder = { Text("مثال: 7392846152") },
                        singleLine = true,
                        leadingIcon = {
                            Icon(Icons.Default.VpnKey, contentDescription = null)
                        },
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = loginPassword,
                        onValueChange = { loginPassword = it },
                        label = { Text(AppStrings.get("enter_password")) },
                        singleLine = true,
                        leadingIcon = {
                            Icon(Icons.Default.Lock, contentDescription = null)
                        },
                        trailingIcon = {
                            IconButton(onClick = { isLoginPasswordVisible = !isLoginPasswordVisible }) {
                                Icon(
                                    imageVector = if (isLoginPasswordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                    contentDescription = if (isLoginPasswordVisible) "Hide password" else "Show password"
                                )
                            }
                        },
                        visualTransformation = if (isLoginPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(20.dp))

                    Button(
                        onClick = {
                            errorMessage = null
                            if (customIdInput.isBlank()) {
                                errorMessage = "أدخل الـ ID المكوّن من 10 أرقام"
                            } else {
                                onLoginCustomId(customIdInput.trim(), loginPassword)
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("login_button")
                    ) {
                        Icon(imageVector = Icons.Default.VpnKey, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(text = AppStrings.get("login"))
                    }
                }

                if (errorMessage != null) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = errorMessage!!,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        textAlign = TextAlign.Center
                    )
                }

                // Saved Accounts on this device (protected by password)
                val savedAccounts = remember(allUsers) {
                    allUsers.filter {
                        it.userId != com.example.utils.AdminConstants.ADMIN_USER_ID &&
                                it.customId != com.example.utils.AdminConstants.ADMIN_CUSTOM_ID
                    }
                }

                if (savedAccounts.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(20.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "الحسابات المسجلة على هذا الهاتف (محمية بكلمة مرور):",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    savedAccounts.forEach { user ->
                        OutlinedButton(
                            onClick = {
                                if (user.passwordHash.isNotBlank()) {
                                    userToUnlock = user
                                    unlockPassword = ""
                                    unlockError = null
                                } else {
                                    onQuickSwitch(user, "")
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 3.dp),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(
                                imageVector = if (user.passwordHash.isNotBlank()) Icons.Default.Lock else Icons.Default.AccountCircle,
                                contentDescription = null,
                                tint = if (user.passwordHash.isNotBlank()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "${user.username} (ID: ${user.customId})",
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.weight(1f)
                            )
                            if (user.passwordHash.isNotBlank()) {
                                Surface(
                                    color = MaterialTheme.colorScheme.primaryContainer,
                                    shape = RoundedCornerShape(6.dp)
                                ) {
                                    Text(
                                        text = "محمي",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Secure Unlock Dialog for switching/opening saved accounts
    if (userToUnlock != null) {
        val targetUser = userToUnlock!!
        AlertDialog(
            onDismissRequest = { userToUnlock = null },
            icon = {
                Icon(
                    imageVector = Icons.Default.Security,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(32.dp)
                )
            },
            title = {
                Text(
                    text = "فتح الحساب المحمي",
                    style = MaterialTheme.typography.titleLarge
                )
            },
            text = {
                Column {
                    Text(
                        text = "الحساب (${targetUser.username}) محمي بكلمة مرور. يرجى إدخال كلمة المرور لحماية البيانات والمحادثات:",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    OutlinedTextField(
                        value = unlockPassword,
                        onValueChange = {
                            unlockPassword = it
                            unlockError = null
                        },
                        label = { Text(AppStrings.get("enter_password")) },
                        singleLine = true,
                        leadingIcon = {
                            Icon(Icons.Default.Lock, contentDescription = null)
                        },
                        trailingIcon = {
                            IconButton(onClick = { isUnlockPasswordVisible = !isUnlockPasswordVisible }) {
                                Icon(
                                    imageVector = if (isUnlockPasswordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                    contentDescription = if (isUnlockPasswordVisible) "Hide password" else "Show password"
                                )
                            }
                        },
                        visualTransformation = if (isUnlockPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth()
                    )

                    if (unlockError != null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = unlockError!!,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (targetUser.passwordHash.isNotBlank() && !com.example.utils.PasswordUtils.verifyPassword(unlockPassword, targetUser.passwordHash)) {
                            unlockError = AppStrings.get("incorrect_password")
                        } else {
                            val u = targetUser
                            val pass = unlockPassword
                            userToUnlock = null
                            onQuickSwitch(u, pass)
                        }
                    }
                ) {
                    Text("دخول الحساب")
                }
            },
            dismissButton = {
                TextButton(onClick = { userToUnlock = null }) {
                    Text(AppStrings.get("cancel"))
                }
            }
        )
    }
}
