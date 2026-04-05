package com.example.hesapyonetimi

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.hesapyonetimi.adapter.CalendarAdapter
import com.example.hesapyonetimi.adapter.ReminderAdapter
import com.example.hesapyonetimi.adapter.TransactionAdapter
import com.example.hesapyonetimi.databinding.LayoutDashboardBinding
import com.example.hesapyonetimi.model.CalendarModel
import com.example.hesapyonetimi.model.ReminderModel
import com.example.hesapyonetimi.model.TransactionModel
import com.example.hesapyonetimi.presentation.common.AddTransactionDialog
import com.example.hesapyonetimi.presentation.common.CurrencyFormatter
import com.example.hesapyonetimi.presentation.dashboard.DashboardViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

@AndroidEntryPoint
class DashboardFragment : Fragment() {

    private var _binding: LayoutDashboardBinding? = null
    private val binding get() = _binding!!

    private val viewModel: DashboardViewModel by viewModels()

    private var selectedDayPosition = -1

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = LayoutDashboardBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        applyStatusBarInset()
        initUI()
        setupMonthlyCalendar()
        observeViewModel()
        setupClickListeners()
    }

    /** Status bar yüksekliğini header_container'ın paddingTop'una ekler */
    private fun applyStatusBarInset() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.headerContainer) { v, insets ->
            val statusBarHeight = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top
            v.setPadding(
                dpToPx(20),               // start — sabit
                statusBarHeight + dpToPx(16), // top — status bar + boşluk
                dpToPx(20),               // end — sabit
                dpToPx(24)                // bottom — kartlar header içinde
            )
            insets
        }
    }

    private fun dpToPx(dp: Int): Int =
        (dp * resources.displayMetrics.density).toInt()

    private fun setupClickListeners() {
        binding.fabAddTransaction.setOnClickListener {
            showAddTransactionDialog(null)
        }
        binding.cardIncome.setOnClickListener {
            showAddTransactionDialog(isIncome = true)
        }
        binding.cardExpense.setOnClickListener {
            showAddTransactionDialog(isIncome = false)
        }
        binding.ivProfile.setOnClickListener {
            // TODO: Profil sayfası
        }
    }

    private fun showAddTransactionDialog(isIncome: Boolean?) {
        val dialog = AddTransactionDialog.newInstance(isIncome)
        dialog.show(childFragmentManager, "AddTransactionDialog")
    }

    private fun initUI() {
        binding.rvRecentTransactions.layoutManager = LinearLayoutManager(context)
        binding.rvDashboardCalendar.layoutManager =
            LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false)
        binding.rvDashboardReminders.layoutManager = LinearLayoutManager(context)

        binding.tvDashboardUsername.text = "Hakan 👋"

        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        binding.tvGreeting.text = when (hour) {
            in 5..11  -> getString(R.string.greeting_morning)
            in 12..17 -> getString(R.string.greeting_afternoon)
            else      -> getString(R.string.greeting_evening)
        }
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    updateUI(state)
                    if (state.daysWithTransactions.isNotEmpty()) {
                        refreshCalendar(state.daysWithTransactions)
                    }
                }
            }
        }
    }

    private fun refreshCalendar(daysWithData: Set<Int>) {
        val calendar = Calendar.getInstance()
        val daysInMonth = calendar.getActualMaximum(Calendar.DAY_OF_MONTH)
        val monthlyDays = mutableListOf<CalendarModel>()
        val dayFormat = SimpleDateFormat("EEE", Locale("tr"))
        val numberFormat = SimpleDateFormat("dd", Locale.getDefault())

        for (i in 1..daysInMonth) {
            val tempCal = calendar.clone() as Calendar
            tempCal.set(Calendar.DAY_OF_MONTH, i)
            monthlyDays.add(
                CalendarModel(
                    dayFormat.format(tempCal.time),
                    numberFormat.format(tempCal.time),
                    hasData = daysWithData.contains(i)
                )
            )
        }

        binding.rvDashboardCalendar.adapter =
            CalendarAdapter(monthlyDays, selectedDayPosition) { position ->
                selectedDayPosition = position
                val clickedCal = Calendar.getInstance()
                clickedCal.set(Calendar.DAY_OF_MONTH, position + 1)
                clickedCal.set(Calendar.HOUR_OF_DAY, 12)
                viewModel.getTransactionsByDate(clickedCal.timeInMillis)
                binding.rvDashboardCalendar.adapter?.notifyDataSetChanged()
            }
    }

    private fun updateUI(state: com.example.hesapyonetimi.presentation.dashboard.DashboardUiState) {
        binding.tvMainBalance.text = CurrencyFormatter.format(state.totalBalance)
        binding.tvDashboardIncomeVal.text = CurrencyFormatter.format(state.totalIncome)
        binding.tvDashboardExpenseVal.text = CurrencyFormatter.format(state.totalExpense)

        // Ay başlığı: "Bu ay — Nisan 2026"
        val monthName = SimpleDateFormat("MMMM yyyy", Locale("tr")).format(java.util.Date())
        binding.tvMonthTitle.text = "Bu ay — $monthName"

        // Dinamik öneri metni
        binding.tvSuggestion.text = when {
            state.totalIncome == 0.0 && state.totalExpense == 0.0 ->
                "Henüz veri yok. İşlem ekleyerek başla 🚀"
            state.totalExpense > state.totalIncome ->
                "Bu ay giderleriniz gelirinizi aştı ⚠️"
            state.totalExpense > state.totalIncome * 0.8 ->
                "Harcamalarınız gelirinizin %${((state.totalExpense / state.totalIncome) * 100).toInt()}'ine ulaştı"
            else ->
                "Harika! Bu ay bütçeni dengeli tutuyorsun 👍"
        }

        // En çok harcama kategorisi
        binding.tvHighestCategory.text = if (state.highestCategory.isNotEmpty())
            state.highestCategory
        else
            "—"

        if (state.recentTransactions.isEmpty()) {
            binding.tvEmptyState.visibility = View.VISIBLE
            binding.rvRecentTransactions.visibility = View.GONE
        } else {
            binding.tvEmptyState.visibility = View.GONE
            binding.rvRecentTransactions.visibility = View.VISIBLE

            val transactionModels = state.recentTransactions.map { t ->
                TransactionModel(
                    title = t.description,
                    category = t.categoryName,
                    amount = CurrencyFormatter.formatWithSign(t.amount, t.isIncome),
                    isIncome = t.isIncome
                )
            }
            binding.rvRecentTransactions.adapter = TransactionAdapter(transactionModels)
        }

        val reminderModels = state.upcomingReminders.map { reminder ->
            val dateFormat = SimpleDateFormat("d MMMM", Locale("tr"))
            val dateStr = when {
                reminder.daysUntilDue == 0  -> "Bugün"
                reminder.daysUntilDue == 1  -> "Yarın"
                reminder.daysUntilDue < 0   -> "Gecikmiş"
                else -> dateFormat.format(Date(reminder.dueDate))
            }
            ReminderModel(
                reminder.title,
                CurrencyFormatter.format(reminder.amount),
                dateStr,
                if (reminder.isPaid) 1 else 0
            )
        }
        binding.rvDashboardReminders.adapter = ReminderAdapter(reminderModels) {}
    }

    private fun setupMonthlyCalendar() {
        val calendar = Calendar.getInstance()
        val currentDay = calendar.get(Calendar.DAY_OF_MONTH)
        val daysInMonth = calendar.getActualMaximum(Calendar.DAY_OF_MONTH)
        val monthlyDays = mutableListOf<CalendarModel>()
        val dayFormat = SimpleDateFormat("EEE", Locale("tr"))
        val numberFormat = SimpleDateFormat("dd", Locale.getDefault())
        val daysWithData = viewModel.uiState.value.daysWithTransactions

        for (i in 1..daysInMonth) {
            val tempCal = calendar.clone() as Calendar
            tempCal.set(Calendar.DAY_OF_MONTH, i)
            monthlyDays.add(
                CalendarModel(
                    dayFormat.format(tempCal.time),
                    numberFormat.format(tempCal.time),
                    hasData = daysWithData.contains(i)
                )
            )
        }

        selectedDayPosition = currentDay - 1

        binding.rvDashboardCalendar.adapter =
            CalendarAdapter(monthlyDays, selectedDayPosition) { position ->
                selectedDayPosition = position
                val clickedCal = Calendar.getInstance()
                clickedCal.set(Calendar.DAY_OF_MONTH, position + 1)
                clickedCal.set(Calendar.HOUR_OF_DAY, 12)
                viewModel.getTransactionsByDate(clickedCal.timeInMillis)
                binding.rvDashboardCalendar.adapter?.notifyDataSetChanged()
            }
        binding.rvDashboardCalendar.scrollToPosition(currentDay - 1)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
