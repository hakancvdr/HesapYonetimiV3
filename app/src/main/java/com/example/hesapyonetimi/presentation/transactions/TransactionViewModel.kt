package com.example.hesapyonetimi.presentation.transactions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.hesapyonetimi.data.local.dao.TagDao
import com.example.hesapyonetimi.data.local.dao.TransactionTagDao
import com.example.hesapyonetimi.data.local.entity.TagEntity
import com.example.hesapyonetimi.data.local.entity.TransactionTagCrossRef
import com.example.hesapyonetimi.domain.model.Category
import com.example.hesapyonetimi.domain.model.Transaction
import com.example.hesapyonetimi.domain.repository.CategoryRepository
import com.example.hesapyonetimi.domain.repository.TransactionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class TransactionUiState(
    val transactions: List<Transaction> = emptyList(),
    val categories: List<Category> = emptyList(),
    val selectedDate: Long = System.currentTimeMillis(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val successMessage: String? = null
)

@HiltViewModel
class TransactionViewModel @Inject constructor(
    private val transactionRepository: TransactionRepository,
    private val categoryRepository: CategoryRepository,
    private val tagDao: TagDao,
    private val transactionTagDao: TransactionTagDao
) : ViewModel() {
    
    private val _uiState = MutableStateFlow(TransactionUiState(isLoading = true))
    val uiState: StateFlow<TransactionUiState> = _uiState.asStateFlow()

    val tags: StateFlow<List<TagEntity>> = tagDao.observeActive()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    
    init {
        loadData()
    }
    
    private fun loadData() {
        viewModelScope.launch {
            try {
                combine(
                    transactionRepository.getAllTransactions(),
                    categoryRepository.getAllCategories()
                ) { transactions, categories ->
                    TransactionUiState(
                        transactions = transactions,
                        categories = categories,
                        isLoading = false
                    )
                }.collect { state ->
                    _uiState.value = state
                }
            } catch (e: Exception) {
                _uiState.value = TransactionUiState(
                    isLoading = false,
                    error = e.message ?: "Bilinmeyen hata"
                )
            }
        }
    }
    
    fun addTransaction(
        amount: Double,
        categoryId: Long,
        description: String,
        date: Long,
        isIncome: Boolean,
        walletId: Long? = null,
        isRecurring: Boolean = false,
        recurringDays: Int = 30,
        tags: String = "",
        tagIds: List<Long> = emptyList()
    ) {
        viewModelScope.launch {
            try {
                val transaction = Transaction(
                    amount = amount,
                    categoryId = categoryId,
                    description = description,
                    date = date,
                    isIncome = isIncome,
                    walletId = walletId,
                    isRecurring = isRecurring,
                    recurringDays = recurringDays,
                    tags = tags
                )
                
                val txId = transactionRepository.insertTransaction(transaction)
                if (tagIds.isNotEmpty() && txId > 0) {
                    transactionTagDao.insertAll(tagIds.distinct().map { tagId ->
                        TransactionTagCrossRef(transactionId = txId, tagId = tagId)
                    })
                }
                
                _uiState.value = _uiState.value.copy(
                    successMessage = "İşlem başarıyla eklendi!"
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    error = e.message ?: "İşlem eklenirken hata oluştu"
                )
            }
        }
    }
    
    fun addTransaction(transaction: Transaction) {
        viewModelScope.launch {
            try {
                transactionRepository.insertTransaction(transaction.copy(id = 0))
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = e.message ?: "Geri alma hatası")
            }
        }
    }

    fun updateTransaction(transaction: Transaction) {
        viewModelScope.launch {
            try {
                transactionRepository.updateTransaction(transaction)
                _uiState.value = _uiState.value.copy(successMessage = "İşlem güncellendi!")
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = e.message ?: "Güncelleme hatası")
            }
        }
    }

    fun deleteTransaction(transaction: Transaction) {
        viewModelScope.launch {
            try {
                if (transaction.id > 0) transactionTagDao.deleteForTransaction(transaction.id)
                transactionRepository.deleteTransaction(transaction)
                _uiState.value = _uiState.value.copy(
                    successMessage = "İşlem silindi!"
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    error = e.message ?: "İşlem silinirken hata oluştu"
                )
            }
        }
    }
    
    fun getExpenseCategories(): List<Category> {
        return _uiState.value.categories.filter { !it.isIncome }
    }
    
    fun getIncomeCategories(): List<Category> {
        return _uiState.value.categories.filter { it.isIncome }
    }
    
    fun clearMessages() {
        _uiState.value = _uiState.value.copy(
            error = null,
            successMessage = null
        )
    }
}
