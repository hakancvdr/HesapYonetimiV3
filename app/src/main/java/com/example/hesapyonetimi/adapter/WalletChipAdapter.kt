package com.example.hesapyonetimi.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.example.hesapyonetimi.R
import com.example.hesapyonetimi.data.local.entity.WalletEntity

class WalletChipAdapter(
    private val wallets: List<WalletEntity>,
    private var selectedId: Long?,
    private val onSelect: (WalletEntity) -> Unit
) : RecyclerView.Adapter<WalletChipAdapter.VH>() {

    inner class VH(view: View) : RecyclerView.ViewHolder(view) {
        val tvName: TextView = view.findViewById(R.id.tvWalletChipName)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH =
        VH(LayoutInflater.from(parent.context).inflate(R.layout.item_wallet_chip, parent, false))

    override fun getItemCount() = wallets.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        val wallet = wallets[position]
        holder.tvName.text = "${wallet.icon} ${wallet.name}"
        val selected = wallet.id == selectedId
        holder.tvName.setBackgroundResource(
            if (selected) R.drawable.kategori_item_selected_bg else R.drawable.kategori_item_bg
        )
        holder.tvName.setTextColor(
            ContextCompat.getColor(holder.itemView.context,
                if (selected) R.color.green_primary else R.color.text_secondary)
        )
        holder.itemView.setOnClickListener {
            selectedId = wallet.id
            onSelect(wallet)
            notifyDataSetChanged()
        }
    }
}
