package com.example.service

import android.content.Context
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale
import java.util.UUID

enum class UrduVoiceStatus {
    AVAILABLE,
    MISSING_DATA,
    NOT_SUPPORTED,
    CHECKING
}

class TtsManager(private val context: Context) : TextToSpeech.OnInitListener {

    private val tag = "KulsoomTTS"
    private var tts: TextToSpeech? = null
    private var isInitialized = false

    private val _isSpeaking = MutableStateFlow(false)
    val isSpeaking: StateFlow<Boolean> = _isSpeaking.asStateFlow()

    private val _urduVoiceStatus = MutableStateFlow(UrduVoiceStatus.CHECKING)
    val urduVoiceStatus: StateFlow<UrduVoiceStatus> = _urduVoiceStatus.asStateFlow()

    private val _activeVoiceName = MutableStateFlow<String?>(null)
    val activeVoiceName: StateFlow<String?> = _activeVoiceName.asStateFlow()

    private var currentPitch = 1.0f
    private var currentSpeed = 1.0f
    private var currentLanguage = "en"

    var onSpeechFinished: (() -> Unit)? = null
    var onUrduVoiceMissing: (() -> Unit)? = null

    init {
        tts = TextToSpeech(context.applicationContext, this)
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            isInitialized = true
            try {
                checkUrduVoiceAvailability()
                setLanguage(currentLanguage)
                setPitch(currentPitch)
                setSpeed(currentSpeed)

                tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {
                        _isSpeaking.value = true
                        Log.d(tag, "TTS started playback for utterance: $utteranceId")
                    }

                    override fun onDone(utteranceId: String?) {
                        _isSpeaking.value = false
                        Log.d(tag, "TTS finished playback for utterance: $utteranceId")
                        onSpeechFinished?.invoke()
                    }

                    override fun onError(utteranceId: String?) {
                        _isSpeaking.value = false
                        Log.w(tag, "TTS error on utterance: $utteranceId")
                        onSpeechFinished?.invoke()
                    }
                })
            } catch (e: Exception) {
                Log.w(tag, "Error configuring TTS post-init: ${e.message}")
            }
        } else {
            Log.e(tag, "TextToSpeech initialization failed with status: $status")
        }
    }

    /**
     * Checks if a proper Urdu voice pack is installed on this Android device.
     */
    fun checkUrduVoiceAvailability(): UrduVoiceStatus {
        val ttsInstance = tts ?: return UrduVoiceStatus.CHECKING
        try {
            val urPkLocale = Locale.forLanguageTag("ur-PK")
            val urLocale = Locale.forLanguageTag("ur")

            val resultPk = try { ttsInstance.isLanguageAvailable(urPkLocale) } catch (e: Exception) { TextToSpeech.LANG_NOT_SUPPORTED }
            val resultUr = try { ttsInstance.isLanguageAvailable(urLocale) } catch (e: Exception) { TextToSpeech.LANG_NOT_SUPPORTED }

            val status = if (resultPk >= TextToSpeech.LANG_AVAILABLE || resultUr >= TextToSpeech.LANG_AVAILABLE) {
                UrduVoiceStatus.AVAILABLE
            } else if (resultPk == TextToSpeech.LANG_MISSING_DATA || resultUr == TextToSpeech.LANG_MISSING_DATA) {
                UrduVoiceStatus.MISSING_DATA
            } else {
                UrduVoiceStatus.NOT_SUPPORTED
            }

            _urduVoiceStatus.value = status
            Log.d(tag, "Urdu voice check result: $status (ur-PK=$resultPk, ur=$resultUr)")
            return status
        } catch (e: Exception) {
            Log.w(tag, "Failed to check Urdu voice availability: ${e.message}")
            _urduVoiceStatus.value = UrduVoiceStatus.NOT_SUPPORTED
            return UrduVoiceStatus.NOT_SUPPORTED
        }
    }

    fun setPitch(pitch: Float) {
        currentPitch = pitch
        if (isInitialized) {
            try {
                tts?.setPitch(pitch)
            } catch (_: Exception) {}
        }
    }

    fun setSpeed(speed: Float) {
        currentSpeed = speed
        if (isInitialized) {
            try {
                tts?.setSpeechRate(speed)
            } catch (_: Exception) {}
        }
    }

    fun setLanguage(langCode: String) {
        currentLanguage = langCode
        if (!isInitialized) return
        val ttsInstance = tts ?: return

        try {
            if (langCode.lowercase(Locale.ROOT).startsWith("ur")) {
                val urPkLocale = Locale.forLanguageTag("ur-PK")
                val urLocale = Locale.forLanguageTag("ur")

                val avail = try { ttsInstance.isLanguageAvailable(urPkLocale) } catch (e: Exception) { TextToSpeech.LANG_NOT_SUPPORTED }
                if (avail >= TextToSpeech.LANG_AVAILABLE) {
                    try { ttsInstance.language = urPkLocale } catch (_: Exception) {}
                    selectBestUrduVoice(urPkLocale)
                    _urduVoiceStatus.value = UrduVoiceStatus.AVAILABLE
                    Log.d(tag, "Successfully selected Urdu (Pakistan) TTS locale")
                } else {
                    val availUr = try { ttsInstance.isLanguageAvailable(urLocale) } catch (e: Exception) { TextToSpeech.LANG_NOT_SUPPORTED }
                    if (availUr >= TextToSpeech.LANG_AVAILABLE) {
                        try { ttsInstance.language = urLocale } catch (_: Exception) {}
                        selectBestUrduVoice(urLocale)
                        _urduVoiceStatus.value = UrduVoiceStatus.AVAILABLE
                        Log.d(tag, "Successfully selected generic Urdu TTS locale")
                    } else {
                        // Urdu voice data missing on device
                        Log.w(tag, "Urdu voice is missing data or not supported on this device")
                        _urduVoiceStatus.value = if (avail == TextToSpeech.LANG_MISSING_DATA || availUr == TextToSpeech.LANG_MISSING_DATA) {
                            UrduVoiceStatus.MISSING_DATA
                        } else {
                            UrduVoiceStatus.NOT_SUPPORTED
                        }
                        onUrduVoiceMissing?.invoke()
                        // Fall back to English locale for engine stability, but status is recorded
                        try { ttsInstance.language = Locale.US } catch (_: Exception) {}
                    }
                }
            } else {
                val locale = when (langCode.lowercase(Locale.ROOT)) {
                    "en-gb" -> Locale.UK
                    "en-us" -> Locale.US
                    else -> Locale.US
                }
                try { ttsInstance.language = locale } catch (_: Exception) {}
                _activeVoiceName.value = try { ttsInstance.voice?.name } catch (_: Exception) { null }
            }
        } catch (e: Exception) {
            Log.w(tag, "Error setting TTS language: ${e.message}")
        }
    }

    private fun selectBestUrduVoice(locale: Locale) {
        val ttsInstance = tts ?: return
        try {
            val voices = ttsInstance.voices
            if (!voices.isNullOrEmpty()) {
                val bestVoice = voices.find { voice: Voice? ->
                    voice != null && voice.locale?.language == "ur" && !voice.isNetworkConnectionRequired
                } ?: voices.find { it?.locale?.language == "ur" }

                if (bestVoice != null) {
                    try {
                        ttsInstance.voice = bestVoice
                        _activeVoiceName.value = bestVoice.name
                        Log.d(tag, "Selected Urdu voice: ${bestVoice.name}")
                    } catch (_: Exception) {}
                }
            }
        } catch (e: Exception) {
            Log.w(tag, "Could not inspect voices list: ${e.message}")
        }
    }

    /**
     * Speaks the given text. If the current language is Urdu and Roman Urdu text is provided,
     * it is transliterated into proper Urdu script before passing to TTS so that the Urdu engine
     * pronounces it authentically rather than with distorted English phonetics.
     * Also breaks text into sentences to start playback immediately.
     */
    fun speak(rawText: String) {
        if (rawText.isBlank()) return
        if (!isInitialized) return

        stop()

        val isUrdu = currentLanguage.lowercase(Locale.ROOT).startsWith("ur")
        val processedText = if (isUrdu) {
            UrduTransliterationHelper.prepareTextForUrduTts(rawText)
        } else {
            rawText
        }

        // Split into sentences for low-latency streaming playback
        val sentences = processedText.split(Regex("(?<=[.!?۔\n])\\s+")).filter { it.isNotBlank() }

        if (sentences.isEmpty()) {
            speakSentence(processedText, isFirst = true)
        } else {
            sentences.forEachIndexed { index, sentence ->
                speakSentence(sentence.trim(), isFirst = (index == 0))
            }
        }

        _isSpeaking.value = true
    }

    private fun speakSentence(sentence: String, isFirst: Boolean) {
        val utteranceId = UUID.randomUUID().toString()
        val params = Bundle().apply {
            putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, utteranceId)
        }
        val queueMode = if (isFirst) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD
        tts?.speak(sentence, queueMode, params, utteranceId)
    }

    fun stop() {
        try {
            tts?.stop()
        } catch (_: Exception) {}
        _isSpeaking.value = false
    }

    fun shutdown() {
        stop()
        try {
            tts?.shutdown()
        } catch (_: Exception) {}
        tts = null
        isInitialized = false
    }
}
