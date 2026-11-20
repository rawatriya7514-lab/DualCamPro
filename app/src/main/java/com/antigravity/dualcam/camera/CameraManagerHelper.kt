package com.antigravity.dualcam.camera

import android.content.Context
import android.graphics.ImageFormat
import android.graphics.SurfaceTexture
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CameraMetadata
import android.hardware.camera2.params.StreamConfigurationMap
import android.media.MediaRecorder
import android.os.Build
import android.util.Log
import android.util.Range
import android.util.Rational
import android.util.Size
import com.antigravity.dualcam.model.CameraState.VideoResolution

object CameraManagerHelper {
    private const val TAG = "CameraManagerHelper"

    fun queryDeviceCapabilities(context: Context): CameraCapabilities {
        val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        val cameraIds = cameraManager.cameraIdList

        var backId: String? = null
        var frontId: String? = null
        var ultrawideId: String? = null

        val backResolutions = mutableListOf<VideoResolution>()
        val frontResolutions = mutableListOf<VideoResolution>()
        val supportedFpsList = mutableSetOf(30) // Default fallback

        var minZoom = 1.0f
        var maxZoom = 10.0f
        var hasFlash = false
        var hasEis = false
        var hasOis = false
        var evRange = Range(-12, 12)
        var evStep = Rational(1, 6)
        var isoRange: Range<Int>? = null
        var exposureTimeRange: Range<Long>? = null

        // 1. Identify Back, Front, and Ultrawide Camera IDs
        for (id in cameraIds) {
            try {
                val chars = cameraManager.getCameraCharacteristics(id)
                val facing = chars.get(CameraCharacteristics.LENS_FACING)
                val focalLengths = chars.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS) ?: floatArrayOf()

                if (facing == CameraCharacteristics.LENS_FACING_BACK) {
                    if (backId == null) {
                        backId = id
                    }
                    // Ultrawide check: focal length typically < 2.5mm
                    if (focalLengths.isNotEmpty() && focalLengths[0] < 2.5f) {
                        ultrawideId = id
                    }
                } else if (facing == CameraCharacteristics.LENS_FACING_FRONT) {
                    if (frontId == null) {
                        frontId = id
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to read characteristics for camera ID $id: ${e.message}")
            }
        }

        // 2. Query Back Camera Capabilities
        backId?.let { id ->
            try {
                val chars = cameraManager.getCameraCharacteristics(id)
                val map = chars.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
                backResolutions.addAll(extractSupportedResolutions(map))

                // Zoom Ratio
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    chars.get(CameraCharacteristics.CONTROL_ZOOM_RATIO_RANGE)?.let {
                        minZoom = it.lower
                        maxZoom = it.upper
                    }
                } else {
                    chars.get(CameraCharacteristics.SCALER_AVAILABLE_MAX_DIGITAL_ZOOM)?.let {
                        maxZoom = it
                    }
                }

                // FPS
                chars.get(CameraCharacteristics.CONTROL_AE_AVAILABLE_TARGET_FPS_RANGES)?.forEach {
                    if (it.upper == 24 || it.upper == 30 || it.upper == 60 || it.upper == 120) {
                        supportedFpsList.add(it.upper)
                    }
                }

                // Flash
                hasFlash = chars.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) ?: false

                // Stabilization
                chars.get(CameraCharacteristics.CONTROL_AVAILABLE_VIDEO_STABILIZATION_MODES)?.let {
                    hasEis = it.contains(CameraMetadata.CONTROL_VIDEO_STABILIZATION_MODE_ON)
                }
                chars.get(CameraCharacteristics.LENS_INFO_AVAILABLE_OPTICAL_STABILIZATION)?.let {
                    hasOis = it.contains(CameraMetadata.LENS_OPTICAL_STABILIZATION_MODE_ON)
                }

                // EV
                chars.get(CameraCharacteristics.CONTROL_AE_COMPENSATION_RANGE)?.let { evRange = it }
                chars.get(CameraCharacteristics.CONTROL_AE_COMPENSATION_STEP)?.let { evStep = it }

                // ISO & Shutter
                isoRange = chars.get(CameraCharacteristics.SENSOR_INFO_SENSITIVITY_RANGE)
                exposureTimeRange = chars.get(CameraCharacteristics.SENSOR_INFO_EXPOSURE_TIME_RANGE)
            } catch (e: Exception) {
                Log.e(TAG, "Error probing back camera: ${e.message}")
            }
        }

        // 3. Query Front Camera Capabilities
        frontId?.let { id ->
            try {
                val chars = cameraManager.getCameraCharacteristics(id)
                val map = chars.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
                frontResolutions.addAll(extractSupportedResolutions(map))
            } catch (e: Exception) {
                Log.e(TAG, "Error probing front camera: ${e.message}")
            }
        }

        // 4. Query Genuine Concurrent Camera Support (Android 11+ / API 30+)
        var isConcurrentSupported = false
        val concurrentCameraSets = mutableSetOf<Set<String>>()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                val sets = cameraManager.concurrentCameraIds
                concurrentCameraSets.addAll(sets)
                // Check if any set contains both a back and front camera
                for (set in sets) {
                    val containsBack = set.any { it == backId }
                    val containsFront = set.any { it == frontId }
                    if (containsBack && containsFront) {
                        isConcurrentSupported = true
                        break
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Concurrent cameras check threw exception: ${e.message}")
                isConcurrentSupported = false
            }
        }

