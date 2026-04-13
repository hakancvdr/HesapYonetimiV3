package com.example.hesapyonetimi.presentation.profile

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.hesapyonetimi.data.local.dao.BudgetDao
import com.example.hesapyonetimi.data.local.dao.GoalContributionDao
import com.example.hesapyonetimi.data.local.dao.GoalDao
import com.example.hesapyonetimi.data.local.dao.ReminderDao
import com.example.hesapyonetimi.data.local.dao.TransactionDao
import com.example.hesapyonetimi.data.local.dao.UserProfileDao
import com.example.hesapyonetimi.data.local.entity.CategoryEntity
import com.example.hesapyonetimi.data.local.entity.TransactionEntity
import com.example.hesapyonetimi.data.local.entity.UserProfileEntity
import com.example.hesapyonetimi.auth.AuthPrefs
import com.example.hesapyonetimi.data.local.dao.CategoryDao
import com.example.hesapyonetimi.util.PayPeriodResolver
import com.example.hesapyonetimi.domain.model.Category
import com.example.hesapyonetimi.domain.model.Transaction
import com.example.hesapyonetimi.domain.repository.CategoryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject

data class ProfileStats(
    val totalTransactions: Int = 0,
    val memberSince: String = "",
    val categoryCount: Int = 0,
    val activeDays: Int = 0,
    val weekExpenseTotal: Double = 0.0,
    val openRemindersCount: Int = 0,
    val membershipTier: String = "FREE"
)

