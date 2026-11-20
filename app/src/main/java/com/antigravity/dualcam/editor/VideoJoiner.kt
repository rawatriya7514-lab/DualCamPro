package com.antigravity.dualcam.editor

import android.content.Context
import com.antigravity.dualcam.model.VideoItem
import com.antigravity.dualcam.recorder.SegmentedRecordingMerger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Combines multiple video items from the gallery into a single merged movie.
 */
object VideoJoiner {
    suspend fun joinVideos(
        context: Context,
        videos: List<VideoItem>,
        outputFile: File
    ): Boolean = withContext(Dispatchers.IO) {
        val files = videos.map { it.file }.filter { it.exists() }
        if (files.isEmpty()) return@withContext false
        SegmentedRecordingMerger.mergeSegments(files, outputFile)
    }
}
