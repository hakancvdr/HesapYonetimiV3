package com.example.hesapyonetimi.presentation.reminders

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.hesapyonetimi.R
import com.example.hesapyonetimi.adapter.HatirlaticiAdapter
import com.example.hesapyonetimi.domain.model.Reminder
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class DayRemindersSheet : BottomSheetDialogFragment() {

    private val viewModel: ReminderViewModel by viewModels(
        ownerProducer = { requireParentFragment() }
    )

    companion object {
        private const val KEY_YEAR = "year"
        private const val KEY_MONTH = "month"
        private const val KEY_DAY = "day"
        private var cache: List<Reminder> = emptyList()

        fun show(fm: FragmentManager, year: Int, month: Int, day: Int, reminders: List<Reminder>) {
            cache = reminders
            DayRemindersSheet().apply {
                arguments = Bundle().apply {
                    putInt(KEY_YEAR, year)
                    putInt(KEY_MONTH, month)
                    putInt(KEY_DAY, day)
                }
            }.show(fm, "DayRemindersSheet")
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
        inflater.inflate(R.layout.bottom_sheet_day_reminders, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val y = requireArguments().getInt(KEY_YEAR)
        val m = requireArguments().getInt(KEY_MONTH)
        val d = requireArguments().getInt(KEY_DAY)

        view.findViewById<TextView>(R.id.tvDayRemindersTitle).text = "$d.${m + 1}.$y"

        val rv = view.findViewById<RecyclerView>(R.id.rvDayReminders)
        rv.layoutManager = LinearLayoutManager(requireContext())

        val parentFm = requireParentFragment().childFragmentManager
        val adapter = HatirlaticiAdapter(
            cache.sortedBy { it.dueDate },
            onOdendi = { id ->
                viewModel.markAsPaid(id)
                dismiss()
            },
            onDuzenle = { reminder ->
                dismiss()
                parentFm.executePendingTransactions()
                HatirlaticiEkleSheet.newInstance(reminder).show(parentFm, "HatirlaticiDuzenle")
            },
            onSil = { id ->
                viewModel.deleteReminder(id)
                dismiss()
            },
            onSilWithUndo = { reminder ->
                viewModel.deleteReminder(reminder.id)
                dismiss()
            }
        )
        rv.adapter = adapter

        view.findViewById<View>(R.id.btnDayRemindersClose).setOnClickListener { dismiss() }
    }
}
