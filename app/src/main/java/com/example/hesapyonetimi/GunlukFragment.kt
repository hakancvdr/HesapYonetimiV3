package com.example.hesapyonetimi

import android.app.DatePickerDialog
import android.content.Context
import android.graphics.Canvas
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.hesapyonetimi.adapter.CategoryChipAdapter
import com.example.hesapyonetimi.adapter.TransactionAdapter
import com.example.hesapyonetimi.adapter.WalletChipAdapter
import com.example.hesapyonetimi.data.local.dao.WalletDao
import javax.inject.Inject
import com.example.hesapyonetimi.domain.model.Category
import com.example.hesapyonetimi.domain.model.Transaction
import com.example.hesapyonetimi.model.TransactionModel
import com.example.hesapyonetimi.presentation.common.CurrencyFormatter
import com.example.hesapyonetimi.presentation.tags.TagViewModel
import com.example.hesapyonetimi.presentation.transactions.TransactionViewModel
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.textfield.TextInputEditText
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

@AndroidEntryPoint
class GunlukFragment : Fragment() {

    @Inject
    lateinit var walletDao: WalletDao

    private val viewModel: TransactionViewModel by viewModels()
    private val tagViewModel: TagViewModel by viewModels()
    private lateinit var categoryAdapter: CategoryChipAdapter
    private var isGider = true
    private var selectedCategory: Category? = null
    private var selectedDateMillis = System.currentTimeMillis()
    private var selectedWalletId: Long? = null
    private val selectedTagFilters = mutableSetOf<String>()
    private var isFormExpanded = false
    private var currentDayTransactions: List<Transaction> = emptyList()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_gunluk, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        view.findViewById<SwipeRefreshLayout>(R.id.swipe_refresh)?.apply {
            setColorSchemeResources(R.color.green_primary)
            setOnRefreshListener { isRefreshing = false }
        }

        val tarihFormat = SimpleDateFormat("d MMMM yyyy", Locale("tr"))
        val tarihFormatUzun = SimpleDateFormat("d MMMM yyyy, EEEE", Locale("tr"))
        val tarihFormatKisa = SimpleDateFormat("d MMMM", Locale("tr"))

        val btnGider  = view.findViewById<TextView>(R.id.btn_gider)
        val btnGelir  = view.findViewById<TextView>(R.id.btn_gelir)
        val btnKaydet = view.findViewById<com.google.android.material.button.MaterialButton>(R.id.btn_kaydet)
        val tvIslemlerBaslik = view.findViewById<TextView>(R.id.tv_islemler_baslik)
        val tvSelectedDayLabel = view.findViewById<TextView>(R.id.tv_selected_day_label)
        val formHeaderRow = view.findViewById<View>(R.id.form_header_row)
        val formContent = view.findViewById<View>(R.id.form_content)
        val ivFormExpand = view.findViewById<TextView>(R.id.iv_form_expand)

        // ── Katlanabilir form ──────────────────────────────────────────────
        fun updateFormExpanded() {
            formContent.visibility = if (isFormExpanded) View.VISIBLE else View.GONE
            ivFormExpand.text = if (isFormExpanded) "▲" else "▼"
        }
        updateFormExpanded()
        formHeaderRow.setOnClickListener {
            isFormExpanded = !isFormExpanded
            updateFormExpanded()
        }

