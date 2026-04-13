package com.example.hesapyonetimi.presentation.common

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.ImageButton
import android.widget.Toast
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.hesapyonetimi.R
import com.example.hesapyonetimi.adapter.DashboardCategoryFlowAdapter
import com.example.hesapyonetimi.adapter.WalletChipAdapter
import com.example.hesapyonetimi.auth.AuthPrefs
import com.example.hesapyonetimi.data.local.dao.WalletDao
import com.example.hesapyonetimi.domain.model.Category
import com.example.hesapyonetimi.presentation.categories.CategoryPickerFragment
import com.example.hesapyonetimi.presentation.categories.CategorySlotBuilder
import com.example.hesapyonetimi.presentation.transactions.TransactionViewModel
import com.google.android.flexbox.AlignItems
import com.google.android.flexbox.FlexDirection
import com.google.android.flexbox.FlexWrap
import com.google.android.flexbox.FlexboxLayoutManager
import com.google.android.flexbox.JustifyContent
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.button.MaterialButton
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class AddTransactionDashboardDialog : BottomSheetDialogFragment() {

    @Inject
    lateinit var walletDao: WalletDao

    private val viewModel: TransactionViewModel by viewModels()
    private var selectedCategory: Category? = null
    private var isIncome: Boolean = false
    private var preSelectedType: Boolean? = null
    private lateinit var categoryAdapter: DashboardCategoryFlowAdapter
    private var selectedWalletId: Long? = null

    companion object {
        private const val ARG_IS_INCOME = "arg_is_income"

        fun newInstance(isIncome: Boolean?): AddTransactionDashboardDialog {
            return AddTransactionDashboardDialog().apply {
                arguments = Bundle().apply { isIncome?.let { putBoolean(ARG_IS_INCOME, it) } }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        preSelectedType = arguments?.getBoolean(ARG_IS_INCOME)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.dialog_add_transaction_dashboard, container, false)
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): android.app.Dialog {
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
        val statusBarHeight = requireContext().resources.getDimensionPixelSize(
            requireContext().resources.getIdentifier("status_bar_height", "dimen", "android")
        )
        (requireView().parent as android.view.View).setPadding(0, statusBarHeight, 0, 0)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        if (savedInstanceState == null) {
            selectedCategory = null
        }

        val isPro = AuthPrefs.isProMember(requireContext())
        val walletSection = view.findViewById<View>(R.id.walletSectionDash)
        val rvWallets = view.findViewById<RecyclerView>(R.id.rvDashWallets)
        rvWallets.layoutManager = LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)

        if (isPro) {
            walletSection?.visibility = View.VISIBLE
            viewLifecycleOwner.lifecycleScope.launch {
                viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                    walletDao.getAllWallets().collect { wallets ->
                        val walletAdapter = WalletChipAdapter(wallets, selectedWalletId) { wallet ->
                            selectedWalletId = wallet.id
                            rvWallets.adapter?.notifyDataSetChanged()
                        }
                        rvWallets.adapter = walletAdapter
                        if (selectedWalletId == null) {
                            selectedWalletId = wallets.firstOrNull { it.isDefault }?.id ?: wallets.firstOrNull()?.id
                        }
                    }
                }
            }
        } else {
            walletSection?.visibility = View.GONE
            viewLifecycleOwner.lifecycleScope.launch {
                if (selectedWalletId == null) {
                    selectedWalletId = walletDao.getDefaultWallet()?.id
                }
            }
        }

        val etAmount = view.findViewById<EditText>(R.id.etDashAmount)
        val etDescription = view.findViewById<EditText>(R.id.etDashDescription)
        val rvCategories = view.findViewById<RecyclerView>(R.id.rvDashCategories)
        val btnClose = view.findViewById<ImageButton>(R.id.btnCloseDashboardSheet)
        val btnExpense = view.findViewById<MaterialButton>(R.id.btnDashExpense)
        val btnIncome = view.findViewById<MaterialButton>(R.id.btnDashIncome)
        val btnSave = view.findViewById<MaterialButton>(R.id.btnDashSave)

        val colorWhite = androidx.core.content.ContextCompat.getColor(requireContext(), R.color.text_white)
        val colorTextPrimary = androidx.core.content.ContextCompat.getColor(requireContext(), R.color.text_primary)

        fun hideKeyboard() {
            val imm = requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.hideSoftInputFromWindow(view.windowToken, 0)
        }

        fun updateSaveLabel() {
            btnSave.text = getString(
                if (isIncome) R.string.gunluk_add_income else R.string.gunluk_add_expense
            )
        }

        fun updateToggleVisuals(expenseSelected: Boolean) {
            if (expenseSelected) {
                btnExpense.backgroundTintList = androidx.core.content.res.ResourcesCompat.getColorStateList(
                    resources, R.color.green_primary, null
                )
                btnExpense.setTextColor(colorWhite)
                btnIncome.backgroundTintList = androidx.core.content.res.ResourcesCompat.getColorStateList(
                    resources, android.R.color.transparent, null
                )
                btnIncome.setTextColor(colorTextPrimary)
            } else {
                btnIncome.backgroundTintList = androidx.core.content.res.ResourcesCompat.getColorStateList(
                    resources, R.color.green_primary, null
                )
                btnIncome.setTextColor(colorWhite)
                btnExpense.backgroundTintList = androidx.core.content.res.ResourcesCompat.getColorStateList(
                    resources, android.R.color.transparent, null
                )
                btnExpense.setTextColor(colorTextPrimary)
            }
            updateSaveLabel()
        }

        categoryAdapter = DashboardCategoryFlowAdapter(
            emptyList(),
            onSelected = { cat -> selectedCategory = cat },
            onAddClick = {
                findNavController().navigate(
                    R.id.categoryPickerFragment,
                    CategoryPickerFragment.args(isIncome = isIncome)
                )
            },
            parentNameResolver = { parentId ->
                viewModel.uiState.value.categories.firstOrNull { it.id == parentId }?.name
            }
        )
        rvCategories.layoutManager = FlexboxLayoutManager(requireContext()).apply {
            flexDirection = FlexDirection.ROW
            flexWrap = FlexWrap.WRAP
            justifyContent = JustifyContent.FLEX_START
            alignItems = AlignItems.CENTER
        }
        rvCategories.adapter = categoryAdapter

        fun loadCategories() {
            val sel = selectedCategory?.takeIf { it.isIncome == isIncome }
            if (sel == null) selectedCategory = null

            val includeSubcats = AuthPrefs.isProMember(requireContext())
            val top = viewModel.getTopCategories(
                isIncome = isIncome,
                includeSubcategories = includeSubcats,
                limit = 6
            )
            val cats = CategorySlotBuilder.buildFixedSlots(
                top = top,
                selected = sel,
                limit = 6
            )
            categoryAdapter.setCategories(cats, "")
            sel?.let { categoryAdapter.setSelected(it.id) }
        }

        requireActivity().supportFragmentManager.setFragmentResultListener(
            CategoryPickerFragment.RESULT_KEY,
            viewLifecycleOwner
        ) { _, bundle ->
            val categoryId = bundle.getLong(CategoryPickerFragment.BUNDLE_CATEGORY_ID, -1L)
            if (categoryId <= 0L) return@setFragmentResultListener
            val picked = viewModel.uiState.value.categories.firstOrNull { it.id == categoryId }
                ?: return@setFragmentResultListener
            isIncome = picked.isIncome
            selectedCategory = picked
            updateToggleVisuals(!isIncome)

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

        preSelectedType?.let { isIncome = it } ?: run { isIncome = false }
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

        btnClose.setOnClickListener {
            hideKeyboard()
            dismiss()
        }

        var didInitialCategoryLoad = false
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    if (state.categories.isNotEmpty() && !didInitialCategoryLoad) {
                        loadCategories()
                        didInitialCategoryLoad = true
                    }
                }
            }
        }

        etAmount.setOnEditorActionListener { _, _, _ -> hideKeyboard(); true }
        etDescription.setOnEditorActionListener { _, _, _ -> hideKeyboard(); true }

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
                Toast.makeText(requireContext(), "\"Diğer\" kategorisinde açıklama zorunlu", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val amount = amountStr.toDoubleOrNull() ?: 0.0
            viewModel.addTransaction(
                amount = amount,
                categoryId = cat.id,
                description = if (description.isEmpty()) cat.name else description,
                date = System.currentTimeMillis(),
                isIncome = isIncome,
                walletId = selectedWalletId,
                tags = "",
                tagIds = emptyList()
            )
            Toast.makeText(requireContext(), "✅ İşlem eklendi!", Toast.LENGTH_SHORT).show()
            dismiss()
        }
    }
}
