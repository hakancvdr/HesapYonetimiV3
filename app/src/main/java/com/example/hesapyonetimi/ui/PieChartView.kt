package com.example.hesapyonetimi.ui

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import kotlin.math.*

class PieChartView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {

    data class PieEntry(val value: Float, val color: Int, val label: String)

    var onSliceTap: ((PieEntry) -> Unit)? = null
    private var showBar = false

    private val paint   = Paint(Paint.ANTI_ALIAS_FLAG)
    private val txtPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface  = Typeface.DEFAULT_BOLD
    }
    private val legendPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.LEFT
    }

    private var entries: List<PieEntry> = emptyList()
    private val sliceAngles  = mutableListOf<Pair<Float, Float>>() // startAngle, sweep
    private var cx = 0f; private var cy = 0f; private var r = 0f
    private var selectedIdx = -1

    fun setData(data: List<PieEntry>) {
        entries   = data.filter { it.value > 0f }
        selectedIdx = -1
        sliceAngles.clear()
        requestLayout()
        invalidate()
    }

    fun setMode(bar: Boolean) { showBar = bar; invalidate() }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (entries.isEmpty()) return
        if (showBar) drawBar(canvas) else drawPie(canvas)
        drawLegend(canvas)
    }

    // ── Pasta ────────────────────────────────────────────────────────────────

    private fun drawPie(canvas: Canvas) {
        val total = entries.sumOf { it.value.toDouble() }.toFloat().coerceAtLeast(0.001f)
        val pieH  = height * 0.60f
        cx = width / 2f
        cy = pieH / 2f
        r  = (minOf(cx, cy) * 0.88f)
        val rect = RectF(cx - r, cy - r, cx + r, cy + r)

        sliceAngles.clear()
        var startAngle = -90f
        entries.forEachIndexed { i, entry ->
            val sweep = (entry.value / total) * 360f
            val isSelected = i == selectedIdx
            val dr = if (isSelected) r * 0.08f else 0f

            val midRad = Math.toRadians((startAngle + sweep / 2.0))
            val expandedRect = if (isSelected) RectF(
                rect.left  + (dr * cos(midRad)).toFloat(),
                rect.top   + (dr * sin(midRad)).toFloat(),
                rect.right + (dr * cos(midRad)).toFloat(),
                rect.bottom + (dr * sin(midRad)).toFloat()
            ) else rect

            paint.style = Paint.Style.FILL
            paint.color = entry.color
            canvas.drawArc(expandedRect, startAngle, sweep, true, paint)

            if (sweep > 18f) {
                val lx = (cx + r * 0.65f * cos(midRad)).toFloat()
                val ly = (cy + r * 0.65f * sin(midRad)).toFloat()
                val pct = (entry.value / total * 100).toInt()
                txtPaint.textSize = if (sweep > 40f) 28f else 22f
                txtPaint.color = Color.WHITE
                canvas.drawText("$pct%", lx, ly + txtPaint.textSize / 3, txtPaint)
            }
            sliceAngles.add(startAngle to sweep)
            startAngle += sweep
        }

        // Donut hole
        paint.style = Paint.Style.FILL
        paint.color = try { context.getColor(com.example.hesapyonetimi.R.color.card_background) }
                      catch (e: Exception) { Color.parseColor("#1A1C28") }
        canvas.drawCircle(cx, cy, r * 0.48f, paint)
    }

    // ── Çubuk ────────────────────────────────────────────────────────────────

    private fun drawBar(canvas: Canvas) {
        sliceAngles.clear()
        val total   = entries.sumOf { it.value.toDouble() }.toFloat().coerceAtLeast(0.001f)
        val maxVal  = entries.maxOf { it.value }
        val barH    = height * 0.52f
        val barArea = width * 0.88f
        val barW    = (barArea / entries.size - 8f).coerceAtLeast(8f)
        val startX  = (width - barArea) / 2f + barW / 2f
        val bottom  = barH * 0.94f

        txtPaint.textSize = 22f
        entries.forEachIndexed { i, entry ->
            val bx     = startX + i * (barArea / entries.size)
            val bh     = (entry.value / maxVal) * bottom * 0.88f
            val top    = bottom - bh
            val isSelected = i == selectedIdx
            paint.color = entry.color
            paint.style = Paint.Style.FILL
            val rr = barW * 0.3f
            val barRect = RectF(bx - barW / 2, top, bx + barW / 2, bottom)
            canvas.drawRoundRect(barRect, rr, rr, paint)
            if (isSelected) {
                paint.color = Color.WHITE
                paint.alpha = 60
                canvas.drawRoundRect(barRect, rr, rr, paint)
                paint.alpha = 255
            }
            val pct = (entry.value / total * 100).toInt()
            if (bh > 40f) {
                txtPaint.color = Color.WHITE
                canvas.drawText("$pct%", bx, bottom - 10f, txtPaint)
            }
        }
    }

    // ── Legend ────────────────────────────────────────────────────────────────

    private fun drawLegend(canvas: Canvas) {
        val legendTop = height * 0.64f
        val dotSize   = 18f
        val colW      = width / 2f
        legendPaint.textSize = 26f
        legendPaint.color    = try { context.getColor(com.example.hesapyonetimi.R.color.text_primary) }
                                catch (e: Exception) { Color.parseColor("#E8EAF2") }

        entries.forEachIndexed { i, entry ->
            val col  = i % 2
            val row  = i / 2
            val lx   = col * colW + 24f
            val ly   = legendTop + row * (dotSize + 18f) + dotSize

            paint.color = entry.color
            paint.style = Paint.Style.FILL
            canvas.drawCircle(lx + dotSize / 2, ly - dotSize / 3, dotSize / 2, paint)

            val label = if (entry.label.length > 12) entry.label.take(11) + "…" else entry.label
            legendPaint.color = if (i == selectedIdx)
                try { context.getColor(com.example.hesapyonetimi.R.color.green_primary) } catch (e: Exception) { Color.BLUE }
            else
                try { context.getColor(com.example.hesapyonetimi.R.color.text_secondary) } catch (e: Exception) { Color.GRAY }
            canvas.drawText(label, lx + dotSize + 8f, ly, legendPaint)
        }
    }

    // ── Dokunma ───────────────────────────────────────────────────────────────

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action != MotionEvent.ACTION_UP) return true
        if (showBar) {
            handleBarTouch(event.x)
        } else {
            handlePieTouch(event.x, event.y)
        }
        return true
    }

    private fun handlePieTouch(tx: Float, ty: Float) {
        val dx  = tx - cx; val dy = ty - cy
        val dist = sqrt(dx * dx + dy * dy)
        if (dist < r * 0.48f || dist > r * 1.1f) { selectedIdx = -1; invalidate(); return }
        var angle = Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())).toFloat() + 90f
        if (angle < 0) angle += 360f
        sliceAngles.forEachIndexed { i, (start, sweep) ->
            val s = ((start + 90) % 360 + 360) % 360
            val e = (s + sweep) % 360
            val inSlice = if (s <= e) angle in s..e else angle >= s || angle <= e
            if (inSlice) {
                selectedIdx = if (selectedIdx == i) -1 else i
                invalidate()
                if (selectedIdx == i) onSliceTap?.invoke(entries[i])
                return
            }
        }
    }

    private fun handleBarTouch(tx: Float) {
        val barArea = width * 0.88f
        val startX  = (width - barArea) / 2f
        val cellW   = barArea / entries.size
        val idx     = ((tx - startX) / cellW).toInt().coerceIn(0, entries.size - 1)
        selectedIdx = if (selectedIdx == idx) -1 else idx
        invalidate()
        if (selectedIdx >= 0) onSliceTap?.invoke(entries[idx])
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = MeasureSpec.getSize(widthMeasureSpec)
        // height = pie (60%) + legend rows
        val legendRows = ((entries.size + 1) / 2).coerceAtLeast(1)
        val h = (w * 0.60f + legendRows * 44f + 16f).toInt()
        setMeasuredDimension(w, h)
    }
}
