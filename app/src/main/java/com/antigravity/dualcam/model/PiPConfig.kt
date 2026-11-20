package com.antigravity.dualcam.model

/**
 * Configuration for floating Picture-in-Picture camera window.
 */
data class PiPConfig(
    var corner: PiPCorner = PiPCorner.TOP_RIGHT,
    var sizePreset: PiPSizePreset = PiPSizePreset.MEDIUM,
    var cornerRadiusDp: Float = 14f,
    var borderWidthDp: Float = 2f,
    var customNormalizedX: Float = 0.65f,
    var customNormalizedY: Float = 0.08f,
    var customScale: Float = 1.0f
) {
    enum class PiPCorner {
        TOP_LEFT,
        TOP_RIGHT,
        BOTTOM_LEFT,
        BOTTOM_RIGHT,
        CUSTOM
    }

    enum class PiPSizePreset(val widthPercent: Float, val aspectRatio: Float) {
        SMALL(0.24f, 16f / 9f),
        MEDIUM(0.32f, 16f / 9f),
        LARGE(0.42f, 16f / 9f)
    }
}