sealed class ProfileUiEvent {
    data class ThemeChanged(val mode: String) : ProfileUiEvent()
    data class ShowMessage(val message: String) : ProfileUiEvent()
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ProfileViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val userProfileDao: UserProfileDao,
    private val categoryRepository: CategoryRepository,
    private val categoryDao: CategoryDao,
    private val transactionDao: TransactionDao,
    private val reminderDao: ReminderDao,
    private val goalDao: GoalDao,
    private val budgetDao: BudgetDao,
    private val goalContributionDao: GoalContributionDao
) : ViewModel() {

    val profile: StateFlow<UserProfileEntity?> = userProfileDao.getProfile()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    // ── Kategori flow — Eagerly ile VM oluştuğu anda DAO sorgusu başlar ──────
    // WhileSubscribed kullanılırsa dialog açılana kadar DAO sorgusu çalışmaz,
    // bu da dialog ilk açıldığında boş liste gösterilmesine yol açar.
    val categories: StateFlow<List<Category>> = categoryDao.getAllCategories()
        .map { list -> list.map { it.toDomainModel() } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val payPeriodRefresh = MutableStateFlow(0L)

    /** Maaş dönemi veya takvim ayı tercihi değişince çağırın. */
    fun refreshPayPeriodDependentFlows() {
        payPeriodRefresh.value = System.currentTimeMillis()
    }

    // ── Dönem toplam gider — bütçe progress için ─────────────────────────────
    val currentMonthExpense: StateFlow<Double> = payPeriodRefresh
        .flatMapLatest {
            val p = PayPeriodResolver.currentPeriod(appContext)
            transactionDao.getTotalExpenseFlow(p.startMillis, p.endInclusiveMillis())
        }
        .map { it ?: 0.0 }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0)

    // ── Dönem toplam gelir ────────────────────────────────────────────────────
    val currentMonthIncome: StateFlow<Double> = payPeriodRefresh
        .flatMapLatest {
            val p = PayPeriodResolver.currentPeriod(appContext)
            transactionDao.getTotalIncomeFlow(p.startMillis, p.endInclusiveMillis())
        }
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
        }.orEmpty()

        val activeDays = firstDate?.let {
            val diff = System.currentTimeMillis() - it
            (diff / (1000 * 60 * 60 * 24)).toInt().coerceAtLeast(1)
        } ?: 0

        val weekStart = System.currentTimeMillis() - 7L * 24 * 60 * 60 * 1000
        val weekExpense = transactionDao.getTotalExpense(weekStart, System.currentTimeMillis()) ?: 0.0
        val openRem = reminderDao.countUnpaidReminders()
        val tier = if (AuthPrefs.isProMember(appContext)) "PREMIUM" else "FREE"

        _stats.value = ProfileStats(
            totalTransactions = totalTx,
            memberSince = memberSince,
            categoryCount = catCount,
            activeDays = activeDays,
            weekExpenseTotal = weekExpense,
            openRemindersCount = openRem,
            membershipTier = tier
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
        viewModelScope.launch {
            val isPro = AuthPrefs.isProMember(appContext)
            if (!isPro) {
                if (category.parentId != null) {
                    _uiEvent.emit(ProfileUiEvent.ShowMessage("Alt kategori özelliği PRO'da"))
                    return@launch
                }
                val count = categoryDao.countUserAddedTopLevel(category.isIncome)
                if (count >= 5) {
                    _uiEvent.emit(ProfileUiEvent.ShowMessage("Ücretsiz sürümde en fazla 5 yeni kategori ekleyebilirsin"))
                    return@launch
                }
            }
            categoryRepository.insertCategory(category)
        }
    }

    fun updateCategory(category: Category) {
        viewModelScope.launch {
            if (category.isLocked || category.name.equals("Diğer", ignoreCase = true)) {
                _uiEvent.emit(ProfileUiEvent.ShowMessage("Diğer kategorisi düzenlenemez"))
                return@launch
            }
            categoryRepository.updateCategory(category)
        }
    }

    fun deleteCategory(category: Category) {
        viewModelScope.launch {
            if (category.isLocked || category.name.equals("Diğer", ignoreCase = true)) {
                _uiEvent.emit(ProfileUiEvent.ShowMessage("Diğer kategorisi silinemez"))
                return@launch
            }

            val other = categoryDao.getOtherLockedCategory(isIncome = category.isIncome)
            if (other == null) {
                _uiEvent.emit(ProfileUiEvent.ShowMessage("Diğer kategorisi bulunamadı"))
                return@launch
            }

            val idsToDelete = if (category.parentId == null) {
                val subIds = categoryDao.getSubcategoryIds(category.id)
                listOf(category.id) + subIds
            } else {
                listOf(category.id)
            }

            val moved = transactionDao.reassignCategories(idsToDelete, other.id)

            val childIds = idsToDelete.filter { it != category.id }
            if (childIds.isNotEmpty()) categoryDao.deleteByIds(childIds)
            categoryDao.deleteById(category.id)

            val msg = if (category.parentId == null) {
                "${category.name} ve alt kategorileri silindi, $moved işlem Diğer'e taşındı."
            } else {
                "${category.name} silindi, $moved işlem Diğer'e taşındı."
            }
            _uiEvent.emit(ProfileUiEvent.ShowMessage(msg))
        }
    }

    /** İşlemler, hatırlatıcılar, hedefler, bütçe kayıtları ve katkı geçmişi — kategoriler kalır */
    fun wipeAllFinancialData() {
        viewModelScope.launch {
            goalContributionDao.deleteAll()
            transactionDao.deleteAll()
            reminderDao.deleteAll()
            goalDao.deleteAll()
            budgetDao.deleteAll()
            _uiEvent.emit(ProfileUiEvent.ShowMessage("Finansal veriler silindi."))
        }
    }

    fun resetAppSettings() {
        viewModelScope.launch {
            userProfileDao.updateThemeMode("SYSTEM")
            userProfileDao.updateBudgetLimit(0.0)
            appContext.getSharedPreferences(AuthPrefs.PREFS_NAME, Context.MODE_PRIVATE).edit()
                .remove("kullanici_pin")
                .putBoolean("biometric_enabled", false)
                .putBoolean("pin_enabled", false)
                .putLong("pin_lock_timeout_ms", AuthPrefs.DEFAULT_PIN_LOCK_TIMEOUT_MS)
                .apply()
            _uiEvent.emit(ProfileUiEvent.ThemeChanged("SYSTEM"))
            _uiEvent.emit(ProfileUiEvent.ShowMessage("PIN, biyometrik ve tema varsayılana alındı."))
        }
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
