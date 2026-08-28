package com.example.service

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class SpeechManager(private val context: Context) {

    private val tag = "KulsoomPerf"
    private var speechRecognizer: SpeechRecognizer? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    private val _isListening = MutableStateFlow(false)
    val isListening: StateFlow<Boolean> = _isListening.asStateFlow()

    private val _liveTranscript = MutableStateFlow("")
    val liveTranscript: StateFlow<String> = _liveTranscript.asStateFlow()

    private val _soundLevel = MutableStateFlow(0f)
    val soundLevel: StateFlow<Float> = _soundLevel.asStateFlow()

    // Captured RMS energy profile for voiceprint embedding verification
    private val _capturedRmsLevels = mutableListOf<Float>()
    val capturedRmsLevels: List<Float>
        get() = synchronized(_capturedRmsLevels) { _capturedRmsLevels.toList() }

    var onSpeechComplete: ((String) -> Unit)? = null
    var onSpeechError: ((String) -> Unit)? = null

    private var speechStartTime = 0L

    // Hard maximum listening watchdog timer (15s) to guarantee Kulsoom never stays stuck listening indefinitely
    private val watchdogRunnable = Runnable {
        Log.w(tag, "⚠️ SpeechManager hard watchdog timeout triggered (15s). Finalizing listening state.")
        val captured = _liveTranscript.value.trim()
        stopListening()
        if (captured.isNotBlank()) {
            onSpeechComplete?.invoke(captured)
        } else {
            onSpeechError?.invoke("Listening timed out. Please tap or say \"Kulsoom\" again.")
        }
    }

    fun isAvailable(): Boolean {
        return SpeechRecognizer.isRecognitionAvailable(context)
    }

    fun startListening(languageCode: String = "en-US") {
        if (!isAvailable()) {
            _isListening.value = false
            onSpeechError?.invoke("Speech recognition is not available on this device.")
            return
        }

        stopListening()
        synchronized(_capturedRmsLevels) {
            _capturedRmsLevels.clear()
        }

        try {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
                setRecognitionListener(object : RecognitionListener {
                    override fun onReadyForSpeech(params: Bundle?) {
                        _isListening.value = true
                        _liveTranscript.value = ""
                        _soundLevel.value = 0.1f
                        speechStartTime = System.currentTimeMillis()
                        Log.d(tag, "⏱️ Speech recognizer ready for input")
                        // Start 15s watchdog fallback timer
                        mainHandler.removeCallbacks(watchdogRunnable)
                        mainHandler.postDelayed(watchdogRunnable, 15000L)
                    }

                    override fun onBeginningOfSpeech() {
                        _isListening.value = true
                        Log.d(tag, "⏱️ User started speaking at +${System.currentTimeMillis() - speechStartTime}ms")
                    }

                    override fun onRmsChanged(rmsdB: Float) {
                        // Normalize dB (-2 to 10 typical) into 0.0 to 1.0
                        val normalized = ((rmsdB + 2f) / 12f).coerceIn(0f, 1f)
                        _soundLevel.value = normalized
                        synchronized(_capturedRmsLevels) {
                            if (_capturedRmsLevels.size < 60) {
                                _capturedRmsLevels.add(normalized)
                            }
                        }
                    }

                    override fun onBufferReceived(buffer: ByteArray?) {}

                    override fun onEndOfSpeech() {
                        _isListening.value = false
                        _soundLevel.value = 0f
                        Log.d(tag, "⏱️ End of speech detected at +${System.currentTimeMillis() - speechStartTime}ms")
                    }

                    override fun onError(error: Int) {
                        mainHandler.removeCallbacks(watchdogRunnable)
                        _isListening.value = false
                        _soundLevel.value = 0f
                        val errorMsg = when (error) {
                            SpeechRecognizer.ERROR_AUDIO -> "Audio recording error"
                            SpeechRecognizer.ERROR_CLIENT -> "Client side error"
                            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Microphone permission required"
                            SpeechRecognizer.ERROR_NETWORK -> "Network connection error"
                            SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Network timeout"
                            SpeechRecognizer.ERROR_NO_MATCH -> "No speech detected"
                            SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Recognition service busy"
                            SpeechRecognizer.ERROR_SERVER -> "Server error"
                            SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No speech detected"
                            else -> "Speech recognition error"
                        }
                        Log.w(tag, "Speech recognition error: $error ($errorMsg)")
                        onSpeechError?.invoke(errorMsg)
                    }

                    override fun onResults(results: Bundle?) {
                        mainHandler.removeCallbacks(watchdogRunnable)
                        _isListening.value = false
                        _soundLevel.value = 0f
                        val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        val text = matches?.firstOrNull()?.trim() ?: ""
                        _liveTranscript.value = text
                        val totalDuration = System.currentTimeMillis() - speechStartTime
                        Log.d(tag, "⏱️ Speech-To-Text finalized in ${totalDuration}ms: \"$text\"")
                        if (text.isNotBlank()) {
                            onSpeechComplete?.invoke(text)
                        } else {
                            onSpeechError?.invoke("No words recognized.")
                        }
                    }

                    override fun onPartialResults(partialResults: Bundle?) {
                        val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        val partial = matches?.firstOrNull()?.trim() ?: ""
                        if (partial.isNotBlank()) {
                            _liveTranscript.value = partial
                        }
                    }

                    override fun onEvent(eventType: Int, params: Bundle?) {}
                })
            }

            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, languageCode)
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
                // Dynamic silence timeouts to finalize automatically within 700-900ms of user finishing speech
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 850L)
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 550L)
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 300L)
            }

            speechRecognizer?.startListening(intent)
        } catch (e: Exception) {
            mainHandler.removeCallbacks(watchdogRunnable)
            _isListening.value = false
            _soundLevel.value = 0f
            onSpeechError?.invoke("Could not start microphone: ${e.message}")
        }
    }

    fun stopListening() {
        mainHandler.removeCallbacks(watchdogRunnable)
        try {
            speechRecognizer?.stopListening()
            speechRecognizer?.destroy()
        } catch (_: Exception) {}
        speechRecognizer = null
        _isListening.value = false
        _soundLevel.value = 0f
    }
}

