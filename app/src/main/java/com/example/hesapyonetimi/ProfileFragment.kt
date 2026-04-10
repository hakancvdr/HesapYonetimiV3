package com.example.hesapyonetimi

import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.Toast
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.example.hesapyonetimi.BuildConfig
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
import com.example.hesapyonetimi.presentation.tags.TagManageDialog
import com.example.hesapyonetimi.ui.EmojiPickerSheet
import com.google.android.material.progressindicator.LinearProgressIndicator
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.util.*

private enum class ProFeature { WALLET, BIOMETRIC, EXPORT, CURRENCY }

@AndroidEntryPoint
class ProfileFragment : Fragment() {

    private val viewModel: ProfileViewModel by viewModels()

    /** Kategori ekle/düzenle diyaloglarında gösterilen modern emoji seti */
    private val categoryIconPresets = listOf(
        "💳", "🏠", "🛒", "🍽️", "🚗", "✈️", "📱", "💊", "🎓", "🎮", "☕", "🎁",
        "📚", "🐾", "⚡", "🌿", "🎵", "🔧", "👔", "🏥", "🎬", "🍕", "🥤", "💡",
        "🧾", "🅿️", "🚌", "🎫", "🏦", "🔋", "☂️", "🛍️", "💼", "🧴", "🎯"
    )

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
    private var categoryDialogJob: Job? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_profile, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        bindViews(view)
        setupClicks(view)
        observe()

