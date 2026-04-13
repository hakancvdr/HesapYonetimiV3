package com.example.hesapyonetimi.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.res.ResourcesCompat
import androidx.recyclerview.widget.RecyclerView
import com.example.hesapyonetimi.R

class EmojiGridAdapter(
    private val items: List<String>,
    private var selected: String,
    private val onSelect: (String) -> Unit
) : RecyclerView.Adapter<EmojiGridAdapter.VH>() {

    private fun looksLikeMaterialIconName(s: String): Boolean =
        s.isNotBlank() && s.all { it.isLowerCase() || it.isDigit() || it == '_' }

    inner class VH(v: View) : RecyclerView.ViewHolder(v) {
        val tv: TextView = v.findViewById(R.id.tvEmojiItem)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_emoji_grid, parent, false)
        return VH(v)
    }

    override fun getItemCount() = items.size

    override fun onBindViewHolder(h: VH, position: Int) {
        val item = items[position]
        h.tv.text = item
        val tf = if (looksLikeMaterialIconName(item))
            runCatching { ResourcesCompat.getFont(h.itemView.context, R.font.material_symbols_outlined) }.getOrNull()
        else null
        if (tf != null) h.tv.typeface = tf else h.tv.typeface = null

        h.tv.isSelected = item == selected
        h.itemView.setOnClickListener {
            selected = item
            notifyDataSetChanged()
            onSelect(item)
        }
    }
}

