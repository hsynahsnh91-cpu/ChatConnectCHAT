package com.example.ui.screens.admin

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.local.entities.SpecialIdentifierEntity
import com.example.data.local.entities.UserEntity
import com.example.service.admin.SpecialIdentifierGenerator
import com.example.ui.components.AvatarImage
import com.example.utils.TimeUtils

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminSpecialIdentifierDialog(
    identifiers: List<SpecialIdentifierEntity>,
    allUsers: List<UserEntity>,
    onCreateIdentifier: (identifier: String, category: String, notes: String?, onResult: (Boolean, String?) -> Unit) -> Unit,
    onReserveIdentifier: (id: String, reservedFor: String, notes: String?, onResult: (Boolean, String?) -> Unit) -> Unit,
    onAssignIdentifier: (id: String, targetUserId: String, onResult: (Boolean, String?) -> Unit) -> Unit,
    onReleaseIdentifier: (id: String, onResult: (Boolean, String?) -> Unit) -> Unit,
    onDisableIdentifier: (id: String, reason: String?, onResult: (Boolean, String?) -> Unit) -> Unit,
    onDeleteIdentifier: (id: String, onResult: (Boolean, String?) -> Unit) -> Unit,
    onDismiss: () -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    var selectedStatusFilter by remember { mutableStateOf<String?>("ALL") }

    var showCreateDialog by remember { mutableStateOf(false) }
    var itemToAssign by remember { mutableStateOf<SpecialIdentifierEntity?>(null) }
    var itemToReserve by remember { mutableStateOf<SpecialIdentifierEntity?>(null) }
    var itemToDisable by remember { mutableStateOf<SpecialIdentifierEntity?>(null) }
    var itemToDelete by remember { mutableStateOf<SpecialIdentifierEntity?>(null) }

    var feedbackMessage by remember { mutableStateOf<String?>(null) }
    var isFeedbackError by remember { mutableStateOf(false) }

    val filteredList = remember(identifiers, searchQuery, selectedStatusFilter) {
        identifiers.filter { item ->
            val matchesFilter = when (selectedStatusFilter) {
                "ALL" -> true
                else -> item.status == selectedStatusFilter
            }
            val q = searchQuery.trim().lowercase()
            val matchesSearch = q.isEmpty() ||
                    item.identifier.lowercase().contains(q) ||
                    (item.assignedUsername?.lowercase()?.contains(q) == true) ||
                    (item.reservedForName?.lowercase()?.contains(q) == true) ||
                    (item.notes?.lowercase()?.contains(q) == true)
            matchesFilter && matchesSearch
        }
    }

    val totalCount = identifiers.size
    val availableCount = identifiers.count { it.status == SpecialIdentifierEntity.STATUS_AVAILABLE }
    val reservedCount = identifiers.count { it.status == SpecialIdentifierEntity.STATUS_RESERVED }
    val assignedCount = identifiers.count { it.status == SpecialIdentifierEntity.STATUS_ASSIGNED }
    val disabledCount = identifiers.count { it.status == SpecialIdentifierEntity.STATUS_DISABLED }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp, vertical = 24.dp),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                // Header
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier.size(44.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.AdminPanelSettings,
                                    contentDescription = "Admin Special Identifiers",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(26.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "إدارة المعرفات المميزة (VIP)",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "إنشاء وتخصيص وحجز المعرفات للمستخدمين",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Feedback Alert
                if (feedbackMessage != null) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (isFeedbackError) MaterialTheme.colorScheme.errorContainer else Color(0xFFDCFCE7),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(12.dp)
                        ) {
                            Icon(
                                imageVector = if (isFeedbackError) Icons.Default.Error else Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = if (isFeedbackError) MaterialTheme.colorScheme.error else Color(0xFF16A34A),
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = feedbackMessage!!,
                                style = MaterialTheme.typography.bodySmall,
                                color = if (isFeedbackError) MaterialTheme.colorScheme.onErrorContainer else Color(0xFF14532D),
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(
                                onClick = { feedbackMessage = null },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(
                                    Icons.Default.Close,
                                    contentDescription = "Dismiss",
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }

                // Stats Dashboard Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    StatBadge(label = "الكل", count = totalCount, color = MaterialTheme.colorScheme.primary, modifier = Modifier.weight(1f))
                    StatBadge(label = "متاح", count = availableCount, color = Color(0xFF16A34A), modifier = Modifier.weight(1f))
                    StatBadge(label = "محجوز", count = reservedCount, color = Color(0xFFD97706), modifier = Modifier.weight(1f))
                    StatBadge(label = "معين", count = assignedCount, color = Color(0xFF2563EB), modifier = Modifier.weight(1f))
                    StatBadge(label = "معطل", count = disabledCount, color = Color(0xFFDC2626), modifier = Modifier.weight(1f))
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Search and Create Button Row
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text("بحث عن معرف، مستخدم، أو ملاحظة...") },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { searchQuery = "" }) {
                                    Icon(Icons.Default.Clear, contentDescription = "Clear")
                                }
                            }
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(52.dp)
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    Button(
                        onClick = { showCreateDialog = true },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.height(52.dp),
                        contentPadding = PaddingValues(horizontal = 16.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("إنشاء", fontWeight = FontWeight.Bold)
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Filter Chips
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    val filterOptions = listOf(
                        "ALL" to "الكل",
                        SpecialIdentifierEntity.STATUS_AVAILABLE to "متاح",
                        SpecialIdentifierEntity.STATUS_RESERVED to "محجوز",
                        SpecialIdentifierEntity.STATUS_ASSIGNED to "معين",
                        SpecialIdentifierEntity.STATUS_DISABLED to "معطل"
                    )
                    filterOptions.forEach { (statusKey, label) ->
                        val isSelected = selectedStatusFilter == statusKey
                        FilterChip(
                            selected = isSelected,
                            onClick = { selectedStatusFilter = statusKey },
                            label = { Text(label, fontSize = 12.sp) }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // List of Identifiers
                if (filteredList.isEmpty()) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = Icons.Default.Badge,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                                modifier = Modifier.size(64.dp)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = if (searchQuery.isNotBlank()) "لا توجد نتائج تطابق بحثك" else "لا توجد معرفات مميزة في هذه الفئة",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                    ) {
                        items(filteredList, key = { it.id }) { item ->
                            SpecialIdentifierCard(
                                item = item,
                                onAssignClick = { itemToAssign = item },
                                onReserveClick = { itemToReserve = item },
                                onReleaseClick = {
                                    onReleaseIdentifier(item.id) { success, err ->
                                        if (success) {
                                            feedbackMessage = "تم تحرير المعرف ${item.identifier} وإعادته إلى الحالة المتاحة."
                                            isFeedbackError = false
                                        } else {
                                            feedbackMessage = err ?: "فشل تحرير المعرف"
                                            isFeedbackError = true
                                        }
                                    }
                                },
                                onDisableClick = { itemToDisable = item },
                                onDeleteClick = { itemToDelete = item }
                            )
                        }
                    }
                }
            }
        }
    }

    // Sub-dialog: Create Special Identifier
    if (showCreateDialog) {
        CreateSpecialIdentifierSubDialog(
            existingIdentifiers = identifiers.map { it.identifier },
            onCreate = { idText, category, notes ->
                onCreateIdentifier(idText, category, notes) { success, err ->
                    if (success) {
                        showCreateDialog = false
                        feedbackMessage = "تم إنشاء المعرف المميز $idText بنجاح!"
                        isFeedbackError = false
                    } else {
                        feedbackMessage = err ?: "فشل إنشاء المعرف المميز"
                        isFeedbackError = true
                    }
                }
            },
            onDismiss = { showCreateDialog = false }
        )
    }

    // Sub-dialog: Assign Identifier to User
    itemToAssign?.let { item ->
        AssignIdentifierSubDialog(
            identifier = item,
            users = allUsers,
            onAssign = { targetUserId ->
                onAssignIdentifier(item.id, targetUserId) { success, err ->
                    if (success) {
                        itemToAssign = null
                        feedbackMessage = "تم تعيين المعرف المميز ${item.identifier} للمستخدم بنجاح!"
                        isFeedbackError = false
                    } else {
                        feedbackMessage = err ?: "فشل تعيين المعرف"
                        isFeedbackError = true
                    }
                }
            },
            onDismiss = { itemToAssign = null }
        )
    }

    // Sub-dialog: Reserve Identifier
    itemToReserve?.let { item ->
        ReserveIdentifierSubDialog(
            identifier = item,
            onReserve = { reservedName, notes ->
                onReserveIdentifier(item.id, reservedName, notes) { success, err ->
                    if (success) {
                        itemToReserve = null
                        feedbackMessage = "تم حجز المعرف ${item.identifier} لصالح $reservedName بنجاح."
                        isFeedbackError = false
                    } else {
                        feedbackMessage = err ?: "فشل حجز المعرف"
                        isFeedbackError = true
                    }
                }
            },
            onDismiss = { itemToReserve = null }
        )
    }

    // Sub-dialog: Disable Identifier
    itemToDisable?.let { item ->
        AlertDialog(
            onDismissRequest = { itemToDisable = null },
            title = { Text("تعطيل المعرف المميز") },
            text = {
                Text("هل أنت متأكد من رغبتك في تعطيل المعرف ${item.identifier}؟ لن يتمكن أي مستخدم من استخدامه أو البحث عنه.")
            },
            confirmButton = {
                Button(
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    onClick = {
                        onDisableIdentifier(item.id, "معطل بواسطة الإدارة") { success, err ->
                            if (success) {
                                itemToDisable = null
                                feedbackMessage = "تم تعطيل المعرف ${item.identifier} بنجاح."
                                isFeedbackError = false
                            } else {
                                feedbackMessage = err ?: "فشل تعطيل المعرف"
                                isFeedbackError = true
                            }
                        }
                    }
                ) {
                    Text("تعطيل المعرف")
                }
            },
            dismissButton = {
                TextButton(onClick = { itemToDisable = null }) {
                    Text("إلغاء")
                }
            }
        )
    }

    // Sub-dialog: Delete Identifier
    itemToDelete?.let { item ->
        AlertDialog(
            onDismissRequest = { itemToDelete = null },
            title = { Text("حذف المعرف نهائياً") },
            text = {
                Text("هل تريد حذف المعرف ${item.identifier} من قاعدة البيانات؟ لا يمكن التراجع عن هذا الإجراء.")
            },
            confirmButton = {
                Button(
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    onClick = {
                        onDeleteIdentifier(item.id) { success, err ->
                            if (success) {
                                itemToDelete = null
                                feedbackMessage = "تم حذف المعرف ${item.identifier} بنجاح."
                                isFeedbackError = false
                            } else {
                                feedbackMessage = err ?: "فشل حذف المعرف"
                                isFeedbackError = true
                            }
                        }
                    }
                ) {
                    Text("حذف نهائي")
                }
            },
            dismissButton = {
                TextButton(onClick = { itemToDelete = null }) {
                    Text("إلغاء")
                }
            }
        )
    }
}

