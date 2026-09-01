package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.ui.viewmodel.BudgetStatus
import com.example.util.DateUtils

private data class BannerStyle(
    val bgColor: Color,
    val iconColor: Color,
    val icon: ImageVector,
    val title: String,
    val message: String
)

@Composable
fun BudgetAlertBanner(
    budgetStatus: BudgetStatus,
    onAdjustBudgetClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (!budgetStatus.isOverBudget && !budgetStatus.isWarning) {
        return // Limit is set and on track - hide the white box with Adjust Limit
    }

    val style = when {
        budgetStatus.isOverBudget -> {
            BannerStyle(
                bgColor = Color(0xFFFEF2F2),
                iconColor = Color(0xFFEF4444),
                icon = Icons.Default.ErrorOutline,
                title = "🚨 Critical Alert: Budget Exceeded!",
                message = "You have spent ${DateUtils.formatCurrency(budgetStatus.totalSpent)} which exceeds your limit of ${DateUtils.formatCurrency(budgetStatus.budgetLimit)} (${budgetStatus.percentUsed}% used)."
            )
        }
        else -> {
            BannerStyle(
                bgColor = Color(0xFFFFFBEB),
                iconColor = Color(0xFFF59E0B),
                icon = Icons.Default.WarningAmber,
                title = "⚠️ Automated Budget Warning",
                message = "You have used ${budgetStatus.percentUsed}% of your monthly limit (${DateUtils.formatCurrency(budgetStatus.totalSpent)} / ${DateUtils.formatCurrency(budgetStatus.budgetLimit)})."
            )
        }
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = style.bgColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(style.iconColor.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = style.icon,
                        contentDescription = "Alert",
                        tint = style.iconColor,
                        modifier = Modifier.size(22.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = style.title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = style.iconColor
                    )
                    Text(
                        text = style.message,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF334155)
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(onClick = onAdjustBudgetClick) {
                    Text(
                        text = "Adjust Limit",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = style.iconColor
                    )
                }
            }
        }
    }
}
