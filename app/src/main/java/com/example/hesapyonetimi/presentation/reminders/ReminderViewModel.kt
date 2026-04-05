package com.example.hesapyonetimi.presentation.reminders

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.hesapyonetimi.domain.model.Reminder
import com.example.hesapyonetimi.domain.repository.ReminderRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ReminderUiState(
    val reminders: List<Reminder> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class ReminderViewModel @Inject constructor(
    private val reminderRepository: ReminderRepository
) : ViewModel() {
    
    private val _uiState = MutableStateFlow(ReminderUiState(isLoading = true))
    val uiState: StateFlow<ReminderUiState> = _uiState.asStateFlow()
    
    init {
        loadReminders()
    }
    
    private fun loadReminders() {
        viewModelScope.launch {
            try {
                reminderRepository.getUnpaidReminders()
                    .collect { reminders ->
                        _uiState.value = ReminderUiState(
                            reminders = reminders,
                            isLoading = false
                        )
                    }
            } catch (e: Exception) {
                _uiState.value = ReminderUiState(
                    isLoading = false,
                    error = e.message ?: "Bilinmeyen hata"
                )
            }
        }
    }
    
    fun markAsPaid(id: Long) {
        viewModelScope.launch {
            try {
                reminderRepository.markAsPaid(id)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    error = e.message ?: "İşlem başarısız"
                )
            }
        }
    }
    
    fun addReminder(title: String, amount: Double, dueDate: Long) {
        viewModelScope.launch {
            try {
                val reminder = Reminder(
                    title = title,
                    amount = amount,
                    dueDate = dueDate
                )
                reminderRepository.insertReminder(reminder)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    error = e.message ?: "Hatırlatıcı eklenirken hata oluştu"
                )
            }
        }
    }
}
