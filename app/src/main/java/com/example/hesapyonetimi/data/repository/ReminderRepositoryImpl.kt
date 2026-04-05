package com.example.hesapyonetimi.data.repository

import com.example.hesapyonetimi.data.local.dao.ReminderDao
import com.example.hesapyonetimi.data.mapper.toDomain
import com.example.hesapyonetimi.data.mapper.toEntity
import com.example.hesapyonetimi.domain.model.Reminder
import com.example.hesapyonetimi.domain.repository.ReminderRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class ReminderRepositoryImpl @Inject constructor(
    private val reminderDao: ReminderDao
) : ReminderRepository {
    
    override suspend fun insertReminder(reminder: Reminder): Long {
        return reminderDao.insert(reminder.toEntity())
    }
    
    override suspend fun updateReminder(reminder: Reminder) {
        reminderDao.update(reminder.toEntity())
    }
    
    override suspend fun deleteReminder(reminder: Reminder) {
        reminderDao.delete(reminder.toEntity())
    }
    
    override fun getAllReminders(): Flow<List<Reminder>> {
        return reminderDao.getAllReminders().map { entities ->
            entities.map { it.toDomain() }
        }
    }
    
    override fun getUnpaidReminders(): Flow<List<Reminder>> {
        return reminderDao.getUnpaidReminders().map { entities ->
            entities.map { it.toDomain() }
        }
    }
    
    override suspend fun markAsPaid(id: Long) {
        reminderDao.updatePaidStatus(id, true)
    }
}
