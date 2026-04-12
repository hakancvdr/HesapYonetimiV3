package com.example.hesapyonetimi.presentation.tags

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.hesapyonetimi.R
import com.example.hesapyonetimi.data.local.entity.TagEntity

class TagRowAdapter(
    private val onDelete: (Long) -> Unit
) : RecyclerView.Adapter<TagRowAdapter.VH>() {

    private var list: List<TagEntity> = emptyList()

    fun submit(newList: List<TagEntity>) {
        list = newList
        notifyDataSetChanged()
    }

    class VH(v: View) : RecyclerView.ViewHolder(v) {
        val tvName: TextView = v.findViewById(R.id.tvTagName)
        val btnDelete: View = v.findViewById(R.id.btnDeleteTag)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_tag_row, parent, false)
        return VH(v)
    }

    override fun getItemCount() = list.size

    override fun onBindViewHolder(h: VH, position: Int) {
        val t = list[position]
        h.tvName.text = t.name
        h.btnDelete.setOnClickListener { onDelete(t.id) }
    }
}
