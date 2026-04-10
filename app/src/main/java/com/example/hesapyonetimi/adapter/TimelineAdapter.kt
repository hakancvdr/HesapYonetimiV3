package com.example.hesapyonetimi.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.example.hesapyonetimi.R
import com.example.hesapyonetimi.domain.model.Transaction
import com.example.hesapyonetimi.presentation.common.CurrencyFormatter
import java.text.SimpleDateFormat
import java.util.*

private const val TYPE_HEADER = 0
private const val TYPE_ITEM   = 1

private sealed class TimelineRow {
    data class Header(val label: String) : TimelineRow()
    data class Item(val tx: Transaction) : TimelineRow()
}

class TimelineAdapter(
    private val onItemClick: (Transaction) -> Unit = {},
    private val onItemLongClick: (Transaction) -> Unit = {}
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private var rows: List<TimelineRow> = emptyList()
    private var allTransactions: List<Transaction> = emptyList()
    private var lastFilterQuery: String = ""
    private val dateFmt = SimpleDateFormat("d MMMM yyyy  —  EEEE", Locale("tr"))
    private val timeFmt = SimpleDateFormat("HH:mm", Locale.getDefault())

    fun submitList(transactions: List<Transaction>) {
        allTransactions = transactions
        val filtered = if (lastFilterQuery.isBlank()) transactions
        else transactions.filter {
            it.description.contains(lastFilterQuery, ignoreCase = true) ||
                it.categoryName.contains(lastFilterQuery, ignoreCase = true)
        }
        rebuildRows(filtered)
    }

    fun filter(query: String) {
        lastFilterQuery = query.trim()
        val filtered = if (lastFilterQuery.isBlank()) allTransactions
        else allTransactions.filter {
            it.description.contains(lastFilterQuery, ignoreCase = true) ||
            it.categoryName.contains(lastFilterQuery, ignoreCase = true)
        }
        rebuildRows(filtered)
    }

    /** Arama metni dolu, dönemde işlem var ama eşleşme yok */
    fun shouldShowSearchEmpty(): Boolean =
        lastFilterQuery.isNotEmpty() && allTransactions.isNotEmpty() && rows.isEmpty()

    private fun rebuildRows(transactions: List<Transaction>) {
        val newRows = mutableListOf<TimelineRow>()
        val grouped = transactions.groupBy { tx ->
            Calendar.getInstance().apply { timeInMillis = tx.date }
                .let { cal ->
                    val c = Calendar.getInstance()
                    if (cal.get(Calendar.YEAR) == c.get(Calendar.YEAR) &&
                        cal.get(Calendar.DAY_OF_YEAR) == c.get(Calendar.DAY_OF_YEAR)) "Bugün"
                    else dateFmt.format(Date(tx.date))
                }
        }
        grouped.forEach { (label, txs) ->
            newRows.add(TimelineRow.Header(label))
            txs.forEach { newRows.add(TimelineRow.Item(it)) }
        }
        rows = newRows
        notifyDataSetChanged()
    }

    override fun getItemViewType(position: Int) = when (rows[position]) {
        is TimelineRow.Header -> TYPE_HEADER
        is TimelineRow.Item   -> TYPE_ITEM
    }

    override fun getItemCount() = rows.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return if (viewType == TYPE_HEADER) {
            HeaderVH(inflater.inflate(R.layout.item_timeline_header, parent, false))
        } else {
            ItemVH(inflater.inflate(R.layout.item_transaction_row, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val row = rows[position]) {
            is TimelineRow.Header -> (holder as HeaderVH).bind(row.label)
            is TimelineRow.Item   -> (holder as ItemVH).bind(row.tx)
        }
    }

    inner class HeaderVH(view: View) : RecyclerView.ViewHolder(view) {
        private val tvDate: TextView = view.findViewById(R.id.tvTimelineDate)
        fun bind(label: String) { tvDate.text = label }
    }

    inner class ItemVH(view: View) : RecyclerView.ViewHolder(view) {
        private val tvTitle: TextView    = view.findViewById(R.id.tv_transaction_title)
        private val tvCategory: TextView = view.findViewById(R.id.tv_transaction_category_name)
        private val tvAmount: TextView   = view.findViewById(R.id.tv_transaction_amount)

        fun bind(tx: Transaction) {
            tvTitle.text    = tx.description.ifBlank { tx.categoryName }
            tvCategory.text = "${tx.categoryIcon} ${tx.categoryName} · ${timeFmt.format(Date(tx.date))}"
            tvAmount.text   = CurrencyFormatter.formatWithSign(tx.amount, tx.isIncome)
            tvAmount.setTextColor(
                ContextCompat.getColor(itemView.context,
                    if (tx.isIncome) R.color.income_green else R.color.expense_red)
            )
            itemView.setOnClickListener { onItemClick(tx) }
            itemView.setOnLongClickListener { onItemLongClick(tx); true }
        }
    }
}
