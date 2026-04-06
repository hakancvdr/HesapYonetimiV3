package com.example.hesapyonetimi

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.ItemTouchHelper
import com.google.android.material.snackbar.Snackbar
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.example.hesapyonetimi.adapter.HatirlaticiAdapter
import com.example.hesapyonetimi.domain.model.Reminder
import com.example.hesapyonetimi.presentation.common.CurrencyFormatter
import com.example.hesapyonetimi.presentation.common.HizliOneri
import com.example.hesapyonetimi.presentation.reminders.HatirlaticiEkleSheet
import com.example.hesapyonetimi.presentation.reminders.ReminderViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import java.util.*

@AndroidEntryPoint
class YaklasanFragment : Fragment() {

    private val viewModel: ReminderViewModel by viewModels()
    private lateinit var adapterBuAy: HatirlaticiAdapter
    private lateinit var adapterSonraki: HatirlaticiAdapter
    private var tumReminders: List<Reminder> = emptyList()
    private var aktifFiltre = 0

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_yaklasan, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        ViewCompat.setOnApplyWindowInsetsListener(view.findViewById(R.id.yaklasan_header)) { v, insets ->
            val sb = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top
            val dp = { n: Int -> (n * resources.displayMetrics.density).toInt() }
            v.setPadding(dp(20), sb + dp(12), dp(20), dp(16))
            insets
        }

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
                // Hatırlatıcı sheet'i aç, bilgileri önceden doldur
                HatirlaticiEkleSheet.newInstance().also { sheet ->
                    sheet.show(childFragmentManager, "HatirlaticiEkle")
                }
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

        view.findViewById<TextView>(R.id.tv_bekleyen_sayi).text = "${bekleyen.size} adet"
        view.findViewById<TextView>(R.id.tv_bekleyen_toplam).text =
            CurrencyFormatter.format(bekleyen.sumOf { it.amount })
        view.findViewById<TextView>(R.id.tv_gecikmus_sayi).text = "${gecikmus.size} adet"

        val filtrelenmis = when (aktifFiltre) {
            1 -> reminders.filter { it.isOverdue }
            2 -> reminders // Tümü — ödenenler dahil
            else -> reminders.filter { !it.isPaid } // Bekleyen — sadece ödenmemiş
        }

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
                    donemSayisi = 1
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
        }

        butonlar[0].setOnClickListener { sec(0) }
        butonlar[1].setOnClickListener { sec(1) }
        butonlar[2].setOnClickListener { sec(2) }
    }
}
