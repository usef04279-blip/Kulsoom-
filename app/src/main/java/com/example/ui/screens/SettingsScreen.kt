package com.example.ui.screens

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import android.speech.tts.TextToSpeech
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.UserProfile
import com.example.service.KulsoomAccessibilityService
import com.example.service.KulsoomWakeWordService
import com.example.service.UrduVoiceStatus
import com.example.ui.components.CreateOrEditProfileDialog
import com.example.ui.components.ProfileAvatarBadge
import com.example.ui.components.VoiceTrainingDialog
import com.example.ui.theme.*
import com.example.ui.viewmodel.AssistantViewModel

@Composable
fun SettingsScreen(
    viewModel: AssistantViewModel,
    modifier: Modifier = Modifier,
    onNavigateToOnboarding: () -> Unit = {},
    onNavigateToMemory: () -> Unit = {},
    onNavigateToDiagnostics: () -> Unit = {}
) {
    val context = LocalContext.current

    val wakeWordEnabled by viewModel.wakeWordEnabled.collectAsStateWithLifecycle()
    val lockScreenResponseEnabled by viewModel.lockScreenResponseEnabled.collectAsStateWithLifecycle()
    val trustedQuickActions by viewModel.trustedQuickActions.collectAsStateWithLifecycle()
    val variedResponsesEnabled by viewModel.variedResponsesEnabled.collectAsStateWithLifecycle()
    val offerDailyBriefingMorning by viewModel.offerDailyBriefingMorning.collectAsStateWithLifecycle()
    val continuousConversationEnabled by viewModel.continuousConversationEnabled.collectAsStateWithLifecycle()
    val allowInterruptionsEnabled by viewModel.allowInterruptionsEnabled.collectAsStateWithLifecycle()
    val inAppReplyEnabled by viewModel.inAppReplyEnabled.collectAsStateWithLifecycle()
    val selectedLanguage by viewModel.selectedLanguage.collectAsStateWithLifecycle()
    val ttsPitch by viewModel.ttsPitch.collectAsStateWithLifecycle()
    val ttsSpeed by viewModel.ttsSpeed.collectAsStateWithLifecycle()
    val profiles by viewModel.profiles.collectAsStateWithLifecycle()
    val activeProfile by viewModel.activeProfile.collectAsStateWithLifecycle()
    val voiceTrainingState by viewModel.voiceTrainingState.collectAsStateWithLifecycle()
    val soundLevel by viewModel.soundLevel.collectAsStateWithLifecycle()
    val isWakeWordServiceRunning by viewModel.isWakeWordServiceRunning.collectAsStateWithLifecycle()
    val urduVoiceStatus by viewModel.urduVoiceStatus.collectAsStateWithLifecycle()
    val crashReportingEnabled by viewModel.crashReportingEnabled.collectAsStateWithLifecycle()
    val longTermMemoryEnabled by viewModel.longTermMemoryEnabled.collectAsStateWithLifecycle()
    val memories by viewModel.memories.collectAsStateWithLifecycle()

    val isAccessibilityEnabled = remember(context) { KulsoomAccessibilityService.isServiceEnabled(context) }
    val canDrawOverlays = remember(context) {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(context)
        } else true
    }

    var showInAppReplyInfoDialog by remember { mutableStateOf(false) }
    var showLanguagePicker by remember { mutableStateOf(false) }
    var showCreateProfileDialog by remember { mutableStateOf(false) }
    var editingProfile by remember { mutableStateOf<UserProfile?>(null) }
    var profileToDelete by remember { mutableStateOf<UserProfile?>(null) }

    // Training temporary holder
    var pendingProfileName by remember { mutableStateOf("") }
    var pendingLanguage by remember { mutableStateOf("en-US") }
    var pendingVoiceTone by remember { mutableStateOf("Natural Warm") }
    var pendingPitch by remember { mutableFloatStateOf(1.0f) }
    var pendingSpeed by remember { mutableFloatStateOf(1.0f) }
    var pendingOnlyMyVoice by remember { mutableStateOf(false) }
    var pendingAvatarColor by remember { mutableIntStateOf(0) }

    // Permission state refreshers
    val hasMic = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
    val hasPhone = ContextCompat.checkSelfPermission(context, Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED
    val hasSms = ContextCompat.checkSelfPermission(context, Manifest.permission.SEND_SMS) == PackageManager.PERMISSION_GRANTED
    val hasContacts = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED
    val hasCalendar = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED
    val isBatteryWhitelisted = KulsoomWakeWordService.isBatteryOptimizationIgnored(context)

    val permLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { }

    val micLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(SpaceBlack)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Assistant Settings",
                    color = TextWhite,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "User profiles, voice recognition & automation",
                    color = TextMuted,
                    fontSize = 12.sp
                )
            }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Section 0: User Profile & Speaker Recognition
            SettingsSectionCard(title = "User Profiles & Voiceprints") {
                // Privacy note
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = BrightTurquoise.copy(alpha = 0.1f),
                    border = BorderStroke(1.dp, BrightTurquoise.copy(alpha = 0.3f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Security,
                            contentDescription = null,
                            tint = BrightTurquoise,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "Voiceprints are calculated and stored locally in Room DB. No voice biometric data is ever uploaded.",
                            color = BrightTurquoise,
                            fontSize = 11.sp,
                            lineHeight = 15.sp
                        )
                    }
                }

                // Guest profile option
                Surface(
                    onClick = { viewModel.selectProfile(null) },
                    shape = RoundedCornerShape(12.dp),
                    color = if (activeProfile == null) ProfessionalBlue.copy(alpha = 0.15f) else GlassSurface,
                    border = BorderStroke(1.dp, if (activeProfile == null) ProfessionalBlue else GlassBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            ProfileAvatarBadge(name = "Guest", colorIndex = 0, size = 36)
                            Column {
                                Text("Guest / Shared Device", color = TextWhite, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                                Text("Universal access without voice restriction", color = TextMuted, fontSize = 11.sp)
                            }
                        }
                        if (activeProfile == null) {
                            Icon(Icons.Default.CheckCircle, contentDescription = "Active", tint = ProfessionalBlue, modifier = Modifier.size(20.dp))
                        }
                    }
                }

                // Enrolled Profiles List
                if (profiles.isNotEmpty()) {
                    Text(
                        text = "ENROLLED PROFILES (${profiles.size})",
                        color = TextMuted,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp,
                        modifier = Modifier.padding(top = 4.dp)
                    )

                    profiles.forEach { profile ->
                        val isSelected = activeProfile?.id == profile.id
                        Surface(
                            onClick = { viewModel.selectProfile(profile) },
                            shape = RoundedCornerShape(14.dp),
                            color = if (isSelected) ProfessionalBlue.copy(alpha = 0.15f) else GlassSurface,
                            border = BorderStroke(1.dp, if (isSelected) ProfessionalBlue else GlassBorder),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                ProfileAvatarBadge(name = profile.displayName, colorIndex = profile.avatarColorIndex, size = 38, hasVoiceprint = profile.hasVoiceprint)

                                Column(
                                    modifier = Modifier
                                        .weight(1f)
                                        .padding(horizontal = 12.dp)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Text(
                                            text = profile.displayName,
                                            color = TextWhite,
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                        if (isSelected) {
                                            Surface(
                                                shape = CircleShape,
                                                color = ProfessionalBlue
                                            ) {
                                                Text(
                                                    text = "ACTIVE",
                                                    color = Color.White,
                                                    fontSize = 9.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                )
                                            }
                                        }
                                    }

                                    Text(
                                        text = "${if (profile.preferredLanguage.startsWith("ur")) "Urdu" else "English"} · ${profile.voiceTonePreset}${if (profile.hasVoiceprint) " · Voice Trained" else " · No Voiceprint"}",
                                        color = if (profile.hasVoiceprint) BrightTurquoise else TextMuted,
                                        fontSize = 11.sp
                                    )
                                }

                                // Actions
                                IconButton(
                                    onClick = { editingProfile = profile },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Edit,
                                        contentDescription = "Edit Profile",
                                        tint = TextMuted,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }

                                IconButton(
                                    onClick = { profileToDelete = profile },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.DeleteOutline,
                                        contentDescription = "Delete Profile",
                                        tint = GlowingRose.copy(alpha = 0.8f),
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                // Add Profile Button
                Button(
                    onClick = { showCreateProfileDialog = true },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = ProfessionalBlue)
                ) {
                    Icon(Icons.Default.PersonAdd, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Create New Profile", fontWeight = FontWeight.Bold)
                }

                OutlinedButton(
                    onClick = onNavigateToOnboarding,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, BrightTurquoise.copy(alpha = 0.5f))
                ) {
                    Icon(Icons.Default.GraphicEq, contentDescription = null, tint = BrightTurquoise, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Launch Voice Calibration Wizard", color = BrightTurquoise, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                }
            }

            // Section 1: Activation & Diagnostics Card (Fix for Bug 3 & Wake Word)
            SettingsSectionCard(title = "Voice Activation & Diagnostics") {
                SettingSwitchRow(
                    icon = Icons.Default.Hearing,
                    title = "Wake Word (\"Kulsoom\")",
                    subtitle = "Activate assistant hands-free by speaking the wake phrase",
                    checked = wakeWordEnabled,
                    onCheckedChange = { isChecked ->
                        if (isChecked && !hasMic) {
                            micLauncher.launch(Manifest.permission.RECORD_AUDIO)
                        }
                        viewModel.setWakeWordEnabled(isChecked)
                    }
                )

                HorizontalDivider(color = GlassBorder)

                SettingSwitchRow(
                    icon = Icons.Default.LockClock,
                    title = "Respond from Lock Screen",
                    subtitle = "Allow Kulsoom to answer safe questions & alarms while device is locked",
                    checked = lockScreenResponseEnabled,
                    onCheckedChange = { viewModel.setLockScreenResponseEnabled(it) }
                )

                HorizontalDivider(color = GlassBorder)

                SettingSwitchRow(
                    icon = Icons.Default.ChatBubble,
                    title = "Varied Responses",
                    subtitle = "Rotate natural phrases for wake-up confirmations and task completions",
                    checked = variedResponsesEnabled,
                    onCheckedChange = { viewModel.setVariedResponsesEnabled(it) }
                )

                HorizontalDivider(color = GlassBorder)

                SettingSwitchRow(
                    icon = Icons.Default.Forum,
                    title = "Continuous Conversation Mode",
                    subtitle = "Automatically keep listening for follow-up questions after Kulsoom speaks",
                    checked = continuousConversationEnabled,
                    onCheckedChange = { viewModel.setContinuousConversationEnabled(it) }
                )

                HorizontalDivider(color = GlassBorder)

                SettingSwitchRow(
                    icon = Icons.Default.Cancel,
                    title = "Allow Natural Interruption (Barge-In)",
                    subtitle = "Stop assistant speech instantly when you speak during a response",
                    checked = allowInterruptionsEnabled,
                    onCheckedChange = { viewModel.setAllowInterruptionsEnabled(it) }
                )

                HorizontalDivider(color = GlassBorder)

                SettingSwitchRow(
                    icon = Icons.Default.WbSunny,
                    title = "Offer Daily Morning Briefing",
                    subtitle = "Offer a spoken day summary when waking or launching Kulsoom in the morning",
                    checked = offerDailyBriefingMorning,
                    onCheckedChange = { viewModel.setOfferDailyBriefingMorning(it) }
                )

                HorizontalDivider(color = GlassBorder)

                // Diagnostic Status Row: Service Status
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
                            imageVector = if (isWakeWordServiceRunning && wakeWordEnabled) Icons.Default.CheckCircle else Icons.Default.Info,
                            contentDescription = null,
                            tint = if (isWakeWordServiceRunning && wakeWordEnabled) StatusSuccess else StatusWarning,
                            modifier = Modifier.size(18.dp)
                        )
                        Column {
                            Text("Background Listening Service", color = TextWhite, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                            Text(
                                if (isWakeWordServiceRunning && wakeWordEnabled) "Foreground microphone service running" else "Service inactive",
                                color = TextMuted,
                                fontSize = 11.sp
                            )
                        }
                    }
                    Surface(
                        shape = CircleShape,
                        color = if (isWakeWordServiceRunning && wakeWordEnabled) StatusSuccess.copy(alpha = 0.2f) else StatusWarning.copy(alpha = 0.2f),
                        border = BorderStroke(1.dp, if (isWakeWordServiceRunning && wakeWordEnabled) StatusSuccess else StatusWarning)
                    ) {
                        Text(
                            text = if (isWakeWordServiceRunning && wakeWordEnabled) "RUNNING" else "STOPPED",
                            color = if (isWakeWordServiceRunning && wakeWordEnabled) StatusSuccess else StatusWarning,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }

                HorizontalDivider(color = GlassBorder)

                // Diagnostic Status Row: Battery Optimization
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = if (isBatteryWhitelisted) Icons.Default.BatteryChargingFull else Icons.Default.BatteryAlert,
                            contentDescription = null,
                            tint = if (isBatteryWhitelisted) StatusSuccess else StatusWarning,
                            modifier = Modifier.size(18.dp)
                        )
                        Column {
                            Text("Battery Optimization", color = TextWhite, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                            Text(
                                if (isBatteryWhitelisted) "Whitelisted for seamless background listening" else "Restricted (OS may kill background audio)",
                                color = TextMuted,
                                fontSize = 11.sp
                            )
                        }
                    }

                    if (!isBatteryWhitelisted) {
                        FilledTonalButton(
                            onClick = {
                                try {
                                    val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                                        data = Uri.parse("package:${context.packageName}")
                                    }
                                    context.startActivity(intent)
                                } catch (_: Exception) {
                                    val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                                    context.startActivity(intent)
                                }
                            },
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.filledTonalButtonColors(containerColor = GlowingAmber.copy(alpha = 0.2f)),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            modifier = Modifier.height(30.dp)
                        ) {
                            Text("Allow", color = GlowingAmber, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    } else {
                        Text("Optimized", color = StatusSuccess, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }

                HorizontalDivider(color = GlassBorder)

                // Wake Word Diagnostics Screen Entry
                Surface(
                    onClick = onNavigateToDiagnostics,
                    shape = RoundedCornerShape(12.dp),
                    color = ProfessionalBlue.copy(alpha = 0.12f),
                    border = BorderStroke(1.dp, ProfessionalBlue.copy(alpha = 0.35f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(ProfessionalBlue.copy(alpha = 0.2f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Analytics,
                                    contentDescription = null,
                                    tint = ProfessionalBlue,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Column {
                                Text(
                                    text = "Wake Word Diagnostics",
                                    color = TextWhite,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Live mic meter, background service & detection logs",
                                    color = TextMuted,
                                    fontSize = 11.sp,
                                    lineHeight = 14.sp
                                )
                            }
                        }
                        Icon(
                            imageVector = Icons.Default.ChevronRight,
                            contentDescription = "Open Diagnostics",
                            tint = ProfessionalBlue,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                HorizontalDivider(color = GlassBorder)

                // Test Wake Word button
                OutlinedButton(
                    onClick = { viewModel.triggerWakeWordTest() },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, ProfessionalPurple.copy(alpha = 0.6f))
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null, tint = ProfessionalPurple, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Test Wake Word Trigger (\"Kulsoom\")", color = ProfessionalPurple, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }
            }

            // Section 2: Speech & Language (Fix for Bug 1 & Urdu TTS)
            SettingsSectionCard(title = "Global Voice & Language") {
                // Language selector row
                Surface(
                    onClick = { showLanguagePicker = true },
                    color = Color.Transparent
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(Icons.Default.Language, contentDescription = null, tint = ProfessionalBlue)
                            Column {
                                Text("Speech Language", color = TextWhite, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                                Text(
                                    when (selectedLanguage) {
                                        "ur-PK" -> "Urdu (Pakistan) / اردو"
                                        "en-GB" -> "English (UK)"
                                        else -> "English (US)"
                                    },
                                    color = TextMuted,
                                    fontSize = 12.sp
                                )
                            }
                        }
                        Icon(Icons.Default.ChevronRight, contentDescription = null, tint = TextMuted)
                    }
                }

                HorizontalDivider(color = GlassBorder)

                // Urdu TTS Engine Status Banner
                if (selectedLanguage.startsWith("ur")) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (urduVoiceStatus == UrduVoiceStatus.AVAILABLE) StatusSuccess.copy(alpha = 0.1f) else GlowingAmber.copy(alpha = 0.12f),
                        border = BorderStroke(1.dp, if (urduVoiceStatus == UrduVoiceStatus.AVAILABLE) StatusSuccess.copy(alpha = 0.3f) else GlowingAmber.copy(alpha = 0.4f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = if (urduVoiceStatus == UrduVoiceStatus.AVAILABLE) Icons.Default.CheckCircle else Icons.Default.Warning,
                                    contentDescription = null,
                                    tint = if (urduVoiceStatus == UrduVoiceStatus.AVAILABLE) StatusSuccess else GlowingAmber,
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(
                                    text = if (urduVoiceStatus == UrduVoiceStatus.AVAILABLE) {
                                        "Urdu voice engine installed with native pronunciation"
                                    } else {
                                        "Urdu voice pack missing on this device"
                                    },
                                    color = if (urduVoiceStatus == UrduVoiceStatus.AVAILABLE) StatusSuccess else GlowingAmber,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }

                            if (urduVoiceStatus != UrduVoiceStatus.AVAILABLE) {
                                Text(
                                    text = "To enable crisp Urdu speech, install the Urdu (Pakistan) voice pack in Android Text-to-Speech settings.",
                                    color = TextMuted,
                                    fontSize = 11.sp
                                )
                                Button(
                                    onClick = {
                                        try {
                                            val intent = Intent(TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA)
                                            context.startActivity(intent)
                                        } catch (_: Exception) {
                                            val intent = Intent("com.android.settings.TTS_SETTINGS")
                                            context.startActivity(intent)
                                        }
                                    },
                                    shape = RoundedCornerShape(8.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = GlowingAmber),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                    modifier = Modifier.fillMaxWidth().height(32.dp)
                                ) {
                                    Text("Open TTS Settings to Install Urdu Voice", color = SpaceBlack, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }

                    HorizontalDivider(color = GlassBorder)
                }

                // Voice Pitch Slider
                Column(modifier = Modifier.padding(vertical = 6.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Voice Pitch", color = TextWhite, fontSize = 14.sp)
                        Text(String.format(java.util.Locale.US, "%.1fx", ttsPitch), color = ProfessionalPurple, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                    Slider(
                        value = ttsPitch,
                        onValueChange = { viewModel.setTtsPitch(it) },
                        valueRange = 0.5f..1.5f,
                        colors = SliderDefaults.colors(
                            thumbColor = ProfessionalPurple,
                            activeTrackColor = ProfessionalPurple,
                            inactiveTrackColor = GlassSurfaceElevated
                        )
                    )
                }

                HorizontalDivider(color = GlassBorder)

                // Voice Speed Slider
                Column(modifier = Modifier.padding(vertical = 6.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Voice Speed", color = TextWhite, fontSize = 14.sp)
                        Text(String.format(java.util.Locale.US, "%.1fx", ttsSpeed), color = ProfessionalBlue, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                    Slider(
                        value = ttsSpeed,
                        onValueChange = { viewModel.setTtsSpeed(it) },
                        valueRange = 0.5f..1.5f,
                        colors = SliderDefaults.colors(
                            thumbColor = ProfessionalBlue,
                            activeTrackColor = ProfessionalBlue,
                            inactiveTrackColor = GlassSurfaceElevated
                        )
                    )
                }
            }

            // Section 3: Long-Term Memory (Personalized Local Memory)
            SettingsSectionCard(title = "Long-Term Memory") {
                SettingSwitchRow(
                    icon = Icons.Default.Psychology,
                    title = "Long-Term Memory",
                    subtitle = "Remember personal facts, preferences, and context across sessions",
                    checked = longTermMemoryEnabled,
                    onCheckedChange = { viewModel.setLongTermMemoryEnabled(it) }
                )

                HorizontalDivider(color = GlassBorder)

                Surface(
                    onClick = onNavigateToMemory,
                    color = Color.Transparent,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(Icons.Default.Memory, contentDescription = null, tint = BrightTurquoise)
                            Column {
                                Text("Manage Saved Memories", color = TextWhite, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                                Text(
                                    if (memories.isEmpty()) "No saved memories yet" else "${memories.size} saved fact${if (memories.size > 1) "s" else ""} on this device",
                                    color = TextMuted,
                                    fontSize = 11.sp
                                )
                            }
                        }
                        Icon(Icons.Default.ChevronRight, contentDescription = null, tint = TextMuted)
                    }
                }
            }

            // Section 4: Safety & Automation
            SettingsSectionCard(title = "Automation & Safety") {
                SettingSwitchRow(
                    icon = Icons.Default.VerifiedUser,
                    title = "Trusted Quick-Actions",
                    subtitle = "Skip verbal confirmation dialogs when initiating calls or SMS",
                    checked = trustedQuickActions,
                    onCheckedChange = { viewModel.setTrustedQuickActions(it) }
                )

                HorizontalDivider(color = GlassBorder)

                SettingSwitchRow(
                    icon = Icons.Default.BugReport,
                    title = "Share Crash Reports",
                    subtitle = "Anonymous non-personal diagnostic logs to help improve Kulsoom reliability",
                    checked = crashReportingEnabled,
                    onCheckedChange = { viewModel.setCrashReportingEnabled(it) }
                )
            }

            // Section 4: In-App Reply Assistant (WhatsApp & Similar Apps)
            SettingsSectionCard(title = "In-App Reply Assistant (WhatsApp & Chat Apps)") {
                SettingSwitchRow(
                    icon = Icons.Default.Quickreply,
                    title = "In-App Reply Dictation",
                    subtitle = "Dictate replies directly inside WhatsApp, SMS, and Messenger using the wake word without switching apps",
                    checked = inAppReplyEnabled,
                    onCheckedChange = { isChecked ->
                        if (isChecked) {
                            showInAppReplyInfoDialog = true
                        } else {
                            viewModel.setInAppReplyEnabled(false)
                        }
                    }
                )

                HorizontalDivider(color = GlassBorder)

                // Accessibility Service Status
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = if (isAccessibilityEnabled) Icons.Default.CheckCircle else Icons.Default.AccessibilityNew,
                            contentDescription = null,
                            tint = if (isAccessibilityEnabled) StatusSuccess else StatusWarning,
                            modifier = Modifier.size(18.dp)
                        )
                        Column {
                            Text("Accessibility Service", color = TextWhite, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                            Text(
                                if (isAccessibilityEnabled) "Active: Ready to detect and insert replies into chat fields" else "Required to detect and type into chat fields",
                                color = TextMuted,
                                fontSize = 11.sp
                            )
                        }
                    }
                    if (!isAccessibilityEnabled) {
                        Button(
                            onClick = {
                                try {
                                    val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                    }
                                    context.startActivity(intent)
                                } catch (_: Exception) {}
                            },
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = BrightTurquoise),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                            modifier = Modifier.height(30.dp)
                        ) {
                            Text("Enable", color = SpaceBlack, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    } else {
                        Surface(
                            shape = CircleShape,
                            color = StatusSuccess.copy(alpha = 0.2f),
                            border = BorderStroke(1.dp, StatusSuccess)
                        ) {
                            Text(
                                text = "ACTIVE",
                                color = StatusSuccess,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }

                HorizontalDivider(color = GlassBorder)

                // Overlay Permission Status
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = if (canDrawOverlays) Icons.Default.CheckCircle else Icons.Default.Layers,
                            contentDescription = null,
                            tint = if (canDrawOverlays) StatusSuccess else StatusWarning,
                            modifier = Modifier.size(18.dp)
                        )
                        Column {
                            Text("Display Over Other Apps", color = TextWhite, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                            Text(
                                if (canDrawOverlays) "Granted: Floating dictation card can overlay chat apps" else "Required to display non-intrusive floating card",
                                color = TextMuted,
                                fontSize = 11.sp
                            )
                        }
                    }
                    if (!canDrawOverlays) {
                        Button(
                            onClick = {
                                try {
                                    val intent = Intent(
                                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                        Uri.parse("package:${context.packageName}")
                                    ).apply {
                                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                    }
                                    context.startActivity(intent)
                                } catch (_: Exception) {
                                    val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION).apply {
                                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                    }
                                    context.startActivity(intent)
                                }
                            },
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = BrightTurquoise),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                            modifier = Modifier.height(30.dp)
                        ) {
                            Text("Grant", color = SpaceBlack, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    } else {
                        Surface(
                            shape = CircleShape,
                            color = StatusSuccess.copy(alpha = 0.2f),
                            border = BorderStroke(1.dp, StatusSuccess)
                        ) {
                            Text(
                                text = "GRANTED",
                                color = StatusSuccess,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }

                HorizontalDivider(color = GlassBorder)

                // Info & Safety dialog opener
                TextButton(
                    onClick = { showInAppReplyInfoDialog = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Info, contentDescription = null, tint = ProfessionalBlue, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("How In-App Reply Works & Privacy Details", color = ProfessionalBlue, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }
            }

            // Section 5: System Permissions Dashboard
            SettingsSectionCard(title = "Permissions Management") {
                PermissionStatusRow(
                    label = "Microphone",
                    isGranted = hasMic,
                    onRequest = { permLauncher.launch(arrayOf(Manifest.permission.RECORD_AUDIO)) }
                )
                PermissionStatusRow(
                    label = "Phone Dialer",
                    isGranted = hasPhone,
                    onRequest = { permLauncher.launch(arrayOf(Manifest.permission.CALL_PHONE)) }
                )
                PermissionStatusRow(
                    label = "SMS Messaging",
                    isGranted = hasSms,
                    onRequest = { permLauncher.launch(arrayOf(Manifest.permission.SEND_SMS)) }
                )
                PermissionStatusRow(
                    label = "Contacts Access",
                    isGranted = hasContacts,
                    onRequest = { permLauncher.launch(arrayOf(Manifest.permission.READ_CONTACTS)) }
                )
                PermissionStatusRow(
                    label = "Calendar Schedule",
                    isGranted = hasCalendar,
                    onRequest = { permLauncher.launch(arrayOf(Manifest.permission.READ_CALENDAR)) }
                )

                OutlinedButton(
                    onClick = {
                        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                            data = Uri.fromParts("package", context.packageName, null)
                        }
                        context.startActivity(intent)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, GlassBorder)
                ) {
                    Icon(Icons.Default.Settings, contentDescription = null, modifier = Modifier.size(16.dp), tint = ProfessionalBlue)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Open Android App Settings", fontSize = 13.sp, color = TextWhite)
                }
            }

            // Section 5: Branding & About
            SettingsSectionCard(title = "About & Copyright") {
                Column(
                    modifier = Modifier.padding(vertical = 6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(
                                    Brush.linearGradient(
                                        listOf(ProfessionalBlue, ProfessionalPurple)
                                    )
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("K", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        }
                        Column {
                            Text(
                                text = "Kulsoom AI Assistant",
                                color = TextWhite,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Version 1.0.0 · Local Voiceprint Neural Engine",
                                color = TextMuted,
                                fontSize = 11.sp
                            )
                        }
                    }

                    Text(
                        text = "\"Your voice. Your commands. Kulsoom listens.\"",
                        color = TextBlueSubtle,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(vertical = 4.dp)
                    )

                    HorizontalDivider(color = GlassBorder)

                    Text(
                        text = "Developer & Owner: Munib u Rehman",
                        color = TextWhite,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "Kulsoom © 2026 Munib u Rehman. All rights reserved.",
                        color = TextMuted,
                        fontSize = 11.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(80.dp))
        }
    }

    // Dialogs
    if (showCreateProfileDialog) {
        CreateOrEditProfileDialog(
            profileToEdit = null,
            onDismiss = { showCreateProfileDialog = false },
            onSaveProfile = { name, lang, voiceTone, pitch, speed, onlyMyVoice, avatarColor, launchTraining ->
                pendingProfileName = name
                pendingLanguage = lang
                pendingVoiceTone = voiceTone
                pendingPitch = pitch
                pendingSpeed = speed
                pendingOnlyMyVoice = onlyMyVoice
                pendingAvatarColor = avatarColor

                if (launchTraining) {
                    viewModel.startVoiceTraining()
                } else {
                    viewModel.createProfile(
                        displayName = name,
                        preferredLanguage = lang,
                        voiceTonePreset = voiceTone,
                        ttsPitch = pitch,
                        ttsSpeed = speed,
                        onlyRespondToMyVoice = onlyMyVoice,
                        avatarColorIndex = avatarColor
                    )
                }
            }
        )
    }

    if (editingProfile != null) {
        CreateOrEditProfileDialog(
            profileToEdit = editingProfile,
            onDismiss = { editingProfile = null },
            onSaveProfile = { name, lang, voiceTone, pitch, speed, onlyMyVoice, avatarColor, _ ->
                val updated = editingProfile!!.copy(
                    displayName = name,
                    preferredLanguage = lang,
                    voiceTonePreset = voiceTone,
                    ttsPitch = pitch,
                    ttsSpeed = speed,
                    onlyRespondToMyVoice = onlyMyVoice,
                    avatarColorIndex = avatarColor
                )
                viewModel.updateProfile(updated)
                editingProfile = null
            }
        )
    }

    if (profileToDelete != null) {
        AlertDialog(
            onDismissRequest = { profileToDelete = null },
            title = { Text("Delete Profile?", color = TextWhite) },
            text = {
                Text(
                    "Are you sure you want to delete ${profileToDelete?.displayName}'s profile? All associated voiceprints, notes, reminders, and chat history will be permanently deleted from this device.",
                    color = TextMuted,
                    fontSize = 13.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        profileToDelete?.let { viewModel.deleteProfile(it.id) }
                        profileToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = GlowingRose)
                ) {
                    Text("Delete", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { profileToDelete = null }) {
                    Text("Cancel", color = TextMuted)
                }
            },
            containerColor = SurfaceDark
        )
    }

    // Voice Training Modal
    if (voiceTrainingState.isActive) {
        VoiceTrainingDialog(
            trainingState = voiceTrainingState,
            soundLevel = soundLevel,
            onRecordSample = {
                if (hasMic) {
                    viewModel.recordTrainingSample()
                } else {
                    micLauncher.launch(Manifest.permission.RECORD_AUDIO)
                }
            },
            onRerecordSample = { step ->
                viewModel.rerecordSample(step)
            },
            onFinish = {
                viewModel.createProfile(
                    displayName = pendingProfileName,
                    preferredLanguage = pendingLanguage,
                    voiceTonePreset = pendingVoiceTone,
                    ttsPitch = pendingPitch,
                    ttsSpeed = pendingSpeed,
                    onlyRespondToMyVoice = pendingOnlyMyVoice,
                    avatarColorIndex = pendingAvatarColor,
                    voiceprintSamples = voiceTrainingState.collectedEmbeddings
                )
                viewModel.cancelVoiceTraining()
            },
            onCancel = {
                viewModel.cancelVoiceTraining()
            }
        )
    }

    if (showLanguagePicker) {
        AlertDialog(
            onDismissRequest = { showLanguagePicker = false },
            title = { Text("Select Assistant Language", color = TextWhite) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    LanguageOption("English (US)", "en-US", selectedLanguage) {
                        viewModel.setLanguage(it)
                        showLanguagePicker = false
                    }
                    LanguageOption("English (UK)", "en-GB", selectedLanguage) {
                        viewModel.setLanguage(it)
                        showLanguagePicker = false
                    }
                    LanguageOption("Urdu (Pakistan) / اردو", "ur-PK", selectedLanguage) {
                        viewModel.setLanguage(it)
                        showLanguagePicker = false
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showLanguagePicker = false }) {
                    Text("Close", color = TextMuted)
                }
            },
            containerColor = SurfaceDark
        )
    }

    if (showInAppReplyInfoDialog) {
        AlertDialog(
            onDismissRequest = { showInAppReplyInfoDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.Quickreply, contentDescription = null, tint = BrightTurquoise)
                    Text("In-App Reply Assistant", color = TextWhite, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        "Reply directly inside messaging apps (WhatsApp, SMS, Telegram, Messenger) without switching away from your current screen.",
                        color = TextMuted,
                        fontSize = 13.sp
                    )

                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = GlassSurfaceElevated,
                        border = BorderStroke(1.dp, GlassBorder)
                    ) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("✨ How It Works", color = BrightTurquoise, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            Text("1. When you're in a chat, say \"Kulsoom\" or \"Kulsoom, reply [message]\".", color = TextWhite, fontSize = 12.sp)
                            Text("2. A compact floating overlay card appears on top of the chat.", color = TextWhite, fontSize = 12.sp)
                            Text("3. Speak your reply in English, Urdu, or Roman Urdu.", color = TextWhite, fontSize = 12.sp)
                            Text("4. Tap 'Insert' to insert into the chat box, or 'Send' after confirming.", color = TextWhite, fontSize = 12.sp)
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = ProfessionalPurple.copy(alpha = 0.12f),
                        border = BorderStroke(1.dp, ProfessionalPurple.copy(alpha = 0.3f))
                    ) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("🔒 Privacy & Safety", color = ProfessionalPurple, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            Text("• Never sends messages automatically without your explicit confirmation.", color = TextWhite, fontSize = 12.sp)
                            Text("• Accessibility Service ONLY inspects text fields when you invoke Kulsoom.", color = TextWhite, fontSize = 12.sp)
                            Text("• Does not record screen contents or read private chat history.", color = TextWhite, fontSize = 12.sp)
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.setInAppReplyEnabled(true)
                        showInAppReplyInfoDialog = false
                        if (!isAccessibilityEnabled) {
                            try {
                                val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                }
                                context.startActivity(intent)
                            } catch (_: Exception) {}
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = BrightTurquoise)
                ) {
                    Text("Turn ON & Setup", color = SpaceBlack, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showInAppReplyInfoDialog = false }) {
                    Text("Cancel", color = TextMuted)
                }
            },
            containerColor = SurfaceDark
        )
    }
}

@Composable
private fun LanguageOption(
    name: String,
    code: String,
    current: String,
    onSelect: (String) -> Unit
) {
    Surface(
        onClick = { onSelect(code) },
        shape = RoundedCornerShape(12.dp),
        color = if (current == code) ProfessionalBlue.copy(alpha = 0.2f) else GlassSurfaceElevated,
        border = BorderStroke(1.dp, if (current == code) ProfessionalBlue else GlassBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(name, color = if (current == code) ProfessionalBlue else TextWhite, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            if (current == code) {
                Icon(Icons.Default.Check, contentDescription = null, tint = ProfessionalBlue, modifier = Modifier.size(18.dp))
            }
        }
    }
}

@Composable
private fun SettingsSectionCard(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = title,
            color = ProfessionalBlue,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.5.sp,
            modifier = Modifier.padding(start = 4.dp)
        )
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = GlassSurfaceElevated),
            border = BorderStroke(1.dp, GlassBorder)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                content = content
            )
        }
    }
}

@Composable
private fun SettingSwitchRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(imageVector = icon, contentDescription = null, tint = ProfessionalBlue, modifier = Modifier.size(22.dp))
            Column {
                Text(title, color = TextWhite, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                Text(subtitle, color = TextMuted, fontSize = 12.sp, modifier = Modifier.padding(top = 2.dp))
            }
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = ProfessionalBlue,
                uncheckedTrackColor = GlassSurface
            )
        )
    }
}

@Composable
private fun PermissionStatusRow(
    label: String,
    isGranted: Boolean,
    onRequest: () -> Unit
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
                imageVector = if (isGranted) Icons.Default.CheckCircle else Icons.Default.Warning,
                contentDescription = null,
                tint = if (isGranted) StatusSuccess else StatusWarning,
                modifier = Modifier.size(18.dp)
            )
            Text(label, color = TextWhite, fontSize = 14.sp)
        }

        if (isGranted) {
            Text("Granted", color = StatusSuccess, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        } else {
            FilledTonalButton(
                onClick = onRequest,
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.filledTonalButtonColors(containerColor = ProfessionalBlue.copy(alpha = 0.15f)),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                modifier = Modifier.height(30.dp)
            ) {
                Text("Grant", color = ProfessionalBlue, fontSize = 11.sp)
            }
        }
    }
}
