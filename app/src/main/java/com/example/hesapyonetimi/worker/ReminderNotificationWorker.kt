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
import com.example.hesapyonetimi.domain.repository.ReminderRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first

@HiltWorker
class ReminderNotificationWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val reminderRepository: ReminderRepository
) : CoroutineWorker(context, params) {

    companion object {
        const val CHANNEL_ID = "reminder_channel"
        const val CHANNEL_NAME = "Hatırlatıcılar"
        const val KEY_REMINDER_ID = "reminder_id"
        const val KEY_REMINDER_TITLE = "reminder_title"
        const val KEY_REMINDER_AMOUNT = "reminder_amount"
        const val ACTION_ODENDI = "com.example.hesapyonetimi.ACTION_ODENDI"
    }

    override suspend fun doWork(): Result {
        val reminderId = inputData.getLong(KEY_REMINDER_ID, -1L)
        val title = inputData.getString(KEY_REMINDER_TITLE) ?: return Result.failure()
        val amount = inputData.getDouble(KEY_REMINDER_AMOUNT, 0.0)

        if (reminderId == -1L) return Result.failure()

        createNotificationChannel()

        // Ana uygulama açma intent'i
        val openIntent = Intent(applicationContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("open_tab", "yaklasan")
        }
        val openPendingIntent = PendingIntent.getActivity(
            applicationContext, reminderId.toInt(), openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Ödendi action intent'i
        val odendiIntent = Intent(applicationContext, OdendiReceiver::class.java).apply {
            action = ACTION_ODENDI
            putExtra(KEY_REMINDER_ID, reminderId)
        }
        val odendiPendingIntent = PendingIntent.getBroadcast(
            applicationContext, reminderId.toInt() + 1000, odendiIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val amountStr = String.format("%,.0f", amount).replace(",", ".")

        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("💳 $title ödemesi bugün!")
            .setContentText("$amountStr ₺ · Unutmadan öde")
            .setStyle(NotificationCompat.BigTextStyle()
                .bigText("$title için $amountStr ₺ ödeme bugün vadesi dolmaktadır."))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(openPendingIntent)
            .addAction(0, "✓ Ödendi", odendiPendingIntent)
            .setAutoCancel(true)
            .build()

        val notificationManager = applicationContext
            .getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(reminderId.toInt(), notification)

        return Result.success()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID, CHANNEL_NAME, NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Yaklaşan ödeme hatırlatmaları"
            }
            val nm = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(channel)
        }
    }
}
