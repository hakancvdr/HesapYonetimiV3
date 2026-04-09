package com.example.hesapyonetimi.worker

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.work.WorkManager
import com.example.hesapyonetimi.domain.repository.ReminderRepository
import com.example.hesapyonetimi.domain.repository.TransactionRepository
import com.example.hesapyonetimi.domain.model.Transaction
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class OdendiReceiver : BroadcastReceiver() {

    @Inject lateinit var reminderRepository: ReminderRepository
    @Inject lateinit var transactionRepository: TransactionRepository

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ReminderNotificationWorker.ACTION_ODENDI) return

        val reminderId = intent.getLongExtra(ReminderNotificationWorker.KEY_REMINDER_ID, -1L)
        if (reminderId == -1L) return

        CoroutineScope(Dispatchers.IO).launch {
            // Ödendi işaretle
            reminderRepository.markAsPaid(reminderId)

            // Günlük takibe ekle
            val reminders = reminderRepository.getAllReminders().first()
            val reminder = reminders.firstOrNull { it.id == reminderId } ?: return@launch

            val transaction = Transaction(
                amount = reminder.amount,
                categoryId = reminder.categoryId,
                description = reminder.title,
                date = System.currentTimeMillis(),
                isIncome = false
            )
            transactionRepository.insertTransaction(transaction)

            // AlarmManager + WorkManager temizle
            ReminderScheduler.cancel(context, reminderId)

            // 5 aşamanın tüm bildirimlerini kapat
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            for (stage in 1..5) {
                nm.cancel((reminderId * 10 + stage).toInt())
            }
        }
    }
}
