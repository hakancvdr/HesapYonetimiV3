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
import com.example.hesapyonetimi.adapter.CalendarAdapter
import com.example.hesapyonetimi.adapter.TransactionAdapter
import com.example.hesapyonetimi.adapter.HatirlaticiAdapter
import com.example.hesapyonetimi.data.local.dao.UserProfileDao
import com.example.hesapyonetimi.databinding.LayoutDashboardBinding
import com.example.hesapyonetimi.presentation.common.AddTransactionDialog
import com.example.hesapyonetimi.presentation.dashboard.DashboardViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.util.Calendar
import javax.inject.Inject

@AndroidEntryPoint
class DashboardFragment : Fragment() {

    private var _binding: LayoutDashboardBinding? = null
    private val binding get() = _binding!!
    private val viewModel: DashboardViewModel by viewModels()

    @Inject
    lateinit var userProfileDao: UserProfileDao

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = LayoutDashboardBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        initUI()
        setupClickListeners()
        observeViewModel()
        observeProfile()
    }

    private fun dpToPx(dp: Int) = (dp * resources.displayMetrics.density).toInt()

    private fun setupClickListeners() {
        binding.fabAddTransaction.setOnClickListener { showAddTransactionDialog(null) }
        binding.cardIncome.setOnClickListener { showAddTransactionDialog(isIncome = true) }
        binding.cardExpense.setOnClickListener { showAddTransactionDialog(isIncome = false) }

        // ── Profil simgesi → Profil sekmesine git ──────────────────────────
        binding.ivProfile.setOnClickListener {
            (activity as? MainActivity)?.gosterProfil()
        }

        binding.btnAddReminder.setOnClickListener {
            activity?.let { act ->
                if (act is MainActivity) act.gosterYaklasan()
            }
        }
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

    // ── Profil adını gözlemle → Dashboard'a yansıt ─────────────────────────
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
                    viewModel.recentTransactions.collectLatest { transactions ->
                        val adapter = binding.rvRecentTransactions.adapter as? TransactionAdapter
                            ?: TransactionAdapter(
                                onItemClick = { tx ->
                                    com.example.hesapyonetimi.presentation.common.EditTransactionSheet
                                        .newInstance(tx)
                                        .show(childFragmentManager, "EditTransaction")
                                }
                            ).also { binding.rvRecentTransactions.adapter = it }
                        adapter.submitList(transactions)

                        binding.tvEmptyState.visibility =
                            if (transactions.isEmpty()) View.VISIBLE else View.GONE
                        binding.rvRecentTransactions.visibility =
                            if (transactions.isEmpty()) View.GONE else View.VISIBLE
                    }
                }

                launch {
                    viewModel.currentMonthSummary.collectLatest { summary ->
                        binding.tvTotalBalance.text =
                            com.example.hesapyonetimi.presentation.common.CurrencyFormatter.format(summary.balance)
                        binding.tvIncomeAmount.text =
                            com.example.hesapyonetimi.presentation.common.CurrencyFormatter.format(summary.income)
                        binding.tvExpenseAmount.text =
                            com.example.hesapyonetimi.presentation.common.CurrencyFormatter.format(summary.expense)
                    }
                }

                launch {
                    viewModel.daysWithTransactions.collectLatest { days ->
                        val calAdapter = binding.rvDashboardCalendar.adapter as? CalendarAdapter
                            ?: CalendarAdapter().also { binding.rvDashboardCalendar.adapter = it }
                        calAdapter.submitList(days)
                    }
                }

                launch {
                    viewModel.upcomingReminders.collectLatest { reminders ->
                        val reminderAdapter = binding.rvDashboardReminders.adapter as? HatirlaticiAdapter
                            ?: HatirlaticiAdapter(
                                onPaidClick = { viewModel.markReminderPaid(it) },
                                onDeleteClick = { viewModel.deleteReminder(it) }
                            ).also { binding.rvDashboardReminders.adapter = it }
                        reminderAdapter.submitList(reminders)

                        binding.tvNoReminders.visibility =
                            if (reminders.isEmpty()) View.VISIBLE else View.GONE
                        binding.rvDashboardReminders.visibility =
                            if (reminders.isEmpty()) View.GONE else View.VISIBLE
                        binding.btnAddReminder.visibility =
                            if (reminders.isEmpty()) View.VISIBLE else View.GONE
                    }
                }

                launch {
                    viewModel.suggestions.collectLatest { suggestions ->
                        binding.rvSuggestions?.let { rv ->
                            // Öneri adapter varsa güncelle
                        }
                    }
                }
            }
        }
    }

    private fun showAddTransactionDialog(isIncome: Boolean?) {
        AddTransactionDialog.newInstance(isIncome).show(childFragmentManager, "AddTransactionDialog")
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
