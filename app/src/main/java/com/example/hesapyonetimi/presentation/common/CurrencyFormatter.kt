package com.example.hesapyonetimi.presentation.common

import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale

object CurrencyFormatter {
    
    private val turkishLocale = Locale("tr", "TR")
    
    private val decimalFormat = DecimalFormat("#,##0.00", DecimalFormatSymbols(turkishLocale)).apply {
        groupingSize = 3
        isGroupingUsed = true
    }
    
    /**
     * Türkiye formatında para formatlar
     * Örnek: 2500.0 → "2.500 ₺"
     */
    fun format(amount: Double, showCurrency: Boolean = true): String {
        // Ondalık kısmı olmayan sayılar için format
        val formatted = if (amount % 1.0 == 0.0) {
            DecimalFormat("#,##0", DecimalFormatSymbols(turkishLocale)).apply {
                groupingSize = 3
                isGroupingUsed = true
            }.format(amount)
        } else {
            decimalFormat.format(amount)
        }
        return if (showCurrency) "$formatted ₺" else formatted
    }
    
    /**
     * İşaret ile birlikte formatlar (gelir/gider)
     * Örnek: 
     * - Gelir: 2500.0, true → "+2.500 ₺"
     * - Gider: 1200.0, false → "-1.200 ₺"
     */
    fun formatWithSign(amount: Double, isIncome: Boolean, showCurrency: Boolean = true): String {
        val sign = if (isIncome) "+" else "-"
        
        // Ondalık kısmı olmayan sayılar için format
        val formatted = if (amount % 1.0 == 0.0) {
            DecimalFormat("#,##0", DecimalFormatSymbols(turkishLocale)).apply {
                groupingSize = 3
                isGroupingUsed = true
            }.format(amount)
        } else {
            decimalFormat.format(amount)
        }
        
        return if (showCurrency) "$sign$formatted ₺" else "$sign$formatted"
    }
    
    /**
     * Compact format (kısa gösterim)
     * Örnek: 12500.0 → "12,5K ₺"
     */
    fun formatCompact(amount: Double, showCurrency: Boolean = true): String {
        val absAmount = kotlin.math.abs(amount)
        val formatted = when {
            absAmount >= 1_000_000 -> String.format(turkishLocale, "%.1fM", absAmount / 1_000_000)
            absAmount >= 1_000 -> String.format(turkishLocale, "%.1fK", absAmount / 1_000)
            else -> decimalFormat.format(absAmount)
        }
        return if (showCurrency) "$formatted ₺" else formatted
    }
}
