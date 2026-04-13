package com.example.hesapyonetimi.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.example.hesapyonetimi.R
import com.example.hesapyonetimi.domain.model.Category
import com.example.hesapyonetimi.ui.MaterialCategoryIcon
import com.google.android.flexbox.FlexboxLayoutManager

/**
 * Flexbox varsayılan flexShrink öğeleri satıra sığdırmak için küçültür; + düğmesi ince çizgi gibi
 * görünüyordu. flexShrink=0 ve + için sabit width/height kullanılır. Chip araları setMargins ile.
 */
class DashboardCategoryFlowAdapter(
    private var categories: List<Category>,
    private val onSelected: (Category) -> Unit,
    private val onAddClick: () -> Unit,
    private val parentNameResolver: ((Long) -> String?)? = null
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private var selectedId: Long = -1

    companion object {
        private const val TYPE_CATEGORY = 0
        private const val TYPE_ADD = 1
    }

    fun setCategories(list: List<Category>, defaultName: String) {
        categories = list
        if (defaultName.isNotEmpty()) {
            val default = list.firstOrNull { it.name.equals(defaultName, ignoreCase = true) } ?: list.firstOrNull()
            selectedId = default?.id ?: -1
            default?.let { onSelected(it) }
        } else {
            selectedId = -1
        }
        notifyDataSetChanged()
    }

    fun setSelected(categoryId: Long) {
        val old = categories.indexOfFirst { it.id == selectedId }
        selectedId = categoryId
        if (old >= 0) notifyItemChanged(old)
        val newIdx = categories.indexOfFirst { it.id == categoryId }
        if (newIdx >= 0) notifyItemChanged(newIdx)
    }

    override fun getItemViewType(position: Int) =
        if (position < categories.size) TYPE_CATEGORY else TYPE_ADD

    override fun getItemCount() = categories.size + 1

    inner class CategoryVH(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val icon: TextView = itemView.findViewById(R.id.tv_chip_icon)
        val name: TextView = itemView.findViewById(R.id.tv_chip_name)
        val parentLabel: TextView = itemView.findViewById(R.id.tv_chip_parent)
    }

    class AddVH(view: View) : RecyclerView.ViewHolder(view)

    private fun marginPx(parent: ViewGroup): Int =
        parent.resources.getDimensionPixelSize(R.dimen.dashboard_category_flow_item_margin)

    private fun applyFlexParamsCategory(parent: ViewGroup, itemView: View) {
        val m = marginPx(parent)
        val lp = FlexboxLayoutManager.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
        lp.setMargins(m, m, m, m)
        lp.flexShrink = 0f
        lp.flexGrow = 0f
        itemView.layoutParams = lp
    }

    private fun applyFlexParamsAdd(parent: ViewGroup, itemView: View) {
        val m = marginPx(parent)
        val size = parent.resources.getDimensionPixelSize(R.dimen.dashboard_category_chip_min_height)
        val lp = FlexboxLayoutManager.LayoutParams(size, size)
        lp.setMargins(m, m, m, m)
        lp.flexShrink = 0f
        lp.flexGrow = 0f
        itemView.layoutParams = lp
    }

    private fun refreshFlexParams(holder: RecyclerView.ViewHolder) {
        val parent = holder.itemView.parent as? ViewGroup ?: return
        when (holder) {
            is AddVH -> applyFlexParamsAdd(parent, holder.itemView)
            is CategoryVH -> applyFlexParamsCategory(parent, holder.itemView)
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            TYPE_ADD -> {
                val v = inflater.inflate(R.layout.item_dashboard_category_add, parent, false)
                applyFlexParamsAdd(parent, v)
                AddVH(v)
            }
            else -> {
                val v = inflater.inflate(R.layout.item_dashboard_category_flow_chip, parent, false)
                applyFlexParamsCategory(parent, v)
                CategoryVH(v)
            }
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        refreshFlexParams(holder)
        when (holder) {
            is AddVH -> holder.itemView.setOnClickListener { onAddClick() }
            is CategoryVH -> {
                val cat = categories[position]
                val selected = cat.id == selectedId
                val ctx = holder.itemView.context
                val white = ContextCompat.getColor(ctx, android.R.color.white)
                val primary = ContextCompat.getColor(ctx, R.color.text_primary)
                val secondary = ContextCompat.getColor(ctx, R.color.text_secondary)

                MaterialCategoryIcon.bind(
                    holder.icon,
                    cat.icon,
                    16f,
                    if (selected) white else primary
                )
                holder.name.text = cat.name
                val parentName = cat.parentId?.let { parentNameResolver?.invoke(it) }
                if (!parentName.isNullOrBlank()) {
                    holder.parentLabel.text = "· $parentName"
                    holder.parentLabel.visibility = View.VISIBLE
                    holder.parentLabel.setTextColor(if (selected) white else secondary)
                } else {
                    holder.parentLabel.visibility = View.GONE
                }

                holder.itemView.setBackgroundResource(
                    if (selected) R.drawable.dashboard_flow_chip_selected
                    else R.drawable.dashboard_flow_chip_unselected
                )
                holder.name.setTextColor(if (selected) white else primary)

                holder.itemView.setOnClickListener {
                    val old = categories.indexOfFirst { it.id == selectedId }
                    selectedId = cat.id
                    onSelected(cat)
                    if (old >= 0) notifyItemChanged(old)
                    notifyItemChanged(position)
                }
            }
        }
    }
}
