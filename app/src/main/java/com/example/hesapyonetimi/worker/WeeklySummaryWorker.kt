package com.example.hesapyonetimi.worker

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.hesapyonetimi.MainActivity
import com.example.hesapyonetimi.R
import com.example.hesapyonetimi.data.local.dao.TransactionDao
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.Calendar
import java.util.concurrent.TimeUnit

@HiltWorker
class WeeklySummaryWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val transactionDao: TransactionDao
) : CoroutineWorker(context, params) {

    companion object {
        const val CHANNEL_ID = "weekly_summary_channel"
        const val CHANNEL_NAME = "Haftalık Özet"
        const val NOTIFICATION_ID = 9002
    }

    override suspend fun doWork(): Result {
        return try {
            val now = System.currentTimeMillis()
            val weekAgo = now - TimeUnit.DAYS.toMillis(7)

            val totalIncome = transactionDao.getTotalIncome(weekAgo, now) ?: 0.0
            val totalExpense = transactionDao.getTotalExpense(weekAgo, now) ?: 0.0
            val net = totalIncome - totalExpense

            sendSummaryNotification(totalIncome, totalExpense, net)
            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }

    private fun sendSummaryNotification(income: Double, expense: Double, net: Double) {
        createChannel()

        val intent = Intent(applicationContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pending = PendingIntent.getActivity(
            applicationContext, NOTIFICATION_ID, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val incStr = String.format("%,.0f", income).replace(",", ".")
        val expStr = String.format("%,.0f", expense).replace(",", ".")
        val netStr = String.format("%,.0f", kotlin.math.abs(net)).replace(",", ".")

        val netEmoji = if (net >= 0) "📈" else "📉"
        val netLabel = if (net >= 0) "Net tasarruf: +$netStr" else "Net açık: -$netStr"
        val summary = "Gelir: $incStr | Gider: $expStr\n$netLabel"

        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("$netEmoji Haftalık Harcama Özeti")
            .setContentText("Gider: $expStr — Gelir: $incStr")
            .setStyle(NotificationCompat.BigTextStyle().bigText(summary))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pending)
            .setAutoCancel(true)
            .build()

        val nm = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIFICATION_ID, notification)
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, CHANNEL_NAME, NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Haftalık gelir/gider özeti bildirimi"
            }
            val nm = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(channel)
        }
    }
}
