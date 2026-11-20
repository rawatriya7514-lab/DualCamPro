# DualCam Pro — Professional Dual Camera Video Recording Android App

**DualCam Pro** (`com.antigravity.dualcam`) is a production-grade, native Android camera application engineered for mobile content creators, vloggers, interviewers, and filmmakers. Built with modern Android architecture using **CameraX**, **Camera2**, **MediaCodec**, **MediaMuxer**, **Media3 (ExoPlayer)**, and **Android MediaStore**, DualCam Pro brings multi-camera layouts, manual pro controls, seamless camera switching during active recording, 10 purpose-built recording templates, and an in-app lossless video editor into a sleek, dark AMOLED-optimized interface.

---

## 🌟 Key Features

### 1. Multi-Camera & Dual-Take Engine
- **One-Tap Camera Switching**: Instant flip between rear and front cameras with smooth transition.
- **Active Recording Switch Continuity**: Switch cameras while video recording is actively in progress without losing frames or corrupting files. Powered by an automated high-performance segmented recording and zero-loss `MediaMuxer` stitching pipeline.
- **Hardware-Aware Concurrent Dual Camera**: 
  - Probes device HAL for `CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_LOGICAL_MULTI_CAMERA` and `CameraManager.getConcurrentCameraIds()`.
  - True simultaneous front + rear camera hardware recording where supported.
  - Transparent hardware audit reporting: never fakes dual-camera mode; provides the best zero-latency seamless fallback when concurrent camera streaming is restricted by OEM hardware/firmware.
- **Picture-in-Picture (PiP) Layout**:
  - Live floating secondary camera preview over full-screen primary camera.
  - **Interactive Touch Gestures**: Drag anywhere on screen with boundary clamping, snap-to-corner physics, and one-tap camera view swapping (promote secondary to main).
- **Split-Screen Layouts**:
  - Side-by-Side vertical split (ideal for interviews, reaction videos, and TikTok/Reels).
  - Top/Bottom horizontal split.

### 2. Professional Pro Controls
- **Resolutions**: 720p (HD), 1080p (Full HD), 2K (QHD), and 4K (UHD) selection based on sensor capabilities.
- **Framerate Targets**: 24 FPS (Cinematic), 30 FPS (Standard), 60 FPS (Smooth), 120 FPS (High-Speed where supported).
- **Smooth Zoom System**: Dynamic zoom slider with quick buttons (`0.5x` ultrawide, `1x` wide, `2x`, `5x` telephoto).
- **Manual Exposure Compensation**: -3.0 EV to +3.0 EV fine-tune slider with live exposure lock.
- **Tap-to-Focus & AE/AF Lock**: Animated focus reticle with dual-metering and long-press lock.
- **White Balance**: Auto, Daylight, Cloudy, Fluorescent, Incandescent presets.
- **Stabilization**: Electronic Image Stabilization (EIS) and Optical Stabilization (OIS) toggle.
- **Rule Grids**: Rule of Thirds (3x3), Golden Ratio (Phi Grid), Crosshair Center, and 1:1 Square Framing.
- **Countdown Timer**: Instant, 3-second, 5-second, and 10-second hands-free recording delay.
- **Audio Studio Controls**:
  - Real-time stereo dBFS LED VU meter monitor.
  - Live microphone mute/unmute toggle.
  - 44.1kHz / 48kHz high-fidelity AAC audio capture.

### 3. 10 Built-In Recording Templates
1. **Vlogging Mode**: 1080p 30fps, 3x3 grid, auto-stabilized, microphone on with quick front/rear toggle.
2. **YouTube Mode**: 16:9 Landscape lock, 4K/1080p 30fps, audio visualizer, rule of thirds grid.
3. **Instagram Reels Mode**: 9:16 Vertical, 1080p 60fps, 1:1 center framing guide, 60-second timer cap.
4. **Interview Mode**: 50/50 Split screen, front + rear dual capture, dual audio monitoring.
5. **Reaction Mode**: PiP overlay (creator face in corner, main subject full view), tap-to-swap.
6. **Travel Mode**: Ultrawide 0.5x preference, horizon level guide, maximum video stabilization.
7. **Podcast Mode**: High-bitrate audio monitoring, static exposure lock, subdued preview for battery savings.
8. **Tutorial Mode**: Top-right compact PiP, golden ratio grid, high contrast exposure.
9. **Cinematic Mode**: 24 FPS locked shutter, 4K UHD, 2.39:1 framing guides, manual white balance.
10. **Dual Camera Mode**: Full hardware concurrent dual-stream capture or seamless multi-cam fallback.

### 4. Integrated Gallery & Lossless Video Editor
- **Custom In-App Gallery**: Grid of recorded videos with durations, timestamps, and resolutions.
- **ExoPlayer Video Player**: Smooth hardware-accelerated playback with play/pause, scrub bar, timecode, and system share intent.
- **Lossless Video Trimmer**: Frame-accurate start/end trimming using `MediaExtractor` and `MediaMuxer` without re-encoding quality loss.
- **Clip Merger**: Sequentially stitch multiple takes into a single MP4 file.
- **Scoped Storage Compliant**: Automatically registers finished videos in Android `MediaStore.Video.Media` under `Movies/DualCam/` with full metadata.

