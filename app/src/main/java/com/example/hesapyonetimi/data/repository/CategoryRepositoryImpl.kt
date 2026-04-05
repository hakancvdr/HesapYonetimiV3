package com.example.hesapyonetimi.data.repository

import com.example.hesapyonetimi.data.local.dao.CategoryDao
import com.example.hesapyonetimi.data.mapper.toDomain
import com.example.hesapyonetimi.data.mapper.toEntity
import com.example.hesapyonetimi.domain.model.Category
import com.example.hesapyonetimi.domain.repository.CategoryRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class CategoryRepositoryImpl @Inject constructor(
    private val categoryDao: CategoryDao
) : CategoryRepository {
    
    override suspend fun insertCategory(category: Category): Long {
        return categoryDao.insert(category.toEntity())
    }
    
    override suspend fun updateCategory(category: Category) {
        categoryDao.update(category.toEntity())
    }
    
    override suspend fun deleteCategory(category: Category) {
        categoryDao.delete(category.toEntity())
    }
    
    override fun getAllCategories(): Flow<List<Category>> {
        return categoryDao.getAllCategories().map { entities ->
            entities.map { it.toDomain() }
        }
    }
    
    override fun getCategoriesByType(isIncome: Boolean): Flow<List<Category>> {
        return categoryDao.getCategoriesByType(isIncome).map { entities ->
            entities.map { it.toDomain() }
        }
    }
    
    override suspend fun getCategoryById(id: Long): Category? {
        return categoryDao.getCategoryById(id)?.toDomain()
    }
}
