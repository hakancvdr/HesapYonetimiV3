package com.example.hesapyonetimi.adapter

import android.graphics.Color
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.hesapyonetimi.databinding.ItemIslemBinding
import com.example.hesapyonetimi.domain.model.Transaction
import com.example.hesapyonetimi.model.TransactionModel

class TransactionAdapter(
    private val transactionList: List<TransactionModel>,
    private val onItemClick: ((Transaction) -> Unit)? = null
) : RecyclerView.Adapter<TransactionAdapter.TransactionViewHolder>() {

    class TransactionViewHolder(val binding: ItemIslemBinding) :
        RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TransactionViewHolder {
        val binding = ItemIslemBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return TransactionViewHolder(binding)
    }

    override fun onBindViewHolder(holder: TransactionViewHolder, position: Int) {
        val islem = transactionList[position]

        holder.binding.tvIslemAciklama.text = islem.title
        val recurringTag = if (islem.transaction?.isRecurring == true) " 🔁" else ""
        holder.binding.tvIslemKategori.text = if (islem.time.isNotEmpty())
            "${islem.category} · ${islem.time}$recurringTag"
        else
            "${islem.category}$recurringTag"

        holder.binding.tvIslemTutar.text = islem.amount
        holder.binding.tvIslemTutar.setTextColor(
            if (islem.isIncome) Color.parseColor("#388E3C") else Color.parseColor("#D32F2F")
        )

        val ikonResId = when (islem.category) {
            "Market"   -> android.R.drawable.ic_menu_add
            "Kira"     -> android.R.drawable.ic_menu_directions
            "Faturalar"-> android.R.drawable.ic_menu_agenda
            "Maaş"     -> android.R.drawable.ic_menu_save
            "Eğlence"  -> android.R.drawable.ic_menu_slideshow
            else       -> android.R.drawable.ic_menu_help
        }
        holder.binding.ivIslemIkon.setImageResource(ikonResId)
        holder.binding.ivIslemIkon.setColorFilter(
            if (islem.isIncome) Color.parseColor("#388E3C") else Color.parseColor("#D32F2F")
        )

        // Tıklama — edit sheet aç
        if (onItemClick != null && islem.transaction != null) {
            holder.itemView.setOnClickListener { onItemClick.invoke(islem.transaction) }
        }
    }

    override fun getItemCount(): Int = transactionList.size

    fun getItem(position: Int): TransactionModel = transactionList[position]
}
