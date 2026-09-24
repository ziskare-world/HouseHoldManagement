package com.example.data.local.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "user_profiles")
data class UserProfile(
    @PrimaryKey val id: String,
    val name: String,
    val email: String = "",
    val upiId: String = "", // e.g. alex@okaxis, john@upi
    val householdId: String = "HOUSE_DEFAULT",
    val householdName: String = "Greenfield Roomies",
    val avatarColorHex: String = "#0F5132",
    val isCurrentUser: Boolean = false,
    val isVirtual: Boolean = false, // Roommate added manually before they register
    val createdAt: Long = System.currentTimeMillis()
) {
    val isExternalFriend: Boolean
        get() = householdName == "EXTERNAL_FRIEND" || householdName.startsWith("FRIEND")
}
