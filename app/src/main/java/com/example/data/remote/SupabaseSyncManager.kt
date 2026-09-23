package com.example.data.remote

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.example.data.local.dao.RoomieDao
import com.example.data.local.model.SyncQueueItem
import com.example.util.NetworkConnectivityObserver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class SyncStatus {
    IDLE,
    SYNCING,
    OFFLINE,
    ERROR
}

class SupabaseSyncManager(private val context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("supabase_sync_prefs", Context.MODE_PRIVATE)

    val authManager = SupabaseAuthManager(context)
    val dataStore = SupabaseDataStore(context, authManager)
    val connectivityObserver = NetworkConnectivityObserver(context)

    private val syncScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _syncStatus = MutableStateFlow(SyncStatus.IDLE)
    val syncStatus: StateFlow<SyncStatus> = _syncStatus.asStateFlow()

    private val _lastSyncMessage = MutableStateFlow("Initialized")
    val lastSyncMessage: StateFlow<String> = _lastSyncMessage.asStateFlow()

    var supabaseUrl: String
        get() = prefs.getString("supabase_url", "https://gxpxbnrehrawxwgzdqqm.supabase.co")
            ?: "https://gxpxbnrehrawxwgzdqqm.supabase.co"
        set(value) = prefs.edit().putString("supabase_url", value.trim()).apply()

    var supabaseAnonKey: String
        get() = prefs.getString("supabase_anon_key", "sb_publishable_pZIu3QSAgoZMWBjVkqCHFw_zOIRng-F")
            ?: "sb_publishable_pZIu3QSAgoZMWBjVkqCHFw_zOIRng-F"
        set(value) = prefs.edit().putString("supabase_anon_key", value.trim()).apply()

    var isSyncEnabled: Boolean
        get() = prefs.getBoolean("supabase_sync_enabled", true) && supabaseUrl.isNotBlank()
        set(value) = prefs.edit().putBoolean("supabase_sync_enabled", value).apply()

    var lastSyncTimestamp: Long
        get() = prefs.getLong("last_sync_timestamp", 0L)
        set(value) = prefs.edit().putLong("last_sync_timestamp", value).apply()

    fun isOnline(): Boolean = connectivityObserver.isCurrentlyOnline()

    /**
     * Start background network watcher. Whenever device goes online, automatically
     * flush pending mutations and pull cloud updates.
     */
    fun startAutoSyncWatcher(dao: RoomieDao, getHouseholdId: () -> String?) {
        syncScope.launch {
            connectivityObserver.isOnline.collect { online ->
                if (online) {
                    Log.d("SupabaseSyncManager", "Device connected to internet. Triggering auto-sync.")
                    val householdId = getHouseholdId()
                    performFullSync(dao, householdId.orEmpty())
                } else {
                    Log.d("SupabaseSyncManager", "Device offline. Changes will queue locally.")
                    _syncStatus.value = SyncStatus.OFFLINE
                    _lastSyncMessage.value = "Offline Mode (Local SQLite Active)"
                }
            }
        }
    }

    /**
     * Flush all pending mutations recorded while offline or between sync intervals.
     */
    suspend fun processPendingQueue(dao: RoomieDao): Int = withContext(Dispatchers.IO) {
        if (!isSyncEnabled || supabaseUrl.isBlank() || supabaseAnonKey.isBlank()) return@withContext 0
        if (!isOnline()) {
            _syncStatus.value = SyncStatus.OFFLINE
            return@withContext 0
        }

        val items = dao.getAllPendingSyncItems()
        if (items.isEmpty()) return@withContext 0

        var successfulCount = 0
        for (item in items) {
            val table = mapEntityTypeToTable(item.entityType)
            if (table.isBlank()) {
                dao.deleteSyncQueueItem(item.id)
                continue
            }

            val success = when (item.action.uppercase()) {
                "DELETE" -> dataStore.deleteRecord(supabaseUrl, supabaseAnonKey, table, item.entityId)
                else -> dataStore.upsertRecord(supabaseUrl, supabaseAnonKey, table, item.payloadJson)
            }

            if (success) {
                dao.deleteSyncQueueItem(item.id)
                successfulCount++
            } else {
                // If a record fails due to network break, stop processing remaining queue
                Log.w("SupabaseSyncManager", "Failed to sync queue item ${item.id} to $table. Stopping batch.")
                break
            }
        }
        successfulCount
    }

    /**
     * Complete two-way synchronization:
     * 1. Push all pending queued mutations to Supabase.
     * 2. Pull all remote records from Supabase into local Room DB.
     */
    suspend fun performFullSync(dao: RoomieDao, householdId: String): Boolean = withContext(Dispatchers.IO) {
        if (!isSyncEnabled || supabaseUrl.isBlank() || supabaseAnonKey.isBlank()) return@withContext false
        if (!isOnline()) {
            _syncStatus.value = SyncStatus.OFFLINE
            _lastSyncMessage.value = "Offline (Local data saved)"
            return@withContext false
        }

        try {
            _syncStatus.value = SyncStatus.SYNCING
            _lastSyncMessage.value = "Synchronizing with Supabase..."

            // Step 1: Process and flush offline mutation queue
            val flushed = processPendingQueue(dao)

            // Step 2: Push any local entities if queue was empty or for initial sync
            if (householdId.isNotBlank()) {
                // Step 3: Pull cloud updates from roommates
                val pullResult = dataStore.pullAllDataFromSupabase(supabaseUrl, supabaseAnonKey, householdId, dao)
                if (pullResult.isSuccess) {
                    lastSyncTimestamp = System.currentTimeMillis()
                    _syncStatus.value = SyncStatus.IDLE
                    _lastSyncMessage.value = "All synced ($flushed pushed, ${pullResult.getOrNull()} pulled)"
                    return@withContext true
                } else {
                    _syncStatus.value = SyncStatus.IDLE
                    _lastSyncMessage.value = "Push complete ($flushed items)"
                    return@withContext true
                }
            }

            _syncStatus.value = SyncStatus.IDLE
            _lastSyncMessage.value = "Synced with cloud"
            true
        } catch (e: Exception) {
            Log.e("SupabaseSyncManager", "Sync error", e)
            _syncStatus.value = SyncStatus.ERROR
            _lastSyncMessage.value = "Sync error: ${e.localizedMessage}"
            false
        }
    }

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

    private fun mapEntityTypeToTable(entityType: String): String {
        return when (entityType.uppercase()) {
            "HOUSEHOLD" -> "households"
            "USER_PROFILE" -> "user_profiles"
            "EXPENSE" -> "expenses"
            "CHORE" -> "chore_tasks"
            "DEBT" -> "settlement_debts"
            "SAVINGS_GOAL" -> "savings_goals"
            "BUDGET_CONFIG" -> "budget_configs"
            "NOTIFICATION" -> "household_notifications"
            else -> ""
        }
    }
}
