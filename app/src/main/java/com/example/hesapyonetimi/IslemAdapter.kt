package com.example.hesapyonetimi

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class IslemAdapter(private val islemler: List<Islem>) : RecyclerView.Adapter<IslemAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        // Android'in hazır list item yapısını kullanıyoruz (simple_list_item_2)
        val tvAciklama: TextView = view.findViewById(android.R.id.text1)
        val tvDetay: TextView = view.findViewById(android.R.id.text2)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(android.R.layout.simple_list_item_2, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val islem = islemler[position]

        holder.tvAciklama.text = "${islem.kategori}: ${islem.aciklama}"

        val isaret = if (islem.isGelir) "+" else "-"
        holder.tvDetay.text = "$isaret ${islem.tutar} TL | ${islem.tarihSaat}"

        // Gelirse yeşil, giderse kırmızı yap
        val renk = if (islem.isGelir) Color.parseColor("#388E3C") else Color.parseColor("#D32F2F")
        holder.tvDetay.setTextColor(renk)
    }

    override fun getItemCount() = islemler.size
}