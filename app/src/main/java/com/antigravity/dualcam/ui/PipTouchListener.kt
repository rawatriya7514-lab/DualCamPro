package com.antigravity.dualcam.ui

import android.view.MotionEvent
import android.view.View
import kotlin.math.abs

/**
 * OnTouchListener for the draggable floating PiP camera window with tap-to-swap detection.
 */
class PipTouchListener(
    private val onSwapCamerasTapped: () -> Unit
) : View.OnTouchListener {

    private var dX = 0f
    private var dY = 0f
    private var startRawX = 0f
    private var startRawY = 0f
    private val CLICK_THRESHOLD_PX = 20

    override fun onTouch(view: View, event: MotionEvent): Boolean {
        val parent = view.parent as? View ?: return false

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                dX = view.x - event.rawX
                dY = view.y - event.rawY
                startRawX = event.rawX
                startRawY = event.rawY
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                var newX = event.rawX + dX
                var newY = event.rawY + dY

                // Screen bounds clamping
                val maxX = (parent.width - view.width).toFloat()
                val maxY = (parent.height - view.height).toFloat()

                newX = newX.coerceIn(16f, maxX - 16f)
                newY = newY.coerceIn(60f, maxY - 120f)

                view.x = newX
                view.y = newY
                return true
            }

            MotionEvent.ACTION_UP -> {
                val deltaX = abs(event.rawX - startRawX)
                val deltaY = abs(event.rawY - startRawY)

                if (deltaX < CLICK_THRESHOLD_PX && deltaY < CLICK_THRESHOLD_PX) {
                    // Tap detected -> swap cameras
                    onSwapCamerasTapped()
                }
                return true
            }
        }
        return false
    }
}
