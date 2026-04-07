package com.example.hesapyonetimi

import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.hesapyonetimi.domain.model.Category
import com.example.hesapyonetimi.presentation.profile.ProfileCategoryAdapter
import com.example.hesapyonetimi.presentation.profile.ProfileUiEvent
import com.example.hesapyonetimi.presentation.profile.ProfileViewModel
import com.google.android.material.progressindicator.LinearProgressIndicator
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.tabs.TabLayout
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.util.*

@AndroidEntryPoint
class ProfileFragment : Fragment() {

    private val viewModel: ProfileViewModel by viewModels()

    private lateinit var tvAvatar: TextView
    private lateinit var tvUserName: TextView
    private lateinit var tvMemberSince: TextView
    private lateinit var tvTotalTransactions: TextView
    private lateinit var tvCategoryCount: TextView
    private lateinit var tvActiveDays: TextView
    private lateinit var tvBudgetLimit: TextView
    private lateinit var budgetProgressContainer: View
    private lateinit var budgetProgress: LinearProgressIndicator
    private lateinit var tvBudgetUsed: TextView
    private lateinit var tvBudgetPercent: TextView
    private lateinit var tvCategorySubtitle: TextView
    private lateinit var tvThemeSubtitle: TextView
    private lateinit var tvAppVersion: TextView

    private val currencyFormat = NumberFormat.getCurrencyInstance(Locale("tr", "TR"))

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_profile, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        bindViews(view)
        setupClicks(view)
        observe()

