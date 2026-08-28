package com.example.service

import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.media.audiofx.AcousticEchoCanceler
import android.util.Log
import kotlinx.coroutines.*
import kotlin.math.sqrt

/**
 * Natural Barge-In / Interruption Detector.
 * Runs lightweight voice-activity monitoring during TTS playback to allow
 * natural conversational barge-in without audio feedback loops.
 */
class BargeInDetector(
    private val context: Context,
    private val onBargeInDetected: () -> Unit
) {
    private val tag = "KulsoomBargeIn"
    private var audioRecord: AudioRecord? = null
    private var echoCanceler: AcousticEchoCanceler? = null
    private var monitoringScope: CoroutineScope? = null
    private var isMonitoring = false

    fun startMonitoring() {
        if (isMonitoring) return

        val hasMic = androidx.core.content.ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.RECORD_AUDIO
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED

        if (!hasMic) return

        try {
            val sampleRate = 16000
            val channelConfig = AudioFormat.CHANNEL_IN_MONO
            val audioFormat = AudioFormat.ENCODING_PCM_16BIT
            val minBufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat)
            val bufferSize = (minBufferSize * 2).coerceAtLeast(2048)

            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.VOICE_COMMUNICATION,
                sampleRate,
                channelConfig,
                audioFormat,
                bufferSize
            )

            if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                Log.w(tag, "AudioRecord failed to initialize for barge-in")
                audioRecord?.release()
                audioRecord = null
                return
            }

            // Enable hardware Acoustic Echo Cancellation if supported on this chipset
            if (AcousticEchoCanceler.isAvailable()) {
                audioRecord?.audioSessionId?.let { sessionId ->
                    try {
                        echoCanceler = AcousticEchoCanceler.create(sessionId)?.apply {
                            enabled = true
                        }
                        Log.d(tag, "Hardware AcousticEchoCanceler enabled for barge-in")
                    } catch (e: Exception) {
                        Log.d(tag, "Could not attach AcousticEchoCanceler: ${e.message}")
                    }
                }
            }

            audioRecord?.startRecording()
            isMonitoring = true

            monitoringScope = CoroutineScope(Dispatchers.IO + Job()).apply {
                launch {
                    val buffer = ShortArray(512)
                    var consecutiveSpeechFrames = 0

                    while (isActive && isMonitoring) {
                        val read = audioRecord?.read(buffer, 0, buffer.size) ?: -1
                        if (read > 0) {
                            var sum = 0.0
                            for (i in 0 until read) {
                                sum += (buffer[i] * buffer[i]).toDouble()
                            }
                            val rms = sqrt(sum / read)

                            // Threshold tuned so speaker TTS playback doesn't trigger self-interruption,
                            // while direct human voice input quickly triggers barge-in
                            if (rms > 2800.0) {
                                consecutiveSpeechFrames++
                                if (consecutiveSpeechFrames >= 2) {
                                    Log.d(tag, "🗣️ Natural Barge-in detected (RMS: $rms). Interrupting TTS playback.")
                                    withContext(Dispatchers.Main) {
                                        stopMonitoring()
                                        onBargeInDetected()
                                    }
                                    break
                                }
                            } else {
                                if (consecutiveSpeechFrames > 0) consecutiveSpeechFrames--
                            }
                        }
                        delay(25)
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(tag, "Could not start barge-in detection: ${e.message}")
            stopMonitoring()
        }
    }

    fun stopMonitoring() {
        isMonitoring = false
        monitoringScope?.cancel()
        monitoringScope = null

        try {
            echoCanceler?.release()
        } catch (_: Exception) {}
        echoCanceler = null

        try {
            if (audioRecord?.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                audioRecord?.stop()
            }
            audioRecord?.release()
        } catch (_: Exception) {}
        audioRecord = null
    }
}
