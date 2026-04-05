package com.example.hesapyonetimi.data.repository

import com.example.hesapyonetimi.data.local.dao.BudgetDao
import com.example.hesapyonetimi.data.local.dao.CategoryDao
import com.example.hesapyonetimi.data.local.dao.TransactionDao
import com.example.hesapyonetimi.data.mapper.toDomain
import com.example.hesapyonetimi.data.mapper.toEntity
import com.example.hesapyonetimi.domain.model.Budget
import com.example.hesapyonetimi.domain.repository.BudgetRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject

class BudgetRepositoryImpl @Inject constructor(
    private val budgetDao: BudgetDao,
    private val categoryDao: CategoryDao,
    private val transactionDao: TransactionDao
) : BudgetRepository {
    
    override suspend fun insertBudget(budget: Budget): Long {
        return budgetDao.insert(budget.toEntity())
    }
    
    override suspend fun updateBudget(budget: Budget) {
        budgetDao.update(budget.toEntity())
    }
    
    override suspend fun deleteBudget(budget: Budget) {
        budgetDao.delete(budget.toEntity())
    }
    
    override fun getBudgetsByMonth(yearMonth: String): Flow<List<Budget>> {
        return budgetDao.getBudgetsByMonth(yearMonth).map { entities ->
            entities.map { entity ->
                val category = categoryDao.getCategoryById(entity.categoryId)
                
                // O ay kategoriye yapılan harcamaları hesapla
                val (startDate, endDate) = getMonthDateRange(yearMonth)
                val spent = transactionDao.getTransactionsByDateRange(startDate, endDate)
                    .map { transactions ->
                        transactions
                            .filter { it.categoryId == entity.categoryId && !it.isIncome }
                            .sumOf { it.amount }
                    }
                
                // Flow'dan değer almak için suspend fonksiyon kullanmalıyız
                // Geçici olarak 0.0 koyuyoruz, ViewModel'de düzgün hesaplayacağız
                entity.toDomain(category, 0.0)
            }
        }
    }
    
    override suspend fun getBudgetByCategoryAndMonth(categoryId: Long, yearMonth: String): Budget? {
        val entity = budgetDao.getBudgetByCategoryAndMonth(categoryId, yearMonth) ?: return null
        val category = categoryDao.getCategoryById(entity.categoryId)
        
        val (startDate, endDate) = getMonthDateRange(yearMonth)
        val spent = transactionDao.getTotalExpense(startDate, endDate) ?: 0.0
        
        return entity.toDomain(category, spent)
    }
    
    private fun getMonthDateRange(yearMonth: String): Pair<Long, Long> {
        val format = SimpleDateFormat("yyyy-MM", Locale.getDefault())
        val date = format.parse(yearMonth) ?: Date()
        val calendar = Calendar.getInstance().apply { time = date }
        
        calendar.set(Calendar.DAY_OF_MONTH, 1)
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        val startDate = calendar.timeInMillis
        
        calendar.set(Calendar.DAY_OF_MONTH, calendar.getActualMaximum(Calendar.DAY_OF_MONTH))
        calendar.set(Calendar.HOUR_OF_DAY, 23)
        calendar.set(Calendar.MINUTE, 59)
        calendar.set(Calendar.SECOND, 59)
        calendar.set(Calendar.MILLISECOND, 999)
        val endDate = calendar.timeInMillis
        
        return startDate to endDate
    }
}
