package com.example.hesapyonetimi.presentation.tags

import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageButton
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
        v.findViewById<ImageButton>(R.id.btnCloseManageTags).setOnClickListener { dismiss() }
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
            .setView(v)
            .create()
    }
}

