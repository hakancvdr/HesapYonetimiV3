package com.example.hesapyonetimi

import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.NumberPicker
import android.widget.TextView
import android.widget.Toast
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.example.hesapyonetimi.auth.AuthPrefs
import com.example.hesapyonetimi.util.PayPeriodResolver
import com.example.hesapyonetimi.presentation.profile.ProfileUiEvent
import com.example.hesapyonetimi.presentation.profile.ProfileViewModel
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.progressindicator.LinearProgressIndicator
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.util.Locale

@AndroidEntryPoint
class ProfileFragment : Fragment() {

    private val viewModel: ProfileViewModel by viewModels()

    private lateinit var tvAvatar: TextView
    private lateinit var tvUserName: TextView
    private lateinit var tvMemberSince: TextView
    private lateinit var tvProfilePayWindow: TextView
    private lateinit var tvHeroThirdStat: TextView
    private lateinit var tvSalaryDaySummary: TextView
    private lateinit var tvMenuBudgetSubtitle: TextView
    private lateinit var tvTotalTransactions: TextView
    private lateinit var tvCategoryCount: TextView
    private lateinit var tvBudgetLimit: TextView
    private lateinit var budgetProgressContainer: View
    private lateinit var budgetProgress: LinearProgressIndicator
    private lateinit var tvBudgetUsed: TextView
    private lateinit var tvBudgetPercent: TextView
    private lateinit var tvPayPeriodSubtitle: TextView

