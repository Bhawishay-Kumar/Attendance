package com.example.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.StartOffset
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.LocalLibrary
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.LocalGlassTheme
import com.example.ui.theme.OutfitFontFamily
import com.example.ui.theme.PlusJakartaSansFontFamily
import com.example.ui.theme.SpaceGroteskFontFamily
import kotlinx.coroutines.delay

/**
 * Visual-only upgrade for the Home "Quick Stats" boxes (Present / Absent / Library / Cancelled).
 * Same inputs as the old QuickStatBox: it only DISPLAYS the numbers it is given.
 * No counting, database or ViewModel logic lives here.
 */
@Composable
fun QuickStatCard(
    label: String,
    count: Int,
    iconColor: Color,
    modifier: Modifier = Modifier
) {
    val glass = LocalGlassTheme.current
    val dark = glass.isDark
    val shape = RoundedCornerShape(16.dp)

    // Icon + stagger index chosen from the label so call sites stay unchanged
    val (icon, order) = quickStatVisual(label)
    val delayMs = order * 70

    var appeared by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { appeared = true }

    val enter by animateFloatAsState(
        if (appeared) 1f else 0f, tween(520, delayMs, FastOutSlowInEasing), label = "enter"
    )
    val shownCount by animateIntAsState(
        count.coerceAtLeast(0), tween(600, easing = FastOutSlowInEasing), label = "count"
    )

    // Bump the number when the value changes (skipped on first load)
    val bump = remember { Animatable(1f) }
    var first by remember { mutableStateOf(true) }
    LaunchedEffect(count) {
        if (first) { first = false; return@LaunchedEffect }
        bump.animateTo(1.2f, tween(110))
        bump.animateTo(1f, spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMedium))
    }

    // Spring icon pop after the card settles
    var iconIn by remember { mutableStateOf(false) }
    LaunchedEffect(appeared) { if (appeared) { delay(delayMs + 220L); iconIn = true } }
    val iconScale by animateFloatAsState(
        if (iconIn) 1f else 0.3f, spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessLow), label = "iconScale"
    )

    // Idle glass sweep + breathing bloom
    val infinite = rememberInfiniteTransition(label = "idle")
    val sweep by infinite.animateFloat(
        0f, 1f,
        infiniteRepeatable(
            keyframes { durationMillis = 5500; 0f at 0; 0f at 3000; 1f at 5500 },
            RepeatMode.Restart, StartOffset(delayMs * 4)
        ), label = "sweep"
    )
    val breathe by infinite.animateFloat(
        0.85f, 1.15f,
        infiniteRepeatable(tween(1800, easing = LinearEasing), RepeatMode.Reverse, StartOffset(delayMs * 3)),
        label = "breathe"
    )
    val sheen = if (dark) 0.10f else 0.55f

    // Theme changes fade instead of snapping
    val border by animateColorAsState(if (dark) Color(0x5C818CF8) else Color(0x80C7D2FE), tween(400), label = "border")
    val iconBg by animateColorAsState(iconColor.copy(alpha = if (dark) 0.20f else 0.16f), tween(400), label = "iconBg")
    val glow by animateColorAsState(iconColor.copy(alpha = if (dark) 0.28f else 0.10f), tween(400), label = "glow")

    val cardBrush = if (dark)
        Brush.verticalGradient(listOf(Color(0x40312E81), Color(0x260B1228)))
    else
        Brush.verticalGradient(listOf(Color(0xCCFFFFFF), Color(0x80EEF0FF)))

    Column(
        modifier = modifier
            .testTag("quick_stat_${label.lowercase()}")
            .heightIn(min = 78.dp)
            .graphicsLayer {
                alpha = enter
                translationY = (1f - enter) * 14.dp.toPx()
                val s = 0.94f + 0.06f * enter
                scaleX = s; scaleY = s
            }
            .clip(shape)
            .background(cardBrush)
            .drawBehind {
                val c = Offset(size.width / 2f, 18.dp.toPx())
                drawCircle(
                    Brush.radialGradient(listOf(glow, Color.Transparent), center = c, radius = size.width * 0.75f * breathe),
                    center = c, radius = size.width * 0.75f * breathe
                )
                val x = -size.width * 0.6f + sweep * size.width * 2.2f
                drawRect(
                    Brush.linearGradient(
                        listOf(Color.Transparent, Color.White.copy(alpha = sheen), Color.Transparent),
                        start = Offset(x, 0f), end = Offset(x + size.width * 0.5f, size.height * 0.4f)
                    )
                )
            }
            .border(1.dp, border, shape)
            .padding(horizontal = 6.dp, vertical = 10.dp)
            .semantics(mergeDescendants = true) { contentDescription = "$label: $count" },
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(24.dp)
                .graphicsLayer { scaleX = iconScale; scaleY = iconScale; rotationZ = (1f - iconScale) * -35f }
                .clip(CircleShape)
                .background(iconBg)
                .border(0.75.dp, iconColor.copy(alpha = if (dark) 0.55f else 0.28f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, null, tint = iconColor, modifier = Modifier.size(14.dp))
        }
        Spacer(Modifier.height(4.dp))
        Text(
            "$shownCount",
            fontFamily = OutfitFontFamily,
            fontWeight = FontWeight.ExtraBold,
            fontSize = 16.sp,
            color = glass.textPrimary,
            maxLines = 1,
            modifier = Modifier.graphicsLayer { scaleX = bump.value; scaleY = bump.value }
        )
        Text(
            label,
            fontFamily = PlusJakartaSansFontFamily,
            fontWeight = FontWeight.SemiBold,
            fontSize = 10.sp,
            color = glass.textSecondary,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

private fun quickStatVisual(label: String): Pair<ImageVector, Int> = when (label) {
    "Present" -> Icons.Default.Check to 0
    "Absent" -> Icons.Default.Close to 1
    "Library" -> Icons.Default.LocalLibrary to 2
    "Cancelled" -> Icons.Default.Block to 3
    else -> Icons.Default.Check to 0
}
