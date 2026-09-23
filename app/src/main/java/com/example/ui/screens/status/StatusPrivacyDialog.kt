package com.example.ui.screens.status

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.data.local.entities.ContactEntity
import com.example.data.local.entities.UserEntity
import com.example.data.local.entities.UserSettingsEntity
import com.example.ui.components.AvatarImage
import com.example.utils.AppStrings

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatusPrivacyDialog(
    currentSettings: UserSettingsEntity?,
    contacts: List<ContactEntity>,
    allUsers: List<UserEntity>,
    onSavePrivacy: (privacyType: String, excludedIds: String, includedIds: String) -> Unit,
    onDismiss: () -> Unit
) {
    val userMap = remember(allUsers) { allUsers.associateBy { it.userId } }

    var selectedPrivacy by remember {
        mutableStateOf(currentSettings?.statusPrivacy ?: "MY_CONTACTS")
    }

    val initialExcluded = remember {
        currentSettings?.statusPrivacyExcludedIds
            ?.split(",")
            ?.map { it.trim() }
            ?.filter { it.isNotEmpty() }
            ?.toSet() ?: emptySet()
    }
    val initialIncluded = remember {
        currentSettings?.statusPrivacyIncludedIds
            ?.split(",")
            ?.map { it.trim() }
            ?.filter { it.isNotEmpty() }
            ?.toSet() ?: emptySet()
    }

    var excludedUserIds by remember { mutableStateOf(initialExcluded) }
    var includedUserIds by remember { mutableStateOf(initialIncluded) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = AppStrings.get("status_privacy"),
                style = MaterialTheme.typography.titleLarge
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 450.dp)
            ) {
                Text(
                    text = AppStrings.get("who_can_see_status"),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Option 1: My contacts
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { selectedPrivacy = "MY_CONTACTS" }
                        .padding(vertical = 8.dp)
                ) {
                    RadioButton(
                        selected = selectedPrivacy == "MY_CONTACTS",
                        onClick = { selectedPrivacy = "MY_CONTACTS" }
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = AppStrings.get("my_contacts"),
                        style = MaterialTheme.typography.bodyLarge
                    )
                }

                // Option 2: My contacts except...
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { selectedPrivacy = "CONTACTS_EXCEPT" }
                        .padding(vertical = 8.dp)
                ) {
                    RadioButton(
                        selected = selectedPrivacy == "CONTACTS_EXCEPT",
                        onClick = { selectedPrivacy = "CONTACTS_EXCEPT" }
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = AppStrings.get("my_contacts_except"),
                            style = MaterialTheme.typography.bodyLarge
                        )
                        if (selectedPrivacy == "CONTACTS_EXCEPT" && excludedUserIds.isNotEmpty()) {
                            Text(
                                text = "${excludedUserIds.size} ${AppStrings.get("contacts_selected")}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }

                // Option 3: Only share with...
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { selectedPrivacy = "ONLY_SHARE_WITH" }
                        .padding(vertical = 8.dp)
                ) {
                    RadioButton(
                        selected = selectedPrivacy == "ONLY_SHARE_WITH",
                        onClick = { selectedPrivacy = "ONLY_SHARE_WITH" }
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = AppStrings.get("only_share_with"),
                            style = MaterialTheme.typography.bodyLarge
                        )
                        if (selectedPrivacy == "ONLY_SHARE_WITH" && includedUserIds.isNotEmpty()) {
                            Text(
                                text = "${includedUserIds.size} ${AppStrings.get("contacts_selected")}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }

                // Contact selection list if selective privacy chosen
                if (selectedPrivacy == "CONTACTS_EXCEPT" || selectedPrivacy == "ONLY_SHARE_WITH") {
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                    Text(
                        text = if (selectedPrivacy == "CONTACTS_EXCEPT")
                            "حدد جهات الاتصال المراد استثناؤها:"
                        else
                            "حدد جهات الاتصال المسموح لها بالمشاهدة:",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    if (contacts.isEmpty()) {
                        Text(
                            text = "لا توجد جهات اتصال محفوظة لديك.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                        ) {
                            items(contacts) { contact ->
                                val user = userMap[contact.contactUserId]
                                val contactName = contact.nickname.ifBlank { user?.username ?: contact.contactUserId }
                                val isChecked = if (selectedPrivacy == "CONTACTS_EXCEPT") {
                                    excludedUserIds.contains(contact.contactUserId)
                                } else {
                                    includedUserIds.contains(contact.contactUserId)
                                }

                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            if (selectedPrivacy == "CONTACTS_EXCEPT") {
                                                excludedUserIds = if (isChecked) {
                                                    excludedUserIds - contact.contactUserId
                                                } else {
                                                    excludedUserIds + contact.contactUserId
                                                }
                                            } else {
                                                includedUserIds = if (isChecked) {
                                                    includedUserIds - contact.contactUserId
                                                } else {
                                                    includedUserIds + contact.contactUserId
                                                }
                                            }
                                        }
                                        .padding(vertical = 6.dp)
                                ) {
                                    AvatarImage(
                                        uri = user?.profilePicUri,
                                        name = contactName,
                                        size = 36.dp
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = contactName,
                                            style = MaterialTheme.typography.bodyMedium
                                        )
                                        if (user?.customId != null) {
                                            Text(
                                                text = "ID: ${user.customId}",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                    Checkbox(
                                        checked = isChecked,
                                        onCheckedChange = { checked ->
                                            if (selectedPrivacy == "CONTACTS_EXCEPT") {
                                                excludedUserIds = if (checked) excludedUserIds + contact.contactUserId else excludedUserIds - contact.contactUserId
                                            } else {
                                                includedUserIds = if (checked) includedUserIds + contact.contactUserId else includedUserIds - contact.contactUserId
                                            }
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSavePrivacy(
                        selectedPrivacy,
                        excludedUserIds.joinToString(","),
                        includedUserIds.joinToString(",")
                    )
                    onDismiss()
                }
            ) {
                Text(AppStrings.get("save_privacy"))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(AppStrings.get("cancel"))
            }
        }
    )
}
