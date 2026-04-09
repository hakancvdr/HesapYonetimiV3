package com.example.hesapyonetimi.presentation.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.hesapyonetimi.data.local.dao.TransactionDao
import com.example.hesapyonetimi.data.local.dao.UserProfileDao
import com.example.hesapyonetimi.data.local.entity.CategoryEntity
import com.example.hesapyonetimi.data.local.entity.TransactionEntity
import com.example.hesapyonetimi.data.local.entity.UserProfileEntity
import com.example.hesapyonetimi.data.local.dao.CategoryDao
import com.example.hesapyonetimi.domain.model.Category
import com.example.hesapyonetimi.domain.model.Transaction
import com.example.hesapyonetimi.domain.repository.CategoryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject

data class ProfileStats(
    val totalTransactions: Int = 0,
    val memberSince: String = "",
    val categoryCount: Int = 0,
    val activeDays: Int = 0
)

sealed class ProfileUiEvent {
    data class ThemeChanged(val mode: String) : ProfileUiEvent()
    data class ShowMessage(val message: String) : ProfileUiEvent()
}

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val userProfileDao: UserProfileDao,
    private val categoryRepository: CategoryRepository,
    private val categoryDao: CategoryDao,
    private val transactionDao: TransactionDao
) : ViewModel() {

    val profile: StateFlow<UserProfileEntity?> = userProfileDao.getProfile()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    // ── Kategori flow — Eagerly ile VM oluştuğu anda DAO sorgusu başlar ──────
    // WhileSubscribed kullanılırsa dialog açılana kadar DAO sorgusu çalışmaz,
    // bu da dialog ilk açıldığında boş liste gösterilmesine yol açar.
    val categories: StateFlow<List<Category>> = categoryDao.getAllCategories()
        .map { list -> list.map { it.toDomainModel() } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    // ── Bu ay başlangıç/bitiş ─────────────────────────────────────────────────
    private val monthRange: Pair<Long, Long> = run {
        val cal = Calendar.getInstance()
        cal.set(Calendar.DAY_OF_MONTH, 1)
        cal.set(Calendar.HOUR_OF_DAY, 0); cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0); cal.set(Calendar.MILLISECOND, 0)
        val start = cal.timeInMillis
        cal.set(Calendar.DAY_OF_MONTH, cal.getActualMaximum(Calendar.DAY_OF_MONTH))
        cal.set(Calendar.HOUR_OF_DAY, 23); cal.set(Calendar.MINUTE, 59)
        cal.set(Calendar.SECOND, 59); cal.set(Calendar.MILLISECOND, 999)
        start to cal.timeInMillis
    }

    // ── Bu ay toplam gider — bütçe progress için ─────────────────────────────
    val currentMonthExpense: StateFlow<Double> =
        transactionDao.getTotalExpenseFlow(monthRange.first, monthRange.second)
            .map { it ?: 0.0 }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0)

    // ── Bu ay toplam gelir ────────────────────────────────────────────────────
    val currentMonthIncome: StateFlow<Double> =
        transactionDao.getTotalIncomeFlow(monthRange.first, monthRange.second)
            .map { it ?: 0.0 }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0)

    private val _stats = MutableStateFlow(ProfileStats())
    val stats: StateFlow<ProfileStats> = _stats.asStateFlow()

    private val _uiEvent = MutableSharedFlow<ProfileUiEvent>()
    val uiEvent: SharedFlow<ProfileUiEvent> = _uiEvent.asSharedFlow()

    init {
        viewModelScope.launch {
            if (userProfileDao.getProfileOnce() == null) {
                userProfileDao.upsertProfile(UserProfileEntity())
            }
        }
        viewModelScope.launch {
            categories.collectLatest { loadStats(it.size) }
        }
    }

    private suspend fun loadStats(catCount: Int) {
        val totalTx = transactionDao.getTotalTransactionCount()
        val firstDate = transactionDao.getFirstTransactionDate()

        val memberSince = firstDate?.let {
            SimpleDateFormat("MMM yyyy", Locale("tr")).format(Date(it))
        } ?: "Bu ay"

        val activeDays = firstDate?.let {
            val diff = System.currentTimeMillis() - it
            (diff / (1000 * 60 * 60 * 24)).toInt().coerceAtLeast(1)
        } ?: 0

        _stats.value = ProfileStats(
            totalTransactions = totalTx,
            memberSince = memberSince,
            categoryCount = catCount,
            activeDays = activeDays
        )
    }

    fun updateName(name: String) {
        viewModelScope.launch {
            userProfileDao.updateName(name.trim().ifBlank { "Kullanıcı" })
        }
    }

    fun updateAvatar(emoji: String) {
        viewModelScope.launch { userProfileDao.updateAvatar(emoji) }
    }

    fun updateBudgetLimit(limit: Double) {
        viewModelScope.launch { userProfileDao.updateBudgetLimit(limit) }
    }

    // ── Tema fix: SharedPreferences üzerinden kaydet, Activity restart etme ─
    fun updateThemeMode(mode: String) {
        viewModelScope.launch {
            userProfileDao.updateThemeMode(mode)
            // Tema değişikliğini Activity'e ilet — Activity uygular, fragment değil
            _uiEvent.emit(ProfileUiEvent.ThemeChanged(mode))
        }
    }

    suspend fun getAllTransactionsOnce(): List<com.example.hesapyonetimi.domain.model.Transaction> {
        // kategori bilgisi için DAO join kullan
        return transactionDao.getAllTransactionsOnce().map { entity ->
            val category = categoryDao.getCategoryById(entity.categoryId)
            entity.toDomainWithCategory(category)
        }
    }

    fun addCategory(category: Category) {
        viewModelScope.launch { categoryRepository.insertCategory(category) }
    }

    fun updateCategory(category: Category) {
        viewModelScope.launch { categoryRepository.updateCategory(category) }
    }

    fun deleteCategory(category: Category) {
        viewModelScope.launch { categoryRepository.deleteCategory(category) }
    }

    // TransactionEntity → Transaction with category join
    private fun TransactionEntity.toDomainWithCategory(cat: CategoryEntity?) = Transaction(
        id = id, amount = amount, categoryId = categoryId,
        categoryName = cat?.name ?: "", categoryIcon = cat?.icon ?: "",
        categoryColor = cat?.color ?: "", description = description,
        date = date, isIncome = isIncome, walletId = walletId
    )

    // CategoryEntity → Category domain dönüşümü (inline helper)
    private fun CategoryEntity.toDomainModel() = Category(
        id = id,
        name = name,
        icon = icon,
        color = color,
        isIncome = isIncome,
        isDefault = isDefault
    )
}
