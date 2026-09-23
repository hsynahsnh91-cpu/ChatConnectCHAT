package com.example.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.example.data.local.entities.UserEntity
import com.example.utils.AppStrings
import com.example.utils.PasswordUtils

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountSwitcherBottomSheet(
    currentUser: UserEntity?,
    allUsers: List<UserEntity>,
    onSelectUser: (UserEntity, String) -> Unit,
    onAddNewUser: () -> Unit,
    onLogout: () -> Unit = onAddNewUser,
    onDismiss: () -> Unit
) {
    var userPromptingForPassword by remember { mutableStateOf<UserEntity?>(null) }
    var enteredPassword by remember { mutableStateOf("") }
    var isPasswordVisible by remember { mutableStateOf(false) }
    var passwordError by remember { mutableStateOf<String?>(null) }
    val focusManager = LocalFocusManager.current

    ModalBottomSheet(onDismissRequest = {
        focusManager.clearFocus()
        onDismiss()
    }) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                text = AppStrings.get("switch_account"),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(allUsers) { user ->
                    val isSelected = currentUser?.userId == user.userId
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                if (isSelected) {
                                    onDismiss()
                                } else if (user.passwordHash.isNotBlank()) {
                                    userPromptingForPassword = user
                                    enteredPassword = ""
                                    passwordError = null
                                } else {
                                    onSelectUser(user, "")
                                    onDismiss()
                                }
                            }
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(12.dp)
                        ) {
                            AvatarImage(
                                uri = user.profilePicUri,
                                name = user.username,
                                size = 48.dp
                            )

                            Spacer(modifier = Modifier.width(12.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = user.username,
                                        style = MaterialTheme.typography.titleMedium
                                    )
                                    if (user.passwordHash.isNotBlank()) {
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Icon(
                                            imageVector = Icons.Default.Lock,
                                            contentDescription = "Protected",
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                                Text(
                                    text = "ID: ${user.customId}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            if (isSelected) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = "Active",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }

                item {
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = {
                            onAddNewUser()
                            onDismiss()
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(imageVector = Icons.Default.Add, contentDescription = "Add")
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(text = AppStrings.get("register"))
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    TextButton(
                        onClick = {
                            onLogout()
                            onDismiss()
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Logout,
                            contentDescription = "Logout",
                            tint = MaterialTheme.colorScheme.error
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = AppStrings.get("logout"),
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        }
    }

    if (userPromptingForPassword != null) {
        val target = userPromptingForPassword!!
        AlertDialog(
            onDismissRequest = {
                focusManager.clearFocus()
                userPromptingForPassword = null
            },
            icon = {
                Icon(
                    imageVector = Icons.Default.Security,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(32.dp)
                )
            },
            title = {
                Text(text = "تأكيد كلمة المرور")
            },
            text = {
                Column {
                    Text(
                        text = "الحساب (${target.username}) محمي بكلمة مرور. أدخل كلمة المرور لتسجيل الدخول بأمان:",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = enteredPassword,
                        onValueChange = {
                            enteredPassword = it
                            passwordError = null
                        },
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

                    if (passwordError != null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = passwordError!!,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (target.passwordHash.isNotBlank() && !PasswordUtils.verifyPassword(enteredPassword, target.passwordHash)) {
                            passwordError = AppStrings.get("incorrect_password")
                        } else {
                            focusManager.clearFocus()
                            val u = target
                            val pass = enteredPassword
                            userPromptingForPassword = null
                            onSelectUser(u, pass)
                            onDismiss()
                        }
                    }
                ) {
                    Text("فتح الحساب")
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    focusManager.clearFocus()
                    userPromptingForPassword = null
                }) {
                    Text(AppStrings.get("cancel"))
                }
            }
        )
    }
}
