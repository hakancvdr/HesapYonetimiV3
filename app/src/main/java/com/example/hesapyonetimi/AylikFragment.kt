package com.example.hesapyonetimi

import android.app.DatePickerDialog
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.GridLayout
import android.widget.HorizontalScrollView
import android.widget.ImageButton
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.hesapyonetimi.presentation.aylik.KategoriIslemleriSheet
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.button.MaterialButton
import com.google.android.material.button.MaterialButtonToggleGroup
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.example.hesapyonetimi.MainActivity
import com.example.hesapyonetimi.adapter.KategoriAnalizAdapter
import com.example.hesapyonetimi.adapter.TimelineAdapter
import com.example.hesapyonetimi.domain.model.Transaction
import com.example.hesapyonetimi.presentation.common.EditTransactionSheet
import com.example.hesapyonetimi.ui.PieChartView
import com.example.hesapyonetimi.presentation.aylik.AylikUiState
import com.example.hesapyonetimi.presentation.aylik.AylikViewModel
import com.example.hesapyonetimi.presentation.common.CurrencyFormatter
import com.google.android.material.textfield.TextInputEditText
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

@AndroidEntryPoint
class AylikFragment : Fragment() {

    private val viewModel: AylikViewModel by viewModels()
    private val tarihFormat = SimpleDateFormat("d MMM yyyy", Locale("tr"))
    private val ayYilFormat = SimpleDateFormat("MMMM yyyy", Locale("tr"))

    private lateinit var tabCalendar: TextView
    private lateinit var tabTimeline: TextView
    private lateinit var tabKategori: TextView
    private lateinit var tabCalendarContent: View
    private lateinit var tabTimelineContent: View
    private lateinit var tabKategoriContent: View

    private val timelineAdapter = TimelineAdapter(
        onItemClick = { tx ->
            EditTransactionSheet.newInstance(tx).show(childFragmentManager, "EditTx")
        }
    )
    private var selectedTab = 0  // 0=İşlem Geçmişi, 1=Takvim, 2=Kategori
    private var showPieChart = true
    private var timelineFiltersExpanded = false

    // Calendar selected day tracking
    private var selectedCalDay = -1
    private var lastCalTransactions: List<Transaction> = emptyList()
    private var lastCalOffset: Int = 0

    companion object {
        private const val STATE_SELECTED_TAB = "aylik_selected_tab"
        private const val STATE_SHOW_PIE = "aylik_show_pie"
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt(STATE_SELECTED_TAB, selectedTab)
        outState.putBoolean(STATE_SHOW_PIE, showPieChart)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? =
        inflater.inflate(R.layout.fragment_aylik, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        view.findViewById<SwipeRefreshLayout>(R.id.swipe_refresh)?.apply {
            setColorSchemeResources(R.color.green_primary)
            setOnRefreshListener { viewModel.refresh(); isRefreshing = false }
        }

        bindTabViews(view)
        setupTabListeners()
        setupCalendarNav(view)
        setupTimelineSearch(view)
        setupDatePickers(view)
        setupDonemButonlari(view)
        setupKategoriTabs(view)
        setupTimelineFiltersToggle(view)

        view.findViewById<RecyclerView>(R.id.rvTimeline).apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = timelineAdapter
        }

        view.findViewById<MaterialButton>(R.id.btnTimelineGoAddTransaction)?.setOnClickListener {
            (activity as? MainActivity)?.gosterGunluk()
        }

        savedInstanceState?.let {
            selectedTab = it.getInt(STATE_SELECTED_TAB, 0).coerceIn(0, 2)
            showPieChart = it.getBoolean(STATE_SHOW_PIE, true)
        }

        observeViewModel(view)
        showTab(selectedTab)
        updateChartToggle(view)
    }

