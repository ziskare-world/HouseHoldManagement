package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.data.local.model.ChoreTask
import com.example.data.local.model.UserProfile
import com.example.ui.components.ChoreDistributionChart
import com.example.util.DateUtils

val ChoreCategories = listOf("Cleaning", "Kitchen", "Trash", "Groceries", "Maintenance")
val ChoreFrequencies = listOf(
    "DAILY_ROTATION" to "Daily Rotation (Day 1: Roommate A, Day 2: Roommate B...)",
    "DAILY" to "Daily Task (Fixed Roommate)",
    "WEEKLY_SUNDAY_ROTATION" to "Sunday-to-Sunday Rotation",
    "WEEKLY_CUSTOM" to "Weekly Schedule",
    "MONTHLY" to "Monthly Task"
)

@Composable
fun ChoresScreen(
    chores: List<ChoreTask>,
    members: List<UserProfile>,
    currentUser: UserProfile?,
    onToggleChore: (ChoreTask) -> Unit,
    onRotateSundayChore: (ChoreTask) -> Unit,
    onSendPushAlert: (ChoreTask) -> Unit,
    onDeleteChore: (String) -> Unit,
    onAddChore: (
        title: String,
        description: String,
        category: String,
        frequency: String,
        assignedUser: UserProfile,
        rotationMembers: List<UserProfile>,
        scheduledTime: String,
        dayOfWeek: Int,
        dayOfMonth: Int,
        points: Int
    ) -> Unit,
    onUpdateChore: ((ChoreTask) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    var showAddDialog by remember { mutableStateOf(false) }
    var editingChore by remember { mutableStateOf<ChoreTask?>(null) }
    var selectedFilter by remember { mutableStateOf("ALL") }

    val allRoommates = remember(members, currentUser) {
        val combined = mutableListOf<UserProfile>()
        currentUser?.let { combined.add(it) }
        members.forEach { m ->
            if (combined.none { it.id == m.id }) combined.add(m)
        }
        combined.ifEmpty { listOfNotNull(currentUser) }
    }

    val filteredChores = remember(chores, selectedFilter) {
        when (selectedFilter) {
            "DAILY" -> chores.filter { it.frequency == "DAILY" || it.frequency == "DAILY_ROTATION" }
            "SUNDAY" -> chores.filter { it.frequency == "WEEKLY_SUNDAY_ROTATION" }
            "MONTHLY" -> chores.filter { it.frequency == "MONTHLY" }
            "MY_CHORES" -> chores.filter { it.assignedToUserId == currentUser?.id }
            else -> chores
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header
            item {
                Column {
                    Text(
                        text = "Chores & Work Roster",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Day-to-day duties, Sunday rotations, and push alert triggers",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Work Distribution Bar Chart (Only show if chores exist)
            if (chores.isNotEmpty()) {
                item {
                    ChoreDistributionChart(chores = chores, members = members)
                }
            }

            // Filter Chips
            item {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val filterOptions = listOf(
                        "ALL" to "All (${chores.size})",
                        "MY_CHORES" to "My Duties",
                        "DAILY" to "Daily",
                        "SUNDAY" to "Sunday Roster",
                        "MONTHLY" to "Monthly"
                    )
                    items(filterOptions) { (key, label) ->
                        val isSelected = selectedFilter == key
                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
                            modifier = Modifier
                                .clip(RoundedCornerShape(20.dp))
                                .clickable { selectedFilter = key }
                        ) {
                            Text(
                                text = label,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                            )
                        }
                    }
                }
            }

            // Chores List
            if (filteredChores.isEmpty()) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Box(modifier = Modifier.padding(32.dp), contentAlignment = Alignment.Center) {
                            Text(
                                text = "No chores found in this category. Tap '+' to create one!",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            } else {
                items(filteredChores, key = { it.id }) { chore ->
                    ChoreCardItem(
                        chore = chore,
                        onToggle = { onToggleChore(chore) },
                        onRotate = { onRotateSundayChore(chore) },
                        onSendAlert = { onSendPushAlert(chore) },
                        onEdit = { editingChore = chore },
                        onDelete = { onDeleteChore(chore.id) }
                    )
                }
            }

            item {
                Spacer(modifier = Modifier.height(64.dp))
            }
        }

        // Floating Action Button to Add Chore
        FloatingActionButton(
            onClick = { showAddDialog = true },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(24.dp),
            containerColor = MaterialTheme.colorScheme.primary
        ) {
            Icon(imageVector = Icons.Default.Add, contentDescription = "Add Chore", tint = Color.White)
        }
    }

    if (showAddDialog) {
        AddChoreDialog(
            members = allRoommates,
            currentUser = currentUser,
            initialChore = null,
            onDismiss = { showAddDialog = false },
            onConfirm = { title, desc, cat, freq, user, rotMembers, time, dow, dom, pts ->
                onAddChore(title, desc, cat, freq, user, rotMembers, time, dow, dom, pts)
                showAddDialog = false
            }
        )
    }

    editingChore?.let { choreToEdit ->
        AddChoreDialog(
            members = allRoommates,
            currentUser = currentUser,
            initialChore = choreToEdit,
            onDismiss = { editingChore = null },
            onConfirm = { title, desc, cat, freq, user, rotMembers, time, dow, dom, pts ->
                val rotationIds = rotMembers.joinToString(",") { it.id }
                val updated = choreToEdit.copy(
                    title = title,
                    description = desc,
                    category = cat,
                    frequency = freq,
                    assignedToUserId = user.id,
                    assignedToUserName = user.name,
                    rotationMemberIds = rotationIds,
                    scheduledTime = time,
                    scheduledDayOfWeek = dow,
                    scheduledDayOfMonth = dom,
                    points = pts
                )
                onUpdateChore?.invoke(updated)
                editingChore = null
            }
        )
    }
}

@Composable
fun ChoreCardItem(
    chore: ChoreTask,
    onToggle: () -> Unit,
    onRotate: () -> Unit,
    onSendAlert: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val isCompleted = chore.status == "COMPLETED"

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isCompleted) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f) else MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isCompleted) 0.dp else 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onToggle) {
                    Icon(
                        imageVector = if (isCompleted) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                        contentDescription = "Toggle status",
                        tint = if (isCompleted) Color(0xFF10B981) else MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(28.dp)
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = chore.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (isCompleted) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f) else MaterialTheme.colorScheme.onSurface
                    )
                    if (chore.description.isNotBlank()) {
                        Text(
                            text = chore.description,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2
                        )
                    }
                }

                // Push Alert Button
                IconButton(onClick = onSendAlert) {
                    Icon(
                        imageVector = Icons.Default.NotificationsActive,
                        contentDescription = "Trigger push alert",
                        tint = Color(0xFFF59E0B)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Details Badges
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Assignee badge
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.primaryContainer
                    ) {
                        Text(
                            text = "👤 ${chore.assignedToUserName}",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }

                    // Frequency badge
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.secondaryContainer
                    ) {
                        Text(
                            text = when (chore.frequency) {
                                "DAILY_ROTATION" -> "🔄 Daily (${chore.scheduledTime})"
                                "WEEKLY_SUNDAY_ROTATION" -> "🔄 Sunday Rotation"
                                "DAILY" -> "📅 Daily (${chore.scheduledTime})"
                                "MONTHLY" -> "📆 Monthly"
                                else -> "Weekly"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }

                    Text(
                        text = "+${chore.points}pts",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF10B981)
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    if (chore.frequency.contains("ROTATION") || chore.rotationMemberIds.isNotBlank()) {
                        IconButton(onClick = onRotate, modifier = Modifier.size(28.dp)) {
                            Icon(
                                imageVector = Icons.Default.SwapHoriz,
                                contentDescription = "Rotate next user",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    IconButton(onClick = onEdit, modifier = Modifier.size(28.dp)) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = "Edit Chore",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                    }

                    IconButton(onClick = onDelete, modifier = Modifier.size(28.dp)) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Delete",
                            tint = MaterialTheme.colorScheme.error.copy(alpha = 0.5f),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AddChoreDialog(
    members: List<UserProfile>,
    currentUser: UserProfile?,
    initialChore: ChoreTask? = null,
    onDismiss: () -> Unit,
    onConfirm: (
        title: String,
        description: String,
        category: String,
        frequency: String,
        assignedUser: UserProfile,
        rotationMembers: List<UserProfile>,
        scheduledTime: String,
        dayOfWeek: Int,
        dayOfMonth: Int,
        points: Int
    ) -> Unit
) {
    val isEditing = initialChore != null

    var title by remember { mutableStateOf(initialChore?.title ?: "") }
    var description by remember { mutableStateOf(initialChore?.description ?: "") }
    var selectedCategory by remember { mutableStateOf(initialChore?.category ?: ChoreCategories.first()) }
    var selectedFrequency by remember { mutableStateOf(initialChore?.frequency ?: "DAILY_ROTATION") }
    var selectedAssignee by remember {
        mutableStateOf(
            initialChore?.let { c -> members.find { it.id == c.assignedToUserId } }
                ?: currentUser
                ?: members.firstOrNull()
                ?: UserProfile("USR_1", "Roommate")
        )
    }
    var selectedRotationMembers by remember {
        mutableStateOf(
            initialChore?.let { c ->
                members.filter { c.rotationMemberIds.contains(it.id) }.ifEmpty { members }
            } ?: members
        )
    }
    var scheduledTime by remember { mutableStateOf(initialChore?.scheduledTime ?: "09:00 AM") }
    var pointsText by remember { mutableStateOf(initialChore?.let { it.points.toString() } ?: "10") }

    var categoryExpanded by remember { mutableStateOf(false) }
    var assigneeExpanded by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = if (isEditing) "Edit Chore Task" else "Add Household Chore Duty",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Chore Title *") },
                    placeholder = { Text("e.g. Kitchen Cleaning, Trash Disposal") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true
                )

                // Frequency Selector Pills
                Text(
                    text = "Repeat Schedule:",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold
                )

                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    ChoreFrequencies.forEach { (freqKey, label) ->
                        val isSelected = selectedFrequency == freqKey
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .clickable { selectedFrequency = freqKey }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = if (freqKey.contains("ROTATION")) Icons.Default.SwapHoriz else Icons.Default.Repeat,
                                    contentDescription = null,
                                    tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = label,
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                )
                            }
                        }
                    }
                }

                // Daily Work Roster selection if rotation frequency is chosen
                if (selectedFrequency.contains("ROTATION")) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f))
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.SwapHoriz,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Daily Work Roster Members *",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            Text(
                                text = "Select the flatmates participating in this daily task (at least 1 mandatory). The duty will automatically rotate daily in order.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 11.sp
                            )

                            // Roommate selection wrapped FlowRow (displays all 3+ roommates clearly)
                            Text(
                                text = "All flatmates (${members.size} available):",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold
                            )

                            FlowRow(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                members.forEach { m ->
                                    val isChecked = selectedRotationMembers.any { it.id == m.id }
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = if (isChecked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(8.dp))
                                            .clickable {
                                                selectedRotationMembers = if (isChecked) {
                                                    // Keep at least 1 member mandatory
                                                    if (selectedRotationMembers.size > 1) selectedRotationMembers.filter { it.id != m.id } else selectedRotationMembers
                                                } else {
                                                    selectedRotationMembers + m
                                                }
                                                if (selectedAssignee.id == m.id && isChecked && selectedRotationMembers.isNotEmpty()) {
                                                    selectedAssignee = selectedRotationMembers.first()
                                                }
                                            }
                                    ) {
                                        Text(
                                            text = "${if (isChecked) "✓ " else "+ "}${m.name}",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = if (isChecked) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                                        )
                                    }
                                }
                            }

                            // Live Rotation Sequence Preview
                            if (selectedRotationMembers.isNotEmpty()) {
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = MaterialTheme.colorScheme.surface,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.padding(8.dp)) {
                                        Text(
                                            text = "🔄 Active Roster Sequence (${selectedRotationMembers.size} flatmates):",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        val sequenceText = selectedRotationMembers.mapIndexed { idx, rm -> "Day ${idx + 1}: ${rm.name}" }.joinToString(" ➔ ")
                                        Text(
                                            text = sequenceText,
                                            style = MaterialTheme.typography.bodySmall,
                                            fontWeight = FontWeight.Medium,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Category Dropdown
                ExposedDropdownMenuBox(
                    expanded = categoryExpanded,
                    onExpandedChange = { categoryExpanded = !categoryExpanded }
                ) {
                    OutlinedTextField(
                        value = selectedCategory,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Category") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = categoryExpanded) },
                        modifier = Modifier
                            .menuAnchor()
                            .fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )
                    ExposedDropdownMenu(
                        expanded = categoryExpanded,
                        onDismissRequest = { categoryExpanded = false }
                    ) {
                        ChoreCategories.forEach { cat ->
                            DropdownMenuItem(
                                text = { Text(cat) },
                                onClick = {
                                    selectedCategory = cat
                                    categoryExpanded = false
                                }
                            )
                        }
                    }
                }

                // Assignee Dropdown (Starting roommate)
                ExposedDropdownMenuBox(
                    expanded = assigneeExpanded,
                    onExpandedChange = { assigneeExpanded = !assigneeExpanded }
                ) {
                    OutlinedTextField(
                        value = selectedAssignee.name,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text(if (selectedFrequency.contains("ROTATION")) "Starting Roommate (Day 1) *" else "Assigned Roommate *") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = assigneeExpanded) },
                        modifier = Modifier
                            .menuAnchor()
                            .fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )
                    ExposedDropdownMenu(
                        expanded = assigneeExpanded,
                        onDismissRequest = { assigneeExpanded = false }
                    ) {
                        val availableMembers = if (selectedFrequency.contains("ROTATION")) selectedRotationMembers else members
                        availableMembers.forEach { m ->
                            DropdownMenuItem(
                                text = { Text(m.name) },
                                onClick = {
                                    selectedAssignee = m
                                    assigneeExpanded = false
                                }
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = scheduledTime,
                        onValueChange = { scheduledTime = it },
                        label = { Text("Time") },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    )
                    OutlinedTextField(
                        value = pointsText,
                        onValueChange = { pointsText = it },
                        label = { Text("Points") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    )
                }

                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Instructions (Optional)") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (title.isNotBlank()) {
                        val rotationList = if (selectedFrequency.contains("ROTATION")) {
                            listOf(selectedAssignee) + selectedRotationMembers.filter { it.id != selectedAssignee.id }
                        } else {
                            listOf(selectedAssignee)
                        }
                        onConfirm(
                            title.trim(),
                            description.trim(),
                            selectedCategory,
                            selectedFrequency,
                            selectedAssignee,
                            rotationList,
                            scheduledTime.trim(),
                            1, // Sunday
                            1,
                            pointsText.toIntOrNull() ?: 10
                        )
                    }
                },
                enabled = title.isNotBlank() && (!selectedFrequency.contains("ROTATION") || selectedRotationMembers.isNotEmpty()),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(if (isEditing) "Save Changes" else "Schedule Chore")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
