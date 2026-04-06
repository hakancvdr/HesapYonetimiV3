package com.example.hesapyonetimi.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.hesapyonetimi.R
import com.example.hesapyonetimi.domain.model.Category

class CategoryGridAdapter(
    private var categories: List<Category>,
    private val onSelected: (Category) -> Unit
) : RecyclerView.Adapter<CategoryGridAdapter.VH>() {

    private var selectedId: Long = -1

    fun setCategories(list: List<Category>, defaultName: String) {
        categories = list
        val default = list.firstOrNull { it.name.equals(defaultName, ignoreCase = true) } ?: list.firstOrNull()
        selectedId = default?.id ?: -1
        default?.let { onSelected(it) }
        notifyDataSetChanged()
    }

    fun getSelectedId() = selectedId

    inner class VH(parent: ViewGroup) : RecyclerView.ViewHolder(
        LayoutInflater.from(parent.context).inflate(R.layout.item_kategori_grid, parent, false)
    ) {
        val icon: TextView = itemView.findViewById(R.id.tv_cat_icon)
        val name: TextView = itemView.findViewById(R.id.tv_cat_name)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = VH(parent)

    override fun onBindViewHolder(holder: VH, position: Int) {
        val cat = categories[position]
        holder.icon.text = cat.icon
        holder.name.text = cat.name

        val isSelected = cat.id == selectedId
        holder.itemView.setBackgroundResource(
            if (isSelected) R.drawable.kategori_item_selected_bg
            else R.drawable.kategori_item_bg
        )
        holder.name.setTextColor(
            if (isSelected)
                androidx.core.content.ContextCompat.getColor(holder.itemView.context, R.color.green_primary)
            else
                androidx.core.content.ContextCompat.getColor(holder.itemView.context, R.color.text_secondary)
        )

        holder.itemView.setOnClickListener {
            val old = categories.indexOfFirst { it.id == selectedId }
            selectedId = cat.id
            onSelected(cat)
            if (old >= 0) notifyItemChanged(old)
            notifyItemChanged(position)
        }
    }

    override fun getItemCount() = categories.size
}
