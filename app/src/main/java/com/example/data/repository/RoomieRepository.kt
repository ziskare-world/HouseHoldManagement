package com.example.data.repository

import android.content.Context
import com.example.data.local.AppDatabase
import com.example.data.local.dao.RoomieDao
import com.example.data.local.model.BudgetConfig
import com.example.data.local.model.ChoreTask
import com.example.data.local.model.ExpenseItem
import com.example.data.local.model.Household
import com.example.data.local.model.SavingsGoal
import com.example.data.local.model.SettlementDebt
import com.example.data.local.model.UserProfile
import com.example.data.remote.SupabaseSyncManager
import com.example.data.remote.SupabaseUser
import com.example.util.DateUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
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

    fun getAllExpenses(householdId: String): Flow<List<ExpenseItem>> =
        dao.getAllExpenses(householdId)

    fun getExpensesByMonth(householdId: String, monthKey: String): Flow<List<ExpenseItem>> =
        dao.getExpensesByMonth(householdId, monthKey)

    fun getAllChores(householdId: String): Flow<List<ChoreTask>> =
        dao.getAllChores(householdId)

    fun getSettlementDebts(householdId: String): Flow<List<SettlementDebt>> =
        dao.getSettlementDebts(householdId)

    fun getSavingsGoals(householdId: String): Flow<List<SavingsGoal>> =
        dao.getSavingsGoals(householdId)

    fun getBudgetConfig(monthKey: String, householdId: String): Flow<BudgetConfig?> =
        dao.getBudgetConfig(monthKey, householdId)

    // --- Clean Slate: Purge Legacy Sample Mock Data ---
    suspend fun purgeSampleMockDataIfPresent() {
        val mockUser = dao.getCurrentUserDirect()
        if (mockUser?.id == "USR_ALEX" || mockUser?.householdId == "HOUSE_FLAT_402") {
            dao.clearAllExpenses()
            dao.clearAllChores()
            dao.clearAllDebts()
            dao.clearAllSavingsGoals()
            dao.clearAllUsers()
            dao.clearAllHouseholds()
        }
    }

    suspend fun regenerateInviteCode(householdId: String): String {
        val newCode = "RV" + UUID.randomUUID().toString().replace("-", "").take(4).uppercase()
        val household = dao.getHouseholdDirect(householdId)
        if (household != null) {
            val updated = household.copy(inviteCode = newCode)
            dao.insertHousehold(updated)
            backgroundScope.launch {
                syncManager.dataStore.pushHousehold(syncManager.supabaseUrl, syncManager.supabaseAnonKey, updated)
            }
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
        backgroundScope.launch {
            syncManager.dataStore.pushHousehold(syncManager.supabaseUrl, syncManager.supabaseAnonKey, updated)
        }

        val monthKey = DateUtils.getCurrentMonthYearKey()
        val existingBudget = dao.getBudgetConfigDirect(monthKey, householdId)
        val newBudget = (existingBudget ?: BudgetConfig(monthYearKey = monthKey, householdId = householdId)).copy(
            totalBudgetLimit = monthlyBudget,
            alertThresholdPercent = warningThreshold
        )
        dao.insertBudgetConfig(newBudget)
        backgroundScope.launch {
            syncManager.dataStore.pushBudgetConfig(syncManager.supabaseUrl, syncManager.supabaseAnonKey, newBudget)
        }
    }

    // --- Action Methods with Automatic Cloud Sync ---

    suspend fun addExpense(
        title: String,
        amount: Double,
        category: String,
        paidBy: UserProfile,
        householdId: String,
        splitType: String,
        members: List<UserProfile>,
        notes: String
    ) {
        val monthKey = DateUtils.getCurrentMonthYearKey()
        val expenseId = UUID.randomUUID().toString()
        val expense = ExpenseItem(
            id = expenseId,
            title = title,
            amount = amount,
            category = category,
            monthYearKey = monthKey,
            paidByUserId = paidBy.id,
            paidByName = paidBy.name,
            splitType = splitType,
            householdId = householdId,
            notes = notes
        )
        dao.insertExpense(expense)

        // Asynchronously push to Supabase
        backgroundScope.launch {
            syncManager.dataStore.pushExpense(syncManager.supabaseUrl, syncManager.supabaseAnonKey, expense)
        }

        // Automatically create settlement debts if split equally
        if (splitType == "EQUAL" && members.isNotEmpty()) {
            val perHeadAmount = amount / members.size
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
                backgroundScope.launch {
                    syncManager.dataStore.pushDebt(syncManager.supabaseUrl, syncManager.supabaseAnonKey, debt)
                }
            }
        }
    }

    suspend fun deleteExpense(expenseId: String) {
        dao.deleteExpense(expenseId)
        backgroundScope.launch {
            syncManager.dataStore.deleteRecord(syncManager.supabaseUrl, syncManager.supabaseAnonKey, "expenses", expenseId)
        }
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
        backgroundScope.launch {
            syncManager.dataStore.pushChore(syncManager.supabaseUrl, syncManager.supabaseAnonKey, chore)
        }
    }

    suspend fun updateChoreStatus(choreId: String, status: String) {
        val today = DateUtils.formatDisplayDate(System.currentTimeMillis())
        dao.updateChoreStatus(choreId, status, today)
        backgroundScope.launch {
            val hid = dao.getCurrentUserDirect()?.householdId ?: "HOUSE_FLAT_402"
            val chores = dao.getAllChoresDirect(hid)
            chores.find { it.id == choreId }?.let {
                syncManager.dataStore.pushChore(syncManager.supabaseUrl, syncManager.supabaseAnonKey, it)
            }
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
        backgroundScope.launch {
            syncManager.dataStore.pushChore(syncManager.supabaseUrl, syncManager.supabaseAnonKey, updatedChore)
        }
    }

    suspend fun deleteChore(choreId: String) {
        dao.deleteChore(choreId)
        backgroundScope.launch {
            syncManager.dataStore.deleteRecord(syncManager.supabaseUrl, syncManager.supabaseAnonKey, "chore_tasks", choreId)
        }
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
        backgroundScope.launch {
            syncManager.dataStore.pushDebt(syncManager.supabaseUrl, syncManager.supabaseAnonKey, debt)
        }
    }

    suspend fun updateDebtStatus(debtId: String, status: String, txRef: String = "") {
        val settledTime = if (status == "VERIFIED") System.currentTimeMillis() else 0L
        dao.updateDebtStatus(debtId, status, settledTime, txRef)
        backgroundScope.launch {
            val hid = dao.getCurrentUserDirect()?.householdId ?: "HOUSE_FLAT_402"
            val debts = dao.getSettlementDebtsDirect(hid)
            debts.find { it.id == debtId }?.let {
                syncManager.dataStore.pushDebt(syncManager.supabaseUrl, syncManager.supabaseAnonKey, it)
            }
        }
    }

    suspend fun deleteDebt(debtId: String) {
        dao.deleteDebt(debtId)
        backgroundScope.launch {
            syncManager.dataStore.deleteRecord(syncManager.supabaseUrl, syncManager.supabaseAnonKey, "settlement_debts", debtId)
        }
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
        backgroundScope.launch {
            syncManager.dataStore.pushSavingsGoal(syncManager.supabaseUrl, syncManager.supabaseAnonKey, goal)
        }
    }

    suspend fun updateSavingsAmount(goalId: String, newAmount: Double) {
        dao.updateSavingsProgress(goalId, newAmount, System.currentTimeMillis())
        backgroundScope.launch {
            val hid = dao.getCurrentUserDirect()?.householdId ?: "HOUSE_FLAT_402"
            val goals = dao.getSavingsGoalsDirect(hid)
            goals.find { it.id == goalId }?.let {
                syncManager.dataStore.pushSavingsGoal(syncManager.supabaseUrl, syncManager.supabaseAnonKey, it.copy(currentAmount = newAmount))
            }
        }
    }

    suspend fun deleteSavingsGoal(goalId: String) {
        dao.deleteSavingsGoal(goalId)
        backgroundScope.launch {
            syncManager.dataStore.deleteRecord(syncManager.supabaseUrl, syncManager.supabaseAnonKey, "savings_goals", goalId)
        }
    }

    suspend fun updateBudgetConfig(config: BudgetConfig) {
        dao.insertBudgetConfig(config)
        backgroundScope.launch {
            syncManager.dataStore.pushBudgetConfig(syncManager.supabaseUrl, syncManager.supabaseAnonKey, config)
        }
    }

    suspend fun addRoommate(
        name: String,
        email: String,
        upiId: String,
        householdId: String,
        avatarColor: String
    ) {
        val user = UserProfile(
            id = "USR_" + UUID.randomUUID().toString().take(8).uppercase(),
            name = name.trim(),
            email = email.trim(),
            upiId = upiId.trim(),
            householdId = householdId,
            avatarColorHex = avatarColor,
            isCurrentUser = false,
            isVirtual = true
        )
        dao.insertUser(user)
        backgroundScope.launch {
            syncManager.dataStore.pushUserProfile(syncManager.supabaseUrl, syncManager.supabaseAnonKey, user)
        }
    }

    suspend fun updateUserProfile(user: UserProfile) {
        dao.insertUser(user)
        backgroundScope.launch {
            syncManager.dataStore.pushUserProfile(syncManager.supabaseUrl, syncManager.supabaseAnonKey, user)
        }
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
        backgroundScope.launch {
            syncManager.dataStore.pushHousehold(syncManager.supabaseUrl, syncManager.supabaseAnonKey, household)
            syncManager.dataStore.pushUserProfile(syncManager.supabaseUrl, syncManager.supabaseAnonKey, updatedUser)
        }
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

        // Pull household cloud data immediately
        syncManager.pullFromCloud(dao, supabaseUser.householdId)
    }

    suspend fun syncAllWithSupabase(householdId: String): Boolean {
        val pushOk = syncManager.pushAllToCloud(dao, householdId)
        val pullRes = syncManager.pullFromCloud(dao, householdId)
        return pushOk || pullRes.isSuccess
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

        val monthKey = DateUtils.getCurrentMonthYearKey()
        val budget = BudgetConfig(
            monthYearKey = monthKey,
            householdId = householdId,
            totalBudgetLimit = monthlyBudget,
            alertThresholdPercent = 80
        )
        dao.insertBudgetConfig(budget)

        backgroundScope.launch {
            syncManager.pushAllToCloud(dao, householdId)
        }
    }
}
