package com.antigravity.dualcam.recorder

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import android.util.Size
import com.antigravity.dualcam.model.VideoItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Scoped Storage helper for saving recordings to Movies/DualCam and querying media gallery.
 */
object MediaStoreHelper {
    private const val TAG = "MediaStoreHelper"
    private const val RELATIVE_SUBFOLDER = "Movies/DualCam"

    fun getAvailableStorageBytes(context: Context): Long {
        val path = context.getExternalFilesDir(Environment.DIRECTORY_MOVIES) ?: context.filesDir
        return path.freeSpace
    }

    fun createOutputVideoFile(context: Context): File {
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val fileName = "DUAL_${timeStamp}.mp4"
        val storageDir = context.getExternalFilesDir(Environment.DIRECTORY_MOVIES) ?: context.filesDir
        val dualCamDir = File(storageDir, "DualCam").apply { if (!exists()) mkdirs() }
        return File(dualCamDir, fileName)
    }

    suspend fun saveVideoToMediaStore(context: Context, sourceFile: File): Uri? = withContext(Dispatchers.IO) {
        if (!sourceFile.exists() || sourceFile.length() == 0L) {
            Log.e(TAG, "Source video file is missing or empty")
            return@withContext null
        }

        val values = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, sourceFile.name)
            put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
            put(MediaStore.Video.Media.DATE_ADDED, System.currentTimeMillis() / 1000)
            put(MediaStore.Video.Media.DATE_MODIFIED, System.currentTimeMillis() / 1000)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Video.Media.RELATIVE_PATH, RELATIVE_SUBFOLDER)
                put(MediaStore.Video.Media.IS_PENDING, 1)
            }
        }

        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        } else {
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        }

        val resolver = context.contentResolver
        val itemUri = resolver.insert(collection, values) ?: return@withContext null

        try {
            resolver.openOutputStream(itemUri)?.use { out ->
                FileInputStream(sourceFile).use { input ->
                    input.copyTo(out)
                }
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                values.clear()
                values.put(MediaStore.Video.Media.IS_PENDING, 0)
                resolver.update(itemUri, values, null, null)
            }

            Log.i(TAG, "Saved video to MediaStore: $itemUri (${sourceFile.name})")
            itemUri
        } catch (e: Exception) {
            Log.e(TAG, "Error writing video to MediaStore: ${e.message}", e)
            resolver.delete(itemUri, null, null)
            null
        }
    }

    suspend fun queryRecordedVideos(context: Context): List<VideoItem> = withContext(Dispatchers.IO) {
        val list = mutableListOf<VideoItem>()
        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        }

        val projection = arrayOf(
            MediaStore.Video.Media._ID,
            MediaStore.Video.Media.DISPLAY_NAME,
            MediaStore.Video.Media.DURATION,
            MediaStore.Video.Media.SIZE,
            MediaStore.Video.Media.WIDTH,
            MediaStore.Video.Media.HEIGHT,
            MediaStore.Video.Media.DATE_ADDED,
            MediaStore.Video.Media.DATA
        )

        // Filter for files in DualCam or starting with DUAL_
        val selection = "${MediaStore.Video.Media.DISPLAY_NAME} LIKE 'DUAL_%' OR ${MediaStore.Video.Media.DATA} LIKE '%DualCam%'"
        val sortOrder = "${MediaStore.Video.Media.DATE_ADDED} DESC"

        try {
            context.contentResolver.query(collection, projection, selection, null, sortOrder)?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
                val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)
                val durationCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION)
                val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.SIZE)
                val widthCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.WIDTH)
                val heightCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.HEIGHT)
                val dateCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATE_ADDED)
                val dataCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATA)

                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idCol)
                    val name = cursor.getString(nameCol) ?: "Video_$id.mp4"
                    val duration = cursor.getLong(durationCol)
                    val size = cursor.getLong(sizeCol)
                    val width = cursor.getInt(widthCol)
                    val height = cursor.getInt(heightCol)
                    val dateAdded = cursor.getLong(dateCol) * 1000
                    val path = cursor.getString(dataCol) ?: ""
                    val uri = ContentUris.withAppendedId(collection, id)

                    list.add(
                        VideoItem(
                            id = id,
                            uri = uri,
                            file = File(path),
                            name = name,
                            durationMs = duration,
                            width = if (width > 0) width else 1920,
                            height = if (height > 0) height else 1080,
                            sizeBytes = size,
                            dateAddedMs = dateAdded
                        )
                    )
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error querying MediaStore videos: ${e.message}")
        }

        // Also check app private DualCam directory for any un-indexed videos
        val privateDir = File(context.getExternalFilesDir(Environment.DIRECTORY_MOVIES), "DualCam")
        if (privateDir.exists()) {
            privateDir.listFiles { f -> f.extension == "mp4" }?.forEach { f ->
                if (list.none { it.name == f.name }) {
                    list.add(
                        VideoItem(
                            id = f.hashCode().toLong(),
                            uri = Uri.fromFile(f),
                            file = f,
                            name = f.name,
                            durationMs = extractDuration(f),
                            width = 1920,
                            height = 1080,
                            sizeBytes = f.length(),
                            dateAddedMs = f.lastModified()
                        )
                    )
                }
            }
        }

        list.sortedByDescending { it.dateAddedMs }
    }

    fun extractThumbnail(context: Context, video: VideoItem): Bitmap? {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && video.uri.scheme == "content") {
                context.contentResolver.loadThumbnail(video.uri, Size(320, 240), null)
            } else {
                val retriever = MediaMetadataRetriever()
                if (video.file.exists()) {
                    retriever.setDataSource(video.file.absolutePath)
                } else {
                    retriever.setDataSource(context, video.uri)
                }
                val bitmap = retriever.getFrameAtTime(1_000_000, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                retriever.release()
                bitmap
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun extractDuration(file: File): Long {
        return try {
            val retriever = MediaMetadataRetriever()
            retriever.setDataSource(file.absolutePath)
            val dur = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
            retriever.release()
            dur
        } catch (e: Exception) {
            0L
        }
    }
}
