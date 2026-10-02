package com.example.wordexplainer

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.*

// ─────────────────────────────────────────────────────────────────────────────
//  CircleView — Floating drag handle.
//  Clean static dark glass circle. No animation. No crosshair inside.
//  When in positioning mode, draws a distinct blue halo ring.
// ─────────────────────────────────────────────────────────────────────────────
//  CircleView — Floating drag handle.
//  Idle: Sleek half-circle tab resting flush against the screen bezel.
//  Drag: Morphs with frosted glass transition into a full targeting circle.
//  When in positioning mode, draws a distinct sky blue halo ring.
// ─────────────────────────────────────────────────────────────────────────────
class CircleView(context: Context) : android.view.View(context) {

    var isDragging = false
        set(v) {
            if (field == v) return
            field = v
            invalidate()
        }

    var isPositioningMode = false
        set(v) {
            if (field == v) return
            field = v
            invalidate()
        }

    var alphaPercent: Int = 80
        set(v) {
            field = v.coerceIn(20, 100)
            updatePaints()
            invalidate()
        }

    private val bodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val pipPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = 0x88FFFFFF.toInt()
    }
    private val posModeRingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2.5f
        color = 0xFF38BDF8.toInt() // Sky blue glow when moving resting spot
    }

    init {
        updatePaints()
    }

    fun updatePaints() {
        val alphaVal = (255 * (alphaPercent / 100f).coerceIn(0.2f, 1f)).toInt()
        // Muted frosted slate grey: high contrast on pure AMOLED black, soft and translucent
        bodyPaint.color = (alphaVal shl 24) or 0x2E384D
    }

    override fun onDraw(canvas: Canvas) {
        val cx = width / 2f
        val cy = height / 2f
        val r = (minOf(width, height) / 2f) - 3f

        if (isPositioningMode) {
            canvas.drawCircle(cx, cy, r + 3f, posModeRingPaint)
        }

        // Muted frosted slate glass circle (borderless / no stroke)
        canvas.drawCircle(cx, cy, r, bodyPaint)

        // Center pip when dragging
        if (isDragging) {
            canvas.drawCircle(cx, cy, 3.5f, pipPaint)
        }
    }
}


// ─────────────────────────────────────────────────────────────────────────────
//  HighlightView — Full-screen overlay.
//  Draws: word/block selection highlights + RED cross to the LEFT of the circle.
// ─────────────────────────────────────────────────────────────────────────────
class HighlightView(context: Context) : android.view.View(context) {

    data class HighlightRect(val rect: RectF, val isBlock: Boolean = false)

    var highlights: List<HighlightRect> = emptyList()
        set(v) { field = v; invalidate() }

    private var crossCX: Float? = null
    private var crossCY: Float? = null
    private var circleCX: Float? = null
    private var circleCY: Float? = null

    fun setPositions(
        circleCenterX: Float?, circleCenterY: Float?,
        crossCenterX: Float?, crossCenterY: Float?
    ) {
        circleCX = circleCenterX; circleCY = circleCenterY
        crossCX = crossCenterX;   crossCY = crossCenterY
        invalidate()
    }

    fun clearCross() {
        crossCX = null; crossCY = null
        circleCX = null; circleCY = null
        invalidate()
    }

    // ── Blue Highlight paints ────────────────────────────────────────────────
    private val wordFill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL; color = 0x2E38BDF8.toInt()
    }
    private val wordBorder = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeWidth = 2.0f; color = 0xFF38BDF8.toInt()
    }
    private val blockFill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL; color = 0x332563EB.toInt()
    }
    private val blockBorder = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeWidth = 2.4f; color = 0xFF3B82F6.toInt()
    }

    // ── RED Cross paints ──────────────────────────────────────────────────────
    private val crossPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2.4f
        strokeCap = Paint.Cap.ROUND
        color = 0xFFFF2D55.toInt() // Laser Red
    }
    private val connectorPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.2f
        color = 0x77FF2D55.toInt() // Soft red connector
        pathEffect = DashPathEffect(floatArrayOf(6f, 7f), 0f)
    }
    private val crossCenterPip = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = 0xFFFF2D55.toInt() // Red center pip
    }

    private val cornerR get() = 8f * resources.displayMetrics.density

    override fun onDraw(canvas: Canvas) {
        // 1 — Selection highlights (Smooth rounded capsules)
        for (h in highlights) {
            val p = if (h.isBlock) Pair(blockFill, blockBorder) else Pair(wordFill, wordBorder)
            val r = if (h.isBlock) cornerR else (cornerR * 0.9f)
            canvas.drawRoundRect(h.rect, r, r, p.first)
            canvas.drawRoundRect(h.rect, r, r, p.second)
        }

        // 2 — RED Cross + connector line (only while dragging)
        val rx = crossCX ?: return
        val ry = crossCY ?: return

        // Thin dashed connector from cross center to circle center
        val cxCircle = circleCX
        val cyCircle = circleCY
        if (cxCircle != null && cyCircle != null) {
            canvas.drawLine(rx, ry, cxCircle, cyCircle, connectorPaint)
        }

        // Precision Red + cross
        val arm = 16f
        canvas.drawLine(rx - arm, ry, rx + arm, ry, crossPaint)  // horizontal
        canvas.drawLine(rx, ry - arm, rx, ry + arm, crossPaint)  // vertical

        // Red center pip
        canvas.drawCircle(rx, ry, 2.5f, crossCenterPip)
    }
}
