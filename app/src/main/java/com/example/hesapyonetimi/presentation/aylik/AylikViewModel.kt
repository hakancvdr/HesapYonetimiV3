package com.example.hesapyonetimi.presentation.aylik

import android.content.Context
import android.os.Parcelable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.hesapyonetimi.domain.model.Transaction
import com.example.hesapyonetimi.domain.repository.TransactionRepository
import com.example.hesapyonetimi.util.PayPeriodResolver
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.parcelize.Parcelize
import java.util.*
import javax.inject.Inject

@Parcelize
data class KategoriOzet(
    val isIncome: Boolean = false,
    val kategoriId: Long,
    val ad: String,
    val icon: String,
    val toplam: Double,
    val islemSayisi: Int,
    val yuzde: Float,
    val islemler: List<Transaction>,
    val gecenAyToplam: Double = 0.0,
    val degisimYuzde: Float = 0f
) : Parcelable

data class AylikUiState(
    val transactions: List<Transaction> = emptyList(),
    val toplamGider: Double = 0.0,
    val toplamGelir: Double = 0.0,
    val kategoriler: List<KategoriOzet> = emptyList(),
    val gelirKategoriler: List<KategoriOzet> = emptyList(),
    val ayAdi: String = "",
    val ayOffset: Int = 0,
    val donem: Int = 0,
    val gunSayisi: Int = 30,
    val baslangicMillis: Long = 0L,
    val bitisMillis: Long = 0L
)

