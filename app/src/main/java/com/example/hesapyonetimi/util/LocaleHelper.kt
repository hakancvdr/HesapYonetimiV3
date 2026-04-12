package com.example.hesapyonetimi.util

import android.content.Context
import android.content.res.Configuration
import com.example.hesapyonetimi.auth.AuthPrefs
import java.util.Locale

object LocaleHelper {

    fun wrap(ctx: Context): Context {
        val tag = AuthPrefs.getAppLocaleTag(ctx)
        if (tag == AuthPrefs.LOCALE_TR) return ctx
        val locale = Locale.forLanguageTag(tag)
        Locale.setDefault(locale)
        val config = Configuration(ctx.resources.configuration)
        config.setLocale(locale)
        return ctx.createConfigurationContext(config)
    }
}
