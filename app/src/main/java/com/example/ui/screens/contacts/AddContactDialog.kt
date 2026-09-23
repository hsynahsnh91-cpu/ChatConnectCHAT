package com.example.ui.screens.contacts

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.unit.dp
import com.example.utils.AppStrings

@Composable
fun AddContactDialog(
    onAdd: (customId: String, nickname: String, onError: (String) -> Unit) -> Unit,
    onDismiss: () -> Unit
) {
    val focusManager = LocalFocusManager.current
    var customId by remember { mutableStateOf("") }
    var nickname by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = {
            focusManager.clearFocus()
            onDismiss()
        },
        title = { Text(text = AppStrings.get("add_contact")) },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = AppStrings.get("enter_custom_id"),
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(modifier = Modifier.height(4.dp))
                OutlinedTextField(
                    value = customId,
                    onValueChange = { customId = it.trim() },
                    placeholder = { Text("مثال: 7392846152") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = AppStrings.get("enter_nickname"),
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(modifier = Modifier.height(4.dp))
                OutlinedTextField(
                    value = nickname,
                    onValueChange = { nickname = it },
                    placeholder = { Text("اختياري") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                if (errorMessage != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = errorMessage!!,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (customId.isBlank()) {
                        errorMessage = AppStrings.get("user_not_found")
                    } else {
                        focusManager.clearFocus()
                        onAdd(customId, nickname) { err ->
                            errorMessage = err
                        }
                    }
                }
            ) {
                Text(text = AppStrings.get("add"))
            }
        },
        dismissButton = {
            TextButton(onClick = {
                focusManager.clearFocus()
                onDismiss()
            }) {
                Text(text = AppStrings.get("cancel"))
            }
        }
    )
}
