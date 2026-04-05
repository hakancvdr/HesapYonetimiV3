package com.example.hesapyonetimi

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class TakvimAdapter(
    private val harcamaGunleri: Set<Int>,
    private val onGunTiklandi: (Int) -> Unit // Tıklama için callback ekledik
) : RecyclerView.Adapter<TakvimAdapter.VH>() {

    class VH(view: View) : RecyclerView.ViewHolder(view) {
        // Android'in dahili basit text alanını kullanıyoruz
        val tvGun: TextView = view.findViewById(android.R.id.text1)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val view = LayoutInflater.from(parent.context)
            .inflate(android.R.layout.simple_list_item_1, parent, false)
        return VH(view)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val gun = position + 1
        holder.tvGun.text = gun.toString()

        // Harcama olan günlerin stili
        if (harcamaGunleri.contains(gun)) {
            holder.tvGun.setBackgroundResource(R.drawable.takvim_isaret_bg)
            holder.tvGun.setTextColor(android.graphics.Color.WHITE)

            // Sadece harcama olan günler tıklanabilir olsun
            holder.itemView.setOnClickListener { onGunTiklandi(gun) }
        } else {
            holder.tvGun.setBackgroundColor(android.graphics.Color.TRANSPARENT)
            holder.tvGun.setTextColor(android.graphics.Color.BLACK)
            holder.itemView.setOnClickListener(null)
        }
    }

    override fun getItemCount(): Int {
        val takvim = java.util.Calendar.getInstance()
        return takvim.getActualMaximum(java.util.Calendar.DAY_OF_MONTH)
    }
}


