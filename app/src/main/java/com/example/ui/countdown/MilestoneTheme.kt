package com.example.ui.countdown

import android.provider.Settings
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.MilestoneAccent
import com.example.ui.theme.DarkCardBorder
import com.example.ui.theme.DarkCardSurface
import com.example.ui.theme.DarkNebulaGlow
import com.example.ui.theme.DarkSpecularHighlight
import com.example.ui.theme.LightAuraOrb1
import com.example.ui.theme.LightAuraOrb2
import com.example.ui.theme.DarkTextMuted
import com.example.ui.theme.DarkTextPrimary
import com.example.ui.theme.DarkTextSecondary
import com.example.ui.theme.LightCardBorder
import com.example.ui.theme.LightCardSurface
import com.example.ui.theme.LightTextMuted
import com.example.ui.theme.LightTextPrimary
import com.example.ui.theme.LightTextSecondary
import com.example.ui.theme.LocalGlassTheme
import com.example.ui.theme.PlusJakartaSansFontFamily
import kotlinx.coroutines.delay

/**
 * Theme-aware colours for Academic Milestones. Everything is derived from the app's own light/dark
 * tokens (ui/theme/Color.kt + LocalGlassTheme) and cross-fades when the app theme is switched, so
 * there is no separate toggle and no hard-coded light-only colour in the components.
 */
@Immutable
class MilestoneColors(
    val isDark: Boolean,
    private val mix: Float,
    val surface: Color,
    val surfaceStrong: Color,
    val border: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textMuted: Color,
    val track: Color,
    val fieldBg: Color,
    val sheetBg: Color,
    val buttonStart: Color,
    val buttonEnd: Color,
    val danger: Color,
    val success: Color,
    /** Backdrop: base gradient plus three soft "aurora" orbs. */
    val baseTop: Color,
    val baseBottom: Color,
    val orbA: Color,
    val orbB: Color,
    val orbC: Color,
    /** Top-edge specular highlight used on glass surfaces. */
    val sheen: Color
) {
    /** Accent colour for the current theme (slightly lighter in dark mode for contrast). */
    fun accent(a: MilestoneAccent): Color {
        val (light, dark) = accentPair(a)
        return lerp(light, dark, mix)
    }

    private fun accentPair(a: MilestoneAccent): Pair<Color, Color> = when (a) {
        MilestoneAccent.PINK -> Color(0xFFDB2777) to Color(0xFFF472B6)
        MilestoneAccent.VIOLET -> Color(0xFF6D28D9) to Color(0xFFA78BFA)
        MilestoneAccent.BLUE -> Color(0xFF2563EB) to Color(0xFF60A5FA)
        MilestoneAccent.GREEN -> Color(0xFF059669) to Color(0xFF34D399)
        MilestoneAccent.AMBER -> Color(0xFFD97706) to Color(0xFFFBBF24)
        MilestoneAccent.RED -> Color(0xFFE11D48) to Color(0xFFFB7185)
    }

    val buttonBrush: Brush get() = Brush.horizontalGradient(listOf(buttonStart, buttonEnd))
}

@Composable
fun rememberMilestoneColors(): MilestoneColors {
    val glass = LocalGlassTheme.current
    val mix by animateFloatAsState(
        targetValue = if (glass.isDark) 1f else 0f,
        animationSpec = tween(450),
        label = "milestoneThemeMix"
    )
    return remember(glass.isDark, mix) {
        MilestoneColors(
            isDark = glass.isDark,
            mix = mix,
            surface = lerp(LightCardSurface, DarkCardSurface, mix),
            surfaceStrong = lerp(Color(0xFFFFFFFF), Color(0xFF151D2E), mix),
            border = lerp(LightCardBorder, DarkCardBorder, mix),
            textPrimary = lerp(LightTextPrimary, DarkTextPrimary, mix),
            textSecondary = lerp(LightTextSecondary, DarkTextSecondary, mix),
            textMuted = lerp(LightTextMuted, DarkTextMuted, mix),
            track = lerp(Color(0xFFE2E8F0), Color(0xFF263150), mix),
            fieldBg = lerp(Color(0xFFF8FAFF), Color(0xFF131B31), mix),
            sheetBg = lerp(Color(0xFFF1F4FF), Color(0xFF0B0F19), mix),
            buttonStart = lerp(Color(0xFF4F46E5), Color(0xFF6366F1), mix),
            buttonEnd = lerp(Color(0xFF7C3AED), Color(0xFF8B5CF6), mix),
            danger = lerp(Color(0xFFE11D48), Color(0xFFFB7185), mix),
            success = lerp(Color(0xFF059669), Color(0xFF34D399), mix),
            baseTop = lerp(LightCardSurface, DarkCardSurface, mix),
            baseBottom = lerp(Color(0xB3EEF2FF), Color(0xB80B1020), mix),
            orbA = lerp(LightAuraOrb1.copy(alpha = 0.55f), DarkNebulaGlow, mix),
            orbB = lerp(LightAuraOrb2.copy(alpha = 0.50f), Color(0x206366F1), mix),
            orbC = lerp(Color(0x33C7D2FE), Color(0x168B5CF6), mix),
            sheen = lerp(Color(0xCCFFFFFF), DarkSpecularHighlight, mix)
        )
    }
}

/** False when the user turned system animations off; components then snap instead of animating. */
@Composable
fun rememberMotionEnabled(): Boolean {
    val resolver = LocalContext.current.contentResolver
    return remember(resolver) {
        runCatching {
            Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) != 0f
        }.getOrDefault(true)
    }
}

