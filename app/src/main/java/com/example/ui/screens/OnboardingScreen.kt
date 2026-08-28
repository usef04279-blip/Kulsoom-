package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.AssistantState
import com.example.service.VOICE_TRAINING_PROMPTS
import com.example.ui.components.ProfileGradients
import com.example.ui.components.WaveformVisualizer
import com.example.ui.theme.*
import com.example.ui.viewmodel.AssistantViewModel

@Composable
fun OnboardingScreen(
    viewModel: AssistantViewModel,
    onOnboardingComplete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current

    var displayName by remember { mutableStateOf("") }
    var selectedLanguage by remember { mutableStateOf("en-US") }
    var selectedColorIndex by remember { mutableIntStateOf(0) }
    var nameError by remember { mutableStateOf<String?>(null) }

    val voiceTrainingState by viewModel.voiceTrainingState.collectAsStateWithLifecycle()
    val soundLevel by viewModel.soundLevel.collectAsStateWithLifecycle()

    val hasMicPermission = ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.RECORD_AUDIO
    ) == PackageManager.PERMISSION_GRANTED

    val micLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            if (!voiceTrainingState.isActive) {
                viewModel.startVoiceTraining()
            }
            viewModel.recordTrainingSample()
        }
    }

    val scrollState = rememberScrollState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(SpaceBlack)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .verticalScroll(scrollState),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            Spacer(modifier = Modifier.height(12.dp))

            // Brand Header & Avatar
            HeaderSection(
                name = displayName,
                colorIndex = selectedColorIndex,
                hasVoiceprint = voiceTrainingState.samplesCollected >= 5
            )

            // Step 1: Display Name Card
            DisplayNameCard(
                displayName = displayName,
                onNameChange = {
                    displayName = it
                    if (it.isNotBlank()) nameError = null
                },
                error = nameError,
                selectedColorIndex = selectedColorIndex,
                onColorSelect = { selectedColorIndex = it },
                onDone = { focusManager.clearFocus() }
            )

            // Step 2: 5-Step Voice Training Card & Progress Tracker
            VoiceTrainingSectionCard(
                trainingState = voiceTrainingState,
                soundLevel = soundLevel,
                onStartOrRecord = {
                    if (hasMicPermission) {
                        if (!voiceTrainingState.isActive) {
                            viewModel.startVoiceTraining()
                        }
                        viewModel.recordTrainingSample()
                    } else {
                        micLauncher.launch(Manifest.permission.RECORD_AUDIO)
                    }
                },
                onResetTraining = {
                    viewModel.startVoiceTraining()
                }
            )

            Spacer(modifier = Modifier.height(8.dp))
        }

        // Bottom Fixed Action Bar
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = SurfaceDark.copy(alpha = 0.95f),
            border = BorderStroke(1.dp, GlassBorder)
        ) {
            Column(
                modifier = Modifier
                    .padding(horizontal = 24.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                val isTrainingComplete = voiceTrainingState.samplesCollected >= 5

                Button(
                    onClick = {
                        val trimmedName = displayName.trim()
                        if (trimmedName.isBlank()) {
                            nameError = "Please enter your name to continue"
                            return@Button
                        }

                        viewModel.completeOnboardingWithProfile(
                            displayName = trimmedName,
                            preferredLanguage = selectedLanguage,
                            voiceTonePreset = "Natural Warm",
                            ttsPitch = 1.0f,
                            ttsSpeed = 1.0f,
                            onlyRespondToMyVoice = isTrainingComplete,
                            avatarColorIndex = selectedColorIndex,
                            voiceprintSamples = if (isTrainingComplete) voiceTrainingState.collectedEmbeddings else emptyList()
                        )
                        viewModel.cancelVoiceTraining()
                        onOnboardingComplete()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp)
                        .testTag("complete_onboarding_button"),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isTrainingComplete) BrightTurquoise else ProfessionalBlue
                    )
                ) {
                    Icon(
                        imageVector = if (isTrainingComplete) Icons.Default.CheckCircle else Icons.Default.ArrowForward,
                        contentDescription = null,
                        tint = if (isTrainingComplete) SpaceBlack else Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (isTrainingComplete) "Complete Setup & Get Started" else "Continue to Assistant",
                        color = if (isTrainingComplete) SpaceBlack else Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                if (!isTrainingComplete) {
                    TextButton(
                        onClick = {
                            val trimmedName = displayName.trim().ifBlank { "User" }
                            viewModel.completeOnboardingWithProfile(
                                displayName = trimmedName,
                                preferredLanguage = selectedLanguage,
                                voiceTonePreset = "Natural Warm",
                                avatarColorIndex = selectedColorIndex,
                                voiceprintSamples = emptyList()
                            )
                            viewModel.cancelVoiceTraining()
                            onOnboardingComplete()
                        },
                        modifier = Modifier.testTag("skip_voice_training_button")
                    ) {
                        Text(
                            text = "Skip voice training and set up later",
                            color = TextMuted,
                            fontSize = 13.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HeaderSection(
    name: String,
    colorIndex: Int,
    hasVoiceprint: Boolean
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        val gradient = ProfileGradients[colorIndex.coerceIn(0, ProfileGradients.lastIndex)]

        // Glowing Hero Icon / Profile Badge
        Box(
            modifier = Modifier
                .size(76.dp)
                .clip(CircleShape)
                .background(Brush.linearGradient(gradient))
                .border(2.dp, GlassBorder, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            if (name.isNotBlank()) {
                Text(
                    text = name.first().uppercase(),
                    color = Color.White,
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Bold
                )
            } else {
                Icon(
                    imageVector = Icons.Default.AutoAwesome,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(38.dp)
                )
            }

            if (hasVoiceprint) {
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .align(Alignment.BottomEnd)
                        .clip(CircleShape)
                        .background(SpaceBlack)
                        .padding(2.dp)
                        .clip(CircleShape)
                        .background(BrightTurquoise),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.GraphicEq,
                        contentDescription = "Voiceprint Saved",
                        tint = SpaceBlack,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
        }

        Text(
            text = "Welcome to Kulsoom",
            color = TextWhite,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = (-0.5).sp
        )

        Text(
            text = "Your personalized, on-device AI voice assistant. Let's personalize your experience.",
            color = TextMuted,
            fontSize = 13.sp,
            textAlign = TextAlign.Center,
            lineHeight = 18.sp,
            modifier = Modifier.padding(horizontal = 12.dp)
        )
    }
}

@Composable
private fun DisplayNameCard(
    displayName: String,
    onNameChange: (String) -> Unit,
    error: String?,
    selectedColorIndex: Int,
    onColorSelect: (Int) -> Unit,
    onDone: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = GlassSurfaceElevated),
        border = BorderStroke(1.dp, GlassBorder)
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Badge,
                    contentDescription = null,
                    tint = ProfessionalBlue,
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    text = "Profile Information",
                    color = TextWhite,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            OutlinedTextField(
                value = displayName,
                onValueChange = onNameChange,
                label = { Text("Your Display Name") },
                placeholder = { Text("e.g., Munib, Sarah, Alex") },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Person,
                        contentDescription = null,
                        tint = ProfessionalBlue
                    )
                },
                trailingIcon = {
                    if (displayName.isNotEmpty()) {
                        IconButton(onClick = { onNameChange("") }) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Clear",
                                tint = TextMuted,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                },
                isError = error != null,
                supportingText = {
                    if (error != null) {
                        Text(text = error, color = GlowingRose, fontSize = 11.sp)
                    } else {
                        Text(text = "Kulsoom uses your name for personal greetings and reminders.", color = TextMuted, fontSize = 11.sp)
                    }
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Words,
                    imeAction = ImeAction.Done
                ),
                keyboardActions = KeyboardActions(onDone = { onDone() }),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = ProfessionalBlue,
                    unfocusedBorderColor = GlassBorder,
                    focusedTextColor = TextWhite,
                    unfocusedTextColor = TextWhite,
                    focusedLabelColor = ProfessionalBlue,
                    unfocusedLabelColor = TextMuted,
                    cursorColor = ProfessionalBlue
                ),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("onboarding_display_name_input")
            )

            // Avatar Color Themes
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Choose Avatar Accent",
                    color = TextMuted,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    ProfileGradients.forEachIndexed { index, gradient ->
                        val isSelected = selectedColorIndex == index
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(Brush.linearGradient(gradient))
                                .border(
                                    width = if (isSelected) 2.5.dp else 1.dp,
                                    color = if (isSelected) Color.White else Color.Transparent,
                                    shape = CircleShape
                                )
                                .clickable { onColorSelect(index) },
                            contentAlignment = Alignment.Center
                        ) {
                            if (isSelected) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun VoiceTrainingSectionCard(
    trainingState: com.example.ui.viewmodel.VoiceTrainingState,
    soundLevel: Float,
    onStartOrRecord: () -> Unit,
    onResetTraining: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = GlassSurfaceElevated),
        border = BorderStroke(1.dp, if (trainingState.samplesCollected >= 5) BrightTurquoise else GlassBorder)
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Header with Security Badge
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
                        imageVector = Icons.Default.GraphicEq,
                        contentDescription = null,
                        tint = BrightTurquoise,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = "5-Step Voice Training",
                        color = TextWhite,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = BrightTurquoise.copy(alpha = 0.15f)
                ) {
                    Text(
                        text = "100% On-Device",
                        color = BrightTurquoise,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                    )
                }
            }

            Text(
                text = "Record 5 quick samples of \"Kulsoom\" to train your on-device voiceprint for speaker recognition.",
                color = TextMuted,
                fontSize = 12.sp,
                lineHeight = 16.sp
            )

            // Progress Tracker: Linear indicator + 5 Step Dots
            VoiceTrainingProgressTracker(
                samplesCollected = trainingState.samplesCollected,
                currentStep = trainingState.currentStep,
                isActive = trainingState.isActive
            )

            // Step Prompt Card
            val currentPrompt = VOICE_TRAINING_PROMPTS.getOrNull(trainingState.currentStep - 1) ?: VOICE_TRAINING_PROMPTS[0]
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                color = GlassSurfaceElevated,
                border = BorderStroke(1.dp, ProfessionalBlue.copy(alpha = 0.5f))
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = ProfessionalBlue.copy(alpha = 0.2f)
                    ) {
                        Text(
                            text = "Sample ${trainingState.currentStep} of 5 • ${currentPrompt.tag}",
                            color = ProfessionalBlue,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                    Text(
                        text = "\"${currentPrompt.phrase}\"",
                        color = TextWhite,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                    Text(
                        text = currentPrompt.explanation,
                        color = TextMuted,
                        fontSize = 11.sp,
                        textAlign = TextAlign.Center
                    )
                }
            }

            // Active Recording & Waveform Card
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                color = GlassSurface,
                border = BorderStroke(1.dp, GlassBorder)
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    WaveformVisualizer(
                        state = if (trainingState.isRecordingSample) AssistantState.LISTENING else AssistantState.IDLE,
                        soundLevel = soundLevel,
                        barCount = 14
                    )

                    Text(
                        text = if (!trainingState.isActive && trainingState.samplesCollected == 0) {
                            "Ready to start. Tap 'Start Voice Training' below."
                        } else {
                            trainingState.feedbackMessage
                        },
                        color = if (trainingState.sampleQualityOk) TextWhite else GlowingAmber,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        textAlign = TextAlign.Center,
                        lineHeight = 17.sp,
                        modifier = Modifier.padding(horizontal = 8.dp)
                    )
                }
            }

            // Training Action Button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val isCompleted = trainingState.samplesCollected >= 5

                Button(
                    onClick = onStartOrRecord,
                    enabled = !trainingState.isRecordingSample && !isCompleted,
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isCompleted) StatusSuccess else ProfessionalBlue,
                        disabledContainerColor = if (isCompleted) StatusSuccess else ProfessionalBlue.copy(alpha = 0.5f)
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("start_voice_training_button")
                ) {
                    val scale by animateFloatAsState(
                        targetValue = if (trainingState.isRecordingSample) 1.2f else 1f,
                        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
                        label = "mic_pulse"
                    )

                    Icon(
                        imageVector = when {
                            isCompleted -> Icons.Default.Check
                            trainingState.isRecordingSample -> Icons.Default.Mic
                            else -> Icons.Default.Mic
                        },
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier
                            .size(18.dp)
                            .scale(scale)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = when {
                            isCompleted -> "Voice Trained (5/5)"
                            trainingState.isRecordingSample -> "Listening for Sample ${trainingState.currentStep}..."
                            trainingState.isActive -> "Record Sample ${trainingState.currentStep} of 5"
                            else -> "Start Voice Training"
                        },
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                }

                if (trainingState.isActive || trainingState.samplesCollected > 0) {
                    IconButton(
                        onClick = onResetTraining,
                        modifier = Modifier
                            .size(48.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(GlassSurface)
                            .border(1.dp, GlassBorder, RoundedCornerShape(14.dp))
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Restart Training",
                            tint = TextMuted,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun VoiceTrainingProgressTracker(
    samplesCollected: Int,
    currentStep: Int,
    isActive: Boolean
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Label with fraction and percentage
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Progress: $samplesCollected of 5 Samples",
                color = TextWhite,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = "${(samplesCollected * 20)}%",
                color = if (samplesCollected == 5) BrightTurquoise else ProfessionalBlue,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
        }

        // Linear Progress Bar
        val animatedProgress by animateFloatAsState(
            targetValue = (samplesCollected / 5f).coerceIn(0f, 1f),
            animationSpec = tween(durationMillis = 400),
            label = "training_progress"
        )

        LinearProgressIndicator(
            progress = { animatedProgress },
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(CircleShape),
            color = if (samplesCollected == 5) BrightTurquoise else ProfessionalBlue,
            trackColor = GlassSurface
        )

        // 5 Step Circular Indicators
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            for (step in 1..5) {
                val isDone = step <= samplesCollected
                val isCurrent = step == currentStep && isActive && !isDone

                val circleBg by animateColorAsState(
                    targetValue = when {
                        isDone -> BrightTurquoise
                        isCurrent -> ProfessionalBlue
                        else -> GlassSurface
                    },
                    label = "circle_color_$step"
                )

                val borderStroke = when {
                    isCurrent -> BorderStroke(2.dp, Color.White)
                    isDone -> null
                    else -> BorderStroke(1.dp, GlassBorder)
                }

                Surface(
                    shape = CircleShape,
                    color = circleBg,
                    border = borderStroke,
                    modifier = Modifier.size(28.dp)
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.fillMaxSize()
                    ) {
                        if (isDone) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = "Step $step complete",
                                tint = SpaceBlack,
                                modifier = Modifier.size(16.dp)
                            )
                        } else {
                            Text(
                                text = "$step",
                                color = if (isCurrent) Color.White else TextMuted,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
}
