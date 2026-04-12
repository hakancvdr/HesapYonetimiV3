package com.example.hesapyonetimi.util

import android.content.Context
import android.util.Log
import com.example.hesapyonetimi.auth.AuthPrefs

/**
 * Anonim kullanım olayları — Firebase Analytics yokken [Log] ile no-op yaklaşımı.
 * [AuthPrefs.isAnonymousAnalyticsEnabled] kapalıysa hiçbir şey yapılmaz.
 */
object AnalyticsHelper {

    private const val TAG = "AppAnalytics"

    fun logEvent(ctx: Context, name: String, params: Map<String, String> = emptyMap()) {
        if (!AuthPrefs.isAnonymousAnalyticsEnabled(ctx)) return
        if (params.isEmpty()) Log.i(TAG, "event=$name")
        else Log.i(TAG, "event=$name params=$params")
    }
}
