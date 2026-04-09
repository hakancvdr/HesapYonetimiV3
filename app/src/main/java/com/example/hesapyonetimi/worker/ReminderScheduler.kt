package com.example.hesapyonetimi.worker

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.work.WorkManager
import com.example.hesapyonetimi.domain.model.Reminder
import java.util.*
import java.util.concurrent.TimeUnit

/**
 * 5-Aşamalı Hatırlatıcı Sistemi — AlarmManager tabanlı
 *
 * WorkManager setInitialDelay() → Doze modunda saatlerce gecikebilir.
 * AlarmManager.setExactAndAllowWhileIdle() → Doze modunda bile zamanında çalışır.
 *
 * Akış:
 * ReminderScheduler.schedule() → AlarmManager → ReminderAlarmReceiver → WorkManager(delay=0) → Bildirim
 *
 * dueDate = 15 Nisan 13:00 ise:
 * Aşama 1 → 13 Nisan 13:00  "Yakında ödeme günü"
 * Aşama 2 → 14 Nisan 13:00  "Yarın son gün"
 * Aşama 3 → 15 Nisan 13:00  "Bugün son gün"
 * Aşama 4 → 15 Nisan 12:00  "Son saatler"   (dueDate - 1 saat)
 * Aşama 5 → 16 Nisan 13:00  "Geçmiş ödeme"
 */
object ReminderScheduler {

    fun schedule(context: Context, reminder: Reminder) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val dueMs = reminder.dueDate
        val stages = computeStages(dueMs)

        stages.forEachIndexed { idx, triggerMs ->
            val delay = triggerMs - System.currentTimeMillis()
            if (delay <= 0) return@forEachIndexed  // geçmiş aşamaları atla

            val stage = idx + 1
            val pendingIntent = buildPendingIntent(context, reminder, stage)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                // Android 12+: canScheduleExactAlarms() kontrolü
                if (alarmManager.canScheduleExactAlarms()) {
                    alarmManager.setExactAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP, triggerMs, pendingIntent
                    )
                } else {
                    // İzin yoksa inexact alarm (daha az güvenilir ama yine de çalışır)
                    alarmManager.setAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP, triggerMs, pendingIntent
                    )
                }
            } else {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP, triggerMs, pendingIntent
                )
            }
        }
    }

    fun cancel(context: Context, reminderId: Long) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

        // 5 aşamanın tüm alarmlarını iptal et
        for (stage in 1..5) {
            val intent = Intent(context, ReminderAlarmReceiver::class.java).apply {
                action = ReminderAlarmReceiver.ACTION_REMINDER
            }
            val requestCode = alarmRequestCode(reminderId, stage)
            val pendingIntent = PendingIntent.getBroadcast(
                context, requestCode, intent,
                PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
            )
            pendingIntent?.let { alarmManager.cancel(it) }
        }

        // WorkManager tag'lerini de temizle
        WorkManager.getInstance(context).cancelAllWorkByTag("reminder_$reminderId")
    }

    // ─────────────────────────────────────────────────────────────────────────

    private fun buildPendingIntent(context: Context, reminder: Reminder, stage: Int): PendingIntent {
        val intent = Intent(context, ReminderAlarmReceiver::class.java).apply {
            action = ReminderAlarmReceiver.ACTION_REMINDER
            putExtra(ReminderAlarmReceiver.KEY_REMINDER_ID,     reminder.id)
            putExtra(ReminderAlarmReceiver.KEY_REMINDER_TITLE,  reminder.title)
            putExtra(ReminderAlarmReceiver.KEY_REMINDER_AMOUNT, reminder.amount)
            putExtra(ReminderAlarmReceiver.KEY_STAGE,           stage)
        }
        return PendingIntent.getBroadcast(
            context,
            alarmRequestCode(reminder.id, stage),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun alarmRequestCode(reminderId: Long, stage: Int): Int =
        (reminderId * 10 + stage).toInt()

    /**
     * 5 tetiklenme anını döndürür (epoch ms).
     */
    private fun computeStages(dueDateMs: Long): List<Long> {
        val cal = Calendar.getInstance().apply { timeInMillis = dueDateMs }
        val dueHour   = cal.get(Calendar.HOUR_OF_DAY)
        val dueMinute = cal.get(Calendar.MINUTE)

        fun dateAtHourMin(offsetDays: Int, hour: Int, minute: Int = 0): Long {
            return Calendar.getInstance().apply {
                timeInMillis = dueDateMs
                add(Calendar.DAY_OF_YEAR, offsetDays)
                set(Calendar.HOUR_OF_DAY, hour)
                set(Calendar.MINUTE, minute)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }.timeInMillis
        }

        return listOf(
            dateAtHourMin(-2, dueHour, dueMinute),   // Aşama 1: 2 gün önce
            dateAtHourMin(-1, dueHour, dueMinute),   // Aşama 2: 1 gün önce
            dateAtHourMin(0,  dueHour, dueMinute),   // Aşama 3: Aynı gün
            dueDateMs - TimeUnit.HOURS.toMillis(1),  // Aşama 4: 1 saat önce
            dateAtHourMin(1,  dueHour, dueMinute)    // Aşama 5: 1 gün sonra
        )
    }
}
