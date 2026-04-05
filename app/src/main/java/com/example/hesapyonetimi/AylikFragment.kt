package com.example.hesapyonetimi

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
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
class AylikFragment : Fragment(R.layout.fragment_aylik) {

    private val viewModel: TransactionViewModel by viewModels()
    private lateinit var rv: RecyclerView
    private lateinit var tvToplamGider: TextView

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        rv = view.findViewById(R.id.rvAylikIslemler)
        tvToplamGider = view.findViewById(R.id.tv_aylik_toplam_gider)

        rv.layoutManager = LinearLayoutManager(requireContext())
        
        observeViewModel()
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
        val toplamGider = state.transactions
            .filter { !it.isIncome }
            .sumOf { it.amount }
        
        tvToplamGider.text = CurrencyFormatter.format(toplamGider)
        
        val adapterListesi = state.transactions.map { transaction ->
            TransactionModel(
                title = transaction.description,
                category = transaction.categoryName,
                amount = CurrencyFormatter.formatWithSign(transaction.amount, transaction.isIncome),
                isIncome = transaction.isIncome
            )
        }
        rv.adapter = TransactionAdapter(adapterListesi)
    }
}
