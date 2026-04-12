package com.example.hesapyonetimi.worker

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.hesapyonetimi.di.ReminderRescheduleEntryPoint
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first

/**
 * Cihaz yeniden başladığında AlarmManager alarmları silinir.
 * Ödenmemiş hatırlatıcılar için tüm gelecek bildirim anları yeniden kurulur.
 */
class ReminderBootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val pendingResult = goAsync()
        val appContext = context.applicationContext
        CoroutineScope(Dispatchers.IO + SupervisorJob()).launch {
            try {
                val ep = EntryPointAccessors.fromApplication(
                    appContext,
                    ReminderRescheduleEntryPoint::class.java
                )
                val unpaid = ep.reminderRepository().getUnpaidReminders().first()
                unpaid.forEach { ReminderScheduler.schedule(appContext, it) }
            } finally {
                pendingResult.finish()
            }
        }
    }
}
