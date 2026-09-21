package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.model.ExpenseItem
import com.example.data.local.model.UserProfile
import com.example.ui.components.ExpenseDetailDialog
import com.example.ui.components.MonthlyExpenseDonutChart
import com.example.ui.components.getCategoryColor
import com.example.ui.viewmodel.BudgetStatus
import com.example.util.DateUtils

import android.app.DatePickerDialog
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.EditCalendar
import androidx.compose.ui.platform.LocalContext
import java.util.Calendar

val ExpenseCategories = listOf(
    "Groceries",
    "Rent",
    "Utilities",
    "Food & Dining",
    "Household Supplies",
    "Entertainment",
    "Maintenance",
    "Other"
)

@Composable
fun ExpensesScreen(
    expenses: List<ExpenseItem>,
    members: List<UserProfile>,
    currentUser: UserProfile?,
    budgetStatus: BudgetStatus,
    selectedMonthKey: String,
    onSelectMonth: (String) -> Unit,
    onAddExpense: (title: String, amount: Double, category: String, paidBy: UserProfile, splitType: String, customMembers: List<UserProfile>, notes: String, dateMillis: Long) -> Unit,
    onUpdateExpense: ((expense: ExpenseItem, customMembers: List<UserProfile>) -> Unit)? = null,
    onDeleteExpense: (String) -> Unit,
    onUpdateBudget: (newLimit: Double, thresholdPercent: Int) -> Unit,
    modifier: Modifier = Modifier
) {
    var showAddDialog by remember { mutableStateOf(false) }
    var editingExpense by remember { mutableStateOf<ExpenseItem?>(null) }
    var viewingExpenseDetails by remember { mutableStateOf<ExpenseItem?>(null) }
    var showBudgetConfigDialog by remember { mutableStateOf(false) }
    var selectedCategoryFilter by remember { mutableStateOf<String?>(null) }

    val monthFilteredExpenses = remember(expenses, selectedMonthKey, selectedCategoryFilter) {
        expenses.filter { it.monthYearKey == selectedMonthKey }
            .filter { selectedCategoryFilter == null || it.category == selectedCategoryFilter }
    }

    val totalMonthSpent = remember(expenses, selectedMonthKey) {
        expenses.filter { it.monthYearKey == selectedMonthKey }.sumOf { it.amount }
    }

    Box(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header & Budget Tuning Action
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Expenses & Budget",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Monthly expansion history and automated alerts",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(
                        onClick = { showBudgetConfigDialog = true },
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Tune,
                            contentDescription = "Budget Settings",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            // Month Selector Chips (August first, then September, October, and past months)
            item {
                val monthOptions = remember(expenses) {
                    val curMonth = DateUtils.getCurrentMonthYearKey()
                    val upcoming = (1..4).map { i ->
                        val cal = Calendar.getInstance().apply { add(Calendar.MONTH, i) }
                        DateUtils.getMonthYearKey(cal.timeInMillis)
                    }
                    val past = (1..6).map { i ->
                        val cal = Calendar.getInstance().apply { add(Calendar.MONTH, -i) }
                        DateUtils.getMonthYearKey(cal.timeInMillis)
                    }
                    val recorded = expenses.mapNotNull { it.monthYearKey.takeIf { k -> k.isNotBlank() } }.distinct()

                    val orderedList = mutableListOf<String>()
                    orderedList.add(curMonth)
                    upcoming.forEach { if (!orderedList.contains(it)) orderedList.add(it) }
                    past.forEach { if (!orderedList.contains(it)) orderedList.add(it) }
                    recorded.forEach { if (!orderedList.contains(it)) orderedList.add(it) }
                    orderedList
                }

                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(vertical = 4.dp)
                ) {
                    items(monthOptions) { monthKey ->
                        val isSelected = monthKey == selectedMonthKey
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { onSelectMonth(monthKey) }
                        ) {
                            Text(
                                text = DateUtils.formatDisplayMonth(monthKey),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                            )
                        }
                    }
                }
            }

            // Total Expenses & Monthly Budget Summary Card (No Category Breakdown)
            item {
                val currentMonthExpenses = expenses.filter { it.monthYearKey == selectedMonthKey }
                val monthTotal = currentMonthExpenses.sumOf { it.amount }
                val budgetLimit = budgetStatus.budgetLimit
                val percentUsed = if (budgetLimit > 0) ((monthTotal / budgetLimit) * 100).toInt() else 0
                val remaining = budgetLimit - monthTotal

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(22.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = "Total Expenses (${DateUtils.formatDisplayMonth(selectedMonthKey)})",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = DateUtils.formatCurrency(monthTotal),
                                    style = MaterialTheme.typography.headlineMedium,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }

                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                            ) {
                                Text(
                                    text = "${currentMonthExpenses.size} items",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // Modern Animated Linear Progress Bar
                        LinearProgressIndicator(
                            progress = { (percentUsed / 100f).coerceIn(0f, 1f) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(RoundedCornerShape(4.dp)),
                            color = when {
                                percentUsed > 100 -> MaterialTheme.colorScheme.error
                                percentUsed >= budgetStatus.warningThreshold -> Color(0xFFF59E0B)
                                else -> MaterialTheme.colorScheme.primary
                            },
                            trackColor = MaterialTheme.colorScheme.surfaceVariant
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Budget: ${DateUtils.formatCurrency(budgetLimit)} ($percentUsed% used)",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = if (remaining >= 0) "${DateUtils.formatCurrency(remaining)} left" else "${DateUtils.formatCurrency(-remaining)} over budget!",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Bold,
                                color = if (remaining >= 0) Color(0xFF10B981) else MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            }

            // Category Filter Pills
            item {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(vertical = 4.dp)
                ) {
                    item {
                        FilterChip(
                            selected = selectedCategoryFilter == null,
                            onClick = { selectedCategoryFilter = null },
                            label = { Text("All (${monthFilteredExpenses.size})") },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primary,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                            )
                        )
                    }
                    items(ExpenseCategories) { cat ->
                        val count = expenses.filter { it.monthYearKey == selectedMonthKey && it.category == cat }.size
                        FilterChip(
                            selected = selectedCategoryFilter == cat,
                            onClick = { selectedCategoryFilter = if (selectedCategoryFilter == cat) null else cat },
                            label = { Text(cat) },
                            leadingIcon = {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(getCategoryColor(cat))
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primary,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                            )
                        )
                    }
                }
            }

            // Expense List Section Header
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Transactions & Splits",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "${monthFilteredExpenses.size} items",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (monthFilteredExpenses.isEmpty()) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Box(modifier = Modifier.padding(32.dp), contentAlignment = Alignment.Center) {
                            Text(
                                text = "No expenses in ${DateUtils.formatDisplayMonth(selectedMonthKey)}. Tap '+' to record one!",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            } else {
                items(monthFilteredExpenses, key = { it.id }) { item ->
                    ExpenseRowItem(
                        expense = item,
                        onClick = { viewingExpenseDetails = item },
                        onEdit = { editingExpense = item },
                        onDelete = { onDeleteExpense(item.id) }
                    )
                }
            }

            item {
                Spacer(modifier = Modifier.height(64.dp))
            }
        }

        // Floating Action Button to Add Expense
        FloatingActionButton(
            onClick = { showAddDialog = true },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(24.dp),
            containerColor = MaterialTheme.colorScheme.primary
        ) {
            Icon(imageVector = Icons.Default.Add, contentDescription = "Add Expense", tint = Color.White)
        }
    }

    // Expense Detail Dialog (inspect full per-roommate splits & shares)
    viewingExpenseDetails?.let { exp ->
        ExpenseDetailDialog(
            expense = exp,
            members = members,
            currentUser = currentUser,
            onDismiss = { viewingExpenseDetails = null },
            onEdit = {
                editingExpense = exp
                viewingExpenseDetails = null
            },
            onDelete = {
                onDeleteExpense(exp.id)
                viewingExpenseDetails = null
            }
        )
    }

    // Add Expense Dialog
    if (showAddDialog) {
        AddExpenseDialog(
            members = members,
            currentUser = currentUser,
            initialExpense = null,
            onDismiss = { showAddDialog = false },
            onConfirm = { title, amount, cat, payer, split, customMembers, notes, dateMillis ->
                val monthKey = DateUtils.getMonthYearKey(dateMillis)
                onAddExpense(title, amount, cat, payer, split, customMembers, notes, dateMillis)
                onSelectMonth(monthKey)
                showAddDialog = false
            }
        )
    }

    // Edit Expense Dialog
    editingExpense?.let { expToEdit ->
        AddExpenseDialog(
            members = members,
            currentUser = currentUser,
            initialExpense = expToEdit,
            onDismiss = { editingExpense = null },
            onConfirm = { title, amount, cat, payer, split, customMembers, notes, dateMillis ->
                val splitUserIds = if (split == "CUSTOM") customMembers.joinToString(",") { it.id } else ""
                val monthKey = DateUtils.getMonthYearKey(dateMillis)
                val updated = expToEdit.copy(
                    title = title,
                    amount = amount,
                    category = cat,
                    paidByUserId = payer.id,
                    paidByName = payer.name,
                    splitType = split,
                    splitWithUserIds = splitUserIds,
                    notes = notes,
                    dateMillis = dateMillis,
                    monthYearKey = monthKey
                )
                onUpdateExpense?.invoke(updated, customMembers)
                onSelectMonth(monthKey)
                editingExpense = null
            }
        )
    }

    // Budget Configuration Dialog
    if (showBudgetConfigDialog) {
        BudgetConfigDialog(
            currentLimit = budgetStatus.budgetLimit,
            currentThreshold = budgetStatus.warningThreshold,
            onDismiss = { showBudgetConfigDialog = false },
            onSave = { newLimit, threshold ->
                onUpdateBudget(newLimit, threshold)
                showBudgetConfigDialog = false
            }
        )
    }
}

