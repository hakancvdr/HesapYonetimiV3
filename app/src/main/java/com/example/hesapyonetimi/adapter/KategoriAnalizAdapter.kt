package com.example.hesapyonetimi.adapter

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import androidx.recyclerview.widget.RecyclerView
import com.example.hesapyonetimi.R
import com.example.hesapyonetimi.presentation.aylik.KategoriOzet
import com.example.hesapyonetimi.presentation.common.CurrencyFormatter
import com.example.hesapyonetimi.ui.CategoryColorPalette
import com.example.hesapyonetimi.ui.MaterialCategoryIcon

class KategoriAnalizAdapter(
    private val kategoriler: List<KategoriOzet>,
    private val onClick: (KategoriOzet) -> Unit
) : RecyclerView.Adapter<KategoriAnalizAdapter.VH>() {

    inner class VH(view: View) : RecyclerView.ViewHolder(view) {
        val icon: TextView = view.findViewById(R.id.tv_cat_icon)
        val name: TextView = view.findViewById(R.id.tv_cat_name)
        val amount: TextView = view.findViewById(R.id.tv_cat_amount)
        val pct: TextView = view.findViewById(R.id.tv_cat_pct)
        val bar: View = view.findViewById(R.id.progress_bar)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_kategori_analiz, parent, false)
        return VH(view)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val kat = kategoriler[position]
        val ctx = holder.itemView.context
        val hex = CategoryColorPalette.closestOrDefault(kat.color)
        val fill = runCatching { Color.parseColor(hex) }.getOrElse {
            ContextCompat.getColor(ctx, R.color.green_primary)
        }
        holder.icon.background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(fill)
        }
        val lum = ColorUtils.calculateLuminance(fill)
        val glyph = if (lum > 0.62) Color.BLACK else Color.WHITE
        MaterialCategoryIcon.bind(holder.icon, kat.icon, 20f, glyph)
        holder.name.text = kat.ad
        val amountStr = CurrencyFormatter.format(kat.toplam)
        holder.amount.text = if (kat.isIncome) "+$amountStr" else amountStr
        val amountColor = if (kat.isIncome)
            androidx.core.content.ContextCompat.getColor(holder.itemView.context, R.color.income_green)
        else
            androidx.core.content.ContextCompat.getColor(holder.itemView.context, R.color.expense_red)
        holder.amount.setTextColor(amountColor)

        val barColor = if (kat.isIncome)
            androidx.core.content.ContextCompat.getColor(holder.itemView.context, R.color.income_green)
        else
            androidx.core.content.ContextCompat.getColor(holder.itemView.context, R.color.green_primary)
        holder.bar.setBackgroundColor(barColor)

        val deltaLine = when {
            kat.degisimYuzde > 0f -> "Geçen aya göre ▲${kat.degisimYuzde.toInt()}%"
            kat.degisimYuzde < 0f -> "Geçen aya göre ▼${(-kat.degisimYuzde).toInt()}%"
            else -> null
        }
        holder.pct.text = listOfNotNull(
            "%${kat.yuzde.toInt()} · ${kat.islemSayisi} işlem",
            deltaLine
        ).joinToString("\n")

        holder.bar.post {
            val parent = holder.bar.parent as View
            val width = (parent.width * kat.yuzde / 100).toInt()
            holder.bar.layoutParams = holder.bar.layoutParams.also { it.width = width }
            holder.bar.requestLayout()
        }

        holder.itemView.setOnClickListener { onClick(kat) }
    }

    override fun getItemCount() = kategoriler.size
}
