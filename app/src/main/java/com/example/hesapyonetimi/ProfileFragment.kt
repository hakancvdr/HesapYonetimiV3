package com.example.hesapyonetimi

import android.app.Dialog
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.Toast
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.example.hesapyonetimi.auth.AuthPrefs
import com.example.hesapyonetimi.util.CsvExporter
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.hesapyonetimi.ui.IconPickerHelper
import com.example.hesapyonetimi.domain.model.Category
import com.example.hesapyonetimi.presentation.profile.ProfileCategoryAdapter
import com.example.hesapyonetimi.presentation.profile.ProfileUiEvent
import com.example.hesapyonetimi.presentation.profile.ProfileViewModel
import com.example.hesapyonetimi.presentation.tags.TagRowAdapter
import com.example.hesapyonetimi.presentation.tags.TagViewModel
import com.example.hesapyonetimi.ui.EmojiPickerSheet
import com.google.android.material.progressindicator.LinearProgressIndicator
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.util.*

@AndroidEntryPoint
class ProfileFragment : Fragment() {

    private val viewModel: ProfileViewModel by viewModels()
    private val tagViewModel: TagViewModel by viewModels()

    /** Kategori ekle/düzenle diyaloglarında gösterilen modern emoji seti */
    private val categoryIconPresets = listOf(
        "💳", "🏠", "🛒", "🍽️", "🚗", "✈️", "📱", "💊", "🎓", "🎮", "☕", "🎁",
        "📚", "🐾", "⚡", "🌿", "🎵", "🔧", "👔", "🏥", "🎬", "🍕", "🥤", "💡",
        "🧾", "🅿️", "🚌", "🎫", "🏦", "🔋", "☂️", "🛍️", "💼", "🧴", "🎯"
    )

    private lateinit var tvAvatar: TextView
    private lateinit var tvUserName: TextView
    private lateinit var tvMemberSince: TextView
    private lateinit var tvWeekExpense: TextView
    private lateinit var tvOpenRemindersCount: TextView
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
    private lateinit var switchPinLock: MaterialSwitch
    private lateinit var cardPinLockTimeout: View
    private lateinit var tvPinLockTimeoutSubtitle: TextView
    private lateinit var cardSecurityQ: View

    private var suppressPinSwitchCallback = false

    private val currencyFormat = NumberFormat.getCurrencyInstance(Locale("tr", "TR"))
    private var categoryDialogJob: Job? = null
    private var tagDialogJob: Job? = null

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

