package com.example.hesapyonetimi.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.FragmentManager
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.hesapyonetimi.R
import com.google.android.material.bottomsheet.BottomSheetDialogFragment

class EmojiPickerSheet : BottomSheetDialogFragment() {

    companion object {
        const val RESULT_KEY = "emoji_picker_result"
        const val BUNDLE_EMOJI = "emoji"

        private const val ARG_SELECTED = "selected"

        fun show(fm: FragmentManager, selected: String, emojis: ArrayList<String>) {
            EmojiPickerSheet().apply {
                arguments = Bundle().apply {
                    putString(ARG_SELECTED, selected)
                    putStringArrayList("emojis", emojis)
                }
            }.show(fm, "EmojiPickerSheet")
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
        inflater.inflate(R.layout.bottom_sheet_emoji_picker, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val selected = requireArguments().getString(ARG_SELECTED, "🎯") ?: "🎯"
        val emojis = requireArguments().getStringArrayList("emojis") ?: arrayListOf("🎯")

        view.findViewById<TextView>(R.id.btnEmojiPickerClose).setOnClickListener { dismiss() }

        val rv = view.findViewById<RecyclerView>(R.id.rvEmojiGrid)
        rv.layoutManager = GridLayoutManager(requireContext(), 6)
        rv.adapter = EmojiGridAdapter(emojis, selected) { emoji ->
            parentFragmentManager.setFragmentResult(
                RESULT_KEY,
                Bundle().apply { putString(BUNDLE_EMOJI, emoji) }
            )
            dismiss()
        }
    }
}