    private val currencyFormat = NumberFormat.getCurrencyInstance(Locale("tr", "TR"))

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_profile, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        bindViews(view)
        ViewCompat.setOnApplyWindowInsetsListener(view.findViewById(R.id.profile_scroll)) { v, insets ->
            val nav = insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom
            val extra = (12 * resources.displayMetrics.density).toInt()
            v.updatePadding(bottom = nav + extra)
            insets
        }
        setupClicks(view)
        observe()
        refreshPayPeriodSubtitle()
    }

    override fun onResume() {
        super.onResume()
        refreshPayPeriodSubtitle()
        viewModel.refreshPayPeriodDependentFlows()
    }

    private fun bindViews(v: View) {
        tvAvatar = v.findViewById(R.id.tvAvatar)
        tvUserName = v.findViewById(R.id.tvUserName)
        tvMemberSince = v.findViewById(R.id.tvMemberSince)
        tvProfilePayWindow = v.findViewById(R.id.tvProfilePayWindow)
        tvHeroThirdStat = v.findViewById(R.id.tvHeroThirdStat)
        tvSalaryDaySummary = v.findViewById(R.id.tvSalaryDaySummary)
        tvMenuBudgetSubtitle = v.findViewById(R.id.tvMenuBudgetSubtitle)
        tvTotalTransactions = v.findViewById(R.id.tvTotalTransactions)
        tvCategoryCount = v.findViewById(R.id.tvCategoryCount)
        tvBudgetLimit = v.findViewById(R.id.tvBudgetLimit)
        budgetProgressContainer = v.findViewById(R.id.budgetProgressContainer)
        budgetProgress = v.findViewById(R.id.budgetProgress)
        tvBudgetUsed = v.findViewById(R.id.tvBudgetUsed)
        tvBudgetPercent = v.findViewById(R.id.tvBudgetPercent)
        tvPayPeriodSubtitle = v.findViewById(R.id.tvPayPeriodSubtitle)
    }

    private fun setupClicks(v: View) {
        v.findViewById<View>(R.id.btnEditName).setOnClickListener { showEditNameDialog() }
        v.findViewById<View>(R.id.tvUserName).setOnClickListener { showEditNameDialog() }
        v.findViewById<View>(R.id.btnEditBudget).setOnClickListener { showEditBudgetDialog() }
        v.findViewById<View>(R.id.cardProPage).setOnClickListener {
            findNavController().navigate(R.id.action_profil_to_pro)
        }
        v.findViewById<View>(R.id.cardWipeFinancialData).setOnClickListener { showWipeFinancialDataDialog() }
        v.findViewById<View>(R.id.row_profile_menu_pay_period).setOnClickListener { showPayPeriodDialog() }
        v.findViewById<View>(R.id.cardProfileSalarySummary).setOnClickListener { showPayPeriodDialog() }
        v.findViewById<View>(R.id.row_profile_menu_budget).setOnClickListener { showEditBudgetDialog() }
        v.findViewById<View>(R.id.cardProfileBudgetSummary).setOnClickListener { showEditBudgetDialog() }
        v.findViewById<View>(R.id.row_profile_menu_security).setOnClickListener {
            findNavController().navigate(R.id.nav_security)
        }
    }

    private fun observe() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {

                launch {
                    combine(
                        viewModel.profile,
                        viewModel.currentMonthExpense,
                        viewModel.stats
                    ) { profile, expense, stats ->
                        Triple(profile, expense, stats)
                    }.collectLatest { (profile, expense, stats) ->
                        tvTotalTransactions.text =
                            getString(R.string.profile_stat_transactions, stats.totalTransactions)
                        tvCategoryCount.text =
                            getString(R.string.profile_stat_categories, stats.categoryCount)

                        val tierLabel = if (AuthPrefs.isProMember(requireContext())) {
                            getString(R.string.membership_premium)
                        } else {
                            getString(R.string.membership_free)
                        }
                        tvMemberSince.text = tierLabel

                        val p = profile
                        if (p == null) {
                            tvHeroThirdStat.text = if (stats.totalTransactions > 0) {
                                getString(R.string.profile_hero_stat_active)
                            } else {
                                getString(R.string.profile_hero_stat_new)
                            }
                            tvMenuBudgetSubtitle.text = getString(R.string.profile_menu_budget_subtitle_none)
                            return@collectLatest
                        }

                        tvAvatar.text =
                            (p.displayName.firstOrNull()?.uppercaseChar()?.toString() ?: "K")
                        tvUserName.text = p.displayName

                        if (p.monthlyBudgetLimit > 0) {
                            tvBudgetLimit.text = currencyFormat.format(p.monthlyBudgetLimit)
                            budgetProgressContainer.visibility = View.VISIBLE
                            tvBudgetUsed.text = getString(
                                R.string.profile_budget_used_period,
                                currencyFormat.format(expense)
                            )
                            val percent = ((expense / p.monthlyBudgetLimit) * 100)
                                .coerceIn(0.0, 100.0).toInt()
                            tvBudgetPercent.text = "%$percent"
                            budgetProgress.progress = percent
                            tvHeroThirdStat.text =
                                getString(R.string.profile_hero_budget_percent, percent)
                            tvMenuBudgetSubtitle.text = getString(
                                R.string.profile_menu_budget_subtitle,
                                currencyFormat.format(expense),
                                currencyFormat.format(p.monthlyBudgetLimit)
                            )
                        } else {
                            tvBudgetLimit.text = getString(R.string.profile_budget_not_set_label)
                            budgetProgressContainer.visibility = View.GONE
                            tvHeroThirdStat.text = if (stats.totalTransactions > 0) {
                                getString(R.string.profile_hero_stat_active)
                            } else {
                                getString(R.string.profile_hero_stat_new)
                            }
                            tvMenuBudgetSubtitle.text =
                                getString(R.string.profile_menu_budget_subtitle_none)
                        }
                    }
                }

                launch {
                    viewModel.uiEvent.collectLatest { event ->
                        when (event) {
                            is ProfileUiEvent.ThemeChanged ->
                                (activity as? MainActivity)?.applyTheme(event.mode)
                            is ProfileUiEvent.ShowMessage -> showSnack(event.message)
                        }
                    }
                }
            }
        }
    }

    private fun showEditNameDialog() {
        val dialog = buildDialog(R.layout.dialog_profile_edit_name)
        val etName = dialog.findViewById<TextInputEditText>(R.id.etName)
        val tilName = dialog.findViewById<TextInputLayout>(R.id.tilName)
        etName.setText(viewModel.profile.value?.displayName ?: "")
        etName.setSelection(etName.text?.length ?: 0)

        dialog.findViewById<View>(R.id.btnCancel).setOnClickListener { dialog.dismiss() }
        dialog.findViewById<View>(R.id.btnSave).setOnClickListener {
            val name = etName.text?.toString().orEmpty().trim()
            if (name.isBlank()) {
                tilName.error = "İsim boş olamaz"
                return@setOnClickListener
            }
            viewModel.updateName(name)
            dialog.dismiss()
        }
        dialog.show()
    }

    private fun showEditBudgetDialog() {
        val dialog = buildDialog(R.layout.dialog_profile_edit_budget)
        val etBudget = dialog.findViewById<TextInputEditText>(R.id.etBudget)
        val tilBudget = dialog.findViewById<TextInputLayout>(R.id.tilBudget)
        val current = viewModel.profile.value?.monthlyBudgetLimit ?: 0.0
        if (current > 0) etBudget.setText(current.toBigDecimal().stripTrailingZeros().toPlainString())

        dialog.findViewById<View>(R.id.btnCancel).setOnClickListener { dialog.dismiss() }
        dialog.findViewById<View>(R.id.btnClear).setOnClickListener {
            viewModel.updateBudgetLimit(0.0)
            dialog.dismiss()
        }
        dialog.findViewById<View>(R.id.btnSave).setOnClickListener {
            val amount = etBudget.text?.toString()?.toDoubleOrNull()
            if (amount == null || amount <= 0) {
                tilBudget.error = "Geçerli bir tutar girin"
                return@setOnClickListener
            }
            viewModel.updateBudgetLimit(amount)
            dialog.dismiss()
        }
        dialog.show()
    }

    private fun buildDialog(layoutRes: Int, widthRatio: Double = 0.90): Dialog {
        val dialog = Dialog(requireContext())
        dialog.setContentView(layoutRes)
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.window?.setLayout((resources.displayMetrics.widthPixels * widthRatio).toInt(), ViewGroup.LayoutParams.WRAP_CONTENT)
        return dialog
    }

    private fun showWipeFinancialDataDialog() {
        val input = TextInputEditText(requireContext()).apply { hint = "VERİLERİMİ SİL" }
        val wrap = FrameLayout(requireContext()).apply {
            val pad = (22 * resources.displayMetrics.density).toInt()
            setPadding(pad, pad, pad, 0)
            addView(input)
        }
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Tüm finansal verileri sil")
            .setMessage("İşlemler, hatırlatıcılar, hedefler ve bütçe kayıtları kalıcı olarak silinir. Devam etmek için aşağıya tam metni yazın.")
            .setView(wrap)
            .setPositiveButton("Sil") { _, _ ->
                if (input.text?.toString()?.trim() == "VERİLERİMİ SİL") {
                    viewModel.wipeAllFinancialData()
                } else {
                    Toast.makeText(requireContext(), "Onay metni tam eşleşmedi.", Toast.LENGTH_LONG).show()
                }
            }
            .setNegativeButton("İptal", null)
            .show()
    }

    private fun refreshPayPeriodSubtitle() {
        if (!::tvPayPeriodSubtitle.isInitialized) return
        val ctx = requireContext()
        tvPayPeriodSubtitle.text =
            if (AuthPrefs.getPayPeriodMode(ctx) == AuthPrefs.PAY_PERIOD_MODE_SALARY) {
                getString(R.string.pay_period_subtitle_salary, AuthPrefs.getSalaryDayOfMonth(ctx))
            } else {
                getString(R.string.pay_period_subtitle_calendar)
            }
        if (::tvProfilePayWindow.isInitialized) {
            tvProfilePayWindow.text = PayPeriodResolver.formatShortRange(
                ctx,
                PayPeriodResolver.currentPeriod(ctx)
            )
        }
        if (::tvSalaryDaySummary.isInitialized) {
            tvSalaryDaySummary.text =
                if (AuthPrefs.getPayPeriodMode(ctx) == AuthPrefs.PAY_PERIOD_MODE_SALARY) {
                    getString(R.string.profile_salary_day_nth, AuthPrefs.getSalaryDayOfMonth(ctx))
                } else {
                    getString(R.string.profile_pay_calendar_short)
                }
        }
    }

    private fun showPayPeriodDialog() {
        val ctx = requireContext()
        val labels = arrayOf(
            getString(R.string.pay_period_option_calendar),
            getString(R.string.pay_period_option_salary)
        )
        MaterialAlertDialogBuilder(ctx)
            .setTitle(R.string.pay_period_dialog_title)
            .setItems(labels) { dialog, which ->
                dialog.dismiss()
                when (which) {
                    0 -> {
                        AuthPrefs.setPayPeriodMode(ctx, AuthPrefs.PAY_PERIOD_MODE_CALENDAR)
                        viewModel.refreshPayPeriodDependentFlows()
                        refreshPayPeriodSubtitle()
                    }
                    1 -> showSalaryDayDialog()
                }
            }
            .show()
    }

    private fun showSalaryDayDialog() {
        val ctx = requireContext()
        val picker = NumberPicker(ctx).apply {
            minValue = 1
            maxValue = 31
            wrapSelectorWheel = false
            value = AuthPrefs.getSalaryDayOfMonth(ctx)
        }
        MaterialAlertDialogBuilder(ctx)
            .setTitle(R.string.pay_period_salary_day_title)
            .setView(picker)
            .setNegativeButton(R.string.close, null)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                AuthPrefs.setPayPeriodMode(ctx, AuthPrefs.PAY_PERIOD_MODE_SALARY)
                AuthPrefs.setSalaryDayOfMonth(ctx, picker.value)
                viewModel.refreshPayPeriodDependentFlows()
                refreshPayPeriodSubtitle()
            }
            .show()
    }

    private fun showSnack(message: String) {
        view?.let { Snackbar.make(it, message, Snackbar.LENGTH_SHORT).show() }
    }
}
