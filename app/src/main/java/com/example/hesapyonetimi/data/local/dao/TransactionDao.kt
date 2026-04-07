package com.example.hesapyonetimi.data.local.dao

import androidx.room.*
import com.example.hesapyonetimi.data.local.entity.TransactionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TransactionDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(transaction: TransactionEntity): Long

    @Update
    suspend fun update(transaction: TransactionEntity)

    @Delete
    suspend fun delete(transaction: TransactionEntity)

    @Query("SELECT * FROM transactions ORDER BY date DESC")
    fun getAllTransactions(): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM transactions WHERE id = :id")
    suspend fun getTransactionById(id: Long): TransactionEntity?

    @Query("SELECT * FROM transactions WHERE date >= :startDate AND date <= :endDate ORDER BY date DESC")
    fun getTransactionsByDateRange(startDate: Long, endDate: Long): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM transactions WHERE categoryId = :categoryId ORDER BY date DESC")
    fun getTransactionsByCategory(categoryId: Long): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM transactions WHERE isIncome = :isIncome ORDER BY date DESC")
    fun getTransactionsByType(isIncome: Boolean): Flow<List<TransactionEntity>>

    @Query("SELECT SUM(amount) FROM transactions WHERE isIncome = 1 AND date >= :startDate AND date <= :endDate")
    suspend fun getTotalIncome(startDate: Long, endDate: Long): Double?

    @Query("SELECT SUM(amount) FROM transactions WHERE isIncome = 0 AND date >= :startDate AND date <= :endDate")
    suspend fun getTotalExpense(startDate: Long, endDate: Long): Double?

    @Query("DELETE FROM transactions")
    suspend fun deleteAll()

    // ── Profil istatistikleri için ── YENİ ────────────────────────────────────

    @Query("SELECT COUNT(*) FROM transactions")
    suspend fun getTotalTransactionCount(): Int

    @Query("SELECT MIN(date) FROM transactions")
    suspend fun getFirstTransactionDate(): Long?
}
