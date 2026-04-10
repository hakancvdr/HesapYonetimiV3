package com.example.hesapyonetimi.ui

import android.content.Context
import android.util.TypedValue
import android.view.Gravity
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.example.hesapyonetimi.R

/**
 * Diyaloglarda yatay emoji / ikon seçici — yuvarlatılmış chip, seçili vurgu.
 */
object IconPickerHelper {

    fun dp(context: Context, n: Int): Int =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, n.toFloat(), context.resources.displayMetrics).toInt()

    fun bindHorizontalChips(
        container: LinearLayout,
        icons: List<String>,
        initialSelection: String,
        onPreview: (String) -> Unit
    ) {
        container.removeAllViews()
        val ctx = container.context
        var selected = when {
            initialSelection in icons -> initialSelection
            icons.isNotEmpty() -> icons.first()
            else -> initialSelection
        }
        val tvs = mutableListOf<TextView>()

        fun applySelection() {
            tvs.forEachIndexed { i, tv ->
                val icon = icons[i]
                val bg = if (icon == selected) R.drawable.bg_icon_chip_selected else R.drawable.bg_icon_chip_unselected
                tv.background = ContextCompat.getDrawable(ctx, bg)
            }
            onPreview(selected)
        }

        icons.forEach { icon ->
            val tv = TextView(ctx).apply {
                text = icon
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 22f)
                gravity = Gravity.CENTER
                includeFontPadding = false
                layoutParams = LinearLayout.LayoutParams(dp(ctx, 46), dp(ctx, 46)).apply {
                    marginEnd = dp(ctx, 8)
                }
                setOnClickListener {
                    selected = icon
                    applySelection()
                }
            }
            tvs.add(tv)
            container.addView(tv)
        }
        applySelection()
    }
}
