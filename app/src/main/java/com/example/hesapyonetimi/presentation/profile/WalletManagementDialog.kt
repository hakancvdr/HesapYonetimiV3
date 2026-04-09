package com.example.hesapyonetimi.presentation.profile

import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.hesapyonetimi.R
import com.example.hesapyonetimi.data.local.dao.TransactionDao
import com.example.hesapyonetimi.data.local.dao.WalletDao
import com.example.hesapyonetimi.data.local.entity.WalletEntity
import com.example.hesapyonetimi.presentation.common.CurrencyFormatter
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class WalletManagementDialog : DialogFragment() {

    @Inject
    lateinit var walletDao: WalletDao

    @Inject
    lateinit var transactionDao: TransactionDao

    private val walletTypes = listOf(
        "CASH" to "💵 Nakit",
        "BANK" to "🏦 Banka",
        "CREDIT" to "💳 Kredi Kartı",
        "CRYPTO" to "₿ Kripto",
        "OTHER" to "📦 Diğer"
    )

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val view = LayoutInflater.from(requireContext())
            .inflate(R.layout.dialog_wallet_management, null)
        setupUI(view)
        return MaterialAlertDialogBuilder(requireContext())
            .setView(view)
            .create()
            .also {
                it.window?.setBackgroundDrawableResource(android.R.color.transparent)
            }
    }

    private fun setupUI(view: View) {
        val rvWallets = view.findViewById<RecyclerView>(R.id.rvWalletList)
        val btnAdd = view.findViewById<View>(R.id.btnAddWallet)

        rvWallets.layoutManager = LinearLayoutManager(requireContext())

        lifecycleScope.launch {
            walletDao.getAllWallets().collect { wallets ->
                // Her cüzdan için harcama/gelir hesapla
                val stats = wallets.associate { w ->
                    val txs = transactionDao.getAllTransactionsOnce()
                        .filter { it.walletId == w.id }
                    val exp = txs.filter { !it.isIncome }.sumOf { it.amount }
                    val inc = txs.filter { it.isIncome }.sumOf { it.amount }
                    w.id to (exp to inc)
                }
                rvWallets.adapter = WalletListAdapter(wallets, stats,
                    onDelete = { wallet -> deleteWallet(wallet) },
                    onSetDefault = { wallet -> setDefault(wallet) }
                )
            }
        }

        btnAdd.setOnClickListener { showAddWalletDialog() }
    }

    private fun deleteWallet(wallet: WalletEntity) {
        if (wallet.isDefault) {
            Toast.makeText(requireContext(), "Varsayılan cüzdan silinemez", Toast.LENGTH_SHORT).show()
            return
        }
        lifecycleScope.launch { walletDao.delete(wallet) }
    }

    private fun setDefault(wallet: WalletEntity) {
        lifecycleScope.launch {
            walletDao.getAllWallets().collect { all ->
                all.forEach { w ->
                    if (w.isDefault && w.id != wallet.id) walletDao.update(w.copy(isDefault = false))
                }
                walletDao.update(wallet.copy(isDefault = true))
                return@collect
            }
        }
    }

    private fun showAddWalletDialog() {
        val dialogView = LayoutInflater.from(requireContext())
            .inflate(R.layout.dialog_add_wallet, null)
        val etName = dialogView.findViewById<TextInputEditText>(R.id.etWalletName)

        var selectedType = "BANK"
        var selectedIcon = "🏦"

        data class WalletTypeBtn(val id: Int, val type: String, val icon: String)
        val typeDefs = listOf(
            WalletTypeBtn(R.id.btnWalletType0, "CASH",   "💵"),
            WalletTypeBtn(R.id.btnWalletType1, "BANK",   "🏦"),
            WalletTypeBtn(R.id.btnWalletType2, "CREDIT", "💳"),
            WalletTypeBtn(R.id.btnWalletType3, "CRYPTO", "₿")
        )

        fun refreshTypeButtons() {
            typeDefs.forEach { def ->
                val btn = dialogView.findViewById<TextView>(def.id) ?: return@forEach
                val isSelected = def.type == selectedType
                btn.setBackgroundResource(if (isSelected) R.drawable.kategori_item_selected_bg else R.drawable.kategori_item_bg)
                btn.setTextColor(resources.getColor(if (isSelected) R.color.green_primary else R.color.text_secondary, null))
            }
        }

        typeDefs.forEach { def ->
            dialogView.findViewById<TextView>(def.id)?.setOnClickListener {
                selectedType = def.type
                selectedIcon = def.icon
                refreshTypeButtons()
            }
        }
        refreshTypeButtons()

        MaterialAlertDialogBuilder(requireContext())
            .setView(dialogView)
            .setTitle("Cüzdan Ekle")
            .setPositiveButton("Ekle") { _, _ ->
                val name = etName?.text.toString().trim()
                if (name.isBlank()) { Toast.makeText(requireContext(), "İsim gerekli", Toast.LENGTH_SHORT).show(); return@setPositiveButton }
                lifecycleScope.launch {
                    walletDao.insert(WalletEntity(name = name, icon = selectedIcon, type = selectedType))
                }
            }
            .setNegativeButton("İptal", null)
            .show()
    }
}

private class WalletListAdapter(
    private val wallets: List<WalletEntity>,
    private val stats: Map<Long, Pair<Double, Double>>,  // id → (expense, income)
    private val onDelete: (WalletEntity) -> Unit,
    private val onSetDefault: (WalletEntity) -> Unit
) : RecyclerView.Adapter<WalletListAdapter.VH>() {

    inner class VH(view: View) : RecyclerView.ViewHolder(view) {
        val tvName: TextView = view.findViewById(R.id.tvWalletName)
        val tvType: TextView = view.findViewById(R.id.tvWalletType)
        val tvDefault: TextView = view.findViewById(R.id.tvWalletDefault)
        val btnDelete: View = view.findViewById(R.id.btnDeleteWallet)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        VH(LayoutInflater.from(parent.context).inflate(R.layout.item_wallet, parent, false))

    override fun getItemCount() = wallets.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        val w = wallets[position]
        holder.tvName.text = "${w.icon} ${w.name}"
        val typeName = when (w.type) {
            "CASH" -> "Nakit"; "BANK" -> "Banka"; "CREDIT" -> "Kredi Kartı"
            "CRYPTO" -> "Kripto"; else -> "Diğer"
        }
        val (exp, inc) = stats[w.id] ?: (0.0 to 0.0)
        val statStr = if (exp > 0 || inc > 0)
            "↑${CurrencyFormatter.format(inc, false)}  ↓${CurrencyFormatter.format(exp, false)}"
        else "İşlem yok"
        holder.tvType.text = "$typeName  •  $statStr"
        holder.tvDefault.visibility = if (w.isDefault) View.VISIBLE else View.GONE
        holder.btnDelete.setOnClickListener { onDelete(w) }
        holder.itemView.setOnLongClickListener { onSetDefault(w); true }
    }
}
