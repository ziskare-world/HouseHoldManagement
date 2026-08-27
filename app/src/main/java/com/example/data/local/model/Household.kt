package com.example.data.local.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "households")
data class Household(
    @PrimaryKey val id: String,
    val name: String,
    val inviteCode: String,
    val createdByUserId: String,
    val currencySymbol: String = "₹",
    val monthlyBudgetLimit: Double = 35000.0,
    val budgetWarningThreshold: Int = 80, // Alert when expenses reach 80%
    val createdAt: Long = System.currentTimeMillis()
)
