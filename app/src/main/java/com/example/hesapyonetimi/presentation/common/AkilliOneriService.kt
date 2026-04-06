package com.example.hesapyonetimi.presentation.common

import com.example.hesapyonetimi.domain.model.Transaction
import com.example.hesapyonetimi.domain.repository.TransactionRepository
import kotlinx.coroutines.flow.first
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton

data class HizliOneri(
    val categoryId: Long,
    val categoryName: String,
    val categoryIcon: String,
    val description: String,
    val amount: Double,
    val isIncome: Boolean,
    val eslesmeSkoru: Int // kaç ayda eşleşti (2 veya 3)
)

@Singleton
class AkilliOneriService @Inject constructor(
    private val transactionRepository: TransactionRepository
) {
    // Bugüne ait hızlı öneriler — ±2 gün toleransla son 3 ayı analiz eder
    suspend fun bugunOneri(): List<HizliOneri> {
        val today = Calendar.getInstance()
        val todayDay = today.get(Calendar.DAY_OF_MONTH)

        // Son 3 ayın verilerini çek
        val uc_ay_once = Calendar.getInstance().apply { add(Calendar.MONTH, -3) }.timeInMillis
        val transactions = transactionRepository
            .getTransactionsByDateRange(uc_ay_once, System.currentTimeMillis())
            .first()

        // Bugüne yakın (±2 gün) işlemleri bul, aya göre grupla
        val ayGruplar = mutableMapOf<String, MutableList<Transaction>>()

        transactions.forEach { tx ->
            val txCal = Calendar.getInstance().apply { timeInMillis = tx.date }
            val txDay = txCal.get(Calendar.DAY_OF_MONTH)
            val txMonth = txCal.get(Calendar.MONTH)
            val txYear = txCal.get(Calendar.YEAR)

            // ±2 gün tolerans — ay sonunu da dikkate al
            val daysInMonth = txCal.getActualMaximum(Calendar.DAY_OF_MONTH)
            val todayDaysInMonth = today.getActualMaximum(Calendar.DAY_OF_MONTH)

            // Normalize: ayın son günü ise "son gün" olarak değerlendir
            val normalizedTxDay = if (txDay == daysInMonth) 99 else txDay
            val normalizedTodayDay = if (todayDay == todayDaysInMonth) 99 else todayDay

            val diff = Math.abs(normalizedTxDay - normalizedTodayDay)
            if (diff > 2) return@forEach

            // Aynı ay içinde birden fazla eşleşmeyi engelle
            val key = "${txYear}_${txMonth}_${tx.categoryId}_${tx.description}_${tx.isIncome}"
            ayGruplar.getOrPut(key) { mutableListOf() }.add(tx)
        }

        // Farklı aylardaki eşleşmeleri say
        // key: categoryId + description + isIncome
        val oneriGrubu = mutableMapOf<String, MutableList<Triple<Int, Transaction, String>>>()
        ayGruplar.forEach { (key, txList) ->
            val parts = key.split("_")
            val year = parts[0].toInt()
            val month = parts[1].toInt()
            val tx = txList.first()
            val oneriKey = "${tx.categoryId}_${tx.description.lowercase().trim()}_${tx.isIncome}"
            oneriGrubu.getOrPut(oneriKey) { mutableListOf() }
                .add(Triple(month, tx, key))
        }

        // En az 2 farklı ayda görünen işlemleri öneri yap
        val oneriler = mutableListOf<HizliOneri>()
        oneriGrubu.forEach { (_, aylarVeTxler) ->
            val farkliAylar = aylarVeTxler.map { it.first }.distinct()
            if (farkliAylar.size < 2) return@forEach

            // Ortalama tutar
            val ortTutar = aylarVeTxler.map { it.second.amount }.average()
            val ornekTx = aylarVeTxler.last().second

            oneriler.add(HizliOneri(
                categoryId = ornekTx.categoryId,
                categoryName = ornekTx.categoryName,
                categoryIcon = ornekTx.categoryIcon,
                description = ornekTx.description,
                amount = ortTutar,
                isIncome = ornekTx.isIncome,
                eslesmeSkoru = farkliAylar.size
            ))
        }

        // En çok eşleşenden sırala
        return oneriler.sortedByDescending { it.eslesmeSkoru }.take(5)
    }
}
