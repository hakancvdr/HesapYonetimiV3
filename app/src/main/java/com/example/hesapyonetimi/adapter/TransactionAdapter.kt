package com.example.hesapyonetimi.adapter

import android.graphics.Color
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.hesapyonetimi.databinding.ItemIslemBinding
import com.example.hesapyonetimi.model.TransactionModel

class TransactionAdapter(private val transactionList: List<TransactionModel>) :
    RecyclerView.Adapter<TransactionAdapter.TransactionViewHolder>() {

    class TransactionViewHolder(val binding: ItemIslemBinding) :
        RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TransactionViewHolder {
        val binding = ItemIslemBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return TransactionViewHolder(binding)
    }

    override fun onBindViewHolder(holder: TransactionViewHolder, position: Int) {
        val islem = transactionList[position]

        // View Binding uses camelCase for IDs (e.g., tv_islem_aciklama -> tvIslemAciklama)
        holder.binding.tvIslemAciklama.text = islem.title
        holder.binding.tvIslemKategori.text = islem.category

        // --- RENK VE TUTAR AYARI ---
        // islem.amount zaten formatlanmış geliyor ("+5.000 ₺" veya "-1.200 ₺")
        holder.binding.tvIslemTutar.text = islem.amount
        
        if (islem.isIncome) {
            holder.binding.tvIslemTutar.setTextColor(Color.parseColor("#388E3C")) // Yeşil
        } else {
            holder.binding.tvIslemTutar.setTextColor(Color.parseColor("#D32F2F")) // Kırmızı
        }

        // --- İKON SEÇİMİ (Kategoriye Göre) ---
        // Android sistem ikonlarını kullanıyoruz
        val ikonResId = when (islem.category) {
            "Market" -> android.R.drawable.ic_menu_add
            "Kira" -> android.R.drawable.ic_menu_directions
            "Fatura" -> android.R.drawable.ic_menu_agenda
            "Maaş" -> android.R.drawable.ic_menu_save
            "Eğlence" -> android.R.drawable.ic_menu_slideshow
            else -> android.R.drawable.ic_menu_help // Varsayılan ikon
        }
        holder.binding.ivIslemIkon.setImageResource(ikonResId)

        // İkonun rengini de harcama tipine göre ayarlıyoruz
        holder.binding.ivIslemIkon.setColorFilter(
            if (islem.isIncome) Color.parseColor("#388E3C") else Color.parseColor("#D32F2F")
        )
    }

    override fun getItemCount(): Int = transactionList.size
}