        // 5. Generate Hardware Diagnostic Audit Report
        val reportBuilder = StringBuilder()
        reportBuilder.append("=== DUALCAM PRO HARDWARE AUDIT ===\n")
        reportBuilder.append("Device: ${Build.MANUFACTURER} ${Build.MODEL} (${Build.PRODUCT})\n")
        reportBuilder.append("Android Version: ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})\n")
        reportBuilder.append("Total Cameras Detected: ${cameraIds.size} (IDs: ${cameraIds.joinToString()})\n")
        reportBuilder.append("Primary Back Camera ID: ${backId ?: "None"}\n")
        reportBuilder.append("Primary Front Camera ID: ${frontId ?: "None"}\n")
        reportBuilder.append("Ultrawide Lens ID: ${ultrawideId ?: "None (Simulated via Digital Crop)"}\n\n")

        reportBuilder.append("--- CONCURRENT DUAL CAMERA CAPABILITY ---\n")
        if (isConcurrentSupported) {
            reportBuilder.append("Status: HARDWARE SUPPORTED [YES]\n")
            reportBuilder.append("Concurrent Combinations: $concurrentCameraSets\n")
            reportBuilder.append("Pipeline: Simultaneous dual-session streaming via Camera2\n")
        } else {
            reportBuilder.append("Status: SINGLE HAL SESSION ONLY [NO HARDWARE CONCURRENT]\n")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                reportBuilder.append("Reason: CameraManager.getConcurrentCameraIds() returned empty or single-facing sets.\n")
            } else {
                reportBuilder.append("Reason: Android version below API 30 (Android 11).\n")
            }
            reportBuilder.append("Alternative Active: Dual-Take Seamless Multi-Cam with zero-drop segment stitching\n")
        }

        reportBuilder.append("\n--- VIDEO RESOLUTIONS & FRAME RATES ---\n")
        reportBuilder.append("Back Camera Resolutions: ${backResolutions.joinToString { it.label }}\n")
        reportBuilder.append("Front Camera Resolutions: ${frontResolutions.joinToString { it.label }}\n")
        reportBuilder.append("Supported FPS: ${supportedFpsList.sorted().joinToString { "$it fps" }}\n")

        reportBuilder.append("\n--- PRO CONTROLS & SENSORS ---\n")
        reportBuilder.append("Zoom Range: ${String.format("%.1fx - %.1fx", minZoom, maxZoom)}\n")
        reportBuilder.append("Flash/Torch: ${if (hasFlash) "Available" else "Not Available"}\n")
        reportBuilder.append("Electronic Stabilization (EIS): ${if (hasEis) "Supported" else "Unsupported"}\n")
        reportBuilder.append("Optical Stabilization (OIS): ${if (hasOis) "Supported" else "Unsupported"}\n")
        reportBuilder.append("Exposure Compensation: ${evRange.lower} to ${evRange.upper} (step ${evStep.numerator}/${evStep.denominator})\n")
        isoRange?.let { reportBuilder.append("ISO Range: ${it.lower} - ${it.upper}\n") }
        exposureTimeRange?.let {
            val minSec = it.lower / 1_000_000_000.0
            val maxSec = it.upper / 1_000_000_000.0
            reportBuilder.append("Shutter Speed Range: 1/${(1.0/minSec).toInt()}s - ${String.format("%.2f", maxSec)}s\n")
        }

        return CameraCapabilities(
            isConcurrentDualCameraSupported = isConcurrentSupported,
            concurrentCameraSets = concurrentCameraSets,
            backCameraId = backId,
            frontCameraId = frontId,
            ultrawideCameraId = ultrawideId,
            backSupportedResolutions = if (backResolutions.isNotEmpty()) backResolutions else listOf(VideoResolution.FHD_1080P, VideoResolution.HD_720P),
            frontSupportedResolutions = if (frontResolutions.isNotEmpty()) frontResolutions else listOf(VideoResolution.FHD_1080P, VideoResolution.HD_720P),
            supportedFps = supportedFpsList.sorted(),
            minZoomRatio = minZoom,
            maxZoomRatio = maxZoom,
            hasUltrawideLens = ultrawideId != null,
            hasFlash = hasFlash,
            hasVideoStabilizationEis = hasEis,
            hasOpticalStabilizationOis = hasOis,
            exposureCompensationRange = evRange,
            exposureCompensationStep = evStep,
            isoRange = isoRange,
            exposureTimeRangeNs = exposureTimeRange,
            hardwareAuditReport = reportBuilder.toString()
        )
    }

    private fun extractSupportedResolutions(map: StreamConfigurationMap?): List<VideoResolution> {
        val list = mutableListOf<VideoResolution>()
        if (map == null) {
            return listOf(VideoResolution.FHD_1080P, VideoResolution.HD_720P)
        }

        val sizes = map.getOutputSizes(MediaRecorder::class.java)
            ?: map.getOutputSizes(SurfaceTexture::class.java)
            ?: emptyArray()

        var has4k = false
        var has2k = false
        var has1080p = false
        var has720p = false

        for (size in sizes) {
            val w = size.width
            val h = size.height
            if ((w >= 3840 && h >= 2160) || (h >= 3840 && w >= 2160)) has4k = true
            if ((w >= 2560 && h >= 1440) || (h >= 2560 && w >= 1440)) has2k = true
            if ((w >= 1920 && h >= 1080) || (h >= 1920 && w >= 1080)) has1080p = true
            if ((w >= 1280 && h >= 720) || (h >= 1280 && w >= 720)) has720p = true
        }

        if (has4k) list.add(VideoResolution.UHD_4K)
        if (has2k) list.add(VideoResolution.QHD_2K)
        if (has1080p) list.add(VideoResolution.FHD_1080P)
        if (has720p) list.add(VideoResolution.HD_720P)

        return if (list.isNotEmpty()) list else listOf(VideoResolution.FHD_1080P, VideoResolution.HD_720P)
    }
}
