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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Payment
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.model.SettlementDebt
import com.example.data.local.model.UserProfile
import com.example.ui.components.UpiPaymentDialog
import com.example.util.DateUtils
import com.example.util.QrCameraScannerDialog

@Composable
fun SettlementsScreen(
    debts: List<SettlementDebt>,
    members: List<UserProfile>,
    currentUser: UserProfile?,
    onPayViaUpi: (SettlementDebt, String?) -> Unit,
    onVerifyPayment: (SettlementDebt, Boolean) -> Unit,
    onRequestConfirmation: (SettlementDebt, String) -> Unit,
    onSendReminder: (SettlementDebt) -> Unit,
    onDeleteDebt: (String) -> Unit,
    onAddDebt: (fromUser: UserProfile, toUser: UserProfile, amount: Double, reason: String) -> Unit,
    onPay3rdParty: ((payeeName: String, upiId: String, amount: Double, note: String, recordExpense: Boolean) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    var showAddDialog by remember { mutableStateOf(false) }
    var showThirdPartyPayDialog by remember { mutableStateOf(false) }
    var selectedDebtForPayment by remember { mutableStateOf<SettlementDebt?>(null) }
    var filterTab by remember { mutableStateOf("PENDING") } // PENDING, SETTLED, ALL

    val allRoommates = remember(members, currentUser) {
        val combined = mutableListOf<UserProfile>()
        currentUser?.let { combined.add(it) }
        members.forEach { m ->
            if (combined.none { it.id == m.id }) combined.add(m)
        }
        combined.ifEmpty { listOfNotNull(currentUser) }
    }

    val myId = currentUser?.id ?: "USR_ALEX"

    val totalIOwe = remember(debts, myId) {
        debts.filter { it.fromUserId == myId && it.status != "VERIFIED" }.sumOf { it.amount }
    }

    val totalOwedToMe = remember(debts, myId) {
        debts.filter { it.toUserId == myId && it.status != "VERIFIED" }.sumOf { it.amount }
    }

    val filteredDebts = remember(debts, filterTab) {
        when (filterTab) {
            "PENDING" -> debts.filter { it.status != "VERIFIED" }
            "SETTLED" -> debts.filter { it.status == "VERIFIED" }
            else -> debts
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
                        text = "Giving Money & Splits",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Friend debts, one-tap UPI pay, and manual check verification",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Balance Summary Cards
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Card(
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFFEF2F2))
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.ArrowUpward,
                                    contentDescription = null,
                                    tint = Color(0xFFEF4444),
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "You Owe",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color(0xFF991B1B)
                                )
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = DateUtils.formatCurrency(totalIOwe),
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color(0xFFDC2626)
                            )
                        }
                    }

                    Card(
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFECFDF5))
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.ArrowDownward,
                                    contentDescription = null,
                                    tint = Color(0xFF10B981),
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "You're Owed",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color(0xFF065F46)
                                )
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = DateUtils.formatCurrency(totalOwedToMe),
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color(0xFF059669)
                            )
                        }
                    }
                }
            }

            // Pay 3rd Party / Vendor Banner
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .clickable { showThirdPartyPayDialog = true },
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(44.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Default.QrCodeScanner,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onPrimary,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = "Pay 3rd Party / Scan QR",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "Direct UPI transfer to Landlord, Maid, Cook or Bill",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.primary
                        ) {
                            Text(
                                text = "PAY UPI",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                            )
                        }
                    }
                }
            }

            // Filter Tabs
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val tabs = listOf(
                        "PENDING" to "Pending Dues (${debts.count { it.status != "VERIFIED" }})",
                        "SETTLED" to "Settled History (${debts.count { it.status == "VERIFIED" }})",
                        "ALL" to "All"
                    )
                    tabs.forEach { (key, label) ->
                        val isSelected = filterTab == key
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { filterTab = key }
                        ) {
                            Text(
                                text = label,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.padding(vertical = 10.dp, horizontal = 4.dp),
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                        }
                    }
                }
            }

            // Debts List
            if (filteredDebts.isEmpty()) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Box(modifier = Modifier.padding(32.dp), contentAlignment = Alignment.Center) {
                            Text(
                                text = if (filterTab == "PENDING") "🎉 No pending settlements! Everyone is squared up." else "No records found.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            } else {
                items(filteredDebts, key = { it.id }) { debt ->
                    DebtCardItem(
                        debt = debt,
                        isCurrentUsersDebt = debt.fromUserId == myId,
                        isOwedToCurrentUser = debt.toUserId == myId,
                        onPayClick = { selectedDebtForPayment = debt },
                        onToggleVerified = { isVerified -> onVerifyPayment(debt, isVerified) },
                        onRequestConfirmation = { txRef -> onRequestConfirmation(debt, txRef) },
                        onSendReminder = { onSendReminder(debt) },
                        onDelete = { onDeleteDebt(debt.id) }
                    )
                }
            }

            item {
                Spacer(modifier = Modifier.height(64.dp))
            }
        }

        FloatingActionButton(
            onClick = { showAddDialog = true },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(24.dp),
            containerColor = MaterialTheme.colorScheme.primary
        ) {
            Icon(imageVector = Icons.Default.Add, contentDescription = "Record Debt", tint = Color.White)
        }
    }

    if (showAddDialog) {
        AddDebtDialog(
            members = allRoommates,
            currentUser = currentUser,
            onDismiss = { showAddDialog = false },
            onConfirm = { from, to, amount, reason ->
                onAddDebt(from, to, amount, reason)
                showAddDialog = false
            }
        )
    }

    if (showThirdPartyPayDialog) {
        ThirdPartyPaymentDialog(
            onDismiss = { showThirdPartyPayDialog = false },
            onConfirm = { name, upi, amount, note, recordExpense ->
                onPay3rdParty?.invoke(name, upi, amount, note, recordExpense)
                // Also trigger UPI payment dialog / intent
                val tempDebt = SettlementDebt(
                    id = java.util.UUID.randomUUID().toString(),
                    fromUserId = currentUser?.id ?: "",
                    fromUserName = currentUser?.name ?: "Me",
                    toUserId = "THIRD_PARTY",
                    toUserName = name,
                    toUserUpiId = upi,
                    amount = amount,
                    reason = note.ifBlank { "Payment to $name" },
                    householdId = currentUser?.householdId ?: ""
                )
                selectedDebtForPayment = tempDebt
                showThirdPartyPayDialog = false
            }
        )
    }

    if (selectedDebtForPayment != null) {
        val debt = selectedDebtForPayment!!
        UpiPaymentDialog(
            debt = debt,
            onDismiss = { selectedDebtForPayment = null },
            onLaunchUpiApp = { app -> onPayViaUpi(debt, app) },
            onMarkVerified = { isVerified -> onVerifyPayment(debt, isVerified) },
            onRequestConfirmation = { txRef -> onRequestConfirmation(debt, txRef) }
        )
    }
}

