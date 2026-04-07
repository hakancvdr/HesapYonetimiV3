package com.example.hesapyonetimi.presentation.reminders

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.hesapyonetimi.data.local.entity.RecurringType
import com.example.hesapyonetimi.domain.model.Reminder
import com.example.hesapyonetimi.domain.model.Transaction
import com.example.hesapyonetimi.domain.repository.ReminderRepository
import com.example.hesapyonetimi.domain.model.Category
import com.example.hesapyonetimi.domain.repository.CategoryRepository
import com.example.hesapyonetimi.domain.repository.TransactionRepository
import com.example.hesapyonetimi.presentation.common.AkilliOneriService
import com.example.hesapyonetimi.presentation.common.HizliOneri
import com.example.hesapyonetimi.worker.ReminderScheduler
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.*
import javax.inject.Inject

data class ReminderUiState(
    val reminders: List<Reminder> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class ReminderViewModel @Inject constructor(
    private val reminderRepository: ReminderRepository,
    private val transactionRepository: TransactionRepository,
    private val categoryRepository: CategoryRepository,
    private val akilliOneriService: AkilliOneriService,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _uiState = MutableStateFlow(ReminderUiState(isLoading = true))
    val uiState: StateFlow<ReminderUiState> = _uiState.asStateFlow()

    private val _oneriler = MutableStateFlow<List<HizliOneri>>(emptyList())
    val oneriler: StateFlow<List<HizliOneri>> = _oneriler.asStateFlow()

    init {
        loadReminders()
        loadOneriler()
    }

    private fun loadReminders() {
        viewModelScope.launch {
            reminderRepository.getAllReminders()
                .collect { reminders ->
                    _uiState.value = ReminderUiState(reminders = reminders)
                }
        }
    }

    fun loadCategories(onResult: (List<Category>) -> Unit) {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            val cats = categoryRepository.getAllCategories().first()
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                onResult(cats)
            }
        }
    }

    private fun loadOneriler() {
        viewModelScope.launch {
            try {
                _oneriler.value = akilliOneriService.bugunOneri()
            } catch (e: Exception) { /* sessizce geç */ }
        }
    }

    fun addReminder(
        title: String,
        amount: Double,
        dueDate: Long,
        categoryId: Long,
        recurringType: RecurringType? = null,
        donemSayisi: Int = 1
    ) {
        viewModelScope.launch {
            try {
                // Tek kayıt oluştur — ödendi basınca sıradaki dönem otomatik oluşur
                val reminder = Reminder(
                    title = title,
                    amount = amount,
                    dueDate = dueDate,
                    categoryId = categoryId,
                    isRecurring = recurringType != null,
                    recurringType = recurringType,
                    // totalDonem: 0=sınırsız, 1=tek seferlik, >1=sınırlı dönem sayısı
                    totalDonem = if (recurringType == null) 1 else donemSayisi,
                    donemIndex = 1
                )
                val id = reminderRepository.insertReminder(reminder)
                ReminderScheduler.schedule(context, reminder.copy(id = id))
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = e.message)
            }
        }
    }

    fun updateReminder(reminder: Reminder) {
        viewModelScope.launch {
            try {
                reminderRepository.updateReminder(reminder)
                ReminderScheduler.cancel(context, reminder.id)
                ReminderScheduler.schedule(context, reminder)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = e.message)
            }
        }
    }

    fun deleteReminder(id: Long) {
        viewModelScope.launch {
            try {
                val reminder = _uiState.value.reminders.firstOrNull { it.id == id } ?: return@launch
                reminderRepository.deleteReminder(reminder)
                ReminderScheduler.cancel(context, id)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = e.message)
            }
        }
    }

    fun markAsPaid(id: Long) {
        viewModelScope.launch {
            try {
                reminderRepository.markAsPaid(id)
                ReminderScheduler.cancel(context, id)

                val reminder = _uiState.value.reminders.firstOrNull { it.id == id } ?: return@launch

                // Günlük takibe ekle
                transactionRepository.insertTransaction(
                    Transaction(
                        amount = reminder.amount,
                        categoryId = reminder.categoryId,
                        description = reminder.title,
                        date = System.currentTimeMillis(),
                        isIncome = false
                    )
                )

                // Tekrarlayansa bir sonraki dönemi oluştur — ama dönem sınırını aşmamak lazım
                if (reminder.isRecurring && reminder.recurringType != null) {
                    val sonrakiIndex = reminder.donemIndex + 1
                    val devamEder = when {
                        !reminder.isRecurring -> false
                        reminder.recurringType == null -> false
                        reminder.totalDonem == 1 -> false  // tek seferlik
                        reminder.totalDonem == 0 -> false  // 0 = tanımsız, güvenli taraf: devam etme
                        else -> sonrakiIndex <= reminder.totalDonem  // sınırlı dönem
                    }
                    if (devamEder) {
                        val nextDate = when (reminder.recurringType) {
                            RecurringType.MONTHLY -> addMonths(reminder.dueDate, 1)
                            RecurringType.WEEKLY -> reminder.dueDate + 7 * 24 * 60 * 60 * 1000L
                            else -> return@launch
                        }
                        val next = Reminder(
                            title = reminder.title,
                            amount = reminder.amount,
                            dueDate = nextDate,
                            categoryId = reminder.categoryId,
                            isRecurring = true,
                            recurringType = reminder.recurringType,
                            totalDonem = reminder.totalDonem,
                            donemIndex = sonrakiIndex
                        )
                        val nextId = reminderRepository.insertReminder(next)
                        ReminderScheduler.schedule(context, next.copy(id = nextId))
                    }
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = e.message)
            }
        }
    }

    private fun addMonths(date: Long, months: Int): Long {
        return Calendar.getInstance().apply {
            timeInMillis = date
            add(Calendar.MONTH, months)
        }.timeInMillis
    }

    private fun addYears(date: Long, years: Int): Long {
        return Calendar.getInstance().apply {
            timeInMillis = date
            add(Calendar.YEAR, years)
        }.timeInMillis
    }
}
