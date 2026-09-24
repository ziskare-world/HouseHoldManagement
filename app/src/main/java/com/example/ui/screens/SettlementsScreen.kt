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
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.data.local.model.SettlementDebt
import com.example.data.local.model.UserProfile
import com.example.util.DateUtils

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
    onPay3rdParty: ((String, String, Double, String, Boolean) -> Unit)? = null,
    onAddPerson: ((name: String, email: String, upiId: String, isExternalFriend: Boolean, colorHex: String, onComplete: ((Boolean, String) -> Unit)?) -> Unit)? = null,
    onSearchUserInCloud: ((query: String, onResult: (List<UserProfile>) -> Unit) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val userId = currentUser?.id.orEmpty()
    var showAddDebtDialog by remember { mutableStateOf(false) }
    var showAddFriendDialog by remember { mutableStateOf(false) }
    var selectedTab by remember { mutableStateOf("PENDING") } // PENDING or SETTLED

    val pendingDebts = remember(debts) { debts.filter { it.status != "VERIFIED" } }
    val settledDebts = remember(debts) { debts.filter { it.status == "VERIFIED" } }
    val displayDebts = if (selectedTab == "PENDING") pendingDebts else settledDebts

    Scaffold(
        modifier = modifier.fillMaxSize(),
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAddDebtDialog = true },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = Color.White
            ) {
                Icon(imageVector = Icons.Default.Add, contentDescription = "Add Debt or Loan")
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            // Header with Add Friend shortcut
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Giving Money & Splits",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Track loans, settle shared balances, and manage external friends",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (onAddPerson != null) {
                    OutlinedButton(
                        onClick = { showAddFriendDialog = true },
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Icon(Icons.Default.PersonAdd, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Add Friend", style = MaterialTheme.typography.labelMedium)
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Tabs: Pending vs Settled
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = if (selectedTab == "PENDING") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier
                        .weight(1f)
                        .clickable { selectedTab = "PENDING" }
                ) {
                    Text(
                        text = "Pending (${pendingDebts.size})",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (selectedTab == "PENDING") MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 10.dp),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }

                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = if (selectedTab == "SETTLED") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier
                        .weight(1f)
                        .clickable { selectedTab = "SETTLED" }
                ) {
                    Text(
                        text = "Settled (${settledDebts.size})",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (selectedTab == "SETTLED") MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 10.dp),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (displayDebts.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (selectedTab == "PENDING") "All settled up! No pending debts." else "No settled records yet.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(displayDebts, key = { it.id }) { debt ->
                        val isDebtor = debt.fromUserId == userId
                        val isCreditor = debt.toUserId == userId
                        val isPendingVerification = debt.status == "PENDING_CONFIRMATION"
                        val isVerified = debt.status == "VERIFIED"

                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = if (isVerified) {
                                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                } else if (isPendingVerification) {
                                    MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.35f)
                                } else {
                                    MaterialTheme.colorScheme.surface
                                }
                            ),
                            elevation = CardDefaults.cardElevation(defaultElevation = if (isVerified) 0.dp else 2.dp)
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                // Top row: Who owes whom + Badge
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Box(
                                            modifier = Modifier
                                                .size(32.dp)
                                                .clip(CircleShape)
                                                .background(
                                                    if (isDebtor) Color(0xFFEF4444).copy(alpha = 0.15f)
                                                    else if (isCreditor) Color(0xFF10B981).copy(alpha = 0.15f)
                                                    else MaterialTheme.colorScheme.surfaceVariant
                                                ),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = if (isDebtor) Icons.Default.ArrowDownward else Icons.Default.ArrowUpward,
                                                contentDescription = null,
                                                tint = if (isDebtor) Color(0xFFEF4444) else Color(0xFF10B981),
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Column {
                                            val otherUserId = if (isDebtor) debt.toUserId else debt.fromUserId
                                            val otherMember = members.find { it.id == otherUserId }
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Text(
                                                    text = when {
                                                        isDebtor -> "You owe ${debt.toUserName}"
                                                        isCreditor -> "${debt.fromUserName} owes you"
                                                        else -> "${debt.fromUserName} owes ${debt.toUserName}"
                                                    },
                                                    style = MaterialTheme.typography.titleSmall,
                                                    fontWeight = FontWeight.Bold
                                                )
                                                if (otherMember?.isExternalFriend == true) {
                                                    Spacer(modifier = Modifier.width(6.dp))
                                                    Surface(
                                                        shape = RoundedCornerShape(4.dp),
                                                        color = Color(0xFFE8F5E9)
                                                    ) {
                                                        Text(
                                                            text = "Friend",
                                                            style = MaterialTheme.typography.labelSmall,
                                                            color = Color(0xFF2E7D32),
                                                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                                        )
                                                    }
                                                }
                                            }
                                            Text(
                                                text = debt.reason.ifBlank { "Direct loan / settlement" },
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }

                                    Text(
                                        text = DateUtils.formatCurrency(debt.amount),
                                        style = MaterialTheme.typography.titleLarge,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = if (isDebtor) Color(0xFFEF4444) else if (isCreditor) Color(0xFF10B981) else MaterialTheme.colorScheme.onSurface
                                    )
                                }

                                // Status badge
                                if (isPendingVerification) {
                                    Spacer(modifier = Modifier.height(10.dp))
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = MaterialTheme.colorScheme.tertiaryContainer
                                    ) {
                                        Text(
                                            text = if (isCreditor) "🔔 Roommate marked paid — Tap Verify to settle" else "⏳ Payment reference sent — Awaiting roommate confirmation",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.onTertiaryContainer,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(12.dp))

                                // Action Buttons Row
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.End,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    // If user is debtor and not yet verified: show Pay UPI button
                                    if (!isVerified && isDebtor) {
                                        Button(
                                            onClick = { onPayViaUpi(debt, null) },
                                            shape = RoundedCornerShape(10.dp),
                                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.OpenInNew,
                                                contentDescription = null,
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text("Pay UPI", fontWeight = FontWeight.Bold)
                                        }
                                    }

                                    // If user is creditor (waiting for money): show Bell reminder and Verify buttons
                                    if (!isVerified && isCreditor) {
                                        // Bell Icon to send push reminder
                                        IconButton(
                                            onClick = { onSendReminder(debt) },
                                            modifier = Modifier.size(40.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.NotificationsActive,
                                                contentDescription = "Send Reminder Nudge",
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(22.dp)
                                            )
                                        }

                                        Spacer(modifier = Modifier.width(6.dp))

                                        // Verify and Settle button
                                        OutlinedButton(
                                            onClick = { onVerifyPayment(debt, true) },
                                            shape = RoundedCornerShape(10.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.CheckCircle,
                                                contentDescription = "Verify",
                                                tint = Color(0xFF10B981),
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text("Verify & Settle", color = Color(0xFF10B981), fontWeight = FontWeight.Bold)
                                        }
                                    }

                                    Spacer(modifier = Modifier.width(4.dp))

                                    // Delete button
                                    IconButton(
                                        onClick = { onDeleteDebt(debt.id) },
                                        modifier = Modifier.size(40.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Delete,
                                            contentDescription = "Delete Debt",
                                            tint = MaterialTheme.colorScheme.error,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Add Debt / Loan Dialog
    if (showAddDebtDialog) {
        AddDebtDialog(
            members = members,
            currentUser = currentUser,
            onDismiss = { showAddDebtDialog = false },
            onConfirm = { fromUser, toUser, amount, reason ->
                onAddDebt(fromUser, toUser, amount, reason)
                showAddDebtDialog = false
            },
            onAddPerson = onAddPerson,
            onSearchUserInCloud = onSearchUserInCloud
        )
    }

    // Standalone Add External Friend Dialog
    if (showAddFriendDialog && onAddPerson != null) {
        AddExternalFriendDialog(
            onDismiss = { showAddFriendDialog = false },
            onAddPerson = onAddPerson,
            onSearchUserInCloud = onSearchUserInCloud
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddDebtDialog(
    members: List<UserProfile>,
    currentUser: UserProfile?,
    onDismiss: () -> Unit,
    onConfirm: (fromUser: UserProfile, toUser: UserProfile, amount: Double, reason: String) -> Unit,
    onAddPerson: ((name: String, email: String, upiId: String, isExternalFriend: Boolean, colorHex: String, onComplete: ((Boolean, String) -> Unit)?) -> Unit)? = null,
    onSearchUserInCloud: ((query: String, onResult: (List<UserProfile>) -> Unit) -> Unit)? = null
) {
    var fromUser by remember { mutableStateOf(members.firstOrNull { it.id != currentUser?.id } ?: currentUser ?: UserProfile("1", "Roommate 1")) }
    var toUser by remember { mutableStateOf(currentUser ?: members.firstOrNull() ?: UserProfile("2", "Roommate 2")) }
    var amountText by remember { mutableStateOf("") }
    var reason by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    var expandedFrom by remember { mutableStateOf(false) }
    var expandedTo by remember { mutableStateOf(false) }
    var showInlineAddFriend by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Record Loan / Given Money", fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                // Quick shortcut to add external friend
                if (onAddPerson != null) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(
                            onClick = { showInlineAddFriend = true },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Icon(Icons.Default.PersonAdd, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("+ Add Friend", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }

                // Who owes? (Debtor)
                ExposedDropdownMenuBox(
                    expanded = expandedFrom,
                    onExpandedChange = { expandedFrom = !expandedFrom }
                ) {
                    val fromTag = if (fromUser.isExternalFriend) "Friend" else "Roommate"
                    OutlinedTextField(
                        value = "${fromUser.name} ($fromTag)",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Who owes money? (Debtor)") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedFrom) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor(),
                        shape = RoundedCornerShape(12.dp)
                    )
                    ExposedDropdownMenu(
                        expanded = expandedFrom,
                        onDismissRequest = { expandedFrom = false }
                    ) {
                        members.forEach { m ->
                            val tag = if (m.isExternalFriend) "Friend" else "Roommate"
                            val isMe = m.id == currentUser?.id
                            DropdownMenuItem(
                                text = {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(if (isMe) "${m.name} (You)" else m.name)
                                        Spacer(Modifier.width(6.dp))
                                        Surface(
                                            shape = RoundedCornerShape(4.dp),
                                            color = if (m.isExternalFriend) Color(0xFFE8F5E9) else Color(0xFFEDE7F6)
                                        ) {
                                            Text(
                                                text = tag,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = if (m.isExternalFriend) Color(0xFF2E7D32) else Color(0xFF512DA8),
                                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                },
                                onClick = {
                                    fromUser = m
                                    expandedFrom = false
                                }
                            )
                        }
                    }
                }

                // Who is owed? (Creditor)
                ExposedDropdownMenuBox(
                    expanded = expandedTo,
                    onExpandedChange = { expandedTo = !expandedTo }
                ) {
                    val toTag = if (toUser.isExternalFriend) "Friend" else "Roommate"
                    OutlinedTextField(
                        value = "${toUser.name} ($toTag)",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Who gave money? (Creditor)") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedTo) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor(),
                        shape = RoundedCornerShape(12.dp)
                    )
                    ExposedDropdownMenu(
                        expanded = expandedTo,
                        onDismissRequest = { expandedTo = false }
                    ) {
                        members.forEach { m ->
                            val tag = if (m.isExternalFriend) "Friend" else "Roommate"
                            val isMe = m.id == currentUser?.id
                            DropdownMenuItem(
                                text = {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(if (isMe) "${m.name} (You)" else m.name)
                                        Spacer(Modifier.width(6.dp))
                                        Surface(
                                            shape = RoundedCornerShape(4.dp),
                                            color = if (m.isExternalFriend) Color(0xFFE8F5E9) else Color(0xFFEDE7F6)
                                        ) {
                                            Text(
                                                text = tag,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = if (m.isExternalFriend) Color(0xFF2E7D32) else Color(0xFF512DA8),
                                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                },
                                onClick = {
                                    toUser = m
                                    expandedTo = false
                                }
                            )
                        }
                    }
                }

                // Recipient UPI information
                if (!toUser.upiId.isNullOrBlank()) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Color(0xFFE8F5E9),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Verified,
                                contentDescription = null,
                                tint = Color(0xFF2E7D32),
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = "Lender UPI: ${toUser.upiId}",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFF2E7D32),
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }

                // Amount
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

                // Reason
                OutlinedTextField(
                    value = reason,
                    onValueChange = { reason = it },
                    label = { Text("Reason / Note") },
                    placeholder = { Text("e.g. Lunch loan, Movie tickets, Cash given") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )

                if (errorMessage != null) {
                    Text(
                        text = errorMessage ?: "",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val amount = amountText.toDoubleOrNull() ?: 0.0
                    if (amount <= 0.0) {
                        errorMessage = "Please enter a valid amount greater than ₹0."
                        return@Button
                    }
                    if (fromUser.id == toUser.id) {
                        errorMessage = "Borrower and Lender cannot be the same person."
                        return@Button
                    }
                    val finalReason = reason.trim().ifBlank { "Direct loan / settlement" }
                    onConfirm(fromUser, toUser, amount, finalReason)
                },
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Save Debt")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )

    if (showInlineAddFriend && onAddPerson != null) {
        AddExternalFriendDialog(
            onDismiss = { showInlineAddFriend = false },
            onAddPerson = onAddPerson,
            onSearchUserInCloud = onSearchUserInCloud
        )
    }
}

@Composable
fun AddExternalFriendDialog(
    onDismiss: () -> Unit,
    onAddPerson: (name: String, email: String, upiId: String, isExternalFriend: Boolean, colorHex: String, onComplete: ((Boolean, String) -> Unit)?) -> Unit,
    onSearchUserInCloud: ((query: String, onResult: (List<UserProfile>) -> Unit) -> Unit)? = null
) {
    var name by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var upiId by remember { mutableStateOf("") }
    var isSearching by remember { mutableStateOf(false) }
    var searchStatusMessage by remember { mutableStateOf<String?>(null) }
    var matchedUser by remember { mutableStateOf<UserProfile?>(null) }
    var isSaving by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = { if (!isSaving) onDismiss() },
        title = {
            Text("Add External Friend", fontWeight = FontWeight.Bold)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Add friends outside your household for giving and receiving money. Search with their Gmail ID to link their account if already registered, or save personally for your transaction records.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // Gmail ID input with Search Button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    OutlinedTextField(
                        value = email,
                        onValueChange = {
                            email = it
                            matchedUser = null
                            searchStatusMessage = null
                            errorMessage = null
                        },
                        label = { Text("Friend Gmail ID") },
                        placeholder = { Text("friend@gmail.com") },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email)
                    )

                    if (onSearchUserInCloud != null) {
                        FilledTonalButton(
                            onClick = {
                                val query = email.trim()
                                if (query.isNotBlank()) {
                                    isSearching = true
                                    searchStatusMessage = null
                                    matchedUser = null
                                    onSearchUserInCloud(query) { results ->
                                        isSearching = false
                                        val match = results.firstOrNull()
                                        if (match != null) {
                                            matchedUser = match
                                            if (name.isBlank()) name = match.name
                                            if (upiId.isBlank() && !match.upiId.isNullOrBlank()) {
                                                upiId = match.upiId.orEmpty()
                                            }
                                            searchStatusMessage = "Found registered account: ${match.name}"
                                        } else {
                                            searchStatusMessage = "No registered account found yet. Will be saved personally for your transactions."
                                        }
                                    }
                                }
                            },
                            enabled = !isSearching && email.isNotBlank(),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            if (isSearching) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                            } else {
                                Icon(Icons.Default.Search, contentDescription = "Search", modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                }

                // Cloud search status banner
                searchStatusMessage?.let { status ->
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = if (matchedUser != null) Color(0xFFE8F5E9) else MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (matchedUser != null) {
                                Icon(
                                    imageVector = Icons.Default.Verified,
                                    contentDescription = null,
                                    tint = Color(0xFF2E7D32),
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(Modifier.width(6.dp))
                            }
                            Text(
                                text = status,
                                style = MaterialTheme.typography.bodySmall,
                                color = if (matchedUser != null) Color(0xFF2E7D32) else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                // Friend Name
                OutlinedTextField(
                    value = name,
                    onValueChange = {
                        name = it
                        errorMessage = null
                    },
                    label = { Text("Friend Name *") },
                    placeholder = { Text("e.g. Rahul, Sneha") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true
                )

                // UPI ID
                OutlinedTextField(
                    value = upiId,
                    onValueChange = { upiId = it },
                    label = { Text("UPI ID (Optional)") },
                    placeholder = { Text("e.g. friend@okhdfcbank") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true
                )

                if (errorMessage != null) {
                    Text(
                        text = errorMessage ?: "",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val trimmedName = name.trim()
                    if (trimmedName.isBlank()) {
                        errorMessage = "Please enter the friend's name."
                        return@Button
                    }
                    isSaving = true
                    onAddPerson(
                        trimmedName,
                        email.trim(),
                        upiId.trim(),
                        true, // isExternalFriend
                        "#4CAF50"
                    ) { success, msg ->
                        isSaving = false
                        if (success) {
                            onDismiss()
                        } else {
                            errorMessage = msg
                        }
                    }
                },
                enabled = !isSaving && name.isNotBlank(),
                shape = RoundedCornerShape(12.dp)
            ) {
                if (isSaving) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp)
                } else {
                    Text("Save Friend")
                }
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                enabled = !isSaving
            ) {
                Text("Cancel")
            }
        }
    )
}
