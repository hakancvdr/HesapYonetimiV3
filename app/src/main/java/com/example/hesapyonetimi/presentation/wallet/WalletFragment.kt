package com.example.hesapyonetimi.presentation.wallet

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import android.os.Bundle
import android.view.View.GONE
import android.view.View.VISIBLE
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.hesapyonetimi.R
import com.example.hesapyonetimi.data.local.entity.WalletEntity
import com.example.hesapyonetimi.presentation.common.CurrencyFormatter
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class WalletFragment : Fragment() {

    private val viewModel: WalletViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_wallet, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val statusBarHeight = run {
            val r = resources.getDimensionPixelSize(
                resources.getIdentifier("status_bar_height", "dimen", "android")
            )
            if (r > 0) r else (24 * resources.displayMetrics.density).toInt()
        }
        val header = view.findViewById<View>(R.id.wallet_header)
        header.setPadding(
            header.paddingLeft,
            statusBarHeight + (12 * resources.displayMetrics.density).toInt(),
            header.paddingRight,
            header.paddingBottom
        )

        view.findViewById<View>(R.id.btnBack).setOnClickListener {
            findNavController().popBackStack()
        }

        view.findViewById<View>(R.id.fabAddWallet).setOnClickListener {
            showAddWalletDialog()
        }

        val rvWallets = view.findViewById<RecyclerView>(R.id.rvWallets)
        rvWallets.layoutManager = LinearLayoutManager(requireContext())

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.wallets.collect { items ->
                    val totalIncome = items.sumOf { it.totalIncome }
                    val totalExpense = items.sumOf { it.totalExpense }
                    val net = totalIncome - totalExpense

                    view.findViewById<TextView>(R.id.tvTotalNet).text =
                        CurrencyFormatter.format(net)
                    view.findViewById<TextView>(R.id.tvTotalIncome).text =
                        CurrencyFormatter.format(totalIncome)
                    view.findViewById<TextView>(R.id.tvTotalExpense).text =
                        CurrencyFormatter.format(totalExpense)

                    rvWallets.adapter = WalletAdapter(
                        items = items,
                        onDelete = { item ->
                            if (item.wallet.isDefault) {
                                Toast.makeText(requireContext(), "Varsayılan cüzdan silinemez", Toast.LENGTH_SHORT).show()
                            } else {
                                MaterialAlertDialogBuilder(requireContext())
                                    .setTitle("Cüzdanı Sil")
                                    .setMessage("\"${item.wallet.name}\" cüzdanını silmek istediğine emin misin?")
                                    .setPositiveButton("Sil") { _, _ -> viewModel.deleteWallet(item.wallet) }
                                    .setNegativeButton("İptal", null)
                                    .show()
                            }
                        },
                        onSetDefault = { item -> viewModel.setDefault(item.wallet) }
                    )
                }
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

        fun refreshButtons() {
            typeDefs.forEach { def ->
                val btn = dialogView.findViewById<TextView>(def.id) ?: return@forEach
                val sel = def.type == selectedType
                btn.setBackgroundResource(if (sel) R.drawable.kategori_item_selected_bg else R.drawable.kategori_item_bg)
                btn.setTextColor(resources.getColor(if (sel) R.color.green_primary else R.color.text_secondary, null))
            }
        }
        typeDefs.forEach { def ->
            dialogView.findViewById<TextView>(def.id)?.setOnClickListener {
                selectedType = def.type; selectedIcon = def.icon; refreshButtons()
            }
        }
        refreshButtons()

        MaterialAlertDialogBuilder(requireContext())
            .setView(dialogView)
            .setTitle("Cüzdan Ekle")
            .setPositiveButton("Ekle") { _, _ ->
                val name = etName?.text.toString().trim()
                if (name.isBlank()) {
                    Toast.makeText(requireContext(), "İsim gerekli", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                viewModel.addWallet(name, selectedIcon, selectedType)
            }
            .setNegativeButton("İptal", null)
            .show()
    }
}

private class WalletAdapter(
    private val items: List<WalletUiItem>,
    private val onDelete: (WalletUiItem) -> Unit,
    private val onSetDefault: (WalletUiItem) -> Unit
) : RecyclerView.Adapter<WalletAdapter.VH>() {

    inner class VH(view: View) : RecyclerView.ViewHolder(view) {
        val tvName: TextView = view.findViewById(R.id.tvWalletName)
        val tvType: TextView = view.findViewById(R.id.tvWalletType)
        val tvDefault: TextView = view.findViewById(R.id.tvWalletDefault)
        val btnDelete: View = view.findViewById(R.id.btnDeleteWallet)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        VH(LayoutInflater.from(parent.context).inflate(R.layout.item_wallet, parent, false))

    override fun getItemCount() = items.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = items[position]
        val w = item.wallet
        holder.tvName.text = "${w.icon} ${w.name}"
        val typeName = when (w.type) {
            "CASH"   -> "Nakit"
            "BANK"   -> "Banka"
            "CREDIT" -> "Kredi Kartı"
            "CRYPTO" -> "Kripto"
            else     -> "Diğer"
        }
        val statStr = if (item.totalIncome > 0 || item.totalExpense > 0)
            "↑${CurrencyFormatter.format(item.totalIncome, false)}  ↓${CurrencyFormatter.format(item.totalExpense, false)}"
        else "İşlem yok"
        holder.tvType.text = "$typeName  •  $statStr"
        holder.tvDefault.visibility = if (w.isDefault) VISIBLE else GONE
        holder.btnDelete.setOnClickListener { onDelete(item) }
        holder.itemView.setOnLongClickListener { onSetDefault(item); true }
    }
}
