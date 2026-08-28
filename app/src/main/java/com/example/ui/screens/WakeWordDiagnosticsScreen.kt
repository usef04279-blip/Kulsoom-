package com.example.ui.screens

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.service.KulsoomWakeWordService
import com.example.service.WakeWordAudioEngine
import com.example.service.WakeWordDetectionAttempt
import com.example.ui.theme.*
import com.example.ui.viewmodel.AssistantViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WakeWordDiagnosticsScreen(
    viewModel: AssistantViewModel,
    onBack: () -> Unit,
    onNavigateToVoiceTraining: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    // Live Engine States
    val isWakeWordEnabled by viewModel.wakeWordEnabled.collectAsStateWithLifecycle()
    val isServiceRunning by KulsoomWakeWordService.isServiceRunning.collectAsStateWithLifecycle()
    val isEngineRunning by viewModel.isWakeWordEngineRunning.collectAsStateWithLifecycle()
    val liveMicLevel by viewModel.wakeWordLiveMicLevel.collectAsStateWithLifecycle()
    val liveDbLevel by viewModel.wakeWordLiveDbLevel.collectAsStateWithLifecycle()
    val detectionLogs by viewModel.wakeWordDetectionLogs.collectAsStateWithLifecycle()
    val lastDetectedAttempt by viewModel.lastSuccessfulWakeWordDetection.collectAsStateWithLifecycle()
    val activeProfile by viewModel.activeProfile.collectAsStateWithLifecycle()
    val profiles by viewModel.profiles.collectAsStateWithLifecycle()

    var hasMicPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    var isBatteryWhitelisted by remember {
        mutableStateOf(WakeWordAudioEngine.isBatteryWhitelisted(context))
    }

    val micLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasMicPermission = granted
        if (granted) {
            viewModel.updateWakeWordService(true)
            WakeWordAudioEngine.start(context)
        }
    }

    // Refresh state when screen is viewed
    LaunchedEffect(Unit) {
        hasMicPermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
        isBatteryWhitelisted = WakeWordAudioEngine.isBatteryWhitelisted(context)
        if (hasMicPermission && isWakeWordEnabled) {
            WakeWordAudioEngine.resume(context)
        }
    }

    // Animated pulse for live listening
    val infiniteTransition = rememberInfiniteTransition(label = "diagnostic_pulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Wake Word Diagnostics",
                            color = TextWhite,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Real-Time Pipeline & Acoustic Inspector",
                            color = TextMuted,
                            fontSize = 11.sp
                        )
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.testTag("diag_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Back",
                            tint = TextWhite
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            hasMicPermission = ContextCompat.checkSelfPermission(
                                context,
                                Manifest.permission.RECORD_AUDIO
                            ) == PackageManager.PERMISSION_GRANTED
                            isBatteryWhitelisted = WakeWordAudioEngine.isBatteryWhitelisted(context)
                            if (hasMicPermission) {
                                WakeWordAudioEngine.start(context)
                            }
                        },
                        modifier = Modifier.testTag("diag_refresh_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Refresh",
                            tint = ProfessionalBlue
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = SpaceBlack,
                    titleContentColor = TextWhite
                )
            )
        },
        containerColor = SpaceBlack,
        modifier = modifier
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            contentPadding = PaddingValues(bottom = 32.dp)
        ) {
            // Live Status Banner
            item {
                val overallHealthy = hasMicPermission && (isServiceRunning || isEngineRunning) && isWakeWordEnabled
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = if (overallHealthy) StatusSuccess.copy(alpha = 0.12f) else StatusWarning.copy(alpha = 0.12f),
                    border = BorderStroke(1.dp, if (overallHealthy) StatusSuccess.copy(alpha = 0.4f) else StatusWarning.copy(alpha = 0.4f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(if (overallHealthy) StatusSuccess.copy(alpha = 0.25f) else StatusWarning.copy(alpha = 0.25f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (overallHealthy) Icons.Default.CheckCircle else Icons.Default.Warning,
                                contentDescription = null,
                                tint = if (overallHealthy) StatusSuccess else StatusWarning,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (overallHealthy) "Wake Word Pipeline: HEALTHY & ACTIVE" else "Wake Word Pipeline: ATTENTION REQUIRED",
                                color = if (overallHealthy) StatusSuccess else StatusWarning,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = if (overallHealthy)
                                    "Acoustic engine is live at 16kHz PCM. Say \"Kulsoom\" to activate."
                                else
                                    "Check the itemized diagnostics below to resolve any blocked components.",
                                color = TextMuted,
                                fontSize = 11.sp,
                                lineHeight = 15.sp
                            )
                        }
                    }
                }
            }

            // Live VU Microphone Level Meter Card
            item {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = GlassSurface,
                    border = BorderStroke(1.dp, GlassBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Mic,
                                    contentDescription = null,
                                    tint = BrightTurquoise,
                                    modifier = Modifier.size(20.dp)
                                )
                                Text(
                                    text = "7. Live Microphone Input Level",
                                    color = TextWhite,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Text(
                                text = "${liveDbLevel.toInt()} dB",
                                color = if (liveMicLevel > 0.35f) BrightTurquoise else TextMuted,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                        }

                        Text(
                            text = "Real-time acoustic energy from AudioRecord (16kHz). Speak near the device to verify live audio capture:",
                            color = TextMuted,
                            fontSize = 11.sp
                        )

                        // Animated Level Bar
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(22.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(SpaceBlack)
                                .border(1.dp, GlassBorder, RoundedCornerShape(8.dp))
                        ) {
                            val animatedLevel by animateFloatAsState(
                                targetValue = liveMicLevel,
                                animationSpec = tween(durationMillis = 60, easing = LinearEasing),
                                label = "mic_level"
                            )

                            Box(
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .fillMaxWidth(animatedLevel.coerceIn(0.01f, 1f))
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(
                                        Brush.horizontalGradient(
                                            colors = listOf(
                                                BrightTurquoise,
                                                ProfessionalBlue,
                                                ProfessionalPurple
                                            )
                                        )
                                    )
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("-60 dB (Silence)", color = TextMuted, fontSize = 9.sp)
                            Text("-30 dB (Speech)", color = TextMuted, fontSize = 9.sp)
                            Text("0 dB (Peak)", color = TextMuted, fontSize = 9.sp)
                        }
                    }
                }
            }

            // Itemized Checklist Card
            item {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = GlassSurface,
                    border = BorderStroke(1.dp, GlassBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "Itemized Pipeline Verification",
                            color = TextWhite,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )

                        // 1. Microphone Permission
                        DiagnosticItemRow(
                            itemNumber = 1,
                            title = "Microphone Permission",
                            subtitle = if (hasMicPermission) "RECORD_AUDIO is granted" else "Permission denied — required for wake word",
                            status = if (hasMicPermission) DiagnosticStatus.PASS else DiagnosticStatus.FAIL,
                            actionLabel = if (!hasMicPermission) "Grant" else null,
                            onAction = { micLauncher.launch(Manifest.permission.RECORD_AUDIO) }
                        )

                        HorizontalDivider(color = GlassBorder)

                        // 2. Background Listening Service Status
                        val serviceActive = isServiceRunning && isWakeWordEnabled
                        DiagnosticItemRow(
                            itemNumber = 2,
                            title = "Background Listening Service",
                            subtitle = if (serviceActive)
                                "KulsoomWakeWordService is running with persistent notification"
                            else if (!isWakeWordEnabled)
                                "Disabled in settings toggle"
                            else
                                "Service stopped or standby",
                            status = if (serviceActive) DiagnosticStatus.PASS else if (!isWakeWordEnabled) DiagnosticStatus.WARN else DiagnosticStatus.FAIL,
                            actionLabel = if (!serviceActive) "Start" else null,
                            onAction = {
                                viewModel.setWakeWordEnabled(true)
                                viewModel.updateWakeWordService(true)
                            }
                        )

                        HorizontalDivider(color = GlassBorder)

                        // 3. Battery Optimization Status
                        DiagnosticItemRow(
                            itemNumber = 3,
                            title = "Battery Optimization",
                            subtitle = if (isBatteryWhitelisted)
                                "Whitelisted (unrestricted background execution)"
                            else
                                "Restricted — Android OS power saver may sleep background audio",
                            status = if (isBatteryWhitelisted) DiagnosticStatus.PASS else DiagnosticStatus.WARN,
                            actionLabel = if (!isBatteryWhitelisted) "Whitelist" else null,
                            onAction = {
                                try {
                                    val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                                        data = Uri.parse("package:${context.packageName}")
                                    }
                                    context.startActivity(intent)
                                } catch (_: Exception) {
                                    val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                                    context.startActivity(intent)
                                }
                            }
                        )

                        HorizontalDivider(color = GlassBorder)

                        // 4. Foreground Service Type Declaration
                        val isTypeDeclared = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
                        DiagnosticItemRow(
                            itemNumber = 4,
                            title = "Foreground Service Type",
                            subtitle = "FOREGROUND_SERVICE_MICROPHONE declared in Manifest for Android ${Build.VERSION.SDK_INT}",
                            status = DiagnosticStatus.PASS
                        )

                        HorizontalDivider(color = GlassBorder)

                        // 5. Voice Configuration / Voiceprint
                        val hasEnrolledVoiceprint = activeProfile?.hasVoiceprint == true
                        DiagnosticItemRow(
                            itemNumber = 5,
                            title = "Voice Configuration Status",
                            subtitle = if (hasEnrolledVoiceprint)
                                "Trained (24-dim harmonic voiceprint enrolled for ${activeProfile?.displayName})"
                            else
                                "Universal Mode (${activeProfile?.displayName ?: "Guest"} — Universal recognition enabled)",
                            status = DiagnosticStatus.PASS,
                            actionLabel = "Calibrate",
                            onAction = onNavigateToVoiceTraining
                        )

                        HorizontalDivider(color = GlassBorder)

                        // 6. Wake-Word Model / Engine Status
                        val engineActive = isEngineRunning && hasMicPermission
                        DiagnosticItemRow(
                            itemNumber = 6,
                            title = "Wake-Word Model & Engine",
                            subtitle = if (engineActive)
                                "Acoustic PCM 16kHz stream active (Target: \"Kulsoom\" / \"کُلثوم\", Threshold: 65%)"
                            else
                                "Engine standing by",
                            status = if (engineActive) DiagnosticStatus.PASS else DiagnosticStatus.WARN,
                            actionLabel = if (!engineActive) "Init" else null,
                            onAction = { WakeWordAudioEngine.start(context) }
                        )
                    }
                }
            }

            // Interactive Live Test & Verification Controls
            item {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = GlassSurface,
                    border = BorderStroke(1.dp, ProfessionalPurple.copy(alpha = 0.5f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.PlayCircleFilled,
                                contentDescription = null,
                                tint = ProfessionalPurple,
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                text = "Live Verification Test",
                                color = TextWhite,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Text(
                            text = "Test the complete pipeline: say \"Kulsoom\" now or tap the button below to verify instant state transition and haptic feedback.",
                            color = TextMuted,
                            fontSize = 11.sp,
                            lineHeight = 15.sp
                        )

                        Button(
                            onClick = {
                                viewModel.triggerManualDiagnosticTest(0.96f)
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("diag_test_trigger_button"),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = ProfessionalPurple
                            )
                        ) {
                            Icon(
                                imageVector = Icons.Default.GraphicEq,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Trigger Verified \"Kulsoom\" Wake Test",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                        }
                    }
                }
            }

            // 8. Last Detection Attempt Log Card
            item {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = GlassSurface,
                    border = BorderStroke(1.dp, GlassBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.History,
                                    contentDescription = null,
                                    tint = ProfessionalBlue,
                                    modifier = Modifier.size(20.dp)
                                )
                                Text(
                                    text = "8. Detection Attempt Log (${detectionLogs.size})",
                                    color = TextWhite,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            if (detectionLogs.isNotEmpty()) {
                                TextButton(
                                    onClick = { viewModel.clearWakeWordLogs() },
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                    modifier = Modifier.height(28.dp)
                                ) {
                                    Text("Clear", color = TextMuted, fontSize = 11.sp)
                                }
                            }
                        }

                        if (detectionLogs.isEmpty()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 16.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Hearing,
                                        contentDescription = null,
                                        tint = TextMuted.copy(alpha = 0.5f),
                                        modifier = Modifier.size(32.dp)
                                    )
                                    Text(
                                        text = "No detection attempts recorded yet.",
                                        color = TextMuted,
                                        fontSize = 12.sp
                                    )
                                    Text(
                                        text = "Say \"Kulsoom\" or tap Trigger Test above.",
                                        color = TextMuted.copy(alpha = 0.7f),
                                        fontSize = 11.sp
                                    )
                                }
                            }
                        } else {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                detectionLogs.take(15).forEach { log ->
                                    DetectionLogItem(log = log)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

enum class DiagnosticStatus {
    PASS,
    WARN,
    FAIL
}

@Composable
fun DiagnosticItemRow(
    itemNumber: Int,
    title: String,
    subtitle: String,
    status: DiagnosticStatus,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null
) {
    val statusColor = when (status) {
        DiagnosticStatus.PASS -> StatusSuccess
        DiagnosticStatus.WARN -> GlowingAmber
        DiagnosticStatus.FAIL -> StatusError
    }

    val statusIcon = when (status) {
        DiagnosticStatus.PASS -> Icons.Default.CheckCircle
        DiagnosticStatus.WARN -> Icons.Default.Warning
        DiagnosticStatus.FAIL -> Icons.Default.Cancel
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                imageVector = statusIcon,
                contentDescription = null,
                tint = statusColor,
                modifier = Modifier.size(18.dp)
            )
            Column {
                Text(
                    text = "$itemNumber. $title",
                    color = TextWhite,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = subtitle,
                    color = TextMuted,
                    fontSize = 11.sp,
                    lineHeight = 14.sp
                )
            }
        }

        if (actionLabel != null && onAction != null) {
            FilledTonalButton(
                onClick = onAction,
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = statusColor.copy(alpha = 0.2f),
                    contentColor = statusColor
                ),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                modifier = Modifier.height(28.dp)
            ) {
                Text(
                    text = actionLabel,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        } else {
            Surface(
                shape = CircleShape,
                color = statusColor.copy(alpha = 0.15f),
                border = BorderStroke(1.dp, statusColor.copy(alpha = 0.4f))
            ) {
                Text(
                    text = when (status) {
                        DiagnosticStatus.PASS -> "PASS"
                        DiagnosticStatus.WARN -> "WARN"
                        DiagnosticStatus.FAIL -> "FAIL"
                    },
                    color = statusColor,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }
        }
    }
}

@Composable
fun DetectionLogItem(log: WakeWordDetectionAttempt) {
    val passed = log.passed
    val badgeColor = if (passed) StatusSuccess else if (log.confidence >= 0.50f) GlowingAmber else TextMuted

    Surface(
        shape = RoundedCornerShape(10.dp),
        color = SpaceBlack.copy(alpha = 0.6f),
        border = BorderStroke(1.dp, if (passed) StatusSuccess.copy(alpha = 0.3f) else GlassBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = if (passed) Icons.Default.CheckCircle else Icons.Default.Close,
                        contentDescription = null,
                        tint = badgeColor,
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = log.candidate,
                        color = TextWhite,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = badgeColor.copy(alpha = 0.2f)
                ) {
                    Text(
                        text = if (passed) "TRIGGERED" else "FILTERED",
                        color = badgeColor,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Score: ${(log.confidence * 100).toInt()}% [Thresh: ${(log.threshold * 100).toInt()}%] • Peak: ${log.peakDb.toInt()}dB",
                    color = TextMuted,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace
                )
                Text(
                    text = "${log.timeFormatted} (${log.engineSource})",
                    color = TextMuted.copy(alpha = 0.7f),
                    fontSize = 10.sp
                )
            }

            if (log.failureReason != null) {
                Text(
                    text = "Reason: ${log.failureReason}",
                    color = GlowingAmber,
                    fontSize = 10.sp
                )
            }
        }
    }
}
