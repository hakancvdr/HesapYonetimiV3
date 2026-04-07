package com.example.hesapyonetimi.presentation.common

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.hesapyonetimi.R
import com.example.hesapyonetimi.adapter.CategoryChipAdapter
import com.example.hesapyonetimi.domain.model.Category
import com.example.hesapyonetimi.presentation.transactions.TransactionViewModel
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.button.MaterialButton
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.textfield.TextInputEditText
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class AddTransactionDialog : BottomSheetDialogFragment() {

    private val viewModel: TransactionViewModel by viewModels()
    private var selectedCategory: Category? = null
    private var isIncome: Boolean = false
    private var preSelectedType: Boolean? = null
    private lateinit var categoryAdapter: CategoryChipAdapter

    companion object {
        private const val ARG_IS_INCOME = "arg_is_income"
        fun newInstance(isIncome: Boolean?): AddTransactionDialog {
            return AddTransactionDialog().apply {
                arguments = Bundle().apply { isIncome?.let { putBoolean(ARG_IS_INCOME, it) } }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        preSelectedType = arguments?.getBoolean(ARG_IS_INCOME)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.dialog_add_transaction, container, false)
    }

    override fun onCreateDialog(savedInstanceState: android.os.Bundle?): android.app.Dialog {
        val dialog = super.onCreateDialog(savedInstanceState)
        dialog.window?.setSoftInputMode(
            android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
        )
        return dialog
    }

    override fun onStart() {
        super.onStart()
        val behavior = com.google.android.material.bottomsheet.BottomSheetBehavior.from(
            requireView().parent as android.view.View
        )
        behavior.state = com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_EXPANDED
        behavior.skipCollapsed = true
        // Status bar yüksekliği kadar padding ekle — sheet status bar arkasına girmesin
        val statusBarHeight = requireContext().resources.getDimensionPixelSize(
            requireContext().resources.getIdentifier("status_bar_height", "dimen", "android")
        )
        (requireView().parent as android.view.View).setPadding(0, statusBarHeight, 0, 0)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val etAmount      = view.findViewById<TextInputEditText>(R.id.etAmount)
        val etDescription = view.findViewById<TextInputEditText>(R.id.etDescription)
        val rvCategories  = view.findViewById<RecyclerView>(R.id.rvDialogCategories)
        // toggle LinearLayout — btnExpense ve btnIncome direkt kullanılıyor
        val btnExpense    = view.findViewById<MaterialButton>(R.id.btnExpense)
        val btnIncome     = view.findViewById<MaterialButton>(R.id.btnIncome)
        val btnSave       = view.findViewById<MaterialButton>(R.id.btnSave)
        val btnCancel     = view.findViewById<MaterialButton>(R.id.btnCancel)

        val colorGreen       = androidx.core.content.ContextCompat.getColor(requireContext(), R.color.green_primary)
        val colorWhite       = androidx.core.content.ContextCompat.getColor(requireContext(), R.color.text_white)
        val colorTextPrimary = androidx.core.content.ContextCompat.getColor(requireContext(), R.color.text_primary)

        // Kategori chip adapter
        categoryAdapter = CategoryChipAdapter(emptyList()) { cat -> selectedCategory = cat }
        rvCategories.layoutManager = GridLayoutManager(requireContext(), 2)
        rvCategories.adapter = categoryAdapter

        fun hideKeyboard() {
            val imm = requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.hideSoftInputFromWindow(view.windowToken, 0)
        }

        fun updateToggleVisuals(expenseSelected: Boolean) {
            if (expenseSelected) {
                btnExpense.backgroundTintList = androidx.core.content.res.ResourcesCompat.getColorStateList(
                    resources, R.color.green_primary, null)
                btnExpense.setTextColor(colorWhite)
                btnIncome.backgroundTintList = androidx.core.content.res.ResourcesCompat.getColorStateList(
                    resources, android.R.color.transparent, null)
                btnIncome.setTextColor(colorTextPrimary)
            } else {
                btnIncome.backgroundTintList = androidx.core.content.res.ResourcesCompat.getColorStateList(
                    resources, R.color.green_primary, null)
                btnIncome.setTextColor(colorWhite)
                btnExpense.backgroundTintList = androidx.core.content.res.ResourcesCompat.getColorStateList(
                    resources, android.R.color.transparent, null)
                btnExpense.setTextColor(colorTextPrimary)
            }
        }

        fun loadCategories() {
            val defaultName = if (isIncome) "Maaş" else "Market"
            val cats = if (isIncome) viewModel.getIncomeCategories() else viewModel.getExpenseCategories()
            categoryAdapter.setCategories(cats, defaultName)
        }

        // Başlangıç
        preSelectedType?.let {
            isIncome = it
        } ?: run { isIncome = false }
        updateToggleVisuals(!isIncome)

        btnExpense.setOnClickListener {
            isIncome = false
            updateToggleVisuals(true)
            loadCategories()
        }
        btnIncome.setOnClickListener {
            isIncome = true
            updateToggleVisuals(false)
            loadCategories()
        }

        // Kategorileri yükle
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    if (state.categories.isNotEmpty()) {
                        loadCategories()
                    }
                }
            }
        }

        etAmount.setOnEditorActionListener { _, _, _ -> hideKeyboard(); true }
        etDescription.setOnEditorActionListener { _, _, _ -> hideKeyboard(); true }
        btnCancel.setOnClickListener { dismiss() }

        btnSave.setOnClickListener {
            hideKeyboard()
            val amountStr = etAmount.text.toString()
            if (amountStr.isEmpty()) { toast("Lütfen tutar girin"); return@setOnClickListener }
            val cat = selectedCategory ?: run { toast("Lütfen kategori seçin"); return@setOnClickListener }
            val description = etDescription.text.toString()
            if (cat.name.equals("Diğer", ignoreCase = true) && description.isEmpty()) {
                toast("Diğer kategorisinde açıklama zorunlu"); return@setOnClickListener
            }
            val amount = amountStr.toDoubleOrNull() ?: 0.0
            viewModel.addTransaction(
                amount = amount,
                categoryId = cat.id,
                description = if (description.isEmpty()) cat.name else description,
                date = System.currentTimeMillis(),
                isIncome = isIncome
            )
            toast("✅ İşlem eklendi!")
            dismiss()
        }
    }

    private fun toast(msg: String) = Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()
}
