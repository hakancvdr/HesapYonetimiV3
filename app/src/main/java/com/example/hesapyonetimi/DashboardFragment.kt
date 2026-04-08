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
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.hesapyonetimi.adapter.CalendarAdapter
import com.example.hesapyonetimi.adapter.ReminderAdapter
import com.example.hesapyonetimi.adapter.TransactionAdapter
import com.example.hesapyonetimi.data.local.dao.UserProfileDao
import com.example.hesapyonetimi.databinding.LayoutDashboardBinding
import com.example.hesapyonetimi.model.CalendarModel
import com.example.hesapyonetimi.model.ReminderModel
import com.example.hesapyonetimi.model.TransactionModel
import com.example.hesapyonetimi.presentation.common.AddTransactionDialog
import com.example.hesapyonetimi.presentation.common.CurrencyFormatter
import com.example.hesapyonetimi.presentation.dashboard.DashboardViewModel
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject

@AndroidEntryPoint
class DashboardFragment : Fragment() {

    private var _binding: LayoutDashboardBinding? = null
    private val binding get() = _binding!!
    private val viewModel: DashboardViewModel by viewModels()
    private var selectedDayPosition = -1

    @Inject
    lateinit var userProfileDao: UserProfileDao

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
        observeProfile()
        setupClickListeners()

