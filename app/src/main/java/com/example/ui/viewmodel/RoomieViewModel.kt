package com.example.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.BuildConfig
import com.example.data.local.model.BudgetConfig
import com.example.data.local.model.ChoreTask
import com.example.data.local.model.ExpenseItem
import com.example.data.local.model.Household
import com.example.data.local.model.HouseholdNotification
import com.example.data.local.model.SavingsGoal
import com.example.data.local.model.SettlementDebt
import com.example.data.local.model.UserProfile
import com.example.data.remote.SupabaseAuthState
import com.example.data.remote.SupabaseUser
import com.example.data.repository.RoomieRepository
import com.example.notification.ChoreNotificationHelper
import com.example.util.AppUpdateInfo
import com.example.util.AppUpdateManager
import com.example.util.DateUtils
import com.example.util.UpiPaymentHelper
import com.example.util.NetworkConnectivityObserver
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
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
    val remainingBudget: Double,
    val warningThreshold: Int = 80
)

@OptIn(ExperimentalCoroutinesApi::class)
class RoomieViewModel(application: Application) : AndroidViewModel(application) {
    val repository = RoomieRepository.getInstance(application)
    private val context = application.applicationContext
    val connectivityObserver = NetworkConnectivityObserver(application)
    val appUpdateManager = AppUpdateManager(application.applicationContext)

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

    private val _syncProgressMessage = MutableStateFlow<String?>(null)
    val syncProgressMessage: StateFlow<String?> = _syncProgressMessage.asStateFlow()

    private val _statusMessage = MutableStateFlow<String?>(null)
    val statusMessage: StateFlow<String?> = _statusMessage.asStateFlow()

    private val _appUpdateInfo = MutableStateFlow<AppUpdateInfo?>(null)
    val appUpdateInfo: StateFlow<AppUpdateInfo?> = _appUpdateInfo.asStateFlow()

    val authState: StateFlow<SupabaseAuthState> = repository.syncManager.authManager.authState

