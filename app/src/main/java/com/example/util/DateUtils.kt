package com.example.util

import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

object DateUtils {
    private val monthYearFormat = SimpleDateFormat("yyyy-MM", Locale.getDefault())
    private val displayDateFormat = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
    private val displayMonthFormat = SimpleDateFormat("MMMM yyyy", Locale.getDefault())
    private val timeFormat = SimpleDateFormat("hh:mm a", Locale.getDefault())
    private val dayOfWeekFormat = SimpleDateFormat("EEEE", Locale.getDefault())

    fun getCurrentMonthYearKey(): String = monthYearFormat.format(Date())

    fun getMonthYearKey(millis: Long): String = monthYearFormat.format(Date(millis))

    fun formatDisplayDate(millis: Long): String = displayDateFormat.format(Date(millis))

    fun formatDisplayMonth(monthYearKey: String): String {
        return try {
            val date = monthYearFormat.parse(monthYearKey)
            if (date != null) displayMonthFormat.format(date) else monthYearKey
        } catch (e: Exception) {
            monthYearKey
        }
    }

    fun formatTime(millis: Long): String = timeFormat.format(Date(millis))

    fun getTodayDayOfWeek(): String = dayOfWeekFormat.format(Date())

    fun isTodaySunday(): Boolean {
        val calendar = Calendar.getInstance()
        return calendar.get(Calendar.DAY_OF_WEEK) == Calendar.SUNDAY
    }

    fun getDaysUntilNextSunday(): Int {
        val calendar = Calendar.getInstance()
        val currentDay = calendar.get(Calendar.DAY_OF_WEEK)
        return if (currentDay == Calendar.SUNDAY) 0 else (Calendar.SUNDAY + 7 - currentDay) % 7
    }

    fun getNextSundayDateString(): String {
        val calendar = Calendar.getInstance()
        val daysUntilSunday = getDaysUntilNextSunday()
        calendar.add(Calendar.DAY_OF_YEAR, if (daysUntilSunday == 0) 0 else daysUntilSunday)
        return displayDateFormat.format(calendar.time)
    }

    fun formatCurrency(amount: Double, symbol: String = "₹"): String {
        val formatter = NumberFormat.getNumberInstance(Locale.getDefault())
        formatter.minimumFractionDigits = 0
        formatter.maximumFractionDigits = 2
        return "$symbol${formatter.format(amount)}"
    }
}