        val swipeRefresh = binding.root.findViewById<SwipeRefreshLayout>(R.id.swipe_refresh)
        swipeRefresh?.setColorSchemeResources(R.color.green_primary)
        swipeRefresh?.setOnRefreshListener {
            viewModel.refreshData()
            swipeRefresh.isRefreshing = false
        }
    }

    private fun applyStatusBarInset() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.headerContainer) { v, insets ->
            val statusBarHeight = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top
            v.setPadding(dpToPx(20), statusBarHeight + dpToPx(12), dpToPx(20), dpToPx(16))
            insets
        }
    }

    private fun dpToPx(dp: Int) = (dp * resources.displayMetrics.density).toInt()

    private fun setupClickListeners() {
        binding.fabAddTransaction.setOnClickListener { showAddTransactionDialog(null) }
        binding.cardIncome.setOnClickListener { showAddTransactionDialog(isIncome = true) }
        binding.cardExpense.setOnClickListener { showAddTransactionDialog(isIncome = false) }

        binding.ivProfile.setOnClickListener {
            (activity as? MainActivity)?.gosterProfil()
        }

        binding.btnAddReminder.setOnClickListener {
            activity?.let { act ->
                if (act is MainActivity) act.gosterYaklasan()
            }
        }
    }

    private fun showAddTransactionDialog(isIncome: Boolean?) {
        AddTransactionDialog.newInstance(isIncome).show(childFragmentManager, "AddTransactionDialog")
    }

    private fun initUI() {
        binding.rvRecentTransactions.layoutManager = LinearLayoutManager(context)
        binding.rvDashboardCalendar.layoutManager =
            LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false)
        binding.rvDashboardReminders.layoutManager = LinearLayoutManager(context)

        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        binding.tvGreeting.text = when (hour) {
            in 5..11  -> getString(R.string.greeting_morning)
            in 12..17 -> getString(R.string.greeting_afternoon)
            else      -> getString(R.string.greeting_evening)
        }
    }

    private fun observeProfile() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                userProfileDao.getProfile().collectLatest { profile ->
                    val name = profile?.displayName?.ifBlank { "Kullanıcı" } ?: "Kullanıcı"
                    binding.tvDashboardUsername.text = "$name 👋"
                }
            }
        }
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.uiState
                        .map { it.copy(recentTransactions = emptyList()) }
                        .distinctUntilChanged()
                        .collect { state ->
                            updateUI(state)
                            if (state.daysWithTransactions.isNotEmpty()) {
                                refreshCalendar(state.daysWithTransactions)
                            }
                        }
                }
                launch {
                    viewModel.uiState
                        .map { it.recentTransactions }
                        .distinctUntilChanged()
                        .collect { transactions ->
                            updateTransactionList(transactions)
                        }
                }
            }
        }
    }

    private fun updateUI(state: com.example.hesapyonetimi.presentation.dashboard.DashboardUiState) {
        binding.tvMainBalance.text = CurrencyFormatter.format(state.totalBalance)
        binding.tvDashboardIncomeVal.text = CurrencyFormatter.format(state.totalIncome)
        binding.tvDashboardExpenseVal.text = CurrencyFormatter.format(state.totalExpense)

        val monthName = SimpleDateFormat("MMMM yyyy", Locale("tr")).format(Date())
        binding.tvMonthTitle.text = "Bu ay — $monthName"

        val suggestionContainer = binding.root.findViewById<android.view.View>(R.id.suggestion_container)
        if (state.totalIncome == 0.0 && state.totalExpense == 0.0) {
            suggestionContainer?.visibility = View.GONE
        } else {
            suggestionContainer?.visibility = View.VISIBLE
            binding.tvSuggestion.text = when {
                state.totalExpense > state.totalIncome ->
                    "Bu ay giderleriniz gelirinizi aştı ⚠️"
                state.totalIncome > 0 && state.totalExpense > state.totalIncome * 0.8 ->
                    "Harcamalarınız gelirinizin %${((state.totalExpense / state.totalIncome) * 100).toInt()}'ine ulaştı"
                else -> "Harika! Bu ay bütçeni dengeli tutuyorsun 👍"
            }
            binding.tvHighestCategory.text = state.highestCategory.ifEmpty { "—" }
        }

        val bugun = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        val yediGunSonra = bugun + 7 * 24 * 60 * 60 * 1000L

        val yaklasamaContainer = binding.root.findViewById<View>(R.id.yaklasan_odeme_container)
        val yaklasanOdemeler = state.upcomingReminders.filter { r ->
            !r.isPaid && r.dueDate >= bugun && r.dueDate <= yediGunSonra
        }

        if (yaklasanOdemeler.isEmpty()) {
            binding.rvDashboardReminders.visibility = View.GONE
            binding.emptyReminders.visibility = View.VISIBLE
            yaklasamaContainer?.visibility = View.GONE
        } else {
            yaklasamaContainer?.visibility = View.VISIBLE
            binding.rvDashboardReminders.visibility = View.VISIBLE
            binding.emptyReminders.visibility = View.GONE
            val dateFormat = SimpleDateFormat("d MMMM", Locale("tr"))
            binding.rvDashboardReminders.adapter = ReminderAdapter(
                yaklasanOdemeler.map { reminder ->
                    val dateStr = when {
                        reminder.daysUntilDue == 0 -> "Bugün"
                        reminder.daysUntilDue == 1 -> "Yarın"
                        reminder.daysUntilDue < 0  -> "Gecikmiş"
                        else -> dateFormat.format(Date(reminder.dueDate))
                    }
                    ReminderModel(reminder.title, CurrencyFormatter.format(reminder.amount), dateStr,
                        if (reminder.isPaid) 1 else 0)
                }
            ) {
                (requireActivity() as? MainActivity)?.gosterYaklasan()
            }
        }
    }

    private fun updateTransactionList(transactions: List<com.example.hesapyonetimi.domain.model.Transaction>) {
        if (transactions.isEmpty()) {
            binding.tvEmptyState.visibility = View.VISIBLE
            binding.rvRecentTransactions.visibility = View.GONE
        } else {
            binding.tvEmptyState.visibility = View.GONE
            binding.rvRecentTransactions.visibility = View.VISIBLE
            val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
            val transactionModels = transactions.take(3).map { t ->
                TransactionModel(
                    id = t.id,
                    title = t.description,
                    category = t.categoryName,
                    amount = CurrencyFormatter.formatWithSign(t.amount, t.isIncome),
                    isIncome = t.isIncome,
                    time = timeFormat.format(Date(t.date)),
                    transaction = t
                )
            }
            binding.rvRecentTransactions.adapter = TransactionAdapter(transactionModels) { transaction ->
                com.example.hesapyonetimi.presentation.common.EditTransactionSheet
                    .newInstance(transaction)
                    .show(childFragmentManager, "EditTransaction")
            }
        }
    }

    private fun refreshCalendar(daysWithData: Set<Int>) {
        val calendar = Calendar.getInstance()
        val daysInMonth = calendar.getActualMaximum(Calendar.DAY_OF_MONTH)
        val dayFormat = SimpleDateFormat("EEE", Locale("tr"))
        val numberFormat = SimpleDateFormat("dd", Locale.getDefault())
        val monthlyDays = (1..daysInMonth).map { i ->
            val c = (calendar.clone() as Calendar).also { it.set(Calendar.DAY_OF_MONTH, i) }
            CalendarModel(dayFormat.format(c.time), numberFormat.format(c.time), hasData = daysWithData.contains(i))
        }
        binding.rvDashboardCalendar.adapter = CalendarAdapter(monthlyDays, selectedDayPosition) { pos ->
            selectedDayPosition = pos
            val c = Calendar.getInstance().also { it.set(Calendar.DAY_OF_MONTH, pos + 1); it.set(Calendar.HOUR_OF_DAY, 12) }
            viewModel.getTransactionsByDate(c.timeInMillis)
            binding.rvDashboardCalendar.adapter?.notifyDataSetChanged()
        }
        binding.rvDashboardCalendar.postDelayed({
            val lm = binding.rvDashboardCalendar.layoutManager as? LinearLayoutManager
            lm?.scrollToPositionWithOffset(selectedDayPosition, 0)
        }, 50)
    }

    private fun setupMonthlyCalendar() {
        val calendar = Calendar.getInstance()
        val currentDay = calendar.get(Calendar.DAY_OF_MONTH)
        val daysInMonth = calendar.getActualMaximum(Calendar.DAY_OF_MONTH)
        val dayFormat = SimpleDateFormat("EEE", Locale("tr"))
        val numberFormat = SimpleDateFormat("dd", Locale.getDefault())
        val daysWithData = viewModel.uiState.value.daysWithTransactions
        val monthlyDays = (1..daysInMonth).map { i ->
            val c = (calendar.clone() as Calendar).also { it.set(Calendar.DAY_OF_MONTH, i) }
            CalendarModel(dayFormat.format(c.time), numberFormat.format(c.time), hasData = daysWithData.contains(i))
        }
        selectedDayPosition = currentDay - 1
        binding.rvDashboardCalendar.adapter = CalendarAdapter(monthlyDays, selectedDayPosition) { pos ->
            selectedDayPosition = pos
            val c = Calendar.getInstance().also { it.set(Calendar.DAY_OF_MONTH, pos + 1); it.set(Calendar.HOUR_OF_DAY, 12) }
            viewModel.getTransactionsByDate(c.timeInMillis)
            binding.rvDashboardCalendar.adapter?.notifyDataSetChanged()
        }
        binding.rvDashboardCalendar.postDelayed({
            val lm = binding.rvDashboardCalendar.layoutManager as? LinearLayoutManager
            lm?.scrollToPositionWithOffset(selectedDayPosition, 0)
        }, 50)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}