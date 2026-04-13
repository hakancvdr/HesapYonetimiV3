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
import com.example.hesapyonetimi.adapter.WalletChipAdapter
import com.example.hesapyonetimi.data.local.dao.WalletDao
import com.example.hesapyonetimi.data.local.entity.WalletEntity
import com.example.hesapyonetimi.domain.model.Category
import com.example.hesapyonetimi.domain.repository.CategoryRepository
import com.example.hesapyonetimi.presentation.transactions.TransactionViewModel
import javax.inject.Inject
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.button.MaterialButton
import com.google.android.material.chip.Chip
import com.google.android.material.textfield.TextInputEditText
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import android.widget.LinearLayout
import android.widget.TextView
import androidx.navigation.fragment.findNavController
import com.example.hesapyonetimi.auth.AuthPrefs
import com.example.hesapyonetimi.presentation.categories.CategoryPickerFragment
import com.example.hesapyonetimi.presentation.categories.CategorySlotBuilder

@AndroidEntryPoint
class AddTransactionDialog : BottomSheetDialogFragment() {

    @Inject
    lateinit var walletDao: WalletDao

    @Inject
    lateinit var categoryRepository: CategoryRepository

    private val viewModel: TransactionViewModel by viewModels()
    private var selectedCategory: Category? = null
    private var isIncome: Boolean = false
    private var preSelectedType: Boolean? = null
    private lateinit var categoryAdapter: CategoryChipAdapter
    private var selectedWalletId: Long? = null

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

        val isPro = AuthPrefs.isProMember(requireContext())
        val walletSection = view.findViewById<View>(R.id.walletSection)
        val rvWallets = view.findViewById<RecyclerView>(R.id.rvWallets)
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

        val etAmount      = view.findViewById<TextInputEditText>(R.id.etAmount)
        val etDescription = view.findViewById<TextInputEditText>(R.id.etDescription)
        val rvCategories  = view.findViewById<RecyclerView>(R.id.rvDialogCategories)
        view.findViewById<View>(R.id.tvDialogCategoriesLink)?.setOnClickListener {
            findNavController().navigate(
                R.id.categoryPickerFragment,
                CategoryPickerFragment.args(isIncome = isIncome)
            )
        }
        // toggle LinearLayout — btnExpense ve btnIncome direkt kullanılıyor
        val btnExpense    = view.findViewById<MaterialButton>(R.id.btnExpense)
        val btnIncome     = view.findViewById<MaterialButton>(R.id.btnIncome)
        val btnSave       = view.findViewById<MaterialButton>(R.id.btnSave)
        val btnCancel     = view.findViewById<MaterialButton>(R.id.btnCancel)

        val colorGreen       = androidx.core.content.ContextCompat.getColor(requireContext(), R.color.green_primary)
        val colorWhite       = androidx.core.content.ContextCompat.getColor(requireContext(), R.color.text_white)
        val colorTextPrimary = androidx.core.content.ContextCompat.getColor(requireContext(), R.color.text_primary)

        // Kategori chip adapter
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
            val includeSubcats = AuthPrefs.isProMember(requireContext())
            val top = viewModel.getTopCategories(
                isIncome = isIncome,
                includeSubcategories = includeSubcats,
                limit = 6
            )
            val cats = CategorySlotBuilder.buildFixedSlots(
                top = top,
                selected = selectedCategory?.takeIf { it.isIncome == isIncome },
                limit = 6
            )
            categoryAdapter.setCategories(cats, defaultName)
            selectedCategory?.takeIf { it.isIncome == isIncome }?.let { categoryAdapter.setSelected(it.id) }
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
                    if (state.categories.isNotEmpty() && categoryAdapter.itemCount == 0) loadCategories()
                }
            }
        }

        // Inline category creation removed (layout no longer contains tvNewCategoryLink)

        // Etiketler popup'tan kaldırıldı

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
                isIncome = isIncome,
                walletId = selectedWalletId,
                tags = "",
                tagIds = emptyList()
            )
            toast("✅ İşlem eklendi!")
            dismiss()
        }

        // Tags observe removed
    }

    private fun toast(msg: String) = Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()

    // Slot logic is centralized in CategorySlotBuilder.

    // Inline category creation removed; categories are managed from the dedicated Categories page.
}
