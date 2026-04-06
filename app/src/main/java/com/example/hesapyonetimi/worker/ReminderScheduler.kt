package com.example.hesapyonetimi.worker

import android.content.Context
import androidx.work.*
import com.example.hesapyonetimi.domain.model.Reminder
import java.util.*
import java.util.concurrent.TimeUnit

object ReminderScheduler {

    fun schedule(context: Context, reminder: Reminder) {
        val workManager = WorkManager.getInstance(context)

        // İki bildirim zamanı: 09:00 ve 14:00
        listOf(9, 14).forEach { hour ->
            val delay = calculateDelay(reminder.dueDate, hour)
            if (delay <= 0) return@forEach

            val data = workDataOf(
                ReminderNotificationWorker.KEY_REMINDER_ID to reminder.id,
                ReminderNotificationWorker.KEY_REMINDER_TITLE to reminder.title,
                ReminderNotificationWorker.KEY_REMINDER_AMOUNT to reminder.amount
            )

            val request = OneTimeWorkRequestBuilder<ReminderNotificationWorker>()
                .setInitialDelay(delay, TimeUnit.MILLISECONDS)
                .setInputData(data)
                .addTag("reminder_${reminder.id}_${hour}")
                .build()

            workManager.enqueueUniqueWork(
                "reminder_${reminder.id}_${hour}",
                ExistingWorkPolicy.REPLACE,
                request
            )
        }
    }

    fun cancel(context: Context, reminderId: Long) {
        val workManager = WorkManager.getInstance(context)
        workManager.cancelAllWorkByTag("reminder_${reminderId}_9")
        workManager.cancelAllWorkByTag("reminder_${reminderId}_14")
    }

    private fun calculateDelay(dueDate: Long, hour: Int): Long {
        val cal = Calendar.getInstance().apply {
            timeInMillis = dueDate
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        return cal.timeInMillis - System.currentTimeMillis()
    }
}