    private fun setupTimelineFiltersToggle(v: View) {
        val container = v.findViewById<View>(R.id.timelineFiltersContainer)
        val btn = v.findViewById<TextView>(R.id.btnToggleTimelineFilters)
        val summary = v.findViewById<TextView>(R.id.tvTimelineFilterSummary)

        fun refresh() {
            container?.visibility = if (timelineFiltersExpanded) View.VISIBLE else View.GONE
            btn?.text = if (timelineFiltersExpanded) "Kapat" else "Filtre"

            val state = viewModel.uiState.value
            val start = tarihFormat.format(Date(state.baslangicMillis))
            val end = tarihFormat.format(Date(state.bitisMillis))
            summary?.text = "${state.ayAdi} · $start → $end"
        }

        btn?.setOnClickListener {
            timelineFiltersExpanded = !timelineFiltersExpanded
            refresh()
        }
        refresh()
    }

    // ── Tab yönetimi ─────────────────────────────────────────────────────────

    private fun bindTabViews(v: View) {
        tabCalendar        = v.findViewById(R.id.tabCalendar)
        tabTimeline        = v.findViewById(R.id.tabTimeline)
        tabKategori        = v.findViewById(R.id.tabKategori)
        tabCalendarContent = v.findViewById(R.id.tabCalendarContent)
        tabTimelineContent = v.findViewById(R.id.tabTimelineContent)
        tabKategoriContent = v.findViewById(R.id.tabKategoriContent)
    }

    private fun setupTabListeners() {
        tabTimeline.setOnClickListener { showTab(0) }
        tabCalendar.setOnClickListener { showTab(1) }
        tabKategori.setOnClickListener { showTab(2) }
    }

    private fun showTab(index: Int) {
        selectedTab = index
        // Tab 0 = İşlem Geçmişi, Tab 1 = Takvim, Tab 2 = Kategori
        tabTimelineContent.visibility = if (index == 0) View.VISIBLE else View.GONE
        tabCalendarContent.visibility = if (index == 1) View.VISIBLE else View.GONE
        tabKategoriContent.visibility = if (index == 2) View.VISIBLE else View.GONE

        if (index != 0) {
            view?.findViewById<View>(R.id.timeline_empty_state)?.visibility = View.GONE
            view?.findViewById<View>(R.id.timeline_no_data_state)?.visibility = View.GONE
        } else {
            view?.let { refreshTimelineEmptyState(it) }
        }

        val tabs = listOf(tabTimeline, tabCalendar, tabKategori)
        tabs.forEachIndexed { i, tv ->
            tv.setBackgroundResource(if (i == index) R.drawable.kategori_item_selected_bg else R.drawable.kategori_item_bg)
            tv.setTextColor(ContextCompat.getColor(requireContext(),
                if (i == index) R.color.green_primary else R.color.text_secondary))
        }
    }

    // ── Takvim görünümü ───────────────────────────────────────────────────────

    private fun setupCalendarNav(v: View) {
        v.findViewById<ImageButton>(R.id.btnPrevMonth).setOnClickListener {
            viewModel.prevCalendarMonth()
        }
        v.findViewById<ImageButton>(R.id.btnNextMonth).setOnClickListener {
            viewModel.nextCalendarMonth()
        }
    }

