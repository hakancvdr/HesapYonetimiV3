package com.example.hesapyonetimi.domain.repository

import com.example.hesapyonetimi.domain.model.Transaction
import com.example.hesapyonetimi.domain.model.Category
import com.example.hesapyonetimi.domain.model.Budget
import com.example.hesapyonetimi.domain.model.Reminder
import kotlinx.coroutines.flow.Flow

interface TransactionRepository {
    suspend fun insertTransaction(transaction: Transaction): Long
    suspend fun updateTransaction(transaction: Transaction)
    suspend fun deleteTransaction(transaction: Transaction)
    fun getAllTransactions(): Flow<List<Transaction>>
    fun getTransactionsByDateRange(startDate: Long, endDate: Long): Flow<List<Transaction>>
    fun getTransactionsByCategory(categoryId: Long): Flow<List<Transaction>>
    suspend fun getTotalIncome(startDate: Long, endDate: Long): Double
    suspend fun getTotalExpense(startDate: Long, endDate: Long): Double
}

interface CategoryRepository {
    suspend fun insertCategory(category: Category): Long
    suspend fun updateCategory(category: Category)
    suspend fun deleteCategory(category: Category)
    fun getAllCategories(): Flow<List<Category>>
    fun getCategoriesByType(isIncome: Boolean): Flow<List<Category>>
    suspend fun getCategoryById(id: Long): Category?
}

interface BudgetRepository {
    suspend fun insertBudget(budget: Budget): Long
    suspend fun updateBudget(budget: Budget)
    suspend fun deleteBudget(budget: Budget)
    fun getBudgetsByMonth(yearMonth: String): Flow<List<Budget>>
    suspend fun getBudgetByCategoryAndMonth(categoryId: Long, yearMonth: String): Budget?
}

interface ReminderRepository {
    suspend fun insertReminder(reminder: Reminder): Long
    suspend fun updateReminder(reminder: Reminder)
    suspend fun deleteReminder(reminder: Reminder)
    fun getAllReminders(): Flow<List<Reminder>>
    fun getUnpaidReminders(): Flow<List<Reminder>>
    suspend fun markAsPaid(id: Long)
}
