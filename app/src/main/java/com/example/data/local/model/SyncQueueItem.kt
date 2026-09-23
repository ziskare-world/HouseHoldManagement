package com.example.data.local.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

/**
 * Represents a pending local change that must be synchronized with Supabase
 * when internet connectivity is active.
 */
@Entity(tableName = "sync_queue")
data class SyncQueueItem(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val entityType: String, // HOUSEHOLD, USER_PROFILE, EXPENSE, CHORE, DEBT, SAVINGS_GOAL, BUDGET_CONFIG, NOTIFICATION
    val entityId: String,
    val action: String,     // UPSERT, DELETE
    val payloadJson: String = "",
    val timestamp: Long = System.currentTimeMillis(),
    val retryCount: Int = 0
)