        val chipGroupInputTags = view.findViewById<ChipGroup>(R.id.chipGroupInputTags)
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                tagViewModel.tags.collect { tagList ->
                    chipGroupInputTags.removeAllViews()
                    tagList.forEach { entity ->
                        val chip = Chip(requireContext()).apply {
                            text = entity.name
                            isCheckable = true
                            isChecked = false
                            textSize = 12f
                        }
                        chipGroupInputTags.addView(chip)
                    }
                }
            }
        }

        // Hızlı ekle (+) → formu aç
        view.findViewById<TextView>(R.id.btn_quick_add)?.setOnClickListener {
            if (!isFormExpanded) {
                isFormExpanded = true
                updateFormExpanded()
            }
            view.findViewById<View>(R.id.form_header_row)?.requestFocus()
        }

        // ── Seçili tarih etiketi (header) ─────────────────────────────────
        fun updateDayLabel() {
            val today = Calendar.getInstance()
            val sel = Calendar.getInstance().apply { timeInMillis = selectedDateMillis }
            tvSelectedDayLabel.text = when {
                sel.get(Calendar.DAY_OF_YEAR) == today.get(Calendar.DAY_OF_YEAR) &&
                sel.get(Calendar.YEAR) == today.get(Calendar.YEAR) -> "Bugün · ${tarihFormatKisa.format(Date(selectedDateMillis))}"
                else -> tarihFormatKisa.format(Date(selectedDateMillis))
            }
        }
        updateDayLabel()

        // ── Kategori chip ──────────────────────────────────────────────────
        val rvKategoriler = view.findViewById<RecyclerView>(R.id.rv_kategoriler)
        categoryAdapter = CategoryChipAdapter(emptyList()) { cat -> selectedCategory = cat }
        rvKategoriler.layoutManager = GridLayoutManager(requireContext(), 2)
        rvKategoriler.adapter = categoryAdapter

        fun updateToggle() {
            val greenColor = ContextCompat.getColor(requireContext(), R.color.green_primary)
            val dimWhite = 0x80FFFFFF.toInt()
            if (isGider) {
                btnGider.setBackgroundResource(R.drawable.toggle_pill_selected)
                btnGider.setTextColor(greenColor)
                btnGelir.background = null
                btnGelir.setTextColor(dimWhite)
                btnKaydet.text = "Gider Ekle"
            } else {
                btnGelir.setBackgroundResource(R.drawable.toggle_pill_selected)
                btnGelir.setTextColor(greenColor)
                btnGider.background = null
                btnGider.setTextColor(dimWhite)
                btnKaydet.text = "Gelir Ekle"
            }
            val cats = if (isGider) viewModel.getExpenseCategories() else viewModel.getIncomeCategories()
            categoryAdapter.setCategories(cats, if (isGider) "Market" else "Maaş")
        }

        btnGider.setOnClickListener { isGider = true; updateToggle() }
        btnGelir.setOnClickListener { isGider = false; updateToggle() }

        // ── Tarih seçici ───────────────────────────────────────────────────
        val etTarih = view.findViewById<TextView>(R.id.et_tarih)
        etTarih.text = tarihFormatUzun.format(Date(selectedDateMillis))

        val tarihClick = View.OnClickListener {
            val cal = Calendar.getInstance().apply { timeInMillis = selectedDateMillis }
            DatePickerDialog(requireContext(), { _, y, m, d ->
                val now = Calendar.getInstance()
                cal.set(y, m, d)
                cal.set(Calendar.HOUR_OF_DAY, now.get(Calendar.HOUR_OF_DAY))
                cal.set(Calendar.MINUTE, now.get(Calendar.MINUTE))
                selectedDateMillis = cal.timeInMillis
                etTarih.text = tarihFormatUzun.format(Date(selectedDateMillis))
                updateDayLabel()
                val today = Calendar.getInstance()
                val isBugün = cal.get(Calendar.DAY_OF_YEAR) == today.get(Calendar.DAY_OF_YEAR) &&
                              cal.get(Calendar.YEAR) == today.get(Calendar.YEAR)
                tvIslemlerBaslik.text = if (isBugün) "Bugünün işlemleri"
                                        else tarihFormat.format(Date(selectedDateMillis)) + " işlemleri"
                refreshIslemler(view, viewModel.uiState.value.transactions)
            }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show()
        }
        etTarih.setOnClickListener(tarihClick)
        view.findViewById<View>(R.id.tarih_row).setOnClickListener(tarihClick)

        // ── İşlem listesi ──────────────────────────────────────────────────
        val rvIslemler = view.findViewById<RecyclerView>(R.id.rv_gunluk_islemler)
        rvIslemler.layoutManager = LinearLayoutManager(requireContext())

        // Kaydet
        val etTutar    = view.findViewById<TextInputEditText>(R.id.et_tutar)
        val etAciklama = view.findViewById<TextInputEditText>(R.id.et_aciklama)

        etAciklama.setOnEditorActionListener { _, _, _ -> hideKeyboard(view); true }
        etTutar.setOnEditorActionListener    { _, _, _ -> hideKeyboard(view); true }

        btnKaydet.setOnClickListener {
            hideKeyboard(view)
            val tutarStr = etTutar.text.toString()
            if (tutarStr.isEmpty()) { toast("Lütfen tutar girin"); return@setOnClickListener }
            val cat = selectedCategory ?: run { toast("Lütfen kategori seçin"); return@setOnClickListener }
            val aciklama = etAciklama.text.toString()
            if (cat.name.equals("Diğer", ignoreCase = true) && aciklama.isEmpty()) {
                toast("Diğer kategorisinde açıklama zorunlu"); return@setOnClickListener
            }
            val tutar = tutarStr.toDoubleOrNull() ?: 0.0
            val tags = (0 until chipGroupInputTags.childCount)
                .map { chipGroupInputTags.getChildAt(it) as Chip }
                .filter { it.isChecked }
                .joinToString(",") { it.text.toString() }
            viewModel.addTransaction(
                amount = tutar,
                categoryId = cat.id,
                description = if (aciklama.isEmpty()) cat.name else aciklama,
                date = selectedDateMillis,
                isIncome = !isGider,
                walletId = selectedWalletId,
                tags = tags
            )
            etTutar.text?.clear()
            etAciklama.text?.clear()
            (0 until chipGroupInputTags.childCount).forEach { i ->
                (chipGroupInputTags.getChildAt(i) as? Chip)?.isChecked = false
            }
            // Formu kapat ve başarı mesajı göster
            isFormExpanded = false
            updateFormExpanded()
            Snackbar.make(view, "✅ İşlem eklendi!", Snackbar.LENGTH_SHORT).show()
        }

        // ── Cüzdan seçimi ──────────────────────────────────────────────────
        val rvWallets = view.findViewById<RecyclerView>(R.id.rvGunlukWallets)
        rvWallets.layoutManager = LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                walletDao.getAllWallets().collect { wallets ->
                    rvWallets.adapter = WalletChipAdapter(wallets, selectedWalletId) { wallet ->
                        selectedWalletId = wallet.id
                        rvWallets.adapter?.notifyDataSetChanged()
                    }
                    if (selectedWalletId == null) {
                        selectedWalletId = wallets.firstOrNull { it.isDefault }?.id ?: wallets.firstOrNull()?.id
                    }
                }
            }
        }

        // ── Tag filter ─────────────────────────────────────────────────────
        val chipGroupTagFilter = view.findViewById<ChipGroup>(R.id.chipGroupTagFilter)
        val tvFilterHint = view.findViewById<TextView>(R.id.tv_filter_hint)

        // ── Observe ────────────────────────────────────────────────────────
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    if (state.categories.isNotEmpty() && categoryAdapter.itemCount == 0) updateToggle()

                    val secilen = Calendar.getInstance().apply { timeInMillis = selectedDateMillis }
                    val todayTxs = state.transactions.filter { t ->
                        val cal = Calendar.getInstance().apply { timeInMillis = t.date }
                        cal.get(Calendar.DAY_OF_YEAR) == secilen.get(Calendar.DAY_OF_YEAR) &&
                        cal.get(Calendar.YEAR) == secilen.get(Calendar.YEAR)
                    }

                    // Günlük özet
                    updateDailySummary(view, todayTxs)

                    // Tag filtre chips
                    val allTags = todayTxs.flatMap { it.tags.split(",").filter { tag -> tag.isNotBlank() } }.toSet()
                    if (allTags.isEmpty()) {
                        chipGroupTagFilter.visibility = View.GONE
                        tvFilterHint.visibility = View.GONE
                    } else {
                        tvFilterHint.visibility = View.VISIBLE
                        chipGroupTagFilter.visibility = View.VISIBLE
                        chipGroupTagFilter.removeAllViews()
                        allTags.forEach { tag ->
                            val chip = Chip(requireContext()).apply {
                                text = "#$tag"
                                isCheckable = true
                                isChecked = tag in selectedTagFilters
                                setOnCheckedChangeListener { _, checked ->
                                    if (checked) selectedTagFilters.add(tag)
                                    else selectedTagFilters.remove(tag)
                                    refreshIslemler(view, state.transactions)
                                }
                            }
                            chipGroupTagFilter.addView(chip)
                        }
                    }

                    refreshIslemler(view, state.transactions)
                }
            }
        }

        // ── Swipe to delete ────────────────────────────────────────────────
        setupSwipeToDelete(view, rvIslemler)
    }

    private fun updateDailySummary(view: View, dayTransactions: List<Transaction>) {
        currentDayTransactions = dayTransactions
        val income  = dayTransactions.filter {  it.isIncome }.sumOf { it.amount }
        val expense = dayTransactions.filter { !it.isIncome }.sumOf { it.amount }
        val net = income - expense

        view.findViewById<TextView>(R.id.tv_daily_income)?.text  = "+${CurrencyFormatter.format(income)}"
        view.findViewById<TextView>(R.id.tv_daily_expense)?.text = "-${CurrencyFormatter.format(expense)}"

        val tvNet = view.findViewById<TextView>(R.id.tv_daily_net) ?: return
        tvNet.text = "= ${CurrencyFormatter.format(net)}"
        val netColor = when {
            net > 0  -> ContextCompat.getColor(requireContext(), R.color.income_green)
            net < 0  -> ContextCompat.getColor(requireContext(), R.color.expense_red)
            else     -> 0xFFFFFFFF.toInt()
        }
        tvNet.setTextColor(netColor)
    }

    private fun setupSwipeToDelete(rootView: View, rvIslemler: RecyclerView) {
        val deleteBackground = ColorDrawable(ContextCompat.getColor(requireContext(), R.color.expense_red))
        val deleteIcon = ContextCompat.getDrawable(requireContext(), android.R.drawable.ic_menu_delete)

        val callback = object : ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT) {
            override fun onMove(rv: RecyclerView, vh: RecyclerView.ViewHolder, target: RecyclerView.ViewHolder) = false

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                val adapter = rvIslemler.adapter as? TransactionAdapter ?: return
                val position = viewHolder.adapterPosition
                val deletedModel = adapter.getItem(position)
                val deletedTransaction = deletedModel.transaction ?: run {
                    adapter.notifyItemChanged(position)
                    return
                }

                viewModel.deleteTransaction(deletedTransaction)

                Snackbar.make(rootView, "İşlem silindi", Snackbar.LENGTH_LONG)
                    .setAction("Geri Al") {
                        viewModel.addTransaction(deletedTransaction.copy(id = 0L))
                    }
                    .setActionTextColor(ContextCompat.getColor(requireContext(), R.color.green_primary))
                    .show()
            }

            override fun onChildDraw(
                c: Canvas, recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder,
                dX: Float, dY: Float, actionState: Int, isCurrentlyActive: Boolean
            ) {
                val itemView = viewHolder.itemView
                val iconMargin = (itemView.height - (deleteIcon?.intrinsicHeight ?: 0)) / 2

                if (dX < 0) {
                    deleteBackground.setBounds(itemView.right + dX.toInt(), itemView.top, itemView.right, itemView.bottom)
                    deleteBackground.draw(c)
                    val iconLeft  = itemView.right - iconMargin - (deleteIcon?.intrinsicWidth ?: 0)
                    val iconRight = itemView.right - iconMargin
                    val iconTop   = itemView.top + iconMargin
                    val iconBottom = itemView.bottom - iconMargin
                    deleteIcon?.setBounds(iconLeft, iconTop, iconRight, iconBottom)
                    deleteIcon?.draw(c)
                } else if (dX > 0) {
                    deleteBackground.setBounds(itemView.left, itemView.top, itemView.left + dX.toInt(), itemView.bottom)
                    deleteBackground.draw(c)
                    val iconLeft  = itemView.left + iconMargin
                    val iconRight = itemView.left + iconMargin + (deleteIcon?.intrinsicWidth ?: 0)
                    val iconTop   = itemView.top + iconMargin
                    val iconBottom = itemView.bottom - iconMargin
                    deleteIcon?.setBounds(iconLeft, iconTop, iconRight, iconBottom)
                    deleteIcon?.draw(c)
                }
                super.onChildDraw(c, recyclerView, viewHolder, dX, dY, actionState, isCurrentlyActive)
            }
        }
        ItemTouchHelper(callback).attachToRecyclerView(rvIslemler)
    }

    private fun refreshIslemler(view: View, allTransactions: List<Transaction>) {
        val rvIslemler = view.findViewById<RecyclerView>(R.id.rv_gunluk_islemler)
        val emptyState = view.findViewById<View>(R.id.empty_state_gunluk)
        val tvIslemlerBaslik = view.findViewById<TextView>(R.id.tv_islemler_baslik)
        val tarihFormat = SimpleDateFormat("d MMMM yyyy", Locale("tr"))
        val timeFormat  = SimpleDateFormat("HH:mm", Locale.getDefault())

        val secilen = Calendar.getInstance().apply { timeInMillis = selectedDateMillis }
        val filtered = allTransactions.filter { t ->
            val cal = Calendar.getInstance().apply { timeInMillis = t.date }
            val isToday = cal.get(Calendar.DAY_OF_YEAR) == secilen.get(Calendar.DAY_OF_YEAR) &&
                          cal.get(Calendar.YEAR) == secilen.get(Calendar.YEAR)
            if (!isToday) return@filter false
            if (selectedTagFilters.isEmpty()) return@filter true
            val txTags = t.tags.split(",").filter { it.isNotBlank() }.toSet()
            selectedTagFilters.any { it in txTags }
        }

        // Başlık
        val today = Calendar.getInstance()
        val isBugün = secilen.get(Calendar.DAY_OF_YEAR) == today.get(Calendar.DAY_OF_YEAR) &&
                      secilen.get(Calendar.YEAR) == today.get(Calendar.YEAR)
        tvIslemlerBaslik?.text = if (isBugün) "Bugünün işlemleri"
                                 else tarihFormat.format(Date(selectedDateMillis)) + " işlemleri"

        if (filtered.isEmpty()) {
            emptyState.visibility = View.VISIBLE
            rvIslemler.visibility = View.GONE
        } else {
            emptyState.visibility = View.GONE
            rvIslemler.visibility = View.VISIBLE
            rvIslemler.adapter = TransactionAdapter(filtered.map { t ->
                TransactionModel(
                    id = t.id,
                    title = t.description,
                    category = t.categoryName,
                    amount = CurrencyFormatter.formatWithSign(t.amount, t.isIncome),
                    isIncome = t.isIncome,
                    time = timeFormat.format(Date(t.date)),
                    transaction = t
                )
            }) { transaction ->
                com.example.hesapyonetimi.presentation.common.EditTransactionSheet
                    .newInstance(transaction)
                    .show(childFragmentManager, "EditTransaction")
            }
        }
    }

    private fun hideKeyboard(view: View) {
        (requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager)
            .hideSoftInputFromWindow(view.windowToken, 0)
    }

    private fun toast(msg: String) = Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()
}
