package com.example.hesapyonetimi

import android.app.DatePickerDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.example.hesapyonetimi.adapter.KategoriAnalizAdapter
import com.example.hesapyonetimi.presentation.aylik.AylikUiState
import com.example.hesapyonetimi.presentation.aylik.AylikViewModel
import com.example.hesapyonetimi.presentation.aylik.KategoriDetayFragment
import com.example.hesapyonetimi.presentation.common.CurrencyFormatter
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

@AndroidEntryPoint
class AylikFragment : Fragment() {

    private val viewModel: AylikViewModel by viewModels()
    private val tarihFormat = SimpleDateFormat("d MMM yyyy", Locale("tr"))

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_aylik, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        ViewCompat.setOnApplyWindowInsetsListener(view.findViewById(R.id.aylik_header)) { v, insets ->
            val sb = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top
            val dp = { n: Int -> (n * resources.displayMetrics.density).toInt() }
            v.setPadding(dp(20), sb + dp(12), dp(20), dp(16))
            insets
        }

        // Pull-to-refresh
        view.findViewById<SwipeRefreshLayout>(R.id.swipe_refresh)?.apply {
            setColorSchemeResources(R.color.green_primary)
            setOnRefreshListener { viewModel.refresh(); isRefreshing = false }
        }

        // Manuel tarih seçici
        setupTarihSecici(view)

        // Dönem butonları
        setupDonemButonlari(view)

        val rvKategoriler = view.findViewById<RecyclerView>(R.id.rv_kategoriler)
        rvKategoriler.layoutManager = LinearLayoutManager(requireContext())

        val rvGelirKategoriler = view.findViewById<RecyclerView>(R.id.rv_gelir_kategoriler)
        rvGelirKategoriler?.layoutManager = LinearLayoutManager(requireContext())

        val btnGiderSekme = view.findViewById<android.widget.TextView>(R.id.btn_gider_sekme)
        val btnGelirSekme = view.findViewById<android.widget.TextView>(R.id.btn_gelir_sekme)
        var giderSecili = true

