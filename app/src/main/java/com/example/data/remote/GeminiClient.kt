package com.example.data.remote

import android.util.Log
import com.example.BuildConfig
import com.example.data.model.MemoryFact
import com.example.data.model.ParsedIntent
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.Query
import java.util.concurrent.TimeUnit

interface GeminiApiService {
    @POST("v1beta/models/gemini-2.5-flash:generateContent")
    suspend fun generateContent(
        @Query("key") apiKey: String,
        @Body request: GeminiRequest
    ): GeminiResponse
}

object GeminiClient {
    private const val BASE_URL = "https://generativelanguage.googleapis.com/"
    private const val TAG = "KulsoomPerf"

    private val moshi: Moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()

    // Fast, responsive timeouts with dedicated ConnectionPool to eliminate TLS/DNS cold starts
    private val okHttpClient = OkHttpClient.Builder()
        .connectionPool(okhttp3.ConnectionPool(8, 5, TimeUnit.MINUTES))
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .addInterceptor(HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BASIC
        })
        .build()

    val service: GeminiApiService by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(GeminiApiService::class.java)
    }

    private val parsedIntentAdapter = moshi.adapter(ParsedIntent::class.java)

    private fun extractJsonSubstring(text: String): String? {
        val trimmed = text.trim()
        val startIdx = trimmed.indexOf('{')
        val endIdx = trimmed.lastIndexOf('}')
        if (startIdx != -1 && endIdx != -1 && endIdx > startIdx) {
            return trimmed.substring(startIdx, endIdx + 1)
        }
        return null
    }

    private fun buildSystemPrompt(
        userDisplayName: String?,
        preferredLanguage: String?,
        userMemories: List<MemoryFact> = emptyList()
    ): String {
        val userAddress = if (!userDisplayName.isNullOrBlank()) "The user's name is $userDisplayName." else ""
        val langContext = when (preferredLanguage?.lowercase(java.util.Locale.ROOT)) {
            "ur", "ur-pk" -> "The user prefers Urdu. Respond naturally in authentic Urdu script (or conversational Roman Urdu if user typed in Roman Urdu)."
            "ur-roman" -> "The user prefers Roman Urdu. Respond in natural, conversational, fluent Roman Urdu."
            else -> "Respond in English or match the user's input language (English, Urdu, Roman Urdu)."
        }

        val memoryContext = if (userMemories.isNotEmpty()) {
            """
RELEVANT USER LONG-TERM MEMORIES (Facts explicitly remembered about this user):
${userMemories.joinToString("\n") { "- ${it.factKey}: ${it.factValue}" }}
(Use these facts naturally to personalize conversations and responses. Do not list them out mechanically unless asked.)
"""
        } else ""

        return """
You are Kulsoom, an advanced, highly intelligent AI personal companion and assistant created and developed by Munib u Rehman.
$userAddress $langContext
$memoryContext

You serve two major roles in one unified intelligence:
1. DEVICE AUTOMATION & ASSISTANCE: When the user asks to perform an action on their phone (open an app, call/message a contact, set an alarm or timer, take a note, create a reminder, remember a fact in long-term memory, forget a fact, open camera / look and tell, toggle flashlight, adjust volume, play music, calculate, give daily briefing / morning summary, or search the web), classify it into the appropriate intent with all extracted parameters.
2. ADVANCED GENERAL CONVERSATION: When the user asks a question, requests advice, seeks an explanation (science, history, philosophy, code, trivia), wants creative writing, tells a joke, or just wants to chat casually (e.g., "kesi ho", "mujhe se baat karo", "kya haal hai"), classify intent as "GENERAL_CHAT" and provide a warm, direct, natural, engaging, and articulate conversational response in "spokenResponse".

STRICT PROHIBITIONS:
- NEVER output generic assistant boilerplate such as "I'm here to help you with your daily tasks" or "How can I assist you with your device today?".
- Always respond directly, naturally, and warmly to the user's conversational greeting, remark, or question.

Respond ONLY with a valid JSON object strictly matching this schema:
{
  "intent": "OPEN_APP | MAKE_CALL | SEND_SMS | SET_ALARM | SET_TIMER | SET_REMINDER | TAKE_NOTE | SAVE_MEMORY | FORGET_MEMORY | LOOK_AND_TELL | WEB_SEARCH | GET_TIME_DATE | GET_BATTERY | TOGGLE_FLASHLIGHT | ADJUST_VOLUME | PLAY_MUSIC | CALCULATE | CREATE_CALENDAR_EVENT | DAILY_BRIEFING | GENERAL_CHAT",
  "spokenResponse": "Direct, thoughtful, natural voice response directly answering the user or confirming the action",
  "appName": "lowercase app name e.g. whatsapp, youtube, spotify, chrome",
  "contactName": "contact name e.g. Ahmed, Mom",
  "phoneNumber": "raw phone number if provided",
  "messageText": "message body to send",
  "alarmHour": 7,
  "alarmMinute": 30,
  "alarmLabel": "Morning workout",
  "timerSeconds": 600,
  "reminderText": "Call the bank",
  "reminderMinutesFromNow": 60,
  "noteText": "Note content",
  "searchQuery": "search terms",
  "musicQuery": "song or artist name",
  "flashlightState": "on | off | toggle",
  "volumeDirection": "up | down | mute | max",
  "calculationExpression": "math expression",
  "calendarTitle": "Meeting with team",
  "calendarMinutesFromNow": 1440,
  "memoryKey": "brief fact descriptor or tag e.g. favorite_coffee, wife_name, project_deadline",
  "memoryValue": "complete remembered fact e.g. Loves oat milk cappuccino",
  "requiresConfirmation": false
}

Rules:
1. For SAVE_MEMORY (e.g., "Remember that...", "Remember my...", "Kulsoom remember I..."): Set intent="SAVE_MEMORY", provide a concise memoryKey, the full memoryValue, and a warm spoken confirmation in spokenResponse ("I'll remember that you love oat milk cappuccino").
2. For FORGET_MEMORY (e.g., "Forget that...", "Forget my..."): Set intent="FORGET_MEMORY" and provide the key or topic in memoryKey / memoryValue.
3. For LOOK_AND_TELL (e.g., "What is this?", "Look at this", "Look and tell", "Read this"): Set intent="LOOK_AND_TELL".
4. For phone calls and SMS, set requiresConfirmation to true if it initiates a direct action.
5. If asked about your identity, creator, or developer, state proudly that you are Kulsoom, created and developed by Munib u Rehman.
6. Match the response depth to the query (concise for quick factual questions, structured & detailed for explanations).
"""
    }

    suspend fun parseUserCommand(
        userQuery: String,
        sessionHistory: List<Pair<String, Boolean>> = emptyList(),
        userDisplayName: String? = null,
        preferredLanguage: String? = null,
        userMemories: List<MemoryFact> = emptyList()
    ): ParsedIntent {
        val apiKey = BuildConfig.GEMINI_API_KEY
        if (apiKey.isNullOrBlank() || apiKey == "MY_GEMINI_API_KEY") {
            Log.d(TAG, "⏱️ Gemini API key not present, using fast local intent parser")
            return LocalIntentParser.parse(userQuery)
        }

        val startTime = System.currentTimeMillis()
        val historyParts = mutableListOf<GeminiContent>()
        // Trim history to the last 4 turns
        sessionHistory.takeLast(4).forEach { (msg, isUser) ->
            historyParts.add(
                GeminiContent(
                    role = if (isUser) "user" else "model",
                    parts = listOf(GeminiPart(text = msg))
                )
            )
        }
        historyParts.add(
            GeminiContent(
                role = "user",
                parts = listOf(GeminiPart(text = userQuery))
            )
        )

        val systemPrompt = buildSystemPrompt(userDisplayName, preferredLanguage, userMemories)

        val request = GeminiRequest(
            contents = historyParts,
            systemInstruction = GeminiContent(parts = listOf(GeminiPart(text = systemPrompt))),
            generationConfig = GeminiGenerationConfig(
                temperature = 0.3f,
                topP = 0.95f,
                topK = 40,
                maxOutputTokens = 800,
                responseMimeType = "application/json",
                thinkingConfig = GeminiThinkingConfig(thinkingLevel = "low")
            )
        )

        return try {
            val tDispatch = System.currentTimeMillis()
            Log.d(TAG, "⏱️ [Gemini Stage 1] Dispatching API request for: \"$userQuery\" at T+${tDispatch - startTime}ms")
            val response = service.generateContent(apiKey, request)
            val tResponse = System.currentTimeMillis()
            val apiLatency = tResponse - tDispatch
            Log.d(TAG, "⏱️ [Gemini Stage 2] HTTP response received in ${apiLatency}ms (Total T+${tResponse - startTime}ms)")

            val rawText = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
            if (!rawText.isNullOrBlank()) {
                val extractedJson = extractJsonSubstring(rawText)
                val parsed = if (extractedJson != null) {
                    try {
                        parsedIntentAdapter.fromJson(extractedJson)
                    } catch (e: Exception) {
                        Log.w(TAG, "Moshi JSON parse error: ${e.message}")
                        null
                    }
                } else null

                val tParsed = System.currentTimeMillis()
                Log.d(TAG, "⏱️ [Gemini Stage 3] JSON parsed in ${tParsed - tResponse}ms (Total elapsed: ${tParsed - startTime}ms)")

                if (parsed != null && parsed.spokenResponse.isNotBlank()) {
                    parsed
                } else if (rawText.isNotBlank()) {
                    // Fallback to raw conversational output from Gemini
                    ParsedIntent(
                        intent = "GENERAL_CHAT",
                        spokenResponse = rawText.removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
                    )
                } else {
                    LocalIntentParser.parse(userQuery)
                }
            } else {
                LocalIntentParser.parse(userQuery)
            }
        } catch (e: Exception) {
            val elapsed = System.currentTimeMillis() - startTime
            Log.w(TAG, "⏱️ Gemini API call failed after ${elapsed}ms (${e.message}), falling back to local intent parser")
            LocalIntentParser.parse(userQuery)
        }
    }

    /**
     * Multimodal Look and Tell analysis: Takes a base64 encoded image frame and user query/prompt,
     * calls Gemini multimodal vision model, and returns a rich, natural, spoken-ready analysis.
     */
    suspend fun analyzeImageWithPrompt(
        base64Image: String,
        prompt: String,
        userDisplayName: String? = null,
        preferredLanguage: String? = null,
        userMemories: List<MemoryFact> = emptyList(),
        mimeType: String = "image/jpeg"
    ): String {
        val apiKey = BuildConfig.GEMINI_API_KEY
        if (apiKey.isNullOrBlank() || apiKey == "MY_GEMINI_API_KEY") {
            return "I need an active Gemini API connection to analyze camera images. Please ensure internet access and API key are configured."
        }

        val effectivePrompt = prompt.ifBlank { "What is in front of me? Describe this object, scene, or text concisely and clearly." }
        val userAddress = if (!userDisplayName.isNullOrBlank()) "The user's name is $userDisplayName." else ""
        val langContext = when (preferredLanguage?.lowercase(java.util.Locale.ROOT)) {
            "ur", "ur-pk" -> "Respond in natural, authentic Urdu."
            "ur-roman" -> "Respond in natural Roman Urdu."
            else -> "Respond in clear, natural, spoken English."
        }

        val memoryContext = if (userMemories.isNotEmpty()) {
            "Relevant facts about user: " + userMemories.joinToString("; ") { "${it.factKey}: ${it.factValue}" }
        } else ""

        val visionSystemPrompt = """
You are Kulsoom, an advanced multimodal AI assistant created by Munib u Rehman.
$userAddress $langContext $memoryContext

You are analyzing a live camera image provided by the user.
Goal: Provide an accurate, clear, articulate, and natural voice response answering the user's question or describing what is visible in the frame (objects, text, scenery, ingredients, documents, math problems, etc.).
Keep the response conversational and spoken-ready. Avoid technical jargon or Markdown formatting unless necessary.
"""

        val content = GeminiContent(
            role = "user",
            parts = listOf(
                GeminiPart(
                    inlineData = GeminiInlineData(
                        mimeType = mimeType,
                        data = base64Image
                    )
                ),
                GeminiPart(text = effectivePrompt)
            )
        )

        val request = GeminiRequest(
            contents = listOf(content),
            systemInstruction = GeminiContent(parts = listOf(GeminiPart(text = visionSystemPrompt))),
            generationConfig = GeminiGenerationConfig(
                temperature = 0.4f,
                topP = 0.95f,
                topK = 40,
                maxOutputTokens = 800,
                responseMimeType = "text/plain",
                thinkingConfig = GeminiThinkingConfig(thinkingLevel = "low")
            )
        )

        return try {
            val response = service.generateContent(apiKey, request)
            val result = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
            if (!result.isNullOrBlank()) {
                result.trim()
            } else {
                "I looked at the image, but couldn't make out the details clearly. Could you try adjusting the angle or lighting?"
            }
        } catch (e: Exception) {
            Log.e(TAG, "Vision analysis error: ${e.message}", e)
            "I couldn't analyze the image right now due to a network error. Please check your connection and try again."
        }
    }

    /**
     * Proactively prewarms the HTTP connection and TLS pool on app startup / mic trigger
     */
    fun prewarmConnection() {
        try {
            val request = okhttp3.Request.Builder()
                .url(BASE_URL)
                .head()
                .build()
            okHttpClient.newCall(request).enqueue(object : okhttp3.Callback {
                override fun onFailure(call: okhttp3.Call, e: java.io.IOException) {}
                override fun onResponse(call: okhttp3.Call, response: okhttp3.Response) {
                    response.close()
                }
            })
        } catch (_: Exception) {}
    }
}
