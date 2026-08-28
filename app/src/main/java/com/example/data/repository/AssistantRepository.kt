package com.example.data.repository

import android.util.Log
import com.example.data.local.ChatMessageDao
import com.example.data.local.MemoryDao
import com.example.data.local.NoteDao
import com.example.data.local.ReminderDao
import com.example.data.local.UserProfileDao
import com.example.data.model.AssistantNote
import com.example.data.model.AssistantReminder
import com.example.data.model.ChatMessage
import com.example.data.model.MemoryFact
import com.example.data.model.ParsedIntent
import com.example.data.model.UserProfile
import com.example.data.remote.GeminiClient
import com.example.data.remote.LocalIntentParser
import com.example.service.IntentRouter
import com.example.service.RoutedResponse
import kotlinx.coroutines.flow.Flow

class AssistantRepository(
    private val userProfileDao: UserProfileDao,
    private val chatMessageDao: ChatMessageDao,
    private val noteDao: NoteDao,
    private val reminderDao: ReminderDao,
    private val memoryDao: MemoryDao,
    private val intentRouter: IntentRouter = IntentRouter()
) {
    private val tag = "KulsoomPerf"

    // Profiles
    val allProfiles: Flow<List<UserProfile>> = userProfileDao.getAllProfiles()

    suspend fun getProfileById(id: Long): UserProfile? = userProfileDao.getProfileById(id)
    suspend fun getProfilesWithVoiceprint(): List<UserProfile> = userProfileDao.getProfilesWithVoiceprint()
    suspend fun createProfile(profile: UserProfile): Long = userProfileDao.insertProfile(profile)
    suspend fun updateProfile(profile: UserProfile) = userProfileDao.updateProfile(profile)
    
    suspend fun deleteProfile(id: Long) {
        userProfileDao.deleteProfileById(id)
        chatMessageDao.clearMessagesForProfile(id)
        noteDao.deleteNotesByProfile(id)
        reminderDao.deleteRemindersByProfile(id)
        memoryDao.deleteMemoriesByProfile(id)
    }

    suspend fun clearVoiceprint(id: Long) = userProfileDao.clearVoiceprint(id)

    // Chat Messages
    fun getMessagesForProfile(profileId: Long): Flow<List<ChatMessage>> = chatMessageDao.getMessagesForProfile(profileId)
    val allMessages: Flow<List<ChatMessage>> = chatMessageDao.getAllMessages()

    fun searchMessages(profileId: Long, query: String): Flow<List<ChatMessage>> = chatMessageDao.searchMessages(profileId, query)

    suspend fun saveUserMessage(text: String, profileId: Long = 0L): Long {
        return chatMessageDao.insertMessage(
            ChatMessage(
                profileId = profileId,
                text = text,
                isUser = true
            )
        )
    }

    suspend fun saveAssistantResponse(
        text: String,
        intent: ParsedIntent,
        status: String,
        profileId: Long = 0L
    ): Long {
        return chatMessageDao.insertMessage(
            ChatMessage(
                profileId = profileId,
                text = text,
                isUser = false,
                intentType = intent.intent,
                targetAppOrContact = intent.appName ?: intent.contactName ?: intent.phoneNumber,
                executionStatus = status
            )
        )
    }

    suspend fun deleteMessage(id: Long) = chatMessageDao.deleteMessageById(id)
    suspend fun clearHistory(profileId: Long? = null) {
        if (profileId != null && profileId > 0) {
            chatMessageDao.clearMessagesForProfile(profileId)
        } else {
            chatMessageDao.clearAllMessages()
        }
    }

    suspend fun parseIntent(
        query: String,
        history: List<ChatMessage>,
        userDisplayName: String? = null,
        preferredLanguage: String? = null,
        userMemories: List<MemoryFact> = emptyList()
    ): ParsedIntent {
        val t0 = System.currentTimeMillis()
        val historyTuples = history.takeLast(4).map { it.text to it.isUser }
        val result = GeminiClient.parseUserCommand(
            userQuery = query,
            sessionHistory = historyTuples,
            userDisplayName = userDisplayName,
            preferredLanguage = preferredLanguage,
            userMemories = userMemories
        )
        val elapsed = System.currentTimeMillis() - t0
        Log.d(tag, "⏱️ parseIntent completed in ${elapsed}ms -> ${result.intent}")
        return result
    }

    suspend fun routeUserCommand(
        query: String,
        history: List<ChatMessage>,
        userDisplayName: String? = null,
        preferredLanguage: String? = null,
        userMemories: List<MemoryFact> = emptyList()
    ): RoutedResponse {
        val t0 = System.currentTimeMillis()

        // FAST-PATH: If query matches an unambiguous local device action, execute instantly in <10ms
        val localAttempt = LocalIntentParser.parse(query)
        if (intentRouter.isValidDeviceAction(localAttempt) && localAttempt.intent != "GENERAL_CHAT" && localAttempt.intent != "WEB_SEARCH") {
            Log.d(tag, "⏱️ Fast-path executed for unambiguous local command: \"$query\" in ${System.currentTimeMillis() - t0}ms")
            return intentRouter.routeParsedIntent(localAttempt, originalQuery = query)
        }

        // For conversational queries and complex commands, route through Gemini
        val parsedIntent = parseIntent(query, history, userDisplayName, preferredLanguage, userMemories)
        val response = intentRouter.routeParsedIntent(parsedIntent, originalQuery = query)
        Log.d(tag, "⏱️ Total routeUserCommand finished in ${System.currentTimeMillis() - t0}ms")
        return response
    }

    // Long-Term Memory
    fun getMemoriesForProfile(profileId: Long): Flow<List<MemoryFact>> = memoryDao.getMemoriesForProfile(profileId)
    val allMemories: Flow<List<MemoryFact>> = memoryDao.getAllMemories()
    suspend fun getMemoriesListForProfile(profileId: Long): List<MemoryFact> = memoryDao.getMemoriesListForProfile(profileId)

    suspend fun saveMemory(factKey: String, factValue: String, profileId: Long = 0L, source: String = "explicit"): Long {
        return memoryDao.insertMemory(
            MemoryFact(
                profileId = profileId,
                factKey = factKey.ifBlank { "memory" },
                factValue = factValue,
                source = source
            )
        )
    }

    suspend fun deleteMemory(id: Long) = memoryDao.deleteMemoryById(id)
    suspend fun deleteMemoriesByQuery(query: String, profileId: Long = 0L) = memoryDao.deleteMemoriesByQuery(profileId, query)
    suspend fun clearMemories(profileId: Long? = null) {
        if (profileId != null && profileId > 0) {
            memoryDao.deleteMemoriesByProfile(profileId)
        } else {
            memoryDao.clearAllMemories()
        }
    }

    // Notes
    fun getNotesForProfile(profileId: Long): Flow<List<AssistantNote>> = noteDao.getNotesForProfile(profileId)
    val allNotes: Flow<List<AssistantNote>> = noteDao.getAllNotes()

    suspend fun addNote(title: String, content: String, profileId: Long = 0L): Long {
        return noteDao.insertNote(AssistantNote(profileId = profileId, title = title, content = content))
    }

    suspend fun deleteNote(id: Long) = noteDao.deleteNoteById(id)

    // Reminders
    fun getRemindersForProfile(profileId: Long): Flow<List<AssistantReminder>> = reminderDao.getRemindersForProfile(profileId)
    val allReminders: Flow<List<AssistantReminder>> = reminderDao.getAllReminders()

    suspend fun addReminder(title: String, triggerTimeMillis: Long, profileId: Long = 0L): Long {
        return reminderDao.insertReminder(
            AssistantReminder(
                profileId = profileId,
                title = title,
                triggerTimeMillis = triggerTimeMillis
            )
        )
    }

    suspend fun toggleReminderComplete(reminder: AssistantReminder) {
        reminderDao.updateReminder(reminder.copy(isCompleted = !reminder.isCompleted))
    }

    suspend fun deleteReminder(id: Long) = reminderDao.deleteReminderById(id)
}
