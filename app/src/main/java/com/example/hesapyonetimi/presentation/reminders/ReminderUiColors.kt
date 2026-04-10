package com.example.hesapyonetimi.presentation.reminders

import android.content.Context
import androidx.core.content.ContextCompat
import com.example.hesapyonetimi.R
import com.example.hesapyonetimi.domain.model.Reminder
import java.util.Calendar

object ReminderUiColors {

    fun statusColor(context: Context, reminder: Reminder): Int {
        val ctx = context

        // Same thresholds as HatirlaticiAdapter
        val todayStart = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        val daysLeft = ((reminder.dueDate - todayStart) / (1000 * 60 * 60 * 24)).toInt()

        return when {
            reminder.isOverdue -> 0xFFD32F2F.toInt()
            daysLeft == 0 -> 0xFFF57C00.toInt()
            daysLeft <= 3 -> 0xFFF57C00.toInt()
            reminder.isPaid -> ContextCompat.getColor(ctx, R.color.green_primary)
            else -> ContextCompat.getColor(ctx, R.color.green_primary)
        }
    }
}

