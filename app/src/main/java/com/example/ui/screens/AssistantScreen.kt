package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.AssistantState
import com.example.ui.components.*
import com.example.ui.theme.*
import com.example.ui.viewmodel.AssistantViewModel

@Composable
fun AssistantScreen(
    viewModel: AssistantViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current

    val assistantState by viewModel.assistantState.collectAsStateWithLifecycle()
    val statusText by viewModel.currentStatusText.collectAsStateWithLifecycle()
    val liveTranscript by viewModel.liveSpeechText.collectAsStateWithLifecycle()
    val soundLevel by viewModel.soundLevel.collectAsStateWithLifecycle()
    val pendingConfirmation by viewModel.pendingConfirmation.collectAsStateWithLifecycle()
    val messages by viewModel.messages.collectAsStateWithLifecycle()
    val selectedLanguage by viewModel.selectedLanguage.collectAsStateWithLifecycle()
    val profiles by viewModel.profiles.collectAsStateWithLifecycle()
    val activeProfile by viewModel.activeProfile.collectAsStateWithLifecycle()
    val voiceTrainingState by viewModel.voiceTrainingState.collectAsStateWithLifecycle()
    val ambiguousCandidates by viewModel.ambiguousPromptCandidates.collectAsStateWithLifecycle()
    val isOnline by viewModel.isOnline.collectAsStateWithLifecycle()
    val isVisionModeActive by viewModel.isVisionModeActive.collectAsStateWithLifecycle()

    var typedInput by remember { mutableStateOf("") }
    var showPermissionsDialog by remember { mutableStateOf(false) }
    var showProfileSelectionDialog by remember { mutableStateOf(false) }
    var showCreateProfileDialog by remember { mutableStateOf(false) }

    // Pending creation data before voice training
    var pendingProfileName by remember { mutableStateOf("") }
    var pendingLanguage by remember { mutableStateOf("en-US") }
    var pendingVoiceTone by remember { mutableStateOf("Natural Warm") }
    var pendingPitch by remember { mutableFloatStateOf(1.0f) }
    var pendingSpeed by remember { mutableFloatStateOf(1.0f) }
    var pendingOnlyMyVoice by remember { mutableStateOf(false) }
    var pendingAvatarColor by remember { mutableIntStateOf(0) }

    // Check mic permission launcher
    val micPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            if (viewModel.wakeWordEnabled.value) {
                viewModel.updateWakeWordService(true)
            }
            viewModel.toggleListening()
        } else {
            showPermissionsDialog = true
        }
    }

    val lastAssistantMessage = messages.firstOrNull { !it.isUser }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(SpaceBlack)
    ) {
        // Ambient background blur orbs
        Canvas(modifier = Modifier.fillMaxSize()) {
            val canvasWidth = size.width
            val canvasHeight = size.height

            // Top-left diffuse blue blur
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(ProfessionalBlue.copy(alpha = 0.22f), Color.Transparent),
                    center = Offset(canvasWidth * 0.2f, canvasHeight * 0.25f),
                    radius = canvasWidth * 0.75f
                ),
                center = Offset(canvasWidth * 0.2f, canvasHeight * 0.25f),
                radius = canvasWidth * 0.75f
            )

            // Center-right diffuse purple blur
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(ProfessionalPurple.copy(alpha = 0.18f), Color.Transparent),
                    center = Offset(canvasWidth * 0.8f, canvasHeight * 0.5f),
                    radius = canvasWidth * 0.7f
                ),
                center = Offset(canvasWidth * 0.8f, canvasHeight * 0.5f),
                radius = canvasWidth * 0.7f
            )

            // Bottom subtle pink highlight
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(ProfessionalPink.copy(alpha = 0.12f), Color.Transparent),
                    center = Offset(canvasWidth * 0.5f, canvasHeight * 0.85f),
                    radius = canvasWidth * 0.6f
                ),
                center = Offset(canvasWidth * 0.5f, canvasHeight * 0.85f),
                radius = canvasWidth * 0.6f
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Header Top Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Branding with gradient avatar badge
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(
                                Brush.linearGradient(
                                    listOf(ProfessionalBlue, ProfessionalPurple)
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "K",
                            color = Color.White,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Column {
                        Text(
                            text = "Kulsoom",
                            color = TextWhite,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.5.sp
                        )
                        Text(
                            text = "AI Voice Companion",
                            color = TextMuted,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Normal
                        )
                    }
                }

                // Right side: Profile Switcher & Language Badge
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Profile Chip / Selector
                    Surface(
                        shape = CircleShape,
                        color = GlassSurfaceElevated,
                        border = BorderStroke(1.dp, GlassBorder),
                        modifier = Modifier.clickable { showProfileSelectionDialog = true }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            if (activeProfile != null) {
                                ProfileAvatarBadge(
                                    name = activeProfile!!.displayName,
                                    colorIndex = activeProfile!!.avatarColorIndex,
                                    size = 20,
                                    hasVoiceprint = activeProfile!!.hasVoiceprint
                                )
                                Text(
                                    text = activeProfile!!.displayName,
                                    color = TextWhite,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Default.PersonOutline,
                                    contentDescription = "Profile",
                                    tint = TextBlueLight,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = "Guest",
                                    color = TextBlueLight,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                            Icon(
                                imageVector = Icons.Default.ArrowDropDown,
                                contentDescription = null,
                                tint = TextMuted,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }

                    // Offline Indicator Badge if network disconnected
                    if (!isOnline) {
                        Surface(
                            shape = CircleShape,
                            color = GlowingAmber.copy(alpha = 0.15f),
                            border = BorderStroke(1.dp, GlowingAmber.copy(alpha = 0.5f))
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CloudOff,
                                    contentDescription = "Offline Mode",
                                    tint = GlowingAmber,
                                    modifier = Modifier.size(13.dp)
                                )
                                Text(
                                    text = "Offline",
                                    color = GlowingAmber,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }

                    // Language / Status Indicator Badge
                    Surface(
                        shape = CircleShape,
                        color = GlassSurfaceElevated,
                        border = BorderStroke(1.dp, GlassBorder)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(7.dp)
                                    .clip(CircleShape)
                                    .background(
                                        when (assistantState) {
                                            AssistantState.LISTENING -> StatusListening
                                            AssistantState.THINKING -> StatusThinking
                                            AssistantState.SPEAKING -> StatusSpeaking
                                            AssistantState.IDLE -> StatusSuccess
                                        }
                                    )
                            )
                            Text(
                                text = if (selectedLanguage.startsWith("ur")) "Urdu" else "English",
                                color = TextBlueLight,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }

            // Center Stage Scrollable Area
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Spacer(modifier = Modifier.height(10.dp))

                // Status Caption
                AnimatedContent(
                    targetState = statusText,
                    transitionSpec = { fadeIn() togetherWith fadeOut() },
                    label = "status_anim"
                ) { targetStatus ->
                    Text(
                        text = targetStatus,
                        color = when (assistantState) {
                            AssistantState.LISTENING -> ProfessionalBlue
                            AssistantState.THINKING -> ProfessionalPurple
                            AssistantState.SPEAKING -> ProfessionalPink
                            AssistantState.IDLE -> TextMuted
                        },
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                        letterSpacing = 0.5.sp,
                        modifier = Modifier.padding(horizontal = 24.dp)
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Central Glowing Orb
                KulsoomOrb(
                    state = assistantState,
                    soundLevel = soundLevel,
                    onClick = {
                        val hasMic = ContextCompat.checkSelfPermission(
                            context,
                            Manifest.permission.RECORD_AUDIO
                        ) == PackageManager.PERMISSION_GRANTED

                        if (hasMic) {
                            if (assistantState == AssistantState.SPEAKING) {
                                viewModel.stopSpeaking()
                            } else {
                                viewModel.toggleListening()
                            }
                        } else {
                            micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                        }
                    },
                    size = 230.dp
                )

                Spacer(modifier = Modifier.height(18.dp))

                // Audio Waveform
                WaveformVisualizer(
                    state = assistantState,
                    soundLevel = soundLevel,
                    barCount = 18
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Live User Transcript or Assistant Response Card
                if (liveTranscript.isNotBlank()) {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 4.dp),
                        shape = RoundedCornerShape(22.dp),
                        color = GlassSurfaceElevated,
                        border = BorderStroke(1.dp, GlassBorder)
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.GraphicEq,
                                contentDescription = null,
                                tint = ProfessionalBlue,
                                modifier = Modifier.size(22.dp)
                            )
                            Text(
                                text = "\"$liveTranscript\"",
                                color = TextWhite,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Medium,
                                lineHeight = 20.sp
                            )
                        }
                    }
                } else if (lastAssistantMessage != null) {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 4.dp),
                        shape = RoundedCornerShape(22.dp),
                        color = GlassSurface,
                        border = BorderStroke(1.dp, GlassBorder)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(20.dp)
                                        .clip(CircleShape)
                                        .background(
                                            Brush.linearGradient(
                                                listOf(ProfessionalBlue, ProfessionalPurple)
                                            )
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text("K", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                }
                                Text(
                                    text = "Kulsoom",
                                    color = TextBlueLight,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = lastAssistantMessage.text,
                                color = TextBlueSubtle,
                                fontSize = 14.sp,
                                lineHeight = 21.sp
                            )
                        }
                    }
                }

                // Action Confirmation Card
                pendingConfirmation?.let { conf ->
                    Spacer(modifier = Modifier.height(10.dp))
                    ActionConfirmationCard(
                        confirmation = conf,
                        onConfirm = { viewModel.confirmPendingAction() },
                        onCancel = { viewModel.cancelPendingAction() }
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))
            }

            // Quick suggestion pills
            QuickSuggestionChips(
                onCommandSelected = { cmd ->
                    viewModel.processUserCommand(cmd)
                }
            )

            // Bottom Input Section: Glassmorphic bar with voice trigger & send
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                shape = RoundedCornerShape(28.dp),
                color = GlassSurfaceElevated,
                border = BorderStroke(1.dp, GlassBorder)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 6.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Voice Action Button
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(
                                if (assistantState == AssistantState.LISTENING)
                                    Brush.linearGradient(listOf(StatusError, ProfessionalPink))
                                else
                                    Brush.linearGradient(listOf(ProfessionalBlue, ProfessionalPurple))
                            )
                            .clickable {
                                val hasMic = ContextCompat.checkSelfPermission(
                                    context,
                                    Manifest.permission.RECORD_AUDIO
                                ) == PackageManager.PERMISSION_GRANTED
                                if (hasMic) {
                                    viewModel.toggleListening()
                                } else {
                                    micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (assistantState == AssistantState.LISTENING) Icons.Default.Stop else Icons.Default.Mic,
                            contentDescription = "Voice Input",
                            tint = Color.White,
                            modifier = Modifier.size(22.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(6.dp))

                    TextField(
                        value = typedInput,
                        onValueChange = { typedInput = it },
                        placeholder = {
                            Text(
                                text = "Ask or type a command...",
                                color = TextMuted,
                                fontSize = 14.sp
                            )
                        },
                        modifier = Modifier.weight(1f),
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            focusedTextColor = TextWhite,
                            unfocusedTextColor = TextWhite,
                            cursorColor = ProfessionalBlue,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent
                        ),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                        keyboardActions = KeyboardActions(
                            onSend = {
                                if (typedInput.isNotBlank()) {
                                    val text = typedInput
                                    typedInput = ""
                                    focusManager.clearFocus()
                                    viewModel.processUserCommand(text)
                                }
                            }
                        ),
                        singleLine = true
                    )

                    IconButton(
                        onClick = {
                            if (typedInput.isNotBlank()) {
                                val text = typedInput
                                typedInput = ""
                                focusManager.clearFocus()
                                viewModel.processUserCommand(text)
                            }
                        },
                        enabled = typedInput.isNotBlank()
                    ) {
                        Icon(
                            imageVector = Icons.Default.Send,
                            contentDescription = "Send Command",
                            tint = if (typedInput.isNotBlank()) ProfessionalBlue else TextMuted.copy(alpha = 0.5f)
                        )
                    }
                }
            }
        }

        // Profile Switcher Dialog
        if (showProfileSelectionDialog) {
            ProfileSelectionDialog(
                profiles = profiles,
                activeProfile = activeProfile,
                onSelectProfile = { profile ->
                    viewModel.selectProfile(profile)
                },
                onCreateNewProfile = {
                    showCreateProfileDialog = true
                },
                onDismiss = { showProfileSelectionDialog = false }
            )
        }

        // Create Profile Dialog
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

        // Voice Training Dialog
        if (voiceTrainingState.isActive) {
            VoiceTrainingDialog(
                trainingState = voiceTrainingState,
                soundLevel = soundLevel,
                onRecordSample = {
                    val hasMic = ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.RECORD_AUDIO
                    ) == PackageManager.PERMISSION_GRANTED
                    if (hasMic) {
                        viewModel.recordTrainingSample()
                    } else {
                        micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
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

        if (showPermissionsDialog) {
            PermissionsDialog(onDismiss = { showPermissionsDialog = false })
        }

        if (isVisionModeActive) {
            VisionScreen(
                viewModel = viewModel,
                onClose = { viewModel.closeVisionMode() },
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

