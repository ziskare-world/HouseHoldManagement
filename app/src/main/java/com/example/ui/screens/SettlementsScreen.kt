package com.example.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
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
    modifier: Modifier = Modifier
) {
    val userId = currentUser?.id.orEmpty()
    val pending = debts.filter { it.status != "VERIFIED" }
    Column(modifier = modifier.fillMaxSize().padding(16.dp)) {
        Text("Giving Money & Splits", style = MaterialTheme.typography.headlineMedium)
        Text("Track household debts and payments", style = MaterialTheme.typography.bodySmall)
        Spacer(Modifier.height(16.dp))
        if (debts.isEmpty()) {
            Text("No settlement records yet.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(debts, key = { it.id }) { debt ->
                    val isOwedToUser = debt.toUserId == userId
                    Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors()) {
                        Column(Modifier.padding(16.dp)) {
                            Text(
                                if (debt.fromUserId == userId) "You owe ${debt.toUserName}"
                                else "${debt.fromUserName} owes you",
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(debt.reason, style = MaterialTheme.typography.bodySmall)
                            Text(DateUtils.formatCurrency(debt.amount), style = MaterialTheme.typography.titleLarge)
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                                if (debt.status != "VERIFIED" && debt.fromUserId == userId) {
                                    Button(onClick = { onPayViaUpi(debt, null) }) {
                                        Icon(Icons.Default.OpenInNew, contentDescription = null)
                                        Text("Pay")
                                    }
                                }
                                if (debt.status != "VERIFIED" && isOwedToUser) {
                                    IconButton(onClick = { onVerifyPayment(debt, true) }) {
                                        Icon(Icons.Default.CheckCircle, contentDescription = "Verify payment")
                                    }
                                }
                                IconButton(onClick = { onDeleteDebt(debt.id) }) {
                                    Icon(Icons.Default.Delete, contentDescription = "Delete debt")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