        tvAppVersion.text = "v${BuildConfig.VERSION_NAME}"
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
        v.findViewById<View>(R.id.btnEditName).setOnClickListener { showEditNameDialog() }
        v.findViewById<View>(R.id.tvUserName).setOnClickListener { showEditNameDialog() }
        v.findViewById<View>(R.id.btnEditBudget).setOnClickListener { showEditBudgetDialog() }
        v.findViewById<View>(R.id.cardCategories).setOnClickListener { showCategoryDialog() }
        v.findViewById<View>(R.id.cardTags).setOnClickListener {
            TagManageDialog().show(childFragmentManager, "TagManageDialog")
        }
        v.findViewById<View>(R.id.cardProPage).setOnClickListener {
            findNavController().navigate(R.id.proFragment)
        }
        v.findViewById<View>(R.id.cardWallets).setOnClickListener { showProDialog(ProFeature.WALLET) }
        v.findViewById<View>(R.id.cardBiometric).setOnClickListener { showProDialog(ProFeature.BIOMETRIC) }
        v.findViewById<View>(R.id.cardChangePin).setOnClickListener { showChangePinDialog() }
        v.findViewById<View>(R.id.cardResetSettings).setOnClickListener { showResetSettingsDialog() }
        v.findViewById<View>(R.id.cardSecurityQ).setOnClickListener { showChangeSecurityQDialog() }
        v.findViewById<View>(R.id.cardTheme).setOnClickListener { showThemeDialog() }
        v.findViewById<View>(R.id.cardExportCsv).setOnClickListener { showProDialog(ProFeature.EXPORT) }
        v.findViewById<View>(R.id.cardCurrency).setOnClickListener { showProDialog(ProFeature.CURRENCY) }
        v.findViewById<View>(R.id.cardWipeFinancialData).setOnClickListener { showWipeFinancialDataDialog() }
        // Para birimi altyazısını güncelle
        updateCurrencySubtitle(v)
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
                        // Ay özeti alt satırında "Üye: Tarih" yeterli; activeDays buraya gelmez
                        tvActiveDays.text = if (stats.totalTransactions > 0) "✓ Aktif" else "Yeni"
                        tvMemberSince.text = "Üye: ${stats.memberSince}"
                        tvCategorySubtitle.text = "${stats.categoryCount} kategori"
                    }
                }

                // Bu ay gelir/gider/net
                launch {
                    kotlinx.coroutines.flow.combine(
                        viewModel.currentMonthIncome,
                        viewModel.currentMonthExpense
                    ) { income, expense -> income to expense }.collectLatest { (income, expense) ->
                        view?.findViewById<TextView>(R.id.tvMonthIncome)?.text = currencyFormat.format(income)
                        view?.findViewById<TextView>(R.id.tvMonthExpense)?.text = currencyFormat.format(expense)
                        val net = income - expense
                        val tvNet = view?.findViewById<TextView>(R.id.tvMonthNet)
                        tvNet?.text = currencyFormat.format(net)
                        tvNet?.setTextColor(resources.getColor(if (net >= 0) R.color.income_green else R.color.expense_red, null))
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

        val rv         = dialog.findViewById<RecyclerView>(R.id.rvCategories)
        val fabAdd     = dialog.findViewById<com.google.android.material.floatingactionbutton.FloatingActionButton>(R.id.fabAddCategory)
        val btnExpense = dialog.findViewById<TextView>(R.id.btnExpenseTab)
        val btnIncome  = dialog.findViewById<TextView>(R.id.btnIncomeTab)

        val adapter = ProfileCategoryAdapter(
            onEdit   = { cat -> dialog.dismiss(); showEditCategoryDialog(cat) },
            onDelete = { cat -> viewModel.deleteCategory(cat) }
        )
        rv.layoutManager = LinearLayoutManager(requireContext())
        rv.adapter = adapter

        fun selectTab(isIncome: Boolean) {
            adapter.showIncome = isIncome
            if (isIncome) {
                btnIncome.setBackgroundResource(R.drawable.kategori_item_selected_bg)
                btnIncome.setTextColor(requireContext().getColor(R.color.green_primary))
                btnExpense.setBackgroundResource(R.drawable.kategori_item_bg)
                btnExpense.setTextColor(requireContext().getColor(R.color.text_secondary))
            } else {
                btnExpense.setBackgroundResource(R.drawable.kategori_item_selected_bg)
                btnExpense.setTextColor(requireContext().getColor(R.color.green_primary))
                btnIncome.setBackgroundResource(R.drawable.kategori_item_bg)
                btnIncome.setTextColor(requireContext().getColor(R.color.text_secondary))
            }
        }
        btnExpense.setOnClickListener { selectTab(false) }
        btnIncome.setOnClickListener  { selectTab(true) }

        // Anında mevcut veriyi göster (empty bug fix: isShowing kontrolü yok)
        adapter.submitFullList(viewModel.categories.value)

        categoryDialogJob?.cancel()
        categoryDialogJob = viewLifecycleOwner.lifecycleScope.launch {
            viewModel.categories.collect { list -> adapter.submitFullList(list) }
        }
        dialog.setOnDismissListener { categoryDialogJob?.cancel() }

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

    private fun showProDialog(feature: ProFeature) {
        val (title, msg) = when (feature) {
            ProFeature.WALLET ->
                "Cüzdan (Pro)" to "Birden fazla banka ve nakit hesabını ayrı ayrı takip etmek Pro sürümünde sunulacak."
            ProFeature.BIOMETRIC ->
                "Gelişmiş güvenlik (Pro)" to "Ek güvenlik katmanları ve yedekleme seçenekleri Pro ile genişletilecek."
            ProFeature.EXPORT ->
                "CSV dışa aktarma (Pro)" to "İşlemlerinizi Excel uyumlu dosya olarak dışa aktarmak Pro özelliği olacak."
            ProFeature.CURRENCY ->
                "Para birimi (Pro)" to "Çoklu para birimi ve kur takibi Pro sürümünde planlanıyor."
        }
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("⭐ $title")
            .setMessage(msg)
            .setPositiveButton("Tamam", null)
            .show()
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

    private fun showResetSettingsDialog() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Ayarları sıfırla")
            .setMessage("PIN kaldırılır, biyometrik kapatılır, tema sistem varsayılana ve aylık bütçe limiti sıfırlanır.")
            .setPositiveButton("Sıfırla") { _, _ -> viewModel.resetAppSettings() }
            .setNegativeButton("İptal", null)
            .show()
    }

    // ── PIN değiştirme ────────────────────────────────────────────────────────
    private fun setupBiometricSwitch(v: View) {
        val prefs = requireContext().getSharedPreferences("HesapPrefs", android.content.Context.MODE_PRIVATE)
        val sw = v.findViewById<androidx.appcompat.widget.SwitchCompat>(R.id.switchBiometric) ?: return
        val tvSub = v.findViewById<TextView>(R.id.tvBiometricSubtitle)

        // Cihaz biyometrik destekliyor mu?
        val bioMgr = androidx.biometric.BiometricManager.from(requireContext())
        val supported = bioMgr.canAuthenticate(
            androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG or
            androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
        ) == androidx.biometric.BiometricManager.BIOMETRIC_SUCCESS

        if (!supported) {
            sw.isEnabled = false
            tvSub?.text = "Cihaz biyometrik desteklemiyor"
            return
        }

        sw.isChecked = prefs.getBoolean("biometric_enabled", false)
        tvSub?.text = if (sw.isChecked) "Aktif — parmak izi / yüz tanıma" else "Parmak izi / yüz ile giriş"

        sw.setOnCheckedChangeListener { _, checked ->
            prefs.edit().putBoolean("biometric_enabled", checked).apply()
            tvSub?.text = if (checked) "Aktif — parmak izi / yüz tanıma" else "Parmak izi / yüz ile giriş"
            if (checked) showSnack("✅ Biyometrik giriş aktifleştirildi")
        }
    }

    private fun showChangePinDialog() {
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
            prefs.edit().putString("kullanici_pin", new1).apply()
            showSnack("✅ PIN güncellendi")
            dialog.dismiss()
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

    private fun updateCurrencySubtitle(v: View) {
        val prefs = requireContext().getSharedPreferences("HesapPrefs", android.content.Context.MODE_PRIVATE)
        val code = prefs.getString("currency_code", "TRY") ?: "TRY"
        val sym = com.example.hesapyonetimi.presentation.common.CurrencyFormatter.currencies[code]?.symbol ?: "₺"
        v.findViewById<TextView>(R.id.tvCurrencySubtitle)?.text = "$code ($sym)"
    }

    private fun showCurrencyDialog() {
        val dialog = Dialog(requireContext())
        dialog.setContentView(R.layout.dialog_currency)
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.window?.setLayout((resources.displayMetrics.widthPixels * 0.88).toInt(), ViewGroup.LayoutParams.WRAP_CONTENT)

        val rv = dialog.findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.rvCurrencies)
        rv.layoutManager = LinearLayoutManager(requireContext())

        val prefs = requireContext().getSharedPreferences("HesapPrefs", android.content.Context.MODE_PRIVATE)
        var selected = prefs.getString("currency_code", "TRY") ?: "TRY"
        val entries = com.example.hesapyonetimi.presentation.common.CurrencyFormatter.currencies.entries.toList()

        val currencyNames = mapOf(
            "TRY" to "Türk Lirası", "USD" to "US Dollar", "EUR" to "Euro",
            "GBP" to "British Pound", "JPY" to "Japanese Yen", "BTC" to "Bitcoin",
            "ETH" to "Ethereum", "CHF" to "Swiss Franc", "CAD" to "Canadian Dollar", "AUD" to "Australian Dollar"
        )

        rv.adapter = object : androidx.recyclerview.widget.RecyclerView.Adapter<androidx.recyclerview.widget.RecyclerView.ViewHolder>() {
            inner class VH(v: View) : androidx.recyclerview.widget.RecyclerView.ViewHolder(v)
            override fun getItemCount() = entries.size
            override fun onCreateViewHolder(parent: ViewGroup, vt: Int): VH {
                val row = LayoutInflater.from(parent.context).inflate(R.layout.item_currency_row, parent, false)
                return VH(row)
            }
            override fun onBindViewHolder(h: androidx.recyclerview.widget.RecyclerView.ViewHolder, pos: Int) {
                val (code, def) = entries[pos]
                h.itemView.findViewById<TextView>(R.id.tvCurrencyCode)?.text = "${def.symbol}  $code"
                h.itemView.findViewById<TextView>(R.id.tvCurrencyName)?.text = currencyNames[code] ?: code
                val isSelected = code == selected
                h.itemView.setBackgroundResource(if (isSelected) R.drawable.kategori_item_selected_bg else android.R.color.transparent)
                h.itemView.setOnClickListener {
                    selected = code
                    com.example.hesapyonetimi.presentation.common.CurrencyFormatter.setCode(requireContext(), code)
                    notifyDataSetChanged()
                    dialog.dismiss()
                    requireActivity().recreate()
                }
            }
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
