package com.example.hesapyonetimi.presentation.aylik

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import androidx.gridlayout.widget.GridLayout
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.hesapyonetimi.R
import com.example.hesapyonetimi.adapter.TransactionAdapter
import com.example.hesapyonetimi.model.TransactionModel
import com.example.hesapyonetimi.presentation.common.CurrencyFormatter
import com.example.hesapyonetimi.presentation.common.EditTransactionSheet
import com.example.hesapyonetimi.ui.MaterialCategoryIcon
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.abs

class KategoriDetayFragment : Fragment() {

    private lateinit var kat: KategoriOzet
    private var ayOffset: Int = 0
    private var calOffset: Int = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        kat = arguments?.getParcelable("kat")
            ?: throw IllegalStateException("KategoriDetayFragment: 'kat' argümanı eksik")
        ayOffset = arguments?.getInt("ayOffset", 0) ?: 0
        calOffset = ayOffset
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_kategori_detay, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        view.findViewById<TextView>(R.id.btn_geri).setOnClickListener {
            findNavController().popBackStack()
        }

        MaterialCategoryIcon.bind(view.findViewById(R.id.tv_detay_icon), kat.icon, 20f)
        view.findViewById<TextView>(R.id.tv_detay_baslik).text = kat.ad

        view.findViewById<TextView>(R.id.tv_stat_toplam).text = CurrencyFormatter.format(kat.toplam)
        view.findViewById<TextView>(R.id.tv_stat_pay).text = "%${kat.yuzde.toInt()}"
        view.findViewById<TextView>(R.id.tv_stat_islem).text = "${kat.islemSayisi} adet"

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
            kat.degisimYuzde > 5 -> {
                tvEmoji.text = "⚠️"
                tvAnaliz.text = "${kat.ad} harcaman geçen aya göre %${abs(kat.degisimYuzde).toInt()} arttı.\n${CurrencyFormatter.format(kat.gecenAyToplam)} → ${CurrencyFormatter.format(kat.toplam)}"
            }
            kat.degisimYuzde < -5 -> {
                tvEmoji.text = "👍"
                tvAnaliz.text = "${kat.ad} harcaman geçen aya göre %${abs(kat.degisimYuzde).toInt()} azaldı.\n${CurrencyFormatter.format(kat.gecenAyToplam)} → ${CurrencyFormatter.format(kat.toplam)}"
            }
            else -> {
                tvEmoji.text = "➡️"
                tvAnaliz.text = "${kat.ad} harcaman geçen ayla benzer seviyede."
            }
        }

        view.findViewById<TextView>(R.id.btn_prev_month).setOnClickListener {
            calOffset--; drawCalendar(view)
        }
        view.findViewById<TextView>(R.id.btn_next_month).setOnClickListener {
            if (calOffset < 0) { calOffset++; drawCalendar(view) }
        }

        drawCalendar(view)

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
        }) { transaction ->
            EditTransactionSheet.newInstance(transaction)
                .show(childFragmentManager, "EditTransaction")
        }
    }

    private fun drawCalendar(view: View) {
        val grid = view.findViewById<GridLayout>(R.id.grid_takvim)
        grid.removeAllViews()
        grid.columnCount = 7

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
        val screenWidth = resources.displayMetrics.widthPixels
        val totalPadding = ((16 + 14) * 2 * density).toInt()
        val cellSize = ((screenWidth - totalPadding) / 7).toInt().coerceAtMost((44 * density).toInt())

        var col = 0
        var row = 0

        fun addCell(text: String, hasData: Boolean) {
            val tv = createCell(text, hasData, cellSize)
            val params = GridLayout.LayoutParams(
                GridLayout.spec(row, 1f),
                GridLayout.spec(col, 1f)
            ).apply {
                width = cellSize
                height = cellSize
                setMargins(1, 1, 1, 1)
            }
            tv.layoutParams = params
            grid.addView(tv)
            col++
            if (col == 7) { col = 0; row++ }
        }

        repeat(firstDay) { addCell("", false) }
        for (day in 1..daysInMonth) { addCell(day.toString(), txDays.contains(day)) }

        view.findViewById<TextView>(R.id.btn_next_month).alpha = if (calOffset < 0) 1f else 0.3f
    }

    private fun createCell(text: String, hasData: Boolean, size: Int): TextView {
        return TextView(requireContext()).apply {
            this.text = text
            textSize = 12f
            gravity = android.view.Gravity.CENTER
            layoutParams = GridLayout.LayoutParams().apply {
                width = size
                height = size
                setMargins(1, 1, 1, 1)
            }
            if (text.isNotEmpty()) {
                if (hasData) {
                    setBackgroundResource(R.drawable.calendar_bg_circle)
                    setTextColor(0xFFFFFFFF.toInt())
                    setTypeface(null, android.graphics.Typeface.BOLD)
                } else {
                    background = null
                    setTextColor(androidx.core.content.ContextCompat.getColor(requireContext(), R.color.text_primary))
                }
            }
        }
    }
}
