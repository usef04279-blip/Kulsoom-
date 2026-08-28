package com.example.util

import android.content.Context
import android.util.Log
import com.google.firebase.FirebaseApp
import com.google.firebase.crashlytics.FirebaseCrashlytics

object CrashReporter {

    private const val TAG = "KulsoomCrashReporter"
    private const val PREFS_NAME = "kulsoom_settings"
    private const val KEY_CRASH_REPORTING_ENABLED = "crash_reporting_enabled"

    private var isCrashlyticsAvailable = false
    private var isEnabled = true

    fun init(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        isEnabled = prefs.getBoolean(KEY_CRASH_REPORTING_ENABLED, true)

        try {
            if (FirebaseApp.getApps(context).isNotEmpty()) {
                val crashlytics = FirebaseCrashlytics.getInstance()
                crashlytics.setCrashlyticsCollectionEnabled(isEnabled)
                crashlytics.setCustomKey("app_name", "Kulsoom AI")
                crashlytics.setCustomKey("app_version", "1.0")
                isCrashlyticsAvailable = true
                Log.d(TAG, "Firebase Crashlytics initialized successfully (Collection enabled: $isEnabled)")
            } else {
                Log.d(TAG, "FirebaseApp not initialized, using local fallback logging")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Crashlytics setup deferred or unavailable: ${e.message}")
        }
    }

    fun isCrashReportingEnabled(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_CRASH_REPORTING_ENABLED, true)
    }

    fun setCrashReportingEnabled(context: Context, enabled: Boolean) {
        isEnabled = enabled
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_CRASH_REPORTING_ENABLED, enabled)
            .apply()

        try {
            if (isCrashlyticsAvailable) {
                FirebaseCrashlytics.getInstance().setCrashlyticsCollectionEnabled(enabled)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Could not update Crashlytics collection state: ${e.message}")
        }
    }

    /**
     * Records a technical breadcrumb for diagnosing issues before crashes or failures.
     * Note: NEVER pass personal text, contact names, or user messages to this function.
     */
    fun logBreadcrumb(feature: String, technicalAction: String) {
        if (!isEnabled) return
        val breadcrumb = "[$feature] $technicalAction"
        Log.d(TAG, "Breadcrumb: $breadcrumb")
        try {
            if (isCrashlyticsAvailable) {
                val crashlytics = FirebaseCrashlytics.getInstance()
                crashlytics.log(breadcrumb)
                crashlytics.setCustomKey("last_active_feature", feature)
            }
        } catch (_: Exception) {}
    }

    /**
     * Records handled non-fatal exceptions (e.g. Gemini network timeouts, TTS init failures,
     * Service start issues) with safe technical context.
     */
    fun recordNonFatal(throwable: Throwable, technicalFeatureTag: String = "") {
        if (!isEnabled) return
        Log.e(TAG, "Non-fatal exception in $technicalFeatureTag: ${throwable.message}", throwable)
        try {
            if (isCrashlyticsAvailable) {
                val crashlytics = FirebaseCrashlytics.getInstance()
                if (technicalFeatureTag.isNotBlank()) {
                    crashlytics.setCustomKey("error_feature", technicalFeatureTag)
                }
                crashlytics.recordException(throwable)
            }
        } catch (_: Exception) {}
    }

    /**
     * Sets a sanitized custom key for crash context.
     */
    fun setTechnicalState(key: String, value: String) {
        if (!isEnabled) return
        try {
            if (isCrashlyticsAvailable) {
                FirebaseCrashlytics.getInstance().setCustomKey(key, value)
            }
        } catch (_: Exception) {}
    }
}
