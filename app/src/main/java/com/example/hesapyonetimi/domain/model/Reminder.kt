package com.example.hesapyonetimi.domain.model

import com.example.hesapyonetimi.data.local.entity.RecurringType

data class Reminder(
    val id: Long = 0,
    val title: String,           // açıklama (Netflix, Kira...)
    val amount: Double,
    val dueDate: Long,
    val categoryId: Long = 0,
    val categoryName: String = "",
    val categoryIcon: String = "",
    val isPaid: Boolean = false,
    val paidAt: Long? = null,
    val isRecurring: Boolean = false,
    val recurringType: RecurringType? = null,
    val daysUntilDue: Int = 0,
    /** 0 = sınırsız tekrar; 1 = tek dönem; 1'den büyük = en fazla bu kadar dönem */
    val totalDonem: Int = 0,
    val donemIndex: Int = 0,
    val notificationPolicy: ReminderNotificationPolicy = ReminderNotificationPolicy.LEGACY_MULTI
) {
    val isOverdue: Boolean
        get() = !isPaid && dueDate < System.currentTimeMillis()

    val calendarDisplayDate: Long
        get() = if (isPaid && paidAt != null) paidAt else dueDate
}
