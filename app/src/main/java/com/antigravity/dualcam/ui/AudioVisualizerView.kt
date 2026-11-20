package com.antigravity.dualcam.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View

/**
 * Custom View rendering an LED-style audio decibel (dBFS) VU meter.
 * Green (-60 to -12 dB), Yellow (-12 to -3 dB), Red peak (-3 to 0 dB).
 */
class AudioVisualizerView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val segmentPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val backgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#331E212B")
    }

    private var currentNormalizedLevel = 0.0f
    private var peakNormalizedLevel = 0.0f
    private val numSegments = 10
    private val segmentRect = RectF()

    fun updateLevel(db: Float, normalized: Float) {
        currentNormalizedLevel = normalized.coerceIn(0f, 1f)
        if (currentNormalizedLevel > peakNormalizedLevel) {
            peakNormalizedLevel = currentNormalizedLevel
        } else {
            peakNormalizedLevel = (peakNormalizedLevel - 0.03f).coerceAtLeast(currentNormalizedLevel)
        }
        postInvalidateOnAnimation()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0 || h <= 0) return

        val padding = 2f
        val segmentWidth = (w - (numSegments - 1) * padding) / numSegments

        val activeCount = (currentNormalizedLevel * numSegments).toInt()
        val peakIndex = (peakNormalizedLevel * numSegments).toInt().coerceIn(0, numSegments - 1)

        for (i in 0 until numSegments) {
            val left = i * (segmentWidth + padding)
            val right = left + segmentWidth
            segmentRect.set(left, 0f, right, h)

            val isActive = i < activeCount
            val isPeak = i == peakIndex

            // Color coding: last 2 segments red, 2 before that yellow, rest green
            val segmentColor = when {
                i >= numSegments - 2 -> Color.parseColor("#FF3B30") // Red
                i >= numSegments - 4 -> Color.parseColor("#FF9F0A") // Yellow
                else -> Color.parseColor("#30D158") // Green
            }

            if (isActive || isPeak) {
                segmentPaint.color = segmentColor
            } else {
                segmentPaint.color = Color.parseColor("#22FFFFFF")
            }

            canvas.drawRoundRect(segmentRect, 3f, 3f, segmentPaint)
        }
    }
}
