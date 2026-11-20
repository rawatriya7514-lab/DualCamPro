package com.antigravity.dualcam.templates

import com.antigravity.dualcam.model.CameraState.GridStyle
import com.antigravity.dualcam.model.CameraState.VideoResolution
import com.antigravity.dualcam.model.LayoutMode
import com.antigravity.dualcam.model.PiPConfig
import com.antigravity.dualcam.templates.RecordingTemplate.TemplateId

object TemplateManager {
    val templates: List<RecordingTemplate> = listOf(
        RecordingTemplate(
            id = TemplateId.VLOGGING,
            title = "Vlogging Mode",
            description = "Front-facing PiP with audio optimization for personal daily vlogging.",
            badge = "PiP • 1080p 30fps",
            layoutMode = LayoutMode.PIP_FRONT_ON_REAR,
            pipConfig = PiPConfig(corner = PiPConfig.PiPCorner.TOP_RIGHT, sizePreset = PiPConfig.PiPSizePreset.MEDIUM),
            resolution = VideoResolution.FHD_1080P,
            targetFps = 30,
            isMicEnabled = true,
            isStabilizationEnabled = true,
            gridStyle = GridStyle.RULE_OF_THIRDS,
            timerSeconds = 0
        ),
        RecordingTemplate(
            id = TemplateId.YOUTUBE,
            title = "YouTube Mode",
            description = "Cinematic 16:9 widescreen capture, 60fps high bitrate, rule of thirds grid.",
            badge = "16:9 • 60fps High",
            layoutMode = LayoutMode.SINGLE_REAR,
            pipConfig = PiPConfig(),
            resolution = VideoResolution.UHD_4K,
            targetFps = 60,
            isMicEnabled = true,
            isStabilizationEnabled = true,
            gridStyle = GridStyle.RULE_OF_THIRDS,
            timerSeconds = 3
        ),
        RecordingTemplate(
            id = TemplateId.INSTAGRAM_REELS,
            title = "Instagram Reels / TikTok",
            description = "Full 9:16 vertical 1080x1920 capture with instant countdown timer.",
            badge = "9:16 • 1080p 30fps",
            layoutMode = LayoutMode.SINGLE_FRONT,
            pipConfig = PiPConfig(),
            resolution = VideoResolution.FHD_1080P,
            targetFps = 30,
            isMicEnabled = true,
            isStabilizationEnabled = true,
            gridStyle = GridStyle.OFF,
            timerSeconds = 3
        ),
        RecordingTemplate(
            id = TemplateId.INTERVIEW,
            title = "Interview Mode",
            description = "50/50 Split screen balancing interviewer and interviewee in one seamless frame.",
            badge = "Split 50/50 • Dual Audio",
            layoutMode = LayoutMode.SPLIT_HORIZONTAL,
            pipConfig = PiPConfig(),
            resolution = VideoResolution.FHD_1080P,
            targetFps = 30,
            isMicEnabled = true,
            isStabilizationEnabled = true,
            gridStyle = GridStyle.CROSSHAIR,
            timerSeconds = 0
        ),
        RecordingTemplate(
            id = TemplateId.REACTION,
            title = "Reaction Mode",
            description = "70% content view with 30% circular reaction window in lower corner.",
            badge = "Reaction PiP • Prominent Audio",
            layoutMode = LayoutMode.PIP_FRONT_ON_REAR,
            pipConfig = PiPConfig(corner = PiPConfig.PiPCorner.BOTTOM_RIGHT, sizePreset = PiPConfig.PiPSizePreset.SMALL),
            resolution = VideoResolution.FHD_1080P,
            targetFps = 30,
            isMicEnabled = true,
            isStabilizationEnabled = false,
            gridStyle = GridStyle.OFF,
            timerSeconds = 0
        ),
        RecordingTemplate(
            id = TemplateId.TRAVEL,
            title = "Travel & Scenery",
            description = "Ultrawide 0.5x priority with active EIS video stabilization.",
            badge = "0.5x Ultrawide • EIS Active",
            layoutMode = LayoutMode.SINGLE_REAR,
            pipConfig = PiPConfig(),
            resolution = VideoResolution.UHD_4K,
            targetFps = 60,
            isMicEnabled = true,
            isStabilizationEnabled = true,
            gridStyle = GridStyle.GOLDEN_RATIO,
            timerSeconds = 0
        ),
        RecordingTemplate(
            id = TemplateId.PODCAST,
            title = "Video Podcast",
            description = "Side-by-side video with pristine 320kbps AAC studio audio leveling.",
            badge = "Dual-Cam • Studio Audio",
            layoutMode = LayoutMode.SPLIT_HORIZONTAL,
            pipConfig = PiPConfig(),
            resolution = VideoResolution.FHD_1080P,
            targetFps = 30,
            isMicEnabled = true,
            isStabilizationEnabled = false,
            gridStyle = GridStyle.OFF,
            timerSeconds = 5
        ),
        RecordingTemplate(
            id = TemplateId.TUTORIAL,
            title = "Tutorial / Walkthrough",
            description = "Presenter top-corner PiP with crisp manual focus lock on subject.",
            badge = "Top PiP • Focus Lock",
            layoutMode = LayoutMode.PIP_FRONT_ON_REAR,
            pipConfig = PiPConfig(corner = PiPConfig.PiPCorner.TOP_LEFT, sizePreset = PiPConfig.PiPSizePreset.SMALL),
            resolution = VideoResolution.FHD_1080P,
            targetFps = 30,
            isMicEnabled = true,
            isStabilizationEnabled = true,
            gridStyle = GridStyle.RULE_OF_THIRDS,
            timerSeconds = 0
        ),
        RecordingTemplate(
            id = TemplateId.CINEMATIC,
            title = "Cinematic 24FPS",
            description = "True 24 FPS motion blur, 2.39:1 letterbox guides, natural color tone.",
            badge = "24 FPS • Film Look",
            layoutMode = LayoutMode.SINGLE_REAR,
            pipConfig = PiPConfig(),
            resolution = VideoResolution.UHD_4K,
            targetFps = 24,
            isMicEnabled = true,
            isStabilizationEnabled = true,
            gridStyle = GridStyle.GOLDEN_RATIO,
            timerSeconds = 3
        ),
        RecordingTemplate(
            id = TemplateId.DUAL_CAMERA_CUSTOM,
            title = "Dual-Cam Pro Custom",
            description = "Full manual control over front + rear cameras and compositing layouts.",
            badge = "Full Manual • Pro Dual",
            layoutMode = LayoutMode.PIP_FRONT_ON_REAR,
            pipConfig = PiPConfig(corner = PiPConfig.PiPCorner.TOP_RIGHT, sizePreset = PiPConfig.PiPSizePreset.MEDIUM),
            resolution = VideoResolution.FHD_1080P,
            targetFps = 30,
            isMicEnabled = true,
            isStabilizationEnabled = true,
            gridStyle = GridStyle.RULE_OF_THIRDS,
            timerSeconds = 0
        )
    )

    fun getTemplate(id: TemplateId): RecordingTemplate {
        return templates.first { it.id == id }
    }
}
