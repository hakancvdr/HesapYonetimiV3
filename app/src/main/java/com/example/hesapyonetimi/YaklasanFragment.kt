package com.example.hesapyonetimi

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.GridLayout
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.ItemTouchHelper
import com.google.android.material.snackbar.Snackbar
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.example.hesapyonetimi.MainActivity
import com.example.hesapyonetimi.adapter.HatirlaticiAdapter
import com.example.hesapyonetimi.domain.model.Reminder
import com.example.hesapyonetimi.presentation.common.CurrencyFormatter
import com.example.hesapyonetimi.presentation.common.HizliOneri
import com.example.hesapyonetimi.presentation.reminders.HatirlaticiEkleSheet
import com.example.hesapyonetimi.presentation.reminders.ReminderViewModel
import com.example.hesapyonetimi.presentation.reminders.ReminderUiColors
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

@AndroidEntryPoint
class YaklasanFragment : Fragment() {

    private val viewModel: ReminderViewModel by viewModels()
    private lateinit var adapterBuAy: HatirlaticiAdapter
    private lateinit var adapterSonraki: HatirlaticiAdapter
    private var tumReminders: List<Reminder> = emptyList()
    private var aktifFiltre = 0
    private var reminderCalOffset = 0
    private var yaklasanGorunumListe = true
    private var selectedCalDay: Int? = null
    private val ayYilTr = SimpleDateFormat("MMMM yyyy", Locale("tr"))

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_yaklasan, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        adapterBuAy = HatirlaticiAdapter(
            emptyList(),
            onOdendi = { id -> viewModel.markAsPaid(id) },
            onDuzenle = { reminder ->
                HatirlaticiEkleSheet.newInstance(reminder)
                    .show(childFragmentManager, "HatirlaticiDuzenle")
            },
            onSil = { id -> viewModel.deleteReminder(id) },
            onSilWithUndo = { reminder -> silWithUndo(reminder) }
        )
        adapterSonraki = HatirlaticiAdapter(
            emptyList(),
            onOdendi = { id -> viewModel.markAsPaid(id) },
            onDuzenle = { reminder ->
                HatirlaticiEkleSheet.newInstance(reminder)
                    .show(childFragmentManager, "HatirlaticiDuzenle")
            },
            onSil = { id -> viewModel.deleteReminder(id) },
            onSilWithUndo = { reminder -> silWithUndo(reminder) }
        )

