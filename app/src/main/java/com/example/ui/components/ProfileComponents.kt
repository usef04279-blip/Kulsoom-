package com.example.ui.components

import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.UserProfile
import com.example.service.VOICE_TRAINING_PROMPTS
import com.example.ui.theme.*
import com.example.ui.viewmodel.AssistantViewModel
import com.example.ui.viewmodel.VoiceTrainingState

val ProfileGradients = listOf(
    listOf(Color(0xFF3B82F6), Color(0xFF8B5CF6)), // Blue -> Purple
    listOf(Color(0xFF10B981), Color(0xFF06B6D4)), // Emerald -> Cyan
    listOf(Color(0xFFEC4899), Color(0xFFF43F5E)), // Pink -> Rose
    listOf(Color(0xFFF59E0B), Color(0xFFEA580C)), // Amber -> Orange
    listOf(Color(0xFF8B5CF6), Color(0xFFD946EF)), // Violet -> Fuchsia
    listOf(Color(0xFF06B6D4), Color(0xFF3B82F6))  // Cyan -> Blue
)

@Composable
fun ProfileAvatarBadge(
    name: String,
    colorIndex: Int = 0,
    size: Int = 36,
    hasVoiceprint: Boolean = false,
    modifier: Modifier = Modifier
) {
    val gradient = ProfileGradients[colorIndex.coerceIn(0, ProfileGradients.lastIndex)]
    val initial = name.firstOrNull()?.uppercase() ?: "U"

    Box(
        modifier = modifier
            .size(size.dp)
            .clip(CircleShape)
            .background(Brush.linearGradient(gradient)),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = initial,
            color = Color.White,
            fontSize = (size * 0.45).sp,
            fontWeight = FontWeight.Bold
        )

        if (hasVoiceprint) {
            Box(
                modifier = Modifier
                    .size((size * 0.32).dp)
                    .align(Alignment.BottomEnd)
                    .clip(CircleShape)
                    .background(SpaceBlack)
                    .padding(1.dp)
                    .clip(CircleShape)
                    .background(BrightTurquoise),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.GraphicEq,
                    contentDescription = "Voiceprint Active",
                    tint = SpaceBlack,
                    modifier = Modifier.size((size * 0.22).dp)
                )
            }
        }
    }
}

@Composable
fun ProfileSelectionDialog(
    profiles: List<UserProfile>,
    activeProfile: UserProfile?,
    onSelectProfile: (UserProfile?) -> Unit,
    onCreateNewProfile: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.AccountCircle,
                    contentDescription = null,
                    tint = ProfessionalBlue
                )
                Text(
                    text = "Switch Profile",
                    color = TextWhite,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "Personal voiceprints, history, and tasks are isolated per profile.",
                    color = TextMuted,
                    fontSize = 12.sp
                )

                Spacer(modifier = Modifier.height(4.dp))

                // Guest / Default Option
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = if (activeProfile == null) ProfessionalBlue.copy(alpha = 0.15f) else GlassSurface,
                    border = BorderStroke(
                        1.dp,
                        if (activeProfile == null) ProfessionalBlue else GlassBorder
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            onSelectProfile(null)
                            onDismiss()
                        }
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(GlassSurfaceElevated),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.PersonOutline,
                                contentDescription = null,
                                tint = TextMuted,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Guest / Standard Mode",
                                color = TextWhite,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "No personal voiceprint filtering",
                                color = TextMuted,
                                fontSize = 11.sp
                            )
                        }
                        if (activeProfile == null) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = "Active",
                                tint = ProfessionalBlue,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }

                // Profile Items
                profiles.forEach { profile ->
                    val isSelected = activeProfile?.id == profile.id
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = if (isSelected) ProfessionalBlue.copy(alpha = 0.15f) else GlassSurface,
                        border = BorderStroke(
                            1.dp,
                            if (isSelected) ProfessionalBlue else GlassBorder
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                onSelectProfile(profile)
                                onDismiss()
                            }
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            ProfileAvatarBadge(
                                name = profile.displayName,
                                colorIndex = profile.avatarColorIndex,
                                size = 36,
                                hasVoiceprint = profile.hasVoiceprint
                            )

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = profile.displayName,
                                    color = TextWhite,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text(
                                        text = if (profile.preferredLanguage.startsWith("ur")) "Urdu" else "English",
                                        color = TextBlueLight,
                                        fontSize = 11.sp
                                    )
                                    if (profile.hasVoiceprint) {
                                        Text(
                                            text = "• Voiceprint Active",
                                            color = BrightTurquoise,
                                            fontSize = 11.sp
                                        )
                                    }
                                }
                            }

                            if (isSelected) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = "Active",
                                    tint = ProfessionalBlue,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                OutlinedButton(
                    onClick = {
                        onDismiss()
                        onCreateNewProfile()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, ProfessionalBlue.copy(alpha = 0.5f))
                ) {
                    Icon(
                        imageVector = Icons.Default.PersonAdd,
                        contentDescription = null,
                        tint = ProfessionalBlue,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Create New Profile", color = ProfessionalBlue, fontWeight = FontWeight.SemiBold)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close", color = TextMuted)
            }
        },
        containerColor = SurfaceDark,
        shape = RoundedCornerShape(22.dp)
    )
}