@Composable
fun ExpenseRowItem(
    expense: ExpenseItem,
    onClick: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable { onClick() },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Category Icon Badge
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(getCategoryColor(expense.category).copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.ReceiptLong,
                        contentDescription = null,
                        tint = getCategoryColor(expense.category),
                        modifier = Modifier.size(24.dp)
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = expense.title,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "${expense.category} • ${DateUtils.formatDisplayDate(expense.dateMillis)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = DateUtils.formatCurrency(expense.amount),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = if (expense.splitType == "PERSONAL") MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f) else MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f)
                    ) {
                        Text(
                            text = when (expense.splitType) {
                                "EQUAL" -> "Split Equal"
                                "CUSTOM" -> "Custom Split"
                                else -> "Personal Only"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            fontSize = 10.sp,
                            fontWeight = if (expense.splitType == "PERSONAL") FontWeight.Bold else FontWeight.Normal,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }

                    Text(
                        text = "Paid by ${expense.paidByName}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    IconButton(
                        onClick = onEdit,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = "Edit Expense",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                    }

                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Delete",
                            tint = MaterialTheme.colorScheme.error.copy(alpha = 0.6f),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            if (expense.notes.isNotBlank()) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "📝 ${expense.notes}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddExpenseDialog(
    members: List<UserProfile>,
    currentUser: UserProfile?,
    initialExpense: ExpenseItem? = null,
    onDismiss: () -> Unit,
    onConfirm: (title: String, amount: Double, category: String, payer: UserProfile, splitType: String, customMembers: List<UserProfile>, notes: String, dateMillis: Long) -> Unit
) {
    val context = LocalContext.current
    val isEditing = initialExpense != null

    var title by remember { mutableStateOf(initialExpense?.title ?: "") }
    var amountText by remember { mutableStateOf(initialExpense?.let { if (it.amount > 0) it.amount.toString() else "" } ?: "") }
    var selectedCategory by remember { mutableStateOf(initialExpense?.category ?: ExpenseCategories.first()) }
    var selectedPayer by remember {
        mutableStateOf(
            if (initialExpense != null) {
                members.find { it.id == initialExpense.paidByUserId } ?: currentUser ?: members.firstOrNull() ?: UserProfile("USR_1", "Alex")
            } else {
                currentUser ?: members.firstOrNull() ?: UserProfile("USR_1", "Alex")
            }
        )
    }
    var splitType by remember { mutableStateOf(initialExpense?.splitType ?: "EQUAL") }
    var selectedCustomMembers by remember {
        mutableStateOf(
            if (initialExpense != null && initialExpense.splitType == "CUSTOM") {
                members.filter { initialExpense.splitWithUserIds.contains(it.id) }.ifEmpty { members }
            } else {
                members
            }
        )
    }
    var notes by remember { mutableStateOf(initialExpense?.notes ?: "") }
    var selectedDateMillis by remember { mutableStateOf(initialExpense?.dateMillis ?: System.currentTimeMillis()) }

    var categoryExpanded by remember { mutableStateOf(false) }
    var payerExpanded by remember { mutableStateOf(false) }

    val calendar = Calendar.getInstance().apply { timeInMillis = selectedDateMillis }
    val datePickerDialog = remember(selectedDateMillis) {
        DatePickerDialog(
            context,
            { _, year, month, dayOfMonth ->
                val newCal = Calendar.getInstance().apply {
                    set(Calendar.YEAR, year)
                    set(Calendar.MONTH, month)
                    set(Calendar.DAY_OF_MONTH, dayOfMonth)
                }
                selectedDateMillis = newCal.timeInMillis
            },
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH)
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = if (isEditing) "Edit Expense" else "Record Shared Expense",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Expense Title *") },
                    placeholder = { Text("e.g. Groceries, WiFi Bill, Dinner") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true
                )

                OutlinedTextField(
                    value = amountText,
                    onValueChange = { amountText = it },
                    label = { Text("Amount (₹) *") },
                    placeholder = { Text("0.00") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true
                )

                // Date Picker Field
                OutlinedTextField(
                    value = DateUtils.formatDisplayDate(selectedDateMillis),
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Expense Date") },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.CalendarToday,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                    },
                    trailingIcon = {
                        IconButton(onClick = { datePickerDialog.show() }) {
                            Icon(
                                imageVector = Icons.Default.EditCalendar,
                                contentDescription = "Pick Date",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { datePickerDialog.show() },
                    shape = RoundedCornerShape(12.dp)
                )

                // Category Selector
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
                        ExpenseCategories.forEach { cat ->
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

                // Payer Selector
                ExposedDropdownMenuBox(
                    expanded = payerExpanded,
                    onExpandedChange = { payerExpanded = !payerExpanded }
                ) {
                    OutlinedTextField(
                        value = selectedPayer.name,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Paid By") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = payerExpanded) },
                        modifier = Modifier
                            .menuAnchor()
                            .fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )
                    ExposedDropdownMenu(
                        expanded = payerExpanded,
                        onDismissRequest = { payerExpanded = false }
                    ) {
                        members.forEach { m ->
                            DropdownMenuItem(
                                text = { Text(m.name) },
                                onClick = {
                                    selectedPayer = m
                                    payerExpanded = false
                                }
                            )
                        }
                    }
                }

                // Split Type Selection
                Text("Split Option:", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val options = listOf("EQUAL" to "Split All", "CUSTOM" to "Custom", "PERSONAL" to "Personal")
                    options.forEach { (type, label) ->
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = if (splitType == type) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(10.dp))
                                .clickable { splitType = type }
                        ) {
                            Text(
                                text = label,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                modifier = Modifier.padding(vertical = 10.dp, horizontal = 4.dp)
                            )
                        }
                    }
                }

                // Custom Split Logic
                if (splitType == "CUSTOM") {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f))
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "Select flatmates for this split:",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                members.forEach { m ->
                                    val isSelected = selectedCustomMembers.any { it.id == m.id }
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(8.dp))
                                            .clickable {
                                                selectedCustomMembers = if (isSelected) {
                                                    if (selectedCustomMembers.size > 1) selectedCustomMembers.filter { it.id != m.id } else selectedCustomMembers
                                                } else {
                                                    selectedCustomMembers + m
                                                }
                                            }
                                    ) {
                                        Text(
                                            text = "${if (isSelected) "✓ " else "+ "}${m.name}",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                        )
                                    }
                                }
                            }

                            val numMembers = selectedCustomMembers.size.coerceAtLeast(1)
                            val parsedAmount = amountText.toDoubleOrNull() ?: 0.0
                            val perHead = parsedAmount / numMembers
                            Text(
                                text = "Split among $numMembers flatmates: ${DateUtils.formatCurrency(perHead)} each",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                } else if (splitType == "EQUAL") {
                    val numMembers = members.size.coerceAtLeast(1)
                    val parsedAmount = amountText.toDoubleOrNull() ?: 0.0
                    val perHead = parsedAmount / numMembers
                    Text(
                        text = "Split equally among all $numMembers flatmates: ${DateUtils.formatCurrency(perHead)} each",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else if (splitType == "PERSONAL") {
                    Text(
                        text = "🔒 Personal expense — Private and only visible to you (hidden from flatmates)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Medium
                    )
                }

                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Notes (Optional)") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val amount = amountText.toDoubleOrNull() ?: 0.0
                    if (title.isNotBlank() && amount > 0) {
                        onConfirm(title.trim(), amount, selectedCategory, selectedPayer, splitType, selectedCustomMembers, notes.trim(), selectedDateMillis)
                    }
                },
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(if (isEditing) "Save Changes" else "Add Expense")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun BudgetConfigDialog(
    currentLimit: Double,
    currentThreshold: Int = 80,
    onDismiss: () -> Unit,
    onSave: (newLimit: Double, threshold: Int) -> Unit
) {
    var limitText by remember(currentLimit) {
        mutableStateOf(if (currentLimit > 0) currentLimit.toInt().toString() else "0")
    }
    var threshold by remember(currentThreshold) {
        mutableIntStateOf(if (currentThreshold in 50..95) currentThreshold else 80)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Automated Budget Limits & Alerts",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "Configure your monthly spending threshold. RoomieVault will automatically push notifications when spending approaches or exceeds this limit.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                OutlinedTextField(
                    value = limitText,
                    onValueChange = { limitText = it },
                    label = { Text("Monthly Budget Limit (₹)") },
                    placeholder = { Text("e.g. 25000") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )

                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Warning Alert Trigger",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "$threshold% of budget",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Slider(
                        value = threshold.toFloat(),
                        onValueChange = { threshold = it.toInt() },
                        valueRange = 50f..95f,
                        steps = 8
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val limit = limitText.toDoubleOrNull() ?: 0.0
                    onSave(limit, threshold)
                },
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Save Budget")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
