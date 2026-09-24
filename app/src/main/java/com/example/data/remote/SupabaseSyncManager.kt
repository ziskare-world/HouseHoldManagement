package com.example.data.remote

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.example.data.local.dao.RoomieDao
import com.example.data.local.model.*
import com.example.notification.ChoreNotificationHelper
import com.example.util.NetworkConnectivityObserver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class SyncStatus {
    IDLE,
    SYNCING,
    OFFLINE,
    ERROR
}

data class SyncReport(
    val success: Boolean,
    val uploadedCount: Int,
    val downloadedCount: Int,
    val summaryMessage: String,
    val errorMessage: String? = null
)

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

    fun getTodayDateKey(): String {
        return SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
    }

    /**
     * Checks if the scheduled cloud sync should run.
     * Returns true ONLY ONCE per day on the first morning/initial open of the app.
     */
    fun shouldRunDailySync(): Boolean {
        if (!isSyncEnabled || supabaseUrl.isBlank() || supabaseAnonKey.isBlank()) return false
        val lastDate = prefs.getString("last_daily_sync_date", "")
        return lastDate != getTodayDateKey()
    }

    /**
     * Records that today's scheduled daily cloud sync has completed.
     */
    fun markDailySyncCompleted() {
        prefs.edit().putString("last_daily_sync_date", getTodayDateKey()).apply()
    }

    /**
     * Start background network watcher. Whenever device goes online, flushes
     * pending offline mutations silently. Full cloud reconciliation runs ONLY ONCE
     * per day on the first morning launch, not every time the app opens.
     */
    fun startAutoSyncWatcher(dao: RoomieDao, getHouseholdId: suspend () -> String?) {
        syncScope.launch {
            connectivityObserver.isOnline.collect { online ->
                if (online) {
                    // Flush pending mutations recorded while offline
                    processPendingQueue(dao)

                    // Scheduled cloud sync: only run once per day on first morning open
                    if (shouldRunDailySync()) {
                        Log.d("SupabaseSyncManager", "First morning launch of the day detected. Running scheduled daily cloud sync.")
                        markDailySyncCompleted()
                        val householdId = getHouseholdId()
                        reconcileAndSync(dao, householdId.orEmpty())
                    }
                } else {
                    Log.d("SupabaseSyncManager", "Device offline. Changes will queue locally.")
                    _syncStatus.value = SyncStatus.OFFLINE
                    _lastSyncMessage.value = "Offline Mode (Local SQLite Active)"
                }
            }
        }
    }

    /**
     * Ensures parent Household and User Profile exist in Supabase first.
     * Prevents foreign key constraint violations (HTTP 409) when inserting child tables.
     */
    suspend fun ensureHouseholdAndUserSynced(dao: RoomieDao, householdId: String): Boolean {
        if (!isSyncEnabled || supabaseUrl.isBlank() || supabaseAnonKey.isBlank()) return false
        val hid = householdId.ifBlank { dao.getCurrentUserDirect()?.householdId.orEmpty() }
        if (hid.isBlank()) return false

        // 1. Ensure Household
        val localHousehold = dao.getHouseholdDirect(hid)
        if (localHousehold != null) {
            val hSuccess = dataStore.pushHousehold(supabaseUrl, supabaseAnonKey, localHousehold)
            if (!hSuccess) {
                Log.w("SupabaseSyncManager", "Failed to sync household $hid to Supabase")
            }
        }

        // 2. Ensure Members & User Profiles
        val members = dao.getHouseholdMembersDirect(hid)
        for (member in members) {
            dataStore.pushUserProfile(supabaseUrl, supabaseAnonKey, member)
        }
        val currentUser = dao.getCurrentUserDirect()
        if (currentUser != null && members.none { it.id == currentUser.id }) {
            dataStore.pushUserProfile(supabaseUrl, supabaseAnonKey, currentUser)
        }
        return true
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

        // Always ensure parent household & user profile exist first
        val curUser = dao.getCurrentUserDirect()
        if (curUser != null && curUser.householdId.isNotBlank()) {
            ensureHouseholdAndUserSynced(dao, curUser.householdId)
        }

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
                Log.w("SupabaseSyncManager", "Failed to sync queue item ${item.id} to $table.")
                if (!isOnline()) break
            }
        }
        successfulCount
    }

    /**
     * Comprehensive Two-Way Reconciler:
     * 1. Checks whether data available locally and in Supabase match.
     * 2. If not matched, uploads missing local data and downloads missing Supabase data.
     * 3. Displays live progress and updates Android notification & in-app status.
     */
    suspend fun reconcileAndSync(
        dao: RoomieDao,
        householdId: String,
        onProgress: (stage: String, percent: Int) -> Unit = { _, _ -> }
    ): SyncReport = withContext(Dispatchers.IO) {
        if (!isSyncEnabled || supabaseUrl.isBlank() || supabaseAnonKey.isBlank()) {
            val msg = "Supabase cloud sync is not configured or disabled"
            _syncStatus.value = SyncStatus.ERROR
            _lastSyncMessage.value = msg
            ChoreNotificationHelper.showSyncComplete(context, "Sync Configuration Required", msg)
            return@withContext SyncReport(false, 0, 0, msg, msg)
        }

        if (!isOnline()) {
            val msg = "Offline: Changes saved locally in Room database"
            _syncStatus.value = SyncStatus.OFFLINE
            _lastSyncMessage.value = msg
            ChoreNotificationHelper.showSyncComplete(context, "Offline Mode", msg)
            return@withContext SyncReport(false, 0, 0, msg, "Device is offline")
        }

        val hid = householdId.ifBlank { dao.getCurrentUserDirect()?.householdId.orEmpty() }
        if (hid.isBlank()) {
            val msg = "No active household found to sync"
            _syncStatus.value = SyncStatus.ERROR
            _lastSyncMessage.value = msg
            ChoreNotificationHelper.showSyncComplete(context, "Sync Error", msg)
            return@withContext SyncReport(false, 0, 0, msg, msg)
        }

        _syncStatus.value = SyncStatus.SYNCING
        var totalUploaded = 0
        var totalDownloaded = 0

        try {
            // Stage 1: Check Connection & Detect Household Assignment (10%)
            onProgress("Connecting to Supabase...", 10)
            ChoreNotificationHelper.showSyncProgress(context, "RoomieVault Cloud Sync", "Connecting to Supabase...", 10, 100)

            var effectiveHid = hid
            val curUser = dao.getCurrentUserDirect()
            if (curUser != null && curUser.email.isNotBlank()) {
                val remoteProfile = dataStore.findUserProfileByEmail(supabaseUrl, supabaseAnonKey, curUser.email)
                if (remoteProfile != null && remoteProfile.householdId.isNotBlank() && remoteProfile.householdId != curUser.householdId) {
                    Log.i("SupabaseSyncManager", "Current user was assigned to new household in cloud: ${remoteProfile.householdId} (previously ${curUser.householdId})")
                    effectiveHid = remoteProfile.householdId
                    val updatedCur = curUser.copy(
                        householdId = remoteProfile.householdId,
                        householdName = remoteProfile.householdName
                    )
                    dao.insertUser(updatedCur)

                    val remoteH = dataStore.fetchRemoteHousehold(supabaseUrl, supabaseAnonKey, effectiveHid)
                    if (remoteH != null) {
                        dao.insertHousehold(remoteH)
                    } else {
                        dao.insertHousehold(
                            Household(
                                id = effectiveHid,
                                name = remoteProfile.householdName.ifBlank { "Household" },
                                inviteCode = effectiveHid.removePrefix("HOUSE_"),
                                createdByUserId = curUser.id
                            )
                        )
                    }
                }
            }

            ensureHouseholdAndUserSynced(dao, effectiveHid)
            processPendingQueue(dao)

            // Stage 1.5: Compare & Reconcile Roommates / User Profiles (20%)
            onProgress("Checking & synchronizing roommates...", 20)
            ChoreNotificationHelper.showSyncProgress(context, "RoomieVault Cloud Sync", "Synchronizing roommates...", 20, 100)

            val localMembers = dao.getHouseholdMembersDirect(effectiveHid)
            val remoteMembers = dataStore.fetchRemoteUserProfiles(supabaseUrl, supabaseAnonKey, effectiveHid)

            val localMemberMap = localMembers.associateBy { it.id }
            val remoteMemberMap = remoteMembers.associateBy { it.id }
            val currentUserId = curUser?.id.orEmpty()
            val currentUserEmail = curUser?.email.orEmpty().trim().lowercase()

            // Upload local household members missing in Supabase
            for (localMem in localMembers) {
                if (localMem.id !in remoteMemberMap) {
                    val ok = dataStore.pushUserProfile(supabaseUrl, supabaseAnonKey, localMem)
                    if (ok) totalUploaded++
                }
            }

            // Download remote roommates missing locally or update existing
            for (remoteMem in remoteMembers) {
                val isSelf = (remoteMem.id == currentUserId) ||
                        (currentUserEmail.isNotBlank() && remoteMem.email.trim().lowercase() == currentUserEmail)

                if (isSelf) {
                    if (curUser != null && (curUser.name != remoteMem.name || curUser.upiId != remoteMem.upiId)) {
                        dao.insertUser(curUser.copy(name = remoteMem.name, upiId = remoteMem.upiId))
                    }
                } else {
                    val existing = localMemberMap[remoteMem.id]
                    if (existing == null) {
                        dao.insertUser(remoteMem.copy(isCurrentUser = false))
                        totalDownloaded++
                    } else if (existing.name != remoteMem.name || existing.upiId != remoteMem.upiId || existing.avatarColorHex != remoteMem.avatarColorHex) {
                        dao.insertUser(remoteMem.copy(isCurrentUser = false))
                    }
                }
            }

            // Stage 2: Compare & Reconcile Expenses (35%)
            onProgress("Checking & synchronizing expenses...", 35)
            ChoreNotificationHelper.showSyncProgress(context, "RoomieVault Cloud Sync", "Matching expenses...", 35, 100)

            val localExpenses = dao.getExpensesByHouseholdDirect(effectiveHid)
            val remoteExpenses = dataStore.fetchRemoteExpenses(supabaseUrl, supabaseAnonKey, effectiveHid)

            val localExpMap = localExpenses.associateBy { it.id }
            val remoteExpMap = remoteExpenses.associateBy { it.id }

            // Upload local expenses missing in Supabase
            val expensesToUpload = localExpenses.filter { it.id !in remoteExpMap }
            for (exp in expensesToUpload) {
                ChoreNotificationHelper.showSyncProgress(context, "RoomieVault Cloud Sync", "Uploading expense: ${exp.title}...", 35, 100)
                val ok = dataStore.pushExpense(supabaseUrl, supabaseAnonKey, exp)
                if (ok) totalUploaded++
            }

            // Download Supabase expenses missing locally
            val curUserId = dao.getCurrentUserDirect()?.id.orEmpty()
            val expensesToDownload = remoteExpenses.filter { remoteExp ->
                remoteExp.id !in localExpMap && (remoteExp.splitType != "PERSONAL" || curUserId.isBlank() || remoteExp.paidByUserId == curUserId)
            }
            if (expensesToDownload.isNotEmpty()) {
                ChoreNotificationHelper.showSyncProgress(context, "RoomieVault Cloud Sync", "Downloading ${expensesToDownload.size} remote expenses...", 45, 100)
                dao.insertExpenses(expensesToDownload)
                totalDownloaded += expensesToDownload.size
            }

            // Stage 3: Compare & Reconcile Chores (55%)
            onProgress("Checking & synchronizing chores...", 55)
            ChoreNotificationHelper.showSyncProgress(context, "RoomieVault Cloud Sync", "Matching chore tasks...", 55, 100)

            val localChores = dao.getAllChoresDirect(effectiveHid)
            val remoteChores = dataStore.fetchRemoteChores(supabaseUrl, supabaseAnonKey, effectiveHid)

            val localChoreMap = localChores.associateBy { it.id }
            val remoteChoreMap = remoteChores.associateBy { it.id }

            val choresToUpload = localChores.filter { it.id !in remoteChoreMap }
            for (chore in choresToUpload) {
                val ok = dataStore.pushChore(supabaseUrl, supabaseAnonKey, chore)
                if (ok) totalUploaded++
            }

            val choresToDownload = remoteChores.filter { it.id !in localChoreMap }
            if (choresToDownload.isNotEmpty()) {
                dao.insertChores(choresToDownload)
                totalDownloaded += choresToDownload.size
            }

            // Stage 4: Compare & Reconcile Settlement Debts (70%)
            onProgress("Checking & synchronizing settlement debts...", 70)
            ChoreNotificationHelper.showSyncProgress(context, "RoomieVault Cloud Sync", "Matching debts & UPI settlements...", 70, 100)

            val localDebts = dao.getSettlementDebtsDirect(effectiveHid)
            val remoteDebts = dataStore.fetchRemoteDebts(supabaseUrl, supabaseAnonKey, effectiveHid)

            val localDebtMap = localDebts.associateBy { it.id }
            val remoteDebtMap = remoteDebts.associateBy { it.id }

            val debtsToUpload = localDebts.filter { it.id !in remoteDebtMap }
            for (debt in debtsToUpload) {
                val ok = dataStore.pushDebt(supabaseUrl, supabaseAnonKey, debt)
                if (ok) totalUploaded++
            }

            val debtsToDownload = remoteDebts.filter { it.id !in localDebtMap }
            if (debtsToDownload.isNotEmpty()) {
                dao.insertDebts(debtsToDownload)
                totalDownloaded += debtsToDownload.size
            }

            // Stage 5: Compare & Reconcile Savings Goals (85%)
            onProgress("Checking & synchronizing savings goals...", 85)
            val localSavings = dao.getSavingsGoalsDirect(effectiveHid)
            val remoteSavings = dataStore.fetchRemoteSavingsGoals(supabaseUrl, supabaseAnonKey, effectiveHid)

            val localSavingsMap = localSavings.associateBy { it.id }
            val remoteSavingsMap = remoteSavings.associateBy { it.id }

            for (goal in localSavings.filter { it.id !in remoteSavingsMap }) {
                val ok = dataStore.pushSavingsGoal(supabaseUrl, supabaseAnonKey, goal)
                if (ok) totalUploaded++
            }
            for (goal in remoteSavings.filter { it.id !in localSavingsMap }) {
                dao.insertSavingsGoal(goal)
                totalDownloaded++
            }

            // Stage 6: Compare & Reconcile Budget Configs (95%)
            onProgress("Checking & synchronizing monthly budgets...", 95)
            val localBudgets = dao.getAllBudgetConfigsDirect(effectiveHid)
            val remoteBudgets = dataStore.fetchRemoteBudgetConfigs(supabaseUrl, supabaseAnonKey, effectiveHid)

            val localBudgetMap = localBudgets.associateBy { "${it.monthYearKey}_${it.householdId}" }
            val remoteBudgetMap = remoteBudgets.associateBy { "${it.monthYearKey}_${it.householdId}" }

            for (b in localBudgets.filter { "${it.monthYearKey}_${it.householdId}" !in remoteBudgetMap }) {
                val ok = dataStore.pushBudgetConfig(supabaseUrl, supabaseAnonKey, b)
                if (ok) totalUploaded++
            }
            for (b in remoteBudgets.filter { "${it.monthYearKey}_${it.householdId}" !in localBudgetMap }) {
                dao.insertBudgetConfig(b)
                totalDownloaded++
            }

            // Completion (100%)
            lastSyncTimestamp = System.currentTimeMillis()
            markDailySyncCompleted()
            _syncStatus.value = SyncStatus.IDLE

            val summary = if (totalUploaded == 0 && totalDownloaded == 0) {
                "All data in sync! Local and Supabase match."
            } else {
                "Sync complete: $totalUploaded uploaded, $totalDownloaded downloaded"
            }

            _lastSyncMessage.value = summary
            onProgress(summary, 100)
            ChoreNotificationHelper.showSyncComplete(context, "RoomieVault Sync Complete", summary)

            SyncReport(
                success = true,
                uploadedCount = totalUploaded,
                downloadedCount = totalDownloaded,
                summaryMessage = summary
            )
        } catch (e: Exception) {
            Log.e("SupabaseSyncManager", "Reconciliation sync error", e)
            val errMsg = "Sync error: ${e.localizedMessage ?: "Unknown error"}"
            _syncStatus.value = SyncStatus.ERROR
            _lastSyncMessage.value = errMsg
            onProgress(errMsg, 100)
            ChoreNotificationHelper.showSyncComplete(context, "RoomieVault Sync Error", errMsg)
            SyncReport(
                success = false,
                uploadedCount = totalUploaded,
                downloadedCount = totalDownloaded,
                summaryMessage = errMsg,
                errorMessage = e.localizedMessage
            )
        }
    }

    suspend fun performFullSync(dao: RoomieDao, householdId: String): Boolean = withContext(Dispatchers.IO) {
        val report = reconcileAndSync(dao, householdId)
        report.success
    }

    suspend fun pushAllToCloud(dao: RoomieDao, householdId: String): Boolean = withContext(Dispatchers.IO) {
        val report = reconcileAndSync(dao, householdId)
        report.success
    }

    suspend fun pullFromCloud(dao: RoomieDao, householdId: String): Result<Int> = withContext(Dispatchers.IO) {
        val report = reconcileAndSync(dao, householdId)
        if (report.success) {
            Result.success(report.downloadedCount)
        } else {
            Result.failure(Exception(report.errorMessage ?: report.summaryMessage))
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
