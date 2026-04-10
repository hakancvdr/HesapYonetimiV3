package com.example.hesapyonetimi.worker

import android.app.AlarmManager
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build

/**
 * "Sonra Hatırlat" aksiyonu — bildirimi 1 saat sonra yeniden gösterir.
 * WorkManager yerine AlarmManager kullanır (Doze modunda güvenilir tetikleme).
 */
class SonraReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_SONRA   = "com.example.hesapyonetimi.ACTION_SONRA"
        const val KEY_NOTIF_ID   = "notif_id"
        // ReminderAlarmReceiver KEY sabitlerini import etmek yerine tekrar tanımlanıyor
        private const val KEY_ID     = "reminder_id"
        private const val KEY_TITLE  = "reminder_title"
        private const val KEY_AMOUNT = "reminder_amount"
        private const val KEY_STAGE  = "stage"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_SONRA) return

        val reminderId = intent.getLongExtra(ReminderNotificationWorker.KEY_REMINDER_ID, -1L)
        val title      = intent.getStringExtra(ReminderNotificationWorker.KEY_REMINDER_TITLE) ?: return
        val amount     = intent.getDoubleExtra(ReminderNotificationWorker.KEY_REMINDER_AMOUNT, 0.0)
        val stage      = intent.getIntExtra(ReminderNotificationWorker.KEY_STAGE, 3)
        val notifId    = intent.getIntExtra(KEY_NOTIF_ID, reminderId.toInt())

        if (reminderId == -1L) return

        // Mevcut bildirimi kapat
        (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
            .cancel(notifId)

        // 1 saat sonraya alarm kur (AlarmManager → ReminderAlarmReceiver → Worker → Bildirim)
        val triggerAt = System.currentTimeMillis() + 60 * 60 * 1000L

        val alarmIntent = Intent(context, ReminderAlarmReceiver::class.java).apply {
            action = ReminderAlarmReceiver.ACTION_REMINDER
            putExtra(ReminderAlarmReceiver.KEY_REMINDER_ID,     reminderId)
            putExtra(ReminderAlarmReceiver.KEY_REMINDER_TITLE,  title)
            putExtra(ReminderAlarmReceiver.KEY_REMINDER_AMOUNT, amount)
            putExtra(ReminderAlarmReceiver.KEY_STAGE,           stage)
        }
        // Snooze için requestCode = notifId + 5000 (stage alarmlarıyla çakışmasın)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            notifId + 5000,
            alarmIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (alarmManager.canScheduleExactAlarms()) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent)
            } else {
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent)
            }
        } else {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent)
        }
    }
}
