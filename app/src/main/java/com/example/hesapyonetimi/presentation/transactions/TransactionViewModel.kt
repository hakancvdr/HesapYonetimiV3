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
import java.util.Locale

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

    /**
     * Returns the most frequently used categories for the last [days] days.
     *
     * - If [includeSubcategories] is false, subcategory usages are attributed to their top-level parent.
     * - Tie-break: most recently used first, then alphabetical.
     * - If there is no usage data, returns first-use defaults (expense=6, income=5).
     */
    fun getTopCategories(
        isIncome: Boolean,
        includeSubcategories: Boolean,
        limit: Int = 6,
        days: Int = 90,
        nowMillis: Long = System.currentTimeMillis()
    ): List<Category> {
        val categories = _uiState.value.categories
            .filter { it.isIncome == isIncome }

        val byId = categories.associateBy { it.id }
        val windowStart = nowMillis - days.toLong() * 24L * 60L * 60L * 1000L

        val windowTx = _uiState.value.transactions
            .asSequence()
            .filter { it.isIncome == isIncome }
            .filter { it.date >= windowStart }
            .toList()

        if (windowTx.isEmpty()) {
            return defaultEntryCategories(isIncome = isIncome, categories = categories)
        }

        data class Usage(val count: Int, val lastUsed: Long, val name: String, val id: Long)

        val usage = mutableMapOf<Long, Pair<Int, Long>>() // id -> (count, lastUsed)
        for (t in windowTx) {
            val rawId = t.categoryId
            val resolvedId = if (includeSubcategories) {
                rawId
            } else {
                val c = byId[rawId]
                (c?.parentId ?: c?.id) ?: rawId
            }
            val prev = usage[resolvedId]
            if (prev == null) {
                usage[resolvedId] = 1 to t.date
            } else {
                val newCount = prev.first + 1
                val newLastUsed = maxOf(prev.second, t.date)
                usage[resolvedId] = newCount to newLastUsed
            }
        }

        val resolvedCategories = usage.keys.mapNotNull { id ->
            byId[id]?.let { c ->
                val (count, lastUsed) = usage[id] ?: (0 to 0L)
                Usage(count = count, lastUsed = lastUsed, name = c.name, id = c.id)
            }
        }

        val sorted = resolvedCategories
            .sortedWith(
                compareByDescending<Usage> { it.count }
                    .thenByDescending { it.lastUsed }
                    .thenBy { it.name.lowercase(Locale("tr")) }
            )
            .take(limit)
            .mapNotNull { byId[it.id] }

        return sorted.ifEmpty {
            defaultEntryCategories(isIncome = isIncome, categories = categories)
        }
    }

    private fun defaultEntryCategories(isIncome: Boolean, categories: List<Category>): List<Category> {
        val wanted = if (isIncome) {
            listOf("Maaş", "Freelance", "Yatırım", "Hediye", "Diğer")
        } else {
            listOf("Market", "Fatura", "Ulaşım", "Eğitim", "Eğlence", "Diğer")
        }

        val topLevel = categories.filter { it.parentId == null }
        val byName = topLevel.associateBy { it.name.lowercase(Locale("tr")) }
        val picked = wanted.mapNotNull { byName[it.lowercase(Locale("tr"))] }

        // If data is partially missing (older DB), just return what we have.
        return picked.ifEmpty { topLevel.take(if (isIncome) 5 else 6) }
    }
    
    fun clearMessages() {
        _uiState.value = _uiState.value.copy(
            error = null,
            successMessage = null
        )
    }
}
