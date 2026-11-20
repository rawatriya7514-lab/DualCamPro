package com.antigravity.dualcam.model

import androidx.camera.core.CameraSelector

/**
 * Live runtime state of active camera session and pro controls.
 */
data class CameraState(
    var lensFacing: Int = CameraSelector.LENS_FACING_BACK,
    var layoutMode: LayoutMode = LayoutMode.SINGLE_REAR,
    var pipConfig: PiPConfig = PiPConfig(),
    var resolution: VideoResolution = VideoResolution.FHD_1080P,
    var fps: Int = 30,
    var zoomRatio: Float = 1.0f,
    var evCompensation: Float = 0.0f,
    var evStepIndex: Int = 0,
    var isTorchOn: Boolean = false,
    var isMicEnabled: Boolean = true,
    var isStabilizationOn: Boolean = true,
    var isAeAfLocked: Boolean = false,
    var whiteBalanceMode: WhiteBalanceMode = WhiteBalanceMode.AUTO,
    var manualIso: Int = 0, // 0 = Auto
    var manualFocusPercent: Float = 0f, // 0 = AF
    var gridStyle: GridStyle = GridStyle.OFF,
    var timerSeconds: Int = 0 // 0 = Off, 3, 5, 10
) {
    enum class VideoResolution(val width: Int, val height: Int, val label: String) {
        UHD_4K(3840, 2160, "4K UHD"),
        QHD_2K(2560, 1440, "2K QHD"),
        FHD_1080P(1920, 1080, "1080P FHD"),
        HD_720P(1280, 720, "720P HD")
    }

    enum class WhiteBalanceMode(val label: String) {
        AUTO("Auto"),
        DAYLIGHT("Daylight"),
        CLOUDY("Cloudy"),
        INCANDESCENT("Tungsten"),
        FLUORESCENT("Fluorescent")
    }

    enum class GridStyle(val label: String) {
        OFF("Off"),
        RULE_OF_THIRDS("3x3"),
        GOLDEN_RATIO("Golden"),
        CROSSHAIR("Cross"),
        SQUARE("1:1")
    }
}
