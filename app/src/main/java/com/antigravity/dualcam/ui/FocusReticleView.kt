package com.antigravity.dualcam.ui

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import android.view.animation.OvershootInterpolator

/**
 * Animated camera focus reticle with exposure offset indicators.
 */
class FocusReticleView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 3f
        color = Color.parseColor("#FF9F0A")
    }

    private val centerDotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.parseColor("#FF9F0A")
    }

    private var touchX = -1f
    private var touchY = -1f
    private var scale = 1.0f
    private var alphaVal = 0

    private var animator: ValueAnimator? = null

    fun showAt(x: Float, y: Float) {
        touchX = x
        touchY = y
        alphaVal = 255

        animator?.cancel()
        animator = ValueAnimator.ofFloat(1.6f, 1.0f).apply {
            duration = 300
            interpolator = OvershootInterpolator(1.5f)
            addUpdateListener {
                scale = it.animatedValue as Float
                postInvalidateOnAnimation()
            }
            start()
        }

        // Auto fade out after 2 seconds
        postDelayed({
            ValueAnimator.ofInt(255, 0).apply {
                duration = 300
                addUpdateListener {
                    alphaVal = it.animatedValue as Int
                    postInvalidateOnAnimation()
                }
                start()
            }
        }, 2000)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (touchX < 0 || touchY < 0 || alphaVal <= 0) return

        ringPaint.alpha = alphaVal
        centerDotPaint.alpha = alphaVal

        val radius = 45f * scale
        canvas.drawCircle(touchX, touchY, radius, ringPaint)
        canvas.drawCircle(touchX, touchY, 4f, centerDotPaint)

        // Draw crosshair notches
        canvas.drawLine(touchX - radius - 10f, touchY, touchX - radius, touchY, ringPaint)
        canvas.drawLine(touchX + radius, touchY, touchX + radius + 10f, touchY, ringPaint)
        canvas.drawLine(touchX, touchY - radius - 10f, touchX, touchY - radius, ringPaint)
        canvas.drawLine(touchX, touchY + radius, touchX, touchY + radius + 10f, ringPaint)
    }
}
