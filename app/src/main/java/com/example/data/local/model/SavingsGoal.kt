package com.example.data.local.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "savings_goals")
data class SavingsGoal(
    @PrimaryKey val id: String,
    val title: String,
    val targetAmount: Double,
    val currentAmount: Double = 0.0,
    val targetMonthYear: String = "2026-12",
    val category: String = "Emergency Fund", // Emergency Fund, Vacation, Room Appliance, Security Deposit
    val householdId: String,
    val iconName: String = "Savings",
    val colorHex: String = "#10B981",
    val isSynced: Boolean = false,
    val updatedAt: Long = System.currentTimeMillis()
)
