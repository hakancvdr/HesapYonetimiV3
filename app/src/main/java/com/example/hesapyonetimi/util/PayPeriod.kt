package com.example.hesapyonetimi.util

import android.content.Context
import com.example.hesapyonetimi.auth.AuthPrefs
import java.util.Calendar

data class PayPeriod(
    val startMillis: Long,
    /** First instant after the period (exclusive upper bound). */
    val endExclusiveMillis: Long
) {
    fun endInclusiveMillis(): Long = (endExclusiveMillis - 1L).coerceAtLeast(startMillis)
}

object PayPeriodResolver {

    fun currentPeriod(ctx: Context): PayPeriod {
        val mode = AuthPrefs.getPayPeriodMode(ctx)
        val dom = AuthPrefs.getSalaryDayOfMonth(ctx)
        return PayPeriodCalculator.containingPeriod(System.currentTimeMillis(), mode, dom)
    }

    /** Dönem bitmeden hemen önceki an: bir önceki bütçe dönemi. */
    fun periodBefore(ctx: Context, period: PayPeriod): PayPeriod {
        val mode = AuthPrefs.getPayPeriodMode(ctx)
        val dom = AuthPrefs.getSalaryDayOfMonth(ctx)
        val anchor = (period.startMillis - 1L).coerceAtLeast(0L)
        return PayPeriodCalculator.containingPeriod(anchor, mode, dom)
    }

    fun previousPeriod(ctx: Context): PayPeriod = periodBefore(ctx, currentPeriod(ctx))

    fun formatShortRange(ctx: Context, period: PayPeriod): String {
        val f = java.text.DateFormat.getDateInstance(java.text.DateFormat.MEDIUM, ctx.resources.configuration.locales[0])
        return "${f.format(period.startMillis)} — ${f.format(period.endInclusiveMillis())}"
    }

    fun inclusiveDayCount(period: PayPeriod): Int {
        val a = startOfDay(period.startMillis)
        val b = startOfDay(period.endInclusiveMillis())
        return (((b - a) / 86_400_000L).toInt() + 1).coerceAtLeast(1)
    }

    /** Bütçe döneminin başından bugüne 1 tabanlı gün sırası (ilk gün = 1). */
    fun currentDayIndexInPeriod(ctx: Context): Int {
        val p = currentPeriod(ctx)
        val start = startOfDay(p.startMillis)
        val today = startOfDay(System.currentTimeMillis())
        return (((today - start) / 86_400_000L).toInt() + 1).coerceAtLeast(1)
    }

    fun startOfDay(millis: Long): Long {
        val c = Calendar.getInstance().apply {
            timeInMillis = millis
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        return c.timeInMillis
    }
}

object PayPeriodCalculator {

    fun containingPeriod(anchorMillis: Long, mode: String, salaryDayOfMonth: Int): PayPeriod {
        return if (mode == AuthPrefs.PAY_PERIOD_MODE_SALARY) {
            salaryContaining(anchorMillis, salaryDayOfMonth.coerceIn(1, 31))
        } else {
            calendarContaining(anchorMillis)
        }
    }

    private fun calendarContaining(anchor: Long): PayPeriod {
        val cal = Calendar.getInstance().apply {
            timeInMillis = anchor
            truncateToDayStart()
            set(Calendar.DAY_OF_MONTH, 1)
        }
        val start = cal.timeInMillis
        cal.add(Calendar.MONTH, 1)
        return PayPeriod(start, cal.timeInMillis)
    }

    private fun salaryContaining(anchor: Long, dom: Int): PayPeriod {
        val c = Calendar.getInstance().apply {
            timeInMillis = anchor
            truncateToDayStart()
        }
        var s = startOfSalaryMonth(c.get(Calendar.YEAR), c.get(Calendar.MONTH), dom)
        if (anchor < s) {
            c.add(Calendar.MONTH, -1)
            s = startOfSalaryMonth(c.get(Calendar.YEAR), c.get(Calendar.MONTH), dom)
        }
        var e = nextSalaryBoundary(s, dom)
        while (anchor >= e) {
            s = e
            e = nextSalaryBoundary(s, dom)
        }
        return PayPeriod(s, e)
    }

    private fun nextSalaryBoundary(startMillis: Long, dom: Int): Long {
        val c = Calendar.getInstance().apply {
            timeInMillis = startMillis
            truncateToDayStart()
            add(Calendar.MONTH, 1)
        }
        return startOfSalaryMonth(c.get(Calendar.YEAR), c.get(Calendar.MONTH), dom)
    }

    private fun startOfSalaryMonth(year: Int, month: Int, dom: Int): Long {
        val c = Calendar.getInstance().apply {
            clear()
            set(year, month, 1, 0, 0, 0)
            set(Calendar.MILLISECOND, 0)
            val max = getActualMaximum(Calendar.DAY_OF_MONTH)
            val day = dom.coerceIn(1, max)
            set(Calendar.DAY_OF_MONTH, day)
        }
        return c.timeInMillis
    }

    private fun Calendar.truncateToDayStart() {
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }
}
