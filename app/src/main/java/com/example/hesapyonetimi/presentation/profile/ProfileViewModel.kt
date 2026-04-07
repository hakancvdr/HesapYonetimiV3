package com.example.hesapyonetimi.presentation.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.hesapyonetimi.data.local.dao.TransactionDao
import com.example.hesapyonetimi.data.local.dao.UserProfileDao
import com.example.hesapyonetimi.data.local.entity.CategoryEntity
import com.example.hesapyonetimi.data.local.entity.UserProfileEntity
import com.example.hesapyonetimi.data.local.dao.CategoryDao
import com.example.hesapyonetimi.domain.model.Category
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

    // ── Kategori bug fix: doğrudan DAO'dan Flow al ──────────────────────────
    // CategoryRepository'nin getAllCategories() bazı durumlarda ilk emit'i kaçırıyor.
    // DAO'dan direkt okuyarak bunu önlüyoruz.
    val categories: StateFlow<List<Category>> = categoryDao.getAllCategories()
        .map { list -> list.map { it.toDomainModel() } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

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

    fun addCategory(category: Category) {
        viewModelScope.launch { categoryRepository.insertCategory(category) }
    }

    fun deleteCategory(category: Category) {
        viewModelScope.launch {
            if (!category.isDefault) {
                categoryRepository.deleteCategory(category)
            } else {
                _uiEvent.emit(ProfileUiEvent.ShowMessage("Varsayılan kategoriler silinemez"))
            }
        }
    }

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