        view.findViewById<RecyclerView>(R.id.rv_bu_ay).apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = adapterBuAy
            ItemTouchHelper(adapterBuAy.createSwipeCallback()).attachToRecyclerView(this)
        }
        view.findViewById<RecyclerView>(R.id.rv_sonraki).apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = adapterSonraki
            ItemTouchHelper(adapterSonraki.createSwipeCallback()).attachToRecyclerView(this)
        }

        view.findViewById<View>(R.id.btn_hatirlatici_ekle).setOnClickListener {
            HatirlaticiEkleSheet.newInstance().show(childFragmentManager, "HatirlaticiEkle")
        }

        setupFiltre(view)
        setupYaklasanViewMode(view)

        view.findViewById<SwipeRefreshLayout>(R.id.swipe_refresh).apply {
            setColorSchemeResources(R.color.green_primary)
            setOnRefreshListener { isRefreshing = false }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.uiState.collect { state ->
                        tumReminders = state.reminders
                        updateUI(view, state.reminders)
                    }
                }
                launch {
                    viewModel.oneriler.collect { oneriler ->
                        gosterOneriKartlari(view, oneriler)
                    }
                }
            }
        }
    }

    private fun gosterOneriKartlari(view: View, oneriler: List<HizliOneri>) {
        val container = view.findViewById<LinearLayout>(R.id.oneri_container) ?: return
        container.removeAllViews()

        if (oneriler.isEmpty()) {
            container.visibility = View.GONE
            return
        }

        container.visibility = View.VISIBLE
        val inflater = LayoutInflater.from(requireContext())

        oneriler.forEach { oneri ->
            val oneriView = inflater.inflate(R.layout.item_hizli_oneri, container, false)
            oneriView.findViewById<TextView>(R.id.tv_oneri_icon).text = oneri.categoryIcon
            oneriView.findViewById<TextView>(R.id.tv_oneri_baslik).text =
                "${oneri.description} · ${CurrencyFormatter.format(oneri.amount)}"
            oneriView.findViewById<TextView>(R.id.tv_oneri_aciklama).text =
                "Son ${oneri.eslesmeSkoru} ayda benzer ödeme"

            oneriView.findViewById<View>(R.id.btn_oneri_ekle).setOnClickListener {
                if (oneri.isIncome) {
                    Toast.makeText(
                        requireContext(),
                        "Hatırlatıcılar gider ödemeleri içindir.",
                        Toast.LENGTH_SHORT
                    ).show()
                    return@setOnClickListener
                }
                HatirlaticiEkleSheet.newInstanceFromQuickFill(
                    oneri.description,
                    oneri.amount,
                    oneri.categoryId
                ).show(childFragmentManager, "HatirlaticiEkle")
            }

            oneriView.findViewById<View>(R.id.btn_oneri_kapat).setOnClickListener {
                container.removeView(oneriView)
                if (container.childCount == 0) container.visibility = View.GONE
            }

            container.addView(oneriView)
        }
    }

    private fun updateUI(view: View, reminders: List<Reminder>) {
        val bekleyen = reminders.filter { !it.isPaid && !it.isOverdue }
        val gecikmus = reminders.filter { it.isOverdue }

        val todayStart = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        val windowEnd = todayStart + 30L * 24 * 60 * 60 * 1000
        val bekleyen30Gun = bekleyen.filter { it.dueDate in todayStart..windowEnd }

        view.findViewById<TextView>(R.id.tv_bekleyen_sayi).text = "${bekleyen.size} adet"
        view.findViewById<TextView>(R.id.tv_bekleyen_toplam).text =
            CurrencyFormatter.format(bekleyen30Gun.sumOf { it.amount })

        val layoutGecikmis = view.findViewById<View>(R.id.layout_gecikmis_ozet)
        val tvGecikmus = view.findViewById<TextView>(R.id.tv_gecikmus_sayi)
        if (gecikmus.isEmpty()) {
            layoutGecikmis?.visibility = View.GONE
        } else {
            layoutGecikmis?.visibility = View.VISIBLE
            tvGecikmus.text = "${gecikmus.size} adet"
            tvGecikmus.setTextColor(0xFFFF6B6B.toInt())
        }

        val filtrelenmisRaw = when (aktifFiltre) {
            1 -> reminders.filter { it.isOverdue }
            2 -> reminders // Tümü — ödenenler dahil
            else -> reminders.filter { !it.isPaid } // Bekleyen — sadece ödenmemiş
        }
        val filtrelenmis = sortRemindersForDisplay(filtrelenmisRaw)

        val ayBitis = Calendar.getInstance().apply {
            set(Calendar.DAY_OF_MONTH, getActualMaximum(Calendar.DAY_OF_MONTH))
            set(Calendar.HOUR_OF_DAY, 23); set(Calendar.MINUTE, 59)
        }.timeInMillis

        val buAy = filtrelenmis.filter { it.dueDate <= ayBitis }
        val sonraki = filtrelenmis.filter { it.dueDate > ayBitis }

        adapterBuAy.update(buAy)
        adapterSonraki.update(sonraki)

        view.findViewById<View>(R.id.tv_bu_ay_baslik).visibility =
            if (buAy.isNotEmpty()) View.VISIBLE else View.GONE
        view.findViewById<View>(R.id.tv_sonraki_baslik).visibility =
            if (sonraki.isNotEmpty()) View.VISIBLE else View.GONE
        view.findViewById<View>(R.id.empty_state).visibility =
            if (filtrelenmis.isEmpty()) View.VISIBLE else View.GONE

        // Takvim görünümünde gecikmiş uyarısı + takvimi yenile
        if (!yaklasanGorunumListe) {
            val banner = view.findViewById<View>(R.id.overdue_calendar_banner)
            val tv = view.findViewById<TextView>(R.id.tv_overdue_calendar_text)
            if (gecikmus.isEmpty()) {
                banner?.visibility = View.GONE
            } else {
                banner?.visibility = View.VISIBLE
                tv?.text = "${gecikmus.size} gecikmiş ödeme var · Dokun"
                banner?.setOnClickListener {
                    com.example.hesapyonetimi.presentation.reminders.DayRemindersSheet
                        .show(childFragmentManager, Calendar.getInstance().get(Calendar.YEAR), Calendar.getInstance().get(Calendar.MONTH), Calendar.getInstance().get(Calendar.DAY_OF_MONTH), gecikmus)
                }
            }
            buildReminderCalendar(view)
        }
    }

    private fun setupYaklasanViewMode(root: View) {
        val btnListe = root.findViewById<TextView>(R.id.btn_yaklasan_liste)
        val btnTakvim = root.findViewById<TextView>(R.id.btn_yaklasan_takvim)
        val swipe = root.findViewById<SwipeRefreshLayout>(R.id.swipe_refresh)
        val nestedCal = root.findViewById<View>(R.id.nested_yaklasan_takvim)
        val grn = ContextCompat.getColor(requireContext(), R.color.green_primary)
        val sec = ContextCompat.getColor(requireContext(), R.color.text_secondary)

        fun styleListe(secili: Boolean) {
            btnListe.setBackgroundResource(if (secili) R.drawable.kategori_item_selected_bg else R.drawable.kategori_item_bg)
            btnListe.setTextColor(if (secili) grn else sec)
            btnTakvim.setBackgroundResource(if (!secili) R.drawable.kategori_item_selected_bg else R.drawable.kategori_item_bg)
            btnTakvim.setTextColor(if (!secili) grn else sec)
        }

        btnListe.setOnClickListener {
            yaklasanGorunumListe = true
            swipe.visibility = View.VISIBLE
            nestedCal.visibility = View.GONE
            styleListe(true)
        }
        btnTakvim.setOnClickListener {
            yaklasanGorunumListe = false
            swipe.visibility = View.GONE
            nestedCal.visibility = View.VISIBLE
            styleListe(false)
            buildReminderCalendar(root)
        }

        root.findViewById<ImageButton>(R.id.btnReminderCalPrev).setOnClickListener {
            reminderCalOffset--
            buildReminderCalendar(root)
        }
        root.findViewById<ImageButton>(R.id.btnReminderCalNext).setOnClickListener {
            reminderCalOffset++
            buildReminderCalendar(root)
        }
        styleListe(true)
    }

    private fun buildReminderCalendar(root: View) {
        val grid = root.findViewById<GridLayout>(R.id.reminderCalendarGrid) ?: return
        grid.removeAllViews()
        val cal = Calendar.getInstance().apply { add(Calendar.MONTH, reminderCalOffset) }
        root.findViewById<TextView>(R.id.tvReminderCalMonth)?.text = ayYilTr.format(cal.time)

        val cellW = ((resources.displayMetrics.widthPixels - dpPx(64)) / 7).coerceAtLeast(dpPx(32))
        val headers = listOf("Pt", "Sa", "Ça", "Pe", "Cu", "Ct", "Pz")
        headers.forEach { h ->
            val tv = TextView(requireContext()).apply {
                text = h
                gravity = Gravity.CENTER
                textSize = 10f
                setTextColor(ContextCompat.getColor(requireContext(), R.color.text_secondary))
            }
            grid.addView(tv, GridLayout.LayoutParams().apply {
                width = 0
                height = GridLayout.LayoutParams.WRAP_CONTENT
                columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
            })
        }

        val y = cal.get(Calendar.YEAR)
        val m = cal.get(Calendar.MONTH)
        cal.set(Calendar.DAY_OF_MONTH, 1)
        val firstDow = cal.get(Calendar.DAY_OF_WEEK)
        val offset7 = (firstDow - 2 + 7) % 7
        val daysInMonth = cal.getActualMaximum(Calendar.DAY_OF_MONTH)

        val filteredForCalendar = when (aktifFiltre) {
            1 -> tumReminders.filter { it.isOverdue }          // Gecikmiş
            2 -> tumReminders                                  // Tümü
            else -> tumReminders.filter { !it.isPaid }         // Bekleyen
        }

        fun remindersForDay(day: Int): List<Reminder> = filteredForCalendar.filter { r ->
            val c = Calendar.getInstance().apply { timeInMillis = r.calendarDisplayDate }
            c.get(Calendar.YEAR) == y && c.get(Calendar.MONTH) == m && c.get(Calendar.DAY_OF_MONTH) == day
        }

        repeat(offset7) {
            val v = View(requireContext())
            grid.addView(v, GridLayout.LayoutParams().apply {
                width = 0
                height = cellW
                columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
            })
        }
        for (day in 1..daysInMonth) {
            val dayReminders = remindersForDay(day)
            val cell = FrameLayout(requireContext()).apply {
                layoutParams = GridLayout.LayoutParams().apply {
                    width = 0
                    height = cellW
                    columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
                }
            }
            val tv = TextView(requireContext()).apply {
                text = day.toString()
                gravity = Gravity.CENTER
                textSize = 12f
                setTextColor(ContextCompat.getColor(requireContext(), R.color.text_primary))
            }
            cell.addView(tv, FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            ))
            if (selectedCalDay == day) {
                cell.background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(ContextCompat.getColor(requireContext(), R.color.border))
                    alpha = 80
                }
            }

            if (dayReminders.isNotEmpty()) {
                val top = dayReminders.minWithOrNull(compareBy<Reminder> { it.isPaid }.thenBy { it.dueDate })
                val dotColor = if (top != null) ReminderUiColors.statusColor(requireContext(), top)
                else ContextCompat.getColor(requireContext(), R.color.green_primary)
                val dot = View(requireContext()).apply {
                    val s = dpPx(5)
                    background = GradientDrawable().apply {
                        shape = GradientDrawable.OVAL
                        setColor(dotColor)
                    }
                    layoutParams = FrameLayout.LayoutParams(s, s).apply {
                        gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
                        bottomMargin = dpPx(4)
                    }
                }
                cell.addView(dot)
            }

            cell.isClickable = true
            cell.isFocusable = true
            cell.setOnClickListener {
                selectedCalDay = day
                if (dayReminders.isNotEmpty()) {
                    com.example.hesapyonetimi.presentation.reminders.DayRemindersSheet
                        .show(childFragmentManager, y, m, day, dayReminders)
                }
                buildReminderCalendar(root)
            }
            grid.addView(cell)
        }
    }

    private fun dpPx(dp: Int) = (dp * resources.displayMetrics.density).toInt()

    /** Ödenmemişler üstte; ödenenler altta, soluk + üstü çizili (adapter ile birlikte) */
    private fun sortRemindersForDisplay(list: List<Reminder>): List<Reminder> {
        val unpaid = list.filter { !it.isPaid }.sortedBy { it.dueDate }
        val paid = list.filter { it.isPaid }.sortedBy { it.dueDate }
        return unpaid + paid
    }

    private fun silWithUndo(reminder: com.example.hesapyonetimi.domain.model.Reminder) {
        // Önce sil
        viewModel.deleteReminder(reminder.id)

        Snackbar.make(requireView(), "${reminder.title} silindi", Snackbar.LENGTH_LONG)
            .setAction("Geri Al") {
                // Geri al — aynı bilgilerle yeniden ekle
                viewModel.addReminder(
                    title = reminder.title,
                    amount = reminder.amount,
                    dueDate = reminder.dueDate,
                    categoryId = reminder.categoryId,
                    recurringType = reminder.recurringType,
                    donemSayisi = 1,
                    notificationPolicy = reminder.notificationPolicy
                )
            }
            .setActionTextColor(androidx.core.content.ContextCompat.getColor(requireContext(), R.color.green_primary))
            .show()
    }

    private fun setupFiltre(view: View) {
        val butonlar = listOf(
            view.findViewById<TextView>(R.id.btn_filtre_bekleyen),
            view.findViewById<TextView>(R.id.btn_filtre_gecikmus),
            view.findViewById<TextView>(R.id.btn_filtre_tumu)
        )

        fun sec(idx: Int) {
            aktifFiltre = idx
            butonlar.forEachIndexed { i, btn ->
                val s = i == idx
                btn.setBackgroundResource(if (s) R.drawable.kategori_item_selected_bg else R.drawable.kategori_item_bg)
                btn.setTextColor(if (s)
                    androidx.core.content.ContextCompat.getColor(requireContext(), R.color.green_primary)
                else
                    androidx.core.content.ContextCompat.getColor(requireContext(), R.color.text_secondary))
            }
            updateUI(view, tumReminders)
            if (!yaklasanGorunumListe) buildReminderCalendar(view)
        }

        butonlar[0].setOnClickListener { sec(0) }
        butonlar[1].setOnClickListener { sec(1) }
        butonlar[2].setOnClickListener { sec(2) }
    }
}
