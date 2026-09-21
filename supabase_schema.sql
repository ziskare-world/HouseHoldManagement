-- =========================================================================
-- ROOMIEVAULT PRODUCTION SUPABASE SCHEMA & POLICIES
-- Project: https://gxpxbnrehrawxwgzdqqm.supabase.co
-- Run this in your Supabase SQL Editor:
-- https://supabase.com/dashboard/project/gxpxbnrehrawxwgzdqqm/sql/new
-- =========================================================================

-- 1. Households Table
CREATE TABLE IF NOT EXISTS public.households (
    id TEXT PRIMARY KEY,
    name TEXT NOT NULL,
    invite_code TEXT NOT NULL,
    created_by_user_id TEXT,
    currency_symbol TEXT DEFAULT '₹',
    monthly_budget_limit NUMERIC DEFAULT 30000.0,
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
    split_with_user_ids TEXT,
    household_id TEXT REFERENCES public.households(id) ON DELETE CASCADE,
    receipt_image_url TEXT,
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
    icon_name TEXT,
    color_hex TEXT DEFAULT '#3B82F6',
    created_at BIGINT DEFAULT (extract(epoch from now()) * 1000)::bigint,
    updated_at BIGINT DEFAULT (extract(epoch from now()) * 1000)::bigint
);

-- 7. Budget Configurations Table
CREATE TABLE IF NOT EXISTS public.budget_configs (
    month_year_key TEXT NOT NULL,
    household_id TEXT NOT NULL,
    total_budget_limit NUMERIC DEFAULT 30000.0,
    alert_threshold_percent INTEGER DEFAULT 80,
    groceries_budget NUMERIC DEFAULT 12000.0,
    rent_utilities_budget NUMERIC DEFAULT 15000.0,
    food_dining_budget NUMERIC DEFAULT 5000.0,
    miscellaneous_budget NUMERIC DEFAULT 3000.0,
    PRIMARY KEY (month_year_key, household_id)
);

-- 8. Household Notifications Table
CREATE TABLE IF NOT EXISTS public.household_notifications (
    id TEXT PRIMARY KEY,
    household_id TEXT REFERENCES public.households(id) ON DELETE CASCADE,
    sender_user_id TEXT NOT NULL,
    sender_user_name TEXT NOT NULL,
    target_user_id TEXT NOT NULL,
    target_user_name TEXT NOT NULL,
    type TEXT NOT NULL,
    title TEXT NOT NULL,
    message TEXT NOT NULL,
    related_entity_id TEXT,
    is_read BOOLEAN DEFAULT false,
    created_at BIGINT DEFAULT (extract(epoch from now()) * 1000)::bigint
);

-- Enable Row Level Security (RLS)
ALTER TABLE public.households ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.user_profiles ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.expenses ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.chore_tasks ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.settlement_debts ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.savings_goals ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.budget_configs ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.household_notifications ENABLE ROW LEVEL SECURITY;

-- Permissive RLS Policies for RoomieVault Sync
DO $$
BEGIN
    DROP POLICY IF EXISTS "Allow all operations for anon and auth" ON public.households;
    CREATE POLICY "Allow all operations for anon and auth" ON public.households FOR ALL USING (true) WITH CHECK (true);

    DROP POLICY IF EXISTS "Allow all operations for anon and auth" ON public.user_profiles;
    CREATE POLICY "Allow all operations for anon and auth" ON public.user_profiles FOR ALL USING (true) WITH CHECK (true);

    DROP POLICY IF EXISTS "Allow all operations for anon and auth" ON public.expenses;
    CREATE POLICY "Allow all operations for anon and auth" ON public.expenses FOR ALL USING (true) WITH CHECK (true);

    DROP POLICY IF EXISTS "Allow all operations for anon and auth" ON public.chore_tasks;
    CREATE POLICY "Allow all operations for anon and auth" ON public.chore_tasks FOR ALL USING (true) WITH CHECK (true);

    DROP POLICY IF EXISTS "Allow all operations for anon and auth" ON public.settlement_debts;
    CREATE POLICY "Allow all operations for anon and auth" ON public.settlement_debts FOR ALL USING (true) WITH CHECK (true);

    DROP POLICY IF EXISTS "Allow all operations for anon and auth" ON public.savings_goals;
    CREATE POLICY "Allow all operations for anon and auth" ON public.savings_goals FOR ALL USING (true) WITH CHECK (true);

    DROP POLICY IF EXISTS "Allow all operations for anon and auth" ON public.budget_configs;
    CREATE POLICY "Allow all operations for anon and auth" ON public.budget_configs FOR ALL USING (true) WITH CHECK (true);

    DROP POLICY IF EXISTS "Allow all operations for anon and auth" ON public.household_notifications;
    CREATE POLICY "Allow all operations for anon and auth" ON public.household_notifications FOR ALL USING (true) WITH CHECK (true);
END $$;
