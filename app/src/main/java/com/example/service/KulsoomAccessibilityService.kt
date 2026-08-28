package com.example.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.provider.Settings
import android.text.TextUtils
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

class KulsoomAccessibilityService : AccessibilityService() {

    companion object {
        private const val TAG = "KulsoomAccessibility"
        var instance: KulsoomAccessibilityService? = null
            private set

        /**
         * Checks if the Accessibility Service is enabled in Android system settings.
         */
        fun isServiceEnabled(context: Context): Boolean {
            val expectedComponentName = "${context.packageName}/${KulsoomAccessibilityService::class.java.name}"
            val enabledServicesSetting = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            ) ?: return false

            val colonSplitter = TextUtils.SimpleStringSplitter(':')
            colonSplitter.setString(enabledServicesSetting)
            while (colonSplitter.hasNext()) {
                val componentName = colonSplitter.next()
                if (componentName.equals(expectedComponentName, ignoreCase = true) ||
                    componentName.endsWith(KulsoomAccessibilityService::class.java.simpleName)
                ) {
                    return true
                }
            }
            return false
        }
    }

    var currentPackageName: String = ""
        private set

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        Log.d(TAG, "KulsoomAccessibilityService connected")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        event.packageName?.let {
            currentPackageName = it.toString()
        }
    }

    override fun onInterrupt() {
        Log.w(TAG, "KulsoomAccessibilityService interrupted")
    }

    override fun onDestroy() {
        super.onDestroy()
        if (instance == this) {
            instance = null
        }
    }

    /**
     * Finds the currently focused or active editable text node (e.g. WhatsApp or SMS reply box).
     */
    fun findFocusedEditableNode(): AccessibilityNodeInfo? {
        val root = rootInActiveWindow ?: return null
        return findEditableRecursively(root)
    }

    private fun findEditableRecursively(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        // Prefer focused editable node
        if (node.isFocused && (node.isEditable || isEditTextClass(node))) {
            return node
        }

        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val found = findEditableRecursively(child)
            if (found != null) return found
        }

        // Secondary fallback: Any editable node if none is strictly focused
        if (node.isEditable || isEditTextClass(node)) {
            return node
        }

        return null
    }

    private fun isEditTextClass(node: AccessibilityNodeInfo): Boolean {
        val className = node.className?.toString() ?: ""
        return className.contains("EditText", ignoreCase = true) ||
                className.contains("TextBox", ignoreCase = true) ||
                node.isEditable
    }

    /**
     * Inserts text directly into the focused field in the current app.
     */
    fun insertTextIntoFocusedNode(text: String): Boolean {
        val targetNode = findFocusedEditableNode() ?: return false

        // Primary method: ACTION_SET_TEXT
        val arguments = Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
        }
        val success = targetNode.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments)
        if (success) {
            Log.d(TAG, "Successfully inserted text via ACTION_SET_TEXT into $currentPackageName")
            return true
        }

        // Fallback method: Clipboard paste
        try {
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            val clip = ClipData.newPlainText("Kulsoom Dictation", text)
            clipboard?.setPrimaryClip(clip)

            targetNode.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
            val pasteSuccess = targetNode.performAction(AccessibilityNodeInfo.ACTION_PASTE)
            if (pasteSuccess) {
                Log.d(TAG, "Successfully inserted text via ACTION_PASTE into $currentPackageName")
                return true
            }
        } catch (e: Exception) {
            Log.w(TAG, "Paste fallback failed: ${e.message}")
        }

        return false
    }

    /**
     * Clicks the send button in the current chat app (only with explicit confirmation).
     */
    fun clickSendButton(): Boolean {
        val root = rootInActiveWindow ?: return false
        val sendNode = findSendButtonRecursively(root) ?: return false
        val clicked = sendNode.performAction(AccessibilityNodeInfo.ACTION_CLICK)
        Log.d(TAG, "Click send button result: $clicked in $currentPackageName")
        return clicked
    }

    private fun findSendButtonRecursively(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        val desc = node.contentDescription?.toString()?.lowercase() ?: ""
        val text = node.text?.toString()?.lowercase() ?: ""
        val viewId = node.viewIdResourceName?.lowercase() ?: ""

        val isSend = desc.contains("send") || desc.contains("ارسال") || desc.contains("bhejo") ||
                text.contains("send") || text.contains("ارسال") ||
                viewId.contains("send_button") || viewId.contains("sendbutton") ||
                viewId.contains("send_message") || viewId.contains("composer_send") ||
                viewId.contains("conversation_send")

        if (isSend && (node.isClickable || node.parent?.isClickable == true)) {
            return if (node.isClickable) node else node.parent
        }

        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val found = findSendButtonRecursively(child)
            if (found != null) return found
        }
        return null
    }

    /**
     * Returns a human-friendly name of the current foreground app (e.g., "WhatsApp", "Messages").
     */
    fun getCurrentAppLabel(): String {
        return try {
            val pm = packageManager
            val appInfo = pm.getApplicationInfo(currentPackageName, 0)
            pm.getApplicationLabel(appInfo).toString()
        } catch (_: Exception) {
            when {
                currentPackageName.contains("whatsapp") -> "WhatsApp"
                currentPackageName.contains("messaging") || currentPackageName.contains("mms") || currentPackageName.contains("sms") -> "Messages"
                currentPackageName.contains("facebook.orca") -> "Messenger"
                currentPackageName.contains("telegram") -> "Telegram"
                else -> "Chat App"
            }
        }
    }
}
