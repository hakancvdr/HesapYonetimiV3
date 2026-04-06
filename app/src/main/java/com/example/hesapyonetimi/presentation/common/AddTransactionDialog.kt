package com.example.hesapyonetimi.presentation.common

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.*
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.hesapyonetimi.R
import com.example.hesapyonetimi.adapter.CategoryChipAdapter
import com.example.hesapyonetimi.domain.model.Category
import com.example.hesapyonetimi.presentation.transactions.TransactionViewModel
import com.google.android.material.textfield.TextInputEditText
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class AddTransactionDialog : DialogFragment() {

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

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val etAmount      = view.findViewById<TextInputEditText>(R.id.etAmount)
        val etDescription = view.findViewById<TextInputEditText>(R.id.etDescription)
        val rvCategories  = view.findViewById<RecyclerView>(R.id.rvDialogCategories)
        val toggleGroup   = view.findViewById<com.google.android.material.button.MaterialButtonToggleGroup>(R.id.toggleGroupType)
        val btnExpense    = view.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnExpense)
        val btnIncome     = view.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnIncome)
        val btnSave       = view.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnSave)
        val btnCancel     = view.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnCancel)
        val ivClose       = view.findViewById<ImageView>(R.id.ivClose)

        val colorGreen       = androidx.core.content.ContextCompat.getColor(requireContext(), R.color.green_primary)
        val colorWhite       = androidx.core.content.ContextCompat.getColor(requireContext(), R.color.text_white)
        val colorTextPrimary = androidx.core.content.ContextCompat.getColor(requireContext(), R.color.text_primary)

        // Kategori chip adapter
        categoryAdapter = CategoryChipAdapter(emptyList()) { cat -> selectedCategory = cat }
        rvCategories.layoutManager = LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
        rvCategories.adapter = categoryAdapter

        fun hideKeyboard() {
            val imm = requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.hideSoftInputFromWindow(view.windowToken, 0)
        }

        fun updateToggleVisuals(checkedId: Int) {
            val expenseSelected = checkedId == R.id.btnExpense
            btnExpense.backgroundTintList = androidx.core.content.res.ResourcesCompat.getColorStateList(
                resources, if (expenseSelected) R.color.green_primary else android.R.color.transparent, null)
            btnExpense.setTextColor(if (expenseSelected) colorWhite else colorTextPrimary)
            btnIncome.backgroundTintList = androidx.core.content.res.ResourcesCompat.getColorStateList(
                resources, if (!expenseSelected) R.color.green_primary else android.R.color.transparent, null)
            btnIncome.setTextColor(if (!expenseSelected) colorWhite else colorTextPrimary)
        }

        fun loadCategories() {
            val defaultName = if (isIncome) "Maaş" else "Market"
            val cats = if (isIncome) viewModel.getIncomeCategories() else viewModel.getExpenseCategories()
            categoryAdapter.setCategories(cats, defaultName)
        }

        // Toggle başlangıç
        preSelectedType?.let { income ->
            val id = if (income) R.id.btnIncome else R.id.btnExpense
            toggleGroup.check(id)
            updateToggleVisuals(id)
            isIncome = income
        } ?: run {
            toggleGroup.check(R.id.btnExpense)
            updateToggleVisuals(R.id.btnExpense)
            isIncome = false
        }

        // Kategorileri yükle
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    if (state.categories.isNotEmpty() && categoryAdapter.itemCount == 0) {
                        loadCategories()
                    }
                }
            }
        }

        toggleGroup.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) {
                isIncome = checkedId == R.id.btnIncome
                updateToggleVisuals(checkedId)
                loadCategories()
            }
        }

        etAmount.setOnEditorActionListener { _, _, _ -> hideKeyboard(); true }
        ivClose.setOnClickListener { dismiss() }
        btnCancel.setOnClickListener { dismiss() }

        btnSave.setOnClickListener {
            hideKeyboard()
            val amountStr = etAmount.text.toString()
            if (amountStr.isEmpty()) {
                Toast.makeText(requireContext(), "Lütfen tutar girin", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val cat = selectedCategory ?: run {
                Toast.makeText(requireContext(), "Lütfen kategori seçin", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val description = etDescription.text.toString()
            if (cat.name.equals("Diğer", ignoreCase = true) && description.isEmpty()) {
                Toast.makeText(requireContext(), "Diğer kategorisinde açıklama zorunlu", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val amount = amountStr.toDoubleOrNull() ?: 0.0
            val finalDescription = if (description.isEmpty()) cat.name else description
            viewModel.addTransaction(
                amount = amount,
                categoryId = cat.id,
                description = finalDescription,
                date = System.currentTimeMillis(),
                isIncome = isIncome
            )
            Toast.makeText(requireContext(), "✅ İşlem eklendi!", Toast.LENGTH_SHORT).show()
            dismiss()
        }
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.apply {
            setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            setBackgroundDrawableResource(android.R.color.transparent)
        }
    }
}
