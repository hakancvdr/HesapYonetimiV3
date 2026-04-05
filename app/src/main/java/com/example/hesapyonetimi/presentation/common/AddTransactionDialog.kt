package com.example.hesapyonetimi.presentation.common

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.hesapyonetimi.R
import com.example.hesapyonetimi.presentation.transactions.TransactionViewModel
import com.google.android.material.textfield.TextInputEditText
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class AddTransactionDialog : DialogFragment() {
    
    private val viewModel: TransactionViewModel by viewModels()
    
    private var selectedCategoryId: Long = 0
    private var isIncome: Boolean = false
    private var selectedCategoryName: String = ""
    
    private var preSelectedType: Boolean? = null
    
    companion object {
        private const val ARG_IS_INCOME = "arg_is_income"
        
        fun newInstance(isIncome: Boolean?): AddTransactionDialog {
            return AddTransactionDialog().apply {
                arguments = Bundle().apply {
                    isIncome?.let { putBoolean(ARG_IS_INCOME, it) }
                }
            }
        }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        preSelectedType = arguments?.getBoolean(ARG_IS_INCOME)
    }
    
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.dialog_add_transaction, container, false)
    }
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        val etAmount = view.findViewById<TextInputEditText>(R.id.etAmount)
        val etDescription = view.findViewById<TextInputEditText>(R.id.etDescription)
        val actvCategory = view.findViewById<AutoCompleteTextView>(R.id.actvCategory)
        val toggleGroupType = view.findViewById<com.google.android.material.button.MaterialButtonToggleGroup>(R.id.toggleGroupType)
        val btnExpense = view.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnExpense)
        val btnIncome = view.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnIncome)
        val btnSave = view.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnSave)
        val btnCancel = view.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnCancel)
        val ivClose = view.findViewById<ImageView>(R.id.ivClose)

        val colorGreen = androidx.core.content.ContextCompat.getColor(requireContext(), R.color.green_primary)
        val colorWhite = androidx.core.content.ContextCompat.getColor(requireContext(), R.color.text_white)
        val colorTextPrimary = androidx.core.content.ContextCompat.getColor(requireContext(), R.color.text_primary)

        fun updateToggleVisuals(checkedId: Int) {
            val expenseSelected = checkedId == R.id.btnExpense
            btnExpense.backgroundTintList = androidx.core.content.res.ResourcesCompat.getColorStateList(
                resources, if (expenseSelected) R.color.green_primary else android.R.color.transparent, null)
            btnExpense.setTextColor(if (expenseSelected) colorWhite else colorTextPrimary)

            btnIncome.backgroundTintList = androidx.core.content.res.ResourcesCompat.getColorStateList(
                resources, if (!expenseSelected) R.color.green_primary else android.R.color.transparent, null)
            btnIncome.setTextColor(if (!expenseSelected) colorWhite else colorTextPrimary)
        }

        // Pre-select income/expense if specified
        preSelectedType?.let { income ->
            if (income) {
                toggleGroupType.check(R.id.btnIncome)
                updateToggleVisuals(R.id.btnIncome)
            } else {
                toggleGroupType.check(R.id.btnExpense)
                updateToggleVisuals(R.id.btnExpense)
            }
            isIncome = income
        } ?: run {
            // Default to expense
            toggleGroupType.check(R.id.btnExpense)
            updateToggleVisuals(R.id.btnExpense)
            isIncome = false
        }

        observeCategories(actvCategory, toggleGroupType)

        toggleGroupType.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) {
                isIncome = checkedId == R.id.btnIncome
                updateCategoryDropdown(actvCategory, isIncome)
                updateToggleVisuals(checkedId)
            }
        }
        
        ivClose.setOnClickListener {
            dismiss()
        }
        
        btnSave.setOnClickListener {
            val amountStr = etAmount.text.toString()
            val description = etDescription.text.toString()
            
            if (amountStr.isEmpty()) {
                Toast.makeText(requireContext(), "Lütfen tutar girin", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            
            if (selectedCategoryName.equals("Diğer", ignoreCase = true) && description.isEmpty()) {
                Toast.makeText(requireContext(), "Lütfen açıklama girin (Diğer kategorisinde zorunlu)", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            
            if (selectedCategoryId == 0L) {
                Toast.makeText(requireContext(), "Lütfen kategori seçin", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            
            val amount = amountStr.toDoubleOrNull() ?: 0.0
            val finalDescription = if (description.isEmpty()) selectedCategoryName else description
            
            viewModel.addTransaction(
                amount = amount,
                categoryId = selectedCategoryId,
                description = finalDescription,
                date = System.currentTimeMillis(),
                isIncome = isIncome
            )
            
            Toast.makeText(requireContext(), "✅ İşlem eklendi!", Toast.LENGTH_SHORT).show()
            dismiss()
        }
        
        btnCancel.setOnClickListener {
            dismiss()
        }
    }
    
    private fun observeCategories(dropdown: AutoCompleteTextView, toggleGroup: com.google.android.material.button.MaterialButtonToggleGroup) {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    if (state.categories.isNotEmpty()) {
                        val isIncomeSelected = toggleGroup.checkedButtonId == R.id.btnIncome
                        updateCategoryDropdown(dropdown, isIncomeSelected)
                    }
                }
            }
        }
    }
    
    private fun updateCategoryDropdown(dropdown: AutoCompleteTextView, isIncome: Boolean) {
        val categories = if (isIncome) {
            viewModel.getIncomeCategories()
        } else {
            viewModel.getExpenseCategories()
        }
        
        if (categories.isEmpty()) {
            return
        }
        
        val categoryNames = categories.map { "${it.icon} ${it.name}" }
        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, categoryNames)
        dropdown.setAdapter(adapter)
        
        dropdown.setOnItemClickListener { _, _, position, _ ->
            selectedCategoryId = categories[position].id
            selectedCategoryName = categories[position].name
        }
        
        if (categories.isNotEmpty()) {
            selectedCategoryId = categories[0].id
            selectedCategoryName = categories[0].name
            dropdown.setText(categoryNames[0], false)
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
