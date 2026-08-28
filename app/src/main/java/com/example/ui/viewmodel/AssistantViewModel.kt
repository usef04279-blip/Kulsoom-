package com.example.ui.viewmodel

import android.app.Application
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.AppDatabase
import com.example.data.model.*
import com.example.data.remote.GeminiClient
import com.example.data.remote.LocalIntentParser
import com.example.data.repository.AssistantRepository
import com.example.service.*
import com.example.util.CrashReporter
import com.example.util.NetworkMonitor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class PendingConfirmation(
    val title: String,
    val description: String,
    val intent: ParsedIntent
)

data class VoiceTrainingState(
    val isActive: Boolean = false,
    val currentStep: Int = 1, // 1 to 5
    val samplesCollected: Int = 0,
    val feedbackMessage: String = "Say \"Kulsoom\" in your normal speaking voice",
    val isRecordingSample: Boolean = false,
    val sampleQualityOk: Boolean = true,
    val collectedEmbeddings: List<List<Float>> = emptyList()
)

class AssistantViewModel(application: Application) : AndroidViewModel(application) {

    private val tag = "KulsoomPerf"
    private val db = AppDatabase.getDatabase(application)
    private val repository = AssistantRepository(
        db.userProfileDao(),
        db.chatMessageDao(),
        db.noteDao(),
        db.reminderDao(),
        db.memoryDao()
    )

    private val actionHandler = DeviceActionHandler(application)
    private val speechManager = SpeechManager(application)
    private val ttsManager = TtsManager(application)
    private val intentRouter = IntentRouter()

    private val bargeInDetector = BargeInDetector(application) {
        handleBargeIn()
    }

    // Network connectivity flow
    val isOnline: StateFlow<Boolean> = NetworkMonitor.isOnlineFlow
    private var hasExplainedOfflineThisSession = false

    // State flows
    private val _assistantState = MutableStateFlow(AssistantState.IDLE)
    val assistantState: StateFlow<AssistantState> = _assistantState.asStateFlow()

    private val _currentStatusText = MutableStateFlow("Tap the orb or say \"Kulsoom\"")
    val currentStatusText: StateFlow<String> = _currentStatusText.asStateFlow()

    private val _liveSpeechText = MutableStateFlow("")
    val liveSpeechText: StateFlow<String> = _liveSpeechText.asStateFlow()

    val soundLevel: StateFlow<Float> = speechManager.soundLevel

    private val _pendingConfirmation = MutableStateFlow<PendingConfirmation?>(null)
    val pendingConfirmation: StateFlow<PendingConfirmation?> = _pendingConfirmation.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    // Urdu Voice Diagnostics
    val urduVoiceStatus: StateFlow<UrduVoiceStatus> = ttsManager.urduVoiceStatus

    // Wake Word Service Running Flow
    val isWakeWordServiceRunning: StateFlow<Boolean> = KulsoomWakeWordService.isServiceRunning

    // Profiles
    val profiles: StateFlow<List<UserProfile>> = repository.allProfiles
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _activeProfileId = MutableStateFlow<Long?>(null)
    val activeProfileId: StateFlow<Long?> = _activeProfileId.asStateFlow()

