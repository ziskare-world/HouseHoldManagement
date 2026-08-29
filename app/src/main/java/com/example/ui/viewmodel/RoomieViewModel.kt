package com.example.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.model.BudgetConfig
import com.example.data.local.model.ChoreTask
import com.example.data.local.model.ExpenseItem
import com.example.data.local.model.Household
import com.example.data.local.model.SavingsGoal
import com.example.data.local.model.SettlementDebt
import com.example.data.local.model.UserProfile
import com.example.data.remote.SupabaseAuthState
import com.example.data.remote.SupabaseUser
import com.example.data.repository.RoomieRepository
import com.example.notification.ChoreNotificationHelper
import com.example.util.DateUtils
import com.example.util.UpiPaymentHelper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class ScreenTab {
    DASHBOARD,
    EXPENSES,
    CHORES,
    SETTLEMENTS,
    ANALYTICS,
    HOUSEHOLD,
    HOUSEHOLD_SETTINGS
}

data class BudgetStatus(
    val totalSpent: Double,
    val budgetLimit: Double,
    val percentUsed: Int,
    val isWarning: Boolean,
    val isOverBudget: Boolean,
    val remainingBudget: Double
)

class RoomieViewModel(application: Application) : AndroidViewModel(application) {
    val repository = RoomieRepository.getInstance(application)
    private val context = application.applicationContext

    private val sessionPrefs = application.getSharedPreferences("roomie_session_prefs", android.content.Context.MODE_PRIVATE)

    private val _isLoggedIn = MutableStateFlow(
        sessionPrefs.getBoolean("is_logged_in", false) || repository.syncManager.authManager.isUserLoggedIn
    )
    val isLoggedIn: StateFlow<Boolean> = _isLoggedIn.asStateFlow()

    private val _currentTab = MutableStateFlow(ScreenTab.DASHBOARD)
    val currentTab: StateFlow<ScreenTab> = _currentTab.asStateFlow()

    private val _selectedMonthKey = MutableStateFlow(DateUtils.getCurrentMonthYearKey())
    val selectedMonthKey: StateFlow<String> = _selectedMonthKey.asStateFlow()

    private val _syncingState = MutableStateFlow(false)
    val syncingState: StateFlow<Boolean> = _syncingState.asStateFlow()

    private val _statusMessage = MutableStateFlow<String?>(null)
    val statusMessage: StateFlow<String?> = _statusMessage.asStateFlow()

    val authState: StateFlow<SupabaseAuthState> = repository.syncManager.authManager.authState

    init {
        ChoreNotificationHelper.createNotificationChannels(context)
    }

    fun logout() {
        viewModelScope.launch {
            sessionPrefs.edit().clear().apply()
            signOutSupabase()
            _isLoggedIn.value = false
            _currentTab.value = ScreenTab.DASHBOARD
        }
    }