        setupPinLockSwitchListener()
        refreshPinSecurityVisibility()
    }

    override fun onResume() {
        super.onResume()
        refreshPinSecurityVisibility()
    }

    private fun bindViews(v: View) {
        tvAvatar = v.findViewById(R.id.tvAvatar)
        tvUserName = v.findViewById(R.id.tvUserName)
        tvMemberSince = v.findViewById(R.id.tvMemberSince)
        tvWeekExpense = v.findViewById(R.id.tvWeekExpense)
        tvOpenRemindersCount = v.findViewById(R.id.tvOpenRemindersCount)
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
        switchPinLock = v.findViewById(R.id.switchPinLock)
        cardPinLockTimeout = v.findViewById(R.id.cardPinLockTimeout)
        tvPinLockTimeoutSubtitle = v.findViewById(R.id.tvPinLockTimeoutSubtitle)
        cardSecurityQ = v.findViewById(R.id.cardSecurityQ)
    }

    private fun setupClicks(v: View) {
        v.findViewById<View>(R.id.btn_profile_close).setOnClickListener {
            findNavController().popBackStack()
        }
        v.findViewById<View>(R.id.btnEditName).setOnClickListener { showEditNameDialog() }
        v.findViewById<View>(R.id.tvUserName).setOnClickListener { showEditNameDialog() }
        v.findViewById<View>(R.id.btnEditBudget).setOnClickListener { showEditBudgetDialog() }
        v.findViewById<View>(R.id.cardCategories).setOnClickListener { showCategoryDialog() }
        v.findViewById<View>(R.id.cardProPage).setOnClickListener {
            findNavController().navigate(R.id.action_profil_to_pro)
        }
        v.findViewById<View>(R.id.cardPinLockTimeout).setOnClickListener { showPinLockTimeoutDialog() }
        v.findViewById<View>(R.id.cardChangePin).setOnClickListener { showChangePinDialog() }
        v.findViewById<View>(R.id.cardResetSettings).setOnClickListener { showResetSettingsDialog() }
        v.findViewById<View>(R.id.cardSecurityQ).setOnClickListener { showChangeSecurityQDialog() }
        v.findViewById<View>(R.id.cardWipeFinancialData).setOnClickListener { showWipeFinancialDataDialog() }

    }

    private fun observe() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {

                // Profil + bu ayki gider — bütçe progress birlikte güncellenir
                launch {
                    combine(viewModel.profile, viewModel.currentMonthExpense) { profile, expense ->
                        profile to expense
                    }.collectLatest { (profile, expense) ->
                        profile ?: return@collectLatest
                        // Avatar seçimi kaldırıldı; her yerde kullanıcı adı baş harfi gösterilecek.
                        tvAvatar.text = (profile.displayName.firstOrNull()?.uppercaseChar()?.toString() ?: "K")
                        tvUserName.text = profile.displayName
                        tvThemeSubtitle.text = when (profile.themeMode) {
                            "LIGHT" -> "Açık tema"
                            "DARK"  -> "Koyu tema"
                            else    -> "Sistem varsayılanı"
                        }

                        if (profile.monthlyBudgetLimit > 0) {
                            tvBudgetLimit.text = currencyFormat.format(profile.monthlyBudgetLimit)
                            budgetProgressContainer.visibility = View.VISIBLE
                            tvBudgetUsed.text = "Bu ay: ${currencyFormat.format(expense)}"
                            val percent = ((expense / profile.monthlyBudgetLimit) * 100)
                                .coerceIn(0.0, 100.0).toInt()
                            tvBudgetPercent.text = "%$percent"
                            budgetProgress.progress = percent
                        } else {
                            tvBudgetLimit.text = "Ayarlanmadı"
                            budgetProgressContainer.visibility = View.GONE
                        }
                    }
                }

                launch {
                    viewModel.stats.collectLatest { stats ->
                        tvTotalTransactions.text = "${stats.totalTransactions} İşlem"
                        tvCategoryCount.text = "${stats.categoryCount} Kategori"
                        tvActiveDays.text = if (stats.totalTransactions > 0) "✓ Aktif" else "Yeni"
                        val tierLabel = if (AuthPrefs.isProMember(requireContext())) {
                            getString(R.string.membership_premium)
                        } else {
                            getString(R.string.membership_free)
                        }
                        tvMemberSince.text = if (stats.memberSince.isNotBlank()) {
                            getString(R.string.profile_membership_line, tierLabel, stats.memberSince)
                        } else {
                            tierLabel
                        }
                        tvWeekExpense.text = currencyFormat.format(stats.weekExpenseTotal)
                        tvOpenRemindersCount.text = stats.openRemindersCount.toString()
                        tvCategorySubtitle.text =
                            "${stats.categoryCount} kategori · etiketler aynı diyalogda"
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
        val dialog = Dialog(requireContext())
        dialog.setContentView(R.layout.dialog_profile_categories)
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.window?.setLayout(
            (resources.displayMetrics.widthPixels * 0.95).toInt(),
            (resources.displayMetrics.heightPixels * 0.82).toInt()
        )
        dialog.findViewById<ImageButton>(R.id.btnCloseCategoriesDialog).setOnClickListener { dialog.dismiss() }

        val rv = dialog.findViewById<RecyclerView>(R.id.rvCategories)
        val fabAdd = dialog.findViewById<com.google.android.material.floatingactionbutton.FloatingActionButton>(R.id.fabAddCategory)
        val btnExpense = dialog.findViewById<TextView>(R.id.btnExpenseTab)
        val btnIncome = dialog.findViewById<TextView>(R.id.btnIncomeTab)
        val btnTags = dialog.findViewById<TextView>(R.id.btnTagsTab)
        val panelTags = dialog.findViewById<View>(R.id.panelTags)
        val rvTags = dialog.findViewById<RecyclerView>(R.id.rvProfileDialogTags)
        val etNewTag = dialog.findViewById<TextInputEditText>(R.id.etProfileDialogNewTag)
        val btnAddTag = dialog.findViewById<View>(R.id.btnProfileDialogAddTag)

        val adapter = ProfileCategoryAdapter(
            onEdit = { cat -> dialog.dismiss(); showEditCategoryDialog(cat) },
            onDelete = { cat -> viewModel.deleteCategory(cat) }
        )
        rv.layoutManager = LinearLayoutManager(requireContext())
        rv.adapter = adapter

        val tagAdapter = TagRowAdapter { id -> tagViewModel.deleteTag(id) }
        rvTags.layoutManager = LinearLayoutManager(requireContext())
        rvTags.adapter = tagAdapter

        val grn = requireContext().getColor(R.color.green_primary)
        val sec = requireContext().getColor(R.color.text_secondary)
        var mode = 0 // 0 gider, 1 gelir, 2 etiket

        fun styleTabs() {
            btnExpense.setBackgroundResource(if (mode == 0) R.drawable.kategori_item_selected_bg else R.drawable.kategori_item_bg)
            btnExpense.setTextColor(if (mode == 0) grn else sec)
            btnIncome.setBackgroundResource(if (mode == 1) R.drawable.kategori_item_selected_bg else R.drawable.kategori_item_bg)
            btnIncome.setTextColor(if (mode == 1) grn else sec)
            btnTags.setBackgroundResource(if (mode == 2) R.drawable.kategori_item_selected_bg else R.drawable.kategori_item_bg)
            btnTags.setTextColor(if (mode == 2) grn else sec)
        }

        fun applyMode(m: Int) {
            mode = m
            when (m) {
                0, 1 -> {
                    fabAdd.visibility = View.VISIBLE
                    rv.visibility = View.VISIBLE
                    panelTags.visibility = View.GONE
                    adapter.showIncome = (m == 1)
                    adapter.submitFullList(viewModel.categories.value)
                }
                2 -> {
                    fabAdd.visibility = View.GONE
                    rv.visibility = View.GONE
                    panelTags.visibility = View.VISIBLE
                }
            }
            styleTabs()
        }

        btnExpense.setOnClickListener { applyMode(0) }
        btnIncome.setOnClickListener { applyMode(1) }
        btnTags.setOnClickListener { applyMode(2) }
        btnAddTag.setOnClickListener {
            tagViewModel.addTag(etNewTag.text?.toString().orEmpty())
            etNewTag.setText("")
        }

        categoryDialogJob?.cancel()
        categoryDialogJob = viewLifecycleOwner.lifecycleScope.launch {
            viewModel.categories.collect { list ->
                if (mode != 2) adapter.submitFullList(list)
            }
        }
        tagDialogJob?.cancel()
        tagDialogJob = viewLifecycleOwner.lifecycleScope.launch {
            tagViewModel.tags.collect { tagAdapter.submit(it) }
        }
        dialog.setOnDismissListener {
            categoryDialogJob?.cancel()
            tagDialogJob?.cancel()
        }

        applyMode(0)
        fabAdd.setOnClickListener { dialog.dismiss(); showAddCategoryDialog() }
        dialog.show()
    }

    private fun showEditCategoryDialog(category: com.example.hesapyonetimi.domain.model.Category) {
        val dialog = buildDialog(R.layout.dialog_profile_edit_category, widthRatio = 0.92)
        var selectedEmoji = category.icon
        val tvSelectedEmoji = dialog.findViewById<TextView>(R.id.tvSelectedEmoji)
        tvSelectedEmoji.text = selectedEmoji
        val iconList =
            if (category.icon !in categoryIconPresets) arrayListOf(category.icon).apply { addAll(categoryIconPresets) }
            else ArrayList(categoryIconPresets)

        parentFragmentManager.setFragmentResultListener(EmojiPickerSheet.RESULT_KEY, viewLifecycleOwner) { _, b ->
            val e = b.getString(EmojiPickerSheet.BUNDLE_EMOJI) ?: return@setFragmentResultListener
            selectedEmoji = e
            tvSelectedEmoji.text = e
        }
        tvSelectedEmoji.setOnClickListener {
            EmojiPickerSheet.show(parentFragmentManager, selectedEmoji, iconList)
        }

        val etName  = dialog.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.etCategoryName)
        val tilName = dialog.findViewById<com.google.android.material.textfield.TextInputLayout>(R.id.tilCategoryName)
        etName.setText(category.name)
        etName.setSelection(etName.text?.length ?: 0)

        dialog.findViewById<View>(R.id.btnCancel).setOnClickListener { dialog.dismiss() }
        dialog.findViewById<View>(R.id.btnSave).setOnClickListener {
            val name = etName.text?.toString().orEmpty().trim()
            if (name.isBlank()) { tilName.error = "Kategori adı girin"; return@setOnClickListener }
            viewModel.updateCategory(category.copy(name = name, icon = selectedEmoji))
            dialog.dismiss()
            showCategoryDialog()
        }
        dialog.show()
    }

    private fun showAddCategoryDialog() {
        val dialog = buildDialog(R.layout.dialog_profile_add_category, widthRatio = 0.92)
        var selectedEmoji = "💳"
        val tvSelectedEmoji = dialog.findViewById<TextView>(R.id.tvSelectedEmoji)
        tvSelectedEmoji.text = selectedEmoji
        val iconList = ArrayList(categoryIconPresets)
        parentFragmentManager.setFragmentResultListener(EmojiPickerSheet.RESULT_KEY, viewLifecycleOwner) { _, b ->
            val e = b.getString(EmojiPickerSheet.BUNDLE_EMOJI) ?: return@setFragmentResultListener
            selectedEmoji = e
            tvSelectedEmoji.text = e
        }
        tvSelectedEmoji.setOnClickListener {
            EmojiPickerSheet.show(parentFragmentManager, selectedEmoji, iconList)
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

    private fun buildDialog(layoutRes: Int, widthRatio: Double = 0.90): Dialog {
        val dialog = Dialog(requireContext())
        dialog.setContentView(layoutRes)
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.window?.setLayout((resources.displayMetrics.widthPixels * widthRatio).toInt(), ViewGroup.LayoutParams.WRAP_CONTENT)
        return dialog
    }

    private fun showWalletDialog() {
        findNavController().navigate(R.id.action_profil_to_wallet)
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

    private fun setupPinLockSwitchListener() {
        switchPinLock.setOnCheckedChangeListener { _, isChecked ->
            if (suppressPinSwitchCallback) return@setOnCheckedChangeListener
            val ctx = requireContext()
            val prefs = AuthPrefs.prefs(ctx)
            if (isChecked) {
                val pin = prefs.getString("kullanici_pin", null)
                if (pin.isNullOrBlank() || pin.length != 4) {
                    suppressPinSwitchCallback = true
                    switchPinLock.isChecked = false
                    suppressPinSwitchCallback = false
                    showChangePinDialog {
                        AuthPrefs.setPinEnabled(ctx, true)
                        suppressPinSwitchCallback = true
                        switchPinLock.isChecked = true
                        suppressPinSwitchCallback = false
                        refreshPinSecurityVisibility()
                    }
                } else {
                    AuthPrefs.setPinEnabled(ctx, true)
                    cardPinLockTimeout.visibility = View.VISIBLE
                }
            } else {
                AuthPrefs.setPinEnabled(ctx, false)
                prefs.edit()
                    .remove("kullanici_pin")
                    .putBoolean("biometric_enabled", false)
                    .apply()
                cardPinLockTimeout.visibility = View.GONE
            }
        }
    }

    private fun refreshPinSecurityVisibility() {
        val ctx = requireContext()
        suppressPinSwitchCallback = true
        switchPinLock.isChecked = AuthPrefs.isPinEnabled(ctx)
        suppressPinSwitchCallback = false
        tvPinLockTimeoutSubtitle.text = AuthPrefs.labelForTimeoutMs(AuthPrefs.getPinLockTimeoutMs(ctx))
        cardPinLockTimeout.visibility =
            if (AuthPrefs.isPinEnabled(ctx)) View.VISIBLE else View.GONE
        cardSecurityQ.visibility =
            if (AuthPrefs.hasSecurityRecovery(ctx)) View.VISIBLE else View.GONE
    }

    private fun showPinLockTimeoutDialog() {
        if (!AuthPrefs.isPinEnabled(requireContext())) return
        val msChoices = AuthPrefs.PIN_TIMEOUT_CHOICES_MS
        val labels = msChoices.map { AuthPrefs.labelForTimeoutMs(it) }.toTypedArray()
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("PIN tekrar sorma süresi")
            .setItems(labels) { _, which ->
                AuthPrefs.setPinLockTimeoutMs(requireContext(), msChoices[which])
                tvPinLockTimeoutSubtitle.text = labels[which]
            }
            .show()
    }

    private fun showResetSettingsDialog() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Ayarları sıfırla")
            .setMessage("PIN kaldırılır, biyometrik kapatılır, tema sistem varsayılana ve aylık bütçe limiti sıfırlanır.")
            .setPositiveButton("Sıfırla") { _, _ -> viewModel.resetAppSettings() }
            .setNegativeButton("İptal", null)
            .show()
    }

    private fun showChangePinDialog(onPinSaved: (() -> Unit)? = null) {
        val prefs = requireContext().getSharedPreferences("HesapPrefs", android.content.Context.MODE_PRIVATE)
        val currentPin = prefs.getString("kullanici_pin", null)

        val dialogView = LayoutInflater.from(requireContext())
            .inflate(R.layout.dialog_change_pin, null)
        val etCurrent = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.etCurrentPin)
        val tilCurrent = dialogView.findViewById<com.google.android.material.textfield.TextInputLayout>(R.id.tilCurrentPin)
        val etNew = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.etNewPin)
        val tilNew = dialogView.findViewById<com.google.android.material.textfield.TextInputLayout>(R.id.tilNewPin)
        val etConfirm = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.etConfirmPin)
        val tilConfirm = dialogView.findViewById<com.google.android.material.textfield.TextInputLayout>(R.id.tilConfirmPin)

        if (currentPin == null) tilCurrent.visibility = View.GONE

        val dialog = buildDialog(R.layout.dialog_change_pin)
        dialog.setContentView(dialogView)
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.window?.setLayout((resources.displayMetrics.widthPixels * 0.90).toInt(), ViewGroup.LayoutParams.WRAP_CONTENT)

        dialogView.findViewById<View>(R.id.btnCancel)?.setOnClickListener { dialog.dismiss() }
        dialogView.findViewById<View>(R.id.btnSavePin)?.setOnClickListener {
            val current = etCurrent.text?.toString()?.trim() ?: ""
            val new1 = etNew.text?.toString()?.trim() ?: ""
            val new2 = etConfirm.text?.toString()?.trim() ?: ""

            if (currentPin != null && current != currentPin) {
                tilCurrent.error = "Mevcut PIN yanlış"; return@setOnClickListener
            }
            if (new1.length != 4 || new1.any { !it.isDigit() }) {
                tilNew.error = "PIN 4 rakam olmalı"; return@setOnClickListener
            }
            if (new1 != new2) {
                tilConfirm.error = "PIN'ler eşleşmiyor"; return@setOnClickListener
            }
            prefs.edit()
                .putString("kullanici_pin", new1)
                .putBoolean("pin_enabled", true)
                .apply()
            showSnack("✅ PIN güncellendi")
            dialog.dismiss()
            onPinSaved?.invoke()
        }
        dialog.show()
    }

    // ── Güvenlik sorusu değiştirme ─────────────────────────────────────────────
    private fun showChangeSecurityQDialog() {
        val prefs = requireContext().getSharedPreferences("HesapPrefs", android.content.Context.MODE_PRIVATE)
        val sorular = listOf(
            "İlk evcil hayvanınızın adı nedir?",
            "Annenizin kız soyadı nedir?",
            "Doğduğunuz şehir neresidir?",
            "En sevdiğiniz film nedir?"
        )
        val currentIdx = prefs.getInt("security_question_index", 0)

        val dialogView = LayoutInflater.from(requireContext())
            .inflate(R.layout.dialog_change_security_q, null)

        // Soru butonları
        val qButtons = listOf(
            dialogView.findViewById<TextView>(R.id.btnQ0),
            dialogView.findViewById<TextView>(R.id.btnQ1),
            dialogView.findViewById<TextView>(R.id.btnQ2),
            dialogView.findViewById<TextView>(R.id.btnQ3)
        )
        var selectedIdx = currentIdx
        fun refreshQButtons() {
            qButtons.forEachIndexed { i, btn ->
                btn.setBackgroundResource(if (i == selectedIdx) R.drawable.kategori_item_selected_bg else R.drawable.kategori_item_bg)
                btn.setTextColor(resources.getColor(if (i == selectedIdx) R.color.green_primary else R.color.text_secondary, null))
            }
        }
        qButtons.forEachIndexed { i, btn ->
            btn.text = sorular[i]
            btn.setOnClickListener { selectedIdx = i; refreshQButtons() }
        }
        refreshQButtons()

        val etAnswer = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.etNewSecurityAnswer)
        val tilAnswer = dialogView.findViewById<com.google.android.material.textfield.TextInputLayout>(R.id.tilNewSecurityAnswer)

        val dialog = buildDialog(R.layout.dialog_change_security_q)
        dialog.setContentView(dialogView)
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.window?.setLayout((resources.displayMetrics.widthPixels * 0.92).toInt(), ViewGroup.LayoutParams.WRAP_CONTENT)

        dialogView.findViewById<View>(R.id.btnCancelSecQ)?.setOnClickListener { dialog.dismiss() }
        dialogView.findViewById<View>(R.id.btnSaveSecQ)?.setOnClickListener {
            val answer = etAnswer.text?.toString()?.trim() ?: ""
            if (answer.isBlank()) { tilAnswer.error = "Cevap boş olamaz"; return@setOnClickListener }
            prefs.edit()
                .putInt("security_question_index", selectedIdx)
                .putString("security_answer", answer.lowercase())
                .apply()
            showSnack("✅ Güvenlik sorusu güncellendi")
            dialog.dismiss()
        }
        dialog.show()
    }

    private fun exportCsv() {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val txList = viewModel.getAllTransactionsOnce()
                if (txList.isEmpty()) { showSnack("Dışa aktarılacak işlem yok"); return@launch }
                val intent = CsvExporter.export(requireContext(), txList)
                startActivity(android.content.Intent.createChooser(intent, "CSV olarak paylaş"))
            } catch (e: Exception) {
                showSnack("Dışa aktarma hatası: ${e.message}")
            }
        }
    }

    private fun showSnack(message: String) {
        view?.let { Snackbar.make(it, message, Snackbar.LENGTH_SHORT).show() }
    }
}
