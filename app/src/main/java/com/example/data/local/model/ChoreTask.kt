package com.example.data.local.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "chore_tasks")
data class ChoreTask(
    @PrimaryKey val id: String,
    val title: String,
    val description: String = "",
    val category: String = "Cleaning", // Cleaning, Kitchen, Trash, Groceries, Maintenance
    val assignedToUserId: String,
    val assignedToUserName: String,
    val householdId: String,
    // Frequency: DAILY, WEEKLY_SUNDAY_ROTATION, WEEKLY_CUSTOM, MONTHLY, ONE_TIME
    val frequency: String = "DAILY",
    val rotationMemberIds: String = "", // Comma separated user IDs for rotating shifts
    val rotationIndex: Int = 0,
    val scheduledTime: String = "09:00 AM",
    val scheduledDayOfWeek: Int = 1, // 1 = Sunday, 2 = Monday, ... 7 = Saturday
    val scheduledDayOfMonth: Int = 1, // 1st of month
    val lastCompletedDate: String = "", // "YYYY-MM-DD"
    val status: String = "PENDING", // PENDING, COMPLETED, SKIPPED
    val points: Int = 10,
    val isSynced: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)
