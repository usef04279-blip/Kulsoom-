package com.example.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.data.model.AssistantState
import com.example.ui.theme.*

@Composable
fun KulsoomOrb(
    state: AssistantState,
    soundLevel: Float,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 230.dp
) {
    val infiniteTransition = rememberInfiniteTransition(label = "orb_anim")

    // Slow rotation
    val rotationAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 10000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotation"
    )

    // Breathing pulse
    val breathingPulse by infiniteTransition.animateFloat(
        initialValue = 0.94f,
        targetValue = 1.06f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = when (state) {
                    AssistantState.THINKING -> 800
                    AssistantState.SPEAKING -> 1200
                    AssistantState.LISTENING -> 600
                    AssistantState.IDLE -> 2600
                },
                easing = FastOutSlowInEasing
            ),
            repeatMode = RepeatMode.Reverse
        ),
        label = "breathing"
    )

    // Ripple wave for listening/speaking
    val rippleScale by infiniteTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = 1.4f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1800, easing = FastOutLinearInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "ripple"
    )
    val rippleAlpha by infiniteTransition.animateFloat(
        initialValue = 0.5f,
        targetValue = 0.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1800, easing = FastOutLinearInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "ripple_alpha"
    )

    // Audio reactive boost
    val dynamicAudioScale = if (state == AssistantState.LISTENING) {
        1.0f + (soundLevel * 0.35f)
    } else 1.0f

    val effectiveScale = breathingPulse * dynamicAudioScale

    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = Offset(this.size.width / 2f, this.size.height / 2f)
            val baseRadius = (this.size.minDimension / 2f) * 0.62f

            // 1. Ambient Glow Field (Blue & Purple diffuse blur aura)
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        ProfessionalBlue.copy(alpha = 0.30f),
                        ProfessionalPurple.copy(alpha = 0.20f),
                        Color.Transparent
                    ),
                    center = center,
                    radius = baseRadius * effectiveScale * 1.5f
                ),
                center = center,
                radius = baseRadius * effectiveScale * 1.45f
            )

            // 2. Active listening/speaking ripple
            if (state == AssistantState.LISTENING || state == AssistantState.SPEAKING) {
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            ProfessionalBlue.copy(alpha = rippleAlpha * 0.6f),
                            ProfessionalPurple.copy(alpha = rippleAlpha * 0.3f),
                            Color.Transparent
                        ),
                        center = center,
                        radius = baseRadius * rippleScale * 1.5f
                    ),
                    center = center,
                    radius = baseRadius * rippleScale * 1.45f
                )
            }

            // 3. Outer Thin Ring (border-2 border-blue-400/20)
            drawCircle(
                color = Color(0x3360A5FA),
                center = center,
                radius = baseRadius * effectiveScale * 1.32f,
                style = Stroke(width = 2.dp.toPx())
            )

            // 4. Middle Ring (border-4 border-purple-400/10)
            drawCircle(
                color = Color(0x1AA855F7),
                center = center,
                radius = baseRadius * effectiveScale * 1.18f,
                style = Stroke(width = 4.dp.toPx())
            )

            // 5. Rotating Gradient Sweep Ring
            rotate(rotationAngle, pivot = center) {
                drawCircle(
                    brush = Brush.sweepGradient(
                        colors = listOf(
                            ProfessionalBlue,
                            ProfessionalPurple,
                            ProfessionalPink,
                            ProfessionalBlue
                        ),
                        center = center
                    ),
                    center = center,
                    radius = baseRadius * effectiveScale * 1.04f,
                    style = Stroke(width = 2.5.dp.toPx())
                )
            }

            // 6. Tri-color Core Glowing Orb (Blue -> Purple -> Pink)
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color(0xFFE0E7FF),
                        ProfessionalBlue,
                        ProfessionalPurple,
                        ProfessionalPink,
                        SpaceBlack.copy(alpha = 0.9f)
                    ),
                    center = Offset(center.x - baseRadius * 0.22f, center.y - baseRadius * 0.22f),
                    radius = baseRadius * effectiveScale
                ),
                center = center,
                radius = baseRadius * effectiveScale * 0.85f
            )

            // 7. Frosted Center Overlay Lens
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.28f),
                        Color(0x330F1115)
                    ),
                    center = center,
                    radius = baseRadius * effectiveScale * 0.5f
                ),
                center = center,
                radius = baseRadius * effectiveScale * 0.55f
            )

            // 8. Subtle Specular Highlight
            drawCircle(
                color = Color.White.copy(alpha = 0.5f),
                center = Offset(center.x - baseRadius * 0.26f, center.y - baseRadius * 0.26f),
                radius = baseRadius * 0.18f
            )
        }

        // Center Indicator (frosted glass pill / icon)
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = Color.White.copy(alpha = 0.18f),
            modifier = Modifier.padding(4.dp)
        ) {
            Box(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                val centerIcon = when (state) {
                    AssistantState.LISTENING -> Icons.Default.GraphicEq
                    AssistantState.THINKING -> Icons.Default.GraphicEq
                    AssistantState.SPEAKING -> Icons.Default.VolumeUp
                    AssistantState.IDLE -> Icons.Default.Mic
                }

                Icon(
                    imageVector = centerIcon,
                    contentDescription = "Kulsoom Status: $state",
                    tint = Color.White,
                    modifier = Modifier.size(28.dp)
                )
            }
        }
    }
}

