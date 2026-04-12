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
import com.example.hesapyonetimi.util.PayPeriodResolver
import com.example.hesapyonetimi.data.local.dao.TransactionDao
import com.example.hesapyonetimi.data.local.dao.UserProfileDao
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

@HiltWorker
class BudgetAlertWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val transactionDao: TransactionDao,
    private val userProfileDao: UserProfileDao
) : CoroutineWorker(context, params) {

    companion object {
        const val CHANNEL_ID = "budget_alert_channel"
        const val CHANNEL_NAME = "Bütçe Uyarıları"
        const val NOTIFICATION_ID = 9001
    }

    override suspend fun doWork(): Result {
        return try {
            val profile = userProfileDao.getProfileOnce() ?: return Result.success()
            val budgetLimit = profile.monthlyBudgetLimit
            if (budgetLimit <= 0) return Result.success()

            val now = System.currentTimeMillis()
            val period = PayPeriodResolver.currentPeriod(applicationContext)
            val totalExpense = transactionDao.getTotalExpense(
                period.startMillis, now
            ) ?: 0.0

            val ratio = totalExpense / budgetLimit
            if (ratio >= 0.8) {
                sendBudgetNotification(totalExpense, budgetLimit, (ratio * 100).toInt())
            }
            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }

    private fun sendBudgetNotification(spent: Double, limit: Double, percent: Int) {
        createChannel()

        val intent = Intent(applicationContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pending = PendingIntent.getActivity(
            applicationContext, NOTIFICATION_ID, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val spentStr = String.format("%,.0f", spent).replace(",", ".")
        val limitStr = String.format("%,.0f", limit).replace(",", ".")

        val message = when {
            percent >= 100 -> "Aylık bütçenizi aştınız! ($spentStr / $limitStr)"
            percent >= 90  -> "Bütçenizin %$percent'ini harcadınız — yavaşlayın!"
            else           -> "Bütçenizin %$percent'ini kullandınız ($spentStr / $limitStr)"
        }

        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("⚠️ Bütçe Uyarısı")
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pending)
            .setAutoCancel(true)
            .build()

        val nm = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIFICATION_ID, notification)
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, CHANNEL_NAME, NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Aylık bütçe eşiği uyarıları"
            }
            val nm = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(channel)
        }
    }
}
