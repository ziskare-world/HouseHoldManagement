package com.example.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.local.model.BudgetConfig
import com.example.data.local.model.ChoreTask
import com.example.data.local.model.ExpenseItem
import com.example.data.local.model.Household
import com.example.data.local.model.HouseholdNotification
import com.example.data.local.model.SavingsGoal
import com.example.data.local.model.SettlementDebt
import com.example.data.local.model.UserProfile
import kotlinx.coroutines.flow.Flow

@Dao
interface RoomieDao {

    // --- Users & Profiles ---
    @Query("SELECT * FROM user_profiles WHERE isCurrentUser = 1 LIMIT 1")
    fun getCurrentUser(): Flow<UserProfile?>

    @Query("SELECT * FROM user_profiles WHERE isCurrentUser = 1 LIMIT 1")
    suspend fun getCurrentUserDirect(): UserProfile?

    @Query("SELECT * FROM user_profiles WHERE householdId = :householdId ORDER BY name ASC")
    fun getHouseholdMembers(householdId: String): Flow<List<UserProfile>>

    @Query("SELECT * FROM user_profiles WHERE householdId = :householdId ORDER BY name ASC")
    suspend fun getHouseholdMembersDirect(householdId: String): List<UserProfile>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUser(user: UserProfile)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUsers(users: List<UserProfile>)

    @Query("UPDATE user_profiles SET isCurrentUser = 0")
    suspend fun clearCurrentUserFlag()

    @Query("DELETE FROM user_profiles WHERE id = :userId")
    suspend fun deleteUser(userId: String)

    // --- Household ---
    @Query("SELECT * FROM households WHERE id = :householdId LIMIT 1")
    fun getHousehold(householdId: String): Flow<Household?>

    @Query("SELECT * FROM households WHERE id = :householdId LIMIT 1")
    suspend fun getHouseholdDirect(householdId: String): Household?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHousehold(household: Household)

    // --- Expenses (Personal expenses are private to the creator) ---
    @Query("SELECT * FROM expenses WHERE householdId = :householdId AND (splitType != 'PERSONAL' OR paidByUserId = :currentUserId) ORDER BY dateMillis DESC")
    fun getAllExpenses(householdId: String, currentUserId: String): Flow<List<ExpenseItem>>

    @Query("SELECT * FROM expenses WHERE householdId = :householdId AND monthYearKey = :monthYearKey AND (splitType != 'PERSONAL' OR paidByUserId = :currentUserId) ORDER BY dateMillis DESC")
    fun getExpensesByMonth(householdId: String, monthYearKey: String, currentUserId: String): Flow<List<ExpenseItem>>

    @Query("SELECT * FROM expenses ORDER BY dateMillis DESC")
    suspend fun getAllExpensesDirect(): List<ExpenseItem>

    @Query("SELECT * FROM expenses WHERE householdId = :householdId AND monthYearKey = :monthYearKey AND (splitType != 'PERSONAL' OR paidByUserId = :currentUserId) ORDER BY dateMillis DESC")
    suspend fun getExpensesByMonthDirect(householdId: String, monthYearKey: String, currentUserId: String): List<ExpenseItem>

    @Query("SELECT * FROM expenses WHERE id = :expenseId LIMIT 1")
    suspend fun getExpenseById(expenseId: String): ExpenseItem?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertExpense(expense: ExpenseItem)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertExpenses(expenses: List<ExpenseItem>)

    @Update
    suspend fun updateExpense(expense: ExpenseItem)

    @Query("DELETE FROM expenses WHERE id = :expenseId")
    suspend fun deleteExpense(expenseId: String)

    // --- Chores ---
    @Query("SELECT * FROM chore_tasks WHERE householdId = :householdId ORDER BY createdAt DESC")
    fun getAllChores(householdId: String): Flow<List<ChoreTask>>

    @Query("SELECT * FROM chore_tasks WHERE householdId = :householdId")
    suspend fun getAllChoresDirect(householdId: String): List<ChoreTask>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertChore(chore: ChoreTask)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertChores(chores: List<ChoreTask>)

    @Update
    suspend fun updateChore(chore: ChoreTask)

    @Query("UPDATE chore_tasks SET status = :status, lastCompletedDate = :date WHERE id = :choreId")
    suspend fun updateChoreStatus(choreId: String, status: String, date: String)

    @Query("UPDATE chore_tasks SET rotationIndex = :newIndex, assignedToUserId = :userId, assignedToUserName = :userName, status = 'PENDING' WHERE id = :choreId")
    suspend fun advanceChoreRotation(choreId: String, newIndex: Int, userId: String, userName: String)

