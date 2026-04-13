package com.example.hesapyonetimi.presentation.common

import android.app.AlertDialog
import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import com.google.android.material.snackbar.Snackbar
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.hesapyonetimi.R
import com.example.hesapyonetimi.adapter.CategoryChipAdapter
import com.example.hesapyonetimi.adapter.WalletChipAdapter
import com.example.hesapyonetimi.data.local.dao.WalletDao
import com.example.hesapyonetimi.domain.model.Category
import com.example.hesapyonetimi.domain.model.Transaction
import javax.inject.Inject
import com.example.hesapyonetimi.presentation.transactions.TransactionViewModel
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import android.os.Build
import androidx.core.os.bundleOf
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import androidx.navigation.fragment.findNavController
import com.example.hesapyonetimi.auth.AuthPrefs
import com.example.hesapyonetimi.presentation.categories.CategoryPickerFragment
import com.example.hesapyonetimi.presentation.categories.CategorySlotBuilder

@AndroidEntryPoint
class EditTransactionSheet : BottomSheetDialogFragment() {

    @Inject
    lateinit var walletDao: WalletDao

    /** Activity scope: sheet dismiss sonrası Snackbar "Geri Al" güvenli kalır */
    private val viewModel: TransactionViewModel by activityViewModels()
    private lateinit var categoryAdapter: CategoryChipAdapter
    private var selectedCategory: Category? = null
    private var isIncome: Boolean = false
    private lateinit var transaction: Transaction
    private var selectedWalletId: Long? = null

    companion object {
        private const val ARG_TX = "arg_transaction"

        fun newInstance(transaction: Transaction): EditTransactionSheet {
            return EditTransactionSheet().apply {
                arguments = bundleOf(ARG_TX to transaction)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val fromState = readTx(savedInstanceState)
        val fromArgs = readTx(arguments)
        transaction = fromState ?: fromArgs
            ?: throw IllegalStateException("EditTransactionSheet requires transaction argument")
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        if (::transaction.isInitialized) {
            outState.putParcelable(ARG_TX, transaction)
        }
    }

    private fun readTx(bundle: Bundle?): Transaction? {
        if (bundle == null) return null
        return if (Build.VERSION.SDK_INT >= 33) {
            bundle.getParcelable(ARG_TX, Transaction::class.java)
        } else {
            @Suppress("DEPRECATION")
            bundle.getParcelable(ARG_TX) as? Transaction
        }
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
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.bottom_sheet_edit_transaction, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val etAmount      = view.findViewById<TextInputEditText>(R.id.etEditAmount)
        val etDescription = view.findViewById<TextInputEditText>(R.id.etEditDescription)
        val rvCategories  = view.findViewById<RecyclerView>(R.id.rvEditCategories)
        view.findViewById<View>(R.id.tvEditCategoriesLink)?.setOnClickListener {
            findNavController().navigate(
                R.id.categoryPickerFragment,
                CategoryPickerFragment.args(isIncome = isIncome)
            )
        }
        val btnExpense    = view.findViewById<MaterialButton>(R.id.btnEditExpense)
        val btnIncome     = view.findViewById<MaterialButton>(R.id.btnEditIncome)
        val btnSave       = view.findViewById<MaterialButton>(R.id.btnEditSave)
        val btnDelete     = view.findViewById<MaterialButton>(R.id.btnDelete)

        val colorGreen       = androidx.core.content.ContextCompat.getColor(requireContext(), R.color.green_primary)
        val colorWhite       = androidx.core.content.ContextCompat.getColor(requireContext(), R.color.text_white)
        val colorTextPrimary = androidx.core.content.ContextCompat.getColor(requireContext(), R.color.text_primary)

        // Mevcut değerleri doldur
        isIncome = transaction.isIncome
        selectedWalletId = transaction.walletId

        val rvEditWallets = view.findViewById<RecyclerView>(R.id.rvEditWallets)
        rvEditWallets.layoutManager = LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                walletDao.getAllWallets().collect { wallets ->
                    rvEditWallets.adapter = WalletChipAdapter(wallets, selectedWalletId) { wallet ->
                        selectedWalletId = wallet.id
                        rvEditWallets.adapter?.notifyDataSetChanged()
                    }
                    if (selectedWalletId == null) {
                        selectedWalletId = wallets.firstOrNull { it.isDefault }?.id ?: wallets.firstOrNull()?.id
                    }
                }
            }
        }
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
        categoryAdapter = CategoryChipAdapter(
            emptyList(),
            onSelected = { cat -> selectedCategory = cat },
            parentNameResolver = { parentId ->
                viewModel.uiState.value.categories.firstOrNull { it.id == parentId }?.name
            }
        )
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
            val includeSubcats = AuthPrefs.isProMember(requireContext())
            val top = viewModel.getTopCategories(
                isIncome = isIncome,
                includeSubcategories = includeSubcats,
                limit = 6
            )
            val all = if (isIncome) viewModel.getIncomeCategories() else viewModel.getExpenseCategories()
            val currentAll = all.firstOrNull { it.id == transaction.categoryId }
            val cats = CategorySlotBuilder.buildFixedSlots(
                top = top,
                selected = currentAll,
                limit = 6
            )
            val currentCat = cats.firstOrNull { it.id == transaction.categoryId } ?: cats.firstOrNull()
            val defaultName = if (isIncome) "Maaş" else "Market"
            categoryAdapter.setCategories(cats, currentCat?.name ?: defaultName)
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

        parentFragmentManager.setFragmentResultListener(
            CategoryPickerFragment.RESULT_KEY,
            viewLifecycleOwner
        ) { _, bundle ->
            val categoryId = bundle.getLong(CategoryPickerFragment.BUNDLE_CATEGORY_ID, -1L)
            if (categoryId <= 0L) return@setFragmentResultListener
            val picked = viewModel.uiState.value.categories.firstOrNull { it.id == categoryId }
                ?: return@setFragmentResultListener
            isIncome = picked.isIncome
            selectedCategory = picked
            updateToggleVisuals()

            val includeSubcats = AuthPrefs.isProMember(requireContext())
            val top = viewModel.getTopCategories(
                isIncome = isIncome,
                includeSubcategories = includeSubcats,
                limit = 6
            )
            val list = CategorySlotBuilder.buildFixedSlots(
                top = top,
                selected = picked,
                limit = 6
            )
            categoryAdapter.setCategories(list, "")
            categoryAdapter.setSelected(picked.id)
        }

        etAmount.setOnEditorActionListener { _, _, _ -> hideKeyboard(); true }

        // Sil — Geri Al Snackbar ile
        btnDelete.setOnClickListener {
            val deletedTransaction = transaction
            viewModel.deleteTransaction(deletedTransaction)
            dismiss()
            val rootView = requireActivity().window.decorView.rootView
            Snackbar.make(rootView, "İşlem silindi", Snackbar.LENGTH_LONG)
                .setAction("Geri Al") {
                    viewModel.addTransaction(deletedTransaction)
                }
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
                    date = selectedDateMillis,
                    walletId = selectedWalletId
                )
            )
            toast("✅ İşlem güncellendi!")
            dismiss()
        }
    }

    private fun toast(msg: String) = Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()
}
