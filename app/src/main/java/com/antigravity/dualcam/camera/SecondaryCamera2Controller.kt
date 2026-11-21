package com.antigravity.dualcam.camera

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.SurfaceTexture
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CaptureRequest
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import android.view.Surface
import android.view.TextureView

/**
 * High-performance, direct Camera2 streaming controller for the secondary camera view.
 * Bypasses CameraX OEM vendor whitelist restrictions to stream the secondary camera sensor
 * directly to a TextureView for genuine simultaneous Dual-Camera preview on Android.
 */
class SecondaryCamera2Controller(
    private val context: Context,
    private val onStreamStateChanged: (Boolean) -> Unit
) {
    private val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
    private var cameraDevice: CameraDevice? = null
    private var captureSession: CameraCaptureSession? = null
    private var backgroundThread: HandlerThread? = null
    private var backgroundHandler: Handler? = null

    private var currentTextureView: TextureView? = null
    private var currentSurface: Surface? = null

    var isStreaming = false
        private set

    private fun startBackgroundThread() {
        if (backgroundThread == null) {
            backgroundThread = HandlerThread("SecondaryCamera2Thread").apply { start() }
            backgroundHandler = Handler(backgroundThread!!.looper)
        }
    }

    private fun stopBackgroundThread() {
        backgroundThread?.quitSafely()
        try {
            backgroundThread?.join(500)
        } catch (_: Exception) {}
        backgroundThread = null
        backgroundHandler = null
    }

    @SuppressLint("MissingPermission")
    fun startSecondaryStream(cameraId: String, textureView: TextureView) {
        stopSecondaryStream()
        startBackgroundThread()
        currentTextureView = textureView

        if (textureView.isAvailable) {
            openCameraWithSurface(cameraId, textureView.surfaceTexture!!)
        } else {
            textureView.surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                override fun onSurfaceTextureAvailable(st: SurfaceTexture, w: Int, h: Int) {
                    openCameraWithSurface(cameraId, st)
                }
                override fun onSurfaceTextureSizeChanged(st: SurfaceTexture, w: Int, h: Int) {}
                override fun onSurfaceTextureDestroyed(st: SurfaceTexture): Boolean = true
                override fun onSurfaceTextureUpdated(st: SurfaceTexture) {}
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun openCameraWithSurface(cameraId: String, surfaceTexture: SurfaceTexture) {
        val handler = backgroundHandler ?: return
        try {
            surfaceTexture.setDefaultBufferSize(1280, 720)
            val surface = Surface(surfaceTexture)
            currentSurface = surface

            cameraManager.openCamera(cameraId, object : CameraDevice.StateCallback() {
                override fun onOpened(camera: CameraDevice) {
                    cameraDevice = camera
                    try {
                        val requestBuilder = camera.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW).apply {
                            addTarget(surface)
                            set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE)
                            set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON)
                        }

                        camera.createCaptureSession(listOf(surface), object : CameraCaptureSession.StateCallback() {
                            override fun onConfigured(session: CameraCaptureSession) {
                                captureSession = session
                                try {
                                    session.setRepeatingRequest(requestBuilder.build(), null, handler)
                                    isStreaming = true
                                    onStreamStateChanged(true)
                                    Log.i(TAG, "Secondary Camera2 ($cameraId) streaming live successfully!")
                                } catch (e: Exception) {
                                    Log.e(TAG, "Failed to start repeating secondary request: ${e.message}")
                                    isStreaming = false
                                    onStreamStateChanged(false)
                                }
                            }

                            override fun onConfigureFailed(session: CameraCaptureSession) {
                                Log.e(TAG, "Secondary camera configure session failed")
                                isStreaming = false
                                onStreamStateChanged(false)
                            }
                        }, handler)
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed creating secondary camera session: ${e.message}")
                        isStreaming = false
                        onStreamStateChanged(false)
                    }
                }

                override fun onDisconnected(camera: CameraDevice) {
                    camera.close()
                    cameraDevice = null
                    isStreaming = false
                    onStreamStateChanged(false)
                }

                override fun onError(camera: CameraDevice, error: Int) {
                    Log.w(TAG, "Secondary Camera2 error: $error (HAL concurrent restriction)")
                    camera.close()
                    cameraDevice = null
                    isStreaming = false
                    onStreamStateChanged(false)
                }
            }, handler)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to open secondary camera ID $cameraId: ${e.message}")
            isStreaming = false
            onStreamStateChanged(false)
        }
    }

    fun stopSecondaryStream() {
        isStreaming = false
        try {
            captureSession?.stopRepeating()
            captureSession?.close()
        } catch (_: Exception) {}
        captureSession = null

        try {
            cameraDevice?.close()
        } catch (_: Exception) {}
        cameraDevice = null

        try {
            currentSurface?.release()
        } catch (_: Exception) {}
        currentSurface = null

        stopBackgroundThread()
        onStreamStateChanged(false)
    }

    companion object {
        private const val TAG = "SecondaryCamera2"
    }
}
