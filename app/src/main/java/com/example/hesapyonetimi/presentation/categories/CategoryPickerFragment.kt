package com.example.hesapyonetimi.presentation.categories

import android.os.Bundle
import android.app.AlertDialog
import android.app.Dialog
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.RecyclerView
import com.example.hesapyonetimi.R
import com.example.hesapyonetimi.auth.AuthPrefs
import com.example.hesapyonetimi.domain.model.Category
import com.example.hesapyonetimi.ui.CategoryColorPalette
import com.example.hesapyonetimi.ui.ColorSwatchAdapter
import com.example.hesapyonetimi.ui.MaterialCategoryIcon
import com.example.hesapyonetimi.ui.MaterialIconPickerSheet
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class CategoryPickerFragment : Fragment() {

    companion object {
        const val RESULT_KEY = "category_picker_result"
        const val BUNDLE_CATEGORY_ID = "categoryId"

        private const val ARG_IS_INCOME = "arg_is_income"

        fun args(isIncome: Boolean): Bundle = bundleOf(ARG_IS_INCOME to isIncome)
    }

    private val viewModel: CategoryPickerViewModel by viewModels()

    private var isIncome: Boolean = false
    private var proEnabled: Boolean = false
    private lateinit var adapter: CategoryPickerAdapter
    private var pendingMaterialIconPick: ((String) -> Unit)? = null

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        isIncome = arguments?.getBoolean(ARG_IS_INCOME) ?: false
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return inflater.inflate(R.layout.fragment_category_picker, container, false)
    }

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

        proEnabled = AuthPrefs.isProMember(requireContext())

        val btnExpense = view.findViewById<TextView>(R.id.btnPickerExpense)
        val btnIncome = view.findViewById<TextView>(R.id.btnPickerIncome)

        val rv = view.findViewById<RecyclerView>(R.id.rvCategoryPicker)
        adapter = CategoryPickerAdapter(
            onSelect = { cat -> returnSelection(cat) },
            onEdit = { cat -> showEditDialog(cat) },
            onDelete = { cat -> confirmDelete(cat) },
            proEnabled = proEnabled
        )
        rv.layoutManager = LinearLayoutManager(requireContext())
        rv.adapter = adapter

        fun updateToggle() {
            val green = ContextCompat.getColor(requireContext(), R.color.green_primary)
            val muted = ContextCompat.getColor(requireContext(), R.color.text_secondary)
            if (!isIncome) {
                btnExpense.setBackgroundResource(R.drawable.toggle_pill_selected)
                btnExpense.setTextColor(green)
                btnIncome.background = null
                btnIncome.setTextColor(muted)
            } else {
                btnIncome.setBackgroundResource(R.drawable.toggle_pill_selected)
                btnIncome.setTextColor(green)
                btnExpense.background = null
                btnExpense.setTextColor(muted)
            }
            viewModel.setType(isIncome)
        }

        btnExpense.setOnClickListener { isIncome = false; updateToggle() }
        btnIncome.setOnClickListener { isIncome = true; updateToggle() }
        updateToggle()

        view.findViewById<View>(R.id.fabAddCategory).setOnClickListener {
            // Limit kontrolü ViewModel içinde yapılır.
            // Böylece silme gibi işlemlerden hemen sonra “stale count” yüzünden
            // kullanıcı gereksiz yere engellenmez.
            showAddDialog()
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    adapter.submit(state.categories)
                    state.message?.let {
                        toast(it)
                        viewModel.clearMessage()
                    }
                }
            }
        }
    }

    private fun returnSelection(cat: Category) {
        requireActivity().supportFragmentManager.setFragmentResult(
            RESULT_KEY,
            bundleOf(BUNDLE_CATEGORY_ID to cat.id)
        )
        findNavController().popBackStack()
    }

    private fun confirmDelete(cat: Category) {
        if (cat.isLocked || cat.name.equals("Diğer", ignoreCase = true)) {
            toast("Diğer kategorisi silinemez")
            return
        }
        val msg = if (cat.parentId == null) {
            "Bu kategori silinirse alt kategorileri de silinir ve bağlı işlemler Diğer'e taşınır."
        } else {
            "Bu alt kategori silinirse bağlı işlemler Diğer'e taşınır."
        }
        AlertDialog.Builder(requireContext())
            .setTitle("Kategori silinsin mi?")
            .setMessage(msg)
            .setNegativeButton("İptal", null)
            .setPositiveButton("Sil") { _, _ ->
                viewModel.deleteCategory(cat)
            }
            .show()
    }

    private fun showAddDialog() {
        val v = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_profile_add_category, null)
        val tvSel = v.findViewById<TextView>(R.id.tvSelectedEmoji)
        val etName = v.findViewById<TextInputEditText>(R.id.etCategoryName)
        val til = v.findViewById<com.google.android.material.textfield.TextInputLayout>(R.id.tilCategoryName)
        val btnExpense = v.findViewById<MaterialButton>(R.id.btnAddCatExpense)
        val btnIncome = v.findViewById<MaterialButton>(R.id.btnAddCatIncome)

        var icon = "shopping_cart"
        var isIncomeCategory = isIncome
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
        btnExpense.setOnClickListener { isIncomeCategory = false; updateTypeVisuals(true) }
        btnIncome.setOnClickListener { isIncomeCategory = true; updateTypeVisuals(false) }

        // Pro: allow subcategory creation via parentId (picked from top-level list)
        var selectedParentId: Long? = null
        val extra = v.findViewById<LinearLayout>(R.id.layoutCategoryExtra)
        val pad = (12 * resources.displayMetrics.density).toInt()
        val tvParent = TextView(requireContext()).apply {
            text = "Üst kategori: (seçilmedi)"
            setTextColor(ContextCompat.getColor(requireContext(), R.color.text_secondary))
            textSize = 12f
            visibility = View.GONE
            setPadding(0, pad, 0, 0)
        }
        val cbSub = if (proEnabled) CheckBox(requireContext()).apply {
            text = "Alt kategori ekle (PRO)"
        } else null
        if (cbSub != null) {
            extra.visibility = View.VISIBLE
            extra.addView(cbSub)
            extra.addView(tvParent)
            cbSub.setOnCheckedChangeListener { _, checked ->
                tvParent.visibility = if (checked) View.VISIBLE else View.GONE
                if (!checked) {
                    selectedParentId = null
                    tvParent.text = "Üst kategori: (seçilmedi)"
                }
            }
            tvParent.setOnClickListener {
                val parents = viewModel.uiState.value.categories
                    .filter { it.parentId == null && !it.isLocked && !it.name.equals("Diğer", true) }
                    .sortedBy { it.name.lowercase() }
                if (parents.isEmpty()) {
                    toast("Üst kategori bulunamadı")
                    return@setOnClickListener
                }
                val labels = parents.map { it.name }.toTypedArray()
                AlertDialog.Builder(requireContext())
                    .setTitle("Üst kategori seç")
                    .setItems(labels) { _, which ->
                        val p = parents[which]
                        selectedParentId = p.id
                        tvParent.text = "Üst kategori: ${p.name}"
                    }
                    .show()
            }
        }

        val dialog = Dialog(requireContext())
        dialog.setContentView(v)
        dialog.window?.setLayout((resources.displayMetrics.widthPixels * 0.92).toInt(), ViewGroup.LayoutParams.WRAP_CONTENT)
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)

        v.findViewById<View>(R.id.btnCancel).setOnClickListener { dialog.dismiss() }
        v.findViewById<View>(R.id.btnCloseProfileCategorySheet).setOnClickListener { dialog.dismiss() }
        v.findViewById<View>(R.id.btnAdd).setOnClickListener {
            val name = etName.text?.toString()?.trim().orEmpty()
            if (name.isBlank()) {
                til.error = getString(R.string.categories_name_required)
                return@setOnClickListener
            }
            val wantsSub = proEnabled && cbSub?.isChecked == true
            val parentId = if (wantsSub) selectedParentId else null
            if (wantsSub && parentId == null) {
                toast("Üst kategori seçmelisin")
                return@setOnClickListener
            }
            viewModel.addCategory(
                name = name,
                icon = icon,
                color = selectedColor,
                isIncome = isIncomeCategory,
                parentId = parentId,
                isPro = proEnabled
            )
            dialog.dismiss()
        }
        dialog.show()
    }

    private fun showEditDialog(category: Category) {
        if (category.isLocked || category.name.equals("Diğer", ignoreCase = true)) {
            toast("Diğer kategorisi düzenlenemez")
            return
        }
        val v = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_profile_edit_category, null)
        val tvSel = v.findViewById<TextView>(R.id.tvSelectedEmoji)
        val etName = v.findViewById<TextInputEditText>(R.id.etCategoryName)
        val til = v.findViewById<com.google.android.material.textfield.TextInputLayout>(R.id.tilCategoryName)
        val btnExpense = v.findViewById<MaterialButton>(R.id.btnAddCatExpense)
        val btnIncome = v.findViewById<MaterialButton>(R.id.btnAddCatIncome)

        var icon = category.icon.ifBlank { "shopping_cart" }
        var isIncomeCategory = category.isIncome
        var selectedColor = CategoryColorPalette.closestOrDefault(category.color)

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
        etName.setText(category.name)
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
        btnExpense.setOnClickListener { isIncomeCategory = false; updateTypeVisuals(true) }
        btnIncome.setOnClickListener { isIncomeCategory = true; updateTypeVisuals(false) }

        val dialog = Dialog(requireContext())
        dialog.setContentView(v)
        dialog.window?.setLayout((resources.displayMetrics.widthPixels * 0.92).toInt(), ViewGroup.LayoutParams.WRAP_CONTENT)
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)

        v.findViewById<View>(R.id.btnCancel).setOnClickListener { dialog.dismiss() }
        v.findViewById<View>(R.id.btnCloseProfileCategorySheet).setOnClickListener { dialog.dismiss() }
        v.findViewById<View>(R.id.btnSave).setOnClickListener {
            val name = etName.text?.toString()?.trim().orEmpty()
            if (name.isBlank()) {
                til.error = getString(R.string.categories_name_required)
                return@setOnClickListener
            }
            viewModel.updateCategory(
                category = category,
                newName = name,
                newIcon = icon,
                newColor = selectedColor,
                newIsIncome = isIncomeCategory
            )
            dialog.dismiss()
        }
        dialog.show()
    }

    private fun toast(msg: String) = Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()
}