    val currentUser: StateFlow<UserProfile?> = repository.currentUser
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val householdMembers: StateFlow<List<UserProfile>> = repository.currentUser
        .combine(repository.currentUser) { user, _ ->
            user?.householdId ?: "HOUSE_FLAT_402"
        }
        .combine(repository.currentUser) { hid, _ -> hid }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "HOUSE_FLAT_402")
        .let { _ ->
            repository.getHouseholdMembers("HOUSE_FLAT_402")
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
        }

    val household: StateFlow<Household?> = repository.getHousehold("HOUSE_FLAT_402")
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val allExpenses: StateFlow<List<ExpenseItem>> = repository.getAllExpenses("HOUSE_FLAT_402")
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allChores: StateFlow<List<ChoreTask>> = repository.getAllChores("HOUSE_FLAT_402")
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val settlementDebts: StateFlow<List<SettlementDebt>> = repository.getSettlementDebts("HOUSE_FLAT_402")
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val savingsGoals: StateFlow<List<SavingsGoal>> = repository.getSavingsGoals("HOUSE_FLAT_402")
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val currentBudgetConfig: StateFlow<BudgetConfig?> = repository.getBudgetConfig(DateUtils.getCurrentMonthYearKey(), "HOUSE_FLAT_402")
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val budgetStatus: StateFlow<BudgetStatus> = allExpenses
        .combine(currentBudgetConfig) { expenses, budgetConfig ->
            val curMonth = DateUtils.getCurrentMonthYearKey()
            val monthExpenses = expenses.filter { it.monthYearKey == curMonth }
            val totalSpent = monthExpenses.sumOf { it.amount }
            val limit = budgetConfig?.totalBudgetLimit ?: 35000.0
            val percent = if (limit > 0) ((totalSpent / limit) * 100).toInt() else 0
            val threshold = budgetConfig?.alertThresholdPercent ?: 80
            val isWarning = percent >= threshold && percent <= 100
            val isOver = percent > 100

            BudgetStatus(
                totalSpent = totalSpent,
                budgetLimit = limit,
                percentUsed = percent,
                isWarning = isWarning,
                isOverBudget = isOver,
                remainingBudget = (limit - totalSpent).coerceAtLeast(0.0)
            )
        }
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            BudgetStatus(0.0, 35000.0, 0, false, false, 35000.0)
        )

    fun selectTab(tab: ScreenTab) {
        _currentTab.value = tab
    }

    fun selectMonth(monthKey: String) {
        _selectedMonthKey.value = monthKey
    }

    fun clearStatusMessage() {
        _statusMessage.value = null
    }

    // --- Expense Actions ---
    fun addExpense(
        title: String,
        amount: Double,
        category: String,
        paidBy: UserProfile,
        splitType: String,
        notes: String
    ) {
        viewModelScope.launch {
            val user = currentUser.value ?: return@launch
            val members = householdMembers.value
            repository.addExpense(
                title = title,
                amount = amount,
                category = category,
                paidBy = paidBy,
                householdId = user.householdId,
                splitType = splitType,
                members = members,
                notes = notes
            )
            _statusMessage.value = "Added expense of ₹$amount for $title"

            // Check budget alert
            val budget = currentBudgetConfig.value
            val limit = budget?.totalBudgetLimit ?: 35000.0
            val curMonth = DateUtils.getCurrentMonthYearKey()
            val curTotal = allExpenses.value.filter { it.monthYearKey == curMonth }.sumOf { it.amount } + amount
            val percent = ((curTotal / limit) * 100).toInt()

            if (percent >= 100) {
                ChoreNotificationHelper.showBudgetAlert(
                    context,
                    1001,
                    "🚨 Monthly Budget Exceeded!",
                    "Warning: Total spending reached ₹$curTotal which exceeds the ₹$limit limit ($percent%)!"
                )
            } else if (percent >= (budget?.alertThresholdPercent ?: 80)) {
                ChoreNotificationHelper.showBudgetAlert(
                    context,
                    1002,
                    "⚠️ Approaching Monthly Budget Limit",
                    "Caution: You have used $percent% of your ₹$limit monthly budget."
                )
            }
        }
    }

    fun deleteExpense(expenseId: String) {
        viewModelScope.launch {
            repository.deleteExpense(expenseId)
            _statusMessage.value = "Expense deleted"
        }
    }

    // --- Chore Actions ---
    fun addChore(
        title: String,
        description: String,
        category: String,
        frequency: String,
        assignedUser: UserProfile,
        rotationMembers: List<UserProfile>,
        scheduledTime: String,
        dayOfWeek: Int,
        dayOfMonth: Int,
        points: Int
    ) {
        viewModelScope.launch {
            val user = currentUser.value ?: return@launch
            repository.addChore(
                title = title,
                description = description,
                category = category,
                frequency = frequency,
                assignedUser = assignedUser,
                rotationMembers = rotationMembers,
                scheduledTime = scheduledTime,
                dayOfWeek = dayOfWeek,
                dayOfMonth = dayOfMonth,
                householdId = user.householdId,
                points = points
            )
            _statusMessage.value = "Chore assigned to ${assignedUser.name}"
        }
    }

    fun toggleChoreCompletion(chore: ChoreTask) {
        viewModelScope.launch {
            val newStatus = if (chore.status == "COMPLETED") "PENDING" else "COMPLETED"
            repository.updateChoreStatus(chore.id, newStatus)

            // If Sunday-to-Sunday weekly rotating chore was completed, advance rotation
            if (newStatus == "COMPLETED" && chore.frequency == "WEEKLY_SUNDAY_ROTATION") {
                val members = householdMembers.value
                repository.advanceChoreRotation(chore, members)
                _statusMessage.value = "Sunday chore completed! Shift rotated to the next roommate."
            } else {
                _statusMessage.value = if (newStatus == "COMPLETED") "Chore marked completed! (+${chore.points} pts)" else "Chore marked pending"
            }
        }
    }

    fun sendChoreNotificationAlert(chore: ChoreTask) {
        ChoreNotificationHelper.showChoreReminder(
            context,
            chore.id.hashCode(),
            "🧹 Chore Alert: ${chore.title}",
            "${chore.assignedToUserName}, it's your turn for ${chore.title} (${chore.scheduledTime})"
        )
        _statusMessage.value = "Notification reminder sent to ${chore.assignedToUserName}"
    }

    fun deleteChore(choreId: String) {
        viewModelScope.launch {
            repository.deleteChore(choreId)
            _statusMessage.value = "Chore task removed"
        }
    }

    // --- Settlement Actions ---
    fun addSettlementDebt(
        fromUser: UserProfile,
        toUser: UserProfile,
        amount: Double,
        reason: String
    ) {
        viewModelScope.launch {
            val user = currentUser.value ?: return@launch
            repository.addDebt(fromUser, toUser, amount, reason, user.householdId)
            _statusMessage.value = "Settlement debt of ₹$amount recorded"
        }
    }

    fun verifyDebtPayment(debt: SettlementDebt, isVerified: Boolean) {
        viewModelScope.launch {
            val newStatus = if (isVerified) "VERIFIED" else "PENDING"
            repository.updateDebtStatus(debt.id, newStatus)
            _statusMessage.value = if (isVerified) "Payment verified & settled!" else "Payment marked pending"
        }
    }

    fun requestDebtVerification(debt: SettlementDebt, txRef: String) {
        viewModelScope.launch {
            repository.updateDebtStatus(debt.id, "PAID_PENDING_CONFIRMATION", txRef)
            _statusMessage.value = "Payment submitted. Awaiting verification from ${debt.toUserName}."
        }
    }

    fun payViaUpi(debt: SettlementDebt, preferredApp: String? = null) {
        val success = UpiPaymentHelper.launchUpiPayment(
            context = context,
            payeeUpiId = debt.toUserUpiId.ifBlank { "roommate@upi" },
            payeeName = debt.toUserName,
            amount = debt.amount,
            note = debt.reason,
            preferredApp = preferredApp
        )
        if (success) {
            _statusMessage.value = "Opening UPI Payment for ₹${debt.amount}..."
        }
    }

    fun sendDebtReminder(debt: SettlementDebt) {
        ChoreNotificationHelper.showSettlementAlert(
            context,
            debt.id.hashCode(),
            "💸 Money Settlement Reminder",
            "Reminder: ${debt.fromUserName}, please settle ₹${debt.amount} to ${debt.toUserName} (UPI: ${debt.toUserUpiId.ifBlank { "N/A" }})"
        )
        _statusMessage.value = "Reminder alert sent to ${debt.fromUserName}"
    }

    // --- Savings Goals ---
    fun addSavingsGoal(
        title: String,
        targetAmount: Double,
        initialAmount: Double,
        category: String,
        colorHex: String
    ) {
        viewModelScope.launch {
            val user = currentUser.value ?: return@launch
            repository.addSavingsGoal(
                title = title,
                targetAmount = targetAmount,
                initialAmount = initialAmount,
                category = category,
                householdId = user.householdId,
                colorHex = colorHex
            )
            _statusMessage.value = "Savings fund goal created: $title"
        }
    }

    fun updateSavingsProgress(goal: SavingsGoal, deltaAmount: Double) {
        viewModelScope.launch {
            val newAmount = (goal.currentAmount + deltaAmount).coerceAtLeast(0.0)
            repository.updateSavingsAmount(goal.id, newAmount)
            _statusMessage.value = "Savings updated: ₹$newAmount / ₹${goal.targetAmount}"
        }
    }

    fun deleteSavingsGoal(goalId: String) {
        viewModelScope.launch {
            repository.deleteSavingsGoal(goalId)
            _statusMessage.value = "Savings goal deleted"
        }
    }

    // --- Budget Limits ---
    fun updateMonthlyBudget(newLimit: Double, thresholdPercent: Int) {
        viewModelScope.launch {
            val user = currentUser.value ?: return@launch
            val curMonth = DateUtils.getCurrentMonthYearKey()
            val config = BudgetConfig(
                monthYearKey = curMonth,
                householdId = user.householdId,
                totalBudgetLimit = newLimit,
                alertThresholdPercent = thresholdPercent
            )
            repository.updateBudgetConfig(config)
            _statusMessage.value = "Budget limit set to ₹$newLimit (Alert at $thresholdPercent%)"
        }
    }

    // --- Roommates & Household ---
    fun addVirtualRoommate(name: String, email: String, upiId: String, colorHex: String) {
        viewModelScope.launch {
            val user = currentUser.value ?: return@launch
            repository.addRoommate(name, email, upiId, user.householdId, colorHex)
            _statusMessage.value = "Roommate $name added to household"
        }
    }

    fun updateMyProfile(name: String, upiId: String, email: String) {
        viewModelScope.launch {
            val user = currentUser.value ?: return@launch
            val updated = user.copy(name = name.trim(), upiId = upiId.trim(), email = email.trim())
            repository.updateUserProfile(updated)
            _statusMessage.value = "Profile & UPI details updated"
        }
    }

    fun switchOrJoinHousehold(code: String, householdName: String) {
        viewModelScope.launch {
            val user = currentUser.value ?: return@launch
            repository.switchHousehold(code, householdName, user)
            _statusMessage.value = "Joined Household $code"
            // Pull cloud data for new household
            repository.syncManager.pullFromCloud(repository.dao, "HOUSE_${code.uppercase().trim()}")
        }
    }

    // --- Supabase Authentication ---

    fun signUpWithSupabase(
        email: String,
        password: String,
        fullName: String,
        upiId: String,
        householdCode: String,
        householdName: String,
        onResult: (Boolean, String) -> Unit
    ) {
        viewModelScope.launch {
            _syncingState.value = true
            val url = repository.syncManager.supabaseUrl
            val anonKey = repository.syncManager.supabaseAnonKey
            val result = repository.syncManager.authManager.signUp(
                baseUrl = url,
                anonKey = anonKey,
                email = email,
                password = password,
                fullName = fullName,
                upiId = upiId,
                householdCode = householdCode,
                householdName = householdName
            )
            _syncingState.value = false
            result.fold(
                onSuccess = { user ->
                    repository.onSupabaseAuthSuccess(user)
                    sessionPrefs.edit().putBoolean("is_logged_in", true).apply()
                    _isLoggedIn.value = true
                    _statusMessage.value = "Welcome ${user.fullName}! Supabase Account Created & Synced."
                    onResult(true, "Account created successfully!")
                },
                onFailure = { err ->
                    val msg = err.localizedMessage ?: "Sign up failed"
                    _statusMessage.value = "Sign Up Error: $msg"
                    onResult(false, msg)
                }
            )
        }
    }

    fun signInWithSupabase(
        email: String,
        password: String,
        onResult: (Boolean, String) -> Unit
    ) {
        viewModelScope.launch {
            _syncingState.value = true
            val url = repository.syncManager.supabaseUrl
            val anonKey = repository.syncManager.supabaseAnonKey
            val result = repository.syncManager.authManager.signIn(
                baseUrl = url,
                anonKey = anonKey,
                email = email,
                password = password
            )
            _syncingState.value = false
            result.fold(
                onSuccess = { user ->
                    repository.onSupabaseAuthSuccess(user)
                    sessionPrefs.edit().putBoolean("is_logged_in", true).apply()
                    _isLoggedIn.value = true
                    _statusMessage.value = "Logged in as ${user.email} (Supabase Cloud Synced)"
                    onResult(true, "Signed in successfully!")
                },
                onFailure = { err ->
                    val msg = err.localizedMessage ?: "Authentication failed"
                    _statusMessage.value = "Login Error: $msg"
                    onResult(false, msg)
                }
            )
        }
    }

    fun signOutSupabase() {
        viewModelScope.launch {
            val url = repository.syncManager.supabaseUrl
            val anonKey = repository.syncManager.supabaseAnonKey
            repository.syncManager.authManager.signOut(url, anonKey)
            _statusMessage.value = "Signed out of Supabase. Running in local mode."
        }
    }

    fun resetSupabasePassword(email: String, onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch {
            val url = repository.syncManager.supabaseUrl
            val anonKey = repository.syncManager.supabaseAnonKey
            val result = repository.syncManager.authManager.resetPassword(url, anonKey, email)
            result.fold(
                onSuccess = { msg ->
                    _statusMessage.value = msg
                    onResult(true, msg)
                },
                onFailure = { err ->
                    val msg = err.localizedMessage ?: "Failed to send reset link"
                    _statusMessage.value = msg
                    onResult(false, msg)
                }
            )
        }
    }

    // --- Supabase Cloud Data Store Sync ---

    fun syncAllWithSupabase() {
        viewModelScope.launch {
            _syncingState.value = true
            val user = currentUser.value
            val hid = user?.householdId ?: "HOUSE_FLAT_402"

            val pushOk = repository.syncManager.pushAllToCloud(repository.dao, hid)
            val pullRes = repository.syncManager.pullFromCloud(repository.dao, hid)

            _syncingState.value = false
            if (pushOk || pullRes.isSuccess) {
                val pulledCount = pullRes.getOrNull() ?: 0
                _statusMessage.value = "Synced with Supabase Cloud! ($pulledCount records updated)"
            } else {
                _statusMessage.value = "Saved offline in local Room database (Supabase cloud queued)"
            }
        }
    }

    fun pullFromSupabase() {
        viewModelScope.launch {
            _syncingState.value = true
            val user = currentUser.value
            val hid = user?.householdId ?: "HOUSE_FLAT_402"
            val res = repository.syncManager.pullFromCloud(repository.dao, hid)
            _syncingState.value = false
            res.fold(
                onSuccess = { count ->
                    _statusMessage.value = "Successfully pulled $count records from Supabase!"
                },
                onFailure = { err ->
                    _statusMessage.value = "Pull failed: ${err.localizedMessage}"
                }
            )
        }
    }

    fun testSupabaseConnection(onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch {
            val res = repository.syncManager.testConnection()
            onResult(res.first, res.second)
        }
    }

    // --- Aliases and helper bridges for UI ---
    val currentHousehold: StateFlow<Household?> get() = household
    val allDebts: StateFlow<List<SettlementDebt>> get() = settlementDebts
    val isSyncing: StateFlow<Boolean> get() = syncingState
    val syncManager get() = repository.syncManager

    fun setTab(tab: ScreenTab) = selectTab(tab)
    fun toggleChoreStatus(chore: ChoreTask) = toggleChoreCompletion(chore)
    fun rotateSundayChore(chore: ChoreTask) {
        viewModelScope.launch {
            repository.advanceChoreRotation(chore, householdMembers.value)
        }
    }
    fun sendChoreReminder(chore: ChoreTask) = sendChoreNotificationAlert(chore)
    fun updateBudget(newLimit: Double, thresholdPercent: Int) = updateMonthlyBudget(newLimit, thresholdPercent)
    fun verifySettlement(debt: SettlementDebt, isVerified: Boolean) = verifyDebtPayment(debt, isVerified)
    fun markDebtPaidPendingConfirmation(debt: SettlementDebt, txRef: String) = requestDebtVerification(debt, txRef)
    fun deleteDebt(id: String) {
        viewModelScope.launch {
            repository.deleteDebt(id)
            _statusMessage.value = "Debt record deleted"
        }
    }
    fun addDebt(from: UserProfile, to: UserProfile, amount: Double, reason: String) =
        addSettlementDebt(from, to, amount, reason)
    fun depositToSavings(goal: SavingsGoal, delta: Double) = updateSavingsProgress(goal, delta)
    fun updateUserProfile(name: String, upi: String, email: String) = updateMyProfile(name, upi, email)
    fun addRoommate(name: String, email: String, upi: String, color: String) = addVirtualRoommate(name, email, upi, color)
    fun joinHousehold(code: String, name: String) = switchOrJoinHousehold(code, name)
    fun syncWithSupabase() = syncAllWithSupabase()

    fun clearSampleDataAndSetupOriginalHousehold(
        userName: String,
        userEmail: String,
        userUpi: String,
        householdName: String,
        householdCode: String,
        monthlyBudget: Double = 30000.0,
        onComplete: (() -> Unit)? = null
    ) {
        viewModelScope.launch {
            _syncingState.value = true
            repository.clearSampleDataAndSetupOriginalHousehold(
                userName = userName,
                userEmail = userEmail,
                userUpi = userUpi,
                householdName = householdName,
                householdCode = householdCode,
                monthlyBudget = monthlyBudget
            )
            sessionPrefs.edit().putBoolean("is_logged_in", true).apply()
            _isLoggedIn.value = true
            _syncingState.value = false
            _statusMessage.value = "Sample data cleared! Ready with your original household."
            onComplete?.invoke()
        }
    }

    fun getUpiPaymentUri(debt: SettlementDebt): String {
        return UpiPaymentHelper.buildUpiUri(
            payeeUpiId = debt.toUserUpiId.ifBlank { "upi@placeholder" },
            payeeName = debt.toUserName,
            amount = debt.amount,
            note = debt.reason
        ).toString()
    }
}

class RoomieViewModelFactory(
    private val application: Application,
    private val repository: RoomieRepository? = null
) : androidx.lifecycle.ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(RoomieViewModel::class.java)) {
            return RoomieViewModel(application) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
