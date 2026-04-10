package com.example.hesapyonetimi.presentation.goals

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.os.bundleOf
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.hesapyonetimi.R
import com.example.hesapyonetimi.data.local.entity.GoalContributionEntity
import com.example.hesapyonetimi.presentation.common.CurrencyFormatter
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

@AndroidEntryPoint
class GoalHistorySheet : BottomSheetDialogFragment() {

    private val viewModel: GoalViewModel by viewModels({ requireParentFragment() })

    companion object {
        private const val ARG_ID = "goalId"
        private const val ARG_HEAD = "heading"

        fun show(manager: FragmentManager, goalId: Long, heading: String) {
            GoalHistorySheet().apply {
                arguments = bundleOf(ARG_ID to goalId, ARG_HEAD to heading)
            }.show(manager, "GoalHistory")
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
        inflater.inflate(R.layout.bottom_sheet_goal_history, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val goalId = requireArguments().getLong(ARG_ID)
        val heading = requireArguments().getString(ARG_HEAD) ?: ""
        view.findViewById<TextView>(R.id.tvGoalHistoryTitle).text = heading

        val rv = view.findViewById<RecyclerView>(R.id.rvGoalHistory)
        val empty = view.findViewById<TextView>(R.id.tvGoalHistoryEmpty)
        val adapter = HistoryRowsAdapter()
        rv.layoutManager = LinearLayoutManager(requireContext())
        rv.adapter = adapter

        val fmt = SimpleDateFormat("d MMM yyyy, HH:mm", Locale("tr"))
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.contributionsFlow(goalId).collect { rows ->
                    if (rows.isEmpty()) {
                        empty.visibility = View.VISIBLE
                        rv.visibility = View.GONE
                    } else {
                        empty.visibility = View.GONE
                        rv.visibility = View.VISIBLE
                        adapter.submit(rows, fmt)
                    }
                }
            }
        }
    }

    private class HistoryRowsAdapter : RecyclerView.Adapter<HistoryRowsAdapter.VH>() {
        private var items: List<GoalContributionEntity> = emptyList()
        private var fmt: SimpleDateFormat? = null

        class VH(val tv: TextView) : RecyclerView.ViewHolder(tv)

        override fun onCreateViewHolder(parent: ViewGroup, vt: Int): VH {
            val tv = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_goal_history_row, parent, false) as TextView
            return VH(tv)
        }

        override fun getItemCount() = items.size

        override fun onBindViewHolder(h: VH, pos: Int) {
            val r = items[pos]
            val f = fmt ?: SimpleDateFormat.getDateTimeInstance()
            h.tv.text = "${f.format(Date(r.contributedAt))}  ·  +${CurrencyFormatter.format(r.amount)}"
        }

        fun submit(rows: List<GoalContributionEntity>, dateFmt: SimpleDateFormat) {
            fmt = dateFmt
            items = rows
            notifyDataSetChanged()
        }
    }
}
