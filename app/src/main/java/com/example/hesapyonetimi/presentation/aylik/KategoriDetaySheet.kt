package com.example.hesapyonetimi.presentation.aylik

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.gridlayout.widget.GridLayout
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.hesapyonetimi.R
import com.example.hesapyonetimi.adapter.TransactionAdapter
import com.example.hesapyonetimi.model.TransactionModel
import com.example.hesapyonetimi.presentation.common.CurrencyFormatter
import com.example.hesapyonetimi.ui.MaterialCategoryIcon
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.abs

class KategoriDetaySheet : BottomSheetDialogFragment() {

    private lateinit var kat: KategoriOzet
    private var ayOffset: Int = 0
    private var calOffset: Int = 0

    companion object {
        fun newInstance(kat: KategoriOzet, ayOffset: Int): KategoriDetaySheet {
            return KategoriDetaySheet().apply {
                this.kat = kat
                this.ayOffset = ayOffset
                this.calOffset = ayOffset
            }
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.bottom_sheet_kategori_detay, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Header
        MaterialCategoryIcon.bind(view.findViewById(R.id.tv_detay_icon), kat.icon, 22f)
        view.findViewById<TextView>(R.id.tv_detay_baslik).text = kat.ad
        view.findViewById<TextView>(R.id.tv_detay_ozet).text = "${kat.islemSayisi} işlem · Toplam giderin %${kat.yuzde.toInt()}'i"
        view.findViewById<TextView>(R.id.tv_detay_toplam).text = CurrencyFormatter.format(kat.toplam)
        view.findViewById<TextView>(R.id.tv_detay_yuzde).text = "Bu ay"

        // Analiz metni
        val tvEmoji = view.findViewById<TextView>(R.id.tv_analiz_emoji)
        val tvAnaliz = view.findViewById<TextView>(R.id.tv_analiz_metin)

        when {
            kat.gecenAyToplam == 0.0 && kat.toplam > 0 -> {
                tvEmoji.text = "🆕"
                tvAnaliz.text = "${kat.ad} kategorisinde bu ay ilk harcamanı yaptın."
            }
            kat.gecenAyToplam == 0.0 -> {
                tvEmoji.text = "💡"
                tvAnaliz.text = "Bu kategoride henüz harcama yok."
            }
            kat.degisimYuzde > 0 -> {
                tvEmoji.text = "⚠️"
                tvAnaliz.text = "${kat.ad} harcaman geçen aya göre %${abs(kat.degisimYuzde).toInt()} arttı. (${CurrencyFormatter.format(kat.gecenAyToplam)} → ${CurrencyFormatter.format(kat.toplam)})"
            }
            kat.degisimYuzde < 0 -> {
                tvEmoji.text = "👍"
                tvAnaliz.text = "${kat.ad} harcaman geçen aya göre %${abs(kat.degisimYuzde).toInt()} azaldı. (${CurrencyFormatter.format(kat.gecenAyToplam)} → ${CurrencyFormatter.format(kat.toplam)})"
            }
            else -> {
                tvEmoji.text = "➡️"
                tvAnaliz.text = "${kat.ad} harcaman geçen ayla aynı seviyede."
            }
        }

        // Takvim navigasyonu
        view.findViewById<TextView>(R.id.btn_prev_month).setOnClickListener {
            calOffset--; drawCalendar(view)
        }
        view.findViewById<TextView>(R.id.btn_next_month).setOnClickListener {
            if (calOffset < 0) { calOffset++; drawCalendar(view) }
        }

        drawCalendar(view)
        drawIslemler(view)
    }

    private fun drawCalendar(view: View) {
        val grid = view.findViewById<GridLayout>(R.id.grid_takvim)
        grid.removeAllViews()

        val cal = Calendar.getInstance().apply { add(Calendar.MONTH, calOffset) }
        val ayFormat = SimpleDateFormat("MMMM yyyy", Locale("tr"))
        view.findViewById<TextView>(R.id.tv_cal_ay).text = ayFormat.format(cal.time)

        val txDays = kat.islemler.filter { t ->
            val c = Calendar.getInstance().apply { timeInMillis = t.date }
            c.get(Calendar.MONTH) == cal.get(Calendar.MONTH) &&
            c.get(Calendar.YEAR) == cal.get(Calendar.YEAR)
        }.map { t ->
            Calendar.getInstance().apply { timeInMillis = t.date }.get(Calendar.DAY_OF_MONTH)
        }.toSet()

        cal.set(Calendar.DAY_OF_MONTH, 1)
        var firstDay = cal.get(Calendar.DAY_OF_WEEK) - 2
        if (firstDay < 0) firstDay = 6

        val daysInMonth = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
        val density = resources.displayMetrics.density
        val cellSize = ((resources.displayMetrics.widthPixels - 32 * density) / 7).toInt()

        repeat(firstDay) { grid.addView(createCell("", false, cellSize)) }
        for (day in 1..daysInMonth) {
            grid.addView(createCell(day.toString(), txDays.contains(day), cellSize))
        }

        view.findViewById<TextView>(R.id.btn_next_month).alpha = if (calOffset < 0) 1f else 0.3f
    }

    private fun createCell(text: String, hasData: Boolean, size: Int): TextView {
        return TextView(requireContext()).apply {
            this.text = text
            textSize = 12f
            gravity = android.view.Gravity.CENTER
            layoutParams = GridLayout.LayoutParams().apply {
                width = size; height = size
                setMargins(2, 2, 2, 2)
            }
            if (text.isNotEmpty()) {
                if (hasData) {
                    setBackgroundResource(R.drawable.calendar_bg_circle)
                    setTextColor(0xFFFFFFFF.toInt())
                    setTypeface(null, android.graphics.Typeface.BOLD)
                } else {
                    background = null
                    setTextColor(0xFF1A1C1E.toInt())
                }
            }
        }
    }

    private fun drawIslemler(view: View) {
        val timeFormat = SimpleDateFormat("d MMM · HH:mm", Locale("tr"))
        val rv = view.findViewById<RecyclerView>(R.id.rv_detay_islemler)
        rv.layoutManager = LinearLayoutManager(requireContext())
        rv.adapter = TransactionAdapter(kat.islemler.map { t ->
            TransactionModel(
                id = t.id,
                title = t.description,
                category = t.categoryName,
                amount = CurrencyFormatter.formatWithSign(t.amount, t.isIncome),
                isIncome = t.isIncome,
                time = timeFormat.format(Date(t.date)),
                transaction = t
            )
        })
    }
}