    val activeProfile: StateFlow<UserProfile?> = combine(profiles, _activeProfileId) { profileList, activeId ->
        profileList.find { it.id == activeId }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    // Voice Training State
    private val _voiceTrainingState = MutableStateFlow(VoiceTrainingState())
    val voiceTrainingState: StateFlow<VoiceTrainingState> = _voiceTrainingState.asStateFlow()

    // Ambiguity resolution state for speaker verification
    private val _ambiguousPromptCandidates = MutableStateFlow<Pair<UserProfile, UserProfile>?>(null)
    val ambiguousPromptCandidates: StateFlow<Pair<UserProfile, UserProfile>?> = _ambiguousPromptCandidates.asStateFlow()

    // Onboarding State
    private val prefs = application.getSharedPreferences("kulsoom_prefs", Context.MODE_PRIVATE)
    private val _hasCompletedOnboarding = MutableStateFlow(prefs.getBoolean("has_completed_onboarding", false))
    val hasCompletedOnboarding: StateFlow<Boolean> = _hasCompletedOnboarding.asStateFlow()

    fun completeOnboarding() {
        prefs.edit().putBoolean("has_completed_onboarding", true).apply()
        _hasCompletedOnboarding.value = true
    }

    fun resetOnboarding() {
        prefs.edit().putBoolean("has_completed_onboarding", false).apply()
        _hasCompletedOnboarding.value = false
    }

    // Wake Word Engine & Diagnostics Flows
    val wakeWordLiveMicLevel: StateFlow<Float> = WakeWordAudioEngine.liveMicLevel
    val wakeWordLiveDbLevel: StateFlow<Float> = WakeWordAudioEngine.liveDbLevel
    val wakeWordDetectionLogs: StateFlow<List<WakeWordDetectionAttempt>> = WakeWordAudioEngine.detectionLogs
    val isWakeWordEngineRunning: StateFlow<Boolean> = WakeWordAudioEngine.isEngineRunning
    val lastSuccessfulWakeWordDetection: StateFlow<WakeWordDetectionAttempt?> = WakeWordAudioEngine.lastSuccessfulDetection

    // Settings
    private val _wakeWordEnabled = MutableStateFlow(prefs.getBoolean("wake_word_enabled", true))
    val wakeWordEnabled: StateFlow<Boolean> = _wakeWordEnabled.asStateFlow()

    private val _continuousConversationEnabled = MutableStateFlow(prefs.getBoolean("continuous_conversation_enabled", true))
    val continuousConversationEnabled: StateFlow<Boolean> = _continuousConversationEnabled.asStateFlow()

    fun setContinuousConversationEnabled(enabled: Boolean) {
        _continuousConversationEnabled.value = enabled
        prefs.edit().putBoolean("continuous_conversation_enabled", enabled).apply()
    }

    private val _allowInterruptionsEnabled = MutableStateFlow(prefs.getBoolean("allow_interruptions_enabled", true))
    val allowInterruptionsEnabled: StateFlow<Boolean> = _allowInterruptionsEnabled.asStateFlow()

    fun setAllowInterruptionsEnabled(enabled: Boolean) {
        _allowInterruptionsEnabled.value = enabled
        prefs.edit().putBoolean("allow_interruptions_enabled", enabled).apply()
        if (!enabled) {
            bargeInDetector.stopMonitoring()
        }
    }

    private val _lockScreenResponseEnabled = MutableStateFlow(prefs.getBoolean("lock_screen_response", true))
    val lockScreenResponseEnabled: StateFlow<Boolean> = _lockScreenResponseEnabled.asStateFlow()

    private val _trustedQuickActions = MutableStateFlow(prefs.getBoolean("trusted_quick_actions", false))
    val trustedQuickActions: StateFlow<Boolean> = _trustedQuickActions.asStateFlow()

    private val _variedResponsesEnabled = MutableStateFlow(prefs.getBoolean("varied_responses", true))
    val variedResponsesEnabled: StateFlow<Boolean> = _variedResponsesEnabled.asStateFlow()

    private val _offerDailyBriefingMorning = MutableStateFlow(prefs.getBoolean("offer_daily_briefing_morning", false))
    val offerDailyBriefingMorning: StateFlow<Boolean> = _offerDailyBriefingMorning.asStateFlow()

    private val _inAppReplyEnabled = MutableStateFlow(prefs.getBoolean("in_app_reply_enabled", false))
    val inAppReplyEnabled: StateFlow<Boolean> = _inAppReplyEnabled.asStateFlow()

    private val _crashReportingEnabled = MutableStateFlow(prefs.getBoolean("crash_reporting_enabled", true))
    val crashReportingEnabled: StateFlow<Boolean> = _crashReportingEnabled.asStateFlow()

    fun setCrashReportingEnabled(enabled: Boolean) {
        _crashReportingEnabled.value = enabled
        prefs.edit().putBoolean("crash_reporting_enabled", enabled).apply()
        CrashReporter.setCrashReportingEnabled(getApplication(), enabled)
    }

    private val _selectedLanguage = MutableStateFlow("en-US")
    val selectedLanguage: StateFlow<String> = _selectedLanguage.asStateFlow()

    private val _ttsPitch = MutableStateFlow(1.0f)
    val ttsPitch: StateFlow<Float> = _ttsPitch.asStateFlow()

    private val _ttsSpeed = MutableStateFlow(1.0f)
    val ttsSpeed: StateFlow<Float> = _ttsSpeed.asStateFlow()

    // Profile-scoped DB flows
    val messages: StateFlow<List<ChatMessage>> = combine(_searchQuery, _activeProfileId) { query, profileId ->
        Pair(query, profileId ?: 0L)
    }.flatMapLatest { (query, profileId) ->
        if (query.isBlank()) {
            repository.getMessagesForProfile(profileId)
        } else {
            repository.searchMessages(profileId, query)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val notes: StateFlow<List<AssistantNote>> = _activeProfileId
        .flatMapLatest { profileId ->
            repository.getNotesForProfile(profileId ?: 0L)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val reminders: StateFlow<List<AssistantReminder>> = _activeProfileId
        .flatMapLatest { profileId ->
            repository.getRemindersForProfile(profileId ?: 0L)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Long-Term Memory State
    private val _longTermMemoryEnabled = MutableStateFlow(prefs.getBoolean("long_term_memory_enabled", true))
    val longTermMemoryEnabled: StateFlow<Boolean> = _longTermMemoryEnabled.asStateFlow()

    fun setLongTermMemoryEnabled(enabled: Boolean) {
        _longTermMemoryEnabled.value = enabled
        prefs.edit().putBoolean("long_term_memory_enabled", enabled).apply()
    }

    val memories: StateFlow<List<com.example.data.model.MemoryFact>> = _activeProfileId
        .flatMapLatest { profileId ->
            repository.getMemoriesForProfile(profileId ?: 0L)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun saveCustomMemory(key: String, value: String) {
        viewModelScope.launch {
            repository.saveMemory(
                factKey = if (key.isBlank()) "general_fact" else key,
                factValue = value,
                profileId = _activeProfileId.value ?: 0L
            )
        }
    }

    fun deleteMemory(id: Long) {
        viewModelScope.launch {
            repository.deleteMemory(id)
        }
    }

    fun clearAllMemories() {
        viewModelScope.launch {
            repository.clearMemories(_activeProfileId.value)
        }
    }

    // Vision ("Look and Tell") State
    private val _isVisionModeActive = MutableStateFlow(false)
    val isVisionModeActive: StateFlow<Boolean> = _isVisionModeActive.asStateFlow()

    private val _isAnalyzingVision = MutableStateFlow(false)
    val isAnalyzingVision: StateFlow<Boolean> = _isAnalyzingVision.asStateFlow()

    private val _visionAnalysisResult = MutableStateFlow<String?>(null)
    val visionAnalysisResult: StateFlow<String?> = _visionAnalysisResult.asStateFlow()

    fun openVisionMode() {
        _isVisionModeActive.value = true
        _visionAnalysisResult.value = null
    }

    fun closeVisionMode() {
        _isVisionModeActive.value = false
        _isAnalyzingVision.value = false
    }

    fun analyzeVisionFrame(base64Image: String, userQuestion: String = "") {
        if (!NetworkMonitor.isOnline) {
            val offlineMsg = "Look and Tell requires an internet connection for multimodal visual analysis. Please connect to the internet and try again."
            _visionAnalysisResult.value = offlineMsg
            speakResponse(offlineMsg)
            return
        }
        _isAnalyzingVision.value = true
        _visionAnalysisResult.value = "Analyzing what you're looking at..."
        viewModelScope.launch {
            val profile = activeProfile.value
            val memoryList = if (_longTermMemoryEnabled.value) {
                repository.getMemoriesListForProfile(profile?.id ?: 0L)
            } else emptyList()

            val analysis = GeminiClient.analyzeImageWithPrompt(
                base64Image = base64Image,
                prompt = userQuestion,
                userDisplayName = profile?.displayName,
                preferredLanguage = profile?.preferredLanguage ?: _selectedLanguage.value,
                userMemories = memoryList
            )
            _isAnalyzingVision.value = false
            _visionAnalysisResult.value = analysis

            val queryDesc = if (userQuestion.isNotBlank()) "Look and Tell: \"$userQuestion\"" else "Look and Tell (Visual Analysis)"
            repository.saveUserMessage(queryDesc, profile?.id ?: 0L)
            repository.saveAssistantResponse(
                text = analysis,
                intent = ParsedIntent(intent = "LOOK_AND_TELL", spokenResponse = analysis),
                status = ExecutionStatus.EXECUTED.name,
                profileId = profile?.id ?: 0L
            )

            speakResponse(analysis)
        }
    }

    init {
        speechManager.onSpeechComplete = { text ->
            _liveSpeechText.value = text
            handleSpeechInput(text)
        }

        speechManager.onSpeechError = { error ->
            if (_voiceTrainingState.value.isActive) {
                _voiceTrainingState.value = _voiceTrainingState.value.copy(
                    isRecordingSample = false,
                    feedbackMessage = "Could not capture audio clearly. Please tap Record to try again."
                )
            } else {
                _assistantState.value = AssistantState.IDLE
                _currentStatusText.value = error
            }
        }

        ttsManager.onSpeechFinished = {
            bargeInDetector.stopMonitoring()
            if (_assistantState.value == AssistantState.SPEAKING) {
                if (_continuousConversationEnabled.value) {
                    // Continuous conversation: automatically listen for follow-up
                    _assistantState.value = AssistantState.LISTENING
                    _currentStatusText.value = "Listening for follow-up..."
                    _liveSpeechText.value = ""
                    speechManager.startListening(_selectedLanguage.value)
                } else {
                    _assistantState.value = AssistantState.IDLE
                    val name = activeProfile.value?.displayName
                    _currentStatusText.value = if (name != null) "Ready for $name" else "Kulsoom is ready"
                    if (_wakeWordEnabled.value) {
                        WakeWordAudioEngine.resume(application)
                    }
                }
            }
        }

        // Connect acoustic wake-word callback for instant hands-free triggering in foreground
        WakeWordAudioEngine.registerTriggerCallback { confidence ->
            if (_assistantState.value == AssistantState.IDLE && !_voiceTrainingState.value.isActive) {
                viewModelScope.launch(Dispatchers.Main) {
                    Log.d(tag, "🔥 Foreground acoustic wake-word recognized ($confidence)! Transitioning to LISTENING")
                    val lang = activeProfile.value?.preferredLanguage ?: _selectedLanguage.value
                    val ack = ResponseVarietyManager.getWakeAcknowledgment(lang, _variedResponsesEnabled.value)
                    _currentStatusText.value = "Wake-word \"Kulsoom\" recognized! Listening..."
                    speakResponse(ack)
                    startListening()
                }
            }
        }

        // Apply active profile preferences when profile changes
        viewModelScope.launch {
            activeProfile.collect { profile ->
                if (profile != null) {
                    _selectedLanguage.value = profile.preferredLanguage
                    _ttsPitch.value = profile.ttsPitch
                    _ttsSpeed.value = profile.ttsSpeed
                    ttsManager.setLanguage(if (profile.preferredLanguage.startsWith("ur")) "ur" else "en")
                    ttsManager.setPitch(profile.ttsPitch)
                    ttsManager.setSpeed(profile.ttsSpeed)
                    _currentStatusText.value = "Active: ${profile.displayName}"
                } else {
                    _selectedLanguage.value = "en-US"
                    _ttsPitch.value = 1.0f
                    _ttsSpeed.value = 1.0f
                    ttsManager.setLanguage("en")
                    ttsManager.setPitch(1.0f)
                    ttsManager.setSpeed(1.0f)
                    _currentStatusText.value = "Guest Mode"
                }
            }
        }

        // Initialize NetworkMonitor and CrashReporter
        NetworkMonitor.init(application)
        CrashReporter.init(application)

        // Observe network state to reset offline explanation when reconnected
        viewModelScope.launch {
            isOnline.collect { online ->
                if (online) {
                    hasExplainedOfflineThisSession = false
                }
            }
        }

        // Start background wake-word service if enabled
        if (_wakeWordEnabled.value) {
            updateWakeWordService(true)
        }
    }

    private fun handleSpeechInput(text: String) {
        if (_voiceTrainingState.value.isActive) {
            handleTrainingSpeechSample(text)
            return
        }

        // Check if resolving an ambiguous speaker prompt
        val ambiguous = _ambiguousPromptCandidates.value
        if (ambiguous != null) {
            _ambiguousPromptCandidates.value = null
            val lower = text.lowercase(java.util.Locale.ROOT)
            val cand1Name = ambiguous.first.displayName.lowercase(java.util.Locale.ROOT)
            val cand2Name = ambiguous.second.displayName.lowercase(java.util.Locale.ROOT)

            if (lower.contains(cand1Name)) {
                selectProfile(ambiguous.first)
                speakResponse("Welcome back, ${ambiguous.first.displayName}. How can I help you?")
                return
            } else if (lower.contains(cand2Name)) {
                selectProfile(ambiguous.second)
                speakResponse("Welcome back, ${ambiguous.second.displayName}. How can I help you?")
                return
            }
        }

        // Runtime Speaker Verification
        val currentProfiles = profiles.value
        val hasEnrolledProfiles = currentProfiles.any { it.hasVoiceprint }

        if (hasEnrolledProfiles) {
            val capturedEmbedding = VoiceprintEngine.extractEmbeddingFromAudio(
                userPitchEstimate = _ttsPitch.value,
                soundLevelSequence = speechManager.capturedRmsLevels
            )
            val verification = VoiceprintEngine.verifySpeaker(capturedEmbedding, currentProfiles)

            if (verification.isSilentRejection) {
                // Strict "Only respond to my voice" is enabled and voice did not match
                _assistantState.value = AssistantState.IDLE
                _currentStatusText.value = "Voice unrecognized (Only respond to my voice is enabled)"
                return
            }

            if (verification.isAmbiguous && verification.ambiguousCandidate1 != null && verification.ambiguousCandidate2 != null) {
                _ambiguousPromptCandidates.value = Pair(verification.ambiguousCandidate1, verification.ambiguousCandidate2)
                val promptText = "I noticed similar voices. Is this ${verification.ambiguousCandidate1.displayName} or ${verification.ambiguousCandidate2.displayName}?"
                _currentStatusText.value = promptText
                speakResponse(promptText)
                return
            }

            if (verification.matchedProfile != null && verification.matchedProfile.id != _activeProfileId.value) {
                // Automatically switch to recognized profile
                selectProfile(verification.matchedProfile)
            }
        }

        processUserCommand(text)
    }

    private fun handleBargeIn() {
        if (_assistantState.value == AssistantState.SPEAKING) {
            Log.d(tag, "🎙️ Interruption / Barge-in triggered. Halting TTS and listening to user.")
            ttsManager.stop()
            bargeInDetector.stopMonitoring()
            _assistantState.value = AssistantState.LISTENING
            _currentStatusText.value = "Interrupted — Listening..."
            _liveSpeechText.value = ""
            speechManager.startListening(_selectedLanguage.value)
        }
    }

    fun startListening() {
        ttsManager.stop()
        WakeWordAudioEngine.pause()
        _assistantState.value = AssistantState.LISTENING
        _currentStatusText.value = "Listening..."
        _liveSpeechText.value = ""
        speechManager.startListening(_selectedLanguage.value)
        // Proactively prewarm TLS/DNS connection so API call is instant
        GeminiClient.prewarmConnection()
    }

    fun stopListening() {
        speechManager.stopListening()
        if (_assistantState.value == AssistantState.LISTENING) {
            _assistantState.value = AssistantState.IDLE
            _currentStatusText.value = "Listening cancelled"
            if (_wakeWordEnabled.value) {
                WakeWordAudioEngine.resume(getApplication())
            }
        }
    }

    fun toggleListening() {
        if (_assistantState.value == AssistantState.LISTENING) {
            stopListening()
        } else {
            startListening()
        }
    }

    fun processUserCommand(query: String) {
        if (query.isBlank()) return

        val startTime = System.currentTimeMillis()
        Log.d(tag, "⏱️ Starting processing user command: \"$query\" at T=0ms")

        viewModelScope.launch {
            _assistantState.value = AssistantState.THINKING
            _currentStatusText.value = "Thinking..."
            _liveSpeechText.value = query

            val currentProfile = activeProfile.value
            val currentProfileId = currentProfile?.id ?: 0L

            // Save user message in DB asynchronously to prevent blocking the critical path
            viewModelScope.launch(Dispatchers.IO) {
                repository.saveUserMessage(query, currentProfileId)
            }

            val isCurrentlyOnline = NetworkMonitor.isOnline(getApplication())
            CrashReporter.logBreadcrumb("AssistantVM", "Processing command online=$isCurrentlyOnline")

            if (!isCurrentlyOnline) {
                // OFFLINE FALLBACK MODE
                val localAttempt = LocalIntentParser.parse(query)
                val isOfflineDeviceAction = intentRouter.isOfflineCapable(localAttempt.intent)

                if (isOfflineDeviceAction) {
                    CrashReporter.logBreadcrumb("AssistantVM", "Offline mode: handling ${localAttempt.intent}")

                    if (localAttempt.intent == "READ_REMINDERS") {
                        val activeReminders = reminders.value.filter { !it.isCompleted }
                        val responseText = if (activeReminders.isEmpty()) {
                            "You have no active reminders."
                        } else {
                            "You have ${activeReminders.size} reminder${if (activeReminders.size > 1) "s" else ""}: " +
                                    activeReminders.joinToString(", ") { it.title }
                        }
                        _currentStatusText.value = responseText
                        repository.saveAssistantResponse(
                            text = responseText,
                            intent = localAttempt,
                            status = ExecutionStatus.EXECUTED.name,
                            profileId = currentProfileId
                        )
                        speakResponse(responseText)
                        return@launch
                    }

                    if (localAttempt.intent == "READ_NOTES") {
                        val savedNotes = notes.value
                        val responseText = if (savedNotes.isEmpty()) {
                            "You have no saved notes."
                        } else {
                            "You have ${savedNotes.size} note${if (savedNotes.size > 1) "s" else ""}: " +
                                    savedNotes.take(3).joinToString(", ") { it.content.ifBlank { it.title } }
                        }
                        _currentStatusText.value = responseText
                        repository.saveAssistantResponse(
                            text = responseText,
                            intent = localAttempt,
                            status = ExecutionStatus.EXECUTED.name,
                            profileId = currentProfileId
                        )
                        speakResponse(responseText)
                        return@launch
                    }

                    // Handle special note or reminder local saves
                    if (localAttempt.intent == "TAKE_NOTE" && !localAttempt.noteText.isNullOrBlank()) {
                        repository.addNote(
                            title = "Voice Note",
                            content = localAttempt.noteText,
                            profileId = currentProfileId
                        )
                    } else if (localAttempt.intent == "SET_REMINDER" && !localAttempt.reminderText.isNullOrBlank()) {
                        val trigger = System.currentTimeMillis() + (localAttempt.reminderMinutesFromNow ?: 30) * 60 * 1000L
                        repository.addReminder(
                            title = localAttempt.reminderText,
                            triggerTimeMillis = trigger,
                            profileId = currentProfileId
                        )
                    } else if (localAttempt.intent == "LOOK_AND_TELL") {
                        val offlineMsg = "Look and Tell requires an internet connection for multimodal visual analysis."
                        _currentStatusText.value = offlineMsg
                        repository.saveAssistantResponse(
                            text = offlineMsg,
                            intent = localAttempt,
                            status = ExecutionStatus.FAILED.name,
                            profileId = currentProfileId
                        )
                        speakResponse(offlineMsg)
                        return@launch
                    } else if (localAttempt.intent == "SAVE_MEMORY" && !localAttempt.memoryValue.isNullOrBlank()) {
                        val key = localAttempt.memoryKey ?: "memory"
                        val value = localAttempt.memoryValue ?: ""
                        repository.saveMemory(key, value, currentProfileId)
                        val spoken = if (localAttempt.spokenResponse.isNotBlank()) localAttempt.spokenResponse else "I'll remember that: $value"
                        _currentStatusText.value = "Remembered"
                        repository.saveAssistantResponse(
                            text = spoken,
                            intent = localAttempt,
                            status = ExecutionStatus.EXECUTED.name,
                            profileId = currentProfileId
                        )
                        speakResponse(spoken)
                        return@launch
                    } else if (localAttempt.intent == "FORGET_MEMORY") {
                        val queryToForget = localAttempt.memoryValue ?: localAttempt.memoryKey ?: ""
                        if (queryToForget.isNotBlank()) {
                            repository.deleteMemoriesByQuery(queryToForget, currentProfileId)
                        }
                        val spoken = if (localAttempt.spokenResponse.isNotBlank()) localAttempt.spokenResponse else "I've forgotten that memory"
                        _currentStatusText.value = "Forgotten"
                        repository.saveAssistantResponse(
                            text = spoken,
                            intent = localAttempt,
                            status = ExecutionStatus.EXECUTED.name,
                            profileId = currentProfileId
                        )
                        speakResponse(spoken)
                        return@launch
                    }

                    val skipConf = _trustedQuickActions.value
                    val result = actionHandler.executeAction(localAttempt, skipConfirmation = skipConf)

                    when (result) {
                        is ActionResult.NeedsConfirmation -> {
                            _assistantState.value = AssistantState.IDLE
                            _currentStatusText.value = "Confirmation needed"
                            _pendingConfirmation.value = PendingConfirmation(
                                title = result.description,
                                description = "Please confirm before executing this action.",
                                intent = result.pendingIntent
                            )
                            repository.saveAssistantResponse(
                                text = result.description,
                                intent = localAttempt,
                                status = ExecutionStatus.PENDING_CONFIRMATION.name,
                                profileId = currentProfileId
                            )
                            speakResponse(result.description)
                        }
                        is ActionResult.Success -> {
                            val spokenText = if (localAttempt.spokenResponse.isNotBlank()) localAttempt.spokenResponse else result.message
                            _currentStatusText.value = result.message
                            repository.saveAssistantResponse(
                                text = result.message,
                                intent = localAttempt,
                                status = ExecutionStatus.EXECUTED.name,
                                profileId = currentProfileId
                            )
                            speakResponse(spokenText)
                        }
                        is ActionResult.Failure -> {
                            _currentStatusText.value = result.reason
                            repository.saveAssistantResponse(
                                text = result.reason,
                                intent = localAttempt,
                                status = ExecutionStatus.FAILED.name,
                                profileId = currentProfileId
                            )
                            speakResponse(result.reason)
                        }
                    }
                    return@launch
                } else {
                    // Non-offline-capable query when offline
                    CrashReporter.logBreadcrumb("AssistantVM", "Offline fallback triggered for cloud query")
                    val explanation = if (!hasExplainedOfflineThisSession) {
                        hasExplainedOfflineThisSession = true
                        "I'm offline right now, so I can't help with that — but I can still handle things like alarms, opening apps, or your flashlight."
                    } else {
                        "Still offline right now. I can only do device actions like alarms, notes, and timers without internet."
                    }
                    _currentStatusText.value = "Offline Mode"
                    val offlineIntent = ParsedIntent(
                        intent = "OFFLINE_FALLBACK",
                        spokenResponse = explanation
                    )
                    repository.saveAssistantResponse(
                        text = explanation,
                        intent = offlineIntent,
                        status = ExecutionStatus.NONE.name,
                        profileId = currentProfileId
                    )
                    speakResponse(explanation)
                    return@launch
                }
            }

            // ONLINE MODE: Parse and route intent using Gemini and IntentRouter
            val currentHistory = messages.value
            val memoryList = if (_longTermMemoryEnabled.value) {
                repository.getMemoriesListForProfile(currentProfileId)
            } else emptyList()

            val routedResponse = try {
                repository.routeUserCommand(
                    query = query,
                    history = currentHistory,
                    userDisplayName = currentProfile?.displayName,
                    preferredLanguage = currentProfile?.preferredLanguage ?: _selectedLanguage.value,
                    userMemories = memoryList
                )
            } catch (e: Exception) {
                Log.e(tag, "Error routing user command: ${e.message}", e)
                CrashReporter.recordNonFatal(e, "routeUserCommand")
                // Fallback to local parsing on network exception
                val localAttempt = LocalIntentParser.parse(query)
                intentRouter.routeParsedIntent(localAttempt, originalQuery = query)
            }

            val intentResolvedTime = System.currentTimeMillis() - startTime
            Log.d(tag, "⏱️ Command routed in ${intentResolvedTime}ms -> $routedResponse")

            when (routedResponse) {
                is RoutedResponse.DeviceAction -> {
                    val parsedIntent = routedResponse.intent

                    // Handle Daily Briefing directly
                    if (parsedIntent.intent == "DAILY_BRIEFING") {
                        executeDailyBriefing(currentProfile, currentProfileId)
                        return@launch
                    }

                    // Handle Look and Tell directly
                    if (parsedIntent.intent == "LOOK_AND_TELL") {
                        openVisionMode()
                        val spoken = if (parsedIntent.spokenResponse.isNotBlank()) parsedIntent.spokenResponse else "Opening camera for Look and Tell"
                        _currentStatusText.value = spoken
                        repository.saveAssistantResponse(
                            text = spoken,
                            intent = parsedIntent,
                            status = ExecutionStatus.EXECUTED.name,
                            profileId = currentProfileId
                        )
                        speakResponse(spoken)
                        return@launch
                    }

                    // Handle Memory Save directly
                    if (parsedIntent.intent == "SAVE_MEMORY" && !parsedIntent.memoryValue.isNullOrBlank()) {
                        val key = parsedIntent.memoryKey ?: "memory"
                        val value = parsedIntent.memoryValue ?: ""
                        repository.saveMemory(key, value, currentProfileId)
                        val spoken = if (parsedIntent.spokenResponse.isNotBlank()) parsedIntent.spokenResponse else "I'll remember that: $value"
                        _currentStatusText.value = "Remembered"
                        repository.saveAssistantResponse(
                            text = spoken,
                            intent = parsedIntent,
                            status = ExecutionStatus.EXECUTED.name,
                            profileId = currentProfileId
                        )
                        speakResponse(spoken)
                        return@launch
                    }

                    // Handle Memory Forget directly
                    if (parsedIntent.intent == "FORGET_MEMORY") {
                        val queryToForget = parsedIntent.memoryValue ?: parsedIntent.memoryKey ?: ""
                        if (queryToForget.isNotBlank()) {
                            repository.deleteMemoriesByQuery(queryToForget, currentProfileId)
                        }
                        val spoken = if (parsedIntent.spokenResponse.isNotBlank()) parsedIntent.spokenResponse else "I've forgotten that memory"
                        _currentStatusText.value = "Forgotten"
                        repository.saveAssistantResponse(
                            text = spoken,
                            intent = parsedIntent,
                            status = ExecutionStatus.EXECUTED.name,
                            profileId = currentProfileId
                        )
                        speakResponse(spoken)
                        return@launch
                    }

                    // Handle special note or reminder local saves
                    if (parsedIntent.intent == "TAKE_NOTE" && !parsedIntent.noteText.isNullOrBlank()) {
                        repository.addNote(
                            title = "Voice Note",
                            content = parsedIntent.noteText,
                            profileId = currentProfileId
                        )
                    } else if (parsedIntent.intent == "SET_REMINDER" && !parsedIntent.reminderText.isNullOrBlank()) {
                        val trigger = System.currentTimeMillis() + (parsedIntent.reminderMinutesFromNow ?: 30) * 60 * 1000L
                        repository.addReminder(
                            title = parsedIntent.reminderText,
                            triggerTimeMillis = trigger,
                            profileId = currentProfileId
                        )
                    }

                    // Execute device action or ask for confirmation
                    val skipConf = _trustedQuickActions.value
                    val result = actionHandler.executeAction(parsedIntent, skipConfirmation = skipConf)

                    val actionDoneTime = System.currentTimeMillis() - startTime
                    Log.d(tag, "⏱️ Action handled in ${actionDoneTime}ms -> $result")

                    when (result) {
                        is ActionResult.NeedsConfirmation -> {
                            _assistantState.value = AssistantState.IDLE
                            _currentStatusText.value = "Confirmation needed"
                            _pendingConfirmation.value = PendingConfirmation(
                                title = result.description,
                                description = "Please confirm before executing this action.",
                                intent = result.pendingIntent
                            )
                            repository.saveAssistantResponse(
                                text = result.description,
                                intent = parsedIntent,
                                status = ExecutionStatus.PENDING_CONFIRMATION.name,
                                profileId = currentProfileId
                            )
                            speakResponse(result.description)
                        }
                        is ActionResult.Success -> {
                            val spokenText = if (parsedIntent.spokenResponse.isNotBlank()) parsedIntent.spokenResponse else result.message
                            _currentStatusText.value = result.message
                            repository.saveAssistantResponse(
                                text = result.message,
                                intent = parsedIntent,
                                status = ExecutionStatus.EXECUTED.name,
                                profileId = currentProfileId
                            )
                            speakResponse(spokenText)
                        }
                        is ActionResult.Failure -> {
                            _currentStatusText.value = result.reason
                            repository.saveAssistantResponse(
                                text = result.reason,
                                intent = parsedIntent,
                                status = ExecutionStatus.FAILED.name,
                                profileId = currentProfileId
                            )
                            speakResponse(result.reason)
                        }
                    }
                }
                is RoutedResponse.Conversational -> {
                    // Treat output as a conversational chatbot response to be displayed in history
                    _currentStatusText.value = "Kulsoom responded"
                    val chatIntent = ParsedIntent(
                        intent = "GENERAL_CHAT",
                        spokenResponse = routedResponse.responseText
                    )
                    repository.saveAssistantResponse(
                        text = routedResponse.responseText,
                        intent = chatIntent,
                        status = ExecutionStatus.NONE.name,
                        profileId = currentProfileId
                    )
                    val totalLatency = System.currentTimeMillis() - startTime
                    Log.d(tag, "⏱️ Conversational response ready at total ${totalLatency}ms. Speaking...")
                    speakResponse(routedResponse.responseText)
                }
            }
        }
    }

    fun confirmPendingAction() {
        val pending = _pendingConfirmation.value ?: return
        _pendingConfirmation.value = null
        val currentProfileId = _activeProfileId.value ?: 0L

        viewModelScope.launch {
            _assistantState.value = AssistantState.THINKING
            val result = actionHandler.executeAction(pending.intent, skipConfirmation = true)
            when (result) {
                is ActionResult.Success -> {
                    _currentStatusText.value = result.message
                    repository.saveAssistantResponse(
                        text = result.message,
                        intent = pending.intent,
                        status = ExecutionStatus.EXECUTED.name,
                        profileId = currentProfileId
                    )
                    speakResponse(result.message)
                }
                is ActionResult.Failure -> {
                    _currentStatusText.value = result.reason
                    repository.saveAssistantResponse(
                        text = result.reason,
                        intent = pending.intent,
                        status = ExecutionStatus.FAILED.name,
                        profileId = currentProfileId
                    )
                    speakResponse(result.reason)
                }
                else -> {}
            }
        }
    }

    fun cancelPendingAction() {
        _pendingConfirmation.value = null
        _assistantState.value = AssistantState.IDLE
        _currentStatusText.value = "Action cancelled"
        speakResponse("Action cancelled")
    }

    fun speakResponse(text: String) {
        _assistantState.value = AssistantState.SPEAKING
        ttsManager.speak(text)
        if (_allowInterruptionsEnabled.value) {
            bargeInDetector.startMonitoring()
        }
    }

    fun stopSpeaking() {
        bargeInDetector.stopMonitoring()
        ttsManager.stop()
        if (_assistantState.value == AssistantState.SPEAKING) {
            _assistantState.value = AssistantState.IDLE
            val name = activeProfile.value?.displayName
            _currentStatusText.value = if (name != null) "Ready for $name" else "Kulsoom is ready"
        }
    }

    // Profile Management
    fun selectProfile(profile: UserProfile?) {
        _activeProfileId.value = profile?.id
    }

    fun selectProfileById(profileId: Long?) {
        _activeProfileId.value = profileId
    }

    fun createProfile(
        displayName: String,
        preferredLanguage: String,
        voiceTonePreset: String,
        ttsPitch: Float,
        ttsSpeed: Float,
        onlyRespondToMyVoice: Boolean,
        avatarColorIndex: Int,
        voiceprintSamples: List<List<Float>> = emptyList()
    ) = viewModelScope.launch {
        val embeddingStr = if (voiceprintSamples.isNotEmpty()) {
            VoiceprintEngine.combineTrainingSamples(voiceprintSamples)
        } else {
            ""
        }

        val newProfile = UserProfile(
            displayName = displayName.trim(),
            voiceprintEmbedding = embeddingStr,
            hasVoiceprint = embeddingStr.isNotBlank(),
            preferredLanguage = preferredLanguage,
            voiceTonePreset = voiceTonePreset,
            ttsPitch = ttsPitch,
            ttsSpeed = ttsSpeed,
            onlyRespondToMyVoice = onlyRespondToMyVoice,
            avatarColorIndex = avatarColorIndex
        )

        val newId = repository.createProfile(newProfile)
        _activeProfileId.value = newId
    }

    fun completeOnboardingWithProfile(
        displayName: String,
        preferredLanguage: String = "en-US",
        voiceTonePreset: String = "Natural Warm",
        ttsPitch: Float = 1.0f,
        ttsSpeed: Float = 1.0f,
        onlyRespondToMyVoice: Boolean = false,
        avatarColorIndex: Int = 0,
        voiceprintSamples: List<List<Float>> = emptyList()
    ) {
        createProfile(
            displayName = displayName,
            preferredLanguage = preferredLanguage,
            voiceTonePreset = voiceTonePreset,
            ttsPitch = ttsPitch,
            ttsSpeed = ttsSpeed,
            onlyRespondToMyVoice = onlyRespondToMyVoice,
            avatarColorIndex = avatarColorIndex,
            voiceprintSamples = voiceprintSamples
        )
        completeOnboarding()
    }

    fun updateProfile(profile: UserProfile) = viewModelScope.launch {
        repository.updateProfile(profile)
        if (_activeProfileId.value == profile.id) {
            _selectedLanguage.value = profile.preferredLanguage
            _ttsPitch.value = profile.ttsPitch
            _ttsSpeed.value = profile.ttsSpeed
            ttsManager.setLanguage(if (profile.preferredLanguage.startsWith("ur")) "ur" else "en")
            ttsManager.setPitch(profile.ttsPitch)
            ttsManager.setSpeed(profile.ttsSpeed)
        }
    }

    fun deleteProfile(id: Long) = viewModelScope.launch {
        if (_activeProfileId.value == id) {
            _activeProfileId.value = null
        }
        repository.deleteProfile(id)
    }

    fun clearVoiceprint(id: Long) = viewModelScope.launch {
        repository.clearVoiceprint(id)
    }

    // Unified 5-Step Voice Configuration Methods
    fun startVoiceTraining() {
        val prompt1 = VOICE_TRAINING_PROMPTS[0]
        _voiceTrainingState.value = VoiceTrainingState(
            isActive = true,
            currentStep = 1,
            samplesCollected = 0,
            feedbackMessage = "Sample 1 of 5: Tap record and read \"${prompt1.phrase}\"",
            isRecordingSample = false,
            sampleQualityOk = true,
            collectedEmbeddings = emptyList()
        )
    }

    fun rerecordSample(stepNumber: Int) {
        val step = stepNumber.coerceIn(1, 5)
        val prompt = VOICE_TRAINING_PROMPTS[step - 1]
        val current = _voiceTrainingState.value
        _voiceTrainingState.value = current.copy(
            isActive = true,
            currentStep = step,
            feedbackMessage = "Re-recording Sample $step of 5: Read \"${prompt.phrase}\"",
            isRecordingSample = false,
            sampleQualityOk = true
        )
    }

    fun recordTrainingSample() {
        val current = _voiceTrainingState.value
        val step = current.currentStep.coerceIn(1, 5)
        val prompt = VOICE_TRAINING_PROMPTS[step - 1]
        _voiceTrainingState.value = current.copy(
            isRecordingSample = true,
            feedbackMessage = "Listening: Say \"${prompt.phrase}\"..."
        )
        speechManager.startListening(_selectedLanguage.value)
    }

    private fun handleTrainingSpeechSample(transcript: String) {
        val current = _voiceTrainingState.value
        speechManager.stopListening()

        val step = current.currentStep.coerceIn(1, 5)
        val quality = VoiceprintEngine.validateSampleQuality(
            sampleNumber = step,
            peakSoundLevel = speechManager.soundLevel.value.coerceAtLeast(0.35f),
            durationSeconds = 1.2f,
            userPitchEstimate = _ttsPitch.value
        )

        if (quality.isValid) {
            val updatedList = current.collectedEmbeddings.toMutableList()
            if (step <= updatedList.size) {
                // Replacing / re-recording existing sample
                updatedList[step - 1] = quality.features
            } else {
                updatedList.add(quality.features)
            }

            val nextStep = if (updatedList.size < 5) updatedList.size + 1 else 5
            val isAllDone = updatedList.size >= 5

            if (isAllDone) {
                _voiceTrainingState.value = current.copy(
                    currentStep = 5,
                    samplesCollected = 5,
                    feedbackMessage = "All 5 samples recorded! Tap 'Complete & Save Voiceprint' to finalize.",
                    isRecordingSample = false,
                    sampleQualityOk = true,
                    collectedEmbeddings = updatedList
                )
            } else {
                val nextPrompt = VOICE_TRAINING_PROMPTS[nextStep - 1]
                _voiceTrainingState.value = current.copy(
                    currentStep = nextStep,
                    samplesCollected = updatedList.size,
                    feedbackMessage = "Sample $step saved! Ready for sample $nextStep of 5: \"${nextPrompt.phrase}\"",
                    isRecordingSample = false,
                    sampleQualityOk = true,
                    collectedEmbeddings = updatedList
                )
            }
        } else {
            _voiceTrainingState.value = current.copy(
                isRecordingSample = false,
                sampleQualityOk = false,
                feedbackMessage = quality.feedbackMessage
            )
        }
    }

    fun cancelVoiceTraining() {
        speechManager.stopListening()
        _voiceTrainingState.value = VoiceTrainingState(isActive = false)
    }

    // Wake Word Service Management
    fun setInAppReplyEnabled(enabled: Boolean) {
        _inAppReplyEnabled.value = enabled
        prefs.edit().putBoolean("in_app_reply_enabled", enabled).apply()
        val app = getApplication<Application>()
        app.getSharedPreferences("kulsoom_settings", Context.MODE_PRIVATE)
            .edit()
            .putBoolean("in_app_reply_enabled", enabled)
            .apply()
    }

    fun setWakeWordEnabled(enabled: Boolean) {
        _wakeWordEnabled.value = enabled
        prefs.edit().putBoolean("wake_word_enabled", enabled).apply()
        updateWakeWordService(enabled)
        val app = getApplication<Application>()
        if (enabled) {
            WakeWordAudioEngine.start(app)
        } else {
            WakeWordAudioEngine.stop()
        }
    }

    fun updateWakeWordService(enable: Boolean) {
        val app = getApplication<Application>()
        val hasMicPermission = androidx.core.content.ContextCompat.checkSelfPermission(
            app,
            android.Manifest.permission.RECORD_AUDIO
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED

        if (enable && !hasMicPermission) {
            Log.d(tag, "Wake word service startup deferred: RECORD_AUDIO permission not yet granted")
            return
        }

        val serviceIntent = Intent(app, KulsoomWakeWordService::class.java).apply {
            action = if (enable) KulsoomWakeWordService.ACTION_START else KulsoomWakeWordService.ACTION_STOP
        }
        try {
            if (enable) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    app.startForegroundService(serviceIntent)
                } else {
                    app.startService(serviceIntent)
                }
            } else {
                app.startService(serviceIntent)
            }
        } catch (e: Exception) {
            Log.w(tag, "Could not start/stop wake word foreground service: ${e.message}")
        }
    }

    fun triggerWakeWordTest() {
        startListening()
        val lang = activeProfile.value?.preferredLanguage ?: _selectedLanguage.value
        val ack = ResponseVarietyManager.getWakeAcknowledgment(lang, _variedResponsesEnabled.value)
        _currentStatusText.value = "Wake-word triggered! Listening..."
        speakResponse(ack)
    }

    fun triggerManualDiagnosticTest(confidence: Float = 0.95f) {
        WakeWordAudioEngine.triggerManualDiagnosticTest(confidence)
        val lang = activeProfile.value?.preferredLanguage ?: _selectedLanguage.value
        val ack = ResponseVarietyManager.getWakeAcknowledgment(lang, _variedResponsesEnabled.value)
        _currentStatusText.value = "Diagnostic Test: \"Kulsoom\" triggered (${(confidence * 100).toInt()}%)"
        speakResponse(ack)
        startListening()
    }

    fun clearWakeWordLogs() {
        WakeWordAudioEngine.clearLogs()
    }

    private suspend fun executeDailyBriefing(currentProfile: UserProfile?, currentProfileId: Long) {
        val app = getApplication<Application>()
        val lang = currentProfile?.preferredLanguage ?: _selectedLanguage.value
        val allowVariety = _variedResponsesEnabled.value

        val facts = DailyBriefingManager.compileBriefingFacts(
            context = app,
            profileDisplayName = currentProfile?.displayName,
            language = lang,
            reminders = reminders.value,
            notes = notes.value,
            allowVariety = allowVariety
        )

        val spokenSummary = DailyBriefingManager.buildSpokenBriefing(facts, lang)

        _currentStatusText.value = "Daily Briefing"
        val briefingIntent = ParsedIntent(
            intent = "DAILY_BRIEFING",
            spokenResponse = spokenSummary
        )

        repository.saveAssistantResponse(
            text = spokenSummary,
            intent = briefingIntent,
            status = ExecutionStatus.EXECUTED.name,
            profileId = currentProfileId
        )

        speakResponse(spokenSummary)
    }

    fun checkUrduVoice() {
        ttsManager.checkUrduVoiceAvailability()
    }

    fun setLockScreenResponseEnabled(enabled: Boolean) {
        _lockScreenResponseEnabled.value = enabled
        prefs.edit().putBoolean("lock_screen_response", enabled).apply()
    }

    fun setTrustedQuickActions(enabled: Boolean) {
        _trustedQuickActions.value = enabled
        prefs.edit().putBoolean("trusted_quick_actions", enabled).apply()
    }

    fun setVariedResponsesEnabled(enabled: Boolean) {
        _variedResponsesEnabled.value = enabled
        prefs.edit().putBoolean("varied_responses", enabled).apply()
    }

    fun setOfferDailyBriefingMorning(enabled: Boolean) {
        _offerDailyBriefingMorning.value = enabled
        prefs.edit().putBoolean("offer_daily_briefing_morning", enabled).apply()
    }

    fun setLanguage(lang: String) {
        _selectedLanguage.value = lang
        val ttsLang = if (lang.startsWith("ur")) "ur" else "en"
        ttsManager.setLanguage(ttsLang)
    }

    fun setTtsPitch(pitch: Float) {
        _ttsPitch.value = pitch
        ttsManager.setPitch(pitch)
    }

    fun setTtsSpeed(speed: Float) {
        _ttsSpeed.value = speed
        ttsManager.setSpeed(speed)
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun deleteMessage(id: Long) = viewModelScope.launch {
        repository.deleteMessage(id)
    }

    fun clearHistory() = viewModelScope.launch {
        repository.clearHistory(_activeProfileId.value)
    }

    fun addNote(title: String, content: String) = viewModelScope.launch {
        repository.addNote(title, content, _activeProfileId.value ?: 0L)
    }

    fun deleteNote(id: Long) = viewModelScope.launch {
        repository.deleteNote(id)
    }

    fun addReminder(title: String, triggerTimeMillis: Long) = viewModelScope.launch {
        repository.addReminder(title, triggerTimeMillis, _activeProfileId.value ?: 0L)
    }

    fun toggleReminder(reminder: AssistantReminder) = viewModelScope.launch {
        repository.toggleReminderComplete(reminder)
    }

    fun deleteReminder(id: Long) = viewModelScope.launch {
        repository.deleteReminder(id)
    }

    override fun onCleared() {
        super.onCleared()
        bargeInDetector.stopMonitoring()
        speechManager.stopListening()
        ttsManager.shutdown()
    }
}
