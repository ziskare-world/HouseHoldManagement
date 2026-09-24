package com.example.data.repository

import android.content.Context
import com.example.data.local.AppDatabase
import com.example.data.local.dao.RoomieDao
import com.example.data.local.model.BudgetConfig
import com.example.data.local.model.ChoreTask
import com.example.data.local.model.ExpenseItem
import com.example.data.local.model.Household
import com.example.data.local.model.HouseholdNotification
import com.example.data.local.model.SavingsGoal
import com.example.data.local.model.SettlementDebt
import com.example.data.local.model.SyncQueueItem
import com.example.data.local.model.UserProfile
import com.example.data.local.model.toJson
import com.example.data.remote.SupabaseSyncManager
import com.example.data.remote.SupabaseUser
import com.example.data.remote.SyncReport
import com.example.data.remote.SyncStatus
import com.example.util.DateUtils
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

class RoomieRepository(
    val dao: RoomieDao,
    val syncManager: SupabaseSyncManager
) {
    private val backgroundScope = CoroutineScope(Dispatchers.IO)

    companion object {
        @Volatile
        private var INSTANCE: RoomieRepository? = null

        fun getInstance(context: Context): RoomieRepository {
            return INSTANCE ?: synchronized(this) {
                val db = AppDatabase.getInstance(context)
                val sync = SupabaseSyncManager(context)
                val repo = RoomieRepository(db.roomieDao(), sync)
                INSTANCE = repo
                sync.startAutoSyncWatcher(repo.dao) {
                    INSTANCE?.dao?.getCurrentUserDirect()?.householdId
                }
                repo
            }
        }
    }

    // --- User & Household Observables ---
    val currentUser: Flow<UserProfile?> = dao.getCurrentUser()

    fun getHouseholdMembers(householdId: String): Flow<List<UserProfile>> =
        dao.getHouseholdMembers(householdId)

    fun getHousehold(householdId: String): Flow<Household?> =
        dao.getHousehold(householdId)

    fun getAllExpenses(householdId: String, currentUserId: String): Flow<List<ExpenseItem>> =
        dao.getAllExpenses(householdId, currentUserId)

    fun getExpensesByMonth(householdId: String, monthKey: String, currentUserId: String): Flow<List<ExpenseItem>> =
        dao.getExpensesByMonth(householdId, monthKey, currentUserId)

    fun getAllChores(householdId: String): Flow<List<ChoreTask>> =
        dao.getAllChores(householdId)

    fun getSettlementDebts(householdId: String): Flow<List<SettlementDebt>> =
        dao.getSettlementDebts(householdId)

    fun getSavingsGoals(householdId: String): Flow<List<SavingsGoal>> =
        dao.getSavingsGoals(householdId)

    fun getBudgetConfig(monthKey: String, householdId: String): Flow<BudgetConfig?> =
        dao.getBudgetConfig(monthKey, householdId)

    fun getNotificationsForUser(householdId: String, userId: String): Flow<List<HouseholdNotification>> =
        dao.getNotificationsForUser(householdId, userId)

    fun getUnreadNotificationsCount(householdId: String, userId: String): Flow<Int> =
        dao.getUnreadNotificationsCount(householdId, userId)

    val pendingSyncCount: Flow<Int> = dao.getPendingSyncCount()
    val syncStatus: StateFlow<SyncStatus> = syncManager.syncStatus
    val lastSyncMessage: StateFlow<String> = syncManager.lastSyncMessage

    /**
     * Persist mutation to SQLite sync queue and push immediately if online.
     */
    suspend fun recordAndPush(
        entityType: String,
        entityId: String,
        action: String = "UPSERT",
        payloadJson: String = ""
    ) {
        dao.insertSyncQueueItem(
            SyncQueueItem(
                entityType = entityType,
                entityId = entityId,
                action = action,
                payloadJson = payloadJson
            )
        )
        if (syncManager.isOnline()) {
            backgroundScope.launch {
                syncManager.processPendingQueue(dao)
            }
        }
    }

    suspend fun reconcileWithCloud(
        householdId: String,
        onProgress: (stage: String, percent: Int) -> Unit = { _, _ -> }
    ): SyncReport {
        return syncManager.reconcileAndSync(dao, householdId, onProgress)
    }

    suspend fun regenerateHouseholdInviteCode(householdId: String): String {
        val newCode = "RV" + UUID.randomUUID().toString().replace("-", "").take(4).uppercase()
        val household = dao.getHouseholdDirect(householdId)
        if (household != null) {
            val updated = household.copy(inviteCode = newCode)
            dao.insertHousehold(updated)
            recordAndPush("HOUSEHOLD", updated.id, "UPSERT", updated.toJson())
        }
        return newCode
    }

    suspend fun updateHouseholdDetailsAndBudget(
        householdId: String,
        name: String,
        monthlyBudget: Double,
        warningThreshold: Int = 80
    ) {
        val household = dao.getHouseholdDirect(householdId)
        val code = household?.inviteCode ?: householdId.removePrefix("HOUSE_").ifBlank { "RV" + UUID.randomUUID().toString().take(4).uppercase() }
        val updated = (household ?: Household(id = householdId, name = name, inviteCode = code, createdByUserId = "ADMIN")).copy(
            name = name,
            monthlyBudgetLimit = monthlyBudget,
            budgetWarningThreshold = warningThreshold
        )
        dao.insertHousehold(updated)
        recordAndPush("HOUSEHOLD", updated.id, "UPSERT", updated.toJson())

        val monthKey = DateUtils.getCurrentMonthYearKey()
        val existingBudget = dao.getBudgetConfigDirect(monthKey, householdId)
        val newBudget = (existingBudget ?: BudgetConfig(monthYearKey = monthKey, householdId = householdId)).copy(
            totalBudgetLimit = monthlyBudget,
            alertThresholdPercent = warningThreshold
        )
        dao.insertBudgetConfig(newBudget)
        recordAndPush("BUDGET_CONFIG", "${newBudget.monthYearKey}_${newBudget.householdId}", "UPSERT", newBudget.toJson())
    }

    // --- Action Methods with Offline-First Persistence + Background Sync ---

    suspend fun addExpense(
        title: String,
        amount: Double,
        category: String,
        paidBy: UserProfile,
        householdId: String,
        splitType: String,
        members: List<UserProfile>,
        notes: String,
        dateMillis: Long = System.currentTimeMillis()
    ) {
        val monthKey = DateUtils.getMonthYearKey(dateMillis)
        val expenseId = UUID.randomUUID().toString()
        val splitUserIds = if (splitType == "CUSTOM") members.joinToString(",") { it.id } else ""
        val expense = ExpenseItem(
            id = expenseId,
            title = title,
            amount = amount,
            category = category,
            dateMillis = dateMillis,
            monthYearKey = monthKey,
            paidByUserId = paidBy.id,
            paidByName = paidBy.name,
            splitType = splitType,
            splitWithUserIds = splitUserIds,
            householdId = householdId,
            notes = notes
        )
        dao.insertExpense(expense)
        recordAndPush("EXPENSE", expense.id, "UPSERT", expense.toJson())

        // Automatically create settlement debts for participating roommates
        if (splitType != "PERSONAL" && members.isNotEmpty()) {
            val totalInvolved = if (members.any { it.id == paidBy.id }) members.size else (members.size + 1)
            val perHeadAmount = amount / totalInvolved
            members.filter { it.id != paidBy.id }.forEach { member ->
                val debt = SettlementDebt(
                    id = UUID.randomUUID().toString(),
                    fromUserId = member.id,
                    fromUserName = member.name,
                    toUserId = paidBy.id,
                    toUserName = paidBy.name,
                    toUserUpiId = paidBy.upiId,
                    amount = perHeadAmount,
                    reason = "Split for $title",
                    status = "PENDING",
                    householdId = householdId
                )
                dao.insertDebt(debt)
                recordAndPush("DEBT", debt.id, "UPSERT", debt.toJson())
            }
        }
    }

    suspend fun updateExpense(
        expense: ExpenseItem,
        splitMembers: List<UserProfile> = emptyList()
    ) {
        val updatedExpense = expense.copy(
            monthYearKey = DateUtils.getMonthYearKey(expense.dateMillis)
        )
        dao.updateExpense(updatedExpense)
        recordAndPush("EXPENSE", updatedExpense.id, "UPSERT", updatedExpense.toJson())
    }

    suspend fun deleteExpense(expenseId: String) {
        dao.deleteExpense(expenseId)
        recordAndPush("EXPENSE", expenseId, "DELETE")
    }

    suspend fun addChore(
        title: String,
        description: String,
        category: String,
        frequency: String,
        assignedUser: UserProfile,
        rotationMembers: List<UserProfile>,
        scheduledTime: String,
        dayOfWeek: Int,
        dayOfMonth: Int,
        householdId: String,
        points: Int
    ) {
        val rotationIds = rotationMembers.joinToString(",") { it.id }
        val chore = ChoreTask(
            id = UUID.randomUUID().toString(),
            title = title,
            description = description,
            category = category,
            assignedToUserId = assignedUser.id,
            assignedToUserName = assignedUser.name,
            householdId = householdId,
            frequency = frequency,
            rotationMemberIds = rotationIds,
            rotationIndex = 0,
            scheduledTime = scheduledTime,
            scheduledDayOfWeek = dayOfWeek,
            scheduledDayOfMonth = dayOfMonth,
            points = points
        )
        dao.insertChore(chore)
        recordAndPush("CHORE", chore.id, "UPSERT", chore.toJson())
    }

    suspend fun updateChore(chore: ChoreTask) {
        dao.updateChore(chore)
        recordAndPush("CHORE", chore.id, "UPSERT", chore.toJson())
    }

    suspend fun updateChoreStatus(choreId: String, status: String) {
        val today = DateUtils.formatDisplayDate(System.currentTimeMillis())
        dao.updateChoreStatus(choreId, status, today)
        val hid = dao.getCurrentUserDirect()?.householdId ?: ""
        val chores = dao.getAllChoresDirect(hid)
        chores.find { it.id == choreId }?.let {
            recordAndPush("CHORE", it.id, "UPSERT", it.toJson())
        }
    }

    suspend fun advanceChoreRotation(chore: ChoreTask, members: List<UserProfile>) {
        if (chore.rotationMemberIds.isBlank()) return
        val memberIdList = chore.rotationMemberIds.split(",").map { it.trim() }
        if (memberIdList.isEmpty()) return

        val nextIndex = (chore.rotationIndex + 1) % memberIdList.size
        val nextUserId = memberIdList[nextIndex]
        val nextUser = members.find { it.id == nextUserId } ?: UserProfile(id = nextUserId, name = "Roommate $nextIndex")

        dao.advanceChoreRotation(chore.id, nextIndex, nextUser.id, nextUser.name)
        val updatedChore = chore.copy(
            rotationIndex = nextIndex,
            assignedToUserId = nextUser.id,
            assignedToUserName = nextUser.name
        )
        recordAndPush("CHORE", updatedChore.id, "UPSERT", updatedChore.toJson())
    }

    suspend fun deleteChore(choreId: String) {
        dao.deleteChore(choreId)
        recordAndPush("CHORE", choreId, "DELETE")
    }

    suspend fun addDebt(
        fromUser: UserProfile,
        toUser: UserProfile,
        amount: Double,
        reason: String,
        householdId: String
    ) {
        val debt = SettlementDebt(
            id = UUID.randomUUID().toString(),
            fromUserId = fromUser.id,
            fromUserName = fromUser.name,
            toUserId = toUser.id,
            toUserName = toUser.name,
            toUserUpiId = toUser.upiId,
            amount = amount,
            reason = reason,
            status = "PENDING",
            householdId = householdId
        )
        dao.insertDebt(debt)
        recordAndPush("DEBT", debt.id, "UPSERT", debt.toJson())
    }

    suspend fun updateDebtStatus(debtId: String, status: String, txRef: String = "") {
        val settledTime = if (status == "VERIFIED") System.currentTimeMillis() else 0L
        dao.updateDebtStatus(debtId, status, settledTime, txRef)
        val hid = dao.getCurrentUserDirect()?.householdId ?: ""
        val debts = dao.getSettlementDebtsDirect(hid)
        debts.find { it.id == debtId }?.let {
            recordAndPush("DEBT", it.id, "UPSERT", it.toJson())
        }
    }

    suspend fun deleteDebt(debtId: String) {
        dao.deleteDebt(debtId)
        recordAndPush("DEBT", debtId, "DELETE")
    }

    suspend fun addSavingsGoal(
        title: String,
        targetAmount: Double,
        initialAmount: Double,
        category: String,
        householdId: String,
        colorHex: String
    ) {
        val goal = SavingsGoal(
            id = UUID.randomUUID().toString(),
            title = title,
            targetAmount = targetAmount,
            currentAmount = initialAmount,
            category = category,
            householdId = householdId,
            colorHex = colorHex
        )
        dao.insertSavingsGoal(goal)
        recordAndPush("SAVINGS_GOAL", goal.id, "UPSERT", goal.toJson())
    }

    suspend fun updateSavingsAmount(goalId: String, newAmount: Double) {
        dao.updateSavingsProgress(goalId, newAmount, System.currentTimeMillis())
        val hid = dao.getCurrentUserDirect()?.householdId ?: ""
        val goals = dao.getSavingsGoalsDirect(hid)
        goals.find { it.id == goalId }?.let {
            val updated = it.copy(currentAmount = newAmount)
            recordAndPush("SAVINGS_GOAL", updated.id, "UPSERT", updated.toJson())
        }
    }

    suspend fun deleteSavingsGoal(goalId: String) {
        dao.deleteSavingsGoal(goalId)
        recordAndPush("SAVINGS_GOAL", goalId, "DELETE")
    }

    suspend fun updateBudgetConfig(config: BudgetConfig) {
        dao.insertBudgetConfig(config)
        recordAndPush("BUDGET_CONFIG", "${config.monthYearKey}_${config.householdId}", "UPSERT", config.toJson())
    }

    suspend fun searchUserInCloud(query: String): List<UserProfile> = withContext(Dispatchers.IO) {
        val url = syncManager.supabaseUrl
        val anonKey = syncManager.supabaseAnonKey
        if (url.isBlank() || anonKey.isBlank()) return@withContext emptyList()
        syncManager.dataStore.searchUserProfiles(url, anonKey, query)
    }

    suspend fun addPerson(
        name: String,
        email: String,
        upiId: String,
        householdId: String,
        isExternalFriend: Boolean,
        avatarColor: String
    ): Pair<Boolean, String> = withContext(Dispatchers.IO) {
        val url = syncManager.supabaseUrl
        val anonKey = syncManager.supabaseAnonKey
        val cleanEmail = email.trim()
        val cleanName = name.trim()
        val cleanUpi = upiId.trim()

        // 1. Check if the user already has a registered account in Supabase
        var registeredUser: UserProfile? = null
        if (cleanEmail.isNotBlank() && url.isNotBlank() && anonKey.isNotBlank()) {
            registeredUser = syncManager.dataStore.findUserProfileByEmail(url, anonKey, cleanEmail)
        }

        if (registeredUser != null) {
            // User ALREADY has an account!
            val user = registeredUser.copy(
                name = cleanName.ifBlank { registeredUser.name },
                upiId = if (cleanUpi.isNotBlank()) cleanUpi else registeredUser.upiId,
                householdId = householdId,
                householdName = if (isExternalFriend) "EXTERNAL_FRIEND" else registeredUser.householdName,
                avatarColorHex = if (avatarColor.isNotBlank()) avatarColor else registeredUser.avatarColorHex,
                isCurrentUser = false,
                isVirtual = false
            )
            dao.insertUser(user)
            recordAndPush("USER_PROFILE", user.id, "UPSERT", user.toJson())

            // Reconcile any existing debts/expenses associated with this email
            reconcileVirtualUsersWithCloud()

            Pair(
                true,
                "✅ Found registered account for ${user.name} (${user.email})! Added & linked successfully."
            )
        } else {
            // User does NOT have an account yet in this app.
            // Save their details personally for transaction / ledger purposes.
            val virtualId = "USR_" + UUID.randomUUID().toString().take(8).uppercase()
            val user = UserProfile(
                id = virtualId,
                name = cleanName,
                email = cleanEmail,
                upiId = cleanUpi,
                householdId = householdId,
                householdName = if (isExternalFriend) "EXTERNAL_FRIEND" else "Household",
                avatarColorHex = avatarColor,
                isCurrentUser = false,
                isVirtual = true
            )
            dao.insertUser(user)
            recordAndPush("USER_PROFILE", user.id, "UPSERT", user.toJson())

            val msg = if (isExternalFriend) {
                "Saved $cleanName personally as external friend. When they register with $cleanEmail, all transactions will link automatically!"
            } else {
                "Saved $cleanName as roommate. When they register with $cleanEmail, all transactions will link automatically!"
            }
            Pair(true, msg)
        }
    }

    suspend fun addRoommate(
        name: String,
        email: String,
        upiId: String,
        householdId: String,
        avatarColor: String
    ) {
        addPerson(
            name = name,
            email = email,
            upiId = upiId,
            householdId = householdId,
            isExternalFriend = false,
            avatarColor = avatarColor
        )
    }

    /**
     * Reconciles all virtual users with registered cloud accounts.
     * When an external friend or roommate creates their account later with the same Gmail address,
     * this automatically migrates all transactions, debts, expenses, and splits to their real account ID!
     */
    suspend fun reconcileVirtualUsersWithCloud(): Int = withContext(Dispatchers.IO) {
        val url = syncManager.supabaseUrl
        val anonKey = syncManager.supabaseAnonKey
        if (url.isBlank() || anonKey.isBlank()) return@withContext 0

        var mergedCount = 0
        try {
            val virtualUsers = dao.getAllVirtualUsersDirect()
            for (vUser in virtualUsers) {
                val cleanEmail = vUser.email.trim()
                if (cleanEmail.isBlank()) continue

                val realUser = syncManager.dataStore.findUserProfileByEmail(url, anonKey, cleanEmail)
                if (realUser != null && realUser.id != vUser.id && !realUser.isVirtual) {
                    Log.i("RoomieRepo", "Merging virtual user ${vUser.name} (${vUser.id}) -> real user ${realUser.name} (${realUser.id})")

                    val targetUpi = if (realUser.upiId.isNotBlank()) realUser.upiId else vUser.upiId

                    // 1. Migrate debts in Room DB
                    dao.migrateDebtDebtor(vUser.id, realUser.id, realUser.name)
                    dao.migrateDebtCreditor(vUser.id, realUser.id, realUser.name, targetUpi)

                    // 2. Migrate expenses in Room DB
                    dao.migrateExpensePayer(vUser.id, realUser.id, realUser.name)
                    val splitExpenses = dao.getExpensesWithSplitMember(vUser.id)
                    for (exp in splitExpenses) {
                        val updatedSplits = exp.splitWithUserIds.split(",")
                            .map { if (it.trim() == vUser.id) realUser.id else it.trim() }
                            .filter { it.isNotBlank() }
                            .distinct()
                            .joinToString(",")
                        dao.insertExpense(exp.copy(splitWithUserIds = updatedSplits, isSynced = false))
                    }

                    // 3. Insert real user with preserved relationship (e.g. EXTERNAL_FRIEND)
                    val mergedProfile = realUser.copy(
                        upiId = targetUpi,
                        householdId = vUser.householdId,
                        householdName = vUser.householdName,
                        isCurrentUser = false,
                        isVirtual = false
                    )
                    dao.insertUser(mergedProfile)

                    // 4. Delete virtual user locally
                    dao.deleteUser(vUser.id)

                    // 5. In Supabase: push merged user profile, delete old virtual user profile
                    syncManager.dataStore.pushUserProfile(url, anonKey, mergedProfile)
                    syncManager.dataStore.deleteRecord(url, anonKey, "user_profiles", vUser.id)

                    // 6. Push all updated debts & expenses to Supabase
                    val updatedDebts = dao.getSettlementDebtsDirect(vUser.householdId)
                    for (d in updatedDebts) {
                        if (d.fromUserId == realUser.id || d.toUserId == realUser.id) {
                            syncManager.dataStore.pushDebt(url, anonKey, d)
                        }
                    }
                    val updatedHouseholdExpenses = dao.getExpensesByHouseholdDirect(vUser.householdId)
                    for (e in updatedHouseholdExpenses) {
                        if (e.paidByUserId == realUser.id || e.splitWithUserIds.contains(realUser.id)) {
                            syncManager.dataStore.pushExpense(url, anonKey, e)
                        }
                    }

                    mergedCount++
                }
            }
        } catch (e: Exception) {
            Log.e("RoomieRepo", "reconcileVirtualUsersWithCloud error: ${e.message}")
        }
        if (mergedCount > 0) {
            Log.i("RoomieRepo", "Successfully reconciled $mergedCount virtual users with registered cloud accounts.")
        }
        mergedCount
    }

    suspend fun updateRoommate(user: UserProfile) {
        dao.insertUser(user)
        recordAndPush("USER_PROFILE", user.id, "UPSERT", user.toJson())
    }

    suspend fun deleteRoommate(userId: String) {
        dao.deleteUser(userId)
        recordAndPush("USER_PROFILE", userId, "DELETE")
    }

    suspend fun updateUserProfile(user: UserProfile) {
        dao.insertUser(user)
        recordAndPush("USER_PROFILE", user.id, "UPSERT", user.toJson())
    }

    suspend fun createAndDispatchNotification(
        householdId: String,
        sender: UserProfile,
        targetUserId: String,
        targetUserName: String,
        type: String,
        title: String,
        message: String,
        relatedEntityId: String = ""
    ) {
        val notif = HouseholdNotification(
            id = UUID.randomUUID().toString(),
            householdId = householdId,
            senderUserId = sender.id,
            senderUserName = sender.name,
            targetUserId = targetUserId,
            targetUserName = targetUserName,
            type = type,
            title = title,
            message = message,
            relatedEntityId = relatedEntityId,
            isRead = false,
            createdAt = System.currentTimeMillis()
        )
        dao.insertNotification(notif)
        recordAndPush("NOTIFICATION", notif.id, "UPSERT", notif.toJson())
    }

    suspend fun markNotificationAsRead(notificationId: String) {
        dao.markNotificationAsRead(notificationId)
    }

    suspend fun markAllNotificationsAsRead(householdId: String, userId: String) {
        dao.markAllNotificationsAsRead(householdId, userId)
    }

    suspend fun switchHousehold(inviteCode: String, householdName: String, currentUser: UserProfile) {
        val householdId = "HOUSE_" + inviteCode.uppercase().trim()
        val household = Household(
            id = householdId,
            name = householdName.ifBlank { "Household $inviteCode" },
            inviteCode = inviteCode.uppercase().trim(),
            createdByUserId = currentUser.id
        )
        dao.insertHousehold(household)
        val updatedUser = currentUser.copy(
            householdId = householdId,
            householdName = household.name
        )
        dao.insertUser(updatedUser)
        recordAndPush("HOUSEHOLD", household.id, "UPSERT", household.toJson())
        recordAndPush("USER_PROFILE", updatedUser.id, "UPSERT", updatedUser.toJson())
    }

    // --- Supabase Authentication & Synchronization Bridges ---

    suspend fun onSupabaseAuthSuccess(supabaseUser: SupabaseUser) {
        dao.clearCurrentUserFlag()
        val userProfile = UserProfile(
            id = supabaseUser.id,
            name = supabaseUser.fullName.ifBlank { supabaseUser.email.substringBefore("@") },
            email = supabaseUser.email,
            upiId = supabaseUser.upiId,
            householdId = supabaseUser.householdId,
            householdName = supabaseUser.householdName,
            avatarColorHex = "#0F5132",
            isCurrentUser = true,
            isVirtual = false
        )
        dao.insertUser(userProfile)

        val household = Household(
            id = supabaseUser.householdId,
            name = supabaseUser.householdName,
            inviteCode = supabaseUser.householdId.removePrefix("HOUSE_"),
            createdByUserId = supabaseUser.id
        )
        dao.insertHousehold(household)

        // Perform full two-way sync on login
        syncManager.performFullSync(dao, supabaseUser.householdId)
        // Automatically reconcile any virtual users matched with cloud accounts
        reconcileVirtualUsersWithCloud()
    }

    suspend fun syncAllWithSupabase(householdId: String): Boolean {
        val result = syncManager.performFullSync(dao, householdId)
        reconcileVirtualUsersWithCloud()
        return result
    }

    suspend fun clearSampleDataAndSetupOriginalHousehold(
        userName: String,
        userEmail: String,
        userUpi: String,
        householdName: String,
        householdCode: String,
        monthlyBudget: Double = 30000.0
    ) {
        dao.clearAllExpenses()
        dao.clearAllChores()
        dao.clearAllDebts()
        dao.clearAllSavingsGoals()
        dao.clearAllNotifications()
        dao.clearAllUsers()
        dao.clearAllHouseholds()

        val cleanCode = householdCode.trim().uppercase().ifBlank { "HOME101" }
        val householdId = "HOUSE_$cleanCode"
        val userId = "USR_" + UUID.randomUUID().toString().take(8).uppercase()

        val household = Household(
            id = householdId,
            name = householdName.ifBlank { "My Household" },
            inviteCode = cleanCode,
            createdByUserId = userId,
            monthlyBudgetLimit = monthlyBudget,
            budgetWarningThreshold = 80
        )
        dao.insertHousehold(household)
        recordAndPush("HOUSEHOLD", household.id, "UPSERT", household.toJson())

        val currentUser = UserProfile(
            id = userId,
            name = userName.ifBlank { "User" },
            email = userEmail.ifBlank { "user@example.com" },
            upiId = userUpi.ifBlank { "user@upi" },
            householdId = householdId,
            householdName = household.name,
            avatarColorHex = "#0F5132",
            isCurrentUser = true,
            isVirtual = false
        )
        dao.insertUser(currentUser)
        recordAndPush("USER_PROFILE", currentUser.id, "UPSERT", currentUser.toJson())

        val monthKey = DateUtils.getCurrentMonthYearKey()
        val budget = BudgetConfig(
            monthYearKey = monthKey,
            householdId = householdId,
            totalBudgetLimit = monthlyBudget,
            alertThresholdPercent = 80
        )
        dao.insertBudgetConfig(budget)
        recordAndPush("BUDGET_CONFIG", "${budget.monthYearKey}_${budget.householdId}", "UPSERT", budget.toJson())
    }

    suspend fun purgeSampleMockDataIfPresent() {
        // Safe no-op hook for clean data start
    }
}
