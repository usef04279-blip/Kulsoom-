package com.example.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.Locale

class KulsoomWakeWordService : Service() {

    companion object {
        const val CHANNEL_ID = "kulsoom_wakeword_service_channel"
        const val NOTIFICATION_ID = 1001
        const val ACTION_START = "com.example.service.action.START_WAKE_WORD"
        const val ACTION_STOP = "com.example.service.action.STOP_WAKE_WORD"
        const val ACTION_WAKE_WORD_TRIGGERED = "com.example.service.action.WAKE_WORD_TRIGGERED"

        private val _isServiceRunning = MutableStateFlow(false)
        val isServiceRunning: StateFlow<Boolean> = _isServiceRunning.asStateFlow()

        private val _lastDetectedWakeWord = MutableStateFlow<String?>(null)
        val lastDetectedWakeWord: StateFlow<String?> = _lastDetectedWakeWord.asStateFlow()

        var isAppInForeground: Boolean = false
            private set

        private var instance: KulsoomWakeWordService? = null

        fun setAppForegroundState(inForeground: Boolean) {
            isAppInForeground = inForeground
            if (inForeground) {
                instance?.stopSpeechRecognizer()
            } else {
                if (_isServiceRunning.value) {
                    instance?.scheduleRestart(isError = false, initialDelayMs = 1500L)
                }
            }
        }

        fun isBatteryOptimizationIgnored(context: Context): Boolean {
            return WakeWordAudioEngine.isBatteryWhitelisted(context)
        }
    }

    private val tag = "KulsoomWakeWord"
    private var wakeLock: PowerManager.WakeLock? = null
    private var speechRecognizer: SpeechRecognizer? = null
    private val serviceScope = CoroutineScope(Dispatchers.Main + Job())
    private var isActivelyListening = false
    private var lastTriggerTime = 0L
    private var consecutiveErrors = 0

    private val WAKE_TRIGGER_COOLDOWN_MS = 2500L
    private val RESTART_BASE_DELAY_MS = 2500L

    private val WAKE_WORD_PATTERN = Regex(
        """\b(kulsoom|kalsoom|kolsum|kulsum|کلثوم|hey\s+kulsoom|hello\s+kulsoom|hi\s+kulsoom|oye\s+kulsoom|suno\s+kulsoom)\b""",
        RegexOption.IGNORE_CASE
    )

    override fun onCreate() {
        super.onCreate()
        instance = this
        Log.d(tag, "KulsoomWakeWordService onCreate")
        createNotificationChannel()

        val powerManager = getSystemService(Context.POWER_SERVICE) as? PowerManager
        wakeLock = powerManager?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Kulsoom::WakeWordLock")?.apply {
            setReferenceCounted(false)
        }

