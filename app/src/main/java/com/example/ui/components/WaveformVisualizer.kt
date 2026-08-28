package com.example.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.data.model.AssistantState
import com.example.ui.theme.ProfessionalBlue
import com.example.ui.theme.ProfessionalPink
import com.example.ui.theme.ProfessionalPurple

@Composable
fun WaveformVisualizer(
    state: AssistantState,
    soundLevel: Float,
    modifier: Modifier = Modifier,
    barCount: Int = 18
) {
    val infiniteTransition = rememberInfiniteTransition(label = "waveform_anim")

    Row(
        modifier = modifier
            .height(44.dp)
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(4.5.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val topColor = when (state) {
            AssistantState.LISTENING -> ProfessionalBlue
            AssistantState.THINKING -> ProfessionalPurple
            AssistantState.SPEAKING -> ProfessionalPink
            AssistantState.IDLE -> ProfessionalBlue.copy(alpha = 0.35f)
        }
        val bottomColor = when (state) {
            AssistantState.LISTENING -> ProfessionalPurple
            AssistantState.THINKING -> ProfessionalPink
            AssistantState.SPEAKING -> ProfessionalBlue
            AssistantState.IDLE -> ProfessionalPurple.copy(alpha = 0.2f)
        }

        for (i in 0 until barCount) {
            val animFraction by infiniteTransition.animateFloat(
                initialValue = 0.2f,
                targetValue = 1.0f,
                animationSpec = infiniteRepeatable(
                    animation = tween(
                        durationMillis = 320 + (i * 40) % 360,
                        easing = FastOutSlowInEasing
                    ),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "bar_$i"
            )

            val heightMultiplier = when (state) {
                AssistantState.LISTENING -> (0.25f + soundLevel * 0.75f) * animFraction
                AssistantState.THINKING -> 0.3f + animFraction * 0.45f
                AssistantState.SPEAKING -> 0.35f + animFraction * 0.65f
                AssistantState.IDLE -> 0.12f
            }

            val targetHeight = (40.dp * heightMultiplier).coerceIn(4.dp, 40.dp)

            Box(
                modifier = Modifier
                    .width(3.dp)
                    .height(targetHeight)
                    .clip(RoundedCornerShape(2.dp))
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                topColor,
                                bottomColor
                            )
                        )
                    )
            )
        }
    }
}

