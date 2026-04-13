package com.example.hesapyonetimi.presentation.profile

import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.ImageButton
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.hesapyonetimi.R
import com.example.hesapyonetimi.domain.model.Category
import com.example.hesapyonetimi.presentation.tags.TagRowAdapter
import com.example.hesapyonetimi.presentation.tags.TagViewModel
import com.example.hesapyonetimi.ui.CategoryColorPalette
import com.example.hesapyonetimi.ui.ColorSwatchAdapter
import com.example.hesapyonetimi.ui.MaterialCategoryIcon
import com.example.hesapyonetimi.ui.MaterialIconPickerSheet
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.textfield.TextInputEditText
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class ProfileCategoriesDialogFragment : DialogFragment() {

    private val profileViewModel: ProfileViewModel by viewModels()
    private val tagViewModel: TagViewModel by viewModels()

    private lateinit var adapter: ProfileCategoryAdapter
    private var selectedTab: Int = 0 // 0 gider 1 gelir 2 etiket
    private var pendingMaterialIconPick: ((String) -> Unit)? = null

    companion object {
        fun newInstance() = ProfileCategoriesDialogFragment()
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val d = super.onCreateDialog(savedInstanceState)
        d.window?.setBackgroundDrawableResource(android.R.color.transparent)
        return d
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
        inflater.inflate(R.layout.dialog_profile_categories, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        childFragmentManager.setFragmentResultListener(
            MaterialIconPickerSheet.RESULT_KEY,
            viewLifecycleOwner
        ) { _, bundle ->
            val iconName = bundle.getString(MaterialIconPickerSheet.BUNDLE_ICON_NAME)
                ?: return@setFragmentResultListener
            pendingMaterialIconPick?.invoke(iconName)
        }

        val rv = view.findViewById<RecyclerView>(R.id.rvCategories)
        val panelTags = view.findViewById<View>(R.id.panelTags)
        val btnExpenseTab = view.findViewById<TextView>(R.id.btnExpenseTab)
        val btnIncomeTab = view.findViewById<TextView>(R.id.btnIncomeTab)
        val btnTagsTab = view.findViewById<TextView>(R.id.btnTagsTab)
        val fab = view.findViewById<FloatingActionButton>(R.id.fabAddCategory)

        view.findViewById<View>(R.id.btnCloseCategoriesDialog).setOnClickListener { dismiss() }

        adapter = ProfileCategoryAdapter(
            onEdit = { showEditCategoryDialog(it) },
            onDelete = { cat -> confirmDeleteCategory(cat) }
        )
        rv.layoutManager = LinearLayoutManager(requireContext())
        rv.adapter = adapter

        val tagAdapter = TagRowAdapter { id -> tagViewModel.deleteTag(id) }
        view.findViewById<RecyclerView>(R.id.rvProfileDialogTags).apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = tagAdapter
        }
        val etTag = view.findViewById<TextInputEditText>(R.id.etProfileDialogNewTag)
        view.findViewById<View>(R.id.btnProfileDialogAddTag).setOnClickListener {
            tagViewModel.addTag(etTag.text?.toString().orEmpty())
            etTag.setText("")
        }

        fun styleTabs() {
            val sel = ContextCompat.getColor(requireContext(), R.color.green_primary)
            val unsel = ContextCompat.getColor(requireContext(), R.color.text_secondary)
            btnExpenseTab.setBackgroundResource(if (selectedTab == 0) R.drawable.kategori_item_selected_bg else R.drawable.kategori_item_bg)
            btnExpenseTab.setTextColor(if (selectedTab == 0) sel else unsel)
            btnIncomeTab.setBackgroundResource(if (selectedTab == 1) R.drawable.kategori_item_selected_bg else R.drawable.kategori_item_bg)
            btnIncomeTab.setTextColor(if (selectedTab == 1) sel else unsel)
            btnTagsTab.setBackgroundResource(if (selectedTab == 2) R.drawable.kategori_item_selected_bg else R.drawable.kategori_item_bg)
            btnTagsTab.setTextColor(if (selectedTab == 2) sel else unsel)
            fab.visibility = if (selectedTab == 2) View.GONE else View.VISIBLE
            rv.visibility = if (selectedTab == 2) View.GONE else View.VISIBLE
            panelTags.visibility = if (selectedTab == 2) View.VISIBLE else View.GONE
        }

        btnExpenseTab.setOnClickListener { selectedTab = 0; adapter.showIncome = false; styleTabs() }
        btnIncomeTab.setOnClickListener { selectedTab = 1; adapter.showIncome = true; styleTabs() }
        btnTagsTab.setOnClickListener { selectedTab = 2; styleTabs() }

        fab.setOnClickListener { showAddCategoryDialog() }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    profileViewModel.categories.collect { list ->
                        adapter.submitFullList(list)
                    }
                }
                launch {
                    tagViewModel.tags.collect { tagAdapter.submit(it) }
                }
            }
        }

        styleTabs()
    }

    override fun onStart() {
        super.onStart()
        val h = (resources.displayMetrics.heightPixels * 0.92f).toInt()
        dialog?.window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, h)
    }

    private fun confirmDeleteCategory(cat: Category) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(cat.name)
            .setMessage(getString(R.string.categories_delete_confirm))
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                profileViewModel.deleteCategory(cat)
            }
            .show()
    }

    private fun showAddCategoryDialog() {
        val v = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_profile_add_category, null)
        val tvSel = v.findViewById<TextView>(R.id.tvSelectedEmoji)
        val etName = v.findViewById<TextInputEditText>(R.id.etCategoryName)
        val til = v.findViewById<com.google.android.material.textfield.TextInputLayout>(R.id.tilCategoryName)
        val btnExpense = v.findViewById<MaterialButton>(R.id.btnAddCatExpense)
        val btnIncome = v.findViewById<MaterialButton>(R.id.btnAddCatIncome)
        var icon = "credit_card"
        var isIncomeCategory = adapter.showIncome
        var selectedColor = CategoryColorPalette.closestOrDefault("#2E7D32")
        val colorWhite = ContextCompat.getColor(requireContext(), R.color.text_white)
        val colorTextPrimary = ContextCompat.getColor(requireContext(), R.color.text_primary)

        fun updateTypeVisuals(expenseSelected: Boolean) {
            if (expenseSelected) {
                btnExpense.backgroundTintList = ResourcesCompat.getColorStateList(resources, R.color.green_primary, null)
                btnExpense.setTextColor(colorWhite)
                btnIncome.backgroundTintList = ResourcesCompat.getColorStateList(resources, android.R.color.transparent, null)
                btnIncome.setTextColor(colorTextPrimary)
            } else {
                btnIncome.backgroundTintList = ResourcesCompat.getColorStateList(resources, R.color.green_primary, null)
                btnIncome.setTextColor(colorWhite)
                btnExpense.backgroundTintList = ResourcesCompat.getColorStateList(resources, android.R.color.transparent, null)
                btnExpense.setTextColor(colorTextPrimary)
            }
        }

        MaterialCategoryIcon.bind(tvSel, icon, 26f)
        v.findViewById<MaterialButton>(R.id.btnBrowseMaterialIcons).setOnClickListener {
            pendingMaterialIconPick = { name ->
                icon = name
                MaterialCategoryIcon.bind(tvSel, name, 26f)
            }
            MaterialIconPickerSheet.newInstance().show(childFragmentManager, "MaterialIconPicker")
        }

        v.findViewById<RecyclerView>(R.id.rvCategoryColors).apply {
            layoutManager = LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
            adapter = ColorSwatchAdapter(
                colors = CategoryColorPalette.hex,
                initialSelected = selectedColor
            ) { c -> selectedColor = c }
        }

        updateTypeVisuals(expenseSelected = !isIncomeCategory)
        btnExpense.setOnClickListener {
            isIncomeCategory = false
            updateTypeVisuals(true)
        }
        btnIncome.setOnClickListener {
            isIncomeCategory = true
            updateTypeVisuals(false)
        }

        val dialog = Dialog(requireContext())
        dialog.setContentView(v)
        dialog.window?.setLayout((resources.displayMetrics.widthPixels * 0.92).toInt(), ViewGroup.LayoutParams.WRAP_CONTENT)
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)

        v.findViewById<ImageButton>(R.id.btnCloseProfileCategorySheet).setOnClickListener { dialog.dismiss() }
        v.findViewById<View>(R.id.btnCancel).setOnClickListener { dialog.dismiss() }
        v.findViewById<View>(R.id.btnAdd).setOnClickListener {
            val name = etName.text?.toString()?.trim().orEmpty()
            if (name.isBlank()) {
                til.error = getString(R.string.categories_name_required)
                return@setOnClickListener
            }
            profileViewModel.addCategory(
                Category(0, name, icon, selectedColor, isIncomeCategory, isDefault = false)
            )
            dialog.dismiss()
        }
        dialog.show()
    }

    private fun showEditCategoryDialog(cat: Category) {
        val v = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_profile_edit_category, null)
        val tvSel = v.findViewById<TextView>(R.id.tvSelectedEmoji)
        val etName = v.findViewById<TextInputEditText>(R.id.etCategoryName)
        val til = v.findViewById<com.google.android.material.textfield.TextInputLayout>(R.id.tilCategoryName)
        val btnExpense = v.findViewById<MaterialButton>(R.id.btnAddCatExpense)
        val btnIncome = v.findViewById<MaterialButton>(R.id.btnAddCatIncome)
        var icon = cat.icon.ifBlank { "credit_card" }
        var isIncomeCategory = cat.isIncome
        var selectedColor = CategoryColorPalette.closestOrDefault(cat.color)
        val colorWhite = ContextCompat.getColor(requireContext(), R.color.text_white)
        val colorTextPrimary = ContextCompat.getColor(requireContext(), R.color.text_primary)

        fun updateTypeVisuals(expenseSelected: Boolean) {
            if (expenseSelected) {
                btnExpense.backgroundTintList = ResourcesCompat.getColorStateList(resources, R.color.green_primary, null)
                btnExpense.setTextColor(colorWhite)
                btnIncome.backgroundTintList = ResourcesCompat.getColorStateList(resources, android.R.color.transparent, null)
                btnIncome.setTextColor(colorTextPrimary)
            } else {
                btnIncome.backgroundTintList = ResourcesCompat.getColorStateList(resources, R.color.green_primary, null)
                btnIncome.setTextColor(colorWhite)
                btnExpense.backgroundTintList = ResourcesCompat.getColorStateList(resources, android.R.color.transparent, null)
                btnExpense.setTextColor(colorTextPrimary)
            }
        }

        MaterialCategoryIcon.bind(tvSel, icon, 26f)
        etName.setText(cat.name)
        v.findViewById<MaterialButton>(R.id.btnBrowseMaterialIcons).setOnClickListener {
            pendingMaterialIconPick = { name ->
                icon = name
                MaterialCategoryIcon.bind(tvSel, name, 26f)
            }
            MaterialIconPickerSheet.newInstance().show(childFragmentManager, "MaterialIconPicker")
        }

        v.findViewById<RecyclerView>(R.id.rvCategoryColors).apply {
            layoutManager = LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
            adapter = ColorSwatchAdapter(
                colors = CategoryColorPalette.hex,
                initialSelected = selectedColor
            ) { c -> selectedColor = c }
        }

        updateTypeVisuals(expenseSelected = !isIncomeCategory)
        btnExpense.setOnClickListener {
            isIncomeCategory = false
            updateTypeVisuals(true)
        }
        btnIncome.setOnClickListener {
            isIncomeCategory = true
            updateTypeVisuals(false)
        }

        val dialog = Dialog(requireContext())
        dialog.setContentView(v)
        dialog.window?.setLayout((resources.displayMetrics.widthPixels * 0.92).toInt(), ViewGroup.LayoutParams.WRAP_CONTENT)
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)

        v.findViewById<ImageButton>(R.id.btnCloseProfileCategorySheet).setOnClickListener { dialog.dismiss() }
        v.findViewById<View>(R.id.btnCancel).setOnClickListener { dialog.dismiss() }
        v.findViewById<View>(R.id.btnSave).setOnClickListener {
            val name = etName.text?.toString()?.trim().orEmpty()
            if (name.isBlank()) {
                til.error = getString(R.string.categories_name_required)
                return@setOnClickListener
            }
            profileViewModel.updateCategory(cat.copy(name = name, icon = icon, color = selectedColor, isIncome = isIncomeCategory))
            dialog.dismiss()
        }
        dialog.show()
    }
}