        fun updateSekme() {
            rvKategoriler.visibility = if (giderSecili) android.view.View.VISIBLE else android.view.View.GONE
            rvGelirKategoriler?.visibility = if (!giderSecili) android.view.View.VISIBLE else android.view.View.GONE
            btnGiderSekme?.setBackgroundResource(if (giderSecili) R.drawable.kategori_item_selected_bg else R.drawable.kategori_item_bg)
            btnGiderSekme?.setTextColor(androidx.core.content.ContextCompat.getColor(requireContext(), if (giderSecili) R.color.green_primary else R.color.text_secondary))
            btnGelirSekme?.setBackgroundResource(if (!giderSecili) R.drawable.kategori_item_selected_bg else R.drawable.kategori_item_bg)
            btnGelirSekme?.setTextColor(androidx.core.content.ContextCompat.getColor(requireContext(), if (!giderSecili) R.color.green_primary else R.color.text_secondary))
        }
        updateSekme()
        btnGiderSekme?.setOnClickListener { giderSecili = true; updateSekme() }
        btnGelirSekme?.setOnClickListener { giderSecili = false; updateSekme() }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    updateUI(view, state)
                    rvKategoriler.adapter = KategoriAnalizAdapter(state.kategoriler) { kat ->
                        val detay = KategoriDetayFragment.newInstance(kat, state.ayOffset)
                        (requireActivity() as MainActivity).showKategoriDetay(detay)
                    }
                    rvGelirKategoriler?.adapter = KategoriAnalizAdapter(state.gelirKategoriler) { kat ->
                        val detay = KategoriDetayFragment.newInstance(kat, state.ayOffset)
                        (requireActivity() as MainActivity).showKategoriDetay(detay)
                    }
                }
            }
        }
    }

    private fun setupTarihSecici(view: View) {
        val tvBaslangic = view.findViewById<TextView>(R.id.tv_baslangic_tarih)
        val tvBitis = view.findViewById<TextView>(R.id.tv_bitis_tarih)

        tvBaslangic.setOnClickListener {
            val current = Calendar.getInstance().apply { timeInMillis = viewModel.uiState.value.baslangicMillis }
            DatePickerDialog(requireContext(), { _, y, m, d ->
                val sec = Calendar.getInstance().apply { set(y, m, d, 0, 0, 0) }
                viewModel.setOzelAralik(sec.timeInMillis, viewModel.uiState.value.bitisMillis)
                seciliDonemTemizle(view)
            }, current.get(Calendar.YEAR), current.get(Calendar.MONTH), current.get(Calendar.DAY_OF_MONTH)).show()
        }

        tvBitis.setOnClickListener {
            val current = Calendar.getInstance().apply { timeInMillis = viewModel.uiState.value.bitisMillis }
            DatePickerDialog(requireContext(), { _, y, m, d ->
                val sec = Calendar.getInstance().apply { set(y, m, d, 23, 59, 59) }
                viewModel.setOzelAralik(viewModel.uiState.value.baslangicMillis, sec.timeInMillis)
                seciliDonemTemizle(view)
            }, current.get(Calendar.YEAR), current.get(Calendar.MONTH), current.get(Calendar.DAY_OF_MONTH)).show()
        }
    }

    private fun seciliDonemTemizle(view: View) {
        listOf(R.id.btn_donem_1a, R.id.btn_donem_3a, R.id.btn_donem_6a, R.id.btn_donem_tumu).forEach { id ->
            view.findViewById<TextView>(id).apply {
                setBackgroundResource(R.drawable.kategori_item_bg)
                setTextColor(androidx.core.content.ContextCompat.getColor(requireContext(), R.color.text_secondary))
            }
        }
    }

    private fun updateUI(view: View, state: AylikUiState) {
        view.findViewById<TextView>(R.id.tv_toplam_gelir).text = CurrencyFormatter.format(state.toplamGelir)
        view.findViewById<TextView>(R.id.tv_toplam_gider).text = CurrencyFormatter.format(state.toplamGider)
        val net = state.toplamGelir - state.toplamGider
        view.findViewById<TextView>(R.id.tv_net).text = CurrencyFormatter.format(net)

        view.findViewById<TextView>(R.id.tv_islem_sayisi).text = "${state.transactions.size} adet"
        val gunlukOrt = state.toplamGider / state.gunSayisi.coerceAtLeast(1)
        view.findViewById<TextView>(R.id.tv_gunluk_ort).text = CurrencyFormatter.format(gunlukOrt)

        // Tarih — her zaman ViewModel'den göster
        view.findViewById<TextView>(R.id.tv_baslangic_tarih).text = tarihFormat.format(Date(state.baslangicMillis))
        view.findViewById<TextView>(R.id.tv_bitis_tarih).text = tarihFormat.format(Date(state.bitisMillis))

        // Gelişmiş öneri
        val tvEmoji = view.findViewById<TextView>(R.id.tv_oneri_emoji)
        val tvMetin = view.findViewById<TextView>(R.id.tv_oneri_metin)
        val enCokKat = state.kategoriler.firstOrNull()
        when {
            state.transactions.isEmpty() -> { tvEmoji.text = "📊"; tvMetin.text = "Bu dönem için henüz veri yok." }
            state.toplamGider > state.toplamGelir -> {
                tvEmoji.text = "⚠️"
                val fark = CurrencyFormatter.format(state.toplamGider - state.toplamGelir)
                tvMetin.text = "Giderler geliri $fark aştı. ${enCokKat?.let { "En büyük kalem: ${it.ad} (${CurrencyFormatter.format(it.toplam)})" } ?: ""}"
            }
            enCokKat != null && enCokKat.degisimYuzde > 20 -> {
                tvEmoji.text = "📈"
                tvMetin.text = "${enCokKat.ad} harcaman geçen aya göre %${enCokKat.degisimYuzde.toInt()} arttı."
            }
            enCokKat != null && enCokKat.degisimYuzde < -10 -> {
                tvEmoji.text = "🎉"
                tvMetin.text = "${enCokKat.ad} harcamanı %${(-enCokKat.degisimYuzde).toInt()} azalttın, aferin!"
            }
            state.toplamGider < state.toplamGelir * 0.5 -> {
                tvEmoji.text = "💰"
                tvMetin.text = "Gelirinizin yarısından azını harcadınız. Tasarruf oranınız yüksek!"
            }
            else -> {
                tvEmoji.text = "💡"
                val oran = (state.toplamGider / state.toplamGelir * 100).toInt()
                tvMetin.text = "Harcamalar gelirin %$oran'i. ${if (oran > 80) "Dikkatli olun." else "Makul seviyede."}"
            }
        }
    }

    private fun setupDonemButonlari(view: View) {
        val butonlar = listOf(
            view.findViewById<TextView>(R.id.btn_donem_1a) to 0,
            view.findViewById<TextView>(R.id.btn_donem_3a) to 1,
            view.findViewById<TextView>(R.id.btn_donem_6a) to 3,
            view.findViewById<TextView>(R.id.btn_donem_tumu) to 6
        )

        fun guncelle(secilenDonem: Int) {
            butonlar.forEach { (btn, donem) ->
                val secili = donem == secilenDonem
                btn.setBackgroundResource(if (secili) R.drawable.kategori_item_selected_bg else R.drawable.kategori_item_bg)
                btn.setTextColor(
                    if (secili) androidx.core.content.ContextCompat.getColor(requireContext(), R.color.green_primary)
                    else androidx.core.content.ContextCompat.getColor(requireContext(), R.color.text_secondary)
                )
            }
        }

        guncelle(0)
        butonlar.forEach { (btn, donem) ->
            btn.setOnClickListener { guncelle(donem); viewModel.setDonem(donem) }
        }
    }
}
