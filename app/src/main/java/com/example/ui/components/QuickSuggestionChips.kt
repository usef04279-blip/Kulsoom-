package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.*

data class QuickAction(
    val label: String,
    val icon: ImageVector,
    val command: String
)

@Composable
fun QuickSuggestionChips(
    onCommandSelected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val suggestions = listOf(
        QuickAction("Look & Tell", Icons.Default.Visibility, "Look and tell"),
        QuickAction("Daily Briefing", Icons.Default.WbSunny, "Give me my daily briefing"),
        QuickAction("Open YouTube", Icons.Default.PlayCircle, "Open YouTube"),
        QuickAction("Call Ahmed", Icons.Default.Call, "Call Ahmed"),
        QuickAction("Set Alarm 7 AM", Icons.Default.Alarm, "Set alarm for 7 AM"),
        QuickAction("Toggle Flashlight", Icons.Default.FlashlightOn, "Turn on flashlight"),
        QuickAction("Check Battery", Icons.Default.BatteryChargingFull, "Check battery status"),
        QuickAction("Play Music", Icons.Default.MusicNote, "Play music on Spotify"),
        QuickAction("Take a Note", Icons.Default.NoteAlt, "Note that I need to buy groceries"),
        QuickAction("10 Min Timer", Icons.Default.Timer, "Set a 10 minute timer"),
        QuickAction("What's the time?", Icons.Default.Schedule, "What's the current time?")
    )

    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        suggestions.forEach { item ->
            Surface(
                onClick = { onCommandSelected(item.command) },
                shape = CircleShape,
                color = GlassSurfaceElevated,
                border = BorderStroke(1.dp, GlassBorder),
                modifier = Modifier.height(38.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = item.icon,
                        contentDescription = item.label,
                        tint = ProfessionalBlue,
                        modifier = Modifier.size(15.dp)
                    )
                    Text(
                        text = item.label,
                        color = TextWhite,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}

