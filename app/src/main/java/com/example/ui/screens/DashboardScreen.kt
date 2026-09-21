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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Payment
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.model.ChoreTask
import com.example.data.local.model.ExpenseItem
import com.example.data.local.model.HouseholdNotification
import com.example.data.local.model.SettlementDebt
import com.example.data.local.model.UserProfile
import com.example.ui.components.BudgetAlertBanner
import com.example.ui.components.ChoreDetailDialog
import com.example.ui.components.HouseholdNotificationDialog
import com.example.ui.components.MonthlySpendingTrendBarChart
import com.example.ui.viewmodel.BudgetStatus
import com.example.ui.viewmodel.ScreenTab
import com.example.util.DateUtils

@Composable
fun DashboardScreen(
    currentUser: UserProfile?,
    householdName: String,
    budgetStatus: BudgetStatus,
    todayChores: List<ChoreTask>,
    sundayRotationChore: ChoreTask?,
    pendingDebts: List<SettlementDebt>,
    expenses: List<ExpenseItem>,
    notifications: List<HouseholdNotification> = emptyList(),
    unreadNotificationsCount: Int = 0,
    members: List<UserProfile> = emptyList(),
    isSyncing: Boolean,
    onNavigate: (ScreenTab) -> Unit,
    onQuickAddExpense: () -> Unit,
    onQuickAddChore: () -> Unit,
    onQuickGiveMoney: () -> Unit,
    onToggleChore: (ChoreTask) -> Unit,
    onPayDebt: (SettlementDebt) -> Unit,
    onTriggerSync: () -> Unit,
    onRotateSundayChore: (ChoreTask) -> Unit,
    onSendAlert: (ChoreTask) -> Unit,
    onSendDebtReminder: ((SettlementDebt) -> Unit)? = null,
    onVerifyDebt: ((SettlementDebt, Boolean) -> Unit)? = null,
    onMarkNotificationRead: ((String) -> Unit)? = null,
    onMarkAllNotificationsRead: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    var showNotificationsDialog by remember { mutableStateOf(false) }
    var viewingChoreDetail by remember { mutableStateOf<ChoreTask?>(null) }

    val myDutiesToday = remember(todayChores, currentUser) {
        todayChores.filter { it.assignedToUserId == currentUser?.id }
    }
    val roommatesDutiesToday = remember(todayChores, currentUser) {
        todayChores.filter { it.assignedToUserId != currentUser?.id }
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Hero Profile & Household Header
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)
                )
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primary),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = currentUser?.name?.take(1)?.uppercase() ?: "R",
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimary,
                                    fontSize = 20.sp
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = "Hello, ${currentUser?.name ?: "Roommate"}",
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = householdName,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                                )
                            }
                        }

                        // Top Header Actions: Notification Bell + Cloud Sync
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            IconButton(
                                onClick = { showNotificationsDialog = true },
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.surface)
                            ) {
                                if (unreadNotificationsCount > 0) {
                                    BadgedBox(
                                        badge = {
                                            Badge(containerColor = MaterialTheme.colorScheme.error) {
                                                Text(unreadNotificationsCount.toString())
                                            }
                                        }
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Notifications,
                                            contentDescription = "Notifications",
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                } else {
                                    Icon(
                                        imageVector = Icons.Default.Notifications,
                                        contentDescription = "Notifications",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            IconButton(
                                onClick = onTriggerSync,
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.surface)
                            ) {
                                if (isSyncing) {
                                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                                } else {
                                    Icon(
                                        imageVector = Icons.Default.CloudSync,
                                        contentDescription = "Sync Supabase",
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Quick Action Buttons
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        QuickActionButton(
                            icon = Icons.Default.Add,
                            label = "+ Expense",
                            onClick = onQuickAddExpense,
                            modifier = Modifier.weight(1f)
                        )
                        QuickActionButton(
                            icon = Icons.Default.CleaningServices,
                            label = "+ Chore",
                            onClick = onQuickAddChore,
                            modifier = Modifier.weight(1f)
                        )
                        QuickActionButton(
                            icon = Icons.Default.Payment,
                            label = "Give Money",
                            onClick = onQuickGiveMoney,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }

        // Automated Budget Alert Banner
        item {
            BudgetAlertBanner(
                budgetStatus = budgetStatus,
                onAdjustBudgetClick = { onNavigate(ScreenTab.EXPENSES) }
            )
        }

        // Sunday-to-Sunday Rotating Chore Spotlight
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFFFEF3C7)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CalendarMonth,
                                    contentDescription = null,
                                    tint = Color(0xFFD97706),
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = "Sunday-to-Sunday Work Roster",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Next Sunday: ${DateUtils.getNextSundayDateString()}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        if (sundayRotationChore != null) {
                            IconButton(onClick = { onRotateSundayChore(sundayRotationChore) }) {
                                Icon(
                                    imageVector = Icons.Default.SwapHoriz,
                                    contentDescription = "Rotate next user",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    if (sundayRotationChore != null) {
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = sundayRotationChore.title,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = "Assigned for this Sunday: ${sundayRotationChore.assignedToUserName}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.Bold
                                    )
                                }

                                FilledTonalButton(
                                    onClick = { onToggleChore(sundayRotationChore) },
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Text(
                                        text = if (sundayRotationChore.status == "COMPLETED") "Done ✓" else "Mark Done",
                                        fontSize = 12.sp
                                    )
                                }
                            }
                        }
                    } else {
                        Text(
                            text = "No Sunday rotation chore set up. Create one in Chores!",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        // Today's Day-to-Day Chore Work Alert ("Whose work for what")
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Today's Work Distribution",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Today's duties roster across roommates",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                TextButton(onClick = { onNavigate(ScreenTab.CHORES) }) {
                    Text("View All Chores")
                }
            }
        }

        if (todayChores.isEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Box(modifier = Modifier.padding(20.dp), contentAlignment = Alignment.Center) {
                        Text(
                            text = "🎉 All household chores for today are completed or clear!",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        } else {
            // 1. My Duties Today
            if (myDutiesToday.isNotEmpty()) {
                item {
                    Text(
                        text = "⭐ Your Duty Today (${myDutiesToday.size})",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(start = 4.dp, top = 4.dp)
                    )
                }
                items(myDutiesToday, key = { "my_${it.id}" }) { chore ->
                    ChoreQuickCard(
                        chore = chore,
                        currentUser = currentUser,
                        onClick = { viewingChoreDetail = chore },
                        onToggle = { onToggleChore(chore) },
                        onSendAlert = { onSendAlert(chore) }
                    )
                }
            }

            // 2. Roommates' Duties Today
            if (roommatesDutiesToday.isNotEmpty()) {
                item {
                    Text(
                        text = "👥 Roommates' Duty Today (${roommatesDutiesToday.size})",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 4.dp, top = 6.dp)
                    )
                }
                items(roommatesDutiesToday, key = { "rm_${it.id}" }) { chore ->
                    ChoreQuickCard(
                        chore = chore,
                        currentUser = currentUser,
                        onClick = { viewingChoreDetail = chore },
                        onToggle = { onToggleChore(chore) },
                        onSendAlert = { onSendAlert(chore) }
                    )
                }
            }
        }

        // Friend Money Alerts & Settlements Spotlight
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Giving Money to Friend Alert",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                TextButton(onClick = { onNavigate(ScreenTab.SETTLEMENTS) }) {
                    Text("All Splits & UPI")
                }
            }
        }

        if (pendingDebts.isEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Box(modifier = Modifier.padding(20.dp), contentAlignment = Alignment.Center) {
                        Text(
                            text = "✨ All shared roommate debts are settled! No pending dues.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        } else {
            items(pendingDebts.take(3), key = { it.id }) { debt ->
                PendingDebtQuickCard(
                    debt = debt,
                    currentUser = currentUser,
                    onPay = { onPayDebt(debt) },
                    onSendReminder = { onSendDebtReminder?.invoke(debt) },
                    onVerify = { isVerified -> onVerifyDebt?.invoke(debt, isVerified) }
                )
            }
        }

        // Monthly Spending Bar Trend
        item {
            MonthlySpendingTrendBarChart(
                expenses = expenses,
                budgetLimit = budgetStatus.budgetLimit
            )
        }
    }

    // Household In-App Notification Center Modal
    if (showNotificationsDialog) {
        HouseholdNotificationDialog(
            notifications = notifications,
            onDismiss = { showNotificationsDialog = false },
            onMarkAsRead = { id -> onMarkNotificationRead?.invoke(id) },
            onMarkAllAsRead = { onMarkAllNotificationsRead?.invoke() },
            onActionClick = { type, _ ->
                showNotificationsDialog = false
                when (type) {
                    "DEBT_SETTLEMENT", "DEBT_VERIFY" -> onNavigate(ScreenTab.SETTLEMENTS)
                    "CHORE_REMINDER" -> onNavigate(ScreenTab.CHORES)
                    "EXPENSE_SPLIT" -> onNavigate(ScreenTab.EXPENSES)
                    else -> {}
                }
            }
        )
    }

    // Chore Detailed Inspection Modal
    viewingChoreDetail?.let { chore ->
        ChoreDetailDialog(
            chore = chore,
            members = members,
            currentUser = currentUser,
            onDismiss = { viewingChoreDetail = null },
            onToggleStatus = {
                onToggleChore(chore)
                viewingChoreDetail = null
            },
            onSendNudge = {
                onSendAlert(chore)
                viewingChoreDetail = null
            },
            onRotateNext = {
                onRotateSundayChore(chore)
                viewingChoreDetail = null
            },
            onEdit = {
                viewingChoreDetail = null
                onNavigate(ScreenTab.CHORES)
            }
        )
    }
}

@Composable
fun QuickActionButton(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Button(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = ButtonDefaults.buttonElevation(defaultElevation = 1.dp)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(vertical = 4.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
fun ChoreQuickCard(
    chore: ChoreTask,
    currentUser: UserProfile?,
    onClick: () -> Unit,
    onToggle: () -> Unit,
    onSendAlert: () -> Unit
) {
    val isMyChore = chore.assignedToUserId == currentUser?.id
    val isCompleted = chore.status == "COMPLETED"

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable { onClick() },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isMyChore) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f) else MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onToggle) {
                Icon(
                    imageVector = if (isCompleted) Icons.Default.CheckCircle else Icons.Default.CleaningServices,
                    contentDescription = "Toggle",
                    tint = if (isCompleted) Color(0xFF10B981) else MaterialTheme.colorScheme.primary
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = chore.title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = if (isMyChore) "Your Duty • ${chore.scheduledTime} (+${chore.points}pts)" else "Duty: ${chore.assignedToUserName} • ${chore.scheduledTime}",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isMyChore) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = if (isMyChore) FontWeight.Bold else FontWeight.Normal
                )
            }

            // Bell button to send reminder alert to roommate (or alert if self)
            IconButton(
                onClick = onSendAlert,
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(if (isMyChore) Color(0xFFF3F4F6) else Color(0xFFFEF3C7))
            ) {
                Icon(
                    imageVector = Icons.Default.NotificationsActive,
                    contentDescription = if (isMyChore) "Alert" else "Nudge ${chore.assignedToUserName}",
                    tint = if (isMyChore) MaterialTheme.colorScheme.onSurfaceVariant else Color(0xFFD97706),
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

@Composable
fun PendingDebtQuickCard(
    debt: SettlementDebt,
    currentUser: UserProfile?,
    onPay: () -> Unit,
    onSendReminder: () -> Unit,
    onVerify: (Boolean) -> Unit
) {
    val isDebtor = debt.fromUserId == currentUser?.id
    val isCreditor = debt.toUserId == currentUser?.id
    val isPendingConfirmation = debt.status == "PENDING_CONFIRMATION"

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isDebtor) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f) else MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = when {
                        isDebtor -> "You owe ${debt.toUserName}"
                        isCreditor -> "${debt.fromUserName} owes you"
                        else -> "${debt.fromUserName} owes ${debt.toUserName}"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (isDebtor) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "${debt.reason} • UPI: ${debt.toUserUpiId.ifBlank { "Not added" }}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (isPendingConfirmation) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Payment submitted (Ref: ${debt.transactionRef})",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFFD97706),
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = DateUtils.formatCurrency(debt.amount),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.ExtraBold,
                    color = if (isDebtor) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(4.dp))

                when {
                    isDebtor -> {
                        if (isPendingConfirmation) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = Color(0xFFFEF3C7)
                            ) {
                                Text(
                                    text = "Pending ✓",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color(0xFFB45309),
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        } else {
                            Button(
                                onClick = onPay,
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                modifier = Modifier.height(32.dp)
                            ) {
                                Text(text = "Pay UPI", fontSize = 11.sp)
                            }
                        }
                    }
                    isCreditor -> {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            // Bell reminder button for creditor
                            IconButton(
                                onClick = onSendReminder,
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFFFEF3C7))
                            ) {
                                Icon(
                                    imageVector = Icons.Default.NotificationsActive,
                                    contentDescription = "Send Reminder",
                                    tint = Color(0xFFD97706),
                                    modifier = Modifier.size(16.dp)
                                )
                            }

                            if (isPendingConfirmation) {
                                Button(
                                    onClick = { onVerify(true) },
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                    modifier = Modifier.height(32.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981))
                                ) {
                                    Text(text = "Verify ✓", fontSize = 11.sp, color = Color.White)
                                }
                            }
                        }
                    }
                    else -> {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant
                        ) {
                            Text(
                                text = "Shared",
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
