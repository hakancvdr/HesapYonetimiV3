package com.example.hesapyonetimi.presentation.common

import com.example.hesapyonetimi.data.local.dao.UserProfileDao
import com.example.hesapyonetimi.domain.repository.TransactionRepository
import kotlinx.coroutines.flow.first
import java.util.Calendar
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.roundToInt

/**
 * Aylık bütçe limiti ve kategori giderlerine göre kısa metin önerileri.
 */
@Singleton
class BudgetInsightService @Inject constructor(
    private val transactionRepository: TransactionRepository,
    private val userProfileDao: UserProfileDao
) {

    suspend fun monthlyBudgetLines(): List<String> {
        val profile = userProfileDao.getProfileOnce() ?: return emptyList()
        val limit = profile.monthlyBudgetLimit
        if (limit <= 0) return emptyList()

        val (start, end) = currentMonthRange()
        val txs = transactionRepository.getTransactionsByDateRange(start, end).first()
        val expenses = txs.filter { !it.isIncome }
        val totalExpense = expenses.sumOf { it.amount }
        if (totalExpense <= 0) return emptyList()

        val cal = Calendar.getInstance()
        val dayOfMonth = cal.get(Calendar.DAY_OF_MONTH)
        val daysInMonth = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
        val remainingDays = (daysInMonth - dayOfMonth + 1).coerceAtLeast(1)

        val pace = totalExpense / dayOfMonth.toDouble()
        val projected = pace * daysInMonth

        val lines = mutableListOf<String>()

        if (projected > limit * 1.05) {
            lines.add(
                "Bu harcama temposuyla ay sonu giderin yaklaşık ${projected.roundToInt()} ₺ olabilir; aylık limitin ${limit.roundToInt()} ₺."
            )
        }

        val byCat = expenses.groupBy { it.categoryName.ifBlank { "Diğer" } }
            .mapValues { (_, v) -> v.sumOf { it.amount } }
        val top = byCat.maxByOrNull { it.value }
        if (top != null) {
            val share = top.value / limit
            if (share >= 0.35) {
                lines.add(
                    "${top.key} bu ay limitinin yaklaşık %${(share * 100).roundToInt()}'ine denk geliyor; orayı kısmak bütçeyi rahatlatır."
                )
            }
        }

        val dailyBudget = limit / daysInMonth.toDouble()
        if (totalExpense / dayOfMonth > dailyBudget * 1.15) {
            lines.add(
                "Kalan $remainingDays gün için günlük yaklaşık ${(limit - totalExpense).coerceAtLeast(0.0) / remainingDays} ₺ bırakmak dengede kalmanı sağlar."
            )
        }

        return lines.take(3)
    }

    private fun currentMonthRange(): Pair<Long, Long> {
        val cal = Calendar.getInstance()
        cal.set(Calendar.DAY_OF_MONTH, 1)
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        val start = cal.timeInMillis
        cal.set(Calendar.DAY_OF_MONTH, cal.getActualMaximum(Calendar.DAY_OF_MONTH))
        cal.set(Calendar.HOUR_OF_DAY, 23)
        cal.set(Calendar.MINUTE, 59)
        cal.set(Calendar.SECOND, 59)
        cal.set(Calendar.MILLISECOND, 999)
        return start to cal.timeInMillis
    }
}
