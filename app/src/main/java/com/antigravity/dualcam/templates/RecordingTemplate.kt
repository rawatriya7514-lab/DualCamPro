package com.antigravity.dualcam.templates

import com.antigravity.dualcam.model.CameraState.GridStyle
import com.antigravity.dualcam.model.CameraState.VideoResolution
import com.antigravity.dualcam.model.LayoutMode
import com.antigravity.dualcam.model.PiPConfig

data class RecordingTemplate(
    val id: TemplateId,
    val title: String,
    val description: String,
    val badge: String,
    val layoutMode: LayoutMode,
    val pipConfig: PiPConfig,
    val resolution: VideoResolution,
    val targetFps: Int,
    val isMicEnabled: Boolean,
    val isStabilizationEnabled: Boolean,
    val gridStyle: GridStyle,
    val timerSeconds: Int
) {
    enum class TemplateId {
        VLOGGING,
        YOUTUBE,
        INSTAGRAM_REELS,
        INTERVIEW,
        REACTION,
        TRAVEL,
        PODCAST,
        TUTORIAL,
        CINEMATIC,
        DUAL_CAMERA_CUSTOM
    }
}
