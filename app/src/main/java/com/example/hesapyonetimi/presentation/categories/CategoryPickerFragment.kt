package com.example.hesapyonetimi.presentation.categories

import android.os.Bundle
import android.app.AlertDialog
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.hesapyonetimi.R
import com.example.hesapyonetimi.auth.AuthPrefs
import com.example.hesapyonetimi.domain.model.Category
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        isIncome = arguments?.getBoolean(ARG_IS_INCOME) ?: false
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return inflater.inflate(R.layout.fragment_category_picker, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

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
            val dimWhite = 0x80FFFFFF.toInt()
            if (!isIncome) {
                btnExpense.setBackgroundResource(R.drawable.toggle_pill_selected)
                btnExpense.setTextColor(green)
                btnIncome.background = null
                btnIncome.setTextColor(dimWhite)
            } else {
                btnIncome.setBackgroundResource(R.drawable.toggle_pill_selected)
                btnIncome.setTextColor(green)
                btnExpense.background = null
                btnExpense.setTextColor(dimWhite)
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
        parentFragmentManager.setFragmentResult(
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
        val pad = (16 * resources.displayMetrics.density).toInt()

        val nameEt = EditText(requireContext()).apply {
            hint = "Kategori adı"
            setSingleLine(true)
        }
        val iconEt = EditText(requireContext()).apply {
            hint = "Emoji (örn: 🛒)"
            setSingleLine(true)
        }

        val container = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, pad)
            addView(nameEt)
            addView(iconEt)
        }

        // Pro: allow subcategory creation via parentId (picked from top-level list)
        var selectedParentId: Long? = null
        val tvParent = TextView(requireContext()).apply {
            text = "Üst kategori: (seçilmedi)"
            setTextColor(ContextCompat.getColor(requireContext(), R.color.text_secondary))
            textSize = 12f
            visibility = View.GONE
            setPadding(0, pad / 2, 0, 0)
        }
        val cbSub = if (proEnabled) CheckBox(requireContext()).apply {
            text = "Alt kategori ekle (PRO)"
        } else null

        if (cbSub != null) {
            container.addView(cbSub)
            container.addView(tvParent)
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
                val labels = parents.map { "${it.icon} ${it.name}" }.toTypedArray()
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

        AlertDialog.Builder(requireContext())
            .setTitle("Kategori ekle")
            .setView(container)
            .setNegativeButton("İptal", null)
            .setPositiveButton("Ekle") { _, _ ->
                val name = nameEt.text?.toString().orEmpty()
                val icon = iconEt.text?.toString().orEmpty()
                val color = if (isIncome) "#4CAF50" else "#4CAF50"
                val wantsSub = proEnabled && cbSub?.isChecked == true
                val parentId = if (wantsSub) selectedParentId else null
                if (wantsSub && parentId == null) {
                    toast("Üst kategori seçmelisin")
                    return@setPositiveButton
                }
                viewModel.addCategory(
                    name = name,
                    icon = icon,
                    color = color,
                    isIncome = isIncome,
                    parentId = parentId,
                    isPro = proEnabled
                )
            }
            .show()
    }

    private fun showEditDialog(category: Category) {
        if (category.isLocked || category.name.equals("Diğer", ignoreCase = true)) {
            toast("Diğer kategorisi düzenlenemez")
            return
        }
        val pad = (16 * resources.displayMetrics.density).toInt()
        val nameEt = EditText(requireContext()).apply {
            hint = "Kategori adı"
            setSingleLine(true)
            setText(category.name)
        }
        val iconEt = EditText(requireContext()).apply {
            hint = "Emoji"
            setSingleLine(true)
            setText(category.icon)
        }
        val container = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, pad)
            addView(nameEt)
            addView(iconEt)
        }
        AlertDialog.Builder(requireContext())
            .setTitle("Kategori düzenle")
            .setView(container)
            .setNegativeButton("İptal", null)
            .setPositiveButton("Kaydet") { _, _ ->
                viewModel.updateCategory(
                    category = category,
                    newName = nameEt.text?.toString().orEmpty(),
                    newIcon = iconEt.text?.toString().orEmpty(),
                    newColor = category.color
                )
            }
            .show()
    }

    private fun toast(msg: String) = Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()
}

