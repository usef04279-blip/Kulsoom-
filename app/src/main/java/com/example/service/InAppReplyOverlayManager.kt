package com.example.service

import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import com.example.data.remote.GeminiClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Manages the floating In-App Reply Dictation overlay card that appears on top of
 * WhatsApp, SMS, and other messaging apps when the wake-word is triggered.
 */
object InAppReplyOverlayManager {

    private const val TAG = "InAppReplyOverlay"
    private var windowManager: WindowManager? = null
    private var overlayView: View? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private var speechManager: SpeechManager? = null

    /**
     * Checks if the app can show floating overlay windows.
     */
    fun canDrawOverlays(context: Context): Boolean {
        return Settings.canDrawOverlays(context)
    }

    /**
     * Shows the In-App Reply overlay over the current chat app.
     */
    fun showReplyOverlay(context: Context, preferredLanguage: String = "en-US") {
        if (!canDrawOverlays(context)) {
            Log.w(TAG, "Cannot draw overlays: permission not granted")
            return
        }

        val accessibility = KulsoomAccessibilityService.instance
        if (accessibility == null || !KulsoomAccessibilityService.isServiceEnabled(context)) {
            Toast.makeText(context, "Please enable Kulsoom Accessibility Service in Settings", Toast.LENGTH_LONG).show()
            return
        }

        // Verify active editable field exists
        val targetNode = accessibility.findFocusedEditableNode()
        val appLabel = accessibility.getCurrentAppLabel()

        mainHandler.post {
            dismissOverlay()

            windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager

            val layoutFlag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            }

            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                layoutFlag,
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
                y = 100
            }

            val inflater = LayoutInflater.from(context)
            val container = createOverlayView(context, appLabel, hasField = targetNode != null) { action, text ->
                when (action) {
                    "INSERT" -> {
                        val inserted = accessibility.insertTextIntoFocusedNode(text)
                        if (!inserted) {
                            Toast.makeText(context, "Could not insert text into $appLabel reply field", Toast.LENGTH_SHORT).show()
                        }
                        dismissOverlay()
                    }
                    "SEND" -> {
                        val inserted = accessibility.insertTextIntoFocusedNode(text)
                        if (inserted) {
                            mainHandler.postDelayed({
                                accessibility.clickSendButton()
                            }, 250)
                        }
                        dismissOverlay()
                    }
                    "CANCEL" -> {
                        dismissOverlay()
                    }
                }
            }

