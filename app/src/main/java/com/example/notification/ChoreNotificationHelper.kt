package com.example.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.MainActivity

object ChoreNotificationHelper {
    const val CHANNEL_CHORES = "roomie_chores_channel"
    const val CHANNEL_BUDGET = "roomie_budget_channel"
    const val CHANNEL_SETTLEMENTS = "roomie_settlements_channel"
    const val CHANNEL_SYNC = "roomie_sync_channel"
    const val SYNC_NOTIFICATION_ID = 2001

    fun createNotificationChannels(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                ?: return

            val choreChannel = NotificationChannel(
                CHANNEL_CHORES,
                "Daily & Sunday Chore Reminders",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Daily task alerts, Sunday rotation work, and roommate chore rosters"
                enableVibration(true)
            }

            val budgetChannel = NotificationChannel(
                CHANNEL_BUDGET,
                "Budget & Expense Alerts",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Automated alerts when monthly expenses exceed budget thresholds"
                enableVibration(true)
            }

            val settlementChannel = NotificationChannel(
                CHANNEL_SETTLEMENTS,
                "Debt & UPI Payment Alerts",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Reminders for giving money to friends and verifying UPI settlements"
            }

            val syncChannel = NotificationChannel(
                CHANNEL_SYNC,
                "Cloud Data Sync Status",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Live progress and status alerts during Supabase data upload and download"
            }

            manager.createNotificationChannel(choreChannel)
            manager.createNotificationChannel(budgetChannel)
            manager.createNotificationChannel(settlementChannel)
            manager.createNotificationChannel(syncChannel)
        }
    }

    fun showChoreAlert(context: Context, id: Int, title: String, message: String) {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            id,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_CHORES)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        try {
            NotificationManagerCompat.from(context).notify(id, notification)
        } catch (e: SecurityException) {
            // Permission not granted yet
        }
    }

    fun showChoreReminder(context: Context, id: Int, title: String, message: String) {
        showChoreAlert(context, id, title, message)
    }

    fun showBudgetAlert(context: Context, id: Int, title: String, message: String) {
        val intent = Intent(context, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            context,
            id,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_BUDGET)
            .setSmallIcon(android.R.drawable.stat_notify_error)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        try {
            NotificationManagerCompat.from(context).notify(id, notification)
        } catch (e: SecurityException) {
            // Permission not granted
        }
    }

    fun showBudgetAlert(context: Context, title: String, message: String) {
        showBudgetAlert(context, 999, title, message)
    }

    fun showSettlementAlert(context: Context, id: Int, title: String, message: String) {
        val intent = Intent(context, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            context,
            id,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_SETTLEMENTS)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        try {
            NotificationManagerCompat.from(context).notify(id, notification)
        } catch (e: SecurityException) {
            // Permission not granted
        }
    }

    fun showNotificationAlert(context: Context, id: Int, title: String, message: String) {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            id,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val channelId = if (title.contains("Payment", true) || title.contains("Debt", true) || title.contains("Settlement", true)) {
            CHANNEL_SETTLEMENTS
        } else {
            CHANNEL_CHORES
        }

        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        try {
            NotificationManagerCompat.from(context).notify(id, notification)
        } catch (e: SecurityException) {
            // Permission not granted
        }
    }

    fun showSyncProgress(context: Context, title: String, message: String, progress: Int = 0, max: Int = 100) {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            SYNC_NOTIFICATION_ID,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_SYNC)
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setContentTitle(title)
            .setContentText(message)
            .setProgress(max, progress, max == 0)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(pendingIntent)
            .build()

        try {
            NotificationManagerCompat.from(context).notify(SYNC_NOTIFICATION_ID, notification)
        } catch (_: SecurityException) {}
    }

    fun showSyncComplete(context: Context, title: String, message: String) {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            SYNC_NOTIFICATION_ID,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_SYNC)
            .setSmallIcon(android.R.drawable.stat_notify_sync_noanim)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setProgress(0, 0, false)
            .setOngoing(false)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .build()

        try {
            NotificationManagerCompat.from(context).notify(SYNC_NOTIFICATION_ID, notification)
        } catch (_: SecurityException) {}
    }

    fun cancelSyncNotification(context: Context) {
        try {
            NotificationManagerCompat.from(context).cancel(SYNC_NOTIFICATION_ID)
        } catch (_: SecurityException) {}
    }
}
