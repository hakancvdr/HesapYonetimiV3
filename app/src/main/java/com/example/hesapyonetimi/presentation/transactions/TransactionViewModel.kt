package com.example.hesapyonetimi.presentation.transactions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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
    private val categoryRepository: CategoryRepository
) : ViewModel() {
    
    private val _uiState = MutableStateFlow(TransactionUiState(isLoading = true))
    val uiState: StateFlow<TransactionUiState> = _uiState.asStateFlow()
    
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
        walletId: Long? = null
    ) {
        viewModelScope.launch {
            try {
                val transaction = Transaction(
                    amount = amount,
                    categoryId = categoryId,
                    description = description,
                    date = date,
                    isIncome = isIncome,
                    walletId = walletId
                )
                
                transactionRepository.insertTransaction(transaction)
                
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
