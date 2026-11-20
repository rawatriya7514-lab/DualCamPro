package com.antigravity.dualcam.model

import android.net.Uri
import java.io.File

/**
 * Model representing a recorded video in the device MediaStore / DualCam directory.
 */
data class VideoItem(
    val id: Long,
    val uri: Uri,
    val file: File,
    val name: String,
    val durationMs: Long,
    val width: Int,
    val height: Int,
    val sizeBytes: Long,
    val dateAddedMs: Long
) {
    val durationFormatted: String
        get() {
            val totalSeconds = durationMs / 1000
            val minutes = totalSeconds / 60
            val seconds = totalSeconds % 60
            val hours = minutes / 60
            return if (hours > 0) {
                String.format("%02d:%02d:%02d", hours, minutes % 60, seconds)
            } else {
                String.format("%02d:%02d", minutes, seconds)
            }
        }

    val sizeFormatted: String
        get() {
            val mb = sizeBytes.toDouble() / (1024 * 1024)
            return if (mb >= 1024) {
                String.format("%.1f GB", mb / 1024)
            } else {
                String.format("%.1f MB", mb)
            }
        }

    val resolutionFormatted: String
        get() = "${width}x${height}"
}
