package com.example.hesapyonetimi.util

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

object PayPeriodLabelFormatter {

    /**
     * Representative salary period from payday [dayOfMonth] in [month] (0-based) / [year]
     * through the day before the next month's payday (e.g. 17 Apr → 16 May).
     */
    fun salaryWindowLabel(locale: Locale, year: Int, monthZeroBased: Int, dayOfMonth: Int): String {
        val cal = Calendar.getInstance()
        cal.set(Calendar.YEAR, year)
        cal.set(Calendar.MONTH, monthZeroBased)
        cal.set(Calendar.DAY_OF_MONTH, 1)
        val maxDay = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
        val d = dayOfMonth.coerceIn(1, maxDay)
        cal.set(Calendar.DAY_OF_MONTH, d)
        val startMs = cal.timeInMillis
        val endCal = cal.clone() as Calendar
        endCal.add(Calendar.MONTH, 1)
        endCal.add(Calendar.DAY_OF_MONTH, -1)
        val fmt = SimpleDateFormat("d MMMM", locale)
        return "${fmt.format(startMs)} → ${fmt.format(endCal.timeInMillis)}"
    }
}
