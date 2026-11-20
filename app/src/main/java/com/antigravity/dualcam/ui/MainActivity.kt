package com.antigravity.dualcam.ui

import android.Manifest
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Color
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.Bundle
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import android.view.animation.AlphaAnimation
import android.view.animation.Animation
import android.widget.ImageView
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.constraintlayout.widget.ConstraintSet
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
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
import java.util.Locale

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

    private var currentZoomRatio = 1.0f
    private lateinit var scaleGestureDetector: ScaleGestureDetector
    private lateinit var gestureDetector: GestureDetector

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

        // Handle punch-hole camera cutouts and system navigation bar insets safely
        ViewCompat.setOnApplyWindowInsetsListener(binding.rootContainer) { _, insets ->
            val statusBars = insets.getInsets(WindowInsetsCompat.Type.statusBars())
            val navBars = insets.getInsets(WindowInsetsCompat.Type.navigationBars())

            // Add dynamic top margin for topBar to clear punch-hole / notch
            val topParams = binding.topBar.layoutParams as androidx.constraintlayout.widget.ConstraintLayout.LayoutParams
            topParams.topMargin = (statusBars.top + 8).coerceAtLeast(44)
            binding.topBar.layoutParams = topParams

            // Add dynamic bottom padding for bottomDeck to clear gesture navigation bar
            val bottomPadding = (navBars.bottom + 16).coerceAtLeast(24)
            binding.bottomDeck.setPadding(0, 10, 0, bottomPadding)
            binding.proDrawer.root.setPadding(16, 16, 16, bottomPadding)
            insets
        }

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
            pipPreview = binding.pipPreview,
            splitPreview = binding.secondarySplitPreview,
            singleCameraController = singleCameraController,
            capabilities = cameraCapabilities,
            onSecondarySnapshotReady = { bitmap ->
                runOnUiThread {
                    if (!dualCameraController.isHardwareDualStreamingActive) {
                        binding.ivPipSnapshot.setImageBitmap(bitmap)
                        binding.ivPipSnapshot.visibility = View.VISIBLE
                        binding.ivSplitSnapshot.setImageBitmap(bitmap)
                        binding.ivSplitSnapshot.visibility = View.VISIBLE
                    }
                }
            }
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
        setupTouchFocusAndGestures()
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

        // Camera Flip Button (Live 3D seamless flip transition)
        binding.btnFlipCamera.setOnClickListener {
            performLiveCameraSwitch()
        }

        // Templates Mode Pill (Tier 1 Dock)
        binding.btnTemplates.setOnClickListener {
            showTemplatesDialog()
        }

        // Dual-Camera Layout Switcher Pill (Tier 1 Dock)
        binding.btnDualCamMode.setOnClickListener {
            cycleNextLayoutMode()
        }

        // Pro Controls Pill (Tier 1 Dock) -> Hide bottomDeck to avoid overlap!
        binding.btnPro.setOnClickListener {
            binding.bottomDeck.visibility = View.GONE
            binding.proDrawer.root.visibility = View.VISIBLE
        }
        binding.proDrawer.btnCloseProDrawer.setOnClickListener {
            binding.proDrawer.root.visibility = View.GONE
            binding.bottomDeck.visibility = View.VISIBLE
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

        // Horizontal Zoom Selector (0.5x, 1x, 2x, 5x)
        binding.btnZoomWide.setOnClickListener { applyZoom(0.5f, binding.btnZoomWide) }
        binding.btnZoom1x.setOnClickListener { applyZoom(1.0f, binding.btnZoom1x) }
        binding.btnZoom2x.setOnClickListener { applyZoom(2.0f, binding.btnZoom2x) }
        binding.btnZoom5x.setOnClickListener { applyZoom(5.0f, binding.btnZoom5x) }

        // Hardware Audit Badge
        binding.badgeHardwareAudit.setOnClickListener {
            showHardwareAuditDialog()
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

    /**
     * Executes an ultra-smooth, cinematic 3D card flip transition between camera lenses.
     * Prevents black screens, flickering, and dropped frames during both preview and active recording.
     */
    private fun performLiveCameraSwitch() {
        // 1. Capture snapshot of current frame
        val snapshot = binding.primaryPreview.bitmap
        if (snapshot != null) {
            binding.ivSwitchTransition.setImageBitmap(snapshot)
            binding.ivSwitchTransition.visibility = View.VISIBLE
            binding.ivSwitchTransition.alpha = 1f
            binding.ivSwitchTransition.rotationY = 0f
            binding.ivSwitchTransition.scaleX = 1f
            binding.ivSwitchTransition.scaleY = 1f
        }

        // 2. Animate flip icon rotation
        binding.ivFlipIcon.animate()
            .rotationBy(180f)
            .setDuration(350)
            .start()

        // 3. Set camera distance perspective for 3D card rotation
        val distance = 6000f * resources.displayMetrics.density
        binding.ivSwitchTransition.cameraDistance = distance
        binding.primaryPreview.cameraDistance = distance

        // 4. Animate flip 0 -> 90 degrees (folding away)
        binding.ivSwitchTransition.animate()
            .rotationY(90f)
            .scaleX(0.92f)
            .scaleY(0.92f)
            .setDuration(160)
            .withEndAction {
                // At apex of fold (invisible), trigger camera switch
                singleCameraController.switchCamera {
                    runOnUiThread {
                        // 5. Unfold from -90 -> 0 degrees displaying new live feed
                        binding.ivSwitchTransition.rotationY = -90f
                        binding.ivSwitchTransition.animate()
                            .rotationY(0f)
                            .scaleX(1f)
                            .scaleY(1f)
                            .alpha(0f)
                            .setDuration(160)
                            .withEndAction {
                                binding.ivSwitchTransition.visibility = View.GONE
                                binding.ivSwitchTransition.alpha = 1f
                                binding.ivSwitchTransition.rotationY = 0f
                            }
                            .start()
                    }
                }
            }
            .start()
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
                    drawer.tvEvValue.text = String.format(Locale.US, "%+.1f EV", evFloat)
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
                    b.setTextColor(Color.WHITE)
                }
                btn.setBackgroundResource(R.drawable.bg_glass_pill_active)
                btn.setTextColor(Color.BLACK)
                Toast.makeText(this, "White Balance: ${btn.text}", Toast.LENGTH_SHORT).show()
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

    private fun setupTouchFocusAndGestures() {
        scaleGestureDetector = ScaleGestureDetector(this, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(detector: ScaleGestureDetector): Boolean {
                currentZoomRatio = (currentZoomRatio * detector.scaleFactor).coerceIn(
                    cameraCapabilities.minZoomRatio,
                    cameraCapabilities.maxZoomRatio
                )
                singleCameraController.setZoom(currentZoomRatio)
                updateZoomIndicator(currentZoomRatio)
                return true
            }
        })

        gestureDetector = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                val x = e.x
                val y = e.y
                singleCameraController.tapToFocus(x, y)
                binding.focusReticle.showAt(x, y)
                return true
            }

            override fun onDoubleTap(e: MotionEvent): Boolean {
                performLiveCameraSwitch()
                return true
            }
        })

        binding.primaryPreview.setOnTouchListener { _, event ->
            scaleGestureDetector.onTouchEvent(event)
            gestureDetector.onTouchEvent(event)
            true
        }
    }

    private fun updateZoomIndicator(ratio: Float) {
        val label = String.format(Locale.US, "%.1fx", ratio)
        binding.btnZoom1x.text = label
    }

    private fun setupPiPInteraction() {
        // PiP dragging and tap-to-swap
        binding.pipContainer.setOnTouchListener(PipTouchListener {
            // Tap on PiP -> swap main and PiP cameras
            performLiveCameraSwitch()
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
                    String.format(Locale.US, "%02d:%02d:%02d", hrs, min % 60, sec)
                } else {
                    String.format(Locale.US, "%02d:%02d", min, sec)
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
        val sizeMb = String.format(Locale.US, "%.1f MB", file.length() / (1024.0 * 1024.0))
        Toast.makeText(this, "Saved: ${file.name} ($sizeMb)", Toast.LENGTH_SHORT).show()
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

    private fun updateStorageInfo() {
        val freeBytes = MediaStoreHelper.getAvailableStorageBytes(this)
        val freeGb = freeBytes / (1024L * 1024L * 1024L)
        binding.tvStorageInfo.text = "$freeGb GB"
    }

    private fun updateHardwareAuditBadge() {
        if (cameraCapabilities.isConcurrentDualCameraSupported) {
            binding.badgeHardwareAudit.text = "HAL DUAL: ACTIVE"
            binding.badgeHardwareAudit.setTextColor(Color.parseColor("#30D158"))
        } else {
            binding.badgeHardwareAudit.text = "DUAL CAM"
            binding.badgeHardwareAudit.setTextColor(Color.parseColor("#FF9F0A"))
        }
    }

    private fun showHardwareAuditDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_hardware_audit, null)
        val tvReport = dialogView.findViewById<TextView>(R.id.tvAuditReport)
        val btnClose = dialogView.findViewById<ImageView>(R.id.btnCloseAudit)
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
        val btnClose = dialogView.findViewById<ImageView>(R.id.btnCloseTemplates)

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

        binding.tvActiveTemplateLabel.text = template.title.split(" ")[0].uppercase()
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
            LayoutMode.PIP_FRONT_ON_REAR -> LayoutMode.SPLIT_VERTICAL
            LayoutMode.SPLIT_VERTICAL -> LayoutMode.SPLIT_HORIZONTAL
            LayoutMode.SPLIT_HORIZONTAL -> LayoutMode.SINGLE_FRONT
            LayoutMode.SINGLE_FRONT -> LayoutMode.SINGLE_REAR
            else -> LayoutMode.SINGLE_REAR
        }
        currentCameraState.layoutMode = next
        binding.tvActiveDualModeLabel.text = when (next) {
            LayoutMode.PIP_FRONT_ON_REAR -> "PIP"
            LayoutMode.SPLIT_VERTICAL -> "SPLIT V"
            LayoutMode.SPLIT_HORIZONTAL -> "SPLIT H"
            LayoutMode.SINGLE_FRONT -> "FRONT"
            LayoutMode.SINGLE_REAR -> "DUAL"
            else -> "DUAL"
        }

        dualCameraController.setLayoutMode(next) { mode ->
            updateLayoutVisibility(mode)
        }
    }

    /**
     * Dynamically adjusts ConstraintLayout constraints to ensure genuine 50/50 split screen layouts.
     */
    private fun applyLayoutModeConstraints(mode: LayoutMode) {
        val constraintSet = ConstraintSet()
        constraintSet.clone(binding.rootContainer)

        when (mode) {
            LayoutMode.SPLIT_VERTICAL -> {
                // Top 50% for Primary Preview
                constraintSet.connect(R.id.primaryPreview, ConstraintSet.TOP, ConstraintSet.PARENT_ID, ConstraintSet.TOP)
                constraintSet.connect(R.id.primaryPreview, ConstraintSet.BOTTOM, R.id.guidelineCenterH, ConstraintSet.TOP)
                constraintSet.connect(R.id.primaryPreview, ConstraintSet.START, ConstraintSet.PARENT_ID, ConstraintSet.START)
                constraintSet.connect(R.id.primaryPreview, ConstraintSet.END, ConstraintSet.PARENT_ID, ConstraintSet.END)

                // Bottom 50% for Secondary Split View
                constraintSet.connect(R.id.secondarySplitContainer, ConstraintSet.TOP, R.id.guidelineCenterH, ConstraintSet.BOTTOM)
                constraintSet.connect(R.id.secondarySplitContainer, ConstraintSet.BOTTOM, ConstraintSet.PARENT_ID, ConstraintSet.BOTTOM)
                constraintSet.connect(R.id.secondarySplitContainer, ConstraintSet.START, ConstraintSet.PARENT_ID, ConstraintSet.START)
                constraintSet.connect(R.id.secondarySplitContainer, ConstraintSet.END, ConstraintSet.PARENT_ID, ConstraintSet.END)

                binding.pipContainer.visibility = View.GONE
                binding.secondarySplitContainer.visibility = View.VISIBLE
            }
            LayoutMode.SPLIT_HORIZONTAL, LayoutMode.CINEMATIC_SPLIT -> {
                // Left 50% for Primary Preview
                constraintSet.connect(R.id.primaryPreview, ConstraintSet.TOP, ConstraintSet.PARENT_ID, ConstraintSet.TOP)
                constraintSet.connect(R.id.primaryPreview, ConstraintSet.BOTTOM, ConstraintSet.PARENT_ID, ConstraintSet.BOTTOM)
                constraintSet.connect(R.id.primaryPreview, ConstraintSet.START, ConstraintSet.PARENT_ID, ConstraintSet.START)
                constraintSet.connect(R.id.primaryPreview, ConstraintSet.END, R.id.guidelineCenterV, ConstraintSet.START)

                // Right 50% for Secondary Split View
                constraintSet.connect(R.id.secondarySplitContainer, ConstraintSet.TOP, ConstraintSet.PARENT_ID, ConstraintSet.TOP)
                constraintSet.connect(R.id.secondarySplitContainer, ConstraintSet.BOTTOM, ConstraintSet.PARENT_ID, ConstraintSet.BOTTOM)
                constraintSet.connect(R.id.secondarySplitContainer, ConstraintSet.START, R.id.guidelineCenterV, ConstraintSet.END)
                constraintSet.connect(R.id.secondarySplitContainer, ConstraintSet.END, ConstraintSet.PARENT_ID, ConstraintSet.END)

                binding.pipContainer.visibility = View.GONE
                binding.secondarySplitContainer.visibility = View.VISIBLE
            }
            else -> {
                // Full Screen for Primary Preview
                constraintSet.connect(R.id.primaryPreview, ConstraintSet.TOP, ConstraintSet.PARENT_ID, ConstraintSet.TOP)
                constraintSet.connect(R.id.primaryPreview, ConstraintSet.BOTTOM, ConstraintSet.PARENT_ID, ConstraintSet.BOTTOM)
                constraintSet.connect(R.id.primaryPreview, ConstraintSet.START, ConstraintSet.PARENT_ID, ConstraintSet.START)
                constraintSet.connect(R.id.primaryPreview, ConstraintSet.END, ConstraintSet.PARENT_ID, ConstraintSet.END)

                binding.secondarySplitContainer.visibility = View.GONE
                if (mode.isPiP) {
                    binding.pipContainer.visibility = View.VISIBLE
                } else {
                    binding.pipContainer.visibility = View.GONE
                }
            }
        }

        constraintSet.applyTo(binding.rootContainer)
    }

    private fun updateLayoutVisibility(mode: LayoutMode) {
        applyLayoutModeConstraints(mode)

        if (dualCameraController.isHardwareDualStreamingActive) {
            // Live concurrent hardware streaming -> hide all static snapshot overlays
            binding.ivPipSnapshot.visibility = View.GONE
            binding.ivSplitSnapshot.visibility = View.GONE
        } else {
            // Fallback Duo mode
            if (mode.isPiP) {
                binding.ivPipSnapshot.visibility = View.VISIBLE
            } else if (mode.isSplit) {
                binding.ivSplitSnapshot.visibility = View.VISIBLE
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
        singleCameraController.bindCamera(singleCameraController.currentLensFacing)
        Toast.makeText(this, "Resolution: ${next.label}", Toast.LENGTH_SHORT).show()
    }

    private fun cycleFps() {
        val supported = cameraCapabilities.supportedFps.sorted()
        val currentIdx = supported.indexOf(currentCameraState.fps)
        val next = supported[(currentIdx + 1) % supported.size]
        currentCameraState.fps = next
        binding.btnFps.text = "$next FPS"
        Toast.makeText(this, "Framerate: $next FPS", Toast.LENGTH_SHORT).show()
    }

    private fun cycleGridStyle() {
        val next = when (binding.gridOverlay.gridStyle) {
            GridStyle.OFF -> GridStyle.RULE_OF_THIRDS
            GridStyle.RULE_OF_THIRDS -> GridStyle.GOLDEN_RATIO
            GridStyle.GOLDEN_RATIO -> GridStyle.CROSSHAIR
            GridStyle.CROSSHAIR -> GridStyle.SQUARE
            GridStyle.SQUARE -> GridStyle.OFF
        }
        binding.gridOverlay.gridStyle = next
        val name = when (next) {
            GridStyle.OFF -> "Grid: OFF"
            GridStyle.RULE_OF_THIRDS -> "Grid: 3x3 (Rule of Thirds)"
            GridStyle.GOLDEN_RATIO -> "Grid: Golden Ratio"
            GridStyle.CROSSHAIR -> "Grid: Crosshair Center"
            GridStyle.SQUARE -> "Grid: 1:1 Square Framing"
        }
        Toast.makeText(this, name, Toast.LENGTH_SHORT).show()
    }

    private fun cycleTimer() {
        val next = when (currentCameraState.timerSeconds) {
            0 -> 3
            3 -> 5
            5 -> 10
            else -> 0
        }
        currentCameraState.timerSeconds = next
        val label = if (next == 0) "Timer: OFF" else "Timer: ${next}s delay"
        Toast.makeText(this, label, Toast.LENGTH_SHORT).show()
    }

    private fun applyZoom(ratio: Float, selectedBtn: View) {
        currentZoomRatio = ratio
        singleCameraController.setZoom(ratio)
        val buttons = listOf(binding.btnZoomWide, binding.btnZoom1x, binding.btnZoom2x, binding.btnZoom5x)
        for (b in buttons) {
            b.setBackgroundResource(android.R.color.transparent)
            (b as? TextView)?.setTextColor(Color.parseColor("#A0A6B5"))
        }
        selectedBtn.setBackgroundResource(R.drawable.bg_glass_pill_active)
        (selectedBtn as? TextView)?.setTextColor(Color.BLACK)
    }

    override fun onDestroy() {
        super.onDestroy()
        audioLevelMonitor?.stop()
        toneGen?.release()
    }
}
