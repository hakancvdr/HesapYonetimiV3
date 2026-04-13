package com.example.hesapyonetimi.adapter

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import androidx.recyclerview.widget.RecyclerView
import com.example.hesapyonetimi.R
import com.example.hesapyonetimi.databinding.ItemIslemBinding
import com.example.hesapyonetimi.domain.model.Transaction
import com.example.hesapyonetimi.model.TransactionModel
import com.example.hesapyonetimi.ui.CategoryColorPalette
import com.example.hesapyonetimi.ui.MaterialCategoryIcon

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

        val rawTitle = islem.title.trim()
        val cat = islem.category.trim()
        val useCategoryOnly = rawTitle.isEmpty() || rawTitle.equals(cat, ignoreCase = true)
        holder.binding.tvIslemAciklama.text = if (useCategoryOnly) cat else rawTitle

        val subtitle = buildString {
            if (!useCategoryOnly) {
                append(cat)
                if (islem.time.isNotEmpty()) append(" · ")
            }
            if (islem.time.isNotEmpty()) append(islem.time)
        }.trim()
        if (subtitle.isEmpty()) {
            holder.binding.tvIslemKategori.visibility = View.GONE
        } else {
            holder.binding.tvIslemKategori.visibility = View.VISIBLE
            holder.binding.tvIslemKategori.text = subtitle
        }

        val ctx = holder.itemView.context
        val incomeC = ContextCompat.getColor(ctx, R.color.income_green)
        val expenseC = ContextCompat.getColor(ctx, R.color.expense_red)
        holder.binding.tvIslemTutar.text = islem.amount
        holder.binding.tvIslemTutar.setTextColor(if (islem.isIncome) incomeC else expenseC)

        val iconName = islem.transaction?.categoryIcon?.trim()?.takeIf { it.isNotEmpty() }
            ?: categoryFallbackIconName(islem.category)
        val hex = CategoryColorPalette.closestOrDefault(islem.transaction?.categoryColor)
        val fill = runCatching { Color.parseColor(hex) }.getOrElse {
            ContextCompat.getColor(ctx, R.color.green_primary)
        }
        holder.binding.tvIslemIkon.background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(fill)
        }
        val lum = ColorUtils.calculateLuminance(fill)
        val glyph = if (lum > 0.62) Color.BLACK else Color.WHITE
        MaterialCategoryIcon.bind(holder.binding.tvIslemIkon, iconName, 20f, glyph)

        // Tıklama — edit sheet aç
        if (onItemClick != null && islem.transaction != null) {
            holder.itemView.setOnClickListener { onItemClick.invoke(islem.transaction) }
        }
    }

    override fun getItemCount(): Int = transactionList.size

    fun getItem(position: Int): TransactionModel = transactionList[position]

    private fun categoryFallbackIconName(categoryLabel: String): String {
        val c = categoryLabel.trim().lowercase()
        return when {
            "market" in c -> "shopping_cart"
            "kira" in c || "fatura" in c -> "home"
            "maaş" in c || "maas" in c -> "payments"
            "eğlence" in c || "eglence" in c || "hobi" in c -> "sports_esports"
            "ulaşım" in c || "ulasim" in c -> "directions_car"
            else -> "list_alt"
        }
    }
}