    val currentUser: StateFlow<UserProfile?> = repository.currentUser
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val householdMembers: StateFlow<List<UserProfile>> = currentUser
        .flatMapLatest { user ->
            val hid = user?.householdId ?: ""
            if (hid.isNotBlank()) repository.getHouseholdMembers(hid) else flowOf(emptyList())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val household: StateFlow<Household?> = currentUser
        .flatMapLatest { user ->
            val hid = user?.householdId ?: ""
            if (hid.isNotBlank()) repository.getHousehold(hid) else flowOf(null)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val allExpenses: StateFlow<List<ExpenseItem>> = currentUser
        .flatMapLatest { user ->
            val hid = user?.householdId ?: ""
            val uid = user?.id ?: ""
            if (hid.isNotBlank()) repository.getAllExpenses(hid, uid) else flowOf(emptyList())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allChores: StateFlow<List<ChoreTask>> = currentUser
        .flatMapLatest { user ->
            val hid = user?.householdId ?: ""
            if (hid.isNotBlank()) repository.getAllChores(hid) else flowOf(emptyList())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val settlementDebts: StateFlow<List<SettlementDebt>> = currentUser
        .flatMapLatest { user ->
            val hid = user?.householdId ?: ""
            if (hid.isNotBlank()) repository.getSettlementDebts(hid) else flowOf(emptyList())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val savingsGoals: StateFlow<List<SavingsGoal>> = currentUser
        .flatMapLatest { user ->
            val hid = user?.householdId ?: ""
            if (hid.isNotBlank()) repository.getSavingsGoals(hid) else flowOf(emptyList())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val currentBudgetConfig: StateFlow<BudgetConfig?> = currentUser
        .flatMapLatest { user ->
            val hid = user?.householdId ?: ""
            val curMonth = DateUtils.getCurrentMonthYearKey()
            if (hid.isNotBlank()) repository.getBudgetConfig(curMonth, hid) else flowOf(null)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val notifications: StateFlow<List<HouseholdNotification>> = currentUser
        .flatMapLatest { user ->
            val hid = user?.householdId ?: ""
            val uid = user?.id ?: ""
            if (hid.isNotBlank() && uid.isNotBlank()) repository.getNotificationsForUser(hid, uid) else flowOf(emptyList())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val unreadNotificationsCount: StateFlow<Int> = currentUser
        .flatMapLatest { user ->
            val hid = user?.householdId ?: ""
            val uid = user?.id ?: ""
            if (hid.isNotBlank() && uid.isNotBlank()) repository.getUnreadNotificationsCount(hid, uid) else flowOf(0)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val budgetStatus: StateFlow<BudgetStatus> = combine(allExpenses, currentBudgetConfig, household) { expenses, budgetConfig, householdObj ->
        val curMonth = DateUtils.getCurrentMonthYearKey()
        val monthExpenses = expenses.filter { it.monthYearKey == curMonth }
        val totalSpent = monthExpenses.sumOf { it.amount }
        val limit = budgetConfig?.totalBudgetLimit ?: householdObj?.monthlyBudgetLimit ?: 0.0
        val threshold = budgetConfig?.alertThresholdPercent ?: householdObj?.budgetWarningThreshold ?: 80
        val percent = if (limit > 0) ((totalSpent / limit) * 100).toInt() else 0
        val isWarning = limit > 0 && percent >= threshold && percent <= 100
        val isOver = limit > 0 && percent > 100

        BudgetStatus(
            totalSpent = totalSpent,
            budgetLimit = limit,
            percentUsed = percent,
            isWarning = isWarning,
            isOverBudget = isOver,
            remainingBudget = if (limit > 0) (limit - totalSpent).coerceAtLeast(0.0) else 0.0,
            warningThreshold = threshold
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        BudgetStatus(0.0, 0.0, 0, false, false, 0.0, 80)
    )

    init {
        ChoreNotificationHelper.createNotificationChannels(context)
        viewModelScope.launch {
            repository.purgeSampleMockDataIfPresent()
        }

        // Automatic realtime sync when network is connected / reconnected
        viewModelScope.launch {
            connectivityObserver.isOnline.collect { online ->
                if (online && isLoggedIn.value) {
                    syncWithSupabase()
                }
            }
        }

        // Check for app updates from GitHub on launch
        viewModelScope.launch {
            checkForAppUpdate(isManual = false)
        }
    }

    fun checkForAppUpdate(isManual: Boolean = false) {
        viewModelScope.launch {
            try {
                val info = appUpdateManager.checkForUpdate()
                if (info != null && info.hasUpdate) {
                    _appUpdateInfo.value = info
                } else if (isManual) {
                    _statusMessage.value = "You're on the latest version (v${BuildConfig.VERSION_NAME})"
                }
            } catch (e: Exception) {
                if (isManual) {
                    _statusMessage.value = "Failed to check update: ${e.message}"
                }
            }
        }
    }

    fun dismissUpdateDialog() {
        _appUpdateInfo.value = null
    }

    fun launchAppUpdate() {
        _appUpdateInfo.value?.let { info ->
            appUpdateManager.launchUpdateDownload(info)
        }
    }

    fun regenerateHouseholdCode() {
        viewModelScope.launch {
            val hid = currentHousehold.value?.id ?: currentUser.value?.householdId ?: return@launch
            val newCode = repository.regenerateHouseholdInviteCode(hid)
            _statusMessage.value = "New Invite Code generated: $newCode"
        }
    }

    fun updateHouseholdAndBudget(name: String, budget: Double, threshold: Int = 80) {
        viewModelScope.launch {
            val hid = currentHousehold.value?.id ?: currentUser.value?.householdId ?: return@launch
            repository.updateHouseholdDetailsAndBudget(hid, name, budget, threshold)
            _statusMessage.value = "Household and monthly budget updated!"
        }
    }

    fun logout() {
        viewModelScope.launch {
            sessionPrefs.edit().clear().apply()
            signOutSupabase()
            _isLoggedIn.value = false
            _currentTab.value = ScreenTab.DASHBOARD
        }
    }

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
        splitWithMembers: List<UserProfile> = emptyList(),
        notes: String,
        dateMillis: Long = System.currentTimeMillis()
    ) {
        viewModelScope.launch {
            val user = currentUser.value
            val hid = user?.householdId?.ifBlank { null } ?: paidBy.householdId.ifBlank { "HOUSE_HOME925" }
            val effectiveMembers = if (splitType == "EQUAL") householdMembers.value else splitWithMembers
            repository.addExpense(
                title = title,
                amount = amount,
                category = category,
                paidBy = paidBy,
                householdId = hid,
                splitType = splitType,
                members = effectiveMembers,
                notes = notes,
                dateMillis = dateMillis
            )
            _statusMessage.value = "Added expense of ₹${amount.toInt()} for $title"
            if (repository.syncManager.isOnline()) {
                viewModelScope.launch {
                    val rep = repository.reconcileWithCloud(hid)
                    if (rep.success && rep.uploadedCount > 0) {
                        _statusMessage.value = "Expense saved locally & synced to Supabase Cloud! (${rep.uploadedCount} uploaded)"
                    }
                }
            }

            // Check budget alert
            val budget = currentBudgetConfig.value
            val limit = budget?.totalBudgetLimit ?: household.value?.monthlyBudgetLimit ?: 0.0
            if (limit > 0) {
                val curMonth = DateUtils.getCurrentMonthYearKey()
                val curTotal = allExpenses.value.filter { it.monthYearKey == curMonth }.sumOf { it.amount } + amount
                val percent = ((curTotal / limit) * 100).toInt()
                val threshold = budget?.alertThresholdPercent ?: household.value?.budgetWarningThreshold ?: 80

                if (percent >= 100) {
                    ChoreNotificationHelper.showBudgetAlert(
                        context,
                        1001,
                        "🚨 Monthly Budget Exceeded!",
                        "Warning: Total spending reached ₹${curTotal.toInt()} which exceeds the ₹${limit.toInt()} limit ($percent%)!"
                    )
                } else if (percent >= threshold) {
                    ChoreNotificationHelper.showBudgetAlert(
                        context,
                        1002,
                        "⚠️ Approaching Monthly Budget Limit",
                        "Caution: You have used $percent% of your ₹${limit.toInt()} monthly budget."
                    )
                }
            }
        }
    }

    fun updateExpense(
        expense: ExpenseItem,
        splitWithMembers: List<UserProfile> = emptyList()
    ) {
        viewModelScope.launch {
            val effectiveMembers = if (expense.splitType == "EQUAL") householdMembers.value else splitWithMembers
            repository.updateExpense(expense, effectiveMembers)
            _statusMessage.value = "Updated expense: ${expense.title}"
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

    fun updateChore(chore: ChoreTask) {
        viewModelScope.launch {
            repository.updateChore(chore)
            _statusMessage.value = "Updated chore: ${chore.title}"
        }
    }

    fun toggleChoreCompletion(chore: ChoreTask) {
        viewModelScope.launch {
            val newStatus = if (chore.status == "COMPLETED") "PENDING" else "COMPLETED"
            repository.updateChoreStatus(chore.id, newStatus)

            // If rotating chore (Daily rotation or Sunday rotation) was completed, advance rotation
            if (newStatus == "COMPLETED" && (chore.rotationMemberIds.isNotBlank() || chore.frequency.contains("ROTATION"))) {
                val members = householdMembers.value
                repository.advanceChoreRotation(chore, members)
                _statusMessage.value = "Chore completed! Duty rotated to next roommate in line."
            } else {
                _statusMessage.value = if (newStatus == "COMPLETED") "Chore marked completed! (+${chore.points} pts)" else "Chore marked pending"
            }
        }
    }

    fun sendChoreNotificationAlert(chore: ChoreTask) {
        val user = currentUser.value
        if (user != null && chore.assignedToUserId.isNotBlank()) {
            viewModelScope.launch {
                repository.createAndDispatchNotification(
                    householdId = chore.householdId.ifBlank { user.householdId },
                    sender = user,
                    targetUserId = chore.assignedToUserId,
                    targetUserName = chore.assignedToUserName,
                    type = "CHORE_REMINDER",
                    title = "🧹 Chore Alert: ${chore.title}",
                    message = "${user.name} reminded you about today's chore '${chore.title}' scheduled for ${chore.scheduledTime}.",
                    relatedEntityId = chore.id
                )
            }
        }
        ChoreNotificationHelper.showChoreReminder(
            context,
            chore.id.hashCode(),
            "🧹 Chore Alert Sent: ${chore.title}",
            "Nudge dispatched to ${chore.assignedToUserName} for ${chore.scheduledTime}."
        )
        _statusMessage.value = "🔔 Reminder alert dispatched to ${chore.assignedToUserName}!"
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
            val user = currentUser.value ?: repository.dao.getCurrentUserDirect()
            val hid = user?.householdId?.takeIf { it.isNotBlank() }
                ?: fromUser.householdId.takeIf { it.isNotBlank() }
                ?: toUser.householdId.takeIf { it.isNotBlank() }
                ?: "HOUSE_FLAT_402"
            val finalReason = reason.trim().ifBlank { "Direct loan / split settlement" }
            repository.addDebt(fromUser, toUser, amount, finalReason, hid)

            // If lender recorded debt for borrower, notify borrower
            if (user != null && fromUser.id != user.id) {
                repository.createAndDispatchNotification(
                    householdId = hid,
                    sender = user,
                    targetUserId = fromUser.id,
                    targetUserName = fromUser.name,
                    type = "DEBT_REMINDER",
                    title = "💸 New Split / Debt Added",
                    message = "${user.name} added ₹$amount for '$finalReason'.",
                    relatedEntityId = ""
                )
            }
            _statusMessage.value = "Recorded debt: ${fromUser.name} owes ₹$amount to ${toUser.name}"
        }
    }

    fun verifyDebtPayment(debt: SettlementDebt, isVerified: Boolean) {
        viewModelScope.launch {
            val newStatus = if (isVerified) "VERIFIED" else "PENDING"
            repository.updateDebtStatus(debt.id, newStatus)
            val user = currentUser.value
            if (isVerified && user != null && debt.fromUserId.isNotBlank()) {
                repository.createAndDispatchNotification(
                    householdId = debt.householdId.ifBlank { user.householdId },
                    sender = user,
                    targetUserId = debt.fromUserId,
                    targetUserName = debt.fromUserName,
                    type = "PAYMENT_VERIFIED",
                    title = "✅ Payment Verified & Settled!",
                    message = "${user.name} confirmed your payment of ₹${debt.amount} for '${debt.reason}'.",
                    relatedEntityId = debt.id
                )
            }
            _statusMessage.value = if (isVerified) "Payment verified & settled!" else "Payment marked pending"
        }
    }

    fun requestDebtVerification(debt: SettlementDebt, txRef: String) {
        viewModelScope.launch {
            repository.updateDebtStatus(debt.id, "PAID_PENDING_CONFIRMATION", txRef)
            val user = currentUser.value
            if (user != null && debt.toUserId.isNotBlank()) {
                repository.createAndDispatchNotification(
                    householdId = debt.householdId.ifBlank { user.householdId },
                    sender = user,
                    targetUserId = debt.toUserId,
                    targetUserName = debt.toUserName,
                    type = "PAYMENT_PENDING_CONFIRMATION",
                    title = "💵 Payment Sent by ${user.name}",
                    message = "${user.name} sent ₹${debt.amount} for '${debt.reason}' (Ref: ${txRef.ifBlank { "UPI" }}). Tap to verify & settle.",
                    relatedEntityId = debt.id
                )
            }
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
        val user = currentUser.value
        if (user != null && debt.fromUserId.isNotBlank()) {
            viewModelScope.launch {
                repository.createAndDispatchNotification(
                    householdId = debt.householdId.ifBlank { user.householdId },
                    sender = user,
                    targetUserId = debt.fromUserId,
                    targetUserName = debt.fromUserName,
                    type = "DEBT_REMINDER",
                    title = "💸 UPI Settlement Reminder from ${user.name}",
                    message = "Reminder to pay ₹${debt.amount} for '${debt.reason}' via UPI to ${debt.toUserName} (${debt.toUserUpiId.ifBlank { "UPI" }}).",
                    relatedEntityId = debt.id
                )
            }
        }
        ChoreNotificationHelper.showSettlementAlert(
            context,
            debt.id.hashCode(),
            "💸 Money Settlement Reminder Sent",
            "Nudge dispatched to ${debt.fromUserName} for ₹${debt.amount}."
        )
        _statusMessage.value = "🔔 Payment reminder sent to ${debt.fromUserName}!"
    }

    fun markNotificationAsRead(id: String) {
        viewModelScope.launch {
            repository.markNotificationAsRead(id)
        }
    }

    fun markAllNotificationsAsRead() {
        viewModelScope.launch {
            val user = currentUser.value ?: return@launch
            repository.markAllNotificationsAsRead(user.householdId, user.id)
            _statusMessage.value = "All notifications marked as read"
        }
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
            val household = repository.dao.getHouseholdDirect(user.householdId)
            val hName = household?.name ?: user.householdName
            repository.updateHouseholdDetailsAndBudget(user.householdId, hName, newLimit, thresholdPercent)
            _statusMessage.value = "Budget limit set to ₹${newLimit.toInt()} (Alert at $thresholdPercent%)"
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

    fun updateRoommate(user: UserProfile) {
        viewModelScope.launch {
            repository.updateRoommate(user)
            _statusMessage.value = "Updated details for ${user.name}"
        }
    }

    fun deleteRoommate(userId: String, userName: String) {
        viewModelScope.launch {
            repository.deleteRoommate(userId)
            _statusMessage.value = "Removed $userName from household"
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

    fun resendConfirmationEmail(email: String, onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch {
            val url = repository.syncManager.supabaseUrl
            val anonKey = repository.syncManager.supabaseAnonKey
            val result = repository.syncManager.authManager.resendConfirmationEmail(url, anonKey, email)
            result.fold(
                onSuccess = { msg ->
                    _statusMessage.value = msg
                    onResult(true, msg)
                },
                onFailure = { err ->
                    val msg = err.localizedMessage ?: "Failed to resend confirmation email"
                    _statusMessage.value = msg
                    onResult(false, msg)
                }
            )
        }
    }

    /**
     * Enter app immediately in Local / Offline Mode without requiring cloud connection.
     * All SQLite/Room data, splits, chore rotations, receipts, and UPI QR codes remain 100% operational offline.
     */
    fun loginAsLocal(
        name: String = "Roommate",
        householdName: String = "My Household",
        householdCode: String = "FLAT402",
        upiId: String = ""
    ) {
        viewModelScope.launch {
            val existingUser = repository.dao.getCurrentUserDirect()
            if (existingUser != null) {
                sessionPrefs.edit().putBoolean("is_logged_in", true).apply()
                _isLoggedIn.value = true
                _statusMessage.value = "Welcome back ${existingUser.name}! (Local Mode)"
            } else {
                val cleanName = name.trim().ifBlank { "Roommate" }
                val cleanHName = householdName.trim().ifBlank { "My Household" }
                val cleanCode = householdCode.trim().uppercase().ifBlank { "FLAT402" }
                val cleanUpi = upiId.trim()

                repository.clearSampleDataAndSetupOriginalHousehold(
                    userName = cleanName,
                    userEmail = "${cleanName.lowercase().replace(" ", "").ifBlank { "user" }}@local.device",
                    userUpi = cleanUpi,
                    householdName = cleanHName,
                    householdCode = cleanCode
                )
                sessionPrefs.edit().putBoolean("is_logged_in", true).apply()
                _isLoggedIn.value = true
                _statusMessage.value = "Welcome $cleanName! Running in Local / Offline Mode."
            }
        }
    }

    fun updateSupabaseConfig(url: String, key: String) {
        repository.syncManager.supabaseUrl = url.trim()
        repository.syncManager.supabaseAnonKey = key.trim()
        _statusMessage.value = "Cloud settings updated."
    }

    // --- Supabase Cloud Data Store Sync ---

    fun syncAllWithSupabase() {
        viewModelScope.launch {
            _syncingState.value = true
            val user = currentUser.value
            val hid = user?.householdId ?: "HOUSE_HOME925"

            _syncProgressMessage.value = "Checking connection with Supabase..."
            _statusMessage.value = "Checking connection with Supabase..."

            val report = repository.reconcileWithCloud(hid) { stage, _ ->
                _syncProgressMessage.value = stage
                _statusMessage.value = stage
            }

            _syncingState.value = false
            _syncProgressMessage.value = null
            _statusMessage.value = report.summaryMessage
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