/** Ripple + spring press-scale in one modifier (clip the shape before calling it for a clipped ripple). */
@Composable
fun Modifier.pressableScale(
    enabled: Boolean = true,
    pressedScale: Float = 0.96f,
    role: Role? = Role.Button,
    onClick: () -> Unit
): Modifier {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed && enabled) pressedScale else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "pressScale"
    )
    return this
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
        .clickable(
            interactionSource = source,
            indication = LocalIndication.current,
            enabled = enabled,
            role = role,
            onClick = onClick
        )
}

@Composable
fun GradientButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    /** Gentle breathing glow, meant for the one primary call-to-action on screen. */
    pulse: Boolean = false
) {
    val colors = rememberMilestoneColors()
    val motion = rememberMotionEnabled()
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed && enabled) 0.95f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "gbPress"
    )
    val iconSpin by animateFloatAsState(
        targetValue = if (pressed && enabled) 90f else 0f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
        label = "gbIconSpin"
    )
    // One-shot light sweep when the button first appears.
    val shine = remember { Animatable(if (motion) 0f else 1f) }
    LaunchedEffect(Unit) {
        if (motion) {
            delay(350)
            shine.animateTo(1f, tween(900, easing = LinearEasing))
        }
    }
    val glow = if (pulse && motion) {
        rememberInfiniteTransition(label = "gbGlow").animateFloat(
            initialValue = 0.25f,
            targetValue = 0.7f,
            animationSpec = infiniteRepeatable(tween(1700, easing = FastOutSlowInEasing), RepeatMode.Reverse),
            label = "gbGlowAlpha"
        )
    } else null
    val shape = RoundedCornerShape(16.dp)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 48.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                alpha = if (enabled) 1f else 0.5f
            }
            .drawBehind {
                val g = glow?.value ?: 0f
                if (g > 0f) {
                    drawRoundRect(
                        color = colors.buttonEnd.copy(alpha = g * 0.55f),
                        topLeft = Offset(-4.dp.toPx(), -2.dp.toPx()),
                        size = Size(size.width + 8.dp.toPx(), size.height + 10.dp.toPx()),
                        cornerRadius = CornerRadius(20.dp.toPx())
                    )
                }
            }
            .clip(shape)
            .background(colors.buttonBrush)
            .drawWithContent {
                drawContent()
                // top highlight
                drawRect(
                    brush = Brush.verticalGradient(
                        listOf(Color.White.copy(alpha = 0.22f), Color.Transparent),
                        endY = size.height * 0.6f
                    )
                )
                val p = shine.value
                if (p in 0.001f..0.999f) {
                    val x = -size.width * 0.4f + (size.width * 1.8f) * p
                    drawRect(
                        brush = Brush.horizontalGradient(
                            listOf(Color.Transparent, Color.White.copy(alpha = 0.42f), Color.Transparent),
                            startX = x,
                            endX = x + size.width * 0.35f
                        )
                    )
                }
            }
            .clickable(
                interactionSource = source,
                indication = LocalIndication.current,
                enabled = enabled,
                role = Role.Button,
                onClick = onClick
            )
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            Icon(
                icon,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(18.dp).graphicsLayer { rotationZ = iconSpin }
            )
            Spacer(Modifier.width(8.dp))
        }
        Text(
            text = text,
            color = Color.White,
            fontFamily = PlusJakartaSansFontFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp,
            letterSpacing = 0.2.sp
        )
    }
}

/** Soft animated "aurora" behind a section: base gradient plus three slowly drifting colour orbs. */
@Composable
fun Modifier.auroraBackground(colors: MilestoneColors, motion: Boolean): Modifier {
    val drift = if (motion) {
        rememberInfiniteTransition(label = "aurora").animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(16000, easing = LinearEasing), RepeatMode.Reverse),
            label = "auroraDrift"
        )
    } else null
    return this.drawBehind {
        val t = drift?.value ?: 0.5f // read in the draw phase only: no recomposition per frame
        drawRect(Brush.verticalGradient(listOf(colors.baseTop, colors.baseBottom)))
        val r = maxOf(size.width, size.height) * 0.62f
        fun orb(color: Color, cx: Float, cy: Float) = drawCircle(
            brush = Brush.radialGradient(listOf(color, Color.Transparent), center = Offset(cx, cy), radius = r),
            radius = r,
            center = Offset(cx, cy)
        )
        orb(colors.orbA, size.width * (0.10f + 0.30f * t), size.height * 0.15f)
        orb(colors.orbB, size.width * (0.92f - 0.28f * t), size.height * (0.80f - 0.10f * t))
        orb(colors.orbC, size.width * 0.50f, size.height * (0.45f + 0.30f * t))
    }
}

/** Fade + rise + scale entrance, delayed by [index] so lists cascade in. */
@Composable
fun Modifier.staggerIn(index: Int, motion: Boolean, key: Any? = Unit): Modifier {
    val progress = remember(key) { Animatable(if (motion) 0f else 1f) }
    LaunchedEffect(key) {
        if (motion) {
            delay(index * 45L)
            progress.animateTo(1f, spring(dampingRatio = 0.72f, stiffness = 380f))
        }
    }
    return this.graphicsLayer {
        val p = progress.value
        alpha = p.coerceIn(0f, 1f)
        translationY = (1f - p) * 26.dp.toPx()
        val sc = 0.92f + 0.08f * p
        scaleX = sc
        scaleY = sc
    }
}