        try {
            tvAppVersion.text = "v${requireContext().packageManager
                .getPackageInfo(requireContext().packageName, 0).versionName}"
        } catch (_: Exception) { tvAppVersion.text = "v1.0" }
    }

    private fun bindViews(v: View) {
        tvAvatar = v.findViewById(R.id.tvAvatar)
        tvUserName = v.findViewById(R.id.tvUserName)
        tvMemberSince = v.findViewById(R.id.tvMemberSince)
        tvTotalTransactions = v.findViewById(R.id.tvTotalTransactions)
        tvCategoryCount = v.findViewById(R.id.tvCategoryCount)
        tvActiveDays = v.findViewById(R.id.tvActiveDays)
        tvBudgetLimit = v.findViewById(R.id.tvBudgetLimit)
        budgetProgressContainer = v.findViewById(R.id.budgetProgressContainer)
        budgetProgress = v.findViewById(R.id.budgetProgress)
        tvBudgetUsed = v.findViewById(R.id.tvBudgetUsed)
        tvBudgetPercent = v.findViewById(R.id.tvBudgetPercent)
        tvCategorySubtitle = v.findViewById(R.id.tvCategorySubtitle)
        tvThemeSubtitle = v.findViewById(R.id.tvThemeSubtitle)
        tvAppVersion = v.findViewById(R.id.tvAppVersion)
    }

    private fun setupClicks(v: View) {
        v.findViewById<View>(R.id.btnEditAvatar).setOnClickListener { showAvatarPicker() }
        v.findViewById<View>(R.id.btnEditName).setOnClickListener { showEditNameDialog() }
        v.findViewById<View>(R.id.tvUserName).setOnClickListener { showEditNameDialog() }
        v.findViewById<View>(R.id.btnEditBudget).setOnClickListener { showEditBudgetDialog() }
        v.findViewById<View>(R.id.cardCategories).setOnClickListener { showCategoryDialog() }
        v.findViewById<View>(R.id.cardTheme).setOnClickListener { showThemeDialog() }
    }

    private fun observe() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.profile.collectLatest { profile ->
                profile ?: return@collectLatest
                tvAvatar.text = profile.avatarEmoji
                tvUserName.text = profile.displayName

                if (profile.monthlyBudgetLimit > 0) {
                    tvBudgetLimit.text = currencyFormat.format(profile.monthlyBudgetLimit)
                    budgetProgressContainer.visibility = View.VISIBLE
                } else {
                    tvBudgetLimit.text = "Ayarlanmadı"
                    budgetProgressContainer.visibility = View.GONE
                }

                tvThemeSubtitle.text = when (profile.themeMode) {
                    "LIGHT" -> "Açık tema"
                    "DARK"  -> "Koyu tema"
                    else    -> "Sistem varsayılanı"
                }
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.stats.collectLatest { stats ->
                tvTotalTransactions.text = stats.totalTransactions.toString()
                tvCategoryCount.text = stats.categoryCount.toString()
                tvActiveDays.text = stats.activeDays.toString()
                tvMemberSince.text = "Üye: ${stats.memberSince}"
                tvCategorySubtitle.text = "${stats.categoryCount} kategori"
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.uiEvent.collectLatest { event ->
                when (event) {
                    is ProfileUiEvent.ThemeChanged -> {
                        // ── Tema değişikliğini Activity'e devret (çöküş olmaz) ──
                        (activity as? MainActivity)?.applyTheme(event.mode)
                    }
                    is ProfileUiEvent.ShowMessage -> showSnack(event.message)
                }
            }
        }
    }

    // ── Dialoglar ─────────────────────────────────────────────────────────────

    private fun showEditNameDialog() {
        val dialog = buildDialog(R.layout.dialog_profile_edit_name)
        val etName = dialog.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.etName)
        val tilName = dialog.findViewById<com.google.android.material.textfield.TextInputLayout>(R.id.tilName)
        etName.setText(viewModel.profile.value?.displayName ?: "")
        etName.setSelection(etName.text?.length ?: 0)

        dialog.findViewById<View>(R.id.btnCancel).setOnClickListener { dialog.dismiss() }
        dialog.findViewById<View>(R.id.btnSave).setOnClickListener {
            val name = etName.text?.toString().orEmpty().trim()
            if (name.isBlank()) { tilName.error = "İsim boş olamaz"; return@setOnClickListener }
            viewModel.updateName(name)
            dialog.dismiss()
        }
        dialog.show()
    }

    private fun showEditBudgetDialog() {
        val dialog = buildDialog(R.layout.dialog_profile_edit_budget)
        val etBudget = dialog.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.etBudget)
        val tilBudget = dialog.findViewById<com.google.android.material.textfield.TextInputLayout>(R.id.tilBudget)
        val current = viewModel.profile.value?.monthlyBudgetLimit ?: 0.0
        if (current > 0) etBudget.setText(current.toBigDecimal().stripTrailingZeros().toPlainString())

        dialog.findViewById<View>(R.id.btnCancel).setOnClickListener { dialog.dismiss() }
        dialog.findViewById<View>(R.id.btnClear).setOnClickListener {
            viewModel.updateBudgetLimit(0.0); dialog.dismiss()
        }
        dialog.findViewById<View>(R.id.btnSave).setOnClickListener {
            val amount = etBudget.text?.toString()?.toDoubleOrNull()
            if (amount == null || amount <= 0) {
                tilBudget.error = "Geçerli bir tutar girin"; return@setOnClickListener
            }
            viewModel.updateBudgetLimit(amount); dialog.dismiss()
        }
        dialog.show()
    }

    private fun showCategoryDialog() {
        val dialog = buildDialog(R.layout.dialog_profile_categories, widthRatio = 0.95)
        val rv = dialog.findViewById<RecyclerView>(R.id.rvCategories)
        val tabFilter = dialog.findViewById<TabLayout>(R.id.tabFilter)
        val fabAdd = dialog.findViewById<com.google.android.material.floatingactionbutton.FloatingActionButton>(R.id.fabAddCategory)

        val adapter = ProfileCategoryAdapter { category -> viewModel.deleteCategory(category) }
        rv.layoutManager = LinearLayoutManager(requireContext())
        rv.adapter = adapter

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.categories.collectLatest { list ->
                if (dialog.isShowing) adapter.submitFullList(list)
            }
        }

        tabFilter.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) {
                adapter.filterType = when (tab.position) { 1 -> "EXPENSE"; 2 -> "INCOME"; else -> "ALL" }
            }
            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) {}
        })

        fabAdd.setOnClickListener { dialog.dismiss(); showAddCategoryDialog() }
        dialog.show()
    }

    private fun showAddCategoryDialog() {
        val dialog = buildDialog(R.layout.dialog_profile_add_category, widthRatio = 0.92)
        val emojiOptions = listOf(
            "📦","🛒","💡","🚗","🍔","🎬","🏥","💼","💰","🏠","👗",
            "✈️","📚","🎮","🐾","🌿","⚽","🎵","💊","🔧","📱","🎁","☕","🍕"
        )
        var selectedEmoji = "📦"
        val tvSelectedEmoji = dialog.findViewById<TextView>(R.id.tvSelectedEmoji)
        val emojiContainer = dialog.findViewById<LinearLayout>(R.id.emojiContainer)

        emojiOptions.forEach { emoji ->
            TextView(requireContext()).apply {
                text = emoji; textSize = 24f; setPadding(12, 8, 12, 8)
                setOnClickListener { selectedEmoji = emoji; tvSelectedEmoji.text = emoji }
            }.also { emojiContainer.addView(it) }
        }

        val etName = dialog.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.etCategoryName)
        val tilName = dialog.findViewById<com.google.android.material.textfield.TextInputLayout>(R.id.tilCategoryName)
        val toggleType = dialog.findViewById<com.google.android.material.button.MaterialButtonToggleGroup>(R.id.toggleType)

        dialog.findViewById<View>(R.id.btnCancel).setOnClickListener { dialog.dismiss() }
        dialog.findViewById<View>(R.id.btnAdd).setOnClickListener {
            val name = etName.text?.toString().orEmpty().trim()
            if (name.isBlank()) { tilName.error = "Kategori adı girin"; return@setOnClickListener }
            val isIncome = toggleType.checkedButtonId == R.id.btnIncome
            viewModel.addCategory(Category(name = name, icon = selectedEmoji, color = if (isIncome) "#4CAF50" else "#F44336", isIncome = isIncome))
            dialog.dismiss()
            showCategoryDialog()
        }
        dialog.show()
    }

    private fun showThemeDialog() {
        val dialog = buildDialog(R.layout.dialog_profile_theme)
        val currentMode = viewModel.profile.value?.themeMode ?: "SYSTEM"

        dialog.findViewById<TextView>(R.id.ivLightCheck).visibility = if (currentMode == "LIGHT") View.VISIBLE else View.GONE
        dialog.findViewById<TextView>(R.id.ivDarkCheck).visibility = if (currentMode == "DARK") View.VISIBLE else View.GONE
        dialog.findViewById<TextView>(R.id.ivSystemCheck).visibility = if (currentMode == "SYSTEM") View.VISIBLE else View.GONE

        dialog.findViewById<View>(R.id.cardLight).setOnClickListener { viewModel.updateThemeMode("LIGHT"); dialog.dismiss() }
        dialog.findViewById<View>(R.id.cardDark).setOnClickListener { viewModel.updateThemeMode("DARK"); dialog.dismiss() }
        dialog.findViewById<View>(R.id.cardSystem).setOnClickListener { viewModel.updateThemeMode("SYSTEM"); dialog.dismiss() }
        dialog.show()
    }

    private fun showAvatarPicker() {
        val avatars = listOf("👤","😊","🧑","👨","👩","🧔","👱","🧒","🐶","🐱","🦊","🐻","🐼","🦁","🐯","🐸","🌟","🔥","💎","🌈","🚀","🎯","🍀","⚡")
        val dialog = Dialog(requireContext())
        val grid = GridView(requireContext()).apply {
            numColumns = 6; setPadding(24, 24, 24, 24)
            adapter = object : BaseAdapter() {
                override fun getCount() = avatars.size
                override fun getItem(p: Int) = avatars[p]
                override fun getItemId(p: Int) = p.toLong()
                override fun getView(p: Int, cv: View?, parent: ViewGroup) =
                    ((cv as? TextView) ?: TextView(requireContext()).apply {
                        textSize = 28f; gravity = android.view.Gravity.CENTER; setPadding(8,8,8,8)
                    }).also { (it as TextView).text = avatars[p] }
            }
            setOnItemClickListener { _, _, pos, _ -> viewModel.updateAvatar(avatars[pos]); dialog.dismiss() }
        }
        dialog.setContentView(grid)
        dialog.window?.setBackgroundDrawableResource(R.drawable.bottom_sheet_bg)
        dialog.window?.setLayout((resources.displayMetrics.widthPixels * 0.85).toInt(), ViewGroup.LayoutParams.WRAP_CONTENT)
        dialog.show()
    }

    private fun buildDialog(layoutRes: Int, widthRatio: Double = 0.90): Dialog {
        val dialog = Dialog(requireContext())
        dialog.setContentView(layoutRes)
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.window?.setLayout((resources.displayMetrics.widthPixels * widthRatio).toInt(), ViewGroup.LayoutParams.WRAP_CONTENT)
        return dialog
    }

    private fun showSnack(message: String) {
        view?.let { Snackbar.make(it, message, Snackbar.LENGTH_SHORT).show() }
    }
}
