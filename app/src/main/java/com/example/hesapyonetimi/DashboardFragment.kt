package com.example.hesapyonetimi

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
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
import com.example.hesapyonetimi.R
import com.example.hesapyonetimi.auth.AuthPrefs
import com.example.hesapyonetimi.presentation.dashboard.DashboardModuleCatalog
import com.example.hesapyonetimi.presentation.dashboard.DashboardViewModel
import com.example.hesapyonetimi.util.PayPeriodResolver
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.example.hesapyonetimi.ui.PieChartView
import androidx.core.content.ContextCompat
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject

@AndroidEntryPoint
class DashboardFragment : Fragment() {

    private var _binding: LayoutDashboardBinding? = null
    private val binding get() = _binding!!
    private val viewModel: DashboardViewModel by viewModels()
    private var selectedDayPosition = -1
    private var lastKnownExpense = 0.0
    private var budgetWarnShown = false

    private fun currentLocale(): Locale = resources.configuration.locales.get(0)

    @Inject
    lateinit var userProfileDao: UserProfileDao

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = LayoutDashboardBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        initUI()
        setupMonthlyCalendar()
        setupMiniPieSectionBehavior()
        observeViewModel()
        observeBudgetWarning()
        observeSuggestions()
        observeNetBakiye()
        setupClickListeners()
        applyDashboardModuleVisibility()
        applyDashboardModuleOrder()

