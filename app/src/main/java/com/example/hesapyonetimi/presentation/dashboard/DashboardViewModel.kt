package com.example.hesapyonetimi.presentation.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import android.content.Context
import java.util.Locale
import com.example.hesapyonetimi.data.local.dao.TransactionDao
import com.example.hesapyonetimi.data.remote.ExchangeRatesService
import com.example.hesapyonetimi.domain.model.Reminder
import com.example.hesapyonetimi.domain.model.Transaction
import com.example.hesapyonetimi.domain.repository.ReminderRepository
import com.example.hesapyonetimi.domain.repository.TransactionRepository
import com.example.hesapyonetimi.presentation.common.AkilliOneriService
import com.example.hesapyonetimi.presentation.common.BudgetInsightService
import com.example.hesapyonetimi.presentation.common.HizliOneri
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Calendar
import javax.inject.Inject
import android.graphics.Color

data class DashboardExpenseSlice(
    val label: String,
    val value: Float,
    val color: Int
)

data class DashboardUiState(
    val totalBalance: Double = 0.0,
    val totalIncome: Double = 0.0,
    val totalExpense: Double = 0.0,
    val recentTransactions: List<Transaction> = emptyList(),
    val upcomingReminders: List<Reminder> = emptyList(),
    val daysWithTransactions: Set<Int> = emptySet(),
    val highestCategory: String = "",
    val isLoading: Boolean = false,
    val error: String? = null,
    /** Döviz şeridi (ör. USD/TRY, EUR/TRY) */
    val exchangeRatesBanner: String? = null,
    /** Tekrarlayan işlem worker özeti */
    val recurringWorkerBanner: String? = null,
    /** Bu ay gider — kategori dilimleri (mini pasta) */
    val expensePieSlices: List<DashboardExpenseSlice> = emptyList()
)

private const val PREFS_APP = "HesapPrefs"
private const val KEY_RECURRING_LAST_ADDED = "recurring_worker_last_added"

private val dashboardPieColors = listOf(
    Color.parseColor("#6B8FFF"), Color.parseColor("#4FC3F7"), Color.parseColor("#10B981"),
    Color.parseColor("#F59E0B"), Color.parseColor("#EF5350"), Color.parseColor("#A78BFA"),
    Color.parseColor("#F472B6"), Color.parseColor("#22C55E")
)

