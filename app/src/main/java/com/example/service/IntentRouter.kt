package com.example.service

import com.example.data.model.ParsedIntent
import com.example.data.remote.LocalIntentParser
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import java.util.Locale

/**
 * Result representation for routed Gemini output.
 */
sealed class RoutedResponse {
    /**
     * A recognized, valid on-device automation intent.
     * Contains the structured intent payload ready for execution by DeviceActionHandler.
     */
    data class DeviceAction(
        val intent: ParsedIntent,
        val actionName: String,
        val spokenResponse: String
    ) : RoutedResponse()

    /**
     * Standard conversational chatbot response to be spoken and displayed in chat history.
     */
    data class Conversational(
        val responseText: String,
        val originalQuery: String = ""
    ) : RoutedResponse()
}

/**
 * IntentRouter processes incoming Gemini text responses by checking if a valid
 * device-action intent exists; if not, treats the output as a conversational
 * chatbot response to be displayed in the history.
 */
class IntentRouter(
    private val moshi: Moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
) {
    private val parsedIntentAdapter = moshi.adapter(ParsedIntent::class.java)

    companion object {
        /**
         * Set of supported on-device action intents.
         */
        val DEVICE_ACTION_INTENTS = setOf(
            "OPEN_APP",
            "MAKE_CALL",
            "SEND_SMS",
            "SET_ALARM",
            "SET_TIMER",
            "SET_REMINDER",
            "TAKE_NOTE",
            "WEB_SEARCH",
            "GET_TIME_DATE",
            "GET_BATTERY",
            "TOGGLE_FLASHLIGHT",
            "ADJUST_VOLUME",
            "PLAY_MUSIC",
            "CALCULATE",
            "CREATE_CALENDAR_EVENT",
            "DAILY_BRIEFING",
            "SET_DND",
            "ADJUST_BRIGHTNESS",
            "READ_REMINDERS",
            "READ_NOTES",
            "SAVE_MEMORY",
            "FORGET_MEMORY",
            "LOOK_AND_TELL"
        )

        /**
         * Set of intents that can execute completely on-device without internet access.
         */
        val OFFLINE_CAPABLE_INTENTS = setOf(
            "OPEN_APP",
            "SET_ALARM",
            "SET_TIMER",
            "SET_REMINDER",
            "TAKE_NOTE",
            "GET_TIME_DATE",
            "GET_BATTERY",
            "TOGGLE_FLASHLIGHT",
            "ADJUST_VOLUME",
            "PLAY_MUSIC",
            "CALCULATE",
            "SET_DND",
            "ADJUST_BRIGHTNESS",
            "READ_REMINDERS",
            "READ_NOTES",
            "SAVE_MEMORY",
            "FORGET_MEMORY",
            "LOOK_AND_TELL"
        )
    }

    fun isOfflineCapable(intentName: String): Boolean {
        return OFFLINE_CAPABLE_INTENTS.contains(intentName)
    }

    /**
     * Processes an incoming raw Gemini text response string.
     * Strips Markdown wrappers/code fences, attempts to parse as a structured ParsedIntent,
     * checks if a valid device action exists, and routes to either DeviceAction or Conversational.
     */
    fun routeGeminiResponse(rawText: String, originalQuery: String = ""): RoutedResponse {
        if (rawText.isBlank()) {
            return RoutedResponse.Conversational(
                responseText = "I'm listening. How can I help you?",
                originalQuery = originalQuery
            )
        }

        val cleanJson = cleanJsonString(rawText)
        val parsedIntent: ParsedIntent? = try {
            parsedIntentAdapter.fromJson(cleanJson)
        } catch (e: Exception) {
            null
        }

        return if (parsedIntent != null) {
            routeParsedIntent(parsedIntent, originalQuery)
        } else {
            // Not JSON or parse failed: check if local fallback has device action
            val localFallback = LocalIntentParser.parse(originalQuery.ifBlank { rawText })
            if (isValidDeviceAction(localFallback)) {
                routeParsedIntent(localFallback, originalQuery)
            } else {
                // Pure conversational response from Gemini text output
                val cleanChatText = sanitizeConversationalText(rawText)
                RoutedResponse.Conversational(
                    responseText = cleanChatText,
                    originalQuery = originalQuery
                )
            }
        }
    }

    /**
     * Evaluates a ParsedIntent instance:
     * Checks if it corresponds to a valid device-action intent with sufficient parameters.
     * If valid, returns RoutedResponse.DeviceAction.
     * Otherwise, treats it as a conversational chatbot response to be displayed in history.
     */
    fun routeParsedIntent(intent: ParsedIntent, originalQuery: String = ""): RoutedResponse {
        val intentName = intent.intent.trim().uppercase(Locale.ROOT)

        if (isValidDeviceAction(intent)) {
            val spoken = if (intent.spokenResponse.isNotBlank()) {
                intent.spokenResponse
            } else {
                getDefaultSpokenForAction(intent)
            }
            return RoutedResponse.DeviceAction(
                intent = intent.copy(spokenResponse = spoken),
                actionName = intentName,
                spokenResponse = spoken
            )
        }

        // No valid device action intent found; treat as conversational response for history
        val chatMessage = if (intent.spokenResponse.isNotBlank()) {
            intent.spokenResponse
        } else {
            "I'm listening, tell me what you'd like to explore or talk about."
        }

        return RoutedResponse.Conversational(
            responseText = chatMessage,
            originalQuery = originalQuery
        )
    }

    /**
     * Checks if an intent name string represents a known device action.
     */
    fun isDeviceActionIntent(intentName: String?): Boolean {
        if (intentName.isNullOrBlank()) return false
        return DEVICE_ACTION_INTENTS.contains(intentName.trim().uppercase(Locale.ROOT))
    }

    /**
     * Validates whether a ParsedIntent has a recognized device action and the necessary payload fields.
     */
    fun isValidDeviceAction(intent: ParsedIntent?): Boolean {
        if (intent == null) return false
        val intentName = intent.intent.trim().uppercase(Locale.ROOT)
        if (!DEVICE_ACTION_INTENTS.contains(intentName)) return false

        // Check required fields for device actions
        return when (intentName) {
            "OPEN_APP" -> !intent.appName.isNullOrBlank()
            "MAKE_CALL" -> !intent.contactName.isNullOrBlank() || !intent.phoneNumber.isNullOrBlank()
            "SEND_SMS" -> (!intent.contactName.isNullOrBlank() || !intent.phoneNumber.isNullOrBlank()) && !intent.messageText.isNullOrBlank()
            "SET_ALARM" -> intent.alarmHour != null
            "SET_TIMER" -> intent.timerSeconds != null && (intent.timerSeconds ?: 0) > 0
            "SET_REMINDER" -> !intent.reminderText.isNullOrBlank()
            "TAKE_NOTE" -> !intent.noteText.isNullOrBlank()
            "WEB_SEARCH" -> !intent.searchQuery.isNullOrBlank()
            "PLAY_MUSIC" -> !intent.musicQuery.isNullOrBlank()
            "CALCULATE" -> !intent.calculationExpression.isNullOrBlank()
            "CREATE_CALENDAR_EVENT" -> !intent.calendarTitle.isNullOrBlank()
            "SAVE_MEMORY" -> !intent.memoryValue.isNullOrBlank()
            "FORGET_MEMORY" -> !intent.memoryValue.isNullOrBlank() || !intent.memoryKey.isNullOrBlank()
            "LOOK_AND_TELL" -> true
            "TOGGLE_FLASHLIGHT", "ADJUST_VOLUME", "GET_BATTERY", "GET_TIME_DATE", "DAILY_BRIEFING", "SET_DND", "ADJUST_BRIGHTNESS", "READ_REMINDERS", "READ_NOTES" -> true
            else -> false
        }
    }

    private fun cleanJsonString(raw: String): String {
        return raw.trim()
            .removePrefix("```json")
            .removePrefix("```JSON")
            .removePrefix("```")
            .removeSuffix("```")
            .trim()
    }

    private fun sanitizeConversationalText(raw: String): String {
        val text = raw.trim()
            .removePrefix("```markdown")
            .removePrefix("```")
            .removeSuffix("```")
            .trim()
        return text.ifBlank { "I understand. How can I help you further?" }
    }

    private fun getDefaultSpokenForAction(intent: ParsedIntent): String {
        return when (intent.intent.uppercase(Locale.ROOT)) {
            "OPEN_APP" -> "Opening ${intent.appName ?: "application"}"
            "MAKE_CALL" -> "Calling ${intent.contactName ?: intent.phoneNumber ?: "contact"}"
            "SEND_SMS" -> "Sending message to ${intent.contactName ?: "recipient"}"
            "SET_ALARM" -> "Setting your alarm"
            "SET_TIMER" -> "Starting timer"
            "SET_REMINDER" -> "Reminder set: ${intent.reminderText ?: ""}"
            "TAKE_NOTE" -> "Note saved"
            "TOGGLE_FLASHLIGHT" -> "Toggling flashlight"
            "ADJUST_VOLUME" -> "Adjusting volume"
            "GET_BATTERY" -> "Checking battery"
            "GET_TIME_DATE" -> "Checking current time and date"
            "PLAY_MUSIC" -> "Playing ${intent.musicQuery ?: "music"}"
            "WEB_SEARCH" -> "Searching for ${intent.searchQuery ?: ""}"
            "CALCULATE" -> "Calculating"
            "CREATE_CALENDAR_EVENT" -> "Adding event to calendar"
            "DAILY_BRIEFING" -> "Here is your daily briefing"
            "SET_DND" -> "Setting Do Not Disturb"
            "ADJUST_BRIGHTNESS" -> "Adjusting screen brightness"
            "READ_REMINDERS" -> "Checking your reminders"
            "READ_NOTES" -> "Checking your notes"
            "SAVE_MEMORY" -> "I will remember that"
            "FORGET_MEMORY" -> "I've forgotten that memory"
            "LOOK_AND_TELL" -> "Opening Look and Tell camera"
            else -> "Processing your request"
        }
    }
}
