package com.example.hesapyonetimi.presentation.aylik

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.os.BundleCompat
import androidx.core.os.bundleOf
import androidx.fragment.app.FragmentManager
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.hesapyonetimi.R
import com.example.hesapyonetimi.adapter.TransactionAdapter
import com.example.hesapyonetimi.model.TransactionModel
import com.example.hesapyonetimi.presentation.common.CurrencyFormatter
import com.example.hesapyonetimi.presentation.common.EditTransactionSheet
import com.example.hesapyonetimi.ui.MaterialCategoryIcon
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.button.MaterialButton
import java.text.SimpleDateFormat
import java.util.*

class KategoriIslemleriSheet : BottomSheetDialogFragment() {

    companion object {
        private const val ARG_KAT = "kat"
        private const val ARG_AY_OFFSET = "ayOffset"

        fun show(manager: FragmentManager, kat: KategoriOzet, ayOffset: Int) {
            KategoriIslemleriSheet().apply {
                arguments = bundleOf(ARG_KAT to kat, ARG_AY_OFFSET to ayOffset)
            }.show(manager, "KategoriIslemleri")
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
        inflater.inflate(R.layout.bottom_sheet_kategori_islemleri, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val kat = BundleCompat.getParcelable(requireArguments(), ARG_KAT, KategoriOzet::class.java) ?: run {
            dismiss(); return
        }
        val ayOffset = requireArguments().getInt(ARG_AY_OFFSET, 0)
        val timeFmt = SimpleDateFormat("HH:mm", Locale.getDefault())

        MaterialCategoryIcon.bind(
            view.findViewById(R.id.tvKategoriSheetIcon),
            kat.icon.ifBlank { "list_alt" },
            18f
        )
        view.findViewById<TextView>(R.id.tvKategoriSheetName).text = kat.ad
        view.findViewById<TextView>(R.id.tvKategoriSheetSubtitle).text =
            "${kat.islemSayisi} işlem · ${CurrencyFormatter.format(kat.toplam)}"

        val models = kat.islemler.map { t ->
            TransactionModel(
                id = t.id,
                title = t.description.ifBlank { t.categoryName },
                category = t.categoryName,
                amount = CurrencyFormatter.formatWithSign(t.amount, t.isIncome),
                isIncome = t.isIncome,
                time = timeFmt.format(Date(t.date)),
                transaction = t
            )
        }

        val rv = view.findViewById<RecyclerView>(R.id.rvKategoriIslemler)
        rv.layoutManager = LinearLayoutManager(requireContext())
        rv.adapter = TransactionAdapter(models) { tx ->
            EditTransactionSheet.newInstance(tx).show(childFragmentManager, "EditTxKat")
        }

        view.findViewById<MaterialButton>(R.id.btnKategoriTamAnaliz).setOnClickListener {
            dismiss()
            runCatching {
                requireParentFragment().findNavController().navigate(
                    R.id.action_aylik_to_kategoriDetay,
                    bundleOf("kat" to kat, "ayOffset" to ayOffset)
                )
            }
        }
    }
}
