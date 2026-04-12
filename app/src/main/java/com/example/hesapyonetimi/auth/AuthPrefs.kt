package com.example.hesapyonetimi.auth

import android.content.Context
import android.content.SharedPreferences

/**
 * Kurulum yöntemi ve PIN tercihleri [HesapPrefs] üzerinde tutulur (Room migration gerekmez).
 */
object AuthPrefs {

    const val PREFS_NAME = "HesapPrefs"

    const val AUTH_METHOD_GMAIL = "GMAIL"
    const val AUTH_METHOD_LOCAL = "LOCAL"
    const val AUTH_METHOD_GUEST = "GUEST"

    private const val KEY_AUTH_METHOD = "auth_method"
    private const val KEY_PIN_ENABLED = "pin_enabled"
    private const val KEY_PIN_LOCK_TIMEOUT_MS = "pin_lock_timeout_ms"

    const val KEY_LINKED_GOOGLE_EMAIL = "linked_google_email"
    const val KEY_APP_LOCALE = "app_locale"

    private const val KEY_PRO_MEMBER = "pro_member"
    private const val KEY_DASHBOARD_FX = "dashboard_fx_visible"
    private const val KEY_DASH_MOD_REMINDERS = "dashboard_mod_reminders"
    private const val KEY_DASH_MOD_INSIGHTS = "dashboard_mod_insights"
    private const val KEY_DASH_MINI_PIE_EXPANDED = "dashboard_mini_pie_expanded"

    const val LOCALE_TR = "tr"
    const val LOCALE_EN = "en"

    const val DEFAULT_PIN_LOCK_TIMEOUT_MS = 5 * 60 * 1000L

    fun prefs(ctx: Context): SharedPreferences =
        ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getAuthMethod(ctx: Context): String {
        val raw = prefs(ctx).getString(KEY_AUTH_METHOD, null)?.trim().orEmpty()
        if (raw.isNotEmpty()) return raw
        // Eski kurulumlar: kayıtlı + güvenlik cevabı → yerel hızlı kayıt
        return if (prefs(ctx).getBoolean("is_registered", false)) AUTH_METHOD_LOCAL else ""
    }

    fun isPinEnabled(ctx: Context): Boolean {
        val p = prefs(ctx)
        if (!p.contains(KEY_PIN_ENABLED)) {
            return p.getString("kullanici_pin", null) != null
        }
        return p.getBoolean(KEY_PIN_ENABLED, false)
    }

    fun setPinEnabled(ctx: Context, enabled: Boolean) {
        prefs(ctx).edit().putBoolean(KEY_PIN_ENABLED, enabled).apply()
    }

    fun getPinLockTimeoutMs(ctx: Context): Long {
        val v = prefs(ctx).getLong(KEY_PIN_LOCK_TIMEOUT_MS, -1L)
        return if (v <= 0L) DEFAULT_PIN_LOCK_TIMEOUT_MS else v
    }

    fun setPinLockTimeoutMs(ctx: Context, ms: Long) {
        prefs(ctx).edit().putLong(KEY_PIN_LOCK_TIMEOUT_MS, ms).apply()
    }

    /** PIN kilidi: açık + dört haneli PIN kayıtlı */
    fun shouldEnforceAppPinLock(ctx: Context): Boolean {
        if (!isPinEnabled(ctx)) return false
        val pin = prefs(ctx).getString("kullanici_pin", null)
        return !pin.isNullOrBlank() && pin.length == 4
    }

    fun hasSecurityRecovery(ctx: Context): Boolean {
        when (getAuthMethod(ctx)) {
            AUTH_METHOD_GMAIL, AUTH_METHOD_GUEST -> return false
        }
        val ans = prefs(ctx).getString("security_answer", null)?.trim().orEmpty()
        return ans.isNotEmpty()
    }

    fun labelForTimeoutMs(ms: Long): String = when (ms) {
        5 * 60 * 1000L -> "5 dakika"
        10 * 60 * 1000L -> "10 dakika"
        15 * 60 * 1000L -> "15 dakika"
        30 * 60 * 1000L -> "30 dakika"
        60 * 60 * 1000L -> "1 saat"
        else -> "${ms / 60_000} dakika"
    }