@Composable
fun DebtCardItem(
    debt: SettlementDebt,
    isCurrentUsersDebt: Boolean,
    isOwedToCurrentUser: Boolean,
    onPayClick: () -> Unit,
    onToggleVerified: (Boolean) -> Unit,
    onRequestConfirmation: (String) -> Unit,
    onSendReminder: () -> Unit,
    onDelete: () -> Unit
) {
    val isVerified = debt.status == "VERIFIED"
    val isAwaitingConfirmation = debt.status == "PAID_PENDING_CONFIRMATION"
    var showSendRefDialog by remember { mutableStateOf(false) }
    var txRefInput by remember { mutableStateOf("") }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = when {
                isVerified -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                isCurrentUsersDebt -> Color(0xFFFFF7ED) // Gentle warm tint for dues I owe
                isOwedToCurrentUser -> Color(0xFFF0FDF4) // Gentle mint tint for money I am owed
                else -> MaterialTheme.colorScheme.surface
            }
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isVerified) 0.dp else 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header Row: Perspective Title and Amount
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    // Headline based on who is viewing
                    val headline = when {
                        isCurrentUsersDebt -> "You owe ${debt.toUserName}"
                        isOwedToCurrentUser -> "${debt.fromUserName} owes you"
                        else -> "${debt.fromUserName} → ${debt.toUserName}"
                    }

                    Text(
                        text = headline,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color = when {
                            isVerified -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                            isCurrentUsersDebt -> Color(0xFFC2410C)
                            isOwedToCurrentUser -> Color(0xFF047857)
                            else -> MaterialTheme.colorScheme.onSurface
                        }
                    )

                    Spacer(modifier = Modifier.height(2.dp))

                    val subtitle = when {
                        isCurrentUsersDebt -> "${debt.reason} • Pay via UPI: ${debt.toUserUpiId.ifBlank { "Not added" }}"
                        isOwedToCurrentUser -> "${debt.reason} • Expected on your UPI (${debt.toUserUpiId.ifBlank { "Not configured" }})"
                        else -> debt.reason
                    }
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = DateUtils.formatCurrency(debt.amount),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.ExtraBold,
                        color = when {
                            isVerified -> Color(0xFF10B981)
                            isCurrentUsersDebt -> Color(0xFFDC2626)
                            else -> Color(0xFF059669)
                        }
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = when {
                            isVerified -> Color(0xFFDCFCE7)
                            isAwaitingConfirmation -> Color(0xFFFEF3C7)
                            else -> Color(0xFFFEE2E2)
                        }
                    ) {
                        Text(
                            text = when {
                                isVerified -> "SETTLED ✓"
                                isAwaitingConfirmation -> "CONFIRMATION PENDING"
                                else -> "DUE PENDING"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = when {
                                isVerified -> Color(0xFF166534)
                                isAwaitingConfirmation -> Color(0xFF92400E)
                                else -> Color(0xFF991B1B)
                            },
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }

            // If debtor has marked payment sent with reference, display banner
            if (isAwaitingConfirmation && debt.transactionRef.isNotBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color(0xFFFEF3C7),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Payment,
                            contentDescription = null,
                            tint = Color(0xFFB45309),
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Txn Ref: ${debt.transactionRef}",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFF92400E)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Action Row: Role-specific action buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Left Side Action: Creditor gets "Mark Verified" checkbox
                if (isOwedToCurrentUser || (!isCurrentUsersDebt && !isOwedToCurrentUser)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.clickable { onToggleVerified(!isVerified) }
                    ) {
                        Checkbox(
                            checked = isVerified,
                            onCheckedChange = { onToggleVerified(it) }
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (isVerified) "Settled ✓" else "Verify Receipt",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Medium
                        )
                    }
                } else {
                    // Debtor sees helpful reminder note or "I've paid" text button
                    if (!isVerified && !isAwaitingConfirmation) {
                        TextButton(
                            onClick = { showSendRefDialog = true },
                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "Paid via cash/app? Send Ref →",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    } else if (isAwaitingConfirmation) {
                        Text(
                            text = "Waiting for ${debt.toUserName} to verify",
                            fontSize = 11.sp,
                            color = Color(0xFF92400E),
                            fontWeight = FontWeight.Medium
                        )
                    } else {
                        Spacer(modifier = Modifier.width(1.dp))
                    }
                }

                // Right Side Actions
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (!isVerified) {
                        // 1. BELL ICON: ONLY shown to the CREDITOR (the person who is owed money) to nudge debtor!
                        if (isOwedToCurrentUser) {
                            FilledTonalButton(
                                onClick = onSendReminder,
                                shape = RoundedCornerShape(10.dp),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                modifier = Modifier.height(34.dp),
                                colors = ButtonDefaults.filledTonalButtonColors(
                                    containerColor = Color(0xFFFEF3C7),
                                    contentColor = Color(0xFFB45309)
                                )
                            ) {
                                Icon(
                                    imageVector = Icons.Default.NotificationsActive,
                                    contentDescription = "Send Reminder",
                                    tint = Color(0xFFD97706),
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "Remind",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        // 2. VERIFY & SETTLE BUTTON: Shown to CREDITOR when debtor has marked payment sent
                        if (isOwedToCurrentUser && isAwaitingConfirmation) {
                            Button(
                                onClick = { onToggleVerified(true) },
                                shape = RoundedCornerShape(10.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                modifier = Modifier.height(34.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF059669))
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(text = "Verify & Settle", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }

                        // 3. PAY UPI BUTTON: ONLY shown to the DEBTOR (the person who owes money) to pay the creditor!
                        if (isCurrentUsersDebt) {
                            Button(
                                onClick = onPayClick,
                                shape = RoundedCornerShape(10.dp),
                                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 4.dp),
                                modifier = Modifier.height(36.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB))
                            ) {
                                Icon(
                                    imageVector = Icons.Default.OpenInNew,
                                    contentDescription = null,
                                    modifier = Modifier.size(15.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Pay UPI",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
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

    // Debtor manual payment reference dialog
    if (showSendRefDialog) {
        AlertDialog(
            onDismissRequest = { showSendRefDialog = false },
            title = { Text("Payment Confirmation Reference") },
            text = {
                Column {
                    Text(
                        text = "If you already paid ₹${debt.amount} to ${debt.toUserName} via GPay, PhonePe, Paytm, or Cash, submit the reference below so they can verify.",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedTextField(
                        value = txRefInput,
                        onValueChange = { txRefInput = it },
                        label = { Text("UPI Txn Ref / Note (Optional)") },
                        placeholder = { Text("e.g. UPI329104829 or Cash Paid") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onRequestConfirmation(txRefInput.trim().ifBlank { "PAID_VIA_UPI" })
                        showSendRefDialog = false
                    },
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("Notify ${debt.toUserName}")
                }
            },
            dismissButton = {
                TextButton(onClick = { showSendRefDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddDebtDialog(
    members: List<UserProfile>,
    currentUser: UserProfile?,
    onDismiss: () -> Unit,
    onConfirm: (from: UserProfile, to: UserProfile, amount: Double, reason: String) -> Unit
) {
    val allRoommates = remember(members, currentUser) {
        val combined = mutableListOf<UserProfile>()
        currentUser?.let { combined.add(it) }
        members.forEach { m ->
            if (combined.none { it.id == m.id }) combined.add(m)
        }
        combined.ifEmpty { listOfNotNull(currentUser) }
    }

    var fromUser by remember {
        mutableStateOf(
            allRoommates.firstOrNull { it.id != currentUser?.id }
                ?: allRoommates.firstOrNull()
                ?: UserProfile("USR_1", "Roommate")
        )
    }
    var toUser by remember {
        mutableStateOf(
            currentUser
                ?: allRoommates.firstOrNull { it.id != fromUser.id }
                ?: allRoommates.firstOrNull()
                ?: UserProfile("USR_ALEX", "Self")
        )
    }
    var amountText by remember { mutableStateOf("") }
    var reason by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    var fromExpanded by remember { mutableStateOf(false) }
    var toExpanded by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Record Money Given / Debt",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Who Owes Money (From)
                ExposedDropdownMenuBox(
                    expanded = fromExpanded,
                    onExpandedChange = { fromExpanded = !fromExpanded }
                ) {
                    OutlinedTextField(
                        value = fromUser.name,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Person Who Owes Money (Borrower) *") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = fromExpanded) },
                        modifier = Modifier
                            .menuAnchor()
                            .fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )
                    ExposedDropdownMenu(
                        expanded = fromExpanded,
                        onDismissRequest = { fromExpanded = false }
                    ) {
                        allRoommates.forEach { m ->
                            DropdownMenuItem(
                                text = { Text(m.name) },
                                onClick = {
                                    fromUser = m
                                    fromExpanded = false
                                    errorMessage = null
                                }
                            )
                        }
                    }
                }

                // Who Lent Money (To / Payee)
                ExposedDropdownMenuBox(
                    expanded = toExpanded,
                    onExpandedChange = { toExpanded = !toExpanded }
                ) {
                    OutlinedTextField(
                        value = toUser.name,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Person to Receive Money (Lender) *") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = toExpanded) },
                        modifier = Modifier
                            .menuAnchor()
                            .fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )
                    ExposedDropdownMenu(
                        expanded = toExpanded,
                        onDismissRequest = { toExpanded = false }
                    ) {
                        allRoommates.forEach { m ->
                            DropdownMenuItem(
                                text = { Text(m.name) },
                                onClick = {
                                    toUser = m
                                    toExpanded = false
                                    errorMessage = null
                                }
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = amountText,
                    onValueChange = {
                        amountText = it
                        errorMessage = null
                    },
                    label = { Text("Amount (₹) *") },
                    placeholder = { Text("e.g. 500") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true
                )

                OutlinedTextField(
                    value = reason,
                    onValueChange = { reason = it },
                    label = { Text("Reason / Description (Optional)") },
                    placeholder = { Text("e.g. Lent for dinner, Groceries, Electricity bill") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )

                if (errorMessage != null) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.6f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = errorMessage ?: "",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val amount = amountText.toDoubleOrNull() ?: 0.0
                    if (amount <= 0.0) {
                        errorMessage = "Please enter an amount greater than ₹0."
                        return@Button
                    }
                    if (fromUser.id == toUser.id) {
                        errorMessage = "Borrower and Lender cannot be the same roommate. Please select different people."
                        return@Button
                    }
                    val finalReason = reason.trim().ifBlank { "Direct loan / settlement" }
                    onConfirm(fromUser, toUser, amount, finalReason)
                },
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Save Debt Record")
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
fun ThirdPartyPaymentDialog(
    onDismiss: () -> Unit,
    onConfirm: (payeeName: String, upiId: String, amount: Double, note: String, recordExpense: Boolean) -> Unit
) {
    var payeeName by remember { mutableStateOf("") }
    var upiId by remember { mutableStateOf("") }
    var amountText by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var recordAsExpense by remember { mutableStateOf(true) }
    var showQrScanner by remember { mutableStateOf(false) }

    if (showQrScanner) {
        QrCameraScannerDialog(
            onDismiss = { showQrScanner = false },
            onQrScanned = { scannedUpi, scannedName ->
                upiId = scannedUpi
                if (payeeName.isBlank() && scannedName.isNotBlank()) {
                    payeeName = scannedName
                }
                showQrScanner = false
            }
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Storefront,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Pay 3rd Party via UPI", fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                // Quick Suggestion Chips
                Text("Quick Selection:", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    val presets = listOf("Landlord", "Maid", "Cook", "Electricity", "Grocer")
                    presets.forEach { preset ->
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (payeeName == preset) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { payeeName = preset }
                        ) {
                            Text(
                                text = preset,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = if (payeeName == preset) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = payeeName,
                    onValueChange = { payeeName = it },
                    label = { Text("Payee Name / Title *") },
                    placeholder = { Text("e.g. House Owner, Cook Didi") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true
                )

                OutlinedTextField(
                    value = upiId,
                    onValueChange = { upiId = it },
                    label = { Text("Payee UPI ID *") },
                    placeholder = { Text("e.g. landlord@okhdfcbank") },
                    trailingIcon = {
                        IconButton(onClick = { showQrScanner = true }) {
                            Icon(
                                imageVector = Icons.Default.QrCodeScanner,
                                contentDescription = "Scan QR",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    },
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

                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("Payment Note (Optional)") },
                    placeholder = { Text("e.g. September Rent, Grocery bill") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Record as Shared Expense",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Switch(
                        checked = recordAsExpense,
                        onCheckedChange = { recordAsExpense = it }
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val amount = amountText.toDoubleOrNull() ?: 0.0
                    if (payeeName.isNotBlank() && upiId.isNotBlank() && amount > 0) {
                        onConfirm(payeeName.trim(), upiId.trim(), amount, note.trim(), recordAsExpense)
                    }
                },
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Proceed to UPI Pay")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
