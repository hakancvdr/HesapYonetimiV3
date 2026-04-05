package com.example.hesapyonetimi

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.hesapyonetimi.adapter.ReminderAdapter
import com.example.hesapyonetimi.model.ReminderModel
import com.example.hesapyonetimi.presentation.common.CurrencyFormatter
import com.example.hesapyonetimi.presentation.reminders.ReminderViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

@AndroidEntryPoint
class YaklasanFragment : Fragment(R.layout.fragment_yaklasan) {

    private val viewModel: ReminderViewModel by viewModels()
    private lateinit var rv: RecyclerView

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        rv = view.findViewById(R.id.rvHatirlaticilar)
        rv.layoutManager = LinearLayoutManager(requireContext())

        observeViewModel()
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    updateUI(state)
                }
            }
        }
    }

    private fun updateUI(state: com.example.hesapyonetimi.presentation.reminders.ReminderUiState) {
        val reminderModels = state.reminders.map { reminder ->
            val dateFormat = SimpleDateFormat("d MMMM yyyy", Locale("tr"))
            val dateStr = when {
                reminder.daysUntilDue == 0 -> "Bugün"
                reminder.daysUntilDue == 1 -> "Yarın"
                reminder.daysUntilDue < 0 -> "Gecikmiş"
                else -> dateFormat.format(Date(reminder.dueDate))
            }
            
            ReminderModel(
                reminder.title,
                CurrencyFormatter.format(reminder.amount),
                dateStr,
                if (reminder.isPaid) 1 else 0
            )
        }
        
        rv.adapter = ReminderAdapter(reminderModels) {
            // Tıklama - boş lambda
        }
    }
}
