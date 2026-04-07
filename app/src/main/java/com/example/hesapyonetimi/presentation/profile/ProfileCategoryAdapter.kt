package com.example.hesapyonetimi.presentation.profile

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.hesapyonetimi.R
import com.example.hesapyonetimi.domain.model.Category

class ProfileCategoryAdapter(
    private val onDelete: (Category) -> Unit
) : ListAdapter<Category, ProfileCategoryAdapter.ViewHolder>(DiffCallback()) {

    var filterType: String = "ALL" // ALL, INCOME, EXPENSE
        set(value) {
            field = value
            submitFiltered(fullList)
        }

    private var fullList: List<Category> = emptyList()

    fun submitFullList(list: List<Category>) {
        fullList = list
        submitFiltered(list)
    }

    private fun submitFiltered(list: List<Category>) {
        val filtered = when (filterType) {
            "INCOME" -> list.filter { it.isIncome }
            "EXPENSE" -> list.filter { !it.isIncome }
            else -> list
        }
        submitList(filtered)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_profile_category, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val vColorDot: View = itemView.findViewById(R.id.vColorDot)
        private val tvEmoji: TextView = itemView.findViewById(R.id.tvEmoji)
        private val tvName: TextView = itemView.findViewById(R.id.tvCategoryName)
        private val tvType: TextView = itemView.findViewById(R.id.tvCategoryType)
        private val tvDefault: TextView = itemView.findViewById(R.id.tvDefaultBadge)
        private val btnDelete: ImageButton = itemView.findViewById(R.id.btnDeleteCategory)

        fun bind(category: Category) {
            tvEmoji.text = category.icon
            tvName.text = category.name
            tvType.text = if (category.isIncome) "Gelir" else "Gider"

            try {
                val bg = vColorDot.background.mutate()
                bg.setTint(Color.parseColor(category.color))
                vColorDot.background = bg
            } catch (_: Exception) {}

            tvDefault.visibility = if (category.isDefault) View.VISIBLE else View.GONE
            btnDelete.visibility = if (category.isDefault) View.INVISIBLE else View.VISIBLE
            btnDelete.setOnClickListener { onDelete(category) }
        }
    }

    class DiffCallback : DiffUtil.ItemCallback<Category>() {
        override fun areItemsTheSame(old: Category, new: Category) = old.id == new.id
        override fun areContentsTheSame(old: Category, new: Category) = old == new
    }
}
