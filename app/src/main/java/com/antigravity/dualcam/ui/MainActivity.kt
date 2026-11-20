package com.antigravity.dualcam.ui

import android.Manifest
import android.animation.ObjectAnimator
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Color
import android.media.ToneGenerator
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.StatFs
import android.view.MotionEvent
import android.view.View
import android.view.animation.AlphaAnimation
import android.view.animation.Animation
import android.widget.SeekBar
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.antigravity.dualcam.R
import com.antigravity.dualcam.camera.CameraCapabilities
import com.antigravity.dualcam.camera.CameraManagerHelper
import com.antigravity.dualcam.camera.DualCameraController
import com.antigravity.dualcam.camera.SingleCameraController
import com.antigravity.dualcam.databinding.ActivityMainBinding
import com.antigravity.dualcam.model.CameraState
import com.antigravity.dualcam.model.CameraState.GridStyle
import com.antigravity.dualcam.model.CameraState.VideoResolution
import com.antigravity.dualcam.model.LayoutMode
import com.antigravity.dualcam.model.RecordingState
import com.antigravity.dualcam.recorder.AudioLevelMonitor
import com.antigravity.dualcam.recorder.MediaStoreHelper
import com.antigravity.dualcam.templates.RecordingTemplate
import com.antigravity.dualcam.templates.TemplateManager
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private lateinit var singleCameraController: SingleCameraController
    private lateinit var dualCameraController: DualCameraController
    private lateinit var cameraCapabilities: CameraCapabilities
    private var audioLevelMonitor: AudioLevelMonitor? = null

    private var currentCameraState = CameraState()
    private var currentRecordingState = RecordingState.IDLE
    private var countdownJob: Job? = null
    private var toneGen: ToneGenerator? = null

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val cameraGranted = permissions[Manifest.permission.CAMERA] == true
        val audioGranted = permissions[Manifest.permission.RECORD_AUDIO] == true
        if (cameraGranted && audioGranted) {
            initAppAndCamera()
        } else {
            Toast.makeText(this, "Camera & Audio permissions are required to record videos", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        try {
            toneGen = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 100)
        } catch (_: Exception) {}

        if (hasRequiredPermissions()) {
            initAppAndCamera()
        } else {
            requestRequiredPermissions()
        }
    }

    private fun hasRequiredPermissions(): Boolean {
        val cam = ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        val audio = ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        return cam && audio
    }

    private fun requestRequiredPermissions() {
        val perms = mutableListOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            perms.add(Manifest.permission.READ_MEDIA_VIDEO)
        } else {
            perms.add(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
        permissionLauncher.launch(perms.toTypedArray())
    }

    private fun initAppAndCamera() {
        // 1. Hardware Audit
        cameraCapabilities = CameraManagerHelper.queryDeviceCapabilities(this)
        updateHardwareAuditBadge()

        // 2. Setup Single & Dual Camera Controllers
        singleCameraController = SingleCameraController(
            context = this,
            lifecycleOwner = this,
            previewView = binding.primaryPreview,
            onRecordingStateChanged = { state, durationMs ->
                onRecordingStateUpdated(state, durationMs)
            },
            onVideoSaved = { videoFile ->
                onVideoRecordingSaved(videoFile)
            },
            onError = { errMsg ->
                Toast.makeText(this, errMsg, Toast.LENGTH_SHORT).show()
            }
        )

        dualCameraController = DualCameraController(
            context = this,
            lifecycleOwner = this,
            primaryPreview = binding.primaryPreview,
            secondaryPreview = binding.pipPreview,
            singleCameraController = singleCameraController,
            capabilities = cameraCapabilities
        )

        // 3. Initialize CameraX
        singleCameraController.initialize {
            runOnUiThread {
                updateStorageInfo()
                updateLastVideoThumbnail()
            }
        }

        // 4. Setup Audio dB Monitor
        audioLevelMonitor = AudioLevelMonitor { db, normalized ->
            runOnUiThread {
                binding.audioVisualizer.updateLevel(db, normalized)
            }
        }
        audioLevelMonitor?.start()

        // 5. Wire UI controls
        setupPrimaryControls()
        setupProDrawerControls()
        setupTouchFocus()
        setupPiPInteraction()
    }

    private fun setupPrimaryControls() {
        // Shutter / Record Button
        binding.btnRecord.setOnClickListener {
            when (currentRecordingState) {
                RecordingState.IDLE -> handleStartRecordingRequested()
                RecordingState.RECORDING, RecordingState.PAUSED -> {
                    singleCameraController.stopRecording()
                }
                else -> {}
            }
        }

        // Camera Flip Button (One-tap front/back switch, seamless during recording!)
        binding.btnFlipCamera.setOnClickListener {
            // Animate flip icon rotation
            ObjectAnimator.ofFloat(binding.btnFlipCamera, "rotation", 0f, 180f).apply {
                duration = 300
                start()
            }
            singleCameraController.switchCamera()
        }

        // Dual-Camera Layout Switcher (PiP -> Split H -> Single)
        binding.btnDualCamMode.setOnClickListener {
            cycleNextLayoutMode()
        }

        // Quick Resolution Selector
        binding.btnResolution.setOnClickListener {
            cycleResolution()
        }

        // Quick FPS Selector
        binding.btnFps.setOnClickListener {
            cycleFps()
        }

        // Torch Toggle
        binding.btnTorch.setOnClickListener {
            currentCameraState.isTorchOn = !currentCameraState.isTorchOn
            singleCameraController.toggleTorch(currentCameraState.isTorchOn)
            if (currentCameraState.isTorchOn) {
                binding.btnTorch.setImageResource(R.drawable.ic_torch_on)
                binding.btnTorch.imageTintList = ColorStateList.valueOf(Color.parseColor("#FF9F0A"))
            } else {
                binding.btnTorch.setImageResource(R.drawable.ic_torch_off)
                binding.btnTorch.imageTintList = ColorStateList.valueOf(Color.WHITE)
            }
        }

        // Microphone Toggle
        binding.btnMic.setOnClickListener {
            currentCameraState.isMicEnabled = !currentCameraState.isMicEnabled
            singleCameraController.isMicEnabled = currentCameraState.isMicEnabled
            if (currentCameraState.isMicEnabled) {
                binding.btnMic.setImageResource(R.drawable.ic_mic_on)
                binding.btnMic.imageTintList = ColorStateList.valueOf(Color.WHITE)
                Toast.makeText(this, "Microphone: ON", Toast.LENGTH_SHORT).show()
            } else {
                binding.btnMic.setImageResource(R.drawable.ic_mic_off)
                binding.btnMic.imageTintList = ColorStateList.valueOf(Color.parseColor("#FF3B30"))
                Toast.makeText(this, "Microphone: MUTED", Toast.LENGTH_SHORT).show()
            }
        }

        // Grid Lines Toggle
        binding.btnGrid.setOnClickListener {
            cycleGridStyle()
        }

        // Timer Toggle
        binding.btnTimer.setOnClickListener {
            cycleTimer()
        }

        // Zoom Presets (0.5x, 1x, 2x, 5x)
        binding.btnZoomWide.setOnClickListener { applyZoom(0.5f, binding.btnZoomWide) }
        binding.btnZoom1x.setOnClickListener { applyZoom(1.0f, binding.btnZoom1x) }
        binding.btnZoom2x.setOnClickListener { applyZoom(2.0f, binding.btnZoom2x) }
        binding.btnZoom5x.setOnClickListener { applyZoom(5.0f, binding.btnZoom5x) }

        // Templates Modal
        binding.btnTemplates.setOnClickListener {
            showTemplatesDialog()
        }

        // Hardware Audit Badge
        binding.badgeHardwareAudit.setOnClickListener {
            showHardwareAuditDialog()
        }

        // Pro Controls Drawer
        binding.btnPro.setOnClickListener {
            binding.proDrawer.root.visibility = View.VISIBLE
        }
        binding.proDrawer.btnCloseProDrawer.setOnClickListener {
            binding.proDrawer.root.visibility = View.GONE
        }

        // Gallery Button
        binding.btnGallery.setOnClickListener {
            startActivity(Intent(this, GalleryActivity::class.java))
        }

        // Pause / Resume inside recording banner
        binding.btnPauseResume.setOnClickListener {
            if (currentRecordingState == RecordingState.RECORDING) {
                singleCameraController.pauseRecording()
            } else if (currentRecordingState == RecordingState.PAUSED) {
                singleCameraController.resumeRecording()
            }
        }
    }

    private fun setupProDrawerControls() {
        val drawer = binding.proDrawer

        // EV Compensation Slider
        drawer.seekExposure.max = 24
        drawer.seekExposure.progress = 12
        drawer.seekExposure.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    val evIndex = progress - 12
                    singleCameraController.setExposure(evIndex)
                    val evFloat = evIndex * 0.166f
                    drawer.tvEvValue.text = String.format("%+.1f EV", evFloat)
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        // ISO Slider
        drawer.seekIso.max = 3200
        drawer.seekIso.progress = 100
        drawer.seekIso.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    drawer.tvIsoValue.text = if (progress <= 50) "AUTO" else "$progress"
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        // Manual Focus Slider
        drawer.seekFocus.max = 100
        drawer.seekFocus.progress = 0
        drawer.seekFocus.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    drawer.tvFocusValue.text = if (progress == 0) "AF" else "${progress}%"
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        // White Balance Buttons
        val wbButtons = listOf(drawer.btnWbAuto, drawer.btnWbDaylight, drawer.btnWbCloudy, drawer.btnWbIncandescent, drawer.btnWbFluorescent)
        for (btn in wbButtons) {
            btn.setOnClickListener {
                for (b in wbButtons) {
                    b.setBackgroundResource(R.drawable.bg_glass_pill)
                    b.setTextColor(resources.getColor(R.color.text_secondary, null))
                }
                btn.setBackgroundResource(R.drawable.bg_glass_pill_active)
                btn.setTextColor(Color.WHITE)
            }
        }

        // EIS Stabilization Toggle
        drawer.btnToggleStabilization.setOnClickListener {
            currentCameraState.isStabilizationOn = !currentCameraState.isStabilizationOn
            if (currentCameraState.isStabilizationOn) {
                drawer.btnToggleStabilization.text = "EIS STABILIZATION: ON"
                drawer.btnToggleStabilization.setTextColor(Color.parseColor("#0A84FF"))
            } else {
                drawer.btnToggleStabilization.text = "EIS STABILIZATION: OFF"
                drawer.btnToggleStabilization.setTextColor(Color.parseColor("#A0A6B5"))
            }
        }

        // AE/AF Lock
        drawer.btnLockExposureFocus.setOnClickListener {
            currentCameraState.isAeAfLocked = !currentCameraState.isAeAfLocked
            if (currentCameraState.isAeAfLocked) {
                binding.tvLockAeAf.visibility = View.VISIBLE
                drawer.btnLockExposureFocus.text = "AE/AF: LOCKED"
                drawer.btnLockExposureFocus.setTextColor(Color.parseColor("#FF9F0A"))
            } else {
                binding.tvLockAeAf.visibility = View.GONE
                drawer.btnLockExposureFocus.text = "LOCK AE/AF"
                drawer.btnLockExposureFocus.setTextColor(Color.parseColor("#A0A6B5"))
            }
        }
    }

    private fun setupTouchFocus() {
        binding.primaryPreview.setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_UP) {
                val x = event.x
                val y = event.y
                singleCameraController.tapToFocus(x, y)
                binding.focusReticle.showAt(x, y)
            }
            true
        }
    }

    private fun setupPiPInteraction() {
        // PiP dragging and tap-to-swap
        binding.pipContainer.setOnTouchListener(PipTouchListener {
            // Tap on PiP -> swap main and PiP cameras
            singleCameraController.switchCamera()
            Toast.makeText(this, "Swapped Primary & Secondary Cameras", Toast.LENGTH_SHORT).show()
        })
    }

    private fun handleStartRecordingRequested() {
        val timerSec = currentCameraState.timerSeconds
        if (timerSec > 0) {
            startCountdown(timerSec) {
                singleCameraController.startRecording()
            }
        } else {
            singleCameraController.startRecording()
        }
    }

    private fun startCountdown(seconds: Int, onComplete: () -> Unit) {
        countdownJob?.cancel()
        binding.tvCountdown.visibility = View.VISIBLE

        countdownJob = lifecycleScope.launch {
            for (i in seconds downTo 1) {
                binding.tvCountdown.text = "$i"
                try { toneGen?.startTone(ToneGenerator.TONE_PROP_BEEP, 150) } catch (_: Exception) {}
                delay(1000)
            }
            binding.tvCountdown.visibility = View.GONE
            try { toneGen?.startTone(ToneGenerator.TONE_PROP_BEEP2, 300) } catch (_: Exception) {}
            onComplete()
        }
    }

    private fun onRecordingStateUpdated(state: RecordingState, durationMs: Long) {
        currentRecordingState = state
        when (state) {
            RecordingState.RECORDING -> {
                binding.recordingBanner.visibility = View.VISIBLE
                binding.recordInner.setBackgroundResource(R.drawable.btn_record_stop_inner)
                binding.btnPauseResume.setImageResource(R.drawable.ic_pause)

                // Format duration 00:00:00
                val totalSec = durationMs / 1000
                val min = totalSec / 60
                val sec = totalSec % 60
                val hrs = min / 60
                binding.tvRecTimer.text = if (hrs > 0) {
                    String.format("%02d:%02d:%02d", hrs, min % 60, sec)
                } else {
                    String.format("%02d:%02d", min, sec)
                }

                // Blink red dot
                if (binding.recDot.animation == null) {
                    val blink = AlphaAnimation(1f, 0.2f).apply {
                        duration = 500
                        repeatMode = Animation.REVERSE
                        repeatCount = Animation.INFINITE
                    }
                    binding.recDot.startAnimation(blink)
                }
            }
            RecordingState.PAUSED -> {
                binding.btnPauseResume.setImageResource(R.drawable.ic_play)
                binding.recDot.clearAnimation()
            }
            RecordingState.FINALIZING -> {
                binding.recordingBanner.visibility = View.GONE
                binding.recDot.clearAnimation()
                binding.recordInner.setBackgroundResource(R.drawable.btn_record_inner)
                Toast.makeText(this, "Finalizing recording...", Toast.LENGTH_SHORT).show()
            }
            RecordingState.IDLE -> {
                binding.recordingBanner.visibility = View.GONE
                binding.recDot.clearAnimation()
                binding.recordInner.setBackgroundResource(R.drawable.btn_record_inner)
            }
            else -> {}
        }
    }

    private fun onVideoRecordingSaved(file: File) {
        Toast.makeText(this, "Video saved to Movies/DualCam", Toast.LENGTH_SHORT).show()
        updateLastVideoThumbnail()
        updateStorageInfo()
    }

    private fun updateLastVideoThumbnail() {
        lifecycleScope.launch {
            val list = MediaStoreHelper.queryRecordedVideos(this@MainActivity)
            if (list.isNotEmpty()) {
                val thumb = MediaStoreHelper.extractThumbnail(this@MainActivity, list[0])
                if (thumb != null) {
                    binding.ivGalleryThumb.setImageBitmap(thumb)
                    binding.ivGalleryThumb.imageTintList = null
                }
            }
        }
    }

    private fun updateHardwareAuditBadge() {
        if (cameraCapabilities.isConcurrentDualCameraSupported) {
            binding.badgeHardwareAudit.text = "HAL DUAL: ACTIVE"
            binding.badgeHardwareAudit.setTextColor(Color.parseColor("#30D158"))
        } else {
            binding.badgeHardwareAudit.text = "HAL: MULTI-CAM"
            binding.badgeHardwareAudit.setTextColor(Color.parseColor("#FF9F0A"))
        }
    }

    private fun showHardwareAuditDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_hardware_audit, null)
        val tvReport = dialogView.findViewById<android.widget.TextView>(R.id.tvAuditReport)
        val btnClose = dialogView.findViewById<android.widget.ImageView>(R.id.btnCloseAudit)
        val btnOk = dialogView.findViewById<android.widget.Button>(R.id.btnOkAudit)

        tvReport.text = cameraCapabilities.hardwareAuditReport

        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .create()

        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        btnClose.setOnClickListener { dialog.dismiss() }
        btnOk.setOnClickListener { dialog.dismiss() }
        dialog.show()
    }

    private fun showTemplatesDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_templates, null)
        val rv = dialogView.findViewById<RecyclerView>(R.id.rvTemplates)
        val btnClose = dialogView.findViewById<android.widget.ImageView>(R.id.btnCloseTemplates)

        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .create()

        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        rv.layoutManager = LinearLayoutManager(this)
        rv.adapter = TemplatesAdapter(TemplateManager.templates) { selectedTemplate ->
            applyTemplate(selectedTemplate)
            dialog.dismiss()
        }

        btnClose.setOnClickListener { dialog.dismiss() }
        dialog.show()
    }

    private fun applyTemplate(template: RecordingTemplate) {
        currentCameraState.resolution = template.resolution
        currentCameraState.fps = template.targetFps
        currentCameraState.gridStyle = template.gridStyle
        currentCameraState.timerSeconds = template.timerSeconds
        currentCameraState.isStabilizationOn = template.isStabilizationEnabled
        currentCameraState.isMicEnabled = template.isMicEnabled

        binding.btnResolution.text = template.resolution.label.split(" ")[0]
        binding.btnFps.text = "${template.targetFps} FPS"
        binding.gridOverlay.gridStyle = template.gridStyle

        singleCameraController.activeResolution = template.resolution
        singleCameraController.isMicEnabled = template.isMicEnabled

        dualCameraController.setLayoutMode(template.layoutMode) { mode ->
            updateLayoutVisibility(mode)
        }

        Toast.makeText(this, "Applied ${template.title}", Toast.LENGTH_SHORT).show()
    }

    private fun cycleNextLayoutMode() {
        val next = when (currentCameraState.layoutMode) {
            LayoutMode.SINGLE_REAR -> LayoutMode.PIP_FRONT_ON_REAR
            LayoutMode.PIP_FRONT_ON_REAR -> LayoutMode.SPLIT_HORIZONTAL
            LayoutMode.SPLIT_HORIZONTAL -> LayoutMode.SINGLE_FRONT
            LayoutMode.SINGLE_FRONT -> LayoutMode.SINGLE_REAR
            else -> LayoutMode.SINGLE_REAR
        }
        currentCameraState.layoutMode = next
        dualCameraController.setLayoutMode(next) { mode ->
            updateLayoutVisibility(mode)
        }
    }

    private fun updateLayoutVisibility(mode: LayoutMode) {
        when {
            mode.isPiP -> {
                binding.pipContainer.visibility = View.VISIBLE
                binding.secondarySplitContainer.visibility = View.GONE
            }
            mode.isSplit -> {
                binding.pipContainer.visibility = View.GONE
                binding.secondarySplitContainer.visibility = View.VISIBLE
            }
            else -> {
                binding.pipContainer.visibility = View.GONE
                binding.secondarySplitContainer.visibility = View.GONE
            }
        }
    }

    private fun cycleResolution() {
        val supported = cameraCapabilities.backSupportedResolutions
        val currentIdx = supported.indexOf(currentCameraState.resolution)
        val next = supported[(currentIdx + 1) % supported.size]
        currentCameraState.resolution = next
        singleCameraController.activeResolution = next
        binding.btnResolution.text = next.label.split(" ")[0]
        Toast.makeText(this, "Resolution: ${next.label}", Toast.LENGTH_SHORT).show()
    }

    private fun cycleFps() {
        val fpsList = cameraCapabilities.supportedFps
        val currentIdx = fpsList.indexOf(currentCameraState.fps)
        val next = fpsList[(currentIdx + 1) % fpsList.size]
        currentCameraState.fps = next
        binding.btnFps.text = "$next FPS"
        Toast.makeText(this, "Frame Rate: $next FPS", Toast.LENGTH_SHORT).show()
    }

    private fun cycleGridStyle() {
        val styles = GridStyle.values()
        val next = styles[(currentCameraState.gridStyle.ordinal + 1) % styles.size]
        currentCameraState.gridStyle = next
        binding.gridOverlay.gridStyle = next
        Toast.makeText(this, "Grid: ${next.label}", Toast.LENGTH_SHORT).show()
    }

    private fun cycleTimer() {
        val timers = listOf(0, 3, 5, 10)
        val currentIdx = timers.indexOf(currentCameraState.timerSeconds)
        val next = timers[(currentIdx + 1) % timers.size]
        currentCameraState.timerSeconds = next
        if (next > 0) {
            binding.btnTimer.imageTintList = ColorStateList.valueOf(Color.parseColor("#FF9F0A"))
            Toast.makeText(this, "Timer: ${next}s", Toast.LENGTH_SHORT).show()
        } else {
            binding.btnTimer.imageTintList = ColorStateList.valueOf(Color.WHITE)
            Toast.makeText(this, "Timer: OFF", Toast.LENGTH_SHORT).show()
        }
    }

    private fun applyZoom(ratio: Float, selectedBtn: android.widget.TextView) {
        val zoomButtons = listOf(binding.btnZoomWide, binding.btnZoom1x, binding.btnZoom2x, binding.btnZoom5x)
        for (btn in zoomButtons) {
            btn.setBackgroundResource(R.drawable.bg_glass_pill)
            btn.setTextColor(resources.getColor(R.color.text_primary, null))
        }
        selectedBtn.setBackgroundResource(R.drawable.bg_glass_pill_active)
        selectedBtn.setTextColor(Color.WHITE)

        singleCameraController.setZoom(ratio)
    }

    private fun updateStorageInfo() {
        try {
            val stat = StatFs(Environment.getExternalStorageDirectory().path)
            val bytesAvailable = stat.availableBlocksLong * stat.blockSizeLong
            val gb = bytesAvailable / (1024.0 * 1024 * 1024)
            // Estimate at 20 Mbps (approx 9GB per hour)
            val hoursLeft = (gb / 9.0).coerceAtLeast(0.1)
            binding.tvStorageInfo.text = String.format("%.0f GB • ~%.1fh", gb, hoursLeft)
        } catch (e: Exception) {
            binding.tvStorageInfo.text = "Storage OK"
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        audioLevelMonitor?.stop()
        toneGen?.release()
        toneGen = null
    }
}
