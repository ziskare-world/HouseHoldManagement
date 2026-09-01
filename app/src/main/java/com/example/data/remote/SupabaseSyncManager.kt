package com.example.data.remote

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.example.data.local.dao.RoomieDao
import com.example.data.local.model.BudgetConfig
import com.example.data.local.model.ChoreTask
import com.example.data.local.model.ExpenseItem
import com.example.data.local.model.Household
import com.example.data.local.model.SavingsGoal
import com.example.data.local.model.SettlementDebt
import com.example.data.local.model.UserProfile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class SupabaseSyncManager(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("supabase_sync_prefs", Context.MODE_PRIVATE)

    val authManager = SupabaseAuthManager(context)
    val dataStore = SupabaseDataStore(context, authManager)

    var supabaseUrl: String
        get() = prefs.getString("supabase_url", "https://gxpxbnrehrawxwgzdqqm.supabase.co") ?: "https://gxpxbnrehrawxwgzdqqm.supabase.co"
        set(value) = prefs.edit().putString("supabase_url", value.trim()).apply()

    var supabaseAnonKey: String
        get() {
            val key = prefs.getString("supabase_anon_key", "sb_publishable_pZIu3QSAgoZMWBjVkqCHFw_zOIRng-F") ?: "sb_publishable_pZIu3QSAgoZMWBjVkqCHFw_zOIRng-F"
            return if (key == "sb-anon-key-placeholder" || key.isBlank()) "sb_publishable_pZIu3QSAgoZMWBjVkqCHFw_zOIRng-F" else key
        }
        set(value) = prefs.edit().putString("supabase_anon_key", value.trim()).apply()

    var isSyncEnabled: Boolean
        get() = prefs.getBoolean("supabase_sync_enabled", true)
        set(value) = prefs.edit().putBoolean("supabase_sync_enabled", value).apply()

    var lastSyncTimestamp: Long
        get() = prefs.getLong("last_sync_timestamp", 0L)
        set(value) = prefs.edit().putLong("last_sync_timestamp", value).apply()

    /**
     * Push all local Room Database entities to Supabase Cloud Data Store
     */
    suspend fun pushAllToCloud(dao: RoomieDao, householdId: String): Boolean = withContext(Dispatchers.IO) {
        if (!isSyncEnabled || supabaseUrl.isBlank() || supabaseAnonKey.isBlank()) {
            return@withContext false
        }
        try {
            // 1. Household
            val household = dao.getHouseholdDirect(householdId)
            if (household != null) {
                dataStore.pushHousehold(supabaseUrl, supabaseAnonKey, household)
            }

            // 2. Roommates / User Profiles
            val members = dao.getHouseholdMembersDirect(householdId)
            members.forEach { dataStore.pushUserProfile(supabaseUrl, supabaseAnonKey, it) }

            // 3. Expenses
            val currentUserId = dao.getCurrentUserDirect()?.id ?: ""
            val expenses = dao.getExpensesByMonthDirect(householdId, com.example.util.DateUtils.getCurrentMonthYearKey(), currentUserId)
            expenses.forEach { dataStore.pushExpense(supabaseUrl, supabaseAnonKey, it) }

            // 4. Chores
            val chores = dao.getAllChoresDirect(householdId)
            chores.forEach { dataStore.pushChore(supabaseUrl, supabaseAnonKey, it) }

            // 5. Debts
            val debts = dao.getSettlementDebtsDirect(householdId)
            debts.forEach { dataStore.pushDebt(supabaseUrl, supabaseAnonKey, it) }

            // 6. Savings Goals
            val savings = dao.getSavingsGoalsDirect(householdId)
            savings.forEach { dataStore.pushSavingsGoal(supabaseUrl, supabaseAnonKey, it) }

            // 7. Budget Config
            val budget = dao.getBudgetConfigDirect(com.example.util.DateUtils.getCurrentMonthYearKey(), householdId)
            if (budget != null) {
                dataStore.pushBudgetConfig(supabaseUrl, supabaseAnonKey, budget)
            }

            lastSyncTimestamp = System.currentTimeMillis()
            true
        } catch (e: Exception) {
            Log.e("SupabaseSyncManager", "Push all failed", e)
            false
        }
    }

    /**
     * Pull remote records from Supabase Data Store into local Room DB
     */
    suspend fun pullFromCloud(dao: RoomieDao, householdId: String): Result<Int> = withContext(Dispatchers.IO) {
        if (!isSyncEnabled || supabaseUrl.isBlank() || supabaseAnonKey.isBlank()) {
            return@withContext Result.failure(Exception("Supabase sync disabled or not configured"))
        }
        val res = dataStore.pullAllDataFromSupabase(supabaseUrl, supabaseAnonKey, householdId, dao)
        if (res.isSuccess) {
            lastSyncTimestamp = System.currentTimeMillis()
        }
        res
    }

    /**
     * Ping Supabase connection
     */
    suspend fun testConnection(): Pair<Boolean, String> {
        return dataStore.pingConnection(supabaseUrl, supabaseAnonKey)
    }
}
