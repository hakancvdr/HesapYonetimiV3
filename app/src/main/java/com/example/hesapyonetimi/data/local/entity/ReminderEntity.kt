package com.example.hesapyonetimi.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.hesapyonetimi.domain.model.ReminderNotificationPolicy

@Entity(tableName = "reminders")
data class ReminderEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val title: String,
    val amount: Double,
    val dueDate: Long,
    val categoryId: Long = 0,
    val isPaid: Boolean = false,
    val paidAt: Long? = null,
    val isRecurring: Boolean = false,
    val recurringType: RecurringType? = null,
    val totalDonem: Int = 0,      // 0 = sınırsız tekrar; 1 = tek dönem; >1 = en fazla bu kadar dönem
    val donemIndex: Int = 0,      // bu hatırlatıcı kaçıncı dönem (1-based)
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    /** [ReminderNotificationPolicy] adı */
    val notificationPolicy: String = ReminderNotificationPolicy.LEGACY_MULTI.name
)

enum class RecurringType {
    DAILY, WEEKLY, MONTHLY, YEARLY
}
