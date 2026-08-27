package com.example.data.local.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "settlement_debts")
data class SettlementDebt(
    @PrimaryKey val id: String,
    val fromUserId: String, // Person who owes money
    val fromUserName: String,
    val toUserId: String, // Person who lent money / should receive money
    val toUserName: String,
    val toUserUpiId: String = "", // UPI ID of recipient (e.g., friend@okhdfcbank)
    val amount: Double,
    val reason: String = "Expense Split",
    val status: String = "PENDING", // PENDING, PAID_PENDING_CONFIRMATION, VERIFIED
    val transactionRef: String = "",
    val householdId: String,
    val createdDateMillis: Long = System.currentTimeMillis(),
    val settledDateMillis: Long = 0L,
    val isSynced: Boolean = false
)
