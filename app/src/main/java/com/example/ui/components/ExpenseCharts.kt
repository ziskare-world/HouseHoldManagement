package com.example.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.model.ExpenseItem
import com.example.util.DateUtils

// Curated distinct vibrant category colors
val CategoryColors = mapOf(
    "Groceries" to Color(0xFF10B981),
    "Rent" to Color(0xFF6366F1),
    "Utilities" to Color(0xFF3B82F6),
    "Food & Dining" to Color(0xFFF59E0B),
    "Household Supplies" to Color(0xFFEC4899),
    "Entertainment" to Color(0xFF8B5CF6),
    "Maintenance" to Color(0xFF14B8A6),
    "Other" to Color(0xFF64748B)
)

fun getCategoryColor(category: String): Color =
    CategoryColors[category] ?: Color(0xFF64748B)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MonthlyExpenseDonutChart(
    expenses: List<ExpenseItem>,
    modifier: Modifier = Modifier
) {
    val totalExpense = expenses.sumOf { it.amount }
    val categoryTotals = remember(expenses) {
        expenses.groupBy { it.category }
            .mapValues { (_, items) -> items.sumOf { it.amount } }
            .toList()
            .sortedByDescending { it.second }
    }

    var selectedCategory by remember { mutableStateOf<String?>(null) }
    val animationProgress = remember { Animatable(0f) }

    LaunchedEffect(expenses) {
        animationProgress.snapTo(0f)
        animationProgress.animateTo(1f, animationSpec = tween(durationMillis = 800))
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Category Breakdown",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = DateUtils.formatCurrency(totalExpense),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (expenses.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No expenses recorded this month yet",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                Box(
                    modifier = Modifier.size(200.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Canvas(modifier = Modifier.size(190.dp)) {
                        val strokeWidth = 32.dp.toPx()
                        val diameter = size.minDimension - strokeWidth
                        val topLeft = Offset(
                            (size.width - diameter) / 2,
                            (size.height - diameter) / 2
                        )
                        val arcSize = Size(diameter, diameter)

                        var startAngle = -90f

                        categoryTotals.forEach { (cat, amount) ->
                            val sweepAngle = if (totalExpense > 0) {
                                ((amount / totalExpense) * 360f * animationProgress.value).toFloat()
                            } else 0f

                            val isSelected = selectedCategory == cat
                            val color = getCategoryColor(cat)

                            drawArc(
                                color = if (selectedCategory == null || isSelected) color else color.copy(alpha = 0.3f),
                                startAngle = startAngle,
                                sweepAngle = sweepAngle,
                                useCenter = false,
                                topLeft = topLeft,
                                size = arcSize,
                                style = Stroke(
                                    width = if (isSelected) strokeWidth + 8.dp.toPx() else strokeWidth,
                                    cap = StrokeCap.Butt
                                )
                            )
                            startAngle += sweepAngle
                        }
                    }

                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = selectedCategory ?: "Total Spent",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1
                        )
                        val displayAmount = if (selectedCategory != null) {
                            categoryTotals.find { it.first == selectedCategory }?.second ?: 0.0
                        } else totalExpense

                        Text(
                            text = DateUtils.formatCurrency(displayAmount),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Legends
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    categoryTotals.forEach { (category, amount) ->
                        val percent = if (totalExpense > 0) ((amount / totalExpense) * 100).toInt() else 0
                        val isSelected = selectedCategory == category

                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .clickable {
                                    selectedCategory = if (selectedCategory == category) null else category
                                }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(10.dp)
                                        .clip(CircleShape)
                                        .background(getCategoryColor(category))
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "$category ($percent%)",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun MonthlySpendingTrendBarChart(
    expenses: List<ExpenseItem>,
    budgetLimit: Double,
    modifier: Modifier = Modifier
) {
    // Group expenses by last 4 months
    val currentMonthKey = DateUtils.getCurrentMonthYearKey()
    val totalThisMonth = expenses.filter { it.monthYearKey == currentMonthKey }.sumOf { it.amount }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Monthly Spending vs Budget",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Limit: ${DateUtils.formatCurrency(budgetLimit)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(
                    text = DateUtils.formatCurrency(totalThisMonth),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (totalThisMonth > budgetLimit) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Progress bar
            val progress = if (budgetLimit > 0) (totalThisMonth / budgetLimit).toFloat().coerceIn(0f, 1f) else 0f
            val percent = if (budgetLimit > 0) ((totalThisMonth / budgetLimit) * 100).toInt() else 0

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(16.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(progress)
                        .height(16.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(
                            when {
                                percent > 100 -> Color(0xFFEF4444)
                                percent >= 80 -> Color(0xFFF59E0B)
                                else -> Color(0xFF10B981)
                            }
                        )
                )
            }

            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "$percent% Used",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = when {
                        percent > 100 -> Color(0xFFEF4444)
                        percent >= 80 -> Color(0xFFF59E0B)
                        else -> Color(0xFF10B981)
                    }
                )
                val remaining = budgetLimit - totalThisMonth
                Text(
                    text = if (remaining >= 0) "${DateUtils.formatCurrency(remaining)} left" else "${DateUtils.formatCurrency(-remaining)} OVER LIMIT",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = if (remaining >= 0) MaterialTheme.colorScheme.onSurfaceVariant else Color(0xFFEF4444)
                )
            }
        }
    }
}
