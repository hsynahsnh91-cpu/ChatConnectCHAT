package com.example.ui.screens.group

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Group
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.example.data.local.entities.ContactEntity
import com.example.data.local.entities.UserEntity
import com.example.ui.components.AvatarImage
import com.example.utils.AppStrings

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateGroupScreen(
    contacts: List<ContactEntity>,
    allUsers: List<UserEntity>,
    onBackClick: () -> Unit,
    onCreateGroup: (name: String, iconUri: String?, selectedUserIds: List<String>) -> Unit
) {
    var groupName by remember { mutableStateOf("") }
    var selectedImageUri by remember { mutableStateOf<String?>(null) }
    val selectedMembers = remember { mutableStateListOf<String>() }

    val userMap = remember(allUsers) { allUsers.associateBy { it.userId } }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            selectedImageUri = uri.toString()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                title = { Text(AppStrings.get("create_group")) }
            )
        },
        floatingActionButton = {
            if (groupName.isNotBlank() && selectedMembers.isNotEmpty()) {
                FloatingActionButton(
                    onClick = {
                        onCreateGroup(groupName.trim(), selectedImageUri, selectedMembers.toList())
                    },
                    containerColor = MaterialTheme.colorScheme.primary
                ) {
                    Icon(Icons.Default.Check, contentDescription = "Create", tint = MaterialTheme.colorScheme.onPrimary)
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.tertiaryContainer)
                        .clickable {
                            photoPickerLauncher.launch(
                                androidx.activity.result.PickVisualMediaRequest(
                                    ActivityResultContracts.PickVisualMedia.ImageOnly
                                )
                            )
                        }
                ) {
                    if (selectedImageUri != null) {
                        AvatarImage(uri = selectedImageUri, name = groupName, size = 64.dp, isGroup = true)
                    } else {
                        Icon(Icons.Default.AddAPhoto, contentDescription = null, tint = MaterialTheme.colorScheme.onTertiaryContainer)
                    }
                }

                Spacer(modifier = Modifier.width(16.dp))

                OutlinedTextField(
                    value = groupName,
                    onValueChange = { groupName = it },
                    label = { Text(AppStrings.get("group_name")) },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "${AppStrings.get("select_members")} (${selectedMembers.size})",
                style = MaterialTheme.typography.titleMedium
            )

            Spacer(modifier = Modifier.height(12.dp))

            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(contacts) { contact ->
                    val user = userMap[contact.contactUserId]
                    val isSelected = selectedMembers.contains(contact.contactUserId)

                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                if (isSelected) selectedMembers.remove(contact.contactUserId)
                                else selectedMembers.add(contact.contactUserId)
                            }
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(12.dp)
                        ) {
                            AvatarImage(uri = user?.profilePicUri, name = contact.nickname, size = 48.dp)
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(text = contact.nickname, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                            Checkbox(
                                checked = isSelected,
                                onCheckedChange = { checked ->
                                    if (checked) selectedMembers.add(contact.contactUserId)
                                    else selectedMembers.remove(contact.contactUserId)
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}