@Composable
fun VoiceTrainingDialog(
    trainingState: VoiceTrainingState,
    soundLevel: Float,
    onRecordSample: () -> Unit,
    onRerecordSample: (Int) -> Unit = {},
    onFinish: () -> Unit,
    onCancel: () -> Unit
) {
    val currentPrompt = VOICE_TRAINING_PROMPTS.getOrNull(trainingState.currentStep - 1) ?: VOICE_TRAINING_PROMPTS[0]

    AlertDialog(
        onDismissRequest = onCancel,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.GraphicEq,
                    contentDescription = null,
                    tint = BrightTurquoise
                )
                Text(
                    text = "Voice Configuration (5 Samples)",
                    color = TextWhite,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(
                    text = "Train Kulsoom with 5 vocal samples so it accurately recognizes your voice across wake-word triggers and ongoing conversation.",
                    color = TextMuted,
                    fontSize = 13.sp,
                    lineHeight = 18.sp
                )

                // Progress Step Dots
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    for (i in 1..5) {
                        val isDone = i <= trainingState.samplesCollected
                        val isCurrent = i == trainingState.currentStep
                        Box(
                            modifier = Modifier
                                .size(if (isCurrent) 28.dp else 22.dp)
                                .clip(CircleShape)
                                .background(
                                    when {
                                        isDone -> BrightTurquoise
                                        isCurrent -> ProfessionalBlue
                                        else -> SurfaceLight
                                    }
                                )
                                .clickable(enabled = isDone && !trainingState.isRecordingSample) {
                                    onRerecordSample(i)
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            if (isDone && !isCurrent) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = "Sample $i recorded",
                                    tint = SpaceBlack,
                                    modifier = Modifier.size(14.dp)
                                )
                            } else {
                                Text(
                                    text = "$i",
                                    color = if (isCurrent) Color.White else TextMuted,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }

                // Prompt Card
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    color = GlassSurfaceElevated,
                    border = BorderStroke(1.dp, ProfessionalBlue.copy(alpha = 0.6f))
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = ProfessionalBlue.copy(alpha = 0.2f)
                        ) {
                            Text(
                                text = "Sample ${trainingState.currentStep} of 5 • ${currentPrompt.tag}",
                                color = ProfessionalBlue,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }

                        Text(
                            text = "\"${currentPrompt.phrase}\"",
                            color = TextWhite,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )

                        Text(
                            text = currentPrompt.explanation,
                            color = TextMuted,
                            fontSize = 12.sp,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }

                // Interactive Recording Visualizer
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    color = GlassSurfaceElevated,
                    border = BorderStroke(1.dp, GlassBorder)
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        WaveformVisualizer(
                            state = if (trainingState.isRecordingSample) com.example.data.model.AssistantState.LISTENING else com.example.data.model.AssistantState.IDLE,
                            soundLevel = soundLevel,
                            barCount = 14
                        )

                        Text(
                            text = trainingState.feedbackMessage,
                            color = if (trainingState.sampleQualityOk) TextWhite else GlowingAmber,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            lineHeight = 16.sp,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }

                // Privacy Note
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Security,
                        contentDescription = null,
                        tint = BrightTurquoise,
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = "100% on-device mathematical vector. Never uploaded.",
                        color = BrightTurquoise,
                        fontSize = 11.sp
                    )
                }
            }
        },
        confirmButton = {
            if (trainingState.samplesCollected >= 5) {
                Button(
                    onClick = onFinish,
                    colors = ButtonDefaults.buttonColors(containerColor = BrightTurquoise)
                ) {
                    Text("Complete & Save Voiceprint", color = SpaceBlack, fontWeight = FontWeight.Bold)
                }
            } else {
                Button(
                    onClick = onRecordSample,
                    enabled = !trainingState.isRecordingSample,
                    colors = ButtonDefaults.buttonColors(containerColor = ProfessionalBlue)
                ) {
                    Icon(
                        imageVector = Icons.Default.Mic,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (trainingState.isRecordingSample) "Listening..." else "Record Sample ${trainingState.currentStep}",
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onCancel) {
                Text("Cancel", color = TextMuted)
            }
        },
        containerColor = SurfaceDark,
        shape = RoundedCornerShape(22.dp)
    )
}

