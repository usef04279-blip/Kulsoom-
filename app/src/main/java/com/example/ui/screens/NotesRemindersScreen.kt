package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.AssistantNote
import com.example.data.model.AssistantReminder
import com.example.ui.theme.*
import com.example.ui.viewmodel.AssistantViewModel
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun NotesRemindersScreen(
    viewModel: AssistantViewModel,
    modifier: Modifier = Modifier
) {
    val notes by viewModel.notes.collectAsStateWithLifecycle()
    val reminders by viewModel.reminders.collectAsStateWithLifecycle()

    var selectedTabIndex by remember { mutableIntStateOf(0) }
    var showAddDialog by remember { mutableStateOf(false) }

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
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Notes & Reminders",
                    color = TextWhite,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Local synced tasks & voice memos",
                    color = TextMuted,
                    fontSize = 12.sp
                )
            }

            FilledTonalButton(
                onClick = { showAddDialog = true },
                colors = ButtonDefaults.filledTonalButtonColors(containerColor = ProfessionalBlue.copy(alpha = 0.2f)),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Add",
                    tint = ProfessionalBlue,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text("New", color = ProfessionalBlue, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            }
        }

        // Tabs
        TabRow(
            selectedTabIndex = selectedTabIndex,
            containerColor = SurfaceDark,
            contentColor = ProfessionalBlue,
            indicator = {},
            divider = {}
        ) {
            Tab(
                selected = selectedTabIndex == 0,
                onClick = { selectedTabIndex = 0 },
                text = {
                    Text(
                        "Voice Notes (${notes.size})",
                        color = if (selectedTabIndex == 0) ProfessionalBlue else TextMuted,
                        fontWeight = if (selectedTabIndex == 0) FontWeight.Bold else FontWeight.Normal
                    )
                }
            )
            Tab(
                selected = selectedTabIndex == 1,
                onClick = { selectedTabIndex = 1 },
                text = {
                    Text(
                        "Reminders (${reminders.size})",
                        color = if (selectedTabIndex == 1) ProfessionalBlue else TextMuted,
                        fontWeight = if (selectedTabIndex == 1) FontWeight.Bold else FontWeight.Normal
                    )
                }
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        if (selectedTabIndex == 0) {
            // Notes Tab
            if (notes.isEmpty()) {
                EmptyStateView(
                    icon = Icons.Default.NoteAlt,
                    title = "No voice notes saved",
                    subtitle = "Say \"Note that I need to buy groceries\" or tap New."
                )
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(bottom = 80.dp)
                ) {
                    items(notes, key = { it.id }) { note ->
                        NoteCard(note = note, onDelete = { viewModel.deleteNote(note.id) })
                    }
                }
            }
        } else {
            // Reminders Tab
            if (reminders.isEmpty()) {
                EmptyStateView(
                    icon = Icons.Default.Alarm,
                    title = "No active reminders",
                    subtitle = "Say \"Remind me to call bank at 5 PM\" or tap New."
                )
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(bottom = 80.dp)
                ) {
                    items(reminders, key = { it.id }) { reminder ->
                        ReminderCard(
                            reminder = reminder,
                            onToggle = { viewModel.toggleReminder(reminder) },
                            onDelete = { viewModel.deleteReminder(reminder.id) }
                        )
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        AddNoteOrReminderDialog(
            initialTab = selectedTabIndex,
            onDismiss = { showAddDialog = false },
            onAddNote = { title, content ->
                viewModel.addNote(title, content)
                showAddDialog = false
            },
            onAddReminder = { title, minutes ->
                val time = System.currentTimeMillis() + minutes * 60 * 1000L
                viewModel.addReminder(title, time)
                showAddDialog = false
            }
        )
    }
}

@Composable
private fun NoteCard(
    note: AssistantNote,
    onDelete: () -> Unit
) {
    val dateFormat = remember { SimpleDateFormat("MMM d, yyyy · h:mm a", Locale.getDefault()) }
    val timeStr = remember(note.createdAt) { dateFormat.format(Date(note.createdAt)) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = GlassSurfaceElevated),
        border = BorderStroke(1.dp, GlassBorder)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = note.title,
                    color = ProfessionalBlue,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                )
                IconButton(onClick = onDelete, modifier = Modifier.size(24.dp)) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Delete",
                        tint = TextMuted,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = note.content,
                color = TextWhite,
                fontSize = 14.sp,
                lineHeight = 20.sp
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = timeStr,
                color = TextMuted,
                fontSize = 11.sp
            )
        }
    }
}

