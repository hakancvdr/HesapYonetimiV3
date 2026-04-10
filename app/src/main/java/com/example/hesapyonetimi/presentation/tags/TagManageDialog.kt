package com.example.hesapyonetimi.presentation.tags

import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.hesapyonetimi.R
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class TagManageDialog : DialogFragment() {

    private val viewModel: TagViewModel by viewModels()

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val v = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_manage_tags, null)
        val rv = v.findViewById<RecyclerView>(R.id.rvTags)
        val et = v.findViewById<TextInputEditText>(R.id.etNewTag)
        val btnAdd = v.findViewById<View>(R.id.btnAddTag)

        val adapter = TagRowAdapter(onDelete = { id -> viewModel.deleteTag(id) })
        rv.layoutManager = LinearLayoutManager(requireContext())
        rv.adapter = adapter

        btnAdd.setOnClickListener {
            viewModel.addTag(et.text?.toString().orEmpty())
            et.setText("")
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.tags.collect { adapter.submit(it) }
            }
        }

        return MaterialAlertDialogBuilder(requireContext())
            .setTitle("Etiketler")
            .setView(v)
            .setPositiveButton("Kapat", null)
            .create()
    }
}

private class TagRowAdapter(
    private val onDelete: (Long) -> Unit
) : RecyclerView.Adapter<TagRowAdapter.VH>() {

    private var list: List<com.example.hesapyonetimi.data.local.entity.TagEntity> = emptyList()

    fun submit(newList: List<com.example.hesapyonetimi.data.local.entity.TagEntity>) {
        list = newList
        notifyDataSetChanged()
    }

    inner class VH(v: View) : RecyclerView.ViewHolder(v) {
        val tvName: TextView = v.findViewById(R.id.tvTagName)
        val btnDelete: View = v.findViewById(R.id.btnDeleteTag)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_tag_row, parent, false)
        return VH(v)
    }

    override fun getItemCount() = list.size

    override fun onBindViewHolder(h: VH, position: Int) {
        val t = list[position]
        h.tvName.text = t.name
        h.btnDelete.setOnClickListener { onDelete(t.id) }
    }
}

