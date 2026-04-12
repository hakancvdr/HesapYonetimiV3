package com.example.hesapyonetimi.presentation.common

import android.content.Context
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale

object CurrencyFormatter {

    private val turkishLocale = Locale("tr", "TR")

    // Para birimi tablosu: kod → (sembol, ondalık nokta/virgül)
    data class CurrencyDef(val symbol: String, val suffix: Boolean = true)

    val currencies = linkedMapOf(
        "TRY" to CurrencyDef("₺"),
        "USD" to CurrencyDef("$", suffix = false),
        "EUR" to CurrencyDef("€", suffix = false),
        "GBP" to CurrencyDef("£", suffix = false),
        "JPY" to CurrencyDef("¥", suffix = false),
        "BTC" to CurrencyDef("₿"),
        "ETH" to CurrencyDef("Ξ"),
        "CHF" to CurrencyDef("CHF"),
        "CAD" to CurrencyDef("CA$", suffix = false),
        "AUD" to CurrencyDef("A$", suffix = false)
    )

    private var activeCurrencyCode = "TRY"

    fun init(context: Context) {
        val prefs = context.getSharedPreferences("HesapPrefs", Context.MODE_PRIVATE)
        activeCurrencyCode = prefs.getString("currency_code", "TRY") ?: "TRY"
    }

    fun setCode(context: Context, code: String) {
        activeCurrencyCode = code
        context.getSharedPreferences("HesapPrefs", Context.MODE_PRIVATE)
            .edit().putString("currency_code", code).apply()
    }

    private fun getSymbol() = currencies[activeCurrencyCode]?.symbol ?: "₺"
    private fun isSuffix() = currencies[activeCurrencyCode]?.suffix ?: true

    /** DecimalFormat eşzamanlı kullanıma uygun değil; çağrı başına yeni örnek. */
    private fun intFormatter() = DecimalFormat("#,##0", DecimalFormatSymbols(turkishLocale)).apply {
        groupingSize = 3; isGroupingUsed = true
    }

    private fun decimalFormatter() = DecimalFormat("#,##0.00", DecimalFormatSymbols(turkishLocale)).apply {
        groupingSize = 3; isGroupingUsed = true
    }

    fun format(amount: Double, showCurrency: Boolean = true): String {
        val formatted = if (amount % 1.0 == 0.0) intFormatter().format(amount) else decimalFormatter().format(amount)
        if (!showCurrency) return formatted
        val sym = getSymbol()
        return if (isSuffix()) "$formatted $sym" else "$sym$formatted"
    }

    fun formatWithSign(amount: Double, isIncome: Boolean, showCurrency: Boolean = true): String {
        val sign = if (isIncome) "+" else "-"
        val formatted = if (amount % 1.0 == 0.0) intFormatter().format(amount) else decimalFormatter().format(amount)
        if (!showCurrency) return "$sign$formatted"
        val sym = getSymbol()
        return if (isSuffix()) "$sign$formatted $sym" else "$sign$sym$formatted"
    }

    fun formatCompact(amount: Double, showCurrency: Boolean = true): String {
        val abs = kotlin.math.abs(amount)
        val formatted = when {
            abs >= 1_000_000 -> String.format(turkishLocale, "%.1fM", abs / 1_000_000)
            abs >= 1_000     -> String.format(turkishLocale, "%.1fK", abs / 1_000)
            else             -> decimalFormatter().format(abs)
        }
        if (!showCurrency) return formatted
        val sym = getSymbol()
        return if (isSuffix()) "$formatted $sym" else "$sym$formatted"
    }
}
