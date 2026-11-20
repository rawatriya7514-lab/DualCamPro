package com.antigravity.dualcam.camera

import android.content.Context
import android.util.Log
import androidx.camera.core.Camera
import androidx.camera.core.CameraControl
import androidx.camera.core.CameraInfo
import androidx.camera.core.CameraSelector
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.Preview
import androidx.camera.core.SurfaceOrientedMeteringPointFactory
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.FileOutputOptions
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.antigravity.dualcam.model.CameraState
import com.antigravity.dualcam.model.CameraState.VideoResolution
import com.antigravity.dualcam.model.RecordingState
import com.antigravity.dualcam.recorder.MediaStoreHelper
import com.antigravity.dualcam.recorder.SegmentedRecordingMerger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File

class SingleCameraController(
    private val context: Context,
    private val lifecycleOwner: LifecycleOwner,
    private val previewView: PreviewView,
    private val onRecordingStateChanged: (RecordingState, Long) -> Unit,
    private val onVideoSaved: (File) -> Unit,
    private val onError: (String) -> Unit
) {
    private var cameraProvider: ProcessCameraProvider? = null
    private var camera: Camera? = null
    private var videoCapture: VideoCapture<Recorder>? = null
    private var activeRecording: Recording? = null

    var currentLensFacing: Int = CameraSelector.LENS_FACING_BACK
        private set

    var activeResolution: VideoResolution = VideoResolution.FHD_1080P
    var isMicEnabled: Boolean = true
    var isTorchEnabled: Boolean = false

    private val segmentFiles = mutableListOf<File>()
    private var currentSegmentFile: File? = null
    private var isSwitchingCameraDuringRecording = false
    private var recordedDurationMs: Long = 0L

    fun initialize(onInitialized: () -> Unit) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        cameraProviderFuture.addListener({
            try {
                cameraProvider = cameraProviderFuture.get()
                bindCamera(currentLensFacing)
                onInitialized()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to initialize CameraX: ${e.message}", e)
                onError("Failed to bind camera: ${e.message}")
            }
        }, ContextCompat.getMainExecutor(context))
    }

    fun bindCamera(lensFacing: Int) {
        val provider = cameraProvider ?: return
        currentLensFacing = lensFacing

        try {
            provider.unbindAll()

            val cameraSelector = CameraSelector.Builder()
                .requireLensFacing(lensFacing)
                .build()

            // Preview Use Case
            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(previewView.surfaceProvider)
            }

            // Quality Selection
            val targetQuality = when (activeResolution) {
                VideoResolution.UHD_4K -> Quality.UHD
                VideoResolution.QHD_2K -> Quality.UHD
                VideoResolution.FHD_1080P -> Quality.FHD
                VideoResolution.HD_720P -> Quality.HD
            }

            val qualitySelector = QualitySelector.from(
                targetQuality,
                androidx.camera.video.FallbackStrategy.lowerQualityOrHigherThan(Quality.SD)
            )

            val recorder = Recorder.Builder()
                .setQualitySelector(qualitySelector)
                .build()

            videoCapture = VideoCapture.withOutput(recorder)

            camera = provider.bindToLifecycle(
                lifecycleOwner,
                cameraSelector,
                preview,
                videoCapture
            )

            // Re-apply torch if needed
            if (isTorchEnabled && lensFacing == CameraSelector.LENS_FACING_BACK) {
                camera?.cameraControl?.enableTorch(true)
            }

            Log.i(TAG, "Camera bound successfully to lens $lensFacing at $activeResolution")
        } catch (e: Exception) {
            Log.e(TAG, "Error binding camera: ${e.message}", e)
            onError("Camera error: ${e.message}")
        }
    }

    fun startRecording() {
        val capture = videoCapture ?: run {
            onError("Camera not ready for recording")
            return
        }

        segmentFiles.clear()
        recordedDurationMs = 0L

        startSegmentRecording(capture)
    }

    private fun startSegmentRecording(capture: VideoCapture<Recorder>) {
        val segmentFile = MediaStoreHelper.createOutputVideoFile(context)
        currentSegmentFile = segmentFile
        val outputOptions = FileOutputOptions.Builder(segmentFile).build()

        try {
            val pending = capture.output.prepareRecording(context, outputOptions)
            if (isMicEnabled) {
                try {
                    pending.withAudioEnabled()
                } catch (e: SecurityException) {
                    Log.w(TAG, "Audio permission missing, recording without audio: ${e.message}")
                }
            }

            activeRecording = pending.start(ContextCompat.getMainExecutor(context)) { event ->
                when (event) {
                    is VideoRecordEvent.Start -> {
                        if (!isSwitchingCameraDuringRecording) {
                            onRecordingStateChanged(RecordingState.RECORDING, 0L)
                        }
                    }
                    is VideoRecordEvent.Status -> {
                        val duration = event.recordingStats.recordedDurationNanos / 1_000_000L
                        recordedDurationMs = duration
                        onRecordingStateChanged(RecordingState.RECORDING, duration)
                    }
                    is VideoRecordEvent.Pause -> {
                        onRecordingStateChanged(RecordingState.PAUSED, recordedDurationMs)
                    }
                    is VideoRecordEvent.Resume -> {
                        onRecordingStateChanged(RecordingState.RECORDING, recordedDurationMs)
                    }
                    is VideoRecordEvent.Finalize -> {
                        if (!event.hasError()) {
                            currentSegmentFile?.let { segmentFiles.add(it) }
                            Log.i(TAG, "Segment finalized successfully (${currentSegmentFile?.length()} bytes)")
                        } else {
                            Log.e(TAG, "Video recording segment error: ${event.error}")
                        }

                        if (isSwitchingCameraDuringRecording) {
                            // Resume recording next segment on opposite camera
                            isSwitchingCameraDuringRecording = false
                            videoCapture?.let { startSegmentRecording(it) }
                        } else {
                            // User ended recording -> merge segments if needed
                            finalizeRecordingOutput()
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start recording segment: ${e.message}", e)
            onError("Recording failed: ${e.message}")
            onRecordingStateChanged(RecordingState.IDLE, 0L)
        }
    }

    fun pauseRecording() {
        activeRecording?.pause()
    }

    fun resumeRecording() {
        activeRecording?.resume()
    }

    fun stopRecording() {
        onRecordingStateChanged(RecordingState.FINALIZING, recordedDurationMs)
        activeRecording?.stop()
        activeRecording = null
    }

    fun switchCamera() {
        val newLens = if (currentLensFacing == CameraSelector.LENS_FACING_BACK) {
            CameraSelector.LENS_FACING_FRONT
        } else {
            CameraSelector.LENS_FACING_BACK
        }

        if (activeRecording != null) {
            // SWITCH CAMERA WHILE RECORDING IS ACTIVE:
            // 1. Close current segment cleanly
            // 2. Flip lens
            // 3. Immediately start next segment and merge upon completion!
            isSwitchingCameraDuringRecording = true
            activeRecording?.stop()
            bindCamera(newLens)
        } else {
            bindCamera(newLens)
        }
    }

    private fun finalizeRecordingOutput() {
        CoroutineScope(Dispatchers.IO).launch {
            val finalOutputFile = MediaStoreHelper.createOutputVideoFile(context)

            val success = if (segmentFiles.size > 1) {
                Log.i(TAG, "Merging ${segmentFiles.size} segments into final video...")
                SegmentedRecordingMerger.mergeSegments(segmentFiles, finalOutputFile)
            } else if (segmentFiles.size == 1) {
                segmentFiles[0].renameTo(finalOutputFile)
                true
            } else {
                false
            }

            if (success) {
                MediaStoreHelper.saveVideoToMediaStore(context, finalOutputFile)
                ContextCompat.getMainExecutor(context).execute {
                    onRecordingStateChanged(RecordingState.IDLE, 0L)
                    onVideoSaved(finalOutputFile)
                }
            } else {
                ContextCompat.getMainExecutor(context).execute {
                    onError("Failed to finalize video")
                    onRecordingStateChanged(RecordingState.IDLE, 0L)
                }
            }
        }
    }

    fun setZoom(ratio: Float) {
        camera?.cameraControl?.setZoomRatio(ratio)
    }

    fun setExposure(index: Int) {
        camera?.cameraControl?.setExposureCompensationIndex(index)
    }

    fun toggleTorch(enable: Boolean) {
        isTorchEnabled = enable
        if (currentLensFacing == CameraSelector.LENS_FACING_BACK) {
            camera?.cameraControl?.enableTorch(enable)
        }
    }

    fun tapToFocus(x: Float, y: Float) {
        val factory = SurfaceOrientedMeteringPointFactory(
            previewView.width.toFloat(),
            previewView.height.toFloat()
        )
        val point = factory.createPoint(x, y)
        val action = FocusMeteringAction.Builder(point, FocusMeteringAction.FLAG_AF or FocusMeteringAction.FLAG_AE)
            .setAutoCancelDuration(3, java.util.concurrent.TimeUnit.SECONDS)
            .build()

        camera?.cameraControl?.startFocusAndMetering(action)
    }

    companion object {
        private const val TAG = "SingleCameraController"
    }
}
