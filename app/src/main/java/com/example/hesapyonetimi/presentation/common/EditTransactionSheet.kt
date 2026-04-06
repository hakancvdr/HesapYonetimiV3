package com.example.hesapyonetimi.presentation.common

import android.app.AlertDialog
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
import com.example.hesapyonetimi.domain.model.Transaction
import com.example.hesapyonetimi.presentation.transactions.TransactionViewModel
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class EditTransactionSheet : BottomSheetDialogFragment() {

    private val viewModel: TransactionViewModel by viewModels()
    private lateinit var categoryAdapter: CategoryChipAdapter
    private var selectedCategory: Category? = null
    private var isIncome: Boolean = false
    private lateinit var transaction: Transaction

    companion object {
        private const val ARG_TRANSACTION_ID = "transaction_id"

        fun newInstance(transaction: Transaction): EditTransactionSheet {
            return EditTransactionSheet().apply {
                this.transaction = transaction
                arguments = Bundle().apply {
                    putLong(ARG_TRANSACTION_ID, transaction.id)
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        val behavior = com.google.android.material.bottomsheet.BottomSheetBehavior.from(
            requireView().parent as android.view.View
        )
        behavior.state = com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_EXPANDED
        behavior.skipCollapsed = true
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.bottom_sheet_edit_transaction, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val etAmount      = view.findViewById<TextInputEditText>(R.id.etEditAmount)
        val etDescription = view.findViewById<TextInputEditText>(R.id.etEditDescription)
        val rvCategories  = view.findViewById<RecyclerView>(R.id.rvEditCategories)
        val btnExpense    = view.findViewById<MaterialButton>(R.id.btnEditExpense)
        val btnIncome     = view.findViewById<MaterialButton>(R.id.btnEditIncome)
        val btnSave       = view.findViewById<MaterialButton>(R.id.btnEditSave)
        val btnDelete     = view.findViewById<MaterialButton>(R.id.btnDelete)

        val colorGreen       = androidx.core.content.ContextCompat.getColor(requireContext(), R.color.green_primary)
        val colorWhite       = androidx.core.content.ContextCompat.getColor(requireContext(), R.color.text_white)
        val colorTextPrimary = androidx.core.content.ContextCompat.getColor(requireContext(), R.color.text_primary)

        // Mevcut değerleri doldur
        isIncome = transaction.isIncome
        etAmount.setText(transaction.amount.toBigDecimal().stripTrailingZeros().toPlainString())
        etDescription.setText(transaction.description)

        // Tarih
        var selectedDateMillis = transaction.date
        val tarihFormat = java.text.SimpleDateFormat("d MMMM yyyy, EEEE", java.util.Locale("tr"))
        val etEditDate = view.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.etEditDate)
        etEditDate.setText(tarihFormat.format(java.util.Date(selectedDateMillis)))

        val tarihClick = android.view.View.OnClickListener {
            val cal = java.util.Calendar.getInstance().apply { timeInMillis = selectedDateMillis }
            android.app.DatePickerDialog(requireContext(), { _, y, m, d ->
                val now = java.util.Calendar.getInstance()
                cal.set(y, m, d)
                cal.set(java.util.Calendar.HOUR_OF_DAY, now.get(java.util.Calendar.HOUR_OF_DAY))
                cal.set(java.util.Calendar.MINUTE, now.get(java.util.Calendar.MINUTE))
                selectedDateMillis = cal.timeInMillis
                etEditDate.setText(tarihFormat.format(java.util.Date(selectedDateMillis)))
            }, cal.get(java.util.Calendar.YEAR), cal.get(java.util.Calendar.MONTH), cal.get(java.util.Calendar.DAY_OF_MONTH)).show()
        }
        etEditDate.setOnClickListener(tarihClick)
        view.findViewById<com.google.android.material.textfield.TextInputLayout>(R.id.tilEditDate)
            .setEndIconOnClickListener(tarihClick)

        // Kategori adapter
        categoryAdapter = CategoryChipAdapter(emptyList()) { cat -> selectedCategory = cat }
        rvCategories.layoutManager = GridLayoutManager(requireContext(), 2)
        rvCategories.adapter = categoryAdapter

        fun hideKeyboard() {
            val imm = requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.hideSoftInputFromWindow(view.windowToken, 0)
        }

        fun updateToggleVisuals() {
            if (!isIncome) {
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
            val cats = if (isIncome) viewModel.getIncomeCategories() else viewModel.getExpenseCategories()
            // Mevcut kategoriyi seç
            val currentCat = cats.firstOrNull { it.id == transaction.categoryId } ?: cats.firstOrNull()
            categoryAdapter.setCategories(cats, currentCat?.name ?: "")
            selectedCategory = currentCat
        }

        updateToggleVisuals()

        btnExpense.setOnClickListener {
            isIncome = false; updateToggleVisuals(); loadCategories()
        }
        btnIncome.setOnClickListener {
            isIncome = true; updateToggleVisuals(); loadCategories()
        }

        // Kategoriler yüklenince doldur
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    if (state.categories.isNotEmpty() && categoryAdapter.itemCount == 0) {
                        loadCategories()
                    }
                }
            }
        }

        etAmount.setOnEditorActionListener { _, _, _ -> hideKeyboard(); true }

        // Sil
        btnDelete.setOnClickListener {
            AlertDialog.Builder(requireContext())
                .setTitle("İşlemi Sil")
                .setMessage("Bu işlemi silmek istediğinden emin misin?")
                .setPositiveButton("Sil") { _, _ ->
                    viewModel.deleteTransaction(transaction)
                    Toast.makeText(requireContext(), "İşlem silindi", Toast.LENGTH_SHORT).show()
                    dismiss()
                }
                .setNegativeButton("İptal", null)
                .show()
        }

        // Kaydet
        btnSave.setOnClickListener {
            hideKeyboard()
            val amountStr = etAmount.text.toString()
            if (amountStr.isEmpty()) { toast("Lütfen tutar girin"); return@setOnClickListener }
            val cat = selectedCategory ?: run { toast("Lütfen kategori seçin"); return@setOnClickListener }
            val description = etDescription.text.toString()
            val amount = amountStr.toDoubleOrNull() ?: 0.0

            viewModel.updateTransaction(
                transaction.copy(
                    amount = amount,
                    categoryId = cat.id,
                    description = if (description.isEmpty()) cat.name else description,
                    isIncome = isIncome,
                    date = selectedDateMillis
                )
            )
            toast("✅ İşlem güncellendi!")
            dismiss()
        }
    }

    private fun toast(msg: String) = Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()
}
