package com.antigravity.dualcam.recorder

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.ByteBuffer

/**
 * High-speed lossless MP4 segment stitcher.
 * Merges video segments created during camera flip while recording into a single continuous file.
 */
object SegmentedRecordingMerger {
    private const val TAG = "SegmentedMerger"

    suspend fun mergeSegments(
        segmentFiles: List<File>,
        outputFile: File
    ): Boolean = withContext(Dispatchers.IO) {
        if (segmentFiles.isEmpty()) return@withContext false
        if (segmentFiles.size == 1) {
            // Fast rename if only single segment
            return@withContext segmentFiles[0].renameTo(outputFile)
        }

        var muxer: MediaMuxer? = null
        try {
            muxer = MediaMuxer(outputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)

            // 1. Identify primary video & audio tracks from first segment
            val firstExtractor = MediaExtractor().apply { setDataSource(segmentFiles[0].absolutePath) }
            var videoTrackIndexInSource = -1
            var audioTrackIndexInSource = -1
            var videoFormat: MediaFormat? = null
            var audioFormat: MediaFormat? = null

            for (i in 0 until firstExtractor.trackCount) {
                val format = firstExtractor.getTrackFormat(i)
                val mime = format.getString(MediaFormat.KEY_MIME) ?: ""
                if (mime.startsWith("video/") && videoTrackIndexInSource == -1) {
                    videoTrackIndexInSource = i
                    videoFormat = format
                } else if (mime.startsWith("audio/") && audioTrackIndexInSource == -1) {
                    audioTrackIndexInSource = i
                    audioFormat = format
                }
            }
            firstExtractor.release()

            if (videoFormat == null) {
                Log.e(TAG, "No video track found in first segment")
                return@withContext false
            }

            val muxerVideoTrack = muxer.addTrack(videoFormat)
            val muxerAudioTrack = if (audioFormat != null) muxer.addTrack(audioFormat) else -1

            muxer.start()

            val buffer = ByteBuffer.allocate(2 * 1024 * 1024) // 2MB buffer
            val bufferInfo = MediaCodec.BufferInfo()

            var videoPtsOffsetUs = 0L
            var audioPtsOffsetUs = 0L

            for (segment in segmentFiles) {
                val extractor = MediaExtractor().apply { setDataSource(segment.absolutePath) }
                var segVideoIndex = -1
                var segAudioIndex = -1

                for (i in 0 until extractor.trackCount) {
                    val format = extractor.getTrackFormat(i)
                    val mime = format.getString(MediaFormat.KEY_MIME) ?: ""
                    if (mime.startsWith("video/") && segVideoIndex == -1) segVideoIndex = i
                    if (mime.startsWith("audio/") && segAudioIndex == -1) segAudioIndex = i
                }

                // Append Video Track
                var lastVideoPtsInSegment = 0L
                if (segVideoIndex != -1) {
                    extractor.selectTrack(segVideoIndex)
                    while (true) {
                        val sampleSize = extractor.readSampleData(buffer, 0)
                        if (sampleSize < 0) break

                        val samplePts = extractor.sampleTime
                        val sampleFlags = extractor.sampleFlags
                        lastVideoPtsInSegment = maxOf(lastVideoPtsInSegment, samplePts)

                        bufferInfo.offset = 0
                        bufferInfo.size = sampleSize
                        bufferInfo.presentationTimeUs = samplePts + videoPtsOffsetUs
                        bufferInfo.flags = sampleFlags

                        muxer.writeSampleData(muxerVideoTrack, buffer, bufferInfo)
                        extractor.advance()
                    }
                    extractor.unselectTrack(segVideoIndex)
                }

                // Append Audio Track
                var lastAudioPtsInSegment = 0L
                if (segAudioIndex != -1 && muxerAudioTrack != -1) {
                    extractor.selectTrack(segAudioIndex)
                    while (true) {
                        val sampleSize = extractor.readSampleData(buffer, 0)
                        if (sampleSize < 0) break

                        val samplePts = extractor.sampleTime
                        val sampleFlags = extractor.sampleFlags
                        lastAudioPtsInSegment = maxOf(lastAudioPtsInSegment, samplePts)

                        bufferInfo.offset = 0
                        bufferInfo.size = sampleSize
                        bufferInfo.presentationTimeUs = samplePts + audioPtsOffsetUs
                        bufferInfo.flags = sampleFlags

                        muxer.writeSampleData(muxerAudioTrack, buffer, bufferInfo)
                        extractor.advance()
                    }
                    extractor.unselectTrack(segAudioIndex)
                }

                extractor.release()

                // Increment offsets for next segment
                videoPtsOffsetUs += lastVideoPtsInSegment + 33_333L // ~1 frame buffer (30fps)
                audioPtsOffsetUs += lastAudioPtsInSegment + 20_000L
            }

            muxer.stop()
            muxer.release()

            // Delete temporary segments on successful merge
            segmentFiles.forEach { it.delete() }
            Log.i(TAG, "Successfully merged ${segmentFiles.size} segments into ${outputFile.name}")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to merge video segments: ${e.message}", e)
            try { muxer?.release() } catch (_: Exception) {}
            false
        }
    }
}