        // Connect acoustic engine trigger callback
        WakeWordAudioEngine.registerTriggerCallback { confidence ->
            Log.d(tag, "Acoustic wake word callback received (confidence: $confidence)")
            _lastDetectedWakeWord.value = "Kulsoom"
            triggerAssistantActivation()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action ?: ACTION_START
        Log.d(tag, "KulsoomWakeWordService onStartCommand with action: $action")

        if (action == ACTION_STOP) {
            stopListeningLoop()
            WakeWordAudioEngine.stop()
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    stopForeground(STOP_FOREGROUND_REMOVE)
                } else {
                    @Suppress("DEPRECATION")
                    stopForeground(true)
                }
            } catch (_: Exception) {}
            stopSelf()
            _isServiceRunning.value = false
            return START_NOT_STICKY
        }

        // Verify RECORD_AUDIO runtime permission before starting microphone foreground service
        val hasMicPermission = WakeWordAudioEngine.isPermissionGranted(this)
        if (!hasMicPermission) {
            Log.w(tag, "Cannot start WakeWordService: RECORD_AUDIO permission is not granted. Stopping service.")
            _isServiceRunning.value = false
            stopSelf()
            return START_NOT_STICKY
        }

        try {
            val notification = buildForegroundNotification()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(
                    NOTIFICATION_ID,
                    notification,
                    android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
                )
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
            _isServiceRunning.value = true
        } catch (e: Exception) {
            Log.e(tag, "Failed to start foreground service: ${e.message}", e)
            _isServiceRunning.value = false
            stopSelf()
            return START_NOT_STICKY
        }

        try {
            wakeLock?.acquire(10 * 60 * 1000L) // 10 minutes rolling lock
        } catch (e: Exception) {
            Log.w(tag, "Could not acquire wake lock: ${e.message}")
        }

        // Start continuous acoustic PCM audio streaming
        WakeWordAudioEngine.start(this)

        if (!isAppInForeground) {
            startListeningLoop()
        }
        return START_STICKY
    }

    private fun startListeningLoop() {
        if (isAppInForeground) {
            Log.d(tag, "App is active in foreground. Standing by for background wake word.")
            return
        }

        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            Log.w(tag, "Speech recognition unavailable for background wake-word listening")
            return
        }

        stopSpeechRecognizer()

        try {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this).apply {
                setRecognitionListener(object : RecognitionListener {
                    override fun onReadyForSpeech(params: android.os.Bundle?) {
                        isActivelyListening = true
                        Log.d(tag, "Wake-word recognizer ready and listening for 'Kulsoom'")
                    }

                    override fun onBeginningOfSpeech() {}
                    override fun onRmsChanged(rmsdB: Float) {}
                    override fun onBufferReceived(buffer: ByteArray?) {}

                    override fun onEndOfSpeech() {
                        isActivelyListening = false
                    }

                    override fun onError(error: Int) {
                        isActivelyListening = false
                        Log.d(tag, "Wake-word recognizer cycle ended (code: $error), scheduling clean backoff...")
                        scheduleRestart(isError = true)
                    }

                    override fun onResults(results: android.os.Bundle?) {
                        isActivelyListening = false
                        val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        val triggered = handleDetectedText(matches)
                        scheduleRestart(isError = false, initialDelayMs = if (triggered) WAKE_TRIGGER_COOLDOWN_MS else RESTART_BASE_DELAY_MS)
                    }

                    override fun onPartialResults(partialResults: android.os.Bundle?) {
                        val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        handleDetectedText(matches)
                    }

                    override fun onEvent(eventType: Int, params: android.os.Bundle?) {}
                })
            }

            val recognizerIntent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 1500L)
            }

            speechRecognizer?.startListening(recognizerIntent)
        } catch (e: Exception) {
            Log.e(tag, "Failed to initialize wake-word recognizer: ${e.message}")
            scheduleRestart(isError = true)
        }
    }

    private fun handleDetectedText(matches: List<String>?): Boolean {
        if (matches.isNullOrEmpty()) return false
        val now = System.currentTimeMillis()
        if (now - lastTriggerTime < WAKE_TRIGGER_COOLDOWN_MS) {
            Log.d(tag, "Wake word cooldown active; ignoring input")
            return false
        }

        for (candidate in matches) {
            val text = candidate.lowercase(Locale.ROOT)
            Log.d(tag, "Heard phrase in background: \"$text\"")

            if (isWakeWordMatch(text)) {
                lastTriggerTime = now
                Log.d(tag, "🔥 WAKE WORD 'Kulsoom' TRIGGERED: \"$text\"")
                _lastDetectedWakeWord.value = candidate

                WakeWordAudioEngine.recordDetectionAttempt(
                    WakeWordDetectionAttempt(
                        candidate = candidate,
                        confidence = 0.95f,
                        threshold = 0.65f,
                        passed = true,
                        peakRms = 2200f,
                        peakDb = -16.0f,
                        engineSource = "SpeechRecognizer Background"
                    )
                )

                triggerAssistantActivation()
                return true
            } else if (text.isNotBlank()) {
                WakeWordAudioEngine.recordDetectionAttempt(
                    WakeWordDetectionAttempt(
                        candidate = candidate,
                        confidence = 0.35f,
                        threshold = 0.65f,
                        passed = false,
                        peakRms = 1200f,
                        peakDb = -26.0f,
                        failureReason = "Phrase did not match 'Kulsoom' pattern",
                        engineSource = "SpeechRecognizer Background"
                    )
                )
            }
        }
        return false
    }

    private fun isWakeWordMatch(query: String): Boolean {
        val trimmed = query.trim().lowercase(Locale.ROOT)
        if (!WAKE_WORD_PATTERN.containsMatchIn(trimmed)) {
            return false
        }

        // Noise & long sentence filtering for single-word wake word:
        // Must either be a concise query (e.g. "Kulsoom", "Kulsoom what time is it")
        // or the wake word must appear at the beginning of speech
        val words = trimmed.split(Regex("\\s+"))
        if (words.size <= 4) {
            return true
        }

        val firstFewWords = words.take(3).joinToString(" ")
        return WAKE_WORD_PATTERN.containsMatchIn(firstFewWords)
    }

    private fun triggerAssistantActivation() {
        // Haptic feedback
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                vibratorManager?.defaultVibrator?.vibrate(
                    VibrationEffect.createPredefined(VibrationEffect.EFFECT_HEAVY_CLICK)
                )
            } else {
                @Suppress("DEPRECATION")
                val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                vibrator?.vibrate(100L)
            }
        } catch (_: Exception) {}

        val prefs = getSharedPreferences("kulsoom_settings", Context.MODE_PRIVATE)
        val inAppReplyEnabled = prefs.getBoolean("in_app_reply_enabled", false)

        val accessibility = KulsoomAccessibilityService.instance
        val hasActiveChatApp = accessibility != null &&
                KulsoomAccessibilityService.isServiceEnabled(this) &&
                accessibility.currentPackageName.isNotBlank() &&
                accessibility.currentPackageName != packageName &&
                (accessibility.findFocusedEditableNode() != null ||
                        accessibility.currentPackageName.contains("whatsapp") ||
                        accessibility.currentPackageName.contains("messaging") ||
                        accessibility.currentPackageName.contains("orca"))

        if (inAppReplyEnabled && hasActiveChatApp && InAppReplyOverlayManager.canDrawOverlays(this)) {
            Log.d(tag, "Opening floating In-App Reply Overlay over ${accessibility.currentPackageName}")
            InAppReplyOverlayManager.showReplyOverlay(this)
            return
        }

        // Send local broadcast to active ViewModel
        val broadcastIntent = Intent(ACTION_WAKE_WORD_TRIGGERED).apply {
            setPackage(packageName)
        }
        sendBroadcast(broadcastIntent)

        // Launch MainActivity to bring assistant to foreground
        val launchIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("WAKE_WORD_TRIGGER", true)
        }
        startActivity(launchIntent)
    }

    fun scheduleRestart(isError: Boolean = false, initialDelayMs: Long? = null) {
        if (isAppInForeground) {
            return
        }
        if (!_isServiceRunning.value) return

        if (isError) {
            consecutiveErrors++
        } else {
            consecutiveErrors = 0
        }

        val delayMs = initialDelayMs ?: (RESTART_BASE_DELAY_MS + (consecutiveErrors * 1000L)).coerceAtMost(6000L)

        serviceScope.launch {
            delay(delayMs)
            if (_isServiceRunning.value && !isAppInForeground) {
                startListeningLoop()
            }
        }
    }

    fun stopSpeechRecognizer() {
        try {
            speechRecognizer?.stopListening()
            speechRecognizer?.destroy()
        } catch (_: Exception) {}
        speechRecognizer = null
        isActivelyListening = false
    }

    private fun stopListeningLoop() {
        stopSpeechRecognizer()
        try {
            if (wakeLock?.isHeld == true) {
                wakeLock?.release()
            }
        } catch (_: Exception) {}
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Kulsoom Wake Word Detection",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Keeps voice assistant listening for 'Kulsoom' hands-free activation"
                setShowBadge(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    private fun buildForegroundNotification(): Notification {
        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            openAppIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Kulsoom Voice Assistant")
            .setContentText("Listening for \"Kulsoom\" hands-free")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(tag, "KulsoomWakeWordService onDestroy")
        stopListeningLoop()
        _isServiceRunning.value = false
        if (instance == this) {
            instance = null
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
