package com.example.hesapyonetimi.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "reminders")
data class ReminderEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    
    val title: String,
    
    val amount: Double,
    
    val dueDate: Long, // Timestamp (hatırlatıcı tarihi)
    
    val isPaid: Boolean = false,
    
    val isRecurring: Boolean = false, // Tekrarlayan mı?
    
    val recurringType: RecurringType? = null, // MONTHLY, WEEKLY, YEARLY
    
    val createdAt: Long = System.currentTimeMillis(),
    
    val updatedAt: Long = System.currentTimeMillis()
)

enum class RecurringType {
    DAILY,
    WEEKLY,
    MONTHLY,
    YEARLY
}