    val PIN_TIMEOUT_CHOICES_MS = longArrayOf(
        5 * 60 * 1000L,
        10 * 60 * 1000L,
        15 * 60 * 1000L,
        30 * 60 * 1000L,
        60 * 60 * 1000L
    )

    fun getAppLocaleTag(ctx: Context): String {
        val raw = prefs(ctx).getString(KEY_APP_LOCALE, null)?.trim().orEmpty()
        return when (raw) {
            LOCALE_EN -> LOCALE_EN
            else -> LOCALE_TR
        }
    }

    fun setAppLocaleTag(ctx: Context, tag: String) {
        val v = if (tag == LOCALE_EN) LOCALE_EN else LOCALE_TR
        prefs(ctx).edit().putString(KEY_APP_LOCALE, v).apply()
    }

    fun getLinkedGoogleEmail(ctx: Context): String? =
        prefs(ctx).getString(KEY_LINKED_GOOGLE_EMAIL, null)?.trim()?.takeIf { it.isNotEmpty() }

    /** Ücretli özellik bayrağı (gerçek IAP yokken manuel / PRO ekranı ile uyumlu). */
    fun isProMember(ctx: Context): Boolean = prefs(ctx).getBoolean(KEY_PRO_MEMBER, false)

    fun setProMember(ctx: Context, pro: Boolean) {
        prefs(ctx).edit().putBoolean(KEY_PRO_MEMBER, pro).apply()
    }

    fun isDashboardFxVisible(ctx: Context): Boolean =
        prefs(ctx).getBoolean(KEY_DASHBOARD_FX, true)

    fun setDashboardFxVisible(ctx: Context, visible: Boolean) {
        prefs(ctx).edit().putBoolean(KEY_DASHBOARD_FX, visible).apply()
    }

    fun isDashboardReminderSectionVisible(ctx: Context): Boolean =
        prefs(ctx).getBoolean(KEY_DASH_MOD_REMINDERS, true)

    fun setDashboardReminderSectionVisible(ctx: Context, visible: Boolean) {
        prefs(ctx).edit().putBoolean(KEY_DASH_MOD_REMINDERS, visible).apply()
    }

    fun isDashboardInsightsVisible(ctx: Context): Boolean =
        prefs(ctx).getBoolean(KEY_DASH_MOD_INSIGHTS, true)

    fun setDashboardInsightsVisible(ctx: Context, visible: Boolean) {
        prefs(ctx).edit().putBoolean(KEY_DASH_MOD_INSIGHTS, visible).apply()
    }

    fun isDashboardMiniPieExpanded(ctx: Context): Boolean =
        prefs(ctx).getBoolean(KEY_DASH_MINI_PIE_EXPANDED, true)

    fun setDashboardMiniPieExpanded(ctx: Context, expanded: Boolean) {
        prefs(ctx).edit().putBoolean(KEY_DASH_MINI_PIE_EXPANDED, expanded).apply()
    }

    /**
     * Oturumu kapatır; tema ve dil tercihleri korunur. Finans verisi (Room) silinmez.
     */
    fun applyLogout(ctx: Context) {
        val p = prefs(ctx)
        val theme = p.getString("theme_mode", "SYSTEM") ?: "SYSTEM"
        val locale = getAppLocaleTag(ctx)
        p.edit()
            .putBoolean("is_registered", false)
            .remove(KEY_AUTH_METHOD)
            .remove("kullanici_pin")
            .remove("pin_enabled")
            .remove("security_answer")
            .remove("security_question_index")
            .remove("biometric_enabled")
            .remove("user_display_name")
            .remove(KEY_LINKED_GOOGLE_EMAIL)
            .remove("son_giris_zamani")
            .putString("theme_mode", theme)
            .putString(KEY_APP_LOCALE, locale)
            .apply()
    }
}
