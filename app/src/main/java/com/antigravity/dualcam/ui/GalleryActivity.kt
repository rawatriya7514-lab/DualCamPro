package com.antigravity.dualcam.ui

import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.antigravity.dualcam.databinding.ActivityGalleryBinding
import com.antigravity.dualcam.model.VideoItem
import com.antigravity.dualcam.recorder.MediaStoreHelper
import kotlinx.coroutines.launch

class GalleryActivity : AppCompatActivity() {
    private lateinit var binding: ActivityGalleryBinding
    private val videoList = mutableListOf<VideoItem>()
    private lateinit var adapter: GalleryAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityGalleryBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupRecyclerView()

        binding.btnBack.setOnClickListener { finish() }
        binding.swipeRefresh.setOnRefreshListener { loadVideos() }

        loadVideos()
    }

    private fun setupRecyclerView() {
        adapter = GalleryAdapter(
            context = this,
            videos = videoList,
            onVideoClick = { video ->
                val intent = Intent(this, VideoPlayerActivity::class.java).apply {
                    putExtra(VideoPlayerActivity.EXTRA_VIDEO_URI, video.uri.toString())
                    putExtra(VideoPlayerActivity.EXTRA_VIDEO_PATH, video.file.absolutePath)
                    putExtra(VideoPlayerActivity.EXTRA_VIDEO_TITLE, video.name)
                }
                startActivity(intent)
            },
            onShareClick = { video ->
                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "video/mp4"
                    putExtra(Intent.EXTRA_STREAM, video.uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                startActivity(Intent.createChooser(shareIntent, "Share Video"))
            },
            onTrimClick = { video ->
                val intent = Intent(this, VideoEditorActivity::class.java).apply {
                    putExtra(VideoEditorActivity.EXTRA_VIDEO_URI, video.uri.toString())
                    putExtra(VideoEditorActivity.EXTRA_VIDEO_PATH, video.file.absolutePath)
                    putExtra(VideoEditorActivity.EXTRA_VIDEO_TITLE, video.name)
                }
                startActivity(intent)
            },
            onDeleteClick = { video, position ->
                confirmDelete(video, position)
            }
        )

        binding.rvVideos.layoutManager = LinearLayoutManager(this)
        binding.rvVideos.adapter = adapter
    }

    private fun loadVideos() {
        binding.swipeRefresh.isRefreshing = true
        lifecycleScope.launch {
            val videos = MediaStoreHelper.queryRecordedVideos(this@GalleryActivity)
            videoList.clear()
            videoList.addAll(videos)
            adapter.notifyDataSetChanged()
            binding.swipeRefresh.isRefreshing = false

            if (videoList.isEmpty()) {
                binding.emptyView.visibility = View.VISIBLE
            } else {
                binding.emptyView.visibility = View.GONE
            }
        }
    }

    private fun confirmDelete(video: VideoItem, position: Int) {
        AlertDialog.Builder(this)
            .setTitle("Delete Video")
            .setMessage("Are you sure you want to delete ${video.name}?")
            .setPositiveButton("Delete") { _, _ ->
                try {
                    if (video.file.exists()) video.file.delete()
                    contentResolver.delete(video.uri, null, null)
                    adapter.removeAt(position)
                    Toast.makeText(this, "Video deleted", Toast.LENGTH_SHORT).show()
                    if (videoList.isEmpty()) binding.emptyView.visibility = View.VISIBLE
                } catch (e: Exception) {
                    Toast.makeText(this, "Delete error: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
}
