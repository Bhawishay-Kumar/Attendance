package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.OutfitFontFamily
import com.example.ui.theme.PlusJakartaSansFontFamily
import com.example.ui.theme.SpaceGroteskFontFamily
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

data class AttendanceToast(
    val message: String,
    val color: Color,
    val emoji: String = "✨"
)

/**
 * Shake modifier for locked actions / invalid attempts
 */
fun Modifier.shake(trigger: Int): Modifier = graphicsLayer {
    // Shake applied via custom modifier if needed or use ShakeBox
}

@Composable
fun ShakeBox(
    trigger: Int,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val shakeOffset = remember { Animatable(0f) }

    LaunchedEffect(trigger) {
        if (trigger > 0) {
            shakeOffset.snapTo(0f)
            shakeOffset.animateTo(-8f, tween(50, easing = LinearEasing))
            shakeOffset.animateTo(8f, tween(50, easing = LinearEasing))
            shakeOffset.animateTo(-6f, tween(50, easing = LinearEasing))
            shakeOffset.animateTo(6f, tween(50, easing = LinearEasing))
            shakeOffset.animateTo(-3f, tween(40, easing = LinearEasing))
            shakeOffset.animateTo(3f, tween(40, easing = LinearEasing))
            shakeOffset.animateTo(0f, tween(30, easing = LinearEasing))
        }
    }

    Box(
        modifier = modifier.offset { IntOffset(shakeOffset.value.roundToInt(), 0) }
    ) {
        content()
    }
}

/**
 * Breathing neon border animation for CPT-II Lab & Featured 2-Hour Blocks
 */
@Composable
fun Modifier.neonPulse(
    glowColor: Color = Color(0xFF6366F1),
    enabled: Boolean = true
): Modifier {
    if (!enabled) return this

    val infiniteTransition = rememberInfiniteTransition(label = "neonPulse")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.35f,
        targetValue = 0.95f,
        animationSpec = infiniteRepeatable(
            animation = tween(1900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "neonAlpha"
    )

    val shadowElevation by infiniteTransition.animateFloat(
        initialValue = 4f,
        targetValue = 16f,
        animationSpec = infiniteRepeatable(
            animation = tween(1900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "neonElevation"
    )

    return this
        .shadow(shadowElevation.dp, RoundedCornerShape(20.dp), spotColor = glowColor.copy(alpha = alpha))
        .border(
            width = 1.5.dp,
            brush = Brush.linearGradient(
                listOf(
                    glowColor.copy(alpha = alpha),
                    glowColor.copy(alpha = alpha * 0.6f),
                    glowColor.copy(alpha = alpha)
                )
            ),
            shape = RoundedCornerShape(20.dp)
        )
}



/**
 * Floating glassmorphic confirmation toast banner
 */
@Composable
fun FloatingAttendanceToast(
    toast: AttendanceToast?,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = toast != null,
        enter = slideInVertically(initialOffsetY = { it * 2 }) + fadeIn(tween(250)),
        exit = slideOutVertically(targetOffsetY = { it * 2 }) + fadeOut(tween(250)),
        modifier = modifier
    ) {
        if (toast != null) {
            Box(
                modifier = Modifier
                    .shadow(16.dp, RoundedCornerShape(9999.dp), spotColor = toast.color.copy(alpha = 0.6f))
                    .clip(RoundedCornerShape(9999.dp))
                    .background(
                        Brush.horizontalGradient(
                            listOf(
                                Color(0xFF0F172A).copy(alpha = 0.92f),
                                Color(0xFF1E1B4B).copy(alpha = 0.94f)
                            )
                        )
                    )
                    .border(
                        1.5.dp,
                        Brush.horizontalGradient(listOf(toast.color, toast.color.copy(alpha = 0.4f))),
                        RoundedCornerShape(9999.dp)
                    )
                    .padding(horizontal = 18.dp, vertical = 10.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .clip(CircleShape)
                            .background(toast.color.copy(alpha = 0.25f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = toast.emoji,
                            fontSize = 13.sp
                        )
                    }

                    Text(
                        text = toast.message,
                        fontFamily = PlusJakartaSansFontFamily,
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }
        }
    }
}