@Composable
private fun ReminderCard(
    reminder: AssistantReminder,
    onToggle: () -> Unit,
    onDelete: () -> Unit
) {
    val dateFormat = remember { SimpleDateFormat("EEE, MMM d · h:mm a", Locale.getDefault()) }
    val timeStr = remember(reminder.triggerTimeMillis) { dateFormat.format(Date(reminder.triggerTimeMillis)) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (reminder.isCompleted) GlassSurface.copy(alpha = 0.5f) else GlassSurfaceElevated
        ),
        border = BorderStroke(1.dp, GlassBorder)
    ) {
        Row(
            modifier = Modifier
                .padding(14.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Checkbox(
                checked = reminder.isCompleted,
                onCheckedChange = { onToggle() },
                colors = CheckboxDefaults.colors(
                    checkedColor = StatusSuccess,
                    uncheckedColor = TextMuted
                )
            )

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = reminder.title,
                    color = if (reminder.isCompleted) TextMuted else TextWhite,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    textDecoration = if (reminder.isCompleted) TextDecoration.LineThrough else TextDecoration.None
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "Due: $timeStr",
                    color = if (reminder.isCompleted) TextMuted.copy(alpha = 0.5f) else GlowingAmber,
                    fontSize = 12.sp
                )
            }

            IconButton(onClick = onDelete, modifier = Modifier.size(28.dp)) {
                Icon(
                    imageVector = Icons.Default.DeleteOutline,
                    contentDescription = "Delete",
                    tint = TextMuted,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
private fun EmptyStateView(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, subtitle: String) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = TextMuted.copy(alpha = 0.4f),
                modifier = Modifier.size(60.dp)
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(text = title, color = TextWhite, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            Spacer(modifier = Modifier.height(4.dp))
            Text(text = subtitle, color = TextMuted, fontSize = 13.sp)
        }
    }
}

@Composable
private fun AddNoteOrReminderDialog(
    initialTab: Int,
    onDismiss: () -> Unit,
    onAddNote: (String, String) -> Unit,
    onAddReminder: (String, Int) -> Unit
) {
    var isNote by remember { mutableStateOf(initialTab == 0) }
    var title by remember { mutableStateOf("") }
    var content by remember { mutableStateOf("") }
    var minutesFromNow by remember { mutableStateOf("30") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isNote) "Create Note" else "Create Reminder", color = TextWhite) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = isNote,
                        onClick = { isNote = true },
                        label = { Text("Note") }
                    )
                    FilterChip(
                        selected = !isNote,
                        onClick = { isNote = false },
                        label = { Text("Reminder") }
                    )
                }

                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text(if (isNote) "Title" else "Reminder Title") },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = TextWhite,
                        unfocusedTextColor = TextWhite,
                        focusedBorderColor = ProfessionalBlue,
                        focusedLabelColor = ProfessionalBlue
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                if (isNote) {
                    OutlinedTextField(
                        value = content,
                        onValueChange = { content = it },
                        label = { Text("Content") },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = TextWhite,
                            unfocusedTextColor = TextWhite,
                            focusedBorderColor = ProfessionalBlue,
                            focusedLabelColor = ProfessionalBlue
                        ),
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 3
                    )
                } else {
                    OutlinedTextField(
                        value = minutesFromNow,
                        onValueChange = { minutesFromNow = it.filter { ch -> ch.isDigit() } },
                        label = { Text("Trigger in (minutes)") },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = TextWhite,
                            unfocusedTextColor = TextWhite,
                            focusedBorderColor = ProfessionalBlue,
                            focusedLabelColor = ProfessionalBlue
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (title.isNotBlank()) {
                        if (isNote) {
                            onAddNote(title, content.ifBlank { title })
                        } else {
                            val min = minutesFromNow.toIntOrNull() ?: 30
                            onAddReminder(title, min)
                        }
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = ProfessionalBlue)
            ) {
                Text("Save", color = Color.White, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = TextMuted)
            }
        },
        containerColor = SurfaceDark
    )
}