    private fun buildCalendar(v: View, transactions: List<Transaction>, offset: Int) {
        lastCalTransactions = transactions
        lastCalOffset = offset

        val cal = Calendar.getInstance().apply { add(Calendar.MONTH, offset) }
        v.findViewById<TextView>(R.id.tvCalendarMonth).text = ayYilFormat.format(cal.time)

        val txByDay = transactions.groupBy { tx ->
            Calendar.getInstance().apply { timeInMillis = tx.date }.get(Calendar.DAY_OF_MONTH)
        }
        val expenseByDay = transactions.filter { !it.isIncome }.groupBy { tx ->
            Calendar.getInstance().apply { timeInMillis = tx.date }.get(Calendar.DAY_OF_MONTH)
        }.mapValues { (_, txs) -> txs.sumOf { it.amount } }
        val maxExpense = expenseByDay.values.maxOrNull() ?: 1.0

        val grid = v.findViewById<GridLayout>(R.id.calendarGrid)
        grid.removeAllViews()

        cal.set(Calendar.DAY_OF_MONTH, 1)
        val firstDow = cal.get(Calendar.DAY_OF_WEEK)
        val offset7  = (firstDow - 2 + 7) % 7
        val daysInMonth = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
        val cellPx = ((resources.displayMetrics.widthPixels -
                dpToPx(32)) / 7).coerceAtLeast(dpToPx(36))

        val todayCal = Calendar.getInstance()
        val isThisMonth = cal.get(Calendar.YEAR)  == todayCal.get(Calendar.YEAR) &&
                          cal.get(Calendar.MONTH) == todayCal.get(Calendar.MONTH)
        val today = todayCal.get(Calendar.DAY_OF_MONTH)

        // Boş hücreler (ay başı boşluk)
        repeat(offset7) { grid.addView(makeEmptyCell(cellPx)) }

        // Gün hücreleri
        for (day in 1..daysInMonth) {
            val hasTx     = txByDay.containsKey(day)
            val expense   = expenseByDay[day] ?: 0.0
            val intensity = (expense / maxExpense).toFloat().coerceIn(0f, 1f)
            val isToday   = isThisMonth && day == today
            val isSelected = day == selectedCalDay
            val cell = makeDayCell(day, hasTx, intensity, isToday, isSelected) {
                selectedCalDay = day
                buildCalendar(v, lastCalTransactions, lastCalOffset)
                showSelectedDay(v, day, txByDay[day] ?: emptyList())
            }
            grid.addView(cell)
        }
    }

