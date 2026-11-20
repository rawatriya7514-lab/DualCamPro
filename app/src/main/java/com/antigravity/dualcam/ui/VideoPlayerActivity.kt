package com.antigravity.dualcam.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import com.antigravity.dualcam.databinding.ActivityPlayerBinding
import java.io.File

class VideoPlayerActivity : AppCompatActivity() {
    private lateinit var binding: ActivityPlayerBinding
    private var player: ExoPlayer? = null
    private var videoUri: Uri? = null
    private var videoFilePath: String? = null
    private var videoTitle: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPlayerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val uriStr = intent.getStringExtra(EXTRA_VIDEO_URI)
        videoFilePath = intent.getStringExtra(EXTRA_VIDEO_PATH)
        videoTitle = intent.getStringExtra(EXTRA_VIDEO_TITLE) ?: "DualCam Recording"

        binding.tvPlayerTitle.text = videoTitle

        if (uriStr != null) {
            videoUri = Uri.parse(uriStr)
            initializePlayer(videoUri!!)
        } else if (videoFilePath != null) {
            videoUri = Uri.fromFile(File(videoFilePath!!))
            initializePlayer(videoUri!!)
        } else {
            Toast.makeText(this, "Video not found", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        binding.btnBack.setOnClickListener { finish() }

        binding.btnShareVideo.setOnClickListener {
            videoUri?.let { uri ->
                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "video/mp4"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                startActivity(Intent.createChooser(shareIntent, "Share DualCam Video"))
            }
        }

        binding.btnEditVideo.setOnClickListener {
            val intent = Intent(this, VideoEditorActivity::class.java).apply {
                putExtra(VideoEditorActivity.EXTRA_VIDEO_URI, videoUri.toString())
                putExtra(VideoEditorActivity.EXTRA_VIDEO_PATH, videoFilePath)
                putExtra(VideoEditorActivity.EXTRA_VIDEO_TITLE, videoTitle)
            }
            startActivity(intent)
        }
    }

    private fun initializePlayer(uri: Uri) {
        player = ExoPlayer.Builder(this).build().also { exo ->
            binding.playerView.player = exo
            val mediaItem = MediaItem.fromUri(uri)
            exo.setMediaItem(mediaItem)
            exo.prepare()
            exo.playWhenReady = true
        }
    }

    override fun onStop() {
        super.onStop()
        player?.pause()
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
