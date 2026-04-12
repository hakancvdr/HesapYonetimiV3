package com.example.hesapyonetimi.data.local.dao

import androidx.room.*
import com.example.hesapyonetimi.data.local.entity.ReminderEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ReminderDao {
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(reminder: ReminderEntity): Long
    
    @Update
    suspend fun update(reminder: ReminderEntity)
    
    @Delete
    suspend fun delete(reminder: ReminderEntity)
    
    @Query("SELECT * FROM reminders ORDER BY dueDate ASC")
    fun getAllReminders(): Flow<List<ReminderEntity>>
    
    @Query("SELECT * FROM reminders WHERE isPaid = 0 ORDER BY dueDate ASC")
    fun getUnpaidReminders(): Flow<List<ReminderEntity>>
    
    @Query("SELECT * FROM reminders WHERE dueDate >= :startDate AND dueDate <= :endDate ORDER BY dueDate ASC")
    fun getRemindersByDateRange(startDate: Long, endDate: Long): Flow<List<ReminderEntity>>
    
    @Query("SELECT * FROM reminders WHERE isRecurring = 1")
    fun getRecurringReminders(): Flow<List<ReminderEntity>>
    
    @Query("UPDATE reminders SET isPaid = :isPaid WHERE id = :id")
    suspend fun updatePaidStatus(id: Long, isPaid: Boolean)

    @Query("UPDATE reminders SET isPaid = 1, paidAt = :paidAt WHERE id = :id")
    suspend fun markPaidAt(id: Long, paidAt: Long)
    
    @Query("DELETE FROM reminders WHERE isPaid = 1 AND dueDate < :beforeDate")
    suspend fun deletePaidRemindersBefore(beforeDate: Long)

    @Query("DELETE FROM reminders")
    suspend fun deleteAll()

    @Query("SELECT COUNT(*) FROM reminders WHERE isPaid = 0")
    suspend fun countUnpaidReminders(): Int
}
