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
        const val KEY_STAGE = "stage"
        const val ACTION_ODENDI = "com.example.hesapyonetimi.ACTION_ODENDI"
    }

    override suspend fun doWork(): Result {
        val reminderId = inputData.getLong(KEY_REMINDER_ID, -1L)
        val title  = inputData.getString(KEY_REMINDER_TITLE) ?: return Result.failure()
        val amount = inputData.getDouble(KEY_REMINDER_AMOUNT, 0.0)
        val stage  = inputData.getInt(KEY_STAGE, 3)

        if (reminderId == -1L) return Result.failure()

        // Zaten ödendiyse bildirim gösterme
        val reminders = reminderRepository.getAllReminders().first()
        val reminder = reminders.firstOrNull { it.id == reminderId }
        if (reminder?.isPaid == true) return Result.success()

        createNotificationChannel()

        val notifId = (reminderId * 10 + stage).toInt()

        // ── Bildirime tıklayınca Yaklaşan sayfasına git ────────────────────────
        val openIntent = Intent(applicationContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("open_tab", "yaklasan")
            putExtra("highlight_reminder_id", reminderId)
        }
        val openPendingIntent = PendingIntent.getActivity(
            applicationContext, notifId, openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // ── "Ödendi" aksiyonu ─────────────────────────────────────────────────
        val odendiIntent = Intent(applicationContext, OdendiReceiver::class.java).apply {
            action = ACTION_ODENDI
            putExtra(KEY_REMINDER_ID, reminderId)
            putExtra(KEY_REMINDER_TITLE, title)
            putExtra(KEY_REMINDER_AMOUNT, amount)
        }
        val odendiPendingIntent = PendingIntent.getBroadcast(
            applicationContext, notifId + 1000, odendiIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // ── "Sonra Hatırlat" aksiyonu (1 saat sonra) ─────────────────────────
        val sonraIntent = Intent(applicationContext, SonraReceiver::class.java).apply {
            action = SonraReceiver.ACTION_SONRA
            putExtra(KEY_REMINDER_ID, reminderId)
            putExtra(KEY_REMINDER_TITLE, title)
            putExtra(KEY_REMINDER_AMOUNT, amount)
            putExtra(KEY_STAGE, stage)
            putExtra(SonraReceiver.KEY_NOTIF_ID, notifId)
        }
        val sonraPendingIntent = PendingIntent.getBroadcast(
            applicationContext, notifId + 2000, sonraIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val amountStr = String.format("%,.0f", amount).replace(",", ".")

        val (notifTitle, notifBody) = when (stage) {
            1 -> "📅 Yakında ödeme gününüz" to "$title — $amountStr ₺ · 2 gün kaldı"
            2 -> "⏰ Yarın son ödeme günü"   to "$title — $amountStr ₺ · Yarın son gün!"
            3 -> "⚠️ Bugün son ödeme günü"  to "$title için $amountStr ₺ ödemeniz bugün vadesi dolmaktadır."
            4 -> "🔔 Ödemenizin son saatleri" to "$title — $amountStr ₺ · Sadece 1 saat kaldı!"
            5 -> "❗ Geçmiş ödemeniz var"    to "$title için $amountStr ₺ ödeme vadesi geçti."
            else -> "💳 $title ödemesi" to "$amountStr ₺ · Unutmadan öde"
        }

        val priority = if (stage >= 3) NotificationCompat.PRIORITY_HIGH else NotificationCompat.PRIORITY_DEFAULT

        val builder = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(notifTitle)
            .setContentText(notifBody)
            .setStyle(NotificationCompat.BigTextStyle().bigText(notifBody))
            .setPriority(priority)
            .setContentIntent(openPendingIntent)   // tıklama → Yaklaşan sayfası
            .setAutoCancel(true)
            .addAction(0, "✓ Ödendi", odendiPendingIntent)
            .addAction(0, "🔕 Sonra Hatırlat", sonraPendingIntent)

        // Stage 5 (gecikmiş) veya stage 3-4 için yüksek öncelik kategori
        if (stage >= 3) {
            builder.setCategory(NotificationCompat.CATEGORY_REMINDER)
        }

        val nm = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(notifId, builder.build())

        return Result.success()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID, CHANNEL_NAME, NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Yaklaşan ödeme hatırlatmaları"
                enableVibration(true)
            }
            val nm = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(channel)
        }
    }
}
