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

    private val paint    = Paint(Paint.ANTI_ALIAS_FLAG)
    private val txtPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface  = Typeface.DEFAULT_BOLD
    }
    private val legendPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.LEFT
    }
    private val strikePaint = Paint(Paint.ANTI_ALIAS_FLAG)

    private var allEntries: List<PieEntry> = emptyList()
    private val hiddenIndices = mutableSetOf<Int>()

    private val entries get() = allEntries.filterIndexed { i, _ -> i !in hiddenIndices }
    private val sliceAngles = mutableListOf<Pair<Float, Float>>()
    private var cx = 0f; private var cy = 0f; private var r = 0f
    private var selectedIdx = -1

    // Legend touch areas: index in allEntries → Rect
    private val legendRects = mutableMapOf<Int, RectF>()
    private var animateIn = true
    private var animProgress = 1f

    fun setData(data: List<PieEntry>) {
        allEntries  = data.filter { it.value > 0f }
        hiddenIndices.clear()
        selectedIdx = -1
        sliceAngles.clear()
        legendRects.clear()
        animateIn = true
        animProgress = 0f
        requestLayout()
        invalidate()
    }

    fun setMode(bar: Boolean) { showBar = bar; invalidate() }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (allEntries.isEmpty()) return
        if (animateIn) {
            animProgress = (animProgress + 0.08f).coerceAtMost(1f)
            if (animProgress >= 1f) animateIn = false else postInvalidateOnAnimation()
        }
        if (showBar) drawBar(canvas) else drawPie(canvas)
        drawLegend(canvas)
    }

    // ── Pasta ────────────────────────────────────────────────────────────────

    private fun drawPie(canvas: Canvas) {
        val visible = entries
        if (visible.isEmpty()) { drawEmptyPie(canvas); return }
        val total = visible.sumOf { it.value.toDouble() }.toFloat().coerceAtLeast(0.001f)
        val pieH  = height * 0.60f
        cx = width / 2f
        cy = pieH / 2f
        r  = (minOf(cx, cy) * 0.88f)
        val rect = RectF(cx - r, cy - r, cx + r, cy + r)

        sliceAngles.clear()
        var startAngle = -90f
        visible.forEachIndexed { visIdx, entry ->
            val sweep = (entry.value / total) * 360f * animProgress
            val globalIdx = allEntries.indexOf(entry)
            val isSelected = globalIdx == selectedIdx
            val dr = if (isSelected) r * 0.08f else 0f
            val midRad = Math.toRadians((startAngle + sweep / 2.0))
            val expandedRect = if (isSelected) RectF(
                rect.left   + (dr * cos(midRad)).toFloat(),
                rect.top    + (dr * sin(midRad)).toFloat(),
                rect.right  + (dr * cos(midRad)).toFloat(),
                rect.bottom + (dr * sin(midRad)).toFloat()
            ) else rect

            paint.style = Paint.Style.FILL
            paint.color = entry.color
            canvas.drawArc(expandedRect, startAngle, sweep, true, paint)

            if (sweep > 18f) {
                val lx  = (cx + r * 0.65f * cos(midRad)).toFloat()
                val ly  = (cy + r * 0.65f * sin(midRad)).toFloat()
                val pct = (entry.value / total * 100).toInt()
                txtPaint.textSize = if (sweep > 40f) 30f else 24f
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

    private fun drawEmptyPie(canvas: Canvas) {
        val pieH = height * 0.60f
        cx = width / 2f; cy = pieH / 2f; r = (minOf(cx, cy) * 0.88f)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 4f
        paint.color = Color.GRAY
        canvas.drawCircle(cx, cy, r, paint)
    }

    // ── Çubuk ────────────────────────────────────────────────────────────────

    private fun drawBar(canvas: Canvas) {
        val visible = entries
        if (visible.isEmpty()) return
        sliceAngles.clear()
        val total  = visible.sumOf { it.value.toDouble() }.toFloat().coerceAtLeast(0.001f)
        val maxVal = visible.maxOf { it.value }
        val barH   = height * 0.52f
        val barArea = width * 0.88f
        val barW   = (barArea / visible.size - 8f).coerceAtLeast(8f)
        val startX = (width - barArea) / 2f + barW / 2f
        val bottom = barH * 0.94f

        txtPaint.textSize = 24f
        visible.forEachIndexed { i, entry ->
            val globalIdx  = allEntries.indexOf(entry)
            val bx         = startX + i * (barArea / visible.size)
            val bh         = (entry.value / maxVal) * bottom * 0.88f * animProgress
            val top        = bottom - bh
            val isSelected = globalIdx == selectedIdx
            paint.color = entry.color
            paint.style = Paint.Style.FILL
            val rr      = barW * 0.3f
            val barRect = RectF(bx - barW / 2, top, bx + barW / 2, bottom)
            canvas.drawRoundRect(barRect, rr, rr, paint)
            if (isSelected) {
                paint.color = Color.WHITE; paint.alpha = 60
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
        legendRects.clear()
        val legendTop = height * 0.64f
        val dotSize   = 22f
        val rowH      = dotSize + 24f
        val colW      = width / 2f
        legendPaint.textSize = 28f

        val primaryColor = try { context.getColor(com.example.hesapyonetimi.R.color.text_primary) }
                           catch (e: Exception) { Color.parseColor("#E8EAF2") }
        val dimColor     = try { context.getColor(com.example.hesapyonetimi.R.color.text_secondary) }
                           catch (e: Exception) { Color.GRAY }

        allEntries.forEachIndexed { i, entry ->
            val col = i % 2
            val row = i / 2
            val lx  = col * colW + 28f
            val ly  = legendTop + row * rowH + dotSize

            val isHidden   = i in hiddenIndices
            val isSelected = i == selectedIdx

            // dot
            paint.color = if (isHidden) Color.GRAY else entry.color
            paint.style = Paint.Style.FILL
            paint.alpha = if (isHidden) 80 else 255
            canvas.drawCircle(lx + dotSize / 2, ly - dotSize / 3, dotSize / 2, paint)
            paint.alpha = 255

            // label
            val rawLabel = if (entry.label.length > 12) entry.label.take(11) + "…" else entry.label
            legendPaint.color = when {
                isHidden   -> dimColor
                isSelected -> try { context.getColor(com.example.hesapyonetimi.R.color.green_primary) }
                              catch (e: Exception) { Color.BLUE }
                else       -> primaryColor
            }
            legendPaint.typeface = if (isSelected) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
            canvas.drawText(rawLabel, lx + dotSize + 10f, ly, legendPaint)

            // strikethrough for hidden entries
            if (isHidden) {
                val textW = legendPaint.measureText(rawLabel)
                strikePaint.color = dimColor
                strikePaint.strokeWidth = 2f
                canvas.drawLine(
                    lx + dotSize + 10f,
                    ly - legendPaint.textSize * 0.3f,
                    lx + dotSize + 10f + textW,
                    ly - legendPaint.textSize * 0.3f,
                    strikePaint
                )
            }

            // touch rect for legend row
            legendRects[i] = RectF(lx, ly - dotSize, lx + colW - 8f, ly + 8f)
        }
    }

    // ── Dokunma ───────────────────────────────────────────────────────────────

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action != MotionEvent.ACTION_UP) return true

        // Check legend tap first
        for ((idx, rect) in legendRects) {
            if (rect.contains(event.x, event.y)) {
                if (idx in hiddenIndices) hiddenIndices.remove(idx) else hiddenIndices.add(idx)
                selectedIdx = -1
                sliceAngles.clear()
                requestLayout()
                invalidate()
                return true
            }
        }

        if (showBar) handleBarTouch(event.x) else handlePieTouch(event.x, event.y)
        return true
    }

    private fun handlePieTouch(tx: Float, ty: Float) {
        val dx   = tx - cx; val dy = ty - cy
        val dist = sqrt(dx * dx + dy * dy)
        if (dist < r * 0.48f || dist > r * 1.1f) { selectedIdx = -1; invalidate(); return }
        var angle = Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())).toFloat() + 90f
        if (angle < 0) angle += 360f
        val visible = entries
        sliceAngles.forEachIndexed { visIdx, (start, sweep) ->
            val s      = ((start + 90) % 360 + 360) % 360
            val e      = (s + sweep) % 360
            val inSlice = if (s <= e) angle in s..e else angle >= s || angle <= e
            if (inSlice) {
                val globalIdx  = if (visIdx < visible.size) allEntries.indexOf(visible[visIdx]) else -1
                selectedIdx    = if (selectedIdx == globalIdx) -1 else globalIdx
                invalidate()
                if (selectedIdx >= 0) onSliceTap?.invoke(allEntries[globalIdx])
                return
            }
        }
    }

    private fun handleBarTouch(tx: Float) {
        val visible = entries
        if (visible.isEmpty()) return
        val barArea = width * 0.88f
        val startX  = (width - barArea) / 2f
        val cellW   = barArea / visible.size
        val visIdx  = ((tx - startX) / cellW).toInt().coerceIn(0, visible.size - 1)
        val globalIdx = allEntries.indexOf(visible[visIdx])
        selectedIdx = if (selectedIdx == globalIdx) -1 else globalIdx
        invalidate()
        if (selectedIdx >= 0) onSliceTap?.invoke(allEntries[globalIdx])
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w          = MeasureSpec.getSize(widthMeasureSpec)
        val legendRows = ((allEntries.size + 1) / 2).coerceAtLeast(1)
        val h          = (w * 0.60f + legendRows * 46f + 20f).toInt()
        setMeasuredDimension(w, h)
    }
}
