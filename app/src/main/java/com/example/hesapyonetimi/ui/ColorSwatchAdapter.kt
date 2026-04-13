package com.example.hesapyonetimi.ui

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.example.hesapyonetimi.R
import com.google.android.material.card.MaterialCardView

class ColorSwatchAdapter(
    private val colors: List<String>,
    initialSelected: String,
    private val onSelected: (String) -> Unit
) : RecyclerView.Adapter<ColorSwatchAdapter.VH>() {

    private var selected: String = initialSelected

    fun getSelected(): String = selected

    fun setSelected(color: String) {
        val idxOld = colors.indexOfFirst { it.equals(selected, ignoreCase = true) }
        selected = color
        if (idxOld != -1) notifyItemChanged(idxOld)
        val idxNew = colors.indexOfFirst { it.equals(selected, ignoreCase = true) }
        if (idxNew != -1) notifyItemChanged(idxNew)
    }

    inner class VH(view: View) : RecyclerView.ViewHolder(view) {
        val card: MaterialCardView = view.findViewById(R.id.cardSwatch)
        val swatch: View = view.findViewById(R.id.viewSwatch)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_color_swatch, parent, false)
        return VH(v)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val colorHex = colors[position]
        holder.swatch.setBackgroundColor(Color.parseColor(colorHex))

        val isSel = colorHex.equals(selected, ignoreCase = true)
        val strokePx = (2 * holder.itemView.resources.displayMetrics.density).toInt().coerceAtLeast(2)
        holder.card.strokeWidth = if (isSel) strokePx else 0
        holder.card.strokeColor = ContextCompat.getColor(holder.itemView.context, R.color.green_primary)

        holder.itemView.setOnClickListener {
            if (!colorHex.equals(selected, ignoreCase = true)) {
                setSelected(colorHex)
                onSelected(colorHex)
            }
        }
    }

    override fun getItemCount(): Int = colors.size
}

