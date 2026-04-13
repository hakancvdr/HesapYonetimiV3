package com.example.hesapyonetimi.presentation.categories

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.hesapyonetimi.R
import com.example.hesapyonetimi.domain.model.Category

class CategoryPickerAdapter(
    private val onSelect: (Category) -> Unit,
    private val onEdit: (Category) -> Unit,
    private val onDelete: (Category) -> Unit,
    private val proEnabled: Boolean
) : RecyclerView.Adapter<CategoryPickerAdapter.VH>() {

    private var source: List<Category> = emptyList()
    private val expandedParents = mutableSetOf<Long>()

    private sealed class Row {
        data class Top(val cat: Category, val hasChildren: Boolean, val expanded: Boolean) : Row()
        data class Sub(val cat: Category, val parent: Category) : Row()
    }

    private var rows: List<Row> = emptyList()

    fun submit(categories: List<Category>) {
        source = categories
        rows = buildRows(categories)
        notifyDataSetChanged()
    }

    private fun buildRows(categories: List<Category>): List<Row> {
        val byId = categories.associateBy { it.id }
        val childrenByParent = categories
            .asSequence()
            .filter { it.parentId != null }
            .groupBy { it.parentId!! }

        val top = categories
            .filter { it.parentId == null }
            .sortedBy { it.name.lowercase() }

        val out = ArrayList<Row>(top.size)
        for (t in top) {
            val children = childrenByParent[t.id].orEmpty()
            val hasChildren = proEnabled && children.isNotEmpty()
            val expanded = hasChildren && (t.id in expandedParents)
            out.add(Row.Top(cat = t, hasChildren = hasChildren, expanded = expanded))
            if (expanded) {
                val subcats = children.sortedBy { it.name.lowercase() }
                for (s in subcats) {
                    val parent = byId[s.parentId] ?: t
                    out.add(Row.Sub(cat = s, parent = parent))
                }
            }
        }
        return out
    }

    inner class VH(parent: ViewGroup) : RecyclerView.ViewHolder(
        LayoutInflater.from(parent.context).inflate(R.layout.item_category_picker_row, parent, false)
    ) {
        val icon: TextView = itemView.findViewById(R.id.tvRowIcon)
        val title: TextView = itemView.findViewById(R.id.tvRowTitle)
        val subtitle: TextView = itemView.findViewById(R.id.tvRowSubtitle)
        val chevron: TextView = itemView.findViewById(R.id.tvRowChevron)
        val btnEdit: TextView = itemView.findViewById(R.id.btnRowEdit)
        val btnDelete: TextView = itemView.findViewById(R.id.btnRowDelete)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = VH(parent)

    override fun onBindViewHolder(holder: VH, position: Int) {
        when (val row = rows[position]) {
            is Row.Top -> bindTop(holder, row)
            is Row.Sub -> bindSub(holder, row)
        }
    }

    private fun bindTop(holder: VH, row: Row.Top) {
        val cat = row.cat
        holder.icon.text = cat.icon
        holder.title.text = cat.name
        holder.subtitle.visibility = View.GONE

        holder.chevron.visibility = if (row.hasChildren) View.VISIBLE else View.GONE
        holder.chevron.text = if (row.expanded) "⌄" else "›"

        val locked = cat.isLocked || cat.name.equals("Diğer", ignoreCase = true)
        holder.btnEdit.visibility = if (locked) View.GONE else View.VISIBLE
        holder.btnDelete.visibility = if (locked) View.GONE else View.VISIBLE

        holder.itemView.setOnClickListener { onSelect(cat) }
        holder.chevron.setOnClickListener {
            if (!row.hasChildren) return@setOnClickListener
            if (cat.id in expandedParents) expandedParents.remove(cat.id) else expandedParents.add(cat.id)
            rows = buildRows(source)
            notifyDataSetChanged()
        }
        holder.btnEdit.setOnClickListener { onEdit(cat) }
        holder.btnDelete.setOnClickListener { onDelete(cat) }
    }

    private fun bindSub(holder: VH, row: Row.Sub) {
        val cat = row.cat
        holder.icon.text = cat.icon
        holder.title.text = cat.name
        holder.subtitle.text = row.parent.name
        holder.subtitle.visibility = View.VISIBLE

        holder.chevron.visibility = View.GONE

        val locked = cat.isLocked || cat.name.equals("Diğer", ignoreCase = true)
        holder.btnEdit.visibility = if (locked) View.GONE else View.VISIBLE
        holder.btnDelete.visibility = if (locked) View.GONE else View.VISIBLE

        holder.itemView.setOnClickListener { onSelect(cat) }
        holder.btnEdit.setOnClickListener { onEdit(cat) }
        holder.btnDelete.setOnClickListener { onDelete(cat) }
    }

    override fun getItemCount() = rows.size
}

