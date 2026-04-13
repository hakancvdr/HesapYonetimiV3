package com.example.hesapyonetimi.data.local.dao

import androidx.room.*
import com.example.hesapyonetimi.data.local.entity.CategoryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CategoryDao {
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(category: CategoryEntity): Long
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(categories: List<CategoryEntity>)
    
    @Update
    suspend fun update(category: CategoryEntity)
    
    @Delete
    suspend fun delete(category: CategoryEntity)
    
    @Query("SELECT * FROM categories ORDER BY id ASC")
    fun getAllCategories(): Flow<List<CategoryEntity>>
    
    @Query("SELECT * FROM categories WHERE id = :id")
    suspend fun getCategoryById(id: Long): CategoryEntity?
    
    @Query("SELECT * FROM categories WHERE isIncome = :isIncome ORDER BY id ASC")
    fun getCategoriesByType(isIncome: Boolean): Flow<List<CategoryEntity>>

    @Query("SELECT * FROM categories WHERE isIncome = :isIncome AND parentId IS NULL ORDER BY id ASC")
    fun getTopLevelCategoriesByType(isIncome: Boolean): Flow<List<CategoryEntity>>

    @Query("SELECT * FROM categories WHERE isIncome = :isIncome AND parentId = :parentId ORDER BY id ASC")
    fun getSubcategories(parentId: Long, isIncome: Boolean): Flow<List<CategoryEntity>>

    @Query("SELECT * FROM categories WHERE isIncome = :isIncome AND isLocked = 1 AND name = 'Diğer' LIMIT 1")
    suspend fun getOtherLockedCategory(isIncome: Boolean): CategoryEntity?

    @Query(
        """
        SELECT COUNT(*) FROM categories
        WHERE isIncome = :isIncome
          AND parentId IS NULL
          AND isDefault = 0
          AND isLocked = 0
        """
    )
    suspend fun countUserAddedTopLevel(isIncome: Boolean): Int

    @Query("SELECT id FROM categories WHERE parentId = :parentId")
    suspend fun getSubcategoryIds(parentId: Long): List<Long>

    @Query("DELETE FROM categories WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<Long>)

    @Query("DELETE FROM categories WHERE id = :id")
    suspend fun deleteById(id: Long)
    
    @Query("SELECT * FROM categories WHERE isDefault = 1")
    fun getDefaultCategories(): Flow<List<CategoryEntity>>
    
    @Query("DELETE FROM categories WHERE isDefault = 0")
    suspend fun deleteNonDefaultCategories()

    @Query("SELECT COUNT(*) FROM categories")
    suspend fun getCategoryCount(): Int
}