        val swipeRefresh = binding.root.findViewById<SwipeRefreshLayout>(R.id.swipe_refresh)
        swipeRefresh?.setColorSchemeResources(R.color.green_primary)
        swipeRefresh?.setOnRefreshListener {
            viewModel.refreshData()
            swipeRefresh.isRefreshing = false
        }
    }

    private fun setupClickListeners() {
        binding.fabAddTransaction.setOnClickListener { showAddTransactionDialog(null) }
        binding.cardIncome.setOnClickListener { showAddTransactionDialog(isIncome = true) }
        binding.cardExpense.setOnClickListener { showAddTransactionDialog(isIncome = false) }


        binding.btnAddReminder.setOnClickListener {
            activity?.let { act ->
                if (act is MainActivity) act.gosterYaklasan()
            }
        }

        // Gecikmiş ödeme banner tıklama → Yaklaşan sayfasına git
        binding.root.findViewById<View>(R.id.overdue_banner)?.setOnClickListener {
            (activity as? MainActivity)?.gosterYaklasan()
        }

        binding.tvMainBalance.setOnClickListener { showPeriodSummaryBottomSheet() }
    }

    private fun applyDashboardModuleOrder() {
        val col = binding.root.findViewById<LinearLayout>(R.id.dashboard_modules_column) ?: return
        val order = AuthPrefs.getDashboardModuleOrder(requireContext())
        val map = mapOf(
            DashboardModuleCatalog.MODULE_OVERDUE to R.id.overdue_banner,
            DashboardModuleCatalog.MODULE_RECURRING_STRIP to R.id.dashboard_recurring_strip,
            DashboardModuleCatalog.MODULE_CARRYOVER_STRIP to R.id.dashboard_carryover_strip,
            DashboardModuleCatalog.MODULE_FORECAST to R.id.dashboard_forecast_card,
            DashboardModuleCatalog.MODULE_MINI_PIE to R.id.dashboard_mini_pie_card,
            DashboardModuleCatalog.MODULE_CALENDAR to R.id.dashboard_calendar_card,
            DashboardModuleCatalog.MODULE_INSIGHTS to R.id.dashboard_section_insights,
            DashboardModuleCatalog.MODULE_FX to R.id.dashboard_fx_card,
            DashboardModuleCatalog.MODULE_REMINDERS to R.id.dashboard_section_reminders,
        )
        val ordered = order.mapNotNull { tag -> map[tag]?.let { id -> col.findViewById<View>(id) } }
        if (ordered.isEmpty()) return
        val mappedIds = map.values.toSet()
        val stray = buildList {
            for (i in 0 until col.childCount) {
                val v = col.getChildAt(i)
                if (v.id != View.NO_ID && v.id !in mappedIds) add(v)
                if (v.id == View.NO_ID) add(v)
            }
        }.distinct()
        stray.forEach { col.removeView(it) }
        ordered.forEach { col.removeView(it) }
        ordered.forEach { col.addView(it) }
        stray.forEach { col.addView(it) }
    }

    private fun showPeriodSummaryBottomSheet() {
        val state = viewModel.uiState.value
        val period = PayPeriodResolver.currentPeriod(requireContext())
        val sheet = BottomSheetDialog(requireContext())
        val v = layoutInflater.inflate(R.layout.sheet_period_summary, null)
        v.findViewById<TextView>(R.id.tv_sheet_period_range).text =
            PayPeriodResolver.formatShortRange(requireContext(), period)
        v.findViewById<TextView>(R.id.tv_sheet_income).text =
            getString(R.string.period_summary_income_line, CurrencyFormatter.format(state.totalIncome))
        v.findViewById<TextView>(R.id.tv_sheet_expense).text =
            getString(R.string.period_summary_expense_line, CurrencyFormatter.format(state.totalExpense))
        val net = state.totalIncome - state.totalExpense
        v.findViewById<TextView>(R.id.tv_sheet_net).text =
            getString(
                R.string.period_summary_net_line,
                CurrencyFormatter.formatWithSign(net, net >= 0)
            )
        sheet.setContentView(v)
        sheet.show()
    }

    private fun showAddTransactionDialog(isIncome: Boolean?) {
        AddTransactionDialog.newInstance(isIncome).show(childFragmentManager, "AddTransactionDialog")
    }

    private fun initUI() {
        binding.rvRecentTransactions.layoutManager = LinearLayoutManager(context)
        binding.rvDashboardCalendar.layoutManager =
            LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false)
        binding.rvDashboardReminders.layoutManager = LinearLayoutManager(context)
    }

    private fun setupMiniPieSectionBehavior() {
        binding.btnToggleMiniPie.setOnClickListener {
            if (!AuthPrefs.isDashboardMiniPieSectionVisible(requireContext())) return@setOnClickListener
            val next = binding.dashboardMiniPie.visibility != View.VISIBLE
            AuthPrefs.setDashboardMiniPieExpanded(requireContext(), next)
            applyMiniPieExpandState()
        }
        applyMiniPieExpandState()
    }

    private fun applyMiniPieExpandState() {
        if (_binding == null) return
        if (!AuthPrefs.isDashboardMiniPieSectionVisible(requireContext())) return
        val expanded = AuthPrefs.isDashboardMiniPieExpanded(requireContext())
        binding.dashboardMiniPie.visibility = if (expanded) View.VISIBLE else View.GONE
        binding.btnToggleMiniPie.rotation = if (expanded) 180f else 0f
    }

    /** Çekmece ana sayfa anahtarları değişince çağrılır. */
    fun refreshDashboardModulePrefs() {
        if (_binding == null || !isAdded) return
        applyDashboardModuleVisibility()
        applyDashboardModuleOrder()
        applyMiniPieExpandState()
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
                launch {
                    viewModel.uiState
                        .map { it.expensePieSlices }
                        .distinctUntilChanged()
                        .collect { slices ->
                            val entries = slices.map {
                                PieChartView.PieEntry(it.value, it.color, it.label)
                            }
                            binding.dashboardMiniPie.setData(entries)
                        }
                }
            }
        }
    }

    private fun updateUI(state: com.example.hesapyonetimi.presentation.dashboard.DashboardUiState) {
        val forecastCard = binding.root.findViewById<View>(R.id.dashboard_forecast_card)
        val tvForecastCompact = binding.root.findViewById<TextView>(R.id.tv_forecast_compact)
        val forecastParts = listOfNotNull(
            state.forecastHeadline.trim().takeIf { it.isNotEmpty() },
            state.forecastLine1.trim().takeIf { it.isNotEmpty() },
            state.forecastLine2.trim().takeIf { it.isNotEmpty() }
        )
        if (forecastParts.isEmpty()) {
            forecastCard?.visibility = View.GONE
        } else {
            forecastCard?.visibility = View.VISIBLE
            tvForecastCompact?.visibility = View.VISIBLE
            tvForecastCompact?.text = forecastParts.joinToString(separator = " — ")
        }

        binding.tvMainBalance.text = CurrencyFormatter.format(state.totalBalance)
        binding.tvDashboardIncomeVal.text = CurrencyFormatter.format(state.totalIncome)
        binding.tvDashboardExpenseVal.text = CurrencyFormatter.format(state.totalExpense)

        val period = PayPeriodResolver.currentPeriod(requireContext())
        binding.tvMonthTitle.text = getString(
            R.string.dashboard_period_title,
            PayPeriodResolver.formatShortRange(requireContext(), period)
        )

        val recurringStrip = binding.root.findViewById<TextView>(R.id.dashboard_recurring_strip)
        val recurringText = state.recurringWorkerBanner?.trim().orEmpty()
        if (recurringText.isEmpty()) {
            recurringStrip?.visibility = View.GONE
        } else {
            recurringStrip?.visibility = View.VISIBLE
            recurringStrip?.text = recurringText
        }

        val carryStrip = binding.root.findViewById<TextView>(R.id.dashboard_carryover_strip)
        val showCarryoverHint =
            state.periodTransactionCount >= 5 && (state.totalIncome > 0.0 || state.totalExpense > 0.0)
        carryStrip?.visibility = if (showCarryoverHint) View.VISIBLE else View.GONE

        val suggestionContainer = binding.root.findViewById<View>(R.id.suggestion_container)
        if (state.totalIncome == 0.0 && state.totalExpense == 0.0) {
            suggestionContainer?.visibility = View.GONE
        } else {
            suggestionContainer?.visibility = View.VISIBLE
            val dayIdx = PayPeriodResolver.currentDayIndexInPeriod(requireContext())
            val minTxForStrongWarnings = 5
            val suppressEarlyBurn =
                dayIdx <= 5 && state.periodTransactionCount < minTxForStrongWarnings
            // Bütçe/denge durum metni — tv_suggestion'a (akıllı öneri tvSmartTip'e)
            binding.tvSuggestion.text = when {
                !suppressEarlyBurn && state.totalExpense > state.totalIncome ->
                    getString(R.string.dashboard_suggestion_expense_over_income)
                !suppressEarlyBurn && state.totalIncome > 0 && state.totalExpense > state.totalIncome * 0.8 ->
                    getString(
                        R.string.dashboard_suggestion_spend_ratio,
                        ((state.totalExpense / state.totalIncome) * 100).toInt()
                    )
                else -> getString(R.string.dashboard_suggestion_balanced)
            }
            binding.tvHighestCategory.text = state.highestCategory.ifEmpty {
                getString(R.string.dashboard_category_placeholder)
            }
        }

        // Gecikmiş ödemeler banner
        val now = System.currentTimeMillis()
        val gecikmisler = state.upcomingReminders.filter { !it.isPaid && it.dueDate < now }
        val overdueBanner = binding.root.findViewById<LinearLayout>(R.id.overdue_banner)
        val overdueTitle = binding.root.findViewById<TextView>(R.id.tv_overdue_title)
        val overdueSubtitle = binding.root.findViewById<TextView>(R.id.tv_overdue_subtitle)
        if (gecikmisler.isEmpty()) {
            overdueBanner?.visibility = View.GONE
        } else {
            overdueBanner?.visibility = View.VISIBLE
            overdueTitle?.text = if (gecikmisler.size == 1) {
                getString(R.string.dashboard_overdue_title_one)
            } else {
                getString(R.string.dashboard_overdue_title_many, gecikmisler.size)
            }
            val totalOverdue = gecikmisler.sumOf { it.amount }
            overdueSubtitle?.text = getString(
                R.string.dashboard_overdue_subtitle,
                CurrencyFormatter.format(totalOverdue)
            )
        }

        // Yaklaşan ödemeler (sadece bugün ve sonrası, gecikmişler ayrı bannerda)
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
            val dateFormat = SimpleDateFormat("d MMMM", currentLocale())
            binding.rvDashboardReminders.adapter = ReminderAdapter(
                yaklasanOdemeler.map { reminder ->
                    val dateStr = when {
                        reminder.daysUntilDue == 0 -> getString(R.string.reminder_due_today)
                        reminder.daysUntilDue == 1 -> getString(R.string.reminder_due_tomorrow)
                        reminder.daysUntilDue < 0  -> getString(R.string.reminder_due_overdue)
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
            binding.tvAllTransactions.visibility = View.GONE
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
            binding.tvAllTransactions.visibility = View.VISIBLE
            binding.tvAllTransactions.setOnClickListener {
                (activity as? MainActivity)?.gosterGunluk()
            }
        }
    }

    private fun refreshCalendar(daysWithData: Set<Int>) {
        val calendar = Calendar.getInstance()
        val daysInMonth = calendar.getActualMaximum(Calendar.DAY_OF_MONTH)
        val dayFormat = SimpleDateFormat("EEE", currentLocale())
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
            updateSelectedDateLabel(pos + 1)
        }
        binding.rvDashboardCalendar.postDelayed({
            val lm = binding.rvDashboardCalendar.layoutManager as? LinearLayoutManager
            lm?.scrollToPositionWithOffset(selectedDayPosition, 0)
        }, 50)
        updateSelectedDateLabel(selectedDayPosition + 1)
    }

    private fun setupMonthlyCalendar() {
        val calendar = Calendar.getInstance()
        val currentDay = calendar.get(Calendar.DAY_OF_MONTH)
        val daysInMonth = calendar.getActualMaximum(Calendar.DAY_OF_MONTH)
        val dayFormat = SimpleDateFormat("EEE", currentLocale())
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
            updateSelectedDateLabel(pos + 1)
        }
        binding.rvDashboardCalendar.postDelayed({
            val lm = binding.rvDashboardCalendar.layoutManager as? LinearLayoutManager
            lm?.scrollToPositionWithOffset(selectedDayPosition, 0)
        }, 50)
        updateSelectedDateLabel(currentDay)
    }

    private fun updateSelectedDateLabel(day: Int) {
        val selectedDateLabel = binding.root.findViewById<TextView>(R.id.tv_selected_date_label) ?: return
        val today = Calendar.getInstance().get(Calendar.DAY_OF_MONTH)
        val monthName = SimpleDateFormat("d MMMM", currentLocale()).format(
            Calendar.getInstance().apply { set(Calendar.DAY_OF_MONTH, day) }.time
        )
        selectedDateLabel.visibility = View.VISIBLE
        selectedDateLabel.text = when (day) {
            today -> getString(R.string.dashboard_selected_day_today, monthName)
            today - 1 -> getString(R.string.dashboard_selected_day_yesterday, monthName)
            today + 1 -> getString(R.string.dashboard_selected_day_tomorrow, monthName)
            else -> getString(R.string.dashboard_selected_day_other, monthName)
        }
    }

    // ── Bütçe %80 uyarısı ─────────────────────────────────────────────────────
    private fun observeBudgetWarning() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                combine(
                    viewModel.uiState,
                    userProfileDao.getProfile()
                ) { state, profile ->
                    state.totalExpense to (profile?.monthlyBudgetLimit ?: 0.0)
                }.collect { (expense, limit) ->
                    checkBudgetThreshold(expense, limit)
                }
            }
        }
    }

    private fun checkBudgetThreshold(expense: Double, limit: Double) {
        if (limit <= 0.0) return
        val ratio = expense / limit
        if (ratio >= 0.8 && expense > lastKnownExpense) {
            if (!budgetWarnShown) {
                budgetWarnShown = true
                val percent = (ratio * 100).toInt().coerceAtMost(100)
                Snackbar.make(
                    binding.root,
                    getString(R.string.snackbar_budget_threshold, percent),
                    Snackbar.LENGTH_LONG
                )
                    .setAction(getString(R.string.action_analyze)) {
                        (activity as? MainActivity)?.gosterAylik()
                    }
                    .setActionTextColor(
                        ContextCompat.getColor(requireContext(), R.color.warning)
                    )
                    .show()
            }
        } else if (ratio < 0.8) {
            budgetWarnShown = false
        }
        lastKnownExpense = expense
    }

    // ── Kümülatif net bakiye (devreden) ──────────────────────────────────────
    private fun observeNetBakiye() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.netBakiye.collect { net ->
                    val tvNetBakiye = binding.root.findViewById<TextView>(R.id.tv_net_bakiye)
                    tvNetBakiye?.text = CurrencyFormatter.format(net)
                    tvNetBakiye?.setTextColor(
                        if (net >= 0) 0xFFAAFFCC.toInt() else 0xFFFF8888.toInt()
                    )
                }
            }
        }
    }

    // ── Akıllı öneri + bütçe satırları — tv_smart_tip çok satır ─────────────
    private fun observeSuggestions() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                combine(
                    viewModel.suggestions,
                    viewModel.budgetInsightLines
                ) { suggestions, budgetLines ->
                    suggestions to budgetLines
                }.collect { (suggestions, budgetLines) ->
                    val tvSmartTip = binding.root.findViewById<TextView>(R.id.tv_smart_tip)
                    val smartTipRow = binding.root.findViewById<View>(R.id.smart_tip_row)
                    val smartTipDivider = binding.root.findViewById<View>(R.id.smart_tip_divider)
                    val lines = mutableListOf<String>()
                    suggestions.take(4).forEach { o ->
                        lines.add(
                            getString(
                                R.string.dashboard_smart_tip_line,
                                o.categoryIcon,
                                o.categoryName,
                                CurrencyFormatter.format(o.amount)
                            )
                        )
                    }
                    budgetLines.forEach { lines.add(getString(R.string.dashboard_budget_tip_line, it)) }
                    val tipsCard = binding.root.findViewById<View>(R.id.dashboard_tips_card)
                    if (lines.isEmpty()) {
                        tipsCard?.visibility = View.GONE
                        tvSmartTip?.visibility = View.GONE
                        smartTipRow?.visibility = View.GONE
                        smartTipDivider?.visibility = View.GONE
                        return@collect
                    }
                    tipsCard?.visibility = View.VISIBLE
                    tvSmartTip?.text = lines.joinToString("\n\n")
                    tvSmartTip?.visibility = View.VISIBLE
                    smartTipRow?.visibility = View.VISIBLE
                    smartTipDivider?.visibility = View.VISIBLE
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        refreshDashboardModulePrefs()
    }

    private fun applyDashboardModuleVisibility() {
        if (_binding == null) return
        val ctx = requireContext()
        binding.root.findViewById<View>(R.id.dashboard_fx_card)?.visibility =
            if (AuthPrefs.isDashboardFxVisible(ctx)) View.VISIBLE else View.GONE
        binding.root.findViewById<View>(R.id.dashboard_section_insights)?.visibility =
            if (AuthPrefs.isDashboardInsightsVisible(ctx)) View.VISIBLE else View.GONE
        binding.root.findViewById<View>(R.id.dashboard_section_reminders)?.visibility =
            if (AuthPrefs.isDashboardReminderSectionVisible(ctx)) View.VISIBLE else View.GONE
        binding.root.findViewById<View>(R.id.dashboard_mini_pie_card)?.visibility =
            if (AuthPrefs.isDashboardMiniPieSectionVisible(ctx)) View.VISIBLE else View.GONE
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
