package com.example.hesapyonetimi.presentation.categories

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.hesapyonetimi.data.local.dao.CategoryDao
import com.example.hesapyonetimi.data.local.dao.TransactionDao
import com.example.hesapyonetimi.domain.model.Category
import com.example.hesapyonetimi.domain.repository.CategoryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import javax.inject.Inject

data class CategoryPickerUiState(
    val isIncome: Boolean = false,
    val categories: List<Category> = emptyList(),
    val message: String? = null
)

@HiltViewModel
@OptIn(ExperimentalCoroutinesApi::class)
class CategoryPickerViewModel @Inject constructor(
    private val categoryRepository: CategoryRepository,
    private val categoryDao: CategoryDao,
    private val transactionDao: TransactionDao
) : ViewModel() {

    private val isIncome = MutableStateFlow(false)
    private val message = MutableStateFlow<String?>(null)

    private val categories = isIncome.flatMapLatest { income ->
        categoryRepository.getCategoriesByType(income)
    }

    val uiState: StateFlow<CategoryPickerUiState> = combine(
        isIncome,
        categories,
        message
    ) { income, cats, msg ->
        CategoryPickerUiState(
            isIncome = income,
            categories = cats,
            message = msg
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CategoryPickerUiState())

    fun setType(income: Boolean) {
        isIncome.value = income
    }

    fun clearMessage() {
        message.value = null
    }

    suspend fun getUserAddedTopLevelCount(isIncome: Boolean): Int {
        return categoryDao.countUserAddedTopLevel(isIncome)
    }

    fun deleteCategory(category: Category) {
        if (category.isLocked || category.name.equals("Diğer", ignoreCase = true)) {
            message.value = "Diğer kategorisi silinemez"
            return
        }

        viewModelScope.launch {
            val other = categoryDao.getOtherLockedCategory(isIncome = category.isIncome)
            if (other == null) {
                message.value = "Diğer kategorisi bulunamadı"
                return@launch
            }

            val idsToDelete = if (category.parentId == null) {
                val subIds = categoryDao.getSubcategoryIds(category.id)
                listOf(category.id) + subIds
            } else {
                listOf(category.id)
            }

            val moved = transactionDao.reassignCategories(idsToDelete, other.id)

            // Delete children first to avoid FK issues
            val childIds = idsToDelete.filter { it != category.id }
            if (childIds.isNotEmpty()) categoryDao.deleteByIds(childIds)
            categoryDao.deleteById(category.id)

            message.value = if (category.parentId == null) {
                "${category.name} ve alt kategorileri silindi, $moved işlem Diğer'e taşındı."
            } else {
                "${category.name} silindi, $moved işlem Diğer'e taşındı."
            }
        }
    }

    fun addCategory(
        name: String,
        icon: String,
        color: String,
        isIncome: Boolean,
        parentId: Long?,
        isPro: Boolean
    ) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) {
            message.value = "Kategori adı boş olamaz"
            return
        }
        if (!isPro && parentId != null) {
            message.value = "Alt kategori özelliği PRO'da"
            return
        }

        viewModelScope.launch {
            if (!isPro && parentId == null) {
                val count = categoryDao.countUserAddedTopLevel(isIncome)
                if (count >= 5) {
                    message.value = "Ücretsiz sürümde en fazla 5 yeni kategori ekleyebilirsin"
                    return@launch
                }
            }

            categoryRepository.insertCategory(
                Category(
                    name = trimmed,
                    icon = icon.ifBlank { "📌" },
                    color = color,
                    isIncome = isIncome,
                    isDefault = false,
                    parentId = parentId,
                    isLocked = false
                )
            )
            message.value = "Kategori eklendi"
        }
    }

    fun updateCategory(category: Category, newName: String, newIcon: String, newColor: String) {
        if (category.isLocked || category.name.equals("Diğer", ignoreCase = true)) {
            message.value = "Diğer kategorisi düzenlenemez"
            return
        }
        val trimmed = newName.trim()
        if (trimmed.isEmpty()) {
            message.value = "Kategori adı boş olamaz"
            return
        }
        viewModelScope.launch {
            categoryRepository.updateCategory(
                category.copy(
                    name = trimmed,
                    icon = newIcon.ifBlank { category.icon },
                    color = newColor
                )
            )
            message.value = "Kategori güncellendi"
        }
    }
}