@Composable
fun CreateOrEditProfileDialog(
    profileToEdit: UserProfile? = null,
    onDismiss: () -> Unit,
    onSaveProfile: (
        name: String,
        language: String,
        voiceTone: String,
        pitch: Float,
        speed: Float,
        onlyMyVoice: Boolean,
        avatarColor: Int,
        launchTraining: Boolean
    ) -> Unit
) {
    var displayName by remember { mutableStateOf(profileToEdit?.displayName ?: "") }
    var selectedLanguage by remember { mutableStateOf(profileToEdit?.preferredLanguage ?: "en-US") }
    var selectedVoiceTone by remember { mutableStateOf(profileToEdit?.voiceTonePreset ?: "Natural Warm") }
    var pitch by remember { mutableFloatStateOf(profileToEdit?.ttsPitch ?: 1.0f) }
    var speed by remember { mutableFloatStateOf(profileToEdit?.ttsSpeed ?: 1.0f) }
    var onlyMyVoice by remember { mutableStateOf(profileToEdit?.onlyRespondToMyVoice ?: false) }
    var avatarColorIndex by remember { mutableIntStateOf(profileToEdit?.avatarColorIndex ?: 0) }

    val isEditing = profileToEdit != null

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = if (isEditing) Icons.Default.Edit else Icons.Default.PersonAdd,
                    contentDescription = null,
                    tint = ProfessionalBlue
                )
                Text(
                    text = if (isEditing) "Edit Profile" else "Create My Profile",
                    color = TextWhite,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Name Field
                OutlinedTextField(
                    value = displayName,
                    onValueChange = { displayName = it },
                    label = { Text("Your Name (What Kulsoom calls you)") },
                    placeholder = { Text("e.g. Munib") },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = TextWhite,
                        unfocusedTextColor = TextWhite,
                        focusedBorderColor = ProfessionalBlue,
                        focusedLabelColor = ProfessionalBlue
                    ),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                // Avatar Color Selection
                Text(text = "Avatar Color", color = TextWhite, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ProfileGradients.indices.forEach { index ->
                        val isSelected = avatarColorIndex == index
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(Brush.linearGradient(ProfileGradients[index]))
                                .border(
                                    width = if (isSelected) 2.dp else 0.dp,
                                    color = if (isSelected) Color.White else Color.Transparent,
                                    shape = CircleShape
                                )
                                .clickable { avatarColorIndex = index },
                            contentAlignment = Alignment.Center
                        ) {
                            if (isSelected) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }

                // Language Selection
                Text(text = "Preferred Language", color = TextWhite, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = selectedLanguage == "en-US",
                        onClick = { selectedLanguage = "en-US" },
                        label = { Text("English") }
                    )
                    FilterChip(
                        selected = selectedLanguage == "ur-PK",
                        onClick = { selectedLanguage = "ur-PK" },
                        label = { Text("Urdu") }
                    )
                    FilterChip(
                        selected = selectedLanguage == "ur-Roman",
                        onClick = { selectedLanguage = "ur-Roman" },
                        label = { Text("Roman Urdu") }
                    )
                }

                // Voice Tone Preset
                Text(text = "Assistant Voice Tone", color = TextWhite, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("Natural Warm", "Professional Crisp", "Soft Melodic").forEach { tone ->
                        FilterChip(
                            selected = selectedVoiceTone == tone,
                            onClick = {
                                selectedVoiceTone = tone
                                when (tone) {
                                    "Natural Warm" -> { pitch = 1.0f; speed = 1.0f }
                                    "Professional Crisp" -> { pitch = 1.15f; speed = 1.05f }
                                    "Soft Melodic" -> { pitch = 0.9f; speed = 0.95f }
                                }
                            },
                            label = { Text(tone) }
                        )
                    }
                }

                // Pitch & Speed Sliders
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Voice Pitch", color = TextMuted, fontSize = 12.sp)
                        Text(String.format(java.util.Locale.US, "%.2fx", pitch), color = ProfessionalBlue, fontSize = 12.sp)
                    }
                    Slider(
                        value = pitch,
                        onValueChange = { pitch = it },
                        valueRange = 0.7f..1.4f,
                        colors = SliderDefaults.colors(thumbColor = ProfessionalBlue, activeTrackColor = ProfessionalBlue)
                    )
                }

                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Speech Speed", color = TextMuted, fontSize = 12.sp)
                        Text(String.format(java.util.Locale.US, "%.2fx", speed), color = ProfessionalBlue, fontSize = 12.sp)
                    }
                    Slider(
                        value = speed,
                        onValueChange = { speed = it },
                        valueRange = 0.7f..1.4f,
                        colors = SliderDefaults.colors(thumbColor = ProfessionalBlue, activeTrackColor = ProfessionalBlue)
                    )
                }

                // Strict Voice Verification Switch
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = GlassSurfaceElevated,
                    border = BorderStroke(1.dp, GlassBorder)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Only respond to my voice",
                                color = TextWhite,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "Assistant stays silent for other unrecognized voices",
                                color = TextMuted,
                                fontSize = 11.sp
                            )
                        }
                        Switch(
                            checked = onlyMyVoice,
                            onCheckedChange = { onlyMyVoice = it },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = ProfessionalBlue
                            )
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (displayName.isNotBlank()) {
                        onSaveProfile(
                            displayName,
                            selectedLanguage,
                            selectedVoiceTone,
                            pitch,
                            speed,
                            onlyMyVoice,
                            avatarColorIndex,
                            !isEditing // Launch voice training if creating new
                        )
                        onDismiss()
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = ProfessionalBlue)
            ) {
                Text(
                    text = if (isEditing) "Save Changes" else "Proceed to Voice Training",
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = TextMuted)
            }
        },
        containerColor = SurfaceDark,
        shape = RoundedCornerShape(22.dp)
    )
}
