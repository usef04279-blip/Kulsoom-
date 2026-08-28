package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.service.WakeWordAudioEngine
import com.example.service.WakeWordDetectionAttempt
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class WakeWordDiagnosticsTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        WakeWordAudioEngine.clearLogs()
    }

    @Test
    fun testWakeWordEngineDiagnosticTrigger() {
        var triggeredConfidence = 0f
        WakeWordAudioEngine.registerTriggerCallback { confidence ->
            triggeredConfidence = confidence
        }

        WakeWordAudioEngine.triggerManualDiagnosticTest(0.92f)

        assertEquals(0.92f, triggeredConfidence, 0.01f)
        val logs = WakeWordAudioEngine.detectionLogs.value
        assertTrue("Expected logs to not be empty after diagnostic trigger", logs.isNotEmpty())

        val latest = logs.first()
        assertTrue(latest.passed)
        assertEquals(0.92f, latest.confidence, 0.01f)
        assertEquals("Kulsoom (Diagnostic)", latest.candidate)
    }

    @Test
    fun testRecordDetectionAttemptsAndClear() {
        WakeWordAudioEngine.recordDetectionAttempt(
            WakeWordDetectionAttempt(
                candidate = "Kulsoom",
                confidence = 0.88f,
                threshold = 0.65f,
                passed = true,
                peakRms = 1800f,
                peakDb = -18f,
                engineSource = "Acoustic PCM Engine"
            )
        )

        WakeWordAudioEngine.recordDetectionAttempt(
            WakeWordDetectionAttempt(
                candidate = "Random speech",
                confidence = 0.22f,
                threshold = 0.65f,
                passed = false,
                peakRms = 800f,
                peakDb = -32f,
                failureReason = "Below threshold",
                engineSource = "Acoustic PCM Engine"
            )
        )

        assertEquals(2, WakeWordAudioEngine.detectionLogs.value.size)
        assertTrue(WakeWordAudioEngine.detectionLogs.value[0].passed)
        assertFalse(WakeWordAudioEngine.detectionLogs.value[1].passed)

        WakeWordAudioEngine.clearLogs()
        assertEquals(0, WakeWordAudioEngine.detectionLogs.value.size)
    }

    @Test
    fun testConsecutiveWakeWordDetections() {
        var triggerCount = 0
        WakeWordAudioEngine.registerTriggerCallback {
            triggerCount++
        }

        for (i in 1..5) {
            WakeWordAudioEngine.triggerManualDiagnosticTest(0.90f + (i * 0.01f))
        }

        assertEquals(5, triggerCount)
        assertEquals(5, WakeWordAudioEngine.detectionLogs.value.size)
    }
}
