package com.example.data.local.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "expenses")
data class ExpenseItem(
    @PrimaryKey val id: String,
    val title: String,
    val amount: Double,
    val category: String, // Groceries, Rent, Utilities, Food & Dining, Supplies, Other
    val dateMillis: Long = System.currentTimeMillis(),
    val monthYearKey: String, // e.g. "2026-08"
    val paidByUserId: String,
    val paidByName: String,
    val splitType: String = "EQUAL", // EQUAL, PERSONAL, CUSTOM
    val splitWithUserIds: String = "", // Comma separated user IDs
    val householdId: String,
    val notes: String = "",
    val isSettled: Boolean = false,
    val isSynced: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)
