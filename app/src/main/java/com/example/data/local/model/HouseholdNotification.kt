package com.example.data.local.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "household_notifications")
data class HouseholdNotification(
    @PrimaryKey val id: String,
    val householdId: String,
    val senderUserId: String,
    val senderUserName: String,
    val targetUserId: String,
    val targetUserName: String,
    // Notification Types: DEBT_REMINDER, CHORE_REMINDER, PAYMENT_PENDING_CONFIRMATION, PAYMENT_VERIFIED, EXPENSE_SPLIT_ADDED
    val type: String,
    val title: String,
    val message: String,
    val relatedEntityId: String = "", // debtId, choreId, expenseId
    val isRead: Boolean = false,
    val isSynced: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)
