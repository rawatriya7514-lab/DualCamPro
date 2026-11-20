package com.antigravity.dualcam.recorder

import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.log10
import kotlin.math.sqrt

/**
 * Monitors real-time audio input level in decibels (dBFS) for the OSD visualizer.
 */
class AudioLevelMonitor(
    private val onLevelUpdate: (db: Float, normalized: Float) -> Unit
) {
    private var audioRecord: AudioRecord? = null
    private var monitorJob: Job? = null
    private var isMonitoring = false

    fun start() {
        if (isMonitoring) return
        isMonitoring = true

        monitorJob = CoroutineScope(Dispatchers.Default).launch {
            val sampleRate = 44100
            val channelConfig = AudioFormat.CHANNEL_IN_MONO
            val audioFormat = AudioFormat.ENCODING_PCM_16BIT
            val minBufSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat)
            val bufferSize = minBufSize.coerceAtLeast(sampleRate / 10) // 100ms buffer

            try {
                audioRecord = AudioRecord(
                    MediaRecorder.AudioSource.MIC,
                    sampleRate,
                    channelConfig,
                    audioFormat,
                    bufferSize
                )

                if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                    Log.w(TAG, "AudioRecord failed to initialize")
                    return@launch
                }

                audioRecord?.startRecording()
                val buffer = ShortArray(bufferSize / 2)

                while (isActive && isMonitoring) {
                    val read = audioRecord?.read(buffer, 0, buffer.size) ?: 0
                    if (read > 0) {
                        var sum = 0.0
                        for (i in 0 until read) {
                            sum += buffer[i] * buffer[i]
                        }
                        val rms = sqrt(sum / read)
                        // Calculate dBFS (0 dB is maximum 32767 amplitude, floor at -60 dB)
                        val db = if (rms > 0) {
                            (20 * log10(rms / 32767.0)).toFloat().coerceIn(-60f, 0f)
                        } else {
                            -60f
                        }
                        val normalized = ((db + 60f) / 60f).coerceIn(0f, 1f)
                        onLevelUpdate(db, normalized)
                    }
                    Thread.sleep(50) // ~20fps updates
                }
            } catch (e: SecurityException) {
                Log.w(TAG, "Missing RECORD_AUDIO permission for audio level monitor")
            } catch (e: Exception) {
                Log.e(TAG, "Error in AudioLevelMonitor: ${e.message}")
            } finally {
                stop()
            }
        }
    }

    fun stop() {
        isMonitoring = false
        monitorJob?.cancel()
        monitorJob = null
        try {
            audioRecord?.stop()
            audioRecord?.release()
        } catch (e: Exception) {
            // Ignore on release
        }
        audioRecord = null
    }

    companion object {
        private const val TAG = "AudioLevelMonitor"
    }
}
