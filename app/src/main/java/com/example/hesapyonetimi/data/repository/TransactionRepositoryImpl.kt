package com.example.hesapyonetimi.data.repository

import com.example.hesapyonetimi.data.local.dao.CategoryDao
import com.example.hesapyonetimi.data.local.dao.TransactionDao
import com.example.hesapyonetimi.data.mapper.toDomain
import com.example.hesapyonetimi.data.mapper.toEntity
import com.example.hesapyonetimi.domain.model.Transaction
import com.example.hesapyonetimi.domain.repository.TransactionRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class TransactionRepositoryImpl @Inject constructor(
    private val transactionDao: TransactionDao,
    private val categoryDao: CategoryDao
) : TransactionRepository {
    
    override suspend fun insertTransaction(transaction: Transaction): Long {
        return transactionDao.insert(transaction.toEntity())
    }
    
    override suspend fun updateTransaction(transaction: Transaction) {
        transactionDao.update(transaction.toEntity())
    }
    
    override suspend fun deleteTransaction(transaction: Transaction) {
        transactionDao.delete(transaction.toEntity())
    }
    
    override fun getAllTransactions(): Flow<List<Transaction>> {
        return transactionDao.getAllTransactions().map { entities ->
            entities.map { entity ->
                val category = categoryDao.getCategoryById(entity.categoryId)
                entity.toDomain(category)
            }
        }
    }
    
    override fun getTransactionsByDateRange(startDate: Long, endDate: Long): Flow<List<Transaction>> {
        return transactionDao.getTransactionsByDateRange(startDate, endDate).map { entities ->
            entities.map { entity ->
                val category = categoryDao.getCategoryById(entity.categoryId)
                entity.toDomain(category)
            }
        }
    }
    
    override fun getTransactionsByCategory(categoryId: Long): Flow<List<Transaction>> {
        return transactionDao.getTransactionsByCategory(categoryId).map { entities ->
            entities.map { entity ->
                val category = categoryDao.getCategoryById(entity.categoryId)
                entity.toDomain(category)
            }
        }
    }
    
    override suspend fun getTotalIncome(startDate: Long, endDate: Long): Double {
        return transactionDao.getTotalIncome(startDate, endDate) ?: 0.0
    }
    
    override suspend fun getTotalExpense(startDate: Long, endDate: Long): Double {
        return transactionDao.getTotalExpense(startDate, endDate) ?: 0.0
    }
}
