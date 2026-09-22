package com.example.data.remote

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.example.data.local.dao.RoomieDao
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class SupabaseSyncManager(private val context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("supabase_sync_prefs", Context.MODE_PRIVATE)

    val authManager = SupabaseAuthManager(context)
    val dataStore = SupabaseDataStore(context, authManager)

    var supabaseUrl: String
        get() = prefs.getString("supabase_url", "https://gxpxbnrehrawxwgzdqqm.supabase.co")
            ?: "https://gxpxbnrehrawxwgzdqqm.supabase.co"
        set(value) = prefs.edit().putString("supabase_url", value.trim()).apply()

    var supabaseAnonKey: String
        get() {
            val key = prefs.getString("supabase_anon_key", "sb_publishable_pZIu3QSAgoZMWBjVkqCHFw_zOIRng-F")
                ?: "sb_publishable_pZIu3QSAgoZMWBjVkqCHFw_zOIRng-F"
            return if (key == "sb-anon-key-placeholder" || key.isBlank()) {
                "sb_publishable_pZIu3QSAgoZMWBjVkqCHFw_zOIRng-F"
            } else key
        }
        set(value) = prefs.edit().putString("supabase_anon_key", value.trim()).apply()

    var isSyncEnabled: Boolean
        get() = prefs.getBoolean("supabase_sync_enabled", true)
        set(value) = prefs.edit().putBoolean("supabase_sync_enabled", value).apply()

    var lastSyncTimestamp: Long
        get() = prefs.getLong("last_sync_timestamp", 0L)
        set(value) = prefs.edit().putLong("last_sync_timestamp", value).apply()

    suspend fun pushAllToCloud(dao: RoomieDao, householdId: String): Boolean = withContext(Dispatchers.IO) {
        if (!isSyncEnabled || supabaseUrl.isBlank() || supabaseAnonKey.isBlank()) return@withContext false
        try {
            dao.getHouseholdDirect(householdId)?.let { dataStore.pushHousehold(supabaseUrl, supabaseAnonKey, it) }
            dao.getHouseholdMembersDirect(householdId)
                .forEach { dataStore.pushUserProfile(supabaseUrl, supabaseAnonKey, it) }
            val currentUserId = dao.getCurrentUserDirect()?.id.orEmpty()
            dao.getExpensesByMonthDirect(householdId, com.example.util.DateUtils.getCurrentMonthYearKey(), currentUserId)
                .forEach { dataStore.pushExpense(supabaseUrl, supabaseAnonKey, it) }
            dao.getAllChoresDirect(householdId).forEach { dataStore.pushChore(supabaseUrl, supabaseAnonKey, it) }
            dao.getSettlementDebtsDirect(householdId).forEach { dataStore.pushDebt(supabaseUrl, supabaseAnonKey, it) }
            dao.getSavingsGoalsDirect(householdId).forEach { dataStore.pushSavingsGoal(supabaseUrl, supabaseAnonKey, it) }
            dao.getBudgetConfigDirect(com.example.util.DateUtils.getCurrentMonthYearKey(), householdId)
                ?.let { dataStore.pushBudgetConfig(supabaseUrl, supabaseAnonKey, it) }
            if (currentUserId.isNotBlank()) {
                dao.getNotificationsForUserDirect(householdId, currentUserId)
                    .forEach { dataStore.pushNotification(supabaseUrl, supabaseAnonKey, it) }
            }
            lastSyncTimestamp = System.currentTimeMillis()
            true
        } catch (e: Exception) {
            Log.e("SupabaseSyncManager", "Push all failed", e)
            false
        }
    }

    suspend fun pullFromCloud(dao: RoomieDao, householdId: String): Result<Int> = withContext(Dispatchers.IO) {
        if (!isSyncEnabled || supabaseUrl.isBlank() || supabaseAnonKey.isBlank()) {
            return@withContext Result.failure(Exception("Supabase sync disabled or not configured"))
        }
        dataStore.pullAllDataFromSupabase(supabaseUrl, supabaseAnonKey, householdId, dao).also {
            if (it.isSuccess) lastSyncTimestamp = System.currentTimeMillis()
        }
    }

    suspend fun testConnection(): Pair<Boolean, String> =
        dataStore.pingConnection(supabaseUrl, supabaseAnonKey)
}
