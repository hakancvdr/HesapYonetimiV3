package com.example.hesapyonetimi.presentation.dashboard

/**
 * Anasayfa B modülü — sabit [id] listesi ve varsayılan sıra.
 *
 * **MVP katalog:** gecikmiş uyarı, dönem şeridi, takvim+işlemler, kısa tahmin, mini pasta,
 * ipuçları, FX, hatırlatıcılar. Modül anahtarları çekmeceye taşındı ([MODULE_PREFS] yalnızca eski kayıtları süzmek için).
 *
 * Sıra UI: varsayılan aşağıdaki; kullanıcı [AuthPrefs] üzerinden hazır düzen seçebilir.
 * Migrasyon: [AuthPrefs] anahtarı yoksa bu sıra kullanılır.
 */
object DashboardModuleCatalog {

    const val MODULE_OVERDUE = "overdue"
    /** Tekrarlayan işlem worker şeridi (varsa). */
    const val MODULE_RECURRING_STRIP = "recurring_strip"
    const val MODULE_CARRYOVER_STRIP = "carryover"
    const val MODULE_FORECAST = "forecast"
    const val MODULE_MINI_PIE = "mini_pie"
    const val MODULE_CALENDAR = "calendar"
    const val MODULE_INSIGHTS = "insights"
    const val MODULE_FX = "fx"
    const val MODULE_REMINDERS = "reminders"
    /** Eski sürümlerde kayıtlı; artık sütunda kullanılmaz. */
    const val MODULE_PREFS = "prefs"

    val defaultOrder: List<String> = listOf(
        MODULE_OVERDUE,
        MODULE_RECURRING_STRIP,
        MODULE_CALENDAR,
        MODULE_FORECAST,
        MODULE_MINI_PIE,
        MODULE_INSIGHTS,
        MODULE_CARRYOVER_STRIP,
        MODULE_FX,
        MODULE_REMINDERS
    )

    val ratesFirstOrder = listOf(
        MODULE_FX,
        MODULE_OVERDUE,
        MODULE_RECURRING_STRIP,
        MODULE_CALENDAR,
        MODULE_FORECAST,
        MODULE_MINI_PIE,
        MODULE_INSIGHTS,
        MODULE_CARRYOVER_STRIP,
        MODULE_REMINDERS
    )

    val remindersFirstOrder = listOf(
        MODULE_REMINDERS,
        MODULE_OVERDUE,
        MODULE_RECURRING_STRIP,
        MODULE_CALENDAR,
        MODULE_FORECAST,
        MODULE_MINI_PIE,
        MODULE_INSIGHTS,
        MODULE_CARRYOVER_STRIP,
        MODULE_FX
    )

    fun normalizeOrder(raw: List<String>): List<String> {
        val known = defaultOrder.toSet()
        val merged = raw.filter { it != MODULE_PREFS && it in known }.distinct().toMutableList()
        defaultOrder.forEach { id ->
            if (id !in merged) merged.add(id)
        }
        return merged
    }
}