            overlayView = container
            try {
                windowManager?.addView(container, params)
                startDictation(context, preferredLanguage)
            } catch (e: Exception) {
                Log.e(TAG, "Error adding overlay view: ${e.message}")
            }
        }
    }

    private fun startDictation(context: Context, preferredLanguage: String) {
        speechManager = SpeechManager(context).apply {
            onSpeechComplete = { rawSpeech ->
                processSpeechToReply(rawSpeech, preferredLanguage)
            }
            onSpeechError = { errorMsg ->
                updateStatusText("Dictation error: $errorMsg")
            }
            startListening(preferredLanguage)
        }
        updateStatusText("Listening for your reply...")
    }

    private fun processSpeechToReply(rawSpeech: String, preferredLanguage: String) {
        if (rawSpeech.isBlank()) {
            updateStatusText("No speech detected.")
            return
        }

        updateStatusText("Formatting reply...")
        // Clean speech (e.g., if user said "tell them I'm on my way" or "send that I'll be there")
        scope.launch {
            val cleanedText = withContext(Dispatchers.IO) {
                cleanReplyText(rawSpeech)
            }
            withContext(Dispatchers.Main) {
                updateDraftedText(cleanedText)
                updateStatusText("Ready to insert into chat")

                // Check if user explicitly included "send it"
                val lower = rawSpeech.lowercase()
                if (lower.contains("and send it") || lower.contains("aur bhej do") || lower.contains("and send that")) {
                    // Pre-select send
                    updateStatusText("Auto-sending enabled (explicitly requested)")
                }
            }
        }
    }

    private fun cleanReplyText(raw: String): String {
        var text = raw.trim()
        val prefixes = listOf(
            "tell them", "tell him", "tell her", "say that", "reply that", "reply with", "type that", "type",
            "unko kaho ke", "unhe kaho ke", "unko bolo ke", "batao ke", "unko bolo"
        )
        for (prefix in prefixes) {
            if (text.startsWith(prefix, ignoreCase = true)) {
                text = text.substring(prefix.length).trim()
                if (text.startsWith("that ", ignoreCase = true)) {
                    text = text.substring(5).trim()
                }
                break
            }
        }
        // Remove trailing "and send it" / "aur bhej do"
        text = text.replace(Regex("(?i)\\s*(and\\s+send\\s+it|and\\s+send\\s+that|aur\\s+bhej\\s+do|aur\\s+send\\s+kardo)$"), "").trim()
        return text.ifBlank { raw }
    }

    private var statusTextView: TextView? = null
    private var draftTextView: TextView? = null
    private var insertButton: Button? = null
    private var sendButton: Button? = null

    private fun updateStatusText(text: String) {
        mainHandler.post {
            statusTextView?.text = text
        }
    }

    private fun updateDraftedText(text: String) {
        mainHandler.post {
            draftTextView?.text = text
            insertButton?.isEnabled = text.isNotBlank()
            sendButton?.isEnabled = text.isNotBlank()
        }
    }

    private fun createOverlayView(
        context: Context,
        appLabel: String,
        hasField: Boolean,
        onAction: (String, String) -> Unit
    ): View {
        val dp = context.resources.displayMetrics.density

        val root = android.widget.LinearLayout(context).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding((16 * dp).toInt(), (16 * dp).toInt(), (16 * dp).toInt(), (16 * dp).toInt())
            background = android.graphics.drawable.GradientDrawable().apply {
                setColor(0xEE121826.toInt()) // Glass dark blue
                cornerRadius = 24 * dp
                setStroke((1.5f * dp).toInt(), 0x4460A5FA.toInt()) // Cyan-blue neon border
            }
            layoutParams = WindowManager.LayoutParams(
                (360 * dp).toInt(),
                WindowManager.LayoutParams.WRAP_CONTENT
            )
        }

        // Header Row (Icon + Title + Close)
        val header = android.widget.LinearLayout(context).apply {
            orientation = android.widget.LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 0, 0, (8 * dp).toInt())
        }

        val title = TextView(context).apply {
            text = "Kulsoom • $appLabel Reply"
            textSize = 15f
            setTextColor(0xFF38BDF8.toInt()) // Neon cyan
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            layoutParams = android.widget.LinearLayout.LayoutParams(0, WindowManager.LayoutParams.WRAP_CONTENT, 1f)
        }

        val closeBtn = TextView(context).apply {
            text = "✕"
            textSize = 18f
            setTextColor(0xFF94A3B8.toInt())
            setPadding((8 * dp).toInt(), 0, (8 * dp).toInt(), 0)
            setOnClickListener {
                onAction("CANCEL", "")
            }
        }

        header.addView(title)
        header.addView(closeBtn)
        root.addView(header)

        // Status / Hint
        val status = TextView(context).apply {
            text = if (hasField) "Listening for your reply..." else "No active chat field detected in $appLabel"
            textSize = 13f
            setTextColor(0xFF94A3B8.toInt())
            setPadding(0, 0, 0, (8 * dp).toInt())
        }
        statusTextView = status
        root.addView(status)

        // Draft Text Box
        val draft = TextView(context).apply {
            text = if (hasField) "Dictate your message now..." else "Please open a chat with a text box open to dictate a reply."
            textSize = 15f
            setTextColor(0xFFF1F5F9.toInt())
            setPadding((12 * dp).toInt(), (10 * dp).toInt(), (12 * dp).toInt(), (10 * dp).toInt())
            background = android.graphics.drawable.GradientDrawable().apply {
                setColor(0x33000000.toInt())
                cornerRadius = 12 * dp
                setStroke((1 * dp).toInt(), 0x22FFFFFF.toInt())
            }
        }
        draftTextView = draft
        root.addView(draft)

        // Buttons Row
        val buttonRow = android.widget.LinearLayout(context).apply {
            orientation = android.widget.LinearLayout.HORIZONTAL
            gravity = Gravity.END
            setPadding(0, (12 * dp).toInt(), 0, 0)
        }

        val cancel = Button(context).apply {
            text = "Cancel"
            textSize = 13f
            setTextColor(0xFF94A3B8.toInt())
            background = null
            setOnClickListener {
                onAction("CANCEL", "")
            }
        }

        val insert = Button(context).apply {
            text = "Insert"
            textSize = 13f
            setTextColor(0xFF0F172A.toInt())
            isEnabled = false
            background = android.graphics.drawable.GradientDrawable().apply {
                setColor(0xFF38BDF8.toInt())
                cornerRadius = 10 * dp
            }
            setOnClickListener {
                onAction("INSERT", draftTextView?.text?.toString() ?: "")
            }
        }
        insertButton = insert

        val send = Button(context).apply {
            text = "Insert & Send"
            textSize = 13f
            setTextColor(0xFFFFFFFF.toInt())
            isEnabled = false
            background = android.graphics.drawable.GradientDrawable().apply {
                setColor(0xFF2563EB.toInt())
                cornerRadius = 10 * dp
            }
            setOnClickListener {
                onAction("SEND", draftTextView?.text?.toString() ?: "")
            }
        }
        sendButton = send

        val lpSpace = android.widget.LinearLayout.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT
        ).apply {
            marginStart = (8 * dp).toInt()
        }

        buttonRow.addView(cancel)
        buttonRow.addView(insert, lpSpace)
        buttonRow.addView(send, lpSpace)

        root.addView(buttonRow)

        return root
    }

    /**
     * Dismisses the floating overlay and stops dictation.
     */
    fun dismissOverlay() {
        mainHandler.post {
            try {
                speechManager?.stopListening()
                speechManager = null
                if (overlayView != null && windowManager != null) {
                    windowManager?.removeView(overlayView)
                    overlayView = null
                }
            } catch (e: Exception) {
                Log.w(TAG, "Error dismissing overlay: ${e.message}")
            }
        }
    }
}
