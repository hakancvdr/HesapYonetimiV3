package com.example.hesapyonetimi.data.local.dao

import androidx.room.*
import com.example.hesapyonetimi.data.local.entity.BudgetEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface BudgetDao {
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(budget: BudgetEntity): Long
    
    @Update
    suspend fun update(budget: BudgetEntity)
    
    @Delete
    suspend fun delete(budget: BudgetEntity)
    
    @Query("SELECT * FROM budgets ORDER BY yearMonth DESC")
    fun getAllBudgets(): Flow<List<BudgetEntity>>
    
    @Query("SELECT * FROM budgets WHERE yearMonth = :yearMonth")
    fun getBudgetsByMonth(yearMonth: String): Flow<List<BudgetEntity>>
    
    @Query("SELECT * FROM budgets WHERE categoryId = :categoryId AND yearMonth = :yearMonth")
    suspend fun getBudgetByCategoryAndMonth(categoryId: Long, yearMonth: String): BudgetEntity?
    
    @Query("DELETE FROM budgets WHERE yearMonth = :yearMonth")
    suspend fun deleteBudgetsByMonth(yearMonth: String)

    @Query("DELETE FROM budgets")
    suspend fun deleteAll()
}
