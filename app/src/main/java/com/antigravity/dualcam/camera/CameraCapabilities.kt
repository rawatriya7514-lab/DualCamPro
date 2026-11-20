package com.antigravity.dualcam.camera

import android.util.Range
import android.util.Rational
import android.util.Size
import com.antigravity.dualcam.model.CameraState.VideoResolution

/**
 * Encapsulates detected hardware capabilities of the device's camera subsystem.
 */
data class CameraCapabilities(
    val isConcurrentDualCameraSupported: Boolean,
    val concurrentCameraSets: Set<Set<String>>,
    val backCameraId: String?,
    val frontCameraId: String?,
    val ultrawideCameraId: String?,
    val backSupportedResolutions: List<VideoResolution>,
    val frontSupportedResolutions: List<VideoResolution>,
    val supportedFps: List<Int>,
    val minZoomRatio: Float,
    val maxZoomRatio: Float,
    val hasUltrawideLens: Boolean,
    val hasFlash: Boolean,
    val hasVideoStabilizationEis: Boolean,
    val hasOpticalStabilizationOis: Boolean,
    val exposureCompensationRange: Range<Int>,
    val exposureCompensationStep: Rational,
    val isoRange: Range<Int>?,
    val exposureTimeRangeNs: Range<Long>?,
    val hardwareAuditReport: String
)
