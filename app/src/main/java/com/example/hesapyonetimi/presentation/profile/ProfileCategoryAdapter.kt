package com.example.hesapyonetimi.presentation.profile

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.hesapyonetimi.R
import com.example.hesapyonetimi.domain.model.Category

class ProfileCategoryAdapter(
    private val onEdit: (Category) -> Unit,
    private val onDelete: (Category) -> Unit
) : RecyclerView.Adapter<ProfileCategoryAdapter.ViewHolder>() {

    private var fullList: List<Category> = emptyList()
    private var displayList: List<Category> = emptyList()

    /** true → gelir kategorileri, false → gider kategorileri */
    var showIncome: Boolean = false
        set(value) {
            field = value
            applyFilter()
        }

    fun submitFullList(list: List<Category>) {
        fullList = list
        applyFilter()
    }

    private fun applyFilter() {
        displayList = fullList
            .filter { it.isIncome == showIncome }
            .filter { it.parentId == null }
        notifyDataSetChanged()
    }

    override fun getItemCount() = displayList.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder =
        ViewHolder(
            LayoutInflater.from(parent.context)
                .inflate(R.layout.item_profile_category, parent, false)
        )

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(displayList[position])
    }

    inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val vColorDot: View      = view.findViewById(R.id.vColorDot)
        private val tvEmoji: TextView    = view.findViewById(R.id.tvEmoji)
        private val tvName: TextView     = view.findViewById(R.id.tvCategoryName)
        private val tvType: TextView     = view.findViewById(R.id.tvCategoryType)
        private val btnEdit: ImageButton = view.findViewById(R.id.btnEditCategory)
        private val btnDel: ImageButton  = view.findViewById(R.id.btnDeleteCategory)

        fun bind(category: Category) {
            tvEmoji.text = category.icon
            tvName.text  = category.name
            tvType.text  = if (category.isIncome) "Gelir" else "Gider"
            try {
                vColorDot.background.mutate().setTint(Color.parseColor(category.color))
            } catch (_: Exception) {}
            val locked = category.isLocked || category.name.equals("Diğer", ignoreCase = true)
            btnEdit.visibility = if (locked) View.GONE else View.VISIBLE
            btnDel.visibility = if (locked) View.GONE else View.VISIBLE
            btnEdit.setOnClickListener { onEdit(category) }
            btnDel.setOnClickListener  { onDelete(category) }
        }
    }
}