@HiltViewModel
class AylikViewModel @Inject constructor(
    private val transactionRepository: TransactionRepository,
    @ApplicationContext private val appContext: Context
) : ViewModel() {

    private val _uiState = MutableStateFlow(AylikUiState())
    val uiState: StateFlow<AylikUiState> = _uiState.asStateFlow()

    private var donem = 0
    private var ozelBaslangic: Long? = null
    private var ozelBitis: Long? = null

    // ── Takvim görünümü için ayrı ay state'i ─────────────────────────────────
    private val _calendarOffset = MutableStateFlow(0)
    val calendarOffset: StateFlow<Int> = _calendarOffset.asStateFlow()

    private val _calendarTransactions = MutableStateFlow<List<Transaction>>(emptyList())
    val calendarTransactions: StateFlow<List<Transaction>> = _calendarTransactions.asStateFlow()

    fun nextCalendarMonth() = _calendarOffset.update { it + 1 }
    fun prevCalendarMonth() = _calendarOffset.update { it - 1 }

    init {
        loadData()
        observeCalendarMonth()
    }

    fun setDonem(yeniDonem: Int) {
        donem = yeniDonem
        ozelBaslangic = null
        ozelBitis = null
        loadData()
    }

    fun refresh() = loadData()

    fun setOzelAralik(baslangic: Long, bitis: Long) {
        ozelBaslangic = baslangic
        ozelBitis = bitis
        donem = -2
        loadData()
    }

    private fun loadData() {
        viewModelScope.launch {
            val (start, end, ayAdi, gunSayisi) = getDateRange()
            val (prevStart, prevEnd) = previousComparisonRange()
            val prevTx = transactionRepository.getTransactionsByDateRange(prevStart, prevEnd).first()
            val prevGiderler = prevTx.filter { !it.isIncome }

            transactionRepository.getTransactionsByDateRange(start, end).collect { transactions ->
                val giderler = transactions.filter { !it.isIncome }
                val toplamGider = giderler.sumOf { it.amount }
                val toplamGelir = transactions.filter { it.isIncome }.sumOf { it.amount }

                val kategoriler = giderler.groupBy { it.categoryId }.map { (catId, txs) ->
                    val toplam = txs.sumOf { it.amount }
                    val gecenAy = prevGiderler.filter { it.categoryId == catId }.sumOf { it.amount }
                    val degisim = if (gecenAy > 0) ((toplam - gecenAy) / gecenAy * 100).toFloat() else 0f
                    KategoriOzet(
                        isIncome = false,
                        kategoriId = catId,
                        ad = txs.first().categoryName,
                        icon = txs.first().categoryIcon,
                        toplam = toplam,
                        islemSayisi = txs.size,
                        yuzde = if (toplamGider > 0) (toplam / toplamGider * 100).toFloat() else 0f,
                        islemler = txs.sortedByDescending { it.date },
                        gecenAyToplam = gecenAy,
                        degisimYuzde = degisim
                    )
                }.sortedByDescending { it.toplam }

                val gelirler = transactions.filter { it.isIncome }
                val prevGelirler = prevTx.filter { it.isIncome }
                val gelirKategoriler = gelirler.groupBy { it.categoryId }.map { (catId, txs) ->
                    val toplam = txs.sumOf { it.amount }
                    val gecenAy = prevGelirler.filter { it.categoryId == catId }.sumOf { it.amount }
                    val degisim = if (gecenAy > 0) ((toplam - gecenAy) / gecenAy * 100).toFloat() else 0f
                    KategoriOzet(
                        isIncome = true,
                        kategoriId = catId,
                        ad = txs.first().categoryName,
                        icon = txs.first().categoryIcon,
                        toplam = toplam,
                        islemSayisi = txs.size,
                        yuzde = if (toplamGelir > 0) (toplam / toplamGelir * 100).toFloat() else 0f,
                        islemler = txs.sortedByDescending { it.date },
                        gecenAyToplam = gecenAy,
                        degisimYuzde = degisim
                    )
                }.sortedByDescending { it.toplam }

                _uiState.value = AylikUiState(
                    transactions = transactions,
                    toplamGider = toplamGider,
                    toplamGelir = toplamGelir,
                    kategoriler = kategoriler,
                    gelirKategoriler = gelirKategoriler,
                    ayAdi = ayAdi,
                    ayOffset = if (donem == 1) -1 else 0,
                    donem = donem,
                    gunSayisi = gunSayisi,
                    baslangicMillis = start,
                    bitisMillis = end
                )
            }
        }
    }

    private fun observeCalendarMonth() {
        viewModelScope.launch {
            _calendarOffset.collectLatest { offset ->
                val (start, end) = getCalendarMonthRange(offset)
                transactionRepository.getTransactionsByDateRange(start, end).collect {
                    _calendarTransactions.value = it
                }
            }
        }
    }

    private fun getCalendarMonthRange(offset: Int): Pair<Long, Long> {
        val cal = Calendar.getInstance().apply { add(Calendar.MONTH, offset) }
        cal.set(Calendar.DAY_OF_MONTH, 1)
        cal.set(Calendar.HOUR_OF_DAY, 0); cal.set(Calendar.MINUTE, 0); cal.set(Calendar.SECOND, 0)
        val start = cal.timeInMillis
        cal.set(Calendar.DAY_OF_MONTH, cal.getActualMaximum(Calendar.DAY_OF_MONTH))
        cal.set(Calendar.HOUR_OF_DAY, 23); cal.set(Calendar.MINUTE, 59); cal.set(Calendar.SECOND, 59)
        return start to cal.timeInMillis
    }

    data class DateRange(val start: Long, val end: Long, val label: String, val gunSayisi: Int)

    private fun getDateRange(): DateRange {
        if (ozelBaslangic != null && ozelBitis != null) {
            val gun = ((ozelBitis!! - ozelBaslangic!!) / (1000 * 60 * 60 * 24)).toInt().coerceAtLeast(1)
            return DateRange(ozelBaslangic!!, ozelBitis!!, "Özel Aralık", gun)
        }

        val cal = Calendar.getInstance()
        return when (donem) {
            0 -> {
                val p = PayPeriodResolver.currentPeriod(appContext)
                val gun = PayPeriodResolver.inclusiveDayCount(p)
                val label = PayPeriodResolver.formatShortRange(appContext, p)
                DateRange(p.startMillis, p.endInclusiveMillis(), label, gun)
            }
            1 -> {
                val p = PayPeriodResolver.previousPeriod(appContext)
                val gun = PayPeriodResolver.inclusiveDayCount(p)
                val label = PayPeriodResolver.formatShortRange(appContext, p)
                DateRange(p.startMillis, p.endInclusiveMillis(), label, gun)
            }
            3 -> {
                val end = cal.timeInMillis
                cal.add(Calendar.MONTH, -3)
                DateRange(cal.timeInMillis, end, "Son 3 Ay", 90)
            }
            6 -> {
                val end = cal.timeInMillis
                cal.add(Calendar.MONTH, -6)
                DateRange(cal.timeInMillis, end, "Son 6 Ay", 180)
            }
            else -> DateRange(0L, Long.MAX_VALUE, "Tüm Zamanlar", 365)
        }
    }

    private fun getMonthRange(offset: Int): Pair<Long, Long> {
        val cal = Calendar.getInstance().apply { add(Calendar.MONTH, offset) }
        cal.set(Calendar.DAY_OF_MONTH, 1)
        cal.set(Calendar.HOUR_OF_DAY, 0); cal.set(Calendar.MINUTE, 0); cal.set(Calendar.SECOND, 0)
        val start = cal.timeInMillis
        cal.set(Calendar.DAY_OF_MONTH, cal.getActualMaximum(Calendar.DAY_OF_MONTH))
        cal.set(Calendar.HOUR_OF_DAY, 23); cal.set(Calendar.MINUTE, 59); cal.set(Calendar.SECOND, 59)
        return start to cal.timeInMillis
    }

    /** Önceki dönemle kıyas için aralık (takvim ayı özel filtrelerde eski davranış). */
    private fun previousComparisonRange(): Pair<Long, Long> {
        return when (donem) {
            0 -> {
                val cur = PayPeriodResolver.currentPeriod(appContext)
                val prev = PayPeriodResolver.periodBefore(appContext, cur)
                prev.startMillis to prev.endInclusiveMillis()
            }
            1 -> {
                val prev = PayPeriodResolver.previousPeriod(appContext)
                val beforePrev = PayPeriodResolver.periodBefore(appContext, prev)
                beforePrev.startMillis to beforePrev.endInclusiveMillis()
            }
            else -> getMonthRange(-1)
        }
    }
}
