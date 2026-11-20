package com.antigravity.dualcam.ui

import android.app.ProgressDialog
import android.content.Intent
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Bundle
import android.widget.SeekBar
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.antigravity.dualcam.R
import com.antigravity.dualcam.databinding.ActivityEditorBinding
import com.antigravity.dualcam.editor.VideoTrimmer
import com.antigravity.dualcam.recorder.MediaStoreHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class VideoEditorActivity : AppCompatActivity() {
    private lateinit var binding: ActivityEditorBinding
    private var player: ExoPlayer? = null
    private var videoUri: Uri? = null
    private var videoFile: File? = null

    private var totalDurationMs: Long = 0L
    private var trimStartMs: Long = 0L
    private var trimEndMs: Long = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityEditorBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val uriStr = intent.getStringExtra(EXTRA_VIDEO_URI)
        val pathStr = intent.getStringExtra(EXTRA_VIDEO_PATH)

        if (uriStr != null) videoUri = Uri.parse(uriStr)
        if (pathStr != null) videoFile = File(pathStr)

        if (videoUri == null && videoFile != null) {
            videoUri = Uri.fromFile(videoFile)
        }

        if (videoUri == null) {
            Toast.makeText(this, "Cannot open video for editing", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        initVideoMetadata()
        initPlayer()
        setupSeekBars()

        binding.btnBack.setOnClickListener { finish() }

        binding.btnTogglePlay.setOnClickListener {
            player?.let { p ->
                if (p.isPlaying) {
                    p.pause()
                    binding.btnTogglePlay.setImageResource(R.drawable.ic_play)
                } else {
                    p.seekTo(trimStartMs)
                    p.play()
                    binding.btnTogglePlay.setImageResource(R.drawable.ic_pause)
                }
            }
        }

        binding.btnExport.setOnClickListener {
            exportTrimmedVideo()
        }
    }

    private fun initVideoMetadata() {
        try {
            val retriever = MediaMetadataRetriever()
            if (videoFile != null && videoFile!!.exists()) {
                retriever.setDataSource(videoFile!!.absolutePath)
            } else {
                retriever.setDataSource(this, videoUri!!)
            }
            val dur = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 10000L
            totalDurationMs = dur
            trimStartMs = 0L
            trimEndMs = dur
            retriever.release()
        } catch (e: Exception) {
            totalDurationMs = 15000L
            trimEndMs = 15000L
        }
        updateTimeDisplays()
    }

    private fun initPlayer() {
        player = ExoPlayer.Builder(this).build().also { exo ->
            binding.editorPlayer.player = exo
            val mediaItem = MediaItem.fromUri(videoUri!!)
            exo.setMediaItem(mediaItem)
            exo.prepare()
            exo.addListener(object : Player.Listener {
                override fun onPlaybackStateChanged(state: Int) {
                    if (state == Player.STATE_ENDED) {
                        binding.btnTogglePlay.setImageResource(R.drawable.ic_play)
                    }
                }
            })
        }
    }

    private fun setupSeekBars() {
        binding.seekStart.max = 1000
        binding.seekStart.progress = 0

        binding.seekEnd.max = 1000
        binding.seekEnd.progress = 1000

        binding.seekStart.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    val calcStart = (progress / 1000.0 * totalDurationMs).toLong()
                    if (calcStart >= trimEndMs) {
                        trimStartMs = (trimEndMs - 500L).coerceAtLeast(0L)
                        binding.seekStart.progress = ((trimStartMs.toDouble() / totalDurationMs) * 1000).toInt()
                    } else {
                        trimStartMs = calcStart
                    }
                    player?.seekTo(trimStartMs)
                    updateTimeDisplays()
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        binding.seekEnd.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    val calcEnd = (progress / 1000.0 * totalDurationMs).toLong()
                    if (calcEnd <= trimStartMs) {
                        trimEndMs = (trimStartMs + 500L).coerceAtMost(totalDurationMs)
                        binding.seekEnd.progress = ((trimEndMs.toDouble() / totalDurationMs) * 1000).toInt()
                    } else {
                        trimEndMs = calcEnd
                    }
                    player?.seekTo(trimEndMs)
                    updateTimeDisplays()
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
    }

    private fun updateTimeDisplays() {
        binding.tvStartTime.text = "Start: ${formatMs(trimStartMs)}"
        binding.tvEndTime.text = "End: ${formatMs(trimEndMs)}"
        val duration = (trimEndMs - trimStartMs).coerceAtLeast(0L)
        binding.tvDuration.text = "Trim: ${formatMs(duration)}"
    }

    private fun formatMs(ms: Long): String {
        val totalSec = ms / 1000
        val min = totalSec / 60
        val sec = totalSec % 60
        return String.format("%02d:%02d", min, sec)
    }

    private fun exportTrimmedVideo() {
        val progressDialog = ProgressDialog(this).apply {
            setTitle("Exporting Trimmed Video")
            setMessage("Processing video without quality loss...")
            setProgressStyle(ProgressDialog.STYLE_HORIZONTAL)
            max = 100
            setCancelable(false)
            show()
        }

        lifecycleScope.launch {
            val outputFile = MediaStoreHelper.createOutputVideoFile(this@VideoEditorActivity)
            val success = VideoTrimmer.trimVideo(
                context = this@VideoEditorActivity,
                sourceUri = videoUri!!,
                sourceFile = videoFile,
                outputFile = outputFile,
                startMs = trimStartMs,
                endMs = trimEndMs,
                onProgress = { p ->
                    runOnUiThread { progressDialog.progress = p }
                }
            )

            progressDialog.dismiss()

            if (success) {
                MediaStoreHelper.saveVideoToMediaStore(this@VideoEditorActivity, outputFile)
                Toast.makeText(this@VideoEditorActivity, "Trimmed video saved successfully!", Toast.LENGTH_LONG).show()
                finish()
            } else {
                Toast.makeText(this@VideoEditorActivity, "Trim failed. Please try again.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        player?.release()
        player = null
    }

    companion object {
        const val EXTRA_VIDEO_URI = "extra_video_uri"
        const val EXTRA_VIDEO_PATH = "extra_video_path"
        const val EXTRA_VIDEO_TITLE = "extra_video_title"
    }
}
