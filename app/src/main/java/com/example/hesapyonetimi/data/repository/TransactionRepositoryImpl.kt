package com.example.hesapyonetimi.data.repository

import com.example.hesapyonetimi.data.local.dao.CategoryDao
import com.example.hesapyonetimi.data.local.dao.TransactionDao
import com.example.hesapyonetimi.data.mapper.toDomain
import com.example.hesapyonetimi.data.mapper.toEntity
import com.example.hesapyonetimi.domain.model.Transaction
import com.example.hesapyonetimi.domain.repository.TransactionRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
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
        return combine(
            transactionDao.getAllTransactions(),
            categoryDao.getAllCategories()
        ) { entities, categories ->
            val byId = categories.associateBy { it.id }
            entities.map { entity -> entity.toDomain(byId[entity.categoryId]) }
        }
    }

    override fun getTransactionsByDateRange(startDate: Long, endDate: Long): Flow<List<Transaction>> {
        return combine(
            transactionDao.getTransactionsByDateRange(startDate, endDate),
            categoryDao.getAllCategories()
        ) { entities, categories ->
            val byId = categories.associateBy { it.id }
            entities.map { entity -> entity.toDomain(byId[entity.categoryId]) }
        }
    }

    override fun getTransactionsByCategory(categoryId: Long): Flow<List<Transaction>> {
        return combine(
            transactionDao.getTransactionsByCategory(categoryId),
            categoryDao.getAllCategories()
        ) { entities, categories ->
            val byId = categories.associateBy { it.id }
            entities.map { entity -> entity.toDomain(byId[entity.categoryId]) }
        }
    }
    
    override suspend fun getTotalIncome(startDate: Long, endDate: Long): Double {
        return transactionDao.getTotalIncome(startDate, endDate) ?: 0.0
    }
    
    override suspend fun getTotalExpense(startDate: Long, endDate: Long): Double {
        return transactionDao.getTotalExpense(startDate, endDate) ?: 0.0
    }
}
