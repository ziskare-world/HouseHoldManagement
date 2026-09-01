package com.example.data.remote

import android.content.Context
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
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class SupabaseDataStore(
    private val context: Context,
    private val authManager: SupabaseAuthManager
) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(12, TimeUnit.SECONDS)
        .build()

    private fun getAuthHeader(anonKey: String): String {
        val userToken = authManager.accessToken
        return if (!userToken.isNullOrBlank()) "Bearer $userToken" else "Bearer $anonKey"
    }

    /**
     * Test connection to Supabase Project
     */
    suspend fun pingConnection(baseUrl: String, anonKey: String): Pair<Boolean, String> = withContext(Dispatchers.IO) {
        val start = System.currentTimeMillis()
        try {
            val url = "${baseUrl.removeSuffix("/")}/rest/v1/households?select=id&limit=1"
            val request = Request.Builder()
                .url(url)
                .addHeader("apikey", anonKey)
                .addHeader("Authorization", getAuthHeader(anonKey))
                .get()
                .build()

            client.newCall(request).execute().use { response ->
                val duration = System.currentTimeMillis() - start
                if (response.isSuccessful || response.code in 200..299) {
                    Pair(true, "Connected successfully (${duration}ms latency)")
                } else if (response.code == 404 || response.code == 400 || response.code == 401) {
                    // Endpoint reached, but table not yet created or auth header issue
                    Pair(true, "Endpoint reachable (${response.code} - ${duration}ms). Ready for table schema.")
                } else {
                    Pair(false, "HTTP ${response.code}: ${response.message}")
                }
            }
        } catch (e: Exception) {
            Pair(false, "Connection error: ${e.localizedMessage ?: "Unknown error"}")
        }
    }

    // ==========================================
    // PUSH / UPSERT TO SUPABASE DATA STORE
    // ==========================================

    suspend fun pushHousehold(baseUrl: String, anonKey: String, household: Household): Boolean = withContext(Dispatchers.IO) {
        val json = JSONObject().apply {
            put("id", household.id)
            put("name", household.name)
            put("invite_code", household.inviteCode)
            put("created_by_user_id", household.createdByUserId)
            put("monthly_budget_limit", household.monthlyBudgetLimit)
            put("budget_warning_threshold", household.budgetWarningThreshold)
            put("created_at", household.createdAt)
        }
        upsertRecord(baseUrl, anonKey, "households", json.toString())
    }

    suspend fun pushUserProfile(baseUrl: String, anonKey: String, user: UserProfile): Boolean = withContext(Dispatchers.IO) {
        val json = JSONObject().apply {
            put("id", user.id)
            put("name", user.name)
            put("email", user.email)
            put("upi_id", user.upiId)
            put("household_id", user.householdId)
            put("household_name", user.householdName)
            put("avatar_color_hex", user.avatarColorHex)
            put("is_virtual", user.isVirtual)
            put("created_at", user.createdAt)
        }
        upsertRecord(baseUrl, anonKey, "user_profiles", json.toString())
    }

    suspend fun pushExpense(baseUrl: String, anonKey: String, expense: ExpenseItem): Boolean = withContext(Dispatchers.IO) {
        val json = JSONObject().apply {
            put("id", expense.id)
            put("title", expense.title)
            put("amount", expense.amount)
            put("category", expense.category)
            put("date_millis", expense.dateMillis)
            put("month_year_key", expense.monthYearKey)
            put("paid_by_user_id", expense.paidByUserId)
            put("paid_by_name", expense.paidByName)
            put("split_type", expense.splitType)
            put("split_with_user_ids", expense.splitWithUserIds)
            put("household_id", expense.householdId)
            put("notes", expense.notes)
            put("created_at", expense.createdAt)
        }
        upsertRecord(baseUrl, anonKey, "expenses", json.toString())
    }

    suspend fun pushChore(baseUrl: String, anonKey: String, chore: ChoreTask): Boolean = withContext(Dispatchers.IO) {
        val json = JSONObject().apply {
            put("id", chore.id)
            put("title", chore.title)
            put("description", chore.description)
            put("category", chore.category)
            put("assigned_to_user_id", chore.assignedToUserId)
            put("assigned_to_user_name", chore.assignedToUserName)
            put("household_id", chore.householdId)
            put("frequency", chore.frequency)
            put("rotation_member_ids", chore.rotationMemberIds)
            put("rotation_index", chore.rotationIndex)
            put("scheduled_time", chore.scheduledTime)
            put("scheduled_day_of_week", chore.scheduledDayOfWeek)
            put("scheduled_day_of_month", chore.scheduledDayOfMonth)
            put("status", chore.status)
            put("points", chore.points)
            put("last_completed_date", chore.lastCompletedDate)
            put("created_at", chore.createdAt)
        }
        upsertRecord(baseUrl, anonKey, "chore_tasks", json.toString())
    }

    suspend fun pushDebt(baseUrl: String, anonKey: String, debt: SettlementDebt): Boolean = withContext(Dispatchers.IO) {
        val json = JSONObject().apply {
            put("id", debt.id)
            put("from_user_id", debt.fromUserId)
            put("from_user_name", debt.fromUserName)
            put("to_user_id", debt.toUserId)
            put("to_user_name", debt.toUserName)
            put("to_user_upi_id", debt.toUserUpiId)
            put("amount", debt.amount)
            put("reason", debt.reason)
            put("created_date_millis", debt.createdDateMillis)
            put("settled_date_millis", debt.settledDateMillis)
            put("status", debt.status)
            put("transaction_ref", debt.transactionRef)
            put("household_id", debt.householdId)
        }
        upsertRecord(baseUrl, anonKey, "settlement_debts", json.toString())
    }

    suspend fun pushSavingsGoal(baseUrl: String, anonKey: String, goal: SavingsGoal): Boolean = withContext(Dispatchers.IO) {
        val json = JSONObject().apply {
            put("id", goal.id)
            put("title", goal.title)
            put("target_amount", goal.targetAmount)
            put("current_amount", goal.currentAmount)
            put("category", goal.category)
            put("target_month_year", goal.targetMonthYear)
            put("household_id", goal.householdId)
            put("icon_name", goal.iconName)
            put("color_hex", goal.colorHex)
            put("updated_at", goal.updatedAt)
        }
        upsertRecord(baseUrl, anonKey, "savings_goals", json.toString())
    }

    suspend fun pushBudgetConfig(baseUrl: String, anonKey: String, config: BudgetConfig): Boolean = withContext(Dispatchers.IO) {
        val json = JSONObject().apply {
            put("month_year_key", config.monthYearKey)
            put("household_id", config.householdId)
            put("total_budget_limit", config.totalBudgetLimit)
            put("alert_threshold_percent", config.alertThresholdPercent)
            put("groceries_budget", config.groceriesBudget)
            put("rent_utilities_budget", config.rentUtilitiesBudget)
            put("food_dining_budget", config.foodDiningBudget)
            put("miscellaneous_budget", config.miscellaneousBudget)
        }
        upsertRecord(baseUrl, anonKey, "budget_configs", json.toString())
    }

    private suspend fun upsertRecord(baseUrl: String, anonKey: String, table: String, jsonPayload: String): Boolean {
        if (baseUrl.isBlank() || anonKey.isBlank()) return false
        return try {
            val url = "${baseUrl.removeSuffix("/")}/rest/v1/$table"
            val body = jsonPayload.toRequestBody("application/json; charset=utf-8".toMediaType())
            val request = Request.Builder()
                .url(url)
                .addHeader("apikey", anonKey)
                .addHeader("Authorization", getAuthHeader(anonKey))
                .addHeader("Prefer", "resolution=merge-duplicates")
                .post(body)
                .build()

            client.newCall(request).execute().use { response ->
                val ok = response.isSuccessful || response.code in 200..299
                if (!ok) {
                    Log.w("SupabaseDataStore", "Upsert to $table responded code: ${response.code}")
                }
                ok
            }
        } catch (e: Exception) {
            Log.d("SupabaseDataStore", "Upsert $table offline: ${e.localizedMessage}")
            false
        }
    }

    suspend fun deleteRecord(baseUrl: String, anonKey: String, table: String, id: String): Boolean = withContext(Dispatchers.IO) {
        if (baseUrl.isBlank() || anonKey.isBlank()) return@withContext false
        try {
            val url = "${baseUrl.removeSuffix("/")}/rest/v1/$table?id=eq.$id"
            val request = Request.Builder()
                .url(url)
                .addHeader("apikey", anonKey)
                .addHeader("Authorization", getAuthHeader(anonKey))
                .delete()
                .build()

            client.newCall(request).execute().use { response ->
                response.isSuccessful || response.code in 200..299
            }
        } catch (e: Exception) {
            Log.d("SupabaseDataStore", "Delete from $table offline: ${e.localizedMessage}")
            false
        }
    }

    // ==========================================
    // PULL / SYNC FROM SUPABASE DATA STORE
    // ==========================================

    suspend fun pullAllDataFromSupabase(
        baseUrl: String,
        anonKey: String,
        householdId: String,
        dao: RoomieDao
    ): Result<Int> = withContext(Dispatchers.IO) {
        if (baseUrl.isBlank() || anonKey.isBlank()) {
            return@withContext Result.failure(Exception("Supabase credentials not configured"))
        }

        var totalRecords = 0
        try {
            // 1. Pull Household
            val householdJson = queryTable(baseUrl, anonKey, "households", "id=eq.$householdId")
            if (!householdJson.isNullOrBlank()) {
                val array = JSONArray(householdJson)
                if (array.length() > 0) {
                    val obj = array.getJSONObject(0)
                    val h = Household(
                        id = obj.optString("id", householdId),
                        name = obj.optString("name", "My Household"),
                        inviteCode = obj.optString("invite_code", "FLAT402"),
                        createdByUserId = obj.optString("created_by_user_id", "USR_ADMIN"),
                        monthlyBudgetLimit = obj.optDouble("monthly_budget_limit", 35000.0),
                        budgetWarningThreshold = obj.optInt("budget_warning_threshold", 80),
                        createdAt = obj.optLong("created_at", System.currentTimeMillis())
                    )
                    dao.insertHousehold(h)
                    totalRecords++
                }
            }

            // 2. Pull User Profiles
            val usersJson = queryTable(baseUrl, anonKey, "user_profiles", "household_id=eq.$householdId")
            if (!usersJson.isNullOrBlank()) {
                val array = JSONArray(usersJson)
                val usersList = mutableListOf<UserProfile>()
                val curUserId = authManager.currentUserId
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    val uid = obj.optString("id", "")
                    if (uid.isNotBlank()) {
                        usersList.add(
                            UserProfile(
                                id = uid,
                                name = obj.optString("name", "Roommate"),
                                email = obj.optString("email", ""),
                                upiId = obj.optString("upi_id", ""),
                                householdId = obj.optString("household_id", householdId),
                                householdName = obj.optString("household_name", "Household"),
                                avatarColorHex = obj.optString("avatar_color_hex", "#3B82F6"),
                                isCurrentUser = (uid == curUserId),
                                isVirtual = obj.optBoolean("is_virtual", false),
                                createdAt = obj.optLong("created_at", System.currentTimeMillis())
                            )
                        )
                    }
                }
                if (usersList.isNotEmpty()) {
                    dao.insertUsers(usersList)
                    totalRecords += usersList.size
                }
            }

            // 3. Pull Expenses
            val currentUserId = dao.getCurrentUserDirect()?.id ?: ""
            val expensesJson = queryTable(baseUrl, anonKey, "expenses", "household_id=eq.$householdId")
            if (!expensesJson.isNullOrBlank()) {
                val array = JSONArray(expensesJson)
                val expenseList = mutableListOf<ExpenseItem>()
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    val splitType = obj.optString("split_type", "EQUAL")
                    val paidByUserId = obj.optString("paid_by_user_id", "")
                    // Privacy guarantee: Do not pull personal expenses created by other roommates!
                    if (splitType == "PERSONAL" && currentUserId.isNotBlank() && paidByUserId != currentUserId) {
                        continue
                    }
                    expenseList.add(
                        ExpenseItem(
                            id = obj.optString("id", ""),
                            title = obj.optString("title", "Expense"),
                            amount = obj.optDouble("amount", 0.0),
                            category = obj.optString("category", "General"),
                            dateMillis = obj.optLong("date_millis", System.currentTimeMillis()),
                            monthYearKey = obj.optString("month_year_key", "2026-08"),
                            paidByUserId = paidByUserId,
                            paidByName = obj.optString("paid_by_name", "User"),
                            splitType = splitType,
                            splitWithUserIds = obj.optString("split_with_user_ids", ""),
                            householdId = obj.optString("household_id", householdId),
                            notes = obj.optString("notes", ""),
                            createdAt = obj.optLong("created_at", System.currentTimeMillis())
                        )
                    )
                }
                if (expenseList.isNotEmpty()) {
                    dao.insertExpenses(expenseList)
                    totalRecords += expenseList.size
                }
            }

            // 4. Pull Chores
            val choresJson = queryTable(baseUrl, anonKey, "chore_tasks", "household_id=eq.$householdId")
            if (!choresJson.isNullOrBlank()) {
                val array = JSONArray(choresJson)
                val choreList = mutableListOf<ChoreTask>()
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    choreList.add(
                        ChoreTask(
                            id = obj.optString("id", ""),
                            title = obj.optString("title", "Chore"),
                            description = obj.optString("description", ""),
                            category = obj.optString("category", "Cleaning"),
                            assignedToUserId = obj.optString("assigned_to_user_id", ""),
                            assignedToUserName = obj.optString("assigned_to_user_name", "Roommate"),
                            householdId = obj.optString("household_id", householdId),
                            frequency = obj.optString("frequency", "DAILY"),
                            rotationMemberIds = obj.optString("rotation_member_ids", ""),
                            rotationIndex = obj.optInt("rotation_index", 0),
                            scheduledTime = obj.optString("scheduled_time", "09:00 AM"),
                            scheduledDayOfWeek = obj.optInt("scheduled_day_of_week", 1),
                            scheduledDayOfMonth = obj.optInt("scheduled_day_of_month", 1),
                            status = obj.optString("status", "PENDING"),
                            points = obj.optInt("points", 10),
                            lastCompletedDate = obj.optString("last_completed_date", ""),
                            createdAt = obj.optLong("created_at", System.currentTimeMillis())
                        )
                    )
                }
                if (choreList.isNotEmpty()) {
                    dao.insertChores(choreList)
                    totalRecords += choreList.size
                }
            }

            // 5. Pull Debts
            val debtsJson = queryTable(baseUrl, anonKey, "settlement_debts", "household_id=eq.$householdId")
            if (!debtsJson.isNullOrBlank()) {
                val array = JSONArray(debtsJson)
                val debtList = mutableListOf<SettlementDebt>()
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    debtList.add(
                        SettlementDebt(
                            id = obj.optString("id", ""),
                            fromUserId = obj.optString("from_user_id", ""),
                            fromUserName = obj.optString("from_user_name", ""),
                            toUserId = obj.optString("to_user_id", ""),
                            toUserName = obj.optString("to_user_name", ""),
                            toUserUpiId = obj.optString("to_user_upi_id", ""),
                            amount = obj.optDouble("amount", 0.0),
                            reason = obj.optString("reason", ""),
                            createdDateMillis = obj.optLong("created_date_millis", System.currentTimeMillis()),
                            settledDateMillis = obj.optLong("settled_date_millis", 0L),
                            status = obj.optString("status", "PENDING"),
                            transactionRef = obj.optString("transaction_ref", ""),
                            householdId = obj.optString("household_id", householdId)
                        )
                    )
                }
                if (debtList.isNotEmpty()) {
                    dao.insertDebts(debtList)
                    totalRecords += debtList.size
                }
            }

            // 6. Pull Savings Goals
            val savingsJson = queryTable(baseUrl, anonKey, "savings_goals", "household_id=eq.$householdId")
            if (!savingsJson.isNullOrBlank()) {
                val array = JSONArray(savingsJson)
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    val goal = SavingsGoal(
                        id = obj.optString("id", ""),
                        title = obj.optString("title", ""),
                        targetAmount = obj.optDouble("target_amount", 0.0),
                        currentAmount = obj.optDouble("current_amount", 0.0),
                        category = obj.optString("category", "General"),
                        targetMonthYear = obj.optString("target_month_year", ""),
                        householdId = obj.optString("household_id", householdId),
                        iconName = obj.optString("icon_name", "Savings"),
                        colorHex = obj.optString("color_hex", "#3B82F6"),
                        updatedAt = obj.optLong("updated_at", System.currentTimeMillis())
                    )
                    dao.insertSavingsGoal(goal)
                    totalRecords++
                }
            }

            // 7. Pull Budget Configs
            val budgetsJson = queryTable(baseUrl, anonKey, "budget_configs", "household_id=eq.$householdId")
            if (!budgetsJson.isNullOrBlank()) {
                val array = JSONArray(budgetsJson)
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    val budget = BudgetConfig(
                        monthYearKey = obj.optString("month_year_key", ""),
                        householdId = obj.optString("household_id", householdId),
                        totalBudgetLimit = obj.optDouble("total_budget_limit", 35000.0),
                        alertThresholdPercent = obj.optInt("alert_threshold_percent", 80),
                        groceriesBudget = obj.optDouble("groceries_budget", 12000.0),
                        rentUtilitiesBudget = obj.optDouble("rent_utilities_budget", 15000.0),
                        foodDiningBudget = obj.optDouble("food_dining_budget", 5000.0),
                        miscellaneousBudget = obj.optDouble("miscellaneous_budget", 3000.0)
                    )
                    dao.insertBudgetConfig(budget)
                    totalRecords++
                }
            }

            Result.success(totalRecords)
        } catch (e: Exception) {
            Log.e("SupabaseDataStore", "Pull all data error", e)
            Result.failure(e)
        }
    }

    private suspend fun queryTable(baseUrl: String, anonKey: String, table: String, filter: String): String? {
        return try {
            val url = "${baseUrl.removeSuffix("/")}/rest/v1/$table?$filter"
            val request = Request.Builder()
                .url(url)
                .addHeader("apikey", anonKey)
                .addHeader("Authorization", getAuthHeader(anonKey))
                .get()
                .build()

            client.newCall(request).execute().use { response ->
                if (response.isSuccessful || response.code in 200..299) {
                    response.body?.string()
                } else {
                    null
                }
            }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Complete Supabase SQL Schema for easy 1-click database initialization
     */
    fun generateSupabaseSqlSchema(): String {
        return """
        -- ============================================================
        -- ROOMIEVAULT SUPABASE DATABASE SCHEMA & RLS POLICIES
        -- Execute this in the Supabase SQL Editor (https://supabase.com/dashboard)
        -- ============================================================

        -- 1. Households Table
        CREATE TABLE IF NOT EXISTS public.households (
            id TEXT PRIMARY KEY,
            name TEXT NOT NULL,
            invite_code TEXT NOT NULL,
            created_by_user_id TEXT,
            monthly_budget_limit NUMERIC DEFAULT 35000.0,
            budget_warning_threshold INTEGER DEFAULT 80,
            created_at BIGINT DEFAULT (extract(epoch from now()) * 1000)::bigint,
            updated_at TIMESTAMP WITH TIME ZONE DEFAULT timezone('utc'::text, now())
        );

        -- 2. User Profiles Table
        CREATE TABLE IF NOT EXISTS public.user_profiles (
            id TEXT PRIMARY KEY,
            name TEXT NOT NULL,
            email TEXT,
            upi_id TEXT,
            household_id TEXT REFERENCES public.households(id) ON DELETE SET NULL,
            household_name TEXT,
            avatar_color_hex TEXT DEFAULT '#0F5132',
            is_virtual BOOLEAN DEFAULT false,
            created_at BIGINT DEFAULT (extract(epoch from now()) * 1000)::bigint,
            updated_at TIMESTAMP WITH TIME ZONE DEFAULT timezone('utc'::text, now())
        );

        -- 3. Expenses Table
        CREATE TABLE IF NOT EXISTS public.expenses (
            id TEXT PRIMARY KEY,
            title TEXT NOT NULL,
            amount NUMERIC NOT NULL,
            category TEXT NOT NULL,
            date_millis BIGINT NOT NULL,
            month_year_key TEXT NOT NULL,
            paid_by_user_id TEXT,
            paid_by_name TEXT,
            split_type TEXT DEFAULT 'EQUAL',
            split_members_json TEXT,
            household_id TEXT REFERENCES public.households(id) ON DELETE CASCADE,
            notes TEXT,
            created_at BIGINT DEFAULT (extract(epoch from now()) * 1000)::bigint
        );

        -- 4. Chore Tasks Table
        CREATE TABLE IF NOT EXISTS public.chore_tasks (
            id TEXT PRIMARY KEY,
            title TEXT NOT NULL,
            description TEXT,
            category TEXT DEFAULT 'Cleaning',
            assigned_to_user_id TEXT,
            assigned_to_user_name TEXT,
            household_id TEXT REFERENCES public.households(id) ON DELETE CASCADE,
            frequency TEXT DEFAULT 'DAILY',
            rotation_member_ids TEXT,
            rotation_index INTEGER DEFAULT 0,
            scheduled_time TEXT,
            scheduled_day_of_week INTEGER DEFAULT 1,
            scheduled_day_of_month INTEGER DEFAULT 1,
            status TEXT DEFAULT 'PENDING',
            points INTEGER DEFAULT 10,
            last_completed_date TEXT,
            created_at BIGINT DEFAULT (extract(epoch from now()) * 1000)::bigint
        );

        -- 5. Settlement Debts Table
        CREATE TABLE IF NOT EXISTS public.settlement_debts (
            id TEXT PRIMARY KEY,
            from_user_id TEXT NOT NULL,
            from_user_name TEXT NOT NULL,
            to_user_id TEXT NOT NULL,
            to_user_name TEXT NOT NULL,
            to_user_upi_id TEXT,
            amount NUMERIC NOT NULL,
            reason TEXT,
            created_date_millis BIGINT NOT NULL,
            settled_date_millis BIGINT DEFAULT 0,
            status TEXT DEFAULT 'PENDING',
            transaction_ref TEXT,
            household_id TEXT REFERENCES public.households(id) ON DELETE CASCADE
        );

        -- 6. Savings Goals Table
        CREATE TABLE IF NOT EXISTS public.savings_goals (
            id TEXT PRIMARY KEY,
            title TEXT NOT NULL,
            target_amount NUMERIC NOT NULL,
            current_amount NUMERIC DEFAULT 0.0,
            category TEXT DEFAULT 'General',
            target_month_year TEXT,
            household_id TEXT REFERENCES public.households(id) ON DELETE CASCADE,
            color_hex TEXT DEFAULT '#3B82F6',
            created_at BIGINT DEFAULT (extract(epoch from now()) * 1000)::bigint,
            updated_at BIGINT DEFAULT (extract(epoch from now()) * 1000)::bigint
        );

        -- 7. Budget Configurations Table
        CREATE TABLE IF NOT EXISTS public.budget_configs (
            month_year_key TEXT NOT NULL,
            household_id TEXT NOT NULL,
            total_budget_limit NUMERIC DEFAULT 35000.0,
            alert_threshold_percent INTEGER DEFAULT 80,
            groceries_budget NUMERIC DEFAULT 12000.0,
            rent_utilities_budget NUMERIC DEFAULT 15000.0,
            food_dining_budget NUMERIC DEFAULT 5000.0,
            miscellaneous_budget NUMERIC DEFAULT 3000.0,
            PRIMARY KEY (month_year_key, household_id)
        );

        -- Enable Row Level Security (RLS) & Public Read/Write for Roommates
        ALTER TABLE public.households ENABLE ROW LEVEL SECURITY;
        ALTER TABLE public.user_profiles ENABLE ROW LEVEL SECURITY;
        ALTER TABLE public.expenses ENABLE ROW LEVEL SECURITY;
        ALTER TABLE public.chore_tasks ENABLE ROW LEVEL SECURITY;
        ALTER TABLE public.settlement_debts ENABLE ROW LEVEL SECURITY;
        ALTER TABLE public.savings_goals ENABLE ROW LEVEL SECURITY;
        ALTER TABLE public.budget_configs ENABLE ROW LEVEL SECURITY;

        -- Create policies for anon and authenticated users
        DO $$
        BEGIN
            EXECUTE 'CREATE POLICY "Allow all operations for anon and auth" ON public.households FOR ALL USING (true) WITH CHECK (true)';
            EXECUTE 'CREATE POLICY "Allow all operations for anon and auth" ON public.user_profiles FOR ALL USING (true) WITH CHECK (true)';
            EXECUTE 'CREATE POLICY "Allow all operations for anon and auth" ON public.expenses FOR ALL USING (true) WITH CHECK (true)';
            EXECUTE 'CREATE POLICY "Allow all operations for anon and auth" ON public.chore_tasks FOR ALL USING (true) WITH CHECK (true)';
            EXECUTE 'CREATE POLICY "Allow all operations for anon and auth" ON public.settlement_debts FOR ALL USING (true) WITH CHECK (true)';
            EXECUTE 'CREATE POLICY "Allow all operations for anon and auth" ON public.savings_goals FOR ALL USING (true) WITH CHECK (true)';
            EXECUTE 'CREATE POLICY "Allow all operations for anon and auth" ON public.budget_configs FOR ALL USING (true) WITH CHECK (true)';
        EXCEPTION WHEN duplicate_object THEN
            NULL;
        END $$;
        """.trimIndent()
    }
}