    private fun makeEmptyCell(sizePx: Int): View = View(requireContext()).apply {
        layoutParams = GridLayout.LayoutParams().apply {
            width = sizePx; height = sizePx
            columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1, 1f)
        }
    }

    private fun makeDayCell(
        day: Int, hasTx: Boolean, intensity: Float, isToday: Boolean, isSelected: Boolean,
        onClick: () -> Unit
    ): FrameLayout {
        val sizePx = ((resources.displayMetrics.widthPixels - dpToPx(32)) / 7).coerceAtLeast(dpToPx(36))

        val cell = FrameLayout(requireContext()).apply {
            layoutParams = GridLayout.LayoutParams().apply {
                width = sizePx; height = sizePx
                columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1, 1f)
            }
            setOnClickListener { onClick() }
        }

        when {
            isSelected -> {
                cell.background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(Color.parseColor("#2979FF"))
                }
            }
            isToday -> {
                cell.background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(ContextCompat.getColor(requireContext(), R.color.green_primary))
                    alpha = 40
                }
            }
        }

        val tvDay = TextView(requireContext()).apply {
            text = day.toString()
            gravity = android.view.Gravity.CENTER
            textSize = 13f
            setTextColor(when {
                isSelected -> Color.WHITE
                isToday    -> ContextCompat.getColor(requireContext(), R.color.green_primary)
                hasTx      -> ContextCompat.getColor(requireContext(), R.color.text_primary)
                else       -> ContextCompat.getColor(requireContext(), R.color.text_secondary)
            })
            if (isToday || isSelected) setTypeface(null, android.graphics.Typeface.BOLD)
        }
        cell.addView(tvDay, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))

        if (hasTx) {
            val dotColor = interpolateColor(
                Color.parseColor("#66BB6A"),
                Color.parseColor("#EF5350"),
                intensity
            )
            val dot = View(requireContext()).apply {
                val dotSize = dpToPx(5)
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(dotColor)
                }
                layoutParams = FrameLayout.LayoutParams(dotSize, dotSize).apply {
                    gravity = android.view.Gravity.BOTTOM or android.view.Gravity.CENTER_HORIZONTAL
                    bottomMargin = dpToPx(4)
                }
            }
            cell.addView(dot)
        }

        return cell
    }

    private fun showSelectedDay(v: View, day: Int, transactions: List<Transaction>) {
        val card  = v.findViewById<View>(R.id.selectedDayCard)
        val title = v.findViewById<TextView>(R.id.tvSelectedDayTitle)
        val rv    = v.findViewById<RecyclerView>(R.id.rvSelectedDayTx)

        if (transactions.isEmpty()) {
            card.visibility = View.GONE
            return
        }
        card.visibility = View.VISIBLE
        val timeFmt = java.text.SimpleDateFormat("HH:mm", Locale.getDefault())
        title.text = "$day ${ayYilFormat.format(Calendar.getInstance().time).split(" ")[0]}" +
                " — ${transactions.size} işlem"
        rv.layoutManager = LinearLayoutManager(requireContext())
        // Bireysel işlemleri göster, tıklanabilir
        rv.adapter = com.example.hesapyonetimi.adapter.TransactionAdapter(
            transactions.map { t ->
                com.example.hesapyonetimi.model.TransactionModel(
                    id = t.id,
                    title = t.description.ifBlank { t.categoryName },
                    category = "${t.categoryIcon} ${t.categoryName}",
                    amount = com.example.hesapyonetimi.presentation.common.CurrencyFormatter.formatWithSign(t.amount, t.isIncome),
                    isIncome = t.isIncome,
                    time = timeFmt.format(java.util.Date(t.date)),
                    transaction = t
                )
            }
        ) { tx ->
            EditTransactionSheet.newInstance(tx).show(childFragmentManager, "EditTxDay")
        }
    }

    // ── Timeline arama ────────────────────────────────────────────────────────

    private fun setupTimelineSearch(v: View) {
        val et = v.findViewById<TextInputEditText>(R.id.etTimelineSearch)
        et.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                timelineAdapter.filter(s?.toString() ?: "")
                refreshTimelineEmptyState(v)
            }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })
        v.findViewById<MaterialButton>(R.id.btnTimelineClearSearch).setOnClickListener {
            et.setText("")
            timelineAdapter.filter("")
            refreshTimelineEmptyState(v)
        }
    }

    private fun updateTimelineTagChips(v: View, transactions: List<Transaction>) {
        val scroll = v.findViewById<HorizontalScrollView>(R.id.timeline_tag_scroll) ?: return
        val group = v.findViewById<ChipGroup>(R.id.chipGroupTimelineTags) ?: return
        group.setOnCheckedChangeListener(null)
        group.removeAllViews()
        val tagSet = transactions.flatMap { tx ->
            tx.tags.split(",").map { it.trim() }.filter { it.isNotEmpty() }
        }.toMutableSet()
        val tags = tagSet.toSortedSet(String.CASE_INSENSITIVE_ORDER).toList()
        if (tags.isEmpty()) {
            scroll.visibility = View.GONE
            timelineAdapter.filterByTag(null)
            return
        }
        scroll.visibility = View.VISIBLE
        val ctx = requireContext()
        fun addChip(label: String, tagValue: String) {
            val chip = Chip(ctx)
            chip.text = label
            chip.tag = tagValue
            chip.isCheckable = true
            chip.id = View.generateViewId()
            group.addView(chip)
        }
        addChip(getString(R.string.tag_filter_all), "")
        tags.forEach { addChip(it, it) }
        group.check(group.getChildAt(0).id)
        group.setOnCheckedChangeListener { _, checkedId ->
            if (checkedId == View.NO_ID) {
                timelineAdapter.filterByTag(null)
            } else {
                val chip = group.findViewById<Chip>(checkedId)
                val raw = chip?.tag as? String
                timelineAdapter.filterByTag(raw?.ifBlank { null })
            }
        }
    }

    private fun refreshTimelineEmptyState(v: View) {
        if (selectedTab != 0) return
        val searchEmpty = timelineAdapter.shouldShowSearchEmpty()
        val periodEmpty = timelineAdapter.shouldShowPeriodEmpty()
        v.findViewById<View>(R.id.timeline_empty_state).visibility =
            if (searchEmpty) View.VISIBLE else View.GONE
        v.findViewById<View>(R.id.timeline_no_data_state).visibility =
            if (periodEmpty && !searchEmpty) View.VISIBLE else View.GONE
    }

    // ── Kategori tabı ─────────────────────────────────────────────────────────

    private fun setupKategoriTabs(v: View) {
        v.findViewById<TextView>(R.id.tabKatGider).setOnClickListener { updateKatTab(v, false) }
        v.findViewById<TextView>(R.id.tabKatGelir).setOnClickListener { updateKatTab(v, true) }

        v.findViewById<MaterialButtonToggleGroup>(R.id.chartTypeToggle)
            .addOnButtonCheckedListener { _, checkedId, isChecked ->
                if (!isChecked) return@addOnButtonCheckedListener
                showPieChart = checkedId == R.id.btnChartPie
                v.findViewById<PieChartView>(R.id.pieChart).setMode(!showPieChart)
                val state = viewModel.uiState.value
                val btnGider = v.findViewById<TextView>(R.id.tabKatGider)
                val showIncome = btnGider.background.constantState ==
                    ContextCompat.getDrawable(requireContext(), R.drawable.kategori_item_bg)?.constantState
                val list = if (showIncome) state.gelirKategoriler else state.kategoriler
                updatePieChart(v, list)
            }
    }

    private fun updateChartToggle(v: View) {
        v.findViewById<MaterialButtonToggleGroup>(R.id.chartTypeToggle)?.let { t ->
            val want = if (showPieChart) R.id.btnChartPie else R.id.btnChartBar
            if (t.checkedButtonId != want) t.check(want)
        }
    }

    private fun updateKatTab(v: View, showIncome: Boolean) {
        val btnGider = v.findViewById<TextView>(R.id.tabKatGider)
        val btnGelir = v.findViewById<TextView>(R.id.tabKatGelir)

        btnGider.setBackgroundResource(if (!showIncome) R.drawable.kategori_item_selected_bg else R.drawable.kategori_item_bg)
        btnGider.setTextColor(ContextCompat.getColor(requireContext(), if (!showIncome) R.color.green_primary else R.color.text_secondary))
        btnGelir.setBackgroundResource(if (showIncome)  R.drawable.kategori_item_selected_bg else R.drawable.kategori_item_bg)
        btnGelir.setTextColor(ContextCompat.getColor(requireContext(), if (showIncome) R.color.green_primary else R.color.text_secondary))

        val state = viewModel.uiState.value
        val list  = if (showIncome) state.gelirKategoriler else state.kategoriler
        v.findViewById<RecyclerView>(R.id.rvKatAnaliz).adapter =
            KategoriAnalizAdapter(list) { kat ->
                KategoriIslemleriSheet.show(childFragmentManager, kat, state.ayOffset)
            }
        updatePieChart(v, list)
    }

    private val chartColors = listOf(
        Color.parseColor("#6B8FFF"), Color.parseColor("#4FC3F7"), Color.parseColor("#10B981"),
        Color.parseColor("#F59E0B"), Color.parseColor("#EF5350"), Color.parseColor("#A78BFA"),
        Color.parseColor("#F472B6"), Color.parseColor("#22C55E"), Color.parseColor("#38BDF8"),
        Color.parseColor("#FB7185")
    )

    private fun updatePieChart(v: View, list: List<com.example.hesapyonetimi.presentation.aylik.KategoriOzet>) {
        val chart = v.findViewById<PieChartView>(R.id.pieChart) ?: return
        if (list.isEmpty()) { chart.visibility = View.GONE; return }
        chart.visibility = View.VISIBLE
        chart.setMode(!showPieChart)
        val entries = list.mapIndexed { i, kat ->
            PieChartView.PieEntry(
                value = kat.toplam.toFloat(),
                color = chartColors[i % chartColors.size],
                label = kat.ad
            )
        }
        chart.setData(entries)
        // Dilime tıklandığında kategori detay aç
        chart.onSliceTap = sliceTap@{ entry ->
            val kat = list.find { it.ad == entry.label } ?: return@sliceTap
            KategoriIslemleriSheet.show(childFragmentManager, kat, viewModel.uiState.value.ayOffset)
        }
    }

    // ── Observer ─────────────────────────────────────────────────────────────

    private fun observeViewModel(view: View) {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {

                launch {
                    viewModel.uiState.collect { state ->
                        updateHeader(view, state)
                        updateDateLabels(view, state)
                        timelineAdapter.submitList(state.transactions)
                        updateTimelineTagChips(view, state.transactions)
                        refreshTimelineEmptyState(view)
                        updateKatList(view, state)
                        updateOneri(view, state)
                    }
                }

                launch {
                    var prevOffset = Int.MIN_VALUE
                    kotlinx.coroutines.flow.combine(
                        viewModel.calendarTransactions,
                        viewModel.calendarOffset
                    ) { txs, offset -> txs to offset }.collect { (txs, offset) ->
                        if (offset != prevOffset) { selectedCalDay = -1; prevOffset = offset }
                        buildCalendar(view, txs, offset)
                    }
                }
            }
        }
    }

    private fun updateHeader(v: View, state: AylikUiState) {
        v.findViewById<TextView>(R.id.tv_toplam_gelir).text = CurrencyFormatter.format(state.toplamGelir)
        v.findViewById<TextView>(R.id.tv_toplam_gider).text = CurrencyFormatter.format(state.toplamGider)
        val net = state.toplamGelir - state.toplamGider
        val tvNet = v.findViewById<TextView>(R.id.tv_net)
        tvNet.text = CurrencyFormatter.format(net)
        tvNet.setTextColor(if (net >= 0) Color.parseColor("#A5F3BB") else Color.parseColor("#FFAAAA"))
        val ozetLine = v.findViewById<TextView>(R.id.tv_ay_ozet_line)
        // Header kartları zaten Gelir/Gider/Net veriyor; burada sadece dönem adı yeterli.
        ozetLine?.text = state.ayAdi
    }

    private fun updateDateLabels(v: View, state: AylikUiState) {
        v.findViewById<TextView>(R.id.tv_baslangic_tarih).text = tarihFormat.format(Date(state.baslangicMillis))
        v.findViewById<TextView>(R.id.tv_bitis_tarih).text     = tarihFormat.format(Date(state.bitisMillis))
    }

    private fun updateKatList(v: View, state: AylikUiState) {
        val rv = v.findViewById<RecyclerView>(R.id.rvKatAnaliz) ?: return
        val btnGider = v.findViewById<TextView>(R.id.tabKatGider)
        val showIncome = btnGider.background.constantState ==
            ContextCompat.getDrawable(requireContext(), R.drawable.kategori_item_bg)?.constantState
        val list = if (showIncome) state.gelirKategoriler else state.kategoriler
        rv.adapter = KategoriAnalizAdapter(list) { kat ->
            KategoriIslemleriSheet.show(childFragmentManager, kat, state.ayOffset)
        }
        updatePieChart(v, list)
    }

    private fun updateOneri(v: View, state: AylikUiState) {
        val row   = v.findViewById<View>(R.id.katOneriRow) ?: return
        val emoji = v.findViewById<TextView>(R.id.tv_oneri_emoji)
        val metin = v.findViewById<TextView>(R.id.tv_oneri_metin)
        val enCok = state.kategoriler.firstOrNull()
        when {
            state.transactions.isEmpty() -> {
                row.visibility = View.GONE
            }
            state.toplamGider > state.toplamGelir -> {
                row.visibility = View.VISIBLE
                emoji.text = "⚠️"
                val fark = CurrencyFormatter.format(state.toplamGider - state.toplamGelir)
                metin.text = "Giderler geliri $fark aştı. ${enCok?.let { "En büyük kalem: ${it.ad}" } ?: ""}"
            }
            enCok != null && enCok.degisimYuzde > 20 -> {
                row.visibility = View.VISIBLE
                emoji.text = "📈"
                metin.text = "${enCok.ad} harcaman geçen aya göre %${enCok.degisimYuzde.toInt()} arttı."
            }
            state.toplamGider < state.toplamGelir * 0.5 -> {
                row.visibility = View.VISIBLE
                emoji.text = "💰"
                metin.text = "Gelirinizin yarısından azını harcadınız. Tasarruf oranınız yüksek!"
            }
            else -> {
                row.visibility = View.VISIBLE
                emoji.text = "💡"
                val oran = if (state.toplamGelir > 0)
                    (state.toplamGider / state.toplamGelir * 100).toInt() else 0
                metin.text = "Harcamalar gelirin %$oran'i."
            }
        }
    }

    // ── Dönem / tarih seçici ─────────────────────────────────────────────────

    private fun setupDatePickers(view: View) {
        view.findViewById<TextView>(R.id.tv_baslangic_tarih).setOnClickListener {
            val c = Calendar.getInstance().apply { timeInMillis = viewModel.uiState.value.baslangicMillis }
            DatePickerDialog(requireContext(), { _, y, m, d ->
                val sel = Calendar.getInstance().apply { set(y, m, d, 0, 0, 0) }
                viewModel.setOzelAralik(sel.timeInMillis, viewModel.uiState.value.bitisMillis)
                seciliDonemTemizle(view)
            }, c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH)).show()
        }
        view.findViewById<TextView>(R.id.tv_bitis_tarih).setOnClickListener {
            val c = Calendar.getInstance().apply { timeInMillis = viewModel.uiState.value.bitisMillis }
            DatePickerDialog(requireContext(), { _, y, m, d ->
                val sel = Calendar.getInstance().apply { set(y, m, d, 23, 59, 59) }
                viewModel.setOzelAralik(viewModel.uiState.value.baslangicMillis, sel.timeInMillis)
                seciliDonemTemizle(view)
            }, c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH)).show()
        }
    }

    private fun seciliDonemTemizle(view: View) {
        listOf(R.id.btn_donem_1a, R.id.btn_donem_3a, R.id.btn_donem_6a, R.id.btn_donem_tumu).forEach { id ->
            view.findViewById<TextView>(id).apply {
                setBackgroundResource(R.drawable.kategori_item_bg)
                setTextColor(ContextCompat.getColor(requireContext(), R.color.text_secondary))
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
                btn.setTextColor(ContextCompat.getColor(requireContext(),
                    if (secili) R.color.green_primary else R.color.text_secondary))
            }
        }
        // UI ve data state'i aynı anda sıfırlansın (Bu Ay)
        guncelle(0)
        viewModel.setDonem(0)
        butonlar.forEach { (btn, donem) ->
            btn.setOnClickListener { guncelle(donem); viewModel.setDonem(donem) }
        }
    }

    // ── Yardımcı ─────────────────────────────────────────────────────────────

    private fun dpToPx(dp: Int) = (dp * resources.displayMetrics.density).toInt()

    private fun interpolateColor(start: Int, end: Int, t: Float): Int {
        val f = t.coerceIn(0f, 1f)
        return Color.rgb(
            (Color.red(start)   + (Color.red(end)   - Color.red(start))   * f).toInt(),
            (Color.green(start) + (Color.green(end) - Color.green(start)) * f).toInt(),
            (Color.blue(start)  + (Color.blue(end)  - Color.blue(start))  * f).toInt()
        )
    }
}