@Composable
private fun StatBadge(
    label: String,
    count: Int,
    color: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = color.copy(alpha = 0.12f),
        modifier = modifier
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(vertical = 6.dp, horizontal = 4.dp)
        ) {
            Text(
                text = count.toString(),
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                color = color
            )
            Text(
                text = label,
                fontSize = 10.sp,
                color = color
            )
        }
    }
}

@Composable
private fun SpecialIdentifierCard(
    item: SpecialIdentifierEntity,
    onAssignClick: () -> Unit,
    onReserveClick: () -> Unit,
    onReleaseClick: () -> Unit,
    onDisableClick: () -> Unit,
    onDeleteClick: () -> Unit
) {
    val statusColor = when (item.status) {
        SpecialIdentifierEntity.STATUS_AVAILABLE -> Color(0xFF16A34A) // Green
        SpecialIdentifierEntity.STATUS_RESERVED -> Color(0xFFD97706)  // Amber
        SpecialIdentifierEntity.STATUS_ASSIGNED -> Color(0xFF2563EB)  // Blue
        else -> Color(0xFFDC2626) // Red
    }

    val statusLabel = when (item.status) {
        SpecialIdentifierEntity.STATUS_AVAILABLE -> "متاح"
        SpecialIdentifierEntity.STATUS_RESERVED -> "محجوز"
        SpecialIdentifierEntity.STATUS_ASSIGNED -> "معين"
        else -> "معطل"
    }

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Identifier and Status Row
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = item.identifier,
                        fontWeight = FontWeight.Bold,
                        fontSize = 17.sp,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                    ) {
                        Text(
                            text = item.category,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = statusColor.copy(alpha = 0.15f)
                ) {
                    Text(
                        text = statusLabel,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = statusColor,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            // Details section based on status
            if (item.status == SpecialIdentifierEntity.STATUS_ASSIGNED) {
                Spacer(modifier = Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Person,
                        contentDescription = null,
                        tint = Color(0xFF2563EB),
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "معين للمستخدم: ${item.assignedUsername ?: "غير معروف"}",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF2563EB)
                    )
                    if (item.assignedAt != null) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "(${TimeUtils.formatTimestamp(item.assignedAt)})",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else if (item.status == SpecialIdentifierEntity.STATUS_RESERVED && !item.reservedForName.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Bookmark,
                        contentDescription = null,
                        tint = Color(0xFFD97706),
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "محجوز لصالح: ${item.reservedForName}",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFFD97706)
                    )
                }
            }

            if (!item.notes.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "ملاحظة: ${item.notes}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Action Buttons Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                when (item.status) {
                    SpecialIdentifierEntity.STATUS_AVAILABLE -> {
                        FilledTonalButton(
                            onClick = onAssignClick,
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            modifier = Modifier.height(34.dp)
                        ) {
                            Icon(Icons.Default.PersonAdd, contentDescription = null, modifier = Modifier.size(15.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("تعيين", fontSize = 12.sp)
                        }
                        Spacer(modifier = Modifier.width(6.dp))
                        OutlinedButton(
                            onClick = onReserveClick,
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            modifier = Modifier.height(34.dp)
                        ) {
                            Icon(Icons.Default.BookmarkBorder, contentDescription = null, modifier = Modifier.size(15.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("حجز", fontSize = 12.sp)
                        }
                        Spacer(modifier = Modifier.width(6.dp))
                        IconButton(onClick = onDisableClick, modifier = Modifier.size(34.dp)) {
                            Icon(Icons.Default.Block, contentDescription = "Disable", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                        }
                        IconButton(onClick = onDeleteClick, modifier = Modifier.size(34.dp)) {
                            Icon(Icons.Default.DeleteOutline, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                        }
                    }
                    SpecialIdentifierEntity.STATUS_RESERVED -> {
                        FilledTonalButton(
                            onClick = onAssignClick,
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            modifier = Modifier.height(34.dp)
                        ) {
                            Icon(Icons.Default.PersonAdd, contentDescription = null, modifier = Modifier.size(15.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("تعيين", fontSize = 12.sp)
                        }
                        Spacer(modifier = Modifier.width(6.dp))
                        OutlinedButton(
                            onClick = onReleaseClick,
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            modifier = Modifier.height(34.dp)
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(15.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("إلغاء الحجز", fontSize = 12.sp)
                        }
                        Spacer(modifier = Modifier.width(6.dp))
                        IconButton(onClick = onDisableClick, modifier = Modifier.size(34.dp)) {
                            Icon(Icons.Default.Block, contentDescription = "Disable", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                        }
                    }
                    SpecialIdentifierEntity.STATUS_ASSIGNED -> {
                        OutlinedButton(
                            onClick = onReleaseClick,
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            modifier = Modifier.height(34.dp)
                        ) {
                            Icon(Icons.Default.LinkOff, contentDescription = null, modifier = Modifier.size(15.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("فك الارتباط (إتاحة)", fontSize = 12.sp)
                        }
                        Spacer(modifier = Modifier.width(6.dp))
                        IconButton(onClick = onDisableClick, modifier = Modifier.size(34.dp)) {
                            Icon(Icons.Default.Block, contentDescription = "Disable", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                        }
                    }
                    SpecialIdentifierEntity.STATUS_DISABLED -> {
                        FilledTonalButton(
                            onClick = onReleaseClick,
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            modifier = Modifier.height(34.dp)
                        ) {
                            Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(15.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("إعادة تفعيل", fontSize = 12.sp)
                        }
                        Spacer(modifier = Modifier.width(6.dp))
                        IconButton(onClick = onDeleteClick, modifier = Modifier.size(34.dp)) {
                            Icon(Icons.Default.DeleteOutline, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CreateSpecialIdentifierSubDialog(
    existingIdentifiers: List<String> = emptyList(),
    onCreate: (identifier: String, category: String, notes: String?) -> Unit,
    onDismiss: () -> Unit
) {
    var identifierText by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf("VIP") }
    var notesText by remember { mutableStateOf("") }
    var errorText by remember { mutableStateOf<String?>(null) }

    val categories = listOf("VIP", "GOLD", "NUMERIC", "ADMIN", "CUSTOM")

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("إنشاء معرف مميز جديد") },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text("أدخل المعرف المميز أو استخدم التوليد التلقائي:", style = MaterialTheme.typography.bodyMedium)
                Spacer(modifier = Modifier.height(6.dp))
                OutlinedTextField(
                    value = identifierText,
                    onValueChange = {
                        identifierText = it.uppercase().replace(" ", "")
                        errorText = null
                    },
                    placeholder = { Text("مثال: VIP0001") },
                    trailingIcon = {
                        IconButton(
                            onClick = {
                                val prefix = if (selectedCategory == "NUMERIC") "NUM" else selectedCategory
                                identifierText = SpecialIdentifierGenerator.generateNextAvailableVanityId(prefix, existingIdentifiers)
                                errorText = null
                            }
                        ) {
                            Icon(
                                Icons.Default.AutoAwesome,
                                contentDescription = "توليد تلقائي",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    FilledTonalButton(
                        onClick = {
                            val prefix = if (selectedCategory == "NUMERIC") "NUM" else selectedCategory
                            identifierText = SpecialIdentifierGenerator.generateNextAvailableVanityId(prefix, existingIdentifiers)
                            errorText = null
                        },
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        modifier = Modifier.height(32.dp)
                    ) {
                        Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("توليد $selectedCategory تلقائي", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Text("تصنيف المعرف:", style = MaterialTheme.typography.bodyMedium)
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    categories.forEach { cat ->
                        FilterChip(
                            selected = selectedCategory == cat,
                            onClick = {
                                selectedCategory = cat
                                val prefix = if (cat == "NUMERIC") "NUM" else cat
                                identifierText = SpecialIdentifierGenerator.generateNextAvailableVanityId(prefix, existingIdentifiers)
                                errorText = null
                            },
                            label = { Text(cat, fontSize = 11.sp) }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Text("ملاحظات إدارية (اختياري):", style = MaterialTheme.typography.bodyMedium)
                Spacer(modifier = Modifier.height(4.dp))
                OutlinedTextField(
                    value = notesText,
                    onValueChange = { notesText = it },
                    placeholder = { Text("مثال: مخصص لعملاء VIP أو الفئة الذهبية") },
                    singleLine = false,
                    maxLines = 3,
                    modifier = Modifier.fillMaxWidth()
                )

                if (errorText != null) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(errorText!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val clean = identifierText.trim()
                    if (clean.length < 3) {
                        errorText = "يجب أن يتكون المعرف من 3 أحرف/أرقام على الأقل."
                    } else {
                        onCreate(clean, selectedCategory, notesText.ifBlank { null })
                    }
                }
            ) {
                Text("إنشاء المعرف")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("إلغاء")
            }
        }
    )
}

@Composable
private fun AssignIdentifierSubDialog(
    identifier: SpecialIdentifierEntity,
    users: List<UserEntity>,
    onAssign: (targetUserId: String) -> Unit,
    onDismiss: () -> Unit
) {
    var searchUserQuery by remember { mutableStateOf("") }
    var selectedUserId by remember { mutableStateOf<String?>(null) }

    val filteredUsers = remember(users, searchUserQuery) {
        users.filter { user ->
            val q = searchUserQuery.trim().lowercase()
            q.isEmpty() ||
                    user.username.lowercase().contains(q) ||
                    user.customId.lowercase().contains(q) ||
                    (user.email?.lowercase()?.contains(q) == true)
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("تعيين المعرف: ${identifier.identifier}") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 400.dp)
            ) {
                Text(
                    text = "اختر المستخدم الذي تريد ربط هذا المعرف المميز بحسابه:",
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = searchUserQuery,
                    onValueChange = { searchUserQuery = it },
                    placeholder = { Text("بحث باسم المستخدم أو المعرف الحالي...") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(8.dp))

                if (filteredUsers.isEmpty()) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(100.dp)
                    ) {
                        Text("لا يوجد مستخدمون مطابقون", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                } else {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        items(filteredUsers, key = { it.userId }) { user ->
                            val isSelected = selectedUserId == user.userId
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { selectedUserId = user.userId }
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(8.dp)
                                ) {
                                    AvatarImage(uri = user.profilePicUri, name = user.username, size = 36.dp)
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(text = user.username, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                        Text(text = "المعرف الحالي: ${user.customId}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    if (isSelected) {
                                        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                enabled = selectedUserId != null,
                onClick = {
                    selectedUserId?.let { onAssign(it) }
                }
            ) {
                Text("تأكيد التعيين")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("إلغاء")
            }
        }
    )
}

@Composable
private fun ReserveIdentifierSubDialog(
    identifier: SpecialIdentifierEntity,
    onReserve: (reservedFor: String, notes: String?) -> Unit,
    onDismiss: () -> Unit
) {
    var reservedNameInput by remember { mutableStateOf(identifier.reservedForName ?: "") }
    var notesInput by remember { mutableStateOf(identifier.notes ?: "") }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("حجز المعرف: ${identifier.identifier}") },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text("محجوز لصالح من؟ (اسم الشخص أو الجهة):", style = MaterialTheme.typography.bodyMedium)
                Spacer(modifier = Modifier.height(4.dp))
                OutlinedTextField(
                    value = reservedNameInput,
                    onValueChange = {
                        reservedNameInput = it
                        error = null
                    },
                    placeholder = { Text("مثال: عبد الله أحمد") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(10.dp))

                Text("ملاحظات إضافية:", style = MaterialTheme.typography.bodyMedium)
                Spacer(modifier = Modifier.height(4.dp))
                OutlinedTextField(
                    value = notesInput,
                    onValueChange = { notesInput = it },
                    placeholder = { Text("سبب الحجز أو شروط التعيين") },
                    singleLine = false,
                    maxLines = 2,
                    modifier = Modifier.fillMaxWidth()
                )

                if (error != null) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(error!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (reservedNameInput.isBlank()) {
                        error = "يرجى كتابة اسم أو جهة الحجز."
                    } else {
                        onReserve(reservedNameInput.trim(), notesInput.ifBlank { null })
                    }
                }
            ) {
                Text("حفظ الحجز")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("إلغاء")
            }
        }
    )
}
