package com.example.service

import android.app.SearchManager
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.net.Uri
import android.os.BatteryManager
import android.os.Build
import android.provider.AlarmClock
import android.provider.CalendarContract
import android.provider.ContactsContract
import android.telephony.SmsManager
import android.widget.Toast
import com.example.data.model.ParsedIntent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Calendar
import java.util.Locale
import kotlin.math.min

sealed class ActionResult {
    data class Success(val message: String) : ActionResult()
    data class Failure(val reason: String) : ActionResult()
    data class NeedsConfirmation(val description: String, val pendingIntent: ParsedIntent) : ActionResult()
}

class DeviceActionHandler(private val context: Context) {

    private val packageManager: PackageManager = context.packageManager
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
    private val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as? CameraManager

    // Cache of installed applications: label -> packageName
    private var installedAppsMap: Map<String, String> = emptyMap()

    init {
        refreshInstalledApps()
    }

    fun refreshInstalledApps() {
        try {
            val flags = PackageManager.GET_META_DATA
            val apps = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                packageManager.getInstalledApplications(PackageManager.ApplicationInfoFlags.of(flags.toLong()))
            } else {
                @Suppress("DEPRECATION")
                packageManager.getInstalledApplications(flags)
            }

            val map = mutableMapOf<String, String>()
            for (app in apps) {
                // Filter launchable apps
                val launchIntent = packageManager.getLaunchIntentForPackage(app.packageName)
                if (launchIntent != null) {
                    val label = packageManager.getApplicationLabel(app).toString().lowercase(Locale.ROOT)
                    map[label] = app.packageName
                }
            }
            installedAppsMap = map
        } catch (e: Exception) {
            installedAppsMap = emptyMap()
        }
    }

    suspend fun executeAction(intent: ParsedIntent, skipConfirmation: Boolean = false): ActionResult = withContext(Dispatchers.IO) {
        try {
            when (intent.intent.uppercase(Locale.ROOT)) {
                "OPEN_APP" -> handleOpenApp(intent.appName ?: "")
                "MAKE_CALL" -> {
                    if (intent.requiresConfirmation && !skipConfirmation) {
                        ActionResult.NeedsConfirmation("Call ${intent.contactName ?: intent.phoneNumber}?", intent)
                    } else {
                        handleMakeCall(intent.contactName, intent.phoneNumber)
                    }
                }
                "SEND_SMS" -> {
                    if (intent.requiresConfirmation && !skipConfirmation) {
                        ActionResult.NeedsConfirmation("Send SMS to ${intent.contactName ?: "recipient"}: '${intent.messageText}'?", intent)
                    } else {
                        handleSendSms(intent.contactName, intent.phoneNumber, intent.messageText ?: "")
                    }
                }
                "SET_ALARM" -> handleSetAlarm(intent.alarmHour ?: 7, intent.alarmMinute ?: 0, intent.alarmLabel ?: "Kulsoom Alarm")
                "SET_TIMER" -> handleSetTimer(intent.timerSeconds ?: 300, intent.alarmLabel ?: "Kulsoom Timer")
                "TOGGLE_FLASHLIGHT" -> handleToggleFlashlight(intent.flashlightState ?: "toggle")
                "ADJUST_VOLUME" -> handleAdjustVolume(intent.volumeDirection ?: "up")
                "GET_BATTERY" -> handleGetBattery()
                "GET_TIME_DATE" -> handleGetTimeDate()
                "PLAY_MUSIC" -> handlePlayMusic(intent.musicQuery ?: "")
                "WEB_SEARCH" -> handleWebSearch(intent.searchQuery ?: "")
                "CREATE_CALENDAR_EVENT" -> handleCreateCalendarEvent(intent.calendarTitle ?: "Meeting", intent.calendarMinutesFromNow ?: 60)
                "CALCULATE" -> handleCalculate(intent.calculationExpression ?: intent.spokenResponse)
                "SET_DND" -> handleSetDnd(intent.flashlightState ?: "toggle")
                "ADJUST_BRIGHTNESS" -> handleAdjustBrightness(intent.volumeDirection ?: "up")
                else -> ActionResult.Success(intent.spokenResponse.ifBlank { "Action completed" })
            }
        } catch (e: Exception) {
            ActionResult.Failure("Failed to perform action: ${e.message}")
        }
    }

    // Section 6 Fix: Reliable Fuzzy App Launcher
    private fun handleOpenApp(rawAppName: String): ActionResult {
        if (rawAppName.isBlank()) {
            return ActionResult.Failure("Please specify the name of the app to open.")
        }
        val target = rawAppName.trim().lowercase(Locale.ROOT)
        refreshInstalledApps()

        // 1. Direct exact match
        var matchedPackage = installedAppsMap[target]

        // 2. Contains match
        if (matchedPackage == null) {
            matchedPackage = installedAppsMap.entries.firstOrNull { (label, _) ->
                label.contains(target) || target.contains(label)
            }?.value
        }

        // 3. Normalized / Spacing variations (e.g. "whats app" -> "whatsapp", "in sta gram" -> "instagram")
        if (matchedPackage == null) {
            val strippedTarget = target.replace(" ", "").replace("-", "")
            matchedPackage = installedAppsMap.entries.firstOrNull { (label, _) ->
                val strippedLabel = label.replace(" ", "").replace("-", "")
                strippedLabel == strippedTarget || strippedLabel.contains(strippedTarget) || strippedTarget.contains(strippedLabel)
            }?.value
        }

        // 4. Common known aliases
        if (matchedPackage == null) {
            val aliasMap = mapOf(
                "whatsapp" to "com.whatsapp",
                "youtube" to "com.google.android.youtube",
                "chrome" to "com.android.chrome",
                "browser" to "com.android.chrome",
                "spotify" to "com.spotify.music",
                "settings" to "com.android.settings",
                "camera" to "com.google.android.GoogleCamera",
                "maps" to "com.google.android.apps.maps",
                "gmail" to "com.google.android.gm",
                "play store" to "com.android.vending",
                "playstore" to "com.android.vending",
                "instagram" to "com.instagram.android",
                "facebook" to "com.facebook.katana",
                "telegram" to "org.telegram.messenger",
                "calculator" to "com.google.android.calculator",
                "clock" to "com.google.android.deskclock"
            )
            val mapped = aliasMap[target]
            if (mapped != null && isPackageInstalled(mapped)) {
                matchedPackage = mapped
            }
        }

        // 5. Levenshtein fuzzy distance matching as final fallback
        if (matchedPackage == null) {
            var bestDistance = Int.MAX_VALUE
            var bestPkg: String? = null
            for ((label, pkg) in installedAppsMap) {
                val dist = levenshteinDistance(target, label)
                if (dist < bestDistance && dist <= 3) {
                    bestDistance = dist
                    bestPkg = pkg
                }
            }
            matchedPackage = bestPkg
        }

        if (matchedPackage == null) {
            return ActionResult.Failure("I couldn't find an app called '$rawAppName' on your phone.")
        }

        return try {
            val launchIntent = packageManager.getLaunchIntentForPackage(matchedPackage)
            if (launchIntent != null) {
                launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(launchIntent)
                ActionResult.Success("Opening $rawAppName")
            } else {
                ActionResult.Failure("Unable to launch $rawAppName.")
            }
        } catch (e: Exception) {
            ActionResult.Failure("Could not open $rawAppName: ${e.message}")
        }
    }

    private fun isPackageInstalled(packageName: String): Boolean {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                packageManager.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                packageManager.getPackageInfo(packageName, 0)
            }
            true
        } catch (e: Exception) {
            false
        }
    }

    private fun handleMakeCall(contactName: String?, rawPhoneNumber: String?): ActionResult {
        var phoneNumber = rawPhoneNumber
        if (phoneNumber.isNullOrBlank() && !contactName.isNullOrBlank()) {
            phoneNumber = resolveContactNumber(contactName)
        }

        return if (!phoneNumber.isNullOrBlank()) {
            val callIntent = Intent(Intent.ACTION_DIAL).apply {
                data = Uri.parse("tel:$phoneNumber")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(callIntent)
            ActionResult.Success("Dialing $phoneNumber${if (!contactName.isNullOrBlank()) " ($contactName)" else ""}")
        } else {
            // Open dialer directly
            val dialIntent = Intent(Intent.ACTION_DIAL).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(dialIntent)
            ActionResult.Success("Opened phone dialer for ${contactName ?: "call"}")
        }
    }

    private fun resolveContactNumber(contactName: String): String? {
        return try {
            val cursor = context.contentResolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                arrayOf(
                    ContactsContract.CommonDataKinds.Phone.NUMBER,
                    ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME
                ),
                "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} LIKE ?",
                arrayOf("%$contactName%"),
                null
            )
            cursor?.use {
                if (it.moveToFirst()) {
                    val numIndex = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                    if (numIndex != -1) it.getString(numIndex) else null
                } else null
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun handleSendSms(contactName: String?, rawPhoneNumber: String?, message: String): ActionResult {
        var phoneNumber = rawPhoneNumber
        if (phoneNumber.isNullOrBlank() && !contactName.isNullOrBlank()) {
            phoneNumber = resolveContactNumber(contactName)
        }

        val sendIntent = Intent(Intent.ACTION_SENDTO).apply {
            data = Uri.parse("smsto:${phoneNumber ?: ""}")
            putExtra("sms_body", message)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(sendIntent)
        return ActionResult.Success("Opened SMS to ${contactName ?: phoneNumber ?: "contact"}: '$message'")
    }

    private fun handleSetAlarm(hour: Int, minute: Int, label: String): ActionResult {
        val alarmIntent = Intent(AlarmClock.ACTION_SET_ALARM).apply {
            putExtra(AlarmClock.EXTRA_HOUR, hour)
            putExtra(AlarmClock.EXTRA_MINUTES, minute)
            putExtra(AlarmClock.EXTRA_MESSAGE, label)
            putExtra(AlarmClock.EXTRA_SKIP_UI, true)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return try {
            context.startActivity(alarmIntent)
            ActionResult.Success("Alarm set for ${String.format("%02d:%02d", hour, minute)}")
        } catch (e: Exception) {
            ActionResult.Failure("Could not set alarm: ${e.message}")
        }
    }

    private fun handleSetTimer(seconds: Int, label: String): ActionResult {
        val timerIntent = Intent(AlarmClock.ACTION_SET_TIMER).apply {
            putExtra(AlarmClock.EXTRA_LENGTH, seconds)
            putExtra(AlarmClock.EXTRA_MESSAGE, label)
            putExtra(AlarmClock.EXTRA_SKIP_UI, true)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return try {
            context.startActivity(timerIntent)
            ActionResult.Success("Timer started for ${seconds / 60} minutes and ${seconds % 60} seconds")
        } catch (e: Exception) {
            ActionResult.Failure("Could not start timer: ${e.message}")
        }
    }

    private var isTorchOn = false
    private fun handleToggleFlashlight(state: String): ActionResult {
        if (cameraManager == null) return ActionResult.Failure("Flashlight not available on this device.")
        return try {
            val cameraId = cameraManager.cameraIdList.firstOrNull { id ->
                val chars = cameraManager.getCameraCharacteristics(id)
                chars.get(android.hardware.camera2.CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
            } ?: cameraManager.cameraIdList.firstOrNull()

            if (cameraId != null) {
                isTorchOn = when (state.lowercase(Locale.ROOT)) {
                    "on" -> true
                    "off" -> false
                    else -> !isTorchOn
                }
                cameraManager.setTorchMode(cameraId, isTorchOn)
                ActionResult.Success("Flashlight turned ${if (isTorchOn) "ON" else "OFF"}")
            } else {
                ActionResult.Failure("Flashlight hardware not found.")
            }
        } catch (e: Exception) {
            ActionResult.Failure("Flashlight toggle error: ${e.message}")
        }
    }

    private fun handleAdjustVolume(direction: String): ActionResult {
        if (audioManager == null) return ActionResult.Failure("Audio manager unavailable.")
        return try {
            when (direction.lowercase(Locale.ROOT)) {
                "up" -> audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_RAISE, AudioManager.FLAG_SHOW_UI)
                "down" -> audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_LOWER, AudioManager.FLAG_SHOW_UI)
                "mute" -> audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_MUTE, AudioManager.FLAG_SHOW_UI)
                "max" -> {
                    val max = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                    audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, max, AudioManager.FLAG_SHOW_UI)
                }
                else -> audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_RAISE, AudioManager.FLAG_SHOW_UI)
            }
            ActionResult.Success("Volume adjusted ($direction)")
        } catch (e: Exception) {
            ActionResult.Failure("Volume control error: ${e.message}")
        }
    }

    private fun handleGetBattery(): ActionResult {
        val batteryIntent = context.registerReceiver(null, android.content.IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = batteryIntent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = batteryIntent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        val status = batteryIntent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL

        val pct = if (level >= 0 && scale > 0) (level * 100 / scale) else 75
        val chargingStatus = if (isCharging) "and currently charging ⚡" else "and discharging"
        return ActionResult.Success("Your battery level is $pct% $chargingStatus.")
    }

    private fun handleGetTimeDate(): ActionResult {
        val cal = Calendar.getInstance()
        val timeFormat = java.text.SimpleDateFormat("h:mm a", Locale.getDefault()).format(cal.time)
        val dateFormat = java.text.SimpleDateFormat("EEEE, MMMM d, yyyy", Locale.getDefault()).format(cal.time)
        return ActionResult.Success("It's $timeFormat on $dateFormat.")
    }

    private fun handlePlayMusic(query: String): ActionResult {
        val intent = Intent(android.provider.MediaStore.INTENT_ACTION_MEDIA_PLAY_FROM_SEARCH).apply {
            putExtra(SearchManager.QUERY, query)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return try {
            context.startActivity(intent)
            ActionResult.Success("Playing '$query'")
        } catch (e: Exception) {
            // Fallback to youtube or browser search
            val webIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/results?search_query=${Uri.encode(query)}")).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(webIntent)
            ActionResult.Success("Searching music for '$query'")
        }
    }

    private fun handleWebSearch(query: String): ActionResult {
        val intent = Intent(Intent.ACTION_WEB_SEARCH).apply {
            putExtra(SearchManager.QUERY, query)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return try {
            context.startActivity(intent)
            ActionResult.Success("Searching for '$query'")
        } catch (e: Exception) {
            val urlIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/search?q=${Uri.encode(query)}")).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(urlIntent)
            ActionResult.Success("Opened Google Search for '$query'")
        }
    }

    private fun handleCreateCalendarEvent(title: String, minutesFromNow: Int): ActionResult {
        val startTime = Calendar.getInstance().apply {
            add(Calendar.MINUTE, minutesFromNow)
        }.timeInMillis
        val endTime = startTime + (60 * 60 * 1000)

        val intent = Intent(Intent.ACTION_INSERT).apply {
            data = CalendarContract.Events.CONTENT_URI
            putExtra(CalendarContract.Events.TITLE, title)
            putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, startTime)
            putExtra(CalendarContract.EXTRA_EVENT_END_TIME, endTime)
            putExtra(CalendarContract.Events.DESCRIPTION, "Created by Kulsoom AI Assistant")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return try {
            context.startActivity(intent)
            ActionResult.Success("Opening calendar to add event: $title")
        } catch (e: Exception) {
            ActionResult.Failure("Could not open calendar: ${e.message}")
        }
    }

    private fun handleCalculate(expression: String): ActionResult {
        val mathResult = com.example.util.MathEvaluator.evaluate(expression)
        if (mathResult != null) {
            return ActionResult.Success(mathResult.formattedAnswer)
        }
        val clean = expression.replace("calculate", "").replace("what is", "").replace("what's", "").trim()
        val result = try {
            evalMath(clean)
        } catch (e: Exception) {
            null
        }
        return if (result != null) {
            val formatted = if (result % 1.0 == 0.0) result.toLong().toString() else String.format(Locale.US, "%.2f", result)
            ActionResult.Success("$clean = $formatted")
        } else {
            ActionResult.Success("Calculated: $clean")
        }
    }

    private fun handleSetDnd(state: String): ActionResult {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? android.app.NotificationManager
        return try {
            val isOff = state.equals("off", ignoreCase = true)
            val isOn = state.equals("on", ignoreCase = true)
            if (notificationManager != null && notificationManager.isNotificationPolicyAccessGranted) {
                val targetFilter = if (isOff) {
                    android.app.NotificationManager.INTERRUPTION_FILTER_ALL
                } else {
                    android.app.NotificationManager.INTERRUPTION_FILTER_PRIORITY
                }
                notificationManager.setInterruptionFilter(targetFilter)
                ActionResult.Success("Do Not Disturb turned ${if (isOff) "OFF" else "ON"}")
            } else {
                // Fallback using AudioManager ringer mode
                if (audioManager != null) {
                    val mode = if (isOff) AudioManager.RINGER_MODE_NORMAL else AudioManager.RINGER_MODE_SILENT
                    audioManager.ringerMode = mode
                    ActionResult.Success("Silent / Do Not Disturb mode turned ${if (isOff) "OFF" else "ON"}")
                } else {
                    val intent = Intent(android.provider.Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(intent)
                    ActionResult.Success("Opened Do Not Disturb settings")
                }
            }
        } catch (e: Exception) {
            ActionResult.Failure("Could not set Do Not Disturb: ${e.message}")
        }
    }

    private fun handleAdjustBrightness(direction: String): ActionResult {
        return try {
            val intent = Intent(android.provider.Settings.ACTION_DISPLAY_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            ActionResult.Success("Opened display brightness settings ($direction)")
        } catch (e: Exception) {
            ActionResult.Failure("Could not open brightness settings: ${e.message}")
        }
    }

    private fun evalMath(str: String): Double? {
        val s = str.replace("x", "*").replace("times", "*").replace("divided by", "/").replace("plus", "+").replace("minus", "-")
        // Check simple binary operation
        val regex = Regex("([0-9.]+)\\s*([+\\-*/%])\\s*([0-9.]+)")
        val match = regex.find(s) ?: return null
        val a = match.groupValues[1].toDoubleOrNull() ?: return null
        val op = match.groupValues[2]
        val b = match.groupValues[3].toDoubleOrNull() ?: return null
        return when (op) {
            "+" -> a + b
            "-" -> a - b
            "*" -> a * b
            "/" -> if (b != 0.0) a / b else 0.0
            "%" -> (a * b) / 100.0
            else -> null
        }
    }

    private fun levenshteinDistance(s1: String, s2: String): Int {
        val dp = Array(s1.length + 1) { IntArray(s2.length + 1) }
        for (i in 0..s1.length) dp[i][0] = i
        for (j in 0..s2.length) dp[0][j] = j
        for (i in 1..s1.length) {
            for (j in 1..s2.length) {
                val cost = if (s1[i - 1] == s2[j - 1]) 0 else 1
                dp[i][j] = min(min(dp[i - 1][j] + 1, dp[i][j - 1] + 1), dp[i - 1][j - 1] + cost)
            }
        }
        return dp[s1.length][s2.length]
    }
}
