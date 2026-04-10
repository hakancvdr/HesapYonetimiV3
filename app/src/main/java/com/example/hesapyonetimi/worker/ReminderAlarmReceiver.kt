package com.example.hesapyonetimi.worker

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf

/**
 * AlarmManager tarafından tetiklenir → ReminderNotificationWorker'ı 0 gecikmeyle çalıştırır.
 * WorkManager ile karşılaştırıldığında: AlarmManager Doze modunda bile zamanında tetikler,
 * WorkManager ise pil optimizasyonu yüzünden saatlerce gecikebilir.
 */
class ReminderAlarmReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_REMINDER = "com.example.hesapyonetimi.ACTION_REMINDER_ALARM"
        const val KEY_REMINDER_ID     = "reminder_id"
        const val KEY_REMINDER_TITLE  = "reminder_title"
        const val KEY_REMINDER_AMOUNT = "reminder_amount"
        const val KEY_STAGE           = "stage"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_REMINDER) return

        val reminderId = intent.getLongExtra(KEY_REMINDER_ID, -1L)
        val title      = intent.getStringExtra(KEY_REMINDER_TITLE) ?: return
        val amount     = intent.getDoubleExtra(KEY_REMINDER_AMOUNT, 0.0)
        val stage      = intent.getIntExtra(KEY_STAGE, 3)

        if (reminderId == -1L) return

        // Hilt Worker'ı 0 gecikmeyle çalıştır (WorkManager + HiltWorkerFactory kullanır)
        val data = workDataOf(
            ReminderNotificationWorker.KEY_REMINDER_ID     to reminderId,
            ReminderNotificationWorker.KEY_REMINDER_TITLE  to title,
            ReminderNotificationWorker.KEY_REMINDER_AMOUNT to amount,
            ReminderNotificationWorker.KEY_STAGE           to stage
        )

        WorkManager.getInstance(context).enqueue(
            OneTimeWorkRequestBuilder<ReminderNotificationWorker>()
                .setInputData(data)
                .addTag("reminder_${reminderId}")
                .build()
        )
    }
}
