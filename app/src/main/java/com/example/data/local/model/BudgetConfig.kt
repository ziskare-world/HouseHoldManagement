package com.example.data.local.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "budget_configs")
data class BudgetConfig(
    @PrimaryKey val monthYearKey: String, // e.g. "2026-08"
    val householdId: String,
    val totalBudgetLimit: Double = 0.0,
    val alertThresholdPercent: Int = 80, // Warning alert triggers at 80%
    val groceriesBudget: Double = 12000.0,
    val rentUtilitiesBudget: Double = 18000.0,
    val foodDiningBudget: Double = 6000.0,
    val miscellaneousBudget: Double = 4000.0,
    val updatedAt: Long = System.currentTimeMillis()
)
