package com.example.hesapyonetimi.ui

import android.graphics.Typeface
import android.widget.TextView
import android.util.TypedValue
import androidx.annotation.ColorInt
import androidx.core.content.res.ResourcesCompat
import com.example.hesapyonetimi.R
import com.example.hesapyonetimi.data.mapper.normalizeStoredCategoryIcon

/**
 * Renders category icon keys with **Material Symbols Outlined** (ligatures).
 * Stored values should be snake_case names (e.g. `shopping_cart`); legacy emoji is normalized first.
 */
object MaterialCategoryIcon {

    private val ligaturePattern = Regex("^[a-z0-9_]+$")

    fun toLigatureText(raw: String): String {
        val normalized = normalizeStoredCategoryIcon(raw.trim())
        val candidate = normalized.lowercase().replace("-", "_")
        return if (ligaturePattern.matches(candidate)) candidate else "inventory_2"
    }

    fun bind(
        textView: TextView,
        rawIcon: String,
        textSizeSp: Float,
        @ColorInt tint: Int? = null
    ) {
        val tf = runCatching {
            ResourcesCompat.getFont(textView.context, R.font.material_symbols_outlined)
        }.getOrNull() ?: Typeface.DEFAULT
        textView.typeface = tf
        textView.includeFontPadding = false
        textView.maxLines = 1
        textView.isSingleLine = true
        textView.ellipsize = null
        textView.text = toLigatureText(rawIcon)
        textView.setTextSize(TypedValue.COMPLEX_UNIT_SP, textSizeSp)
        tint?.let { textView.setTextColor(it) }
    }
}
