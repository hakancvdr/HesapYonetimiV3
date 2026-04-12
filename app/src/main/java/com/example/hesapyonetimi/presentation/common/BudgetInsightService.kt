package com.example.hesapyonetimi.presentation.common

import android.content.Context
import com.example.hesapyonetimi.R
import com.example.hesapyonetimi.data.local.dao.UserProfileDao
import com.example.hesapyonetimi.domain.repository.TransactionRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import com.example.hesapyonetimi.util.PayPeriod
import com.example.hesapyonetimi.util.PayPeriodResolver
import kotlinx.coroutines.flow.first
import java.util.Calendar
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.roundToInt

data class MonthlyForecastCard(
    val headline: String,
    val line1: String,
    val line2: String
)

/**
 * Aylık bütçe limiti ve kategori giderlerine göre kısa metin önerileri.
 */
@Singleton
class BudgetInsightService @Inject constructor(
    private val transactionRepository: TransactionRepository,
    private val userProfileDao: UserProfileDao,
    @ApplicationContext private val appContext: Context
) {

    /** Ay sonu gider tahmini kartı (sadece bu ay kayıtlı giderler; gelir projeksiyonu yok). */
    suspend fun monthlyForecastCard(): MonthlyForecastCard {
        val profile = userProfileDao.getProfileOnce()
        val limit = profile?.monthlyBudgetLimit ?: 0.0
        if (limit <= 0.0) {
            return MonthlyForecastCard(headline = "", line1 = "", line2 = "")
        }

        val period = PayPeriodResolver.currentPeriod(appContext)
        val (start, end) = period.startMillis to period.endInclusiveMillis()
        val txs = transactionRepository.getTransactionsByDateRange(start, end).first()
        val expenses = txs.filter { !it.isIncome }
        val totalExpense = expenses.sumOf { it.amount }

        val (dayOfMonth, daysInMonth) = payPeriodDayIndexAndLength(period)

        if (totalExpense <= 0.0) {
            return MonthlyForecastCard(
                headline = appContext.getString(R.string.dashboard_forecast_no_expense_headline),
                line1 = appContext.getString(R.string.dashboard_forecast_no_expense_line1),
                line2 = appContext.getString(R.string.dashboard_forecast_assumption_scope)
            )
        }

        val pace = totalExpense / dayOfMonth.toDouble()
        val projected = pace * daysInMonth
        val headline = appContext.getString(
            R.string.dashboard_forecast_headline_pace,
            CurrencyFormatter.format(projected)
        )
        val line1 = appContext.getString(
            R.string.dashboard_forecast_line_limit,
            CurrencyFormatter.format(limit)
        )
        val line2 = buildString {
            append(appContext.getString(R.string.dashboard_forecast_assumption_scope))
            if (projected > limit * 1.05) {
                append("\n")
                append(
                    appContext.getString(
                        R.string.dashboard_forecast_over_limit_hint,
                        CurrencyFormatter.format(limit)
                    )
                )
            }
        }
        return MonthlyForecastCard(headline, line1, line2)
    }

    suspend fun monthlyBudgetLines(): List<String> {
        val profile = userProfileDao.getProfileOnce() ?: return emptyList()
        val limit = profile.monthlyBudgetLimit
        if (limit <= 0) return emptyList()

        val period = PayPeriodResolver.currentPeriod(appContext)
        val (start, end) = period.startMillis to period.endInclusiveMillis()
        val txs = transactionRepository.getTransactionsByDateRange(start, end).first()
        val expenses = txs.filter { !it.isIncome }
        val totalExpense = expenses.sumOf { it.amount }
        if (totalExpense <= 0) return emptyList()

        val (dayOfMonth, daysInMonth) = payPeriodDayIndexAndLength(period)
        val remainingDays = (daysInMonth - dayOfMonth + 1).coerceAtLeast(1)

        val pace = totalExpense / dayOfMonth.toDouble()
        val projected = pace * daysInMonth

        val lines = mutableListOf<String>()

        if (projected > limit * 1.05) {
            lines.add(
                appContext.getString(
                    R.string.budget_insight_pace_over,
                    projected.roundToInt(),
                    limit.roundToInt()
                )
            )
        }

        val byCat = expenses.groupBy { it.categoryName.ifBlank { "Diğer" } }
            .mapValues { (_, v) -> v.sumOf { it.amount } }
        val top = byCat.maxByOrNull { it.value }
        if (top != null) {
            val share = top.value / limit
            if (share >= 0.35) {
                lines.add(
                    appContext.getString(
                        R.string.budget_insight_top_category,
                        top.key,
                        (share * 100.0).roundToInt()
                    )
                )
            }
        }

        val dailyBudget = limit / daysInMonth.toDouble()
        if (totalExpense / dayOfMonth > dailyBudget * 1.15) {
            lines.add(
                appContext.getString(
                    R.string.budget_insight_daily_balance,
                    remainingDays,
                    (limit - totalExpense).coerceAtLeast(0.0) / remainingDays
                )
            )
        }

        return lines.take(3)
    }

    private fun startOfDayMillis(millis: Long): Long {
        return Calendar.getInstance().apply {
            timeInMillis = millis
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }

    private fun inclusiveDayCount(fromStartOfDay: Long, toStartOfDay: Long): Int {
        val diff = ((toStartOfDay - fromStartOfDay) / 86_400_000L).toInt() + 1
        return diff.coerceAtLeast(1)
    }

    /** 1-based day index within pay period, and total length in days. */
    private fun payPeriodDayIndexAndLength(period: PayPeriod): Pair<Int, Int> {
        val today = startOfDayMillis(System.currentTimeMillis())
        val periodStart = startOfDayMillis(period.startMillis)
        val periodEnd = startOfDayMillis(period.endInclusiveMillis())
        val effectiveToday = today.coerceIn(periodStart, periodEnd)
        val lengthDays = inclusiveDayCount(periodStart, periodEnd)
        val dayIndex = inclusiveDayCount(periodStart, effectiveToday).coerceIn(1, lengthDays)
        return dayIndex to lengthDays
    }
}
