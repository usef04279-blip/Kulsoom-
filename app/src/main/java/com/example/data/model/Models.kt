package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.squareup.moshi.JsonClass

enum class AssistantState {
    IDLE,
    LISTENING,
    THINKING,
    SPEAKING
}

enum class IntentType {
    OPEN_APP,
    MAKE_CALL,
    SEND_SMS,
    SET_ALARM,
    SET_TIMER,
    SET_REMINDER,
    TAKE_NOTE,
    WEB_SEARCH,
    GET_TIME_DATE,
    GET_BATTERY,
    TOGGLE_FLASHLIGHT,
    ADJUST_VOLUME,
    PLAY_MUSIC,
    CALCULATE,
    CREATE_CALENDAR_EVENT,
    SAVE_MEMORY,
    FORGET_MEMORY,
    LOOK_AND_TELL,
    GENERAL_CHAT
}

enum class ExecutionStatus {
    PENDING_CONFIRMATION,
    EXECUTED,
    CANCELLED,
    FAILED,
    NONE
}

@Entity(tableName = "user_profiles")
data class UserProfile(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val displayName: String,
    val voiceprintEmbedding: String = "",
    val hasVoiceprint: Boolean = false,
    val preferredLanguage: String = "en-US", // "en-US", "ur-PK", "ur-Roman"
    val voiceTonePreset: String = "Natural Warm", // "Natural Warm", "Professional Crisp", "Soft Melodic"
    val ttsPitch: Float = 1.0f,
    val ttsSpeed: Float = 1.0f,
    val onlyRespondToMyVoice: Boolean = false,
    val avatarColorIndex: Int = 0,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "chat_messages")
data class ChatMessage(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val profileId: Long = 0L,
    val text: String,
    val isUser: Boolean,
    val timestamp: Long = System.currentTimeMillis(),
    val intentType: String? = null,
    val targetAppOrContact: String? = null,
    val actionPayloadJson: String? = null,
    val executionStatus: String = ExecutionStatus.NONE.name,
    val spokenLanguage: String = "en"
)

@Entity(tableName = "assistant_notes")
data class AssistantNote(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val profileId: Long = 0L,
    val title: String,
    val content: String,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "assistant_reminders")
data class AssistantReminder(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val profileId: Long = 0L,
    val title: String,
    val triggerTimeMillis: Long,
    val isCompleted: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "assistant_memories")
data class MemoryFact(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val profileId: Long = 0L,
    val factKey: String,
    val factValue: String,
    val source: String = "explicit", // "explicit", "user_stated"
    val createdAt: Long = System.currentTimeMillis(),
    val lastAccessedAt: Long = System.currentTimeMillis()
)

@JsonClass(generateAdapter = true)
data class ParsedIntent(
    val intent: String = "GENERAL_CHAT",
    val spokenResponse: String = "",
    val appName: String? = null,
    val contactName: String? = null,
    val phoneNumber: String? = null,
    val messageText: String? = null,
    val alarmHour: Int? = null,
    val alarmMinute: Int? = null,
    val alarmLabel: String? = null,
    val timerSeconds: Int? = null,
    val reminderText: String? = null,
    val reminderMinutesFromNow: Int? = null,
    val noteText: String? = null,
    val searchQuery: String? = null,
    val musicQuery: String? = null,
    val flashlightState: String? = null, // "on", "off", "toggle"
    val volumeDirection: String? = null, // "up", "down", "mute", "max"
    val calculationExpression: String? = null,
    val calendarTitle: String? = null,
    val calendarMinutesFromNow: Int? = null,
    val memoryKey: String? = null,
    val memoryValue: String? = null,
    val requiresConfirmation: Boolean = false
)