private fun buildExpensePieSlices(monthTransactions: List<Transaction>): List<DashboardExpenseSlice> {
    val pairs = monthTransactions.asSequence()
        .filter { !it.isIncome && it.amount > 0 }
        .groupBy { it.categoryName.ifBlank { "Diğer" } }
        .mapValues { (_, txs) -> txs.sumOf { it.amount }.toFloat() }
        .map { it.key to it.value }
        .sortedByDescending { it.second }
        .toMutableList()
    if (pairs.isEmpty()) return emptyList()
    val head = pairs.take(5).toMutableList()
    if (pairs.size > 5) {
        val rest = pairs.drop(5).sumOf { it.second.toDouble() }.toFloat()
        if (rest > 0f) head.add("Diğer" to rest)
    }
    return head.mapIndexed { i, (label, value) ->
        DashboardExpenseSlice(label, value, dashboardPieColors[i % dashboardPieColors.size])
    }
}

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val transactionRepository: TransactionRepository,
    private val reminderRepository: ReminderRepository,
    private val akilliOneriService: AkilliOneriService,
    private val budgetInsightService: BudgetInsightService,
    private val transactionDao: TransactionDao,
    private val exchangeRatesService: ExchangeRatesService,
    @ApplicationContext private val appContext: Context
) : ViewModel() {

    private val _uiState = MutableStateFlow(DashboardUiState(isLoading = true))
    val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()

    private val _suggestions = MutableStateFlow<List<HizliOneri>>(emptyList())
    val suggestions: StateFlow<List<HizliOneri>> = _suggestions.asStateFlow()

    private val _budgetInsightLines = MutableStateFlow<List<String>>(emptyList())
    val budgetInsightLines: StateFlow<List<String>> = _budgetInsightLines.asStateFlow()

    // Tüm zamanlara ait kümülatif net bakiye (devreden)
    val netBakiye: StateFlow<Double> = combine(
        transactionDao.getTotalIncomeAllTime(),
        transactionDao.getTotalExpenseAllTime()
    ) { income, expense -> (income ?: 0.0) - (expense ?: 0.0) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0.0)

    // Seçili tarih — başlangıçta bugün
    private val _selectedDate = MutableStateFlow(System.currentTimeMillis())
    val selectedDateMillis: Long get() = _selectedDate.value

    /** Aynı gün seçiliyken bile günlük işlem listesini yeniden yüklemek için */
    private val _recentTxRefresh = MutableStateFlow(0)

    private var statsJob: Job? = null
    private var dateJob: Job? = null

    init {
        loadMonthlyStats()
        observeSelectedDate()
        loadRemindersNow()
        loadSuggestions()
        loadExchangeAndRecurringBanners()
    }

    private fun loadExchangeAndRecurringBanners() {
        viewModelScope.launch {
            val rates = withContext(Dispatchers.IO) { exchangeRatesService.tryCrossRatesBanner() }
            val prefs = appContext.getSharedPreferences(PREFS_APP, Context.MODE_PRIVATE)
            val added = prefs.getInt(KEY_RECURRING_LAST_ADDED, 0)
            val recurringLine = if (added > 0) {
                prefs.edit().putInt(KEY_RECURRING_LAST_ADDED, 0).apply()
                "Tekrarlayan işlemler: son görevde $added yeni kayıt oluşturuldu."
            } else null
            _uiState.update {
                it.copy(
                    exchangeRatesBanner = rates,
                    recurringWorkerBanner = recurringLine
                )
            }
        }
    }

    private fun loadSuggestions() {
        viewModelScope.launch {
            try {
                _suggestions.value = akilliOneriService.bugunOneri()
            } catch (_: Exception) {
                _suggestions.value = emptyList()
            }
            try {
                _budgetInsightLines.value = budgetInsightService.monthlyBudgetLines()
            } catch (_: Exception) {
                _budgetInsightLines.value = emptyList()
            }
        }
    }

    private fun loadRemindersNow() {
        viewModelScope.launch {
            reminderRepository.getUnpaidReminders().collect { reminders ->
                _uiState.update { it.copy(upcomingReminders = reminders.take(5)) }
            }
        }
    }

    // Aylık istatistikler — recentTransactions'a hiç dokunmuyor
    private fun loadMonthlyStats() {
        statsJob?.cancel()
        statsJob = viewModelScope.launch {
            try {
                val (startOfMonth, endOfMonth) = getCurrentMonthDateRange()
                combine(
                    reminderRepository.getUnpaidReminders(),
                    transactionRepository.getTransactionsByDateRange(startOfMonth, endOfMonth)
                ) { reminders, monthTransactions ->
                    val income = transactionRepository.getTotalIncome(startOfMonth, endOfMonth)
                    val expense = transactionRepository.getTotalExpense(startOfMonth, endOfMonth)
                    val daysWithData = monthTransactions.map {
                        Calendar.getInstance().apply { timeInMillis = it.date }
                            .get(Calendar.DAY_OF_MONTH)
                    }.toSet()
                    val highestCat = monthTransactions
                        .filter { !it.isIncome }
                        .groupBy { it.categoryName }
                        .mapValues { (_, txs) -> txs.sumOf { it.amount } }
                        .maxByOrNull { it.value }
                        ?.let { "${it.key}: ${String.format("%,.0f", it.value)} ₺" } ?: ""

                    val pieSlices = buildExpensePieSlices(monthTransactions)
                    listOf(income, expense) to Triple(reminders, daysWithData, highestCat) to pieSlices
                }.collect { (balancesAndRest, pieSlices) ->
                    val balances = balancesAndRest.first
                    val (reminders, daysWithData, highestCat) = balancesAndRest.second
                    _uiState.update { current ->
                        current.copy(
                            totalBalance = balances[0] - balances[1],
                            totalIncome = balances[0],
                            totalExpense = balances[1],
                            upcomingReminders = reminders.take(3),
                            daysWithTransactions = daysWithData,
                            highestCategory = highestCat,
                            expensePieSlices = pieSlices,
                            isLoading = false
                        )
                    }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = e.message) }
            }
        }
    }

    // Seçili tarihin işlemlerini dinle — sadece recentTransactions günceller
    private fun observeSelectedDate() {
        viewModelScope.launch {
            combine(_selectedDate, _recentTxRefresh) { date, _ -> date }
                .collectLatest { date ->
                    val (start, end) = getDateRange(date)
                    transactionRepository.getTransactionsByDateRange(start, end)
                        .collect { transactions ->
                            _uiState.update { it.copy(recentTransactions = transactions) }
                        }
                }
        }
    }

    fun getTransactionsByDate(date: Long) {
        _selectedDate.value = date
    }

    fun refreshData() {
        loadMonthlyStats()
        loadExchangeAndRecurringBanners()
        _recentTxRefresh.update { it + 1 }
    }

    private fun getDateRange(timestamp: Long): Pair<Long, Long> {
        val cal = Calendar.getInstance().apply {
            timeInMillis = timestamp
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }
        val start = cal.timeInMillis
        cal.set(Calendar.HOUR_OF_DAY, 23); cal.set(Calendar.MINUTE, 59)
        cal.set(Calendar.SECOND, 59); cal.set(Calendar.MILLISECOND, 999)
        return start to cal.timeInMillis
    }

    private fun getCurrentMonthDateRange(): Pair<Long, Long> {
        val cal = Calendar.getInstance()
        cal.set(Calendar.DAY_OF_MONTH, 1)
        cal.set(Calendar.HOUR_OF_DAY, 0); cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0); cal.set(Calendar.MILLISECOND, 0)
        val start = cal.timeInMillis
        cal.set(Calendar.DAY_OF_MONTH, cal.getActualMaximum(Calendar.DAY_OF_MONTH))
        cal.set(Calendar.HOUR_OF_DAY, 23); cal.set(Calendar.MINUTE, 59)
        cal.set(Calendar.SECOND, 59); cal.set(Calendar.MILLISECOND, 999)
        return start to cal.timeInMillis
    }
}
