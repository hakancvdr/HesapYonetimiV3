package com.example.hesapyonetimi.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.hesapyonetimi.R

class EmojiGridAdapter(
    private val items: List<String>,
    private var selected: String,
    private val onSelect: (String) -> Unit
) : RecyclerView.Adapter<EmojiGridAdapter.VH>() {

    inner class VH(v: View) : RecyclerView.ViewHolder(v) {
        val tv: TextView = v.findViewById(R.id.tvEmojiItem)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_emoji_grid, parent, false)
        return VH(v)
    }

    override fun getItemCount() = items.size

    override fun onBindViewHolder(h: VH, position: Int) {
        val emoji = items[position]
        h.tv.text = emoji
        h.tv.isSelected = emoji == selected
        h.itemView.setOnClickListener {
            selected = emoji
            notifyDataSetChanged()
            onSelect(emoji)
        }
    }
}

