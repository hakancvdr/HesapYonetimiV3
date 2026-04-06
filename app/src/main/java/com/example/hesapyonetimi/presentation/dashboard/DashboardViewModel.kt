package com.example.hesapyonetimi.presentation.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.hesapyonetimi.domain.model.Reminder
import com.example.hesapyonetimi.domain.model.Transaction
import com.example.hesapyonetimi.domain.repository.ReminderRepository
import com.example.hesapyonetimi.domain.repository.TransactionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.Calendar
import javax.inject.Inject

data class DashboardUiState(
    val totalBalance: Double = 0.0,
    val totalIncome: Double = 0.0,
    val totalExpense: Double = 0.0,
    val recentTransactions: List<Transaction> = emptyList(),
    val upcomingReminders: List<Reminder> = emptyList(),
    val daysWithTransactions: Set<Int> = emptySet(),
    val highestCategory: String = "",
    val isLoading: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val transactionRepository: TransactionRepository,
    private val reminderRepository: ReminderRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(DashboardUiState(isLoading = true))
    val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()

    // Seçili tarih — başlangıçta bugün
    private val _selectedDate = MutableStateFlow(System.currentTimeMillis())

    private var statsJob: Job? = null
    private var dateJob: Job? = null

    init {
        loadMonthlyStats()
        observeSelectedDate()
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

                    listOf(income, expense) to Triple(reminders, daysWithData, highestCat)
                }.collect { (balances, rest) ->
                    val (reminders, daysWithData, highestCat) = rest
                    _uiState.update { current ->
                        current.copy(
                            totalBalance = balances[0] - balances[1],
                            totalIncome = balances[0],
                            totalExpense = balances[1],
                            upcomingReminders = reminders.take(3),
                            daysWithTransactions = daysWithData,
                            highestCategory = highestCat,
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
            _selectedDate.collectLatest { date ->
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
        _selectedDate.value = _selectedDate.value // trigger yenileme
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
