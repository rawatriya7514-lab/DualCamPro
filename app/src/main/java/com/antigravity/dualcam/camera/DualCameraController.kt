package com.antigravity.dualcam.camera

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import android.view.TextureView
import androidx.camera.core.CameraSelector
import com.antigravity.dualcam.model.LayoutMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Controller orchestrating true simultaneous Dual-Camera operations.
 * Drives the primary camera via SingleCameraController (CameraX preview + VideoCapture)
 * and streams the secondary camera concurrently via SecondaryCamera2Controller (Camera2 hardware direct stream).
 */
class DualCameraController(
    private val context: Context,
    private val singleCameraController: SingleCameraController,
    private val pipTexture: TextureView,
    private val splitTexture: TextureView,
    private val capabilities: CameraCapabilities,
    private val onSecondarySnapshotReady: ((Bitmap) -> Unit)? = null,
    private val onHardwareDualStateChanged: ((Boolean) -> Unit)? = null
) {
    var activeLayoutMode: LayoutMode = LayoutMode.SINGLE_REAR
        private set

    var isHardwareDualStreamingActive = false
        private set

    private val secondaryCamera2Controller = SecondaryCamera2Controller(context) { isStreaming ->
        isHardwareDualStreamingActive = isStreaming
        onHardwareDualStateChanged?.invoke(isStreaming)
    }

    fun setLayoutMode(mode: LayoutMode, onModeChanged: (LayoutMode) -> Unit) {
        activeLayoutMode = mode
        Log.i(TAG, "Setting LayoutMode to: $mode")

        if (mode == LayoutMode.SINGLE_REAR) {
            stopDualStreaming()
            singleCameraController.bindCamera(CameraSelector.LENS_FACING_BACK) {
                onModeChanged(mode)
            }
            return
        }

        if (mode == LayoutMode.SINGLE_FRONT) {
            stopDualStreaming()
            singleCameraController.bindCamera(CameraSelector.LENS_FACING_FRONT) {
                onModeChanged(mode)
            }
            return
        }

        // Dual Camera Modes (PiP or Split)
        bindDualCameraMode(mode, onModeChanged)
    }

    private fun bindDualCameraMode(mode: LayoutMode, onModeChanged: (LayoutMode) -> Unit) {
        // 1. Notify UI immediately to apply 50/50 split constraints and show view containers
        onModeChanged(mode)

        // 2. Identify primary and secondary camera IDs
        val isPrimaryFront = mode == LayoutMode.PIP_REAR_ON_FRONT
        val primaryLens = if (isPrimaryFront) CameraSelector.LENS_FACING_FRONT else CameraSelector.LENS_FACING_BACK
        val secondaryCameraId = if (isPrimaryFront) {
            capabilities.backCameraId ?: "0"
        } else {
            capabilities.frontCameraId ?: "1"
        }

        // 3. Bind primary camera stream
        singleCameraController.bindCamera(primaryLens) {
            // 4. Start concurrent secondary stream via direct Camera2
            val targetTexture = if (mode.isPiP) pipTexture else splitTexture
            try {
                secondaryCamera2Controller.startSecondaryStream(secondaryCameraId, targetTexture)
            } catch (e: Exception) {
                Log.w(TAG, "Secondary camera direct open failed: ${e.message}")
                fallbackSnapshot()
            }
        }
    }

    private fun fallbackSnapshot() {
        CoroutineScope(Dispatchers.Main).launch {
            delay(350)
            try {
                val currentBmp = singleCameraController.captureCurrentBitmap()
                if (currentBmp != null) {
                    onSecondarySnapshotReady?.invoke(currentBmp)
                }
            } catch (e: Exception) {
                Log.w(TAG, "Snapshot fallback: ${e.message}")
            }
        }
    }

    fun stopDualStreaming() {
        isHardwareDualStreamingActive = false
        secondaryCamera2Controller.stopSecondaryStream()
        onHardwareDualStateChanged?.invoke(false)
    }

    companion object {
        private const val TAG = "DualCameraController"
    }
}
