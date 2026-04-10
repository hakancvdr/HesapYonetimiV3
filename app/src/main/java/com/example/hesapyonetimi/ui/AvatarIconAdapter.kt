package com.example.hesapyonetimi.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.example.hesapyonetimi.R
import com.google.android.material.card.MaterialCardView

class AvatarIconAdapter(
    private val icons: List<String>,
    currentIcon: String,
    private val onPick: (String) -> Unit
) : RecyclerView.Adapter<AvatarIconAdapter.VH>() {

    private var selectedIndex = icons.indexOf(currentIcon).takeIf { it >= 0 } ?: 0

    inner class VH(val card: MaterialCardView, val emoji: android.widget.TextView) :
        RecyclerView.ViewHolder(card)

    override fun onCreateViewHolder(parent: ViewGroup, vt: Int): VH {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_avatar_icon, parent, false)
        val card = v as MaterialCardView
        val tv = v.findViewById<android.widget.TextView>(R.id.tvAvatarEmoji)
        return VH(card, tv)
    }

    override fun onBindViewHolder(h: VH, pos: Int) {
        val icon = icons[pos]
        h.emoji.text = icon
        val ctx = h.card.context
        val sel = pos == selectedIndex
        h.card.strokeWidth = if (sel) (2 * ctx.resources.displayMetrics.density).toInt() else (1 * ctx.resources.displayMetrics.density).toInt()
        h.card.strokeColor = ContextCompat.getColor(ctx, if (sel) R.color.green_primary else R.color.divider)
        h.card.setCardBackgroundColor(
            ContextCompat.getColor(ctx, if (sel) R.color.icon_chip_selected_fill else R.color.card_background)
        )
        h.card.setOnClickListener {
            val old = selectedIndex
            selectedIndex = pos
            notifyItemChanged(old)
            notifyItemChanged(pos)
            onPick(icon)
        }
    }

    override fun getItemCount() = icons.size
}
