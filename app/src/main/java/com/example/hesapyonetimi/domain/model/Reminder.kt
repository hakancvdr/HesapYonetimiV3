package com.example.hesapyonetimi.domain.model

import com.example.hesapyonetimi.data.local.entity.RecurringType

data class Reminder(
    val id: Long = 0,
    val title: String,
    val amount: Double,
    val dueDate: Long,
    val isPaid: Boolean = false,
    val isRecurring: Boolean = false,
    val recurringType: RecurringType? = null,
    val daysUntilDue: Int = 0
) {
    val isOverdue: Boolean
        get() = !isPaid && dueDate < System.currentTimeMillis()
}
