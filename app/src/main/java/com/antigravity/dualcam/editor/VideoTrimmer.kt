package com.antigravity.dualcam.editor

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.ByteBuffer

/**
 * Lossless video trimmer using Android MediaExtractor and MediaMuxer.
 * Precision cuts between startMs and endMs without re-encoding quality degradation.
 */
object VideoTrimmer {
    private const val TAG = "VideoTrimmer"

    suspend fun trimVideo(
        context: Context,
        sourceUri: Uri,
        sourceFile: File?,
        outputFile: File,
        startMs: Long,
        endMs: Long,
        onProgress: (Int) -> Unit
    ): Boolean = withContext(Dispatchers.IO) {
        val startUs = startMs * 1000L
        val endUs = endMs * 1000L

        var extractor: MediaExtractor? = null
        var muxer: MediaMuxer? = null

        try {
            extractor = MediaExtractor()
            if (sourceFile != null && sourceFile.exists()) {
                extractor.setDataSource(sourceFile.absolutePath)
            } else {
                extractor.setDataSource(context, sourceUri, null)
            }

            muxer = MediaMuxer(outputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)

            var videoTrackIndex = -1
            var audioTrackIndex = -1
            var muxerVideoTrack = -1
            var muxerAudioTrack = -1

            for (i in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(i)
                val mime = format.getString(MediaFormat.KEY_MIME) ?: ""
                if (mime.startsWith("video/") && videoTrackIndex == -1) {
                    videoTrackIndex = i
                    muxerVideoTrack = muxer.addTrack(format)
                } else if (mime.startsWith("audio/") && audioTrackIndex == -1) {
                    audioTrackIndex = i
                    muxerAudioTrack = muxer.addTrack(format)
                }
            }

            if (muxerVideoTrack == -1) {
                Log.e(TAG, "No video track found to trim")
                return@withContext false
            }

            muxer.start()

            val buffer = ByteBuffer.allocate(2 * 1024 * 1024)
            val bufferInfo = MediaCodec.BufferInfo()

            // 1. Process Video Track
            extractor.selectTrack(videoTrackIndex)
            extractor.seekTo(startUs, MediaExtractor.SEEK_TO_CLOSEST_SYNC)

            var firstVideoPtsUs = -1L
            val totalTrimDurationUs = (endUs - startUs).coerceAtLeast(1L)

            while (true) {
                val sampleSize = extractor.readSampleData(buffer, 0)
                if (sampleSize < 0) break

                val sampleTime = extractor.sampleTime
                if (sampleTime > endUs) break

                if (sampleTime >= startUs || firstVideoPtsUs == -1L) {
                    if (firstVideoPtsUs == -1L) {
                        firstVideoPtsUs = sampleTime
                    }

                    bufferInfo.offset = 0
                    bufferInfo.size = sampleSize
                    bufferInfo.presentationTimeUs = sampleTime - firstVideoPtsUs
                    bufferInfo.flags = extractor.sampleFlags

                    muxer.writeSampleData(muxerVideoTrack, buffer, bufferInfo)

                    val progress = (((sampleTime - startUs).toFloat() / totalTrimDurationUs) * 50).toInt().coerceIn(0, 50)
                    onProgress(progress)
                }
                extractor.advance()
            }
            extractor.unselectTrack(videoTrackIndex)

            // 2. Process Audio Track (if present)
            if (audioTrackIndex != -1 && muxerAudioTrack != -1) {
                extractor.selectTrack(audioTrackIndex)
                extractor.seekTo(startUs, MediaExtractor.SEEK_TO_CLOSEST_SYNC)

                var firstAudioPtsUs = -1L
                while (true) {
                    val sampleSize = extractor.readSampleData(buffer, 0)
                    if (sampleSize < 0) break

                    val sampleTime = extractor.sampleTime
                    if (sampleTime > endUs) break

                    if (sampleTime >= startUs || firstAudioPtsUs == -1L) {
                        if (firstAudioPtsUs == -1L) {
                            firstAudioPtsUs = sampleTime
                        }

                        bufferInfo.offset = 0
                        bufferInfo.size = sampleSize
                        bufferInfo.presentationTimeUs = sampleTime - firstAudioPtsUs
                        bufferInfo.flags = extractor.sampleFlags

                        muxer.writeSampleData(muxerAudioTrack, buffer, bufferInfo)

                        val progress = 50 + (((sampleTime - startUs).toFloat() / totalTrimDurationUs) * 50).toInt().coerceIn(0, 50)
                        onProgress(progress)
                    }
                    extractor.advance()
                }
                extractor.unselectTrack(audioTrackIndex)
            }

            muxer.stop()
            onProgress(100)
            Log.i(TAG, "Trim completed: ${outputFile.absolutePath}")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Video trimming failed: ${e.message}", e)
            false
        } finally {
            try { extractor?.release() } catch (_: Exception) {}
            try { muxer?.release() } catch (_: Exception) {}
        }
    }
}
