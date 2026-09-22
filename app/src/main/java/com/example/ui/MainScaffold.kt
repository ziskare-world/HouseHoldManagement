package com.example.ui

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Payment
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.model.ChoreTask
import com.example.data.local.model.HouseholdNotification
import com.example.data.local.model.UserProfile
import com.example.ui.components.AppUpdateDialog
import com.example.util.AppUpdateInfo
import com.example.ui.screens.ChoresScreen
import com.example.ui.screens.DashboardScreen
import com.example.ui.screens.ExpensesScreen
import com.example.ui.screens.HouseholdProfileScreen
import com.example.ui.screens.LoginScreen
import com.example.ui.screens.SettlementsScreen
import com.example.ui.viewmodel.RoomieViewModel
import com.example.ui.viewmodel.ScreenTab
import kotlinx.coroutines.launch

data class NavigationItem(
    val tab: ScreenTab,
    val title: String,
    val icon: ImageVector,
    val badgeCount: Int = 0
)

@Composable
fun MainScaffold(
    viewModel: RoomieViewModel,
    onLaunchUpiPayment: (debt: com.example.data.local.model.SettlementDebt, packageName: String?) -> Unit
) {
    val isLoggedIn by viewModel.isLoggedIn.collectAsState()
    val currentTab by viewModel.currentTab.collectAsState()
    val currentUser by viewModel.currentUser.collectAsState()
    val household by viewModel.currentHousehold.collectAsState()
    val householdMembers by viewModel.householdMembers.collectAsState()
    val allExpenses by viewModel.allExpenses.collectAsState()
    val allChores by viewModel.allChores.collectAsState()
    val allDebts by viewModel.allDebts.collectAsState()
    val savingsGoals by viewModel.savingsGoals.collectAsState()
    val budgetStatus by viewModel.budgetStatus.collectAsState()
    val isSyncing by viewModel.isSyncing.collectAsState()
    val selectedMonthKey by viewModel.selectedMonthKey.collectAsState()
    val appUpdateInfo by viewModel.appUpdateInfo.collectAsState()
    val notifications by viewModel.notifications.collectAsState()
    val unreadNotificationsCount by viewModel.unreadNotificationsCount.collectAsState()

    val pendingChoresCount = remember(allChores) {
        allChores.count { it.status == "PENDING" }
    }
    val pendingDebtsCount = remember(allDebts, currentUser) {
        allDebts.count { it.fromUserId == currentUser?.id && it.status != "VERIFIED" }
    }

    // 5 Clean, Focused Navigation Tabs
    val navItems = listOf(
        NavigationItem(ScreenTab.DASHBOARD, "Dashboard", Icons.Default.Dashboard),
        NavigationItem(ScreenTab.EXPENSES, "Expenses", Icons.Default.ReceiptLong),
        NavigationItem(ScreenTab.CHORES, "Chores", Icons.Default.CleaningServices, badgeCount = pendingChoresCount),
        NavigationItem(ScreenTab.SETTLEMENTS, "Splits & UPI", Icons.Default.Payment, badgeCount = pendingDebtsCount),
        NavigationItem(ScreenTab.HOUSEHOLD, "Profile", Icons.Default.Person)
    )

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    Crossfade(targetState = isLoggedIn, label = "AuthGateTransition") { loggedIn ->
        if (!loggedIn) {
            // First Screen on Launch: Secure & Modern Login Page
            LoginScreen(
                onSignIn = { email, pass, cb ->
                    viewModel.signInWithSupabase(email, pass, cb)
                },
                onSignUp = { email, pass, name, upi, code, hName, cb ->
                    viewModel.signUpWithSupabase(email, pass, name, upi, code, hName, cb)
                },
                onForgotPassword = { email, cb ->
                    viewModel.resetSupabasePassword(email, cb)
                },
                onContinueOffline = { name, hName, code, upi ->
                    viewModel.loginAsLocal(name, hName, code, upi)
                },
                currentSupabaseUrl = viewModel.repository.syncManager.supabaseUrl,
                currentSupabaseKey = viewModel.repository.syncManager.supabaseAnonKey,
                onUpdateSupabaseConfig = { url, key ->
                    viewModel.updateSupabaseConfig(url, key)
                }
            )
        } else {
            // Authenticated: Dashboard & All Features
            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                val isWideScreen = maxWidth > 680.dp

                if (isWideScreen) {
                    // Desktop / Tablet layout: NavigationRail on the left + Content area
                    Row(modifier = Modifier.fillMaxSize()) {
                        NavigationRail(
                            modifier = Modifier.fillMaxHeight(),
                            containerColor = MaterialTheme.colorScheme.surface,
                            header = {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier.padding(vertical = 16.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(42.dp)
                                            .clip(CircleShape)
                                            .background(MaterialTheme.colorScheme.primary),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = "RV",
                                            fontWeight = FontWeight.ExtraBold,
                                            color = MaterialTheme.colorScheme.onPrimary,
                                            fontSize = 16.sp
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = "RoomieVault",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        ) {
                            Spacer(modifier = Modifier.height(12.dp))
                            navItems.forEach { item ->
                                val isSelected = currentTab == item.tab
                                NavigationRailItem(
                                    selected = isSelected,
                                    onClick = { viewModel.setTab(item.tab) },
                                    icon = {
                                        if (item.badgeCount > 0) {
                                            BadgedBox(
                                                badge = {
                                                    Badge { Text(item.badgeCount.toString()) }
                                                }
                                            ) {
                                                Icon(imageVector = item.icon, contentDescription = item.title)
                                            }
                                        } else {
                                            Icon(imageVector = item.icon, contentDescription = item.title)
                                        }
                                    },
                                    label = { Text(item.title, fontSize = 11.sp) }
                                )
                            }
                        }

                        // Content Screen
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(MaterialTheme.colorScheme.background)
                        ) {
                            RenderScreen(
                                currentTab = currentTab,
                                viewModel = viewModel,
                                currentUser = currentUser,
                                householdName = household?.name ?: "Roomie Household",
                                household = household,
                                householdMembers = householdMembers,
                                allExpenses = allExpenses,
                                allChores = allChores,
                                allDebts = allDebts,
                                savingsGoals = savingsGoals,
                                budgetStatus = budgetStatus,
                                isSyncing = isSyncing,
                                selectedMonthKey = selectedMonthKey,
                                notifications = notifications,
                                unreadNotificationsCount = unreadNotificationsCount,
                                onLaunchUpiPayment = onLaunchUpiPayment,
                                onShowMessage = { msg ->
                                    scope.launch { snackbarHostState.showSnackbar(msg) }
                                }
                            )
                            SnackbarHost(
                                hostState = snackbarHostState,
                                modifier = Modifier.align(Alignment.BottomCenter)
                            )
                        }
                    }
                } else {
                    // Mobile layout: Standard Bottom Navigation Bar
                    Scaffold(
                        bottomBar = {
                            NavigationBar(
                                containerColor = MaterialTheme.colorScheme.surface,
                                tonalElevation = 6.dp
                            ) {
                                navItems.forEach { item ->
                                    val isSelected = currentTab == item.tab
                                    NavigationBarItem(
                                        selected = isSelected,
                                        onClick = { viewModel.setTab(item.tab) },
                                        icon = {
                                            if (item.badgeCount > 0) {
                                                BadgedBox(
                                                    badge = {
                                                        Badge { Text(item.badgeCount.toString()) }
                                                    }
                                                ) {
                                                    Icon(imageVector = item.icon, contentDescription = item.title)
                                                }
                                            } else {
                                                Icon(imageVector = item.icon, contentDescription = item.title)
                                            }
                                        },
                                        label = { Text(item.title, maxLines = 1, fontSize = 11.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal) }
                                    )
                                }
                            }
                        },
                        snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
                    ) { innerPadding ->
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(innerPadding)
                                .background(MaterialTheme.colorScheme.background)
                        ) {
                            RenderScreen(
                                currentTab = currentTab,
                                viewModel = viewModel,
                                currentUser = currentUser,
                                householdName = household?.name ?: "Roomie Household",
                                household = household,
                                householdMembers = householdMembers,
                                allExpenses = allExpenses,
                                allChores = allChores,
                                allDebts = allDebts,
                                savingsGoals = savingsGoals,
                                budgetStatus = budgetStatus,
                                isSyncing = isSyncing,
                                selectedMonthKey = selectedMonthKey,
                                notifications = notifications,
                                unreadNotificationsCount = unreadNotificationsCount,
                                onLaunchUpiPayment = onLaunchUpiPayment,
                                onShowMessage = { msg ->
                                    scope.launch { snackbarHostState.showSnackbar(msg) }
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    // GitHub In-App Mandatory / Optional Update Dialog (Always visible on top of everything)
    appUpdateInfo?.let { updateInfo ->
        if (updateInfo.hasUpdate) {
            AppUpdateDialog(
                updateInfo = updateInfo,
                onUpdateClick = { viewModel.launchAppUpdate() },
                onDismiss = { viewModel.dismissUpdateDialog() }
            )
        }
    }
}

@Composable
fun RenderScreen(
    currentTab: ScreenTab,
    viewModel: RoomieViewModel,
    currentUser: UserProfile?,
    householdName: String,
    household: com.example.data.local.model.Household?,
    householdMembers: List<UserProfile>,
    allExpenses: List<com.example.data.local.model.ExpenseItem>,
    allChores: List<ChoreTask>,
    allDebts: List<com.example.data.local.model.SettlementDebt>,
    savingsGoals: List<com.example.data.local.model.SavingsGoal>,
    budgetStatus: com.example.ui.viewmodel.BudgetStatus,
    isSyncing: Boolean,
    selectedMonthKey: String,
    notifications: List<HouseholdNotification> = emptyList(),
    unreadNotificationsCount: Int = 0,
    onLaunchUpiPayment: (debt: com.example.data.local.model.SettlementDebt, packageName: String?) -> Unit,
    onShowMessage: (String) -> Unit
) {
    val sundayChore = remember(allChores) {
        allChores.firstOrNull { it.frequency == "WEEKLY_SUNDAY_ROTATION" }
    }
    val todayChores = remember(allChores) {
        allChores.filter { it.status == "PENDING" }
    }

    when (currentTab) {
        ScreenTab.DASHBOARD -> {
            DashboardScreen(
                currentUser = currentUser,
                householdName = householdName,
                budgetStatus = budgetStatus,
                todayChores = todayChores,
                sundayRotationChore = sundayChore,
                pendingDebts = allDebts.filter { it.status != "VERIFIED" },
                expenses = allExpenses,
                notifications = notifications,
                unreadNotificationsCount = unreadNotificationsCount,
                members = householdMembers,
                isSyncing = isSyncing,
                onNavigate = { tab -> viewModel.setTab(tab) },
                onQuickAddExpense = { viewModel.setTab(ScreenTab.EXPENSES) },
                onQuickAddChore = { viewModel.setTab(ScreenTab.CHORES) },
                onQuickGiveMoney = { viewModel.setTab(ScreenTab.SETTLEMENTS) },
                onToggleChore = { chore -> viewModel.toggleChoreStatus(chore) },
                onPayDebt = { debt ->
                    onLaunchUpiPayment(debt, null)
                },
                onTriggerSync = {
                    viewModel.syncWithSupabase()
                    onShowMessage("Triggered cloud sync with Supabase!")
                },
                onRotateSundayChore = { chore ->
                    viewModel.rotateSundayChore(chore)
                    onShowMessage("Rotated Sunday chore duty to next roommate!")
                },
                onSendAlert = { chore ->
                    viewModel.sendChoreReminder(chore)
                    onShowMessage("Push alert sent for '${chore.title}' to ${chore.assignedToUserName}!")
                },
                onSendDebtReminder = { debt ->
                    viewModel.sendDebtReminder(debt)
                    onShowMessage("Payment reminder pushed to ${debt.fromUserName}!")
                },
                onVerifyDebt = { debt, isVerified ->
                    viewModel.verifySettlement(debt, isVerified)
                    onShowMessage(if (isVerified) "Payment verified and settled!" else "Marked as pending")
                },
                onMarkNotificationRead = { id ->
                    viewModel.markNotificationAsRead(id)
                },
                onMarkAllNotificationsRead = {
                    viewModel.markAllNotificationsAsRead()
                }
            )
        }

        ScreenTab.EXPENSES -> {
            ExpensesScreen(
                expenses = allExpenses,
                members = householdMembers,
                currentUser = currentUser,
                budgetStatus = budgetStatus,
                selectedMonthKey = selectedMonthKey,
                onSelectMonth = { month -> viewModel.selectMonth(month) },
                onAddExpense = { title, amount, cat, payer, split, customMembers, notes, dateMillis ->
                    viewModel.addExpense(title, amount, cat, payer, split, customMembers, notes, dateMillis)
                    onShowMessage("Added expense: $title")
                },
                onUpdateExpense = { expense, customMembers ->
                    viewModel.updateExpense(expense, customMembers)
                    onShowMessage("Updated expense: ${expense.title}")
                },
                onDeleteExpense = { id ->
                    viewModel.deleteExpense(id)
                    onShowMessage("Expense deleted")
                },
                onUpdateBudget = { limit, threshold ->
                    viewModel.updateBudget(limit, threshold)
                    onShowMessage("Updated monthly budget limit!")
                }
            )
        }

        ScreenTab.CHORES -> {
            ChoresScreen(
                chores = allChores,
                members = householdMembers,
                currentUser = currentUser,
                onToggleChore = { chore -> viewModel.toggleChoreStatus(chore) },
                onRotateSundayChore = { chore ->
                    viewModel.rotateSundayChore(chore)
                    onShowMessage("Advanced Sunday rotation to next roommate!")
                },
                onSendPushAlert = { chore ->
                    viewModel.sendChoreReminder(chore)
                    onShowMessage("Notification pushed to ${chore.assignedToUserName}!")
                },
                onDeleteChore = { id ->
                    viewModel.deleteChore(id)
                    onShowMessage("Chore deleted")
                },
                onAddChore = { title, desc, cat, freq, user, rotMembers, time, dow, dom, pts ->
                    viewModel.addChore(title, desc, cat, freq, user, rotMembers, time, dow, dom, pts)
                    onShowMessage("Scheduled chore: $title")
                },
                onUpdateChore = { chore ->
                    viewModel.updateChore(chore)
                    onShowMessage("Updated chore: ${chore.title}")
                }
            )
        }

        ScreenTab.SETTLEMENTS -> {
            SettlementsScreen(
                debts = allDebts,
                members = householdMembers,
                currentUser = currentUser,
                onPayViaUpi = { debt, preferredApp ->
                    onLaunchUpiPayment(debt, preferredApp)
                },
                onVerifyPayment = { debt, isVerified ->
                    viewModel.verifySettlement(debt, isVerified)
                    onShowMessage(if (isVerified) "Payment verified and settled!" else "Marked as pending")
                },
                onRequestConfirmation = { debt, txRef ->
                    viewModel.markDebtPaidPendingConfirmation(debt, txRef)
                    onShowMessage("Marked paid with ref: $txRef. Waiting for friend confirmation.")
                },
                onSendReminder = { debt ->
                    viewModel.sendDebtReminder(debt)
                    onShowMessage("Payment reminder pushed to ${debt.fromUserName}!")
                },
                onDeleteDebt = { id ->
                    viewModel.deleteDebt(id)
                    onShowMessage("Debt record removed")
                },
                onAddDebt = { from, to, amount, reason ->
                    viewModel.addDebt(from, to, amount, reason)
                    onShowMessage("Recorded debt for $reason")
                },
                onPay3rdParty = { name, upi, amount, note, recordExpense ->
                    if (recordExpense) {
                        viewModel.addExpense(
                            title = "Payment to $name",
                            amount = amount,
                            category = if (name.contains("Rent", true) || name.contains("Landlord", true)) "Rent" else "Utilities",
                            paidBy = currentUser ?: householdMembers.first(),
                            splitType = "EQUAL",
                            splitWithMembers = emptyList(),
                            notes = note
                        )
                        onShowMessage("Payment initiated & recorded in shared expenses!")
                    }
                }
            )
        }

        ScreenTab.HOUSEHOLD, ScreenTab.HOUSEHOLD_SETTINGS, ScreenTab.ANALYTICS -> {
            HouseholdProfileScreen(
                currentUser = currentUser,
                household = household,
                members = householdMembers,
                syncManager = viewModel.syncManager,
                onUpdateProfile = { name, upi, email ->
                    viewModel.updateUserProfile(name, upi, email)
                    onShowMessage("Profile updated!")
                },
                onAddRoommate = { name, email, upi, color ->
                    viewModel.addRoommate(name, email, upi, color)
                    onShowMessage("Added roommate: $name")
                },
                onUpdateRoommate = { user ->
                    viewModel.updateRoommate(user)
                    onShowMessage("Updated ${user.name}")
                },
                onDeleteRoommate = { id, name ->
                    viewModel.deleteRoommate(id, name)
                    onShowMessage("Removed $name")
                },
                onJoinHousehold = { code, name ->
                    viewModel.joinHousehold(code, name)
                    onShowMessage("Switched to household: $code")
                },
                onRegenerateCode = {
                    viewModel.regenerateHouseholdCode()
                },
                onUpdateHouseholdAndBudget = { name, budget, threshold ->
                    viewModel.updateHouseholdAndBudget(name, budget, threshold)
                },
                onTriggerSync = {
                    viewModel.syncWithSupabase()
                    onShowMessage("Cloud sync complete!")
                },
                onSignIn = { email, pass, cb ->
                    viewModel.signInWithSupabase(email, pass, cb)
                },
                onSignUp = { email, pass, name, upi, code, hName, cb ->
                    viewModel.signUpWithSupabase(email, pass, name, upi, code, hName, cb)
                },
                onSignOut = {
                    viewModel.logout()
                    onShowMessage("Signed out of RoomieVault")
                },
                onResetPassword = { email, cb ->
                    viewModel.resetSupabasePassword(email, cb)
                },
                onPullFromCloud = {
                    viewModel.pullFromSupabase()
                },
                onTestConnection = { cb ->
                    viewModel.testSupabaseConnection(cb)
                },
                onCheckAppUpdate = {
                    viewModel.checkForAppUpdate(isManual = true)
                }
            )
        }
    }
}
