package com.example.hesapyonetimi.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.os.bundleOf
import androidx.core.widget.doAfterTextChanged
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.hesapyonetimi.R
import androidx.lifecycle.lifecycleScope
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.textfield.TextInputEditText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MaterialIconPickerSheet : BottomSheetDialogFragment() {

    companion object {
        const val RESULT_KEY = "material_icon_picker_result"
        const val BUNDLE_ICON_NAME = "iconName"

        fun newInstance() = MaterialIconPickerSheet()
    }

    private var searchJob: Job? = null
    private var allNames: List<String> = emptyList()
    private lateinit var adapter: IconNameAdapter

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
        inflater.inflate(R.layout.sheet_material_icon_picker, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val rv = view.findViewById<RecyclerView>(R.id.rvMaterialIcons)
        val etSearch = view.findViewById<TextInputEditText>(R.id.etIconSearch)

        adapter = IconNameAdapter { name ->
            parentFragmentManager.setFragmentResult(
                RESULT_KEY,
                bundleOf(BUNDLE_ICON_NAME to name)
            )
            dismiss()
        }
        rv.layoutManager = GridLayoutManager(requireContext(), 4)
        rv.adapter = adapter

        viewLifecycleOwner.lifecycleScope.launch {
            allNames = withContext(Dispatchers.IO) {
                requireContext().assets.open("material_icons_outlined.txt")
                    .bufferedReader()
                    .useLines { seq ->
                        seq.map { it.trim() }.filter { it.isNotEmpty() }.sorted().toList()
                    }
            }
            adapter.submit(allNames)
        }

        etSearch.doAfterTextChanged { editable ->
            searchJob?.cancel()
            val q = editable?.toString().orEmpty().trim().lowercase()
            searchJob = viewLifecycleOwner.lifecycleScope.launch {
                delay(120)
                val filtered = if (q.isEmpty()) allNames
                else allNames.filter { it.contains(q) }
                adapter.submit(filtered)
            }
        }
    }

    override fun onStart() {
        super.onStart()
        val behavior = com.google.android.material.bottomsheet.BottomSheetBehavior.from(
            requireView().parent as View
        )
        behavior.state = com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_EXPANDED
        behavior.skipCollapsed = true
    }

    private class IconNameAdapter(
        private val onPick: (String) -> Unit
    ) : RecyclerView.Adapter<IconNameAdapter.VH>() {

        private var items: List<String> = emptyList()

        fun submit(list: List<String>) {
            items = list
            notifyDataSetChanged()
        }

        class VH(view: View) : RecyclerView.ViewHolder(view) {
            val glyph: TextView = view.findViewById(R.id.tv_material_icon_glyph)
            val label: TextView = view.findViewById(R.id.tv_material_icon_name)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val v = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_material_icon_cell, parent, false)
            return VH(v)
        }

        override fun onBindViewHolder(holder: VH, position: Int) {
            val name = items[position]
            MaterialCategoryIcon.bind(holder.glyph, name, 24f)
            holder.label.text = name.replace('_', ' ')
            holder.itemView.setOnClickListener { onPick(name) }
        }

        override fun getItemCount() = items.size
    }
}
