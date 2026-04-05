package com.example.hesapyonetimi.presentation.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.hesapyonetimi.domain.model.Reminder
import com.example.hesapyonetimi.domain.model.Transaction
import com.example.hesapyonetimi.domain.repository.ReminderRepository
import com.example.hesapyonetimi.domain.repository.TransactionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
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
    
    init {
        loadDashboardData()
    }
    
    private fun loadDashboardData() {
        viewModelScope.launch {
            try {
                // Bugünün başlangıç ve bitiş zamanı
                val (startOfDay, endOfDay) = getTodayDateRange()
                
                // Bu ayın başlangıç ve bitiş zamanı
                val (startOfMonth, endOfMonth) = getCurrentMonthDateRange()
                
                combine(
                    transactionRepository.getTransactionsByDateRange(startOfDay, endOfDay),
                    reminderRepository.getUnpaidReminders(),
                    transactionRepository.getTransactionsByDateRange(startOfMonth, endOfMonth)
                ) { todayTransactions, reminders, monthTransactions ->
                    
                    // Bu ay toplam gelir/gider hesapla
                    val monthIncome = transactionRepository.getTotalIncome(startOfMonth, endOfMonth)
                    val monthExpense = transactionRepository.getTotalExpense(startOfMonth, endOfMonth)
                    
                    // Hangi günlerde işlem var bul
                    val daysWithData = monthTransactions.map { transaction ->
                        val cal = Calendar.getInstance().apply {
                            timeInMillis = transaction.date
                        }
                        cal.get(Calendar.DAY_OF_MONTH)
                    }.toSet()
                    
                    // En çok harcama yapılan kategori
                    val highestCat = monthTransactions
                        .filter { !it.isIncome }
                        .groupBy { it.categoryName }
                        .mapValues { (_, txs) -> txs.sumOf { it.amount } }
                        .maxByOrNull { it.value }
                    val highestCategoryText = highestCat?.let {
                        "${it.key}: ${String.format("%,.0f", it.value)} ₺"
                    } ?: ""

                    DashboardUiState(
                        totalBalance = monthIncome - monthExpense,
                        totalIncome = monthIncome,
                        totalExpense = monthExpense,
                        recentTransactions = todayTransactions.take(5),
                        upcomingReminders = reminders.take(3),
                        daysWithTransactions = daysWithData,
                        highestCategory = highestCategoryText,
                        isLoading = false
                    )
                }.collect { state ->
                    _uiState.value = state
                }
                
            } catch (e: Exception) {
                _uiState.value = DashboardUiState(
                    isLoading = false,
                    error = e.message ?: "Bilinmeyen hata"
                )
            }
        }
    }
    
    fun refreshData() {
        _uiState.value = _uiState.value.copy(isLoading = true)
        loadDashboardData()
    }
    
    fun getTransactionsByDate(date: Long) {
        viewModelScope.launch {
            val (startOfDay, endOfDay) = getDateRange(date)
            
            transactionRepository.getTransactionsByDateRange(startOfDay, endOfDay)
                .collect { transactions ->
                    _uiState.value = _uiState.value.copy(
                        recentTransactions = transactions,
                        isLoading = false
                    )
                }
        }
    }
    
    private fun getTodayDateRange(): Pair<Long, Long> {
        val calendar = Calendar.getInstance()
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        val startOfDay = calendar.timeInMillis
        
        calendar.set(Calendar.HOUR_OF_DAY, 23)
        calendar.set(Calendar.MINUTE, 59)
        calendar.set(Calendar.SECOND, 59)
        calendar.set(Calendar.MILLISECOND, 999)
        val endOfDay = calendar.timeInMillis
        
        return startOfDay to endOfDay
    }
    
    private fun getDateRange(timestamp: Long): Pair<Long, Long> {
        val calendar = Calendar.getInstance().apply {
            timeInMillis = timestamp
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val startOfDay = calendar.timeInMillis
        
        calendar.set(Calendar.HOUR_OF_DAY, 23)
        calendar.set(Calendar.MINUTE, 59)
        calendar.set(Calendar.SECOND, 59)
        calendar.set(Calendar.MILLISECOND, 999)
        val endOfDay = calendar.timeInMillis
        
        return startOfDay to endOfDay
    }
    
    private fun getCurrentMonthDateRange(): Pair<Long, Long> {
        val calendar = Calendar.getInstance()
        calendar.set(Calendar.DAY_OF_MONTH, 1)
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        val startOfMonth = calendar.timeInMillis
        
        calendar.set(Calendar.DAY_OF_MONTH, calendar.getActualMaximum(Calendar.DAY_OF_MONTH))
        calendar.set(Calendar.HOUR_OF_DAY, 23)
        calendar.set(Calendar.MINUTE, 59)
        calendar.set(Calendar.SECOND, 59)
        calendar.set(Calendar.MILLISECOND, 999)
        val endOfMonth = calendar.timeInMillis
        
        return startOfMonth to endOfMonth
    }
}