---

## 🏗️ Architecture & Technical Stack

```
DualCamPro/
├── app/
│   ├── src/main/
│   │   ├── AndroidManifest.xml          # Permissions, orientations, activities
│   │   ├── java/com/antigravity/dualcam/
│   │   │   ├── DualCamApp.kt            # Application singleton, notification channels
│   │   │   ├── camera/
│   │   │   │   ├── CameraCapabilities.kt     # Hardware specs data model
│   │   │   │   ├── CameraManagerHelper.kt    # Camera2 HAL inspection & concurrent detection
│   │   │   │   ├── SingleCameraController.kt  # CameraX Preview + VideoCapture controller
│   │   │   │   └── DualCameraController.kt    # Dual-stream orchestrator & fallback manager
│   │   │   ├── recorder/
│   │   │   │   ├── AudioLevelMonitor.kt      # Real-time AudioRecord RMS dBFS analyzer
│   │   │   │   ├── SegmentedRecordingMerger.kt# Lossless MediaExtractor/Muxer segment stitcher
│   │   │   │   └── MediaStoreHelper.kt       # Android 10+ Scoped Storage & gallery queries
│   │   │   ├── templates/
│   │   │   │   ├── RecordingTemplate.kt      # Template definitions & presets
│   │   │   │   └── TemplateManager.kt        # Template loader & switch logic
│   │   │   ├── editor/
│   │   │   │   ├── VideoTrimmer.kt           # Lossless start/end timestamp trimmer
│   │   │   │   └── VideoJoiner.kt            # Multi-segment MP4 concatenate engine
│   │   │   ├── model/
│   │   │   │   ├── LayoutMode.kt             # Single, PiP, SplitVertical, SplitHorizontal
│   │   │   │   ├── PiPConfig.kt              # Floating PiP coordinates & scale
│   │   │   │   ├── VideoItem.kt              # Video metadata record
│   │   │   │   └── RecordingState.kt         # Idle, Starting, Recording, Paused, Merging
│   │   │   └── ui/
│   │   │       ├── MainActivity.kt           # Main viewfinder & recording console
│   │   │       ├── GalleryActivity.kt        # Media gallery activity
│   │   │       ├── VideoPlayerActivity.kt    # ExoPlayer playback console
│   │   │       ├── VideoEditorActivity.kt    # Visual trim & merge editor
│   │   │       ├── PipTouchListener.kt       # Draggable PiP physics & corner snapping
│   │   │       ├── GridOverlayView.kt        # Custom Canvas composition rule overlays
│   │   │       ├── AudioVisualizerView.kt    # Custom Canvas LED VU meter
│   │   │       ├── FocusReticleView.kt       # Animated Camera focus & exposure indicator
│   │   │       ├── GalleryAdapter.kt         # RecyclerView gallery adapter
│   │   │       └── TemplatesAdapter.kt       # Bottom sheet template picker adapter
│   │   └── res/                              # AMOLED dark theme, icons, layouts, animations
```

---

## 📱 Hardware Capabilities & HAL Detection

DualCam Pro adheres strictly to honest camera hardware reporting:
- During startup, `CameraManagerHelper.inspectHardware(context)` enumerates all camera sensors.
- It detects whether `CameraManager.getConcurrentCameraIds()` returns valid pairs (e.g., Back Camera `0` + Front Camera `1`).
- When concurrent streaming is supported by the SoC HAL, both camera sensors stream simultaneously to hardware surfaces.
- When concurrent streaming is not exposed by OEM firmware (common on devices with shared ISP pipelines), DualCam Pro activates the **Dual-Take Segmented Engine**:
  1. Primary camera records segment A.
  2. One-tap switch flips to secondary camera in under 150ms and records segment B.
  3. Upon stopping, `SegmentedRecordingMerger` concatenates the H.264/AAC tracks losslessly using container remuxing, producing a seamless, continuous MP4 video with no sync drift.

---

## 🛠️ Build & Installation

### Option 1: Direct Build in Terminal (Termux / Linux)
```bash
cd /data/data/com.termux/files/home/DualCamPro
export JAVA_HOME=/data/data/com.termux/files/usr/lib/jvm/java-17-openjdk
./gradlew assembleDebug
```
The compiled APK will be at:
`app/build/outputs/apk/debug/app-debug.apk`

### Option 2: Install via ADB
```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

### Option 3: Open in Android Studio
1. Open Android Studio (Ladybug / Koala or later).
2. Choose **Open an Existing Project** and select `DualCamPro`.
3. Allow Gradle to sync dependencies.
4. Run on a connected physical Android device (Android 8.0 Oreo through Android 16).

---

## 🔒 Permissions & Privacy
DualCam Pro requires only the standard media permissions:
- `android.permission.CAMERA`: For viewfinder preview and video stream capture.
- `android.permission.RECORD_AUDIO`: For high-definition microphone recording.
- `android.permission.READ_MEDIA_VIDEO` / `READ_EXTERNAL_STORAGE`: For gallery access.
- `android.permission.VIBRATE`: For haptic feedback on record start/stop.

**Privacy Guarantee**: No data, telemetry, or recordings are ever sent to remote servers. All processing, trimming, and storage occurs 100% locally on the device.