    @Query("DELETE FROM chore_tasks WHERE id = :choreId")
    suspend fun deleteChore(choreId: String)

    // --- Settlement Debts ---
    @Query("SELECT * FROM settlement_debts WHERE householdId = :householdId ORDER BY createdDateMillis DESC")
    fun getSettlementDebts(householdId: String): Flow<List<SettlementDebt>>

    @Query("SELECT * FROM settlement_debts WHERE householdId = :householdId")
    suspend fun getSettlementDebtsDirect(householdId: String): List<SettlementDebt>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDebt(debt: SettlementDebt)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDebts(debts: List<SettlementDebt>)

    @Query("UPDATE settlement_debts SET status = :status, settledDateMillis = :settledDate, transactionRef = :txRef WHERE id = :debtId")
    suspend fun updateDebtStatus(debtId: String, status: String, settledDate: Long, txRef: String)

    @Query("DELETE FROM settlement_debts WHERE id = :debtId")
    suspend fun deleteDebt(debtId: String)

    // --- Savings Goals ---
    @Query("SELECT * FROM savings_goals WHERE householdId = :householdId ORDER BY targetMonthYear ASC")
    fun getSavingsGoals(householdId: String): Flow<List<SavingsGoal>>

    @Query("SELECT * FROM savings_goals WHERE householdId = :householdId")
    suspend fun getSavingsGoalsDirect(householdId: String): List<SavingsGoal>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSavingsGoal(goal: SavingsGoal)

    @Query("UPDATE savings_goals SET currentAmount = :newAmount, updatedAt = :time WHERE id = :goalId")
    suspend fun updateSavingsProgress(goalId: String, newAmount: Double, time: Long)

    @Query("DELETE FROM savings_goals WHERE id = :goalId")
    suspend fun deleteSavingsGoal(goalId: String)

    // --- Budget Config ---
    @Query("SELECT * FROM budget_configs WHERE monthYearKey = :monthYearKey AND householdId = :householdId LIMIT 1")
    fun getBudgetConfig(monthYearKey: String, householdId: String): Flow<BudgetConfig?>

    @Query("SELECT * FROM budget_configs WHERE monthYearKey = :monthYearKey AND householdId = :householdId LIMIT 1")
    suspend fun getBudgetConfigDirect(monthYearKey: String, householdId: String): BudgetConfig?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBudgetConfig(config: BudgetConfig)

    // --- Clean Slate & Data Reset ---
    @Query("DELETE FROM expenses")
    suspend fun clearAllExpenses()

    @Query("DELETE FROM chore_tasks")
    suspend fun clearAllChores()

    @Query("DELETE FROM settlement_debts")
    suspend fun clearAllDebts()

    @Query("DELETE FROM savings_goals")
    suspend fun clearAllSavingsGoals()

    @Query("DELETE FROM user_profiles")
    suspend fun clearAllUsers()

    @Query("DELETE FROM households")
    suspend fun clearAllHouseholds()

    // --- Household Notifications & Alerts ---
    @Query("SELECT * FROM household_notifications WHERE householdId = :householdId AND targetUserId = :userId ORDER BY createdAt DESC")
    fun getNotificationsForUser(householdId: String, userId: String): Flow<List<HouseholdNotification>>

    @Query("SELECT * FROM household_notifications WHERE householdId = :householdId AND targetUserId = :userId ORDER BY createdAt DESC")
    suspend fun getNotificationsForUserDirect(householdId: String, userId: String): List<HouseholdNotification>

    @Query("SELECT COUNT(*) FROM household_notifications WHERE householdId = :householdId AND targetUserId = :userId AND isRead = 0")
    fun getUnreadNotificationsCount(householdId: String, userId: String): Flow<Int>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNotification(notification: HouseholdNotification)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNotifications(notifications: List<HouseholdNotification>)

    @Query("UPDATE household_notifications SET isRead = 1 WHERE id = :id")
    suspend fun markNotificationAsRead(id: String)

    @Query("UPDATE household_notifications SET isRead = 1 WHERE householdId = :householdId AND targetUserId = :userId")
    suspend fun markAllNotificationsAsRead(householdId: String, userId: String)

    @Query("DELETE FROM household_notifications WHERE id = :id")
    suspend fun deleteNotification(id: String)

    @Query("DELETE FROM household_notifications")
    suspend fun clearAllNotifications()
}
