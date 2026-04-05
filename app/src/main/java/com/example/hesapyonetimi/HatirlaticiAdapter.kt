package com.example.hesapyonetimi



import android.view.LayoutInflater

import android.view.View

import android.view.ViewGroup

import android.widget.TextView

import androidx.core.content.ContextCompat

import androidx.recyclerview.widget.RecyclerView

import java.text.SimpleDateFormat

import java.util.*



// DİKKAT: Veri modelindeki isimleri (zamanMs/tarihSaatMs) kontrol etmeyi unutma!

class HatirlaticiAdapter(private val hatirlaticiListesi: List<Hatirlatici>) :

    RecyclerView.Adapter<HatirlaticiAdapter.ViewHolder>() {



    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {

        val tvBaslik: TextView = view.findViewById(R.id.tvBaslik)

        val tvTarih: TextView = view.findViewById(R.id.tvTarih)

        val tvTutar: TextView = view.findViewById(R.id.tvTutar)

        val vDurumNoktasi: View = view.findViewById(R.id.vDurumNoktasi)

    }



    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {

        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_hatirlatici, parent, false)

        return ViewHolder(view)

    }



    override fun onBindViewHolder(holder: ViewHolder, position: Int) {

        val hatirlatici = hatirlaticiListesi[position]



        // 1. ADIM: Zaman hesapla

        val simdi = System.currentTimeMillis()

        // Hatırlatıcı modelinde 'zamanMs' veya 'tarihSaatMs' hangisi varsa onu kullan:

        val kalanSure = hatirlatici.tarihSaatMs - simdi

        val birGunMs = 24 * 60 * 60 * 1000L



        // 2. ADIM: Renk belirle (Zaman duyarlı renk sistemi)

        val durumRengi = when {

            kalanSure < 0 -> R.color.gider_kkirmizi          // Süresi geçmiş

            kalanSure <= birGunMs -> R.color.gider_kirmizi    // 1 günden az (Kritik)

            kalanSure <= 3 * birGunMs -> R.color.mustard // 3 günden az (Uyarı)

            else -> R.color.gelir_yesil                      // 1 hafta+ (Güvenli)

        }



        // 3. ADIM: Görsel setleme

        val context = holder.itemView.context



        // Renkli nokta (vDurumNoktasi) rengini güncelle

        holder.vDurumNoktasi.backgroundTintList = ContextCompat.getColorStateList(context, durumRengi)



        holder.tvBaslik.text = hatirlatici.baslik

        holder.tvTutar.text = "${hatirlatici.tutar} TL"



        // Tarihi okunabilir formatta yazdır (Long -> String)

        val sdf = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault())

        holder.tvTarih.text = sdf.format(Date(hatirlatici.tarihSaatMs))

    }



    override fun getItemCount(): Int = hatirlaticiListesi.size

}