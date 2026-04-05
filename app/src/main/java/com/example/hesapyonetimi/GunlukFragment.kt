package com.example.hesapyonetimi

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.hesapyonetimi.adapter.TransactionAdapter
import com.example.hesapyonetimi.model.TransactionModel
import com.example.hesapyonetimi.presentation.common.CurrencyFormatter
import com.example.hesapyonetimi.presentation.transactions.TransactionViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class GunlukFragment : Fragment() {

    private val viewModel: TransactionViewModel by viewModels()
    private lateinit var rvIslemler: RecyclerView

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_gunluk, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        rvIslemler = view.findViewById(R.id.rv_gunluk_islemler)
        val etTutar = view.findViewById<EditText>(R.id.et_tutar)
        val etAciklama = view.findViewById<EditText>(R.id.et_aciklama)
        val autoKategori = view.findViewById<AutoCompleteTextView>(R.id.auto_kategori)
        val btnKaydet = view.findViewById<Button>(R.id.btn_kaydet)
        val rgIslemTipi = view.findViewById<RadioGroup>(R.id.rg_islem_tipi)
        val okSimgesi = view.findViewById<ImageView>(R.id.iv_dropdown_arrow)

        rvIslemler.layoutManager = LinearLayoutManager(requireContext())

        var selectedCategoryId = 0L
        var selectedCategoryName = ""
        
        fun spinnerGuncelle(isIncome: Boolean) {
            val categories = if (isIncome) {
                viewModel.getIncomeCategories()
            } else {
                viewModel.getExpenseCategories()
            }
            
            val categoryNames = categories.map { "${it.icon} ${it.name}" }.toTypedArray()
            val sAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_list_item_1, categoryNames)
            autoKategori.setAdapter(sAdapter)
            
            if (categories.isNotEmpty()) {
                autoKategori.setText(categoryNames[0], false)
                selectedCategoryId = categories[0].id
                selectedCategoryName = categories[0].name
            }
            
            autoKategori.setOnItemClickListener { _, _, position, _ ->
                selectedCategoryId = categories[position].id
                selectedCategoryName = categories[position].name
            }
        }

        spinnerGuncelle(false)

        rgIslemTipi.setOnCheckedChangeListener { _, checkedId ->
            spinnerGuncelle(checkedId == R.id.rb_gelir)
        }

        val acilisIslemi = View.OnClickListener { autoKategori.showDropDown() }
        autoKategori.setOnClickListener(acilisIslemi)
        okSimgesi?.setOnClickListener(acilisIslemi)

        observeViewModel()

        btnKaydet.setOnClickListener {
            val tutarStr = etTutar.text.toString()
            val aciklama = etAciklama.text.toString()
            val isGelir = (rgIslemTipi.checkedRadioButtonId == R.id.rb_gelir)

            if (tutarStr.isEmpty()) {
                Toast.makeText(requireContext(), "Lütfen tutar girin!", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            
            // Sadece "Diğer" kategorisinde açıklama zorunlu
            if (selectedCategoryName.equals("Diğer", ignoreCase = true) && aciklama.isEmpty()) {
                Toast.makeText(requireContext(), "Lütfen açıklama girin (Diğer kategorisinde zorunlu)", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            
            if (selectedCategoryId == 0L) {
                Toast.makeText(requireContext(), "Lütfen kategori seçin!", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val tutar = tutarStr.toDoubleOrNull() ?: 0.0
            
            // Açıklama boşsa kategori adını kullan
            val finalAciklama = if (aciklama.isEmpty()) selectedCategoryName else aciklama
            
            viewModel.addTransaction(
                amount = tutar,
                categoryId = selectedCategoryId,
                description = finalAciklama,
                date = System.currentTimeMillis(),
                isIncome = isGelir
            )

            etTutar.text.clear()
            etAciklama.text.clear()
            
            Toast.makeText(requireContext(), "İşlem eklendi!", Toast.LENGTH_SHORT).show()
        }
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    updateUI(state)
                }
            }
        }
    }

    private fun updateUI(state: com.example.hesapyonetimi.presentation.transactions.TransactionUiState) {
        val adapterListesi = state.transactions.map { transaction ->
            TransactionModel(
                title = transaction.description,
                category = transaction.categoryName,
                amount = CurrencyFormatter.formatWithSign(transaction.amount, transaction.isIncome),
                isIncome = transaction.isIncome
            )
        }
        rvIslemler.adapter = TransactionAdapter(adapterListesi)
    }
}
