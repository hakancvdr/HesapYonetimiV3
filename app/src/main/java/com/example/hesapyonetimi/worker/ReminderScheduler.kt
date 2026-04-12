package com.example.hesapyonetimi.worker

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.work.WorkManager
import com.example.hesapyonetimi.domain.model.Reminder
import com.example.hesapyonetimi.domain.model.ReminderNotificationPolicy
import java.util.Calendar
import java.util.concurrent.TimeUnit

/**
 * Hatırlatıcı bildirimleri — AlarmManager (`setAlarmClock`) ile **bağımsız** zamanlar.
 *
 * Bu yapı **sıralı bir “aşama oyunu” değildir**: Her bildirim anı, vadeye göre hesaplanır ve
 * yalnızca **o an henüz gelmemişse** kurulur. Bir an geçmiş olsa bile (ör. 2 gün öncesi),
 * diğer anlar (ör. vade 1 saat önce) **kendi başına** değerlendirilir; zincirde “önceki
 * aşama atlandı” diye sonraki anlar iptal edilmez.
 *
 * Hatırlatıcı istediğiniz zaman eklenebilir; her kayıtta yalnızca **gelecekte kalan** anlar
 * planlanır. `schedule` önce mevcut alarmları temizler; uygulama açılışı ve BOOT_COMPLETED
 * ile ödenmemiş kayıtlar yeniden planlanır.
 *
 * Akış: `schedule` → AlarmManager → ReminderAlarmReceiver → WorkManager → bildirim
 *
 * Örnek (vade 15 Nisan 13:00): aynı saat kuralıyla 2 gün önce / 1 gün önce / tam vade /
 * vade − 1 saat / 1 gün sonra — her biri ayrı alarm; geçmiş olanlar kurulmaz.
 */
object ReminderScheduler {

    private data class NotificationSlot(
        val triggerAtMs: Long,
        /** Bildirim metni için 1..5 ([ReminderNotificationWorker]) */
        val kind: Int
    )

    fun schedule(context: Context, reminder: Reminder) {
        cancel(context, reminder.id)

        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val rawSlots = buildNotificationSlots(reminder.dueDate, reminder.notificationPolicy)
        // Aynı milisaniyede iki tetik varsa tek alarm (yüksek öncelik = daha küçük kind)
        val slots = rawSlots
            .groupBy { it.triggerAtMs }
            .values
            .map { group -> group.minBy { it.kind } }

        var scheduled = 0
        slots.forEach { slot ->
            if (slot.triggerAtMs <= System.currentTimeMillis()) return@forEach
            val pendingIntent = buildPendingIntent(context, reminder, slot.kind)
            val info = AlarmManager.AlarmClockInfo(slot.triggerAtMs, null)
            alarmManager.setAlarmClock(info, pendingIntent)
            scheduled++
        }

        val due = reminder.dueDate
        if (scheduled == 0 && due > System.currentTimeMillis()) {
            val pendingIntent = buildPendingIntent(context, reminder, 3)
            val trigger = due.coerceAtLeast(System.currentTimeMillis() + 2_000L)
            alarmManager.setAlarmClock(AlarmManager.AlarmClockInfo(trigger, null), pendingIntent)
        }
    }

    fun cancel(context: Context, reminderId: Long) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

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

        WorkManager.getInstance(context).cancelAllWorkByTag("reminder_$reminderId")
    }

    private fun buildPendingIntent(context: Context, reminder: Reminder, kind: Int): PendingIntent {
        val intent = Intent(context, ReminderAlarmReceiver::class.java).apply {
            action = ReminderAlarmReceiver.ACTION_REMINDER
            putExtra(ReminderAlarmReceiver.KEY_REMINDER_ID, reminder.id)
            putExtra(ReminderAlarmReceiver.KEY_REMINDER_TITLE, reminder.title)
            putExtra(ReminderAlarmReceiver.KEY_REMINDER_AMOUNT, reminder.amount)
            putExtra(ReminderAlarmReceiver.KEY_STAGE, kind)
        }
        return PendingIntent.getBroadcast(
            context,
            alarmRequestCode(reminder.id, kind),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun alarmRequestCode(reminderId: Long, stage: Int): Int =
        (reminderId * 10 + stage).toInt()

    private fun buildNotificationSlots(dueDateMs: Long, policy: ReminderNotificationPolicy): List<NotificationSlot> {
        val cal = Calendar.getInstance().apply { timeInMillis = dueDateMs }
        val dueHour = cal.get(Calendar.HOUR_OF_DAY)
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

        val oneHourBefore = dueDateMs - TimeUnit.HOURS.toMillis(1)
        val legacy = listOf(
            NotificationSlot(dateAtHourMin(-2, dueHour, dueMinute), 1),
            NotificationSlot(dateAtHourMin(-1, dueHour, dueMinute), 2),
            NotificationSlot(dueDateMs, 3),
            NotificationSlot(oneHourBefore, 4),
            NotificationSlot(dateAtHourMin(1, dueHour, dueMinute), 5)
        )

        return when (policy) {
            ReminderNotificationPolicy.LEGACY_MULTI -> legacy
            ReminderNotificationPolicy.DUE_DAY ->
                listOf(NotificationSlot(dueDateMs, 3))
            ReminderNotificationPolicy.ONE_DAY_BEFORE ->
                listOf(NotificationSlot(dateAtHourMin(-1, dueHour, dueMinute), 2))
            ReminderNotificationPolicy.TWO_DAYS_BEFORE ->
                listOf(NotificationSlot(dateAtHourMin(-2, dueHour, dueMinute), 1))
            ReminderNotificationPolicy.ONE_WEEK_BEFORE ->
                listOf(NotificationSlot(dateAtHourMin(-7, dueHour, dueMinute), 1))
        }
    }
}
