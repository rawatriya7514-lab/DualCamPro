package com.antigravity.dualcam.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import com.antigravity.dualcam.model.CameraState.GridStyle

/**
 * Renders composition guides: Rule of Thirds (3x3), Golden Ratio, Crosshair, and 1:1 Square.
 */
class GridOverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.5f
        color = Color.parseColor("#44FFFFFF")
    }

    private val letterboxPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.parseColor("#99000000")
    }

    var gridStyle: GridStyle = GridStyle.OFF
        set(value) {
            field = value
            invalidate()
        }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (gridStyle == GridStyle.OFF) return

        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0 || h <= 0) return

        when (gridStyle) {
            GridStyle.RULE_OF_THIRDS -> {
                val x1 = w / 3f
                val x2 = 2 * w / 3f
                val y1 = h / 3f
                val y2 = 2 * h / 3f

                canvas.drawLine(x1, 0f, x1, h, linePaint)
                canvas.drawLine(x2, 0f, x2, h, linePaint)
                canvas.drawLine(0f, y1, w, y1, linePaint)
                canvas.drawLine(0f, y2, w, y2, linePaint)
            }
            GridStyle.GOLDEN_RATIO -> {
                val phi = 0.618f
                val x1 = w * (1f - phi)
                val x2 = w * phi
                val y1 = h * (1f - phi)
                val y2 = h * phi

                canvas.drawLine(x1, 0f, x1, h, linePaint)
                canvas.drawLine(x2, 0f, x2, h, linePaint)
                canvas.drawLine(0f, y1, w, y1, linePaint)
                canvas.drawLine(0f, y2, w, y2, linePaint)
            }
            GridStyle.CROSSHAIR -> {
                val cx = w / 2f
                val cy = h / 2f
                val armLength = 40f

                canvas.drawLine(cx - armLength, cy, cx + armLength, cy, linePaint)
                canvas.drawLine(cx, cy - armLength, cx, cy + armLength, linePaint)
                canvas.drawCircle(cx, cy, 20f, linePaint)
            }
            GridStyle.SQUARE -> {
                // 1:1 Square letterbox overlay
                val side = minOf(w, h)
                val top = (h - side) / 2f
                val bottom = top + side

                // Draw top & bottom dark letterboxes
                if (top > 0) {
                    canvas.drawRect(0f, 0f, w, top, letterboxPaint)
                    canvas.drawRect(0f, bottom, w, h, letterboxPaint)
                }
                canvas.drawRect(0f, top, w, bottom, linePaint)
            }
            GridStyle.OFF -> {}
        }
    }
}
