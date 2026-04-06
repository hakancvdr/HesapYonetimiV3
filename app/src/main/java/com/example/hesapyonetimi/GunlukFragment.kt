package com.example.hesapyonetimi

import android.app.DatePickerDialog
import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.TextView
import android.widget.Toast
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.hesapyonetimi.adapter.CategoryChipAdapter
import com.example.hesapyonetimi.adapter.TransactionAdapter
import com.example.hesapyonetimi.domain.model.Category
import com.example.hesapyonetimi.model.TransactionModel
import com.example.hesapyonetimi.presentation.common.CurrencyFormatter
import com.example.hesapyonetimi.presentation.transactions.TransactionViewModel
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

@AndroidEntryPoint
class GunlukFragment : Fragment() {

    private val viewModel: TransactionViewModel by viewModels()
    private lateinit var categoryAdapter: CategoryChipAdapter
    private var isGider = true
    private var selectedCategory: Category? = null
    private var selectedDateMillis = System.currentTimeMillis()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_gunluk, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Pull-to-refresh
        view.findViewById<SwipeRefreshLayout>(R.id.swipe_refresh)?.apply {
            setColorSchemeResources(R.color.green_primary)
            setOnRefreshListener { isRefreshing = false }
        }

        // Status bar inset
        ViewCompat.setOnApplyWindowInsetsListener(view.findViewById(R.id.gunluk_header)) { v, insets ->
            val sb = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top
            val dp = { n: Int -> (n * resources.displayMetrics.density).toInt() }
            v.setPadding(dp(20), sb + dp(12), dp(20), dp(16))
            insets
        }

        val tarihFormat   = SimpleDateFormat("d MMMM yyyy", Locale("tr"))
        val tarihFormatUzun = SimpleDateFormat("d MMMM yyyy, EEEE", Locale("tr"))

        // Header tarih
        // tarih, et_tarih içinde gösteriliyor

        val btnGider  = view.findViewById<TextView>(R.id.btn_gider)
        val btnGelir  = view.findViewById<TextView>(R.id.btn_gelir)
        val btnKaydet = view.findViewById<com.google.android.material.button.MaterialButton>(R.id.btn_kaydet)
        val tvIslemlerBaslik = view.findViewById<TextView>(R.id.tv_islemler_baslik)

        // Kategori chip
        val rvKategoriler = view.findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.rv_kategoriler)
        categoryAdapter = CategoryChipAdapter(emptyList()) { cat -> selectedCategory = cat }
        rvKategoriler.layoutManager = GridLayoutManager(requireContext(), 2)
        rvKategoriler.adapter = categoryAdapter

        fun updateToggle() {
            val greenColor = androidx.core.content.ContextCompat.getColor(requireContext(), R.color.green_primary)
            val whiteColor = 0xFFFFFFFF.toInt()
            val dimWhite   = 0x80FFFFFF.toInt()

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

        // Tarih seçici
        val etTarih = view.findViewById<TextView>(R.id.et_tarih)
        etTarih.text = tarihFormatUzun.format(Date(selectedDateMillis))

        val tarihClick = View.OnClickListener {
            val cal = Calendar.getInstance().apply { timeInMillis = selectedDateMillis }
            DatePickerDialog(requireContext(), { _, y, m, d ->
                val now = Calendar.getInstance()
                cal.set(y, m, d)
                cal.set(Calendar.HOUR_OF_DAY, now.get(Calendar.HOUR_OF_DAY))
                cal.set(Calendar.MINUTE, now.get(Calendar.MINUTE))
                cal.set(Calendar.SECOND, now.get(Calendar.SECOND))
                selectedDateMillis = cal.timeInMillis
                etTarih.text = tarihFormatUzun.format(Date(selectedDateMillis))

                // Başlığı güncelle
                val today = Calendar.getInstance()
                val isBugün = cal.get(Calendar.DAY_OF_YEAR) == today.get(Calendar.DAY_OF_YEAR) &&
                              cal.get(Calendar.YEAR) == today.get(Calendar.YEAR)
                tvIslemlerBaslik.text = if (isBugün) "Bugünün işlemleri"
                                        else tarihFormat.format(Date(selectedDateMillis)) + " işlemleri"

                // Listeyi yenile
                refreshIslemler(view, viewModel.uiState.value.transactions)
            }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show()
        }
        etTarih.setOnClickListener(tarihClick)
        view.findViewById<View>(R.id.tarih_row).setOnClickListener(tarihClick)

        // İşlem listesi
        val rvIslemler   = view.findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.rv_gunluk_islemler)
        val emptyState   = view.findViewById<View>(R.id.empty_state_gunluk)
        rvIslemler.layoutManager = LinearLayoutManager(requireContext())

        // Kaydet
        val etTutar    = view.findViewById<TextInputEditText>(R.id.et_tutar)
        val etAciklama = view.findViewById<TextInputEditText>(R.id.et_aciklama)

        etAciklama.setOnEditorActionListener { _, _, _ ->
            hideKeyboard(view); true
        }

        etTutar.setOnEditorActionListener { _, _, _ ->
            hideKeyboard(view); true
        }

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
            viewModel.addTransaction(
                amount = tutar,
                categoryId = cat.id,
                description = if (aciklama.isEmpty()) cat.name else aciklama,
                date = selectedDateMillis,
                isIncome = !isGider
            )
            etTutar.text?.clear()
            etAciklama.text?.clear()
            toast("✅ İşlem eklendi!")
        }

        // Observe
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    if (state.categories.isNotEmpty() && categoryAdapter.itemCount == 0) updateToggle()
                    refreshIslemler(view, state.transactions)
                }
            }
        }
    }

    private fun refreshIslemler(view: View, allTransactions: List<com.example.hesapyonetimi.domain.model.Transaction>) {
        val rvIslemler = view.findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.rv_gunluk_islemler)
        val emptyState = view.findViewById<View>(R.id.empty_state_gunluk)
        val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())

        val secilen = Calendar.getInstance().apply { timeInMillis = selectedDateMillis }
        val filtered = allTransactions.filter { t ->
            val cal = Calendar.getInstance().apply { timeInMillis = t.date }
            cal.get(Calendar.DAY_OF_YEAR) == secilen.get(Calendar.DAY_OF_YEAR) &&
            cal.get(Calendar.YEAR) == secilen.get(Calendar.YEAR)
        }

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
