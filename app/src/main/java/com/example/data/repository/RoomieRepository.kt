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

    // --- Seed Initial Data if First Run ---
    suspend fun checkAndSeedInitialData() {
        val existingUser = dao.getCurrentUserDirect()
        if (existingUser != null) return // Already initialized

        val defaultHouseholdId = "HOUSE_FLAT_402"
        val initialHousehold = Household(
            id = defaultHouseholdId,
            name = "Flat 402 - Green View",
            inviteCode = "FLAT402",
            createdByUserId = "USR_ALEX",
            monthlyBudgetLimit = 35000.0,
            budgetWarningThreshold = 80
        )
        dao.insertHousehold(initialHousehold)

        val currentUser = UserProfile(
            id = "USR_ALEX",
            name = "Alex Sharma",
            email = "alex.sharma@example.com",
            upiId = "alexsharma@okaxis",
            householdId = defaultHouseholdId,
            householdName = "Flat 402 - Green View",
            avatarColorHex = "#0F5132",
            isCurrentUser = true
        )

        val roommateRahul = UserProfile(
            id = "USR_RAHUL",
            name = "Rahul Verma",
            email = "rahul.v@example.com",
            upiId = "rahulverma@paytm",
            householdId = defaultHouseholdId,
            householdName = "Flat 402 - Green View",
            avatarColorHex = "#3B82F6",
            isCurrentUser = false
        )

        val roommatePriya = UserProfile(
            id = "USR_PRIYA",
            name = "Priya Patel",
            email = "priya.p@example.com",
            upiId = "priyapatel@okhdfcbank",
            householdId = defaultHouseholdId,
            householdName = "Flat 402 - Green View",
            avatarColorHex = "#EC4899",
            isCurrentUser = false
        )

        val roommateVikram = UserProfile(
            id = "USR_VIKRAM",
            name = "Vikram Singh",
            email = "vikram.s@example.com",
            upiId = "vikram@ybl",
            householdId = defaultHouseholdId,
            householdName = "Flat 402 - Green View",
            avatarColorHex = "#F59E0B",
            isCurrentUser = false
        )

        dao.insertUsers(listOf(currentUser, roommateRahul, roommatePriya, roommateVikram))

        // Initial Expenses
        val currentMonthKey = DateUtils.getCurrentMonthYearKey()
        val expenses = listOf(
            ExpenseItem(
                id = UUID.randomUUID().toString(),
                title = "Monthly Groceries & Supermarket",
                amount = 6450.0,
                category = "Groceries",
                monthYearKey = currentMonthKey,
                paidByUserId = currentUser.id,
                paidByName = currentUser.name,
                splitType = "EQUAL",
                householdId = defaultHouseholdId,
                notes = "Vegetables, rice, oil, spices from DMart"
            ),
            ExpenseItem(
                id = UUID.randomUUID().toString(),
                title = "Fiber WiFi & Internet Bill",
                amount = 1199.0,
                category = "Utilities",
                monthYearKey = currentMonthKey,
                paidByUserId = roommateRahul.id,
                paidByName = roommateRahul.name,
                splitType = "EQUAL",
                householdId = defaultHouseholdId,
                notes = "Airtel 200Mbps Monthly Unlimited"
            ),
            ExpenseItem(
                id = UUID.randomUUID().toString(),
                title = "House Cleaning Supplies & Detergents",
                amount = 1420.0,
                category = "Household Supplies",
                monthYearKey = currentMonthKey,
                paidByUserId = roommatePriya.id,
                paidByName = roommatePriya.name,
                splitType = "EQUAL",
                householdId = defaultHouseholdId,
                notes = "Mops, trash bags, dishwash liquid"
            ),
            ExpenseItem(
                id = UUID.randomUUID().toString(),
                title = "Weekend Flatmate Dinner & Biryani",
                amount = 2850.0,
                category = "Food & Dining",
                monthYearKey = currentMonthKey,
                paidByUserId = currentUser.id,
                paidByName = currentUser.name,
                splitType = "EQUAL",
                householdId = defaultHouseholdId,
                notes = "Special Biryani Feast"
            )
        )
        dao.insertExpenses(expenses)

        // Initial Chores with Daily, Sunday-to-Sunday rotating, and Monthly
        val memberIds = "USR_ALEX,USR_RAHUL,USR_PRIYA,USR_VIKRAM"
        val chores = listOf(
            ChoreTask(
                id = UUID.randomUUID().toString(),
                title = "Sunday Deep Cleaning (Hall & Balcony)",
                description = "Alternates every Sunday among roommates. This Sunday is Alex's turn, next is Rahul's!",
                category = "Cleaning",
                assignedToUserId = currentUser.id,
                assignedToUserName = currentUser.name,
                householdId = defaultHouseholdId,
                frequency = "WEEKLY_SUNDAY_ROTATION",
                rotationMemberIds = memberIds,
                rotationIndex = 0,
                scheduledTime = "10:00 AM",
                scheduledDayOfWeek = 1, // Sunday
                status = "PENDING",
                points = 25
            ),
            ChoreTask(
                id = UUID.randomUUID().toString(),
                title = "Daily Evening Trash & Wet Waste Disposal",
                description = "Take waste bags to society collection bin before 9 PM",
                category = "Trash",
                assignedToUserId = roommateRahul.id,
                assignedToUserName = roommateRahul.name,
                householdId = defaultHouseholdId,
                frequency = "DAILY",
                scheduledTime = "08:30 PM",
                status = "PENDING",
                points = 10
            ),
            ChoreTask(
                id = UUID.randomUUID().toString(),
                title = "Kitchen Countertop & Stove Wipedown",
                description = "Nightly kitchen sanitize after dinner",
                category = "Kitchen",
                assignedToUserId = roommatePriya.id,
                assignedToUserName = roommatePriya.name,
                householdId = defaultHouseholdId,
                frequency = "DAILY",
                scheduledTime = "10:30 PM",
                status = "COMPLETED",
                lastCompletedDate = DateUtils.formatDisplayDate(System.currentTimeMillis()),
                points = 10
            ),
            ChoreTask(
                id = UUID.randomUUID().toString(),
                title = "Monthly RO Water Purifier & AC Filter Clean",
                description = "Check RO TDS, change pre-filter, clean AC dust mesh on 1st of every month",
                category = "Maintenance",
                assignedToUserId = roommateVikram.id,
                assignedToUserName = roommateVikram.name,
                householdId = defaultHouseholdId,
                frequency = "MONTHLY",
                scheduledDayOfMonth = 1,
                scheduledTime = "11:00 AM",
                status = "PENDING",
                points = 30
            )
        )
        dao.insertChores(chores)

        // Initial Settlement Debts
        val debts = listOf(
            SettlementDebt(
                id = UUID.randomUUID().toString(),
                fromUserId = roommateRahul.id,
                fromUserName = roommateRahul.name,
                toUserId = currentUser.id,
                toUserName = currentUser.name,
                toUserUpiId = currentUser.upiId,
                amount = 1612.50,
                reason = "Share of Monthly Groceries (DMart)",
                status = "PENDING",
                householdId = defaultHouseholdId
            ),
            SettlementDebt(
                id = UUID.randomUUID().toString(),
                fromUserId = currentUser.id,
                fromUserName = currentUser.name,
                toUserId = roommateRahul.id,
                toUserName = roommateRahul.name,
                toUserUpiId = roommateRahul.upiId,
                amount = 299.75,
                reason = "WiFi Bill Split (Airtel)",
                status = "PENDING",
                householdId = defaultHouseholdId
            ),
            SettlementDebt(
                id = UUID.randomUUID().toString(),
                fromUserId = roommateVikram.id,
                fromUserName = roommateVikram.name,
                toUserId = currentUser.id,
                toUserName = currentUser.name,
                toUserUpiId = currentUser.upiId,
                amount = 712.50,
                reason = "Weekend Dinner Biryani Split",
                status = "PAID_PENDING_CONFIRMATION",
                transactionRef = "UPI/GPay/6829103",
                householdId = defaultHouseholdId
            )
        )
        dao.insertDebts(debts)

        // Initial Savings Goals
        val savings = listOf(
            SavingsGoal(
                id = UUID.randomUUID().toString(),
                title = "Flat Emergency & Repair Fund",
                targetAmount = 40000.0,
                currentAmount = 24500.0,
                category = "Emergency Fund",
                householdId = defaultHouseholdId,
                colorHex = "#10B981"
            ),
            SavingsGoal(
                id = UUID.randomUUID().toString(),
                title = "Living Room Smart TV & Microwave",
                targetAmount = 28000.0,
                currentAmount = 19000.0,
                category = "Room Appliance",
                householdId = defaultHouseholdId,
                colorHex = "#3B82F6"
            )
        )
        savings.forEach { dao.insertSavingsGoal(it) }

        // Initial Budget Config
        val budget = BudgetConfig(
            monthYearKey = currentMonthKey,
            householdId = defaultHouseholdId,
            totalBudgetLimit = 35000.0,
            alertThresholdPercent = 80,
            groceriesBudget = 12000.0,
            rentUtilitiesBudget = 15000.0,
            foodDiningBudget = 5000.0,
            miscellaneousBudget = 3000.0
        )
        dao.insertBudgetConfig(budget)
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
