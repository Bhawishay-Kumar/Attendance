package com.example.ui.pdf

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.ui.reports.GlassCard
import com.example.ui.reports.RText
import com.example.ui.reports.RptPalette
import com.example.ui.reports.pressClickable
import com.example.ui.reports.shineSweep

enum class PdfMessageKind { INFO, WARNING, ERROR }

/** Shared button used across the PDF flow (filled gradient or quiet outline). */
@Composable
internal fun PdfButton(
    p: RptPalette,
    label: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    filled: Boolean = true,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(16.dp)
    val a by animateFloatAsState(if (enabled) 1f else 0.45f, tween(220), label = "btnAlpha")
    val bg = if (filled) Brush.horizontalGradient(listOf(p.indigo, p.cyan)) else Brush.horizontalGradient(listOf(p.trackBg, p.trackBg))
    Row(
        modifier
            .alpha(a)
            .clip(shape)
            .background(bg)
            .then(if (filled) Modifier else Modifier.border(1.dp, p.border, shape))
            .pressClickable(enabled) { onClick() }
            .padding(vertical = 14.dp, horizontal = 16.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = if (filled) Color.White else p.text, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
        }
        RText(label, 14f, if (filled) Color.White else p.text, weight = FontWeight.Bold, maxLines = 1)
    }
}

/**
 * The hero call to action: a slowly flowing indigo→violet→cyan gradient, a soft pulsing glow
 * and a light sweep while enabled; fades flat when the selection is invalid.
 */
@Composable
internal fun PdfGenerateButton(
    p: RptPalette,
    label: String,
    icon: ImageVector,
    enabled: Boolean,
    reduced: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val t = rememberInfiniteTransition(label = "gen")
    val flowAnim by t.animateFloat(0f, 2f, infiniteRepeatable(tween(5200, easing = LinearEasing)), label = "flow")
    val glowAnim by t.animateFloat(
        6f, 16f,
        infiniteRepeatable(tween(1700, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "glow"
    )
    val a by animateFloatAsState(if (enabled) 1f else 0.45f, tween(260), label = "genAlpha")
    val flow = if (reduced) 0f else flowAnim
    val glow = if (reduced || !enabled) 6f else glowAnim
    val shape = RoundedCornerShape(18.dp)
    Row(
        modifier
            .alpha(a)
            .shadow(glow.dp, shape, ambientColor = p.violet, spotColor = p.violet)
            .clip(shape)
            .drawBehind {
                val w = size.width
                drawRect(
                    Brush.horizontalGradient(
                        listOf(p.indigo, p.violet, p.cyan),
                        startX = flow * w, endX = flow * w + w, tileMode = TileMode.Mirror
                    )
                )
            }
            .shineSweep(enabled = enabled && !reduced, periodMs = 3200, strength = 0.22f)
            .pressClickable(enabled) { onClick() }
            .padding(vertical = 16.dp, horizontal = 20.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(10.dp))
        RText(label, 15f, Color.White, weight = FontWeight.Bold, maxLines = 1, display = true)
    }
}

@Composable
fun PdfGeneratingState(p: RptPalette, reduced: Boolean = false) {
    val steps = listOf(
        "Reading your attendance…",
        "Applying credit weights…",
        "Checking your targets…",
        "Laying out the pages…"
    )
    val t = rememberInfiniteTransition(label = "bar")
    val barAnim by t.animateFloat(-0.4f, 1.4f, infiniteRepeatable(tween(1500, easing = LinearEasing)), label = "barX")
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(18.dp)) {
            AiOrb(p, reduced)
            CyclingStatus(p, steps, reduced)
            RText("Your PDF is being prepared on this device.", 12f, p.textSec)
            // Soft indeterminate bar (honest: no percentage is implied).
            Box(
                Modifier.width(180.dp).height(5.dp).clip(RoundedCornerShape(50)).background(p.trackBg)
                    .drawBehind {
                        if (!reduced) {
                            val seg = size.width * 0.4f
                            val x = size.width * barAnim - seg / 2f
                            drawRoundRect(
                                Brush.horizontalGradient(listOf(Color.Transparent, p.cyan, p.violet, Color.Transparent), x, x + seg),
                                cornerRadius = androidx.compose.ui.geometry.CornerRadius(size.height / 2f)
                            )
                        }
                    }
            )
        }
    }
}

/** Empty, insufficient-data and error states: animated icon, clear message and a recovery action. */
@Composable
fun PdfMessageState(
    p: RptPalette,
    kind: PdfMessageKind,
    title: String,
    message: String,
    primaryLabel: String,
    onPrimary: () -> Unit,
    secondaryLabel: String? = null,
    onSecondary: (() -> Unit)? = null,
    reduced: Boolean = false
) {
    val (icon, tone) = when (kind) {
        PdfMessageKind.INFO -> Icons.Default.Info to p.cyan
        PdfMessageKind.WARNING -> Icons.Default.Warning to p.amber
        PdfMessageKind.ERROR -> Icons.Default.ErrorOutline to p.rose
    }
    val t = rememberInfiniteTransition(label = "halo")
    val ringAnim by t.animateFloat(0f, 1f, infiniteRepeatable(tween(1900, easing = LinearEasing)), label = "ring")
    val wobbleAnim by t.animateFloat(
        -4f, 4f,
        infiniteRepeatable(tween(1200, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "wobble"
    )
    Box(Modifier.fillMaxSize().padding(horizontal = 16.dp), contentAlignment = Alignment.Center) {
        GlassCard(p, Modifier.fillMaxWidth().staggerIn(0, reduced), corner = 24.dp, accent = tone) {
            Column(
                Modifier.fillMaxWidth().padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(Modifier.size(92.dp), contentAlignment = Alignment.Center) {
                    Canvas(Modifier.matchParentSize()) {
                        val ring = if (reduced) 0.5f else ringAnim
                        val r = size.minDimension / 2f
                        drawCircle(tone.copy(alpha = (1f - ring) * 0.4f), r * (0.55f + 0.45f * ring), center, style = Stroke(2.dp.toPx()))
                    }
                    Box(
                        Modifier.size(54.dp)
                            .floating(reduced)
                            .graphicsLayer { rotationZ = if (kind == PdfMessageKind.ERROR && !reduced) wobbleAnim else 0f }
                            .clip(RoundedCornerShape(18.dp)).background(tone.copy(alpha = 0.16f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(icon, contentDescription = null, tint = tone, modifier = Modifier.size(28.dp))
                    }
                }
                Box(Modifier.staggerIn(1, reduced)) { RText(title, 17f, p.text, weight = FontWeight.Bold, display = true) }
                Box(Modifier.staggerIn(2, reduced)) { RText(message, 13f, p.textSec, modifier = Modifier.fillMaxWidth()) }
                Spacer(Modifier.height(6.dp))
                Box(Modifier.staggerIn(3, reduced)) { PdfButton(p, primaryLabel, Modifier.fillMaxWidth(), onClick = onPrimary) }
                if (secondaryLabel != null && onSecondary != null) {
                    Box(Modifier.staggerIn(4, reduced)) {
                        PdfButton(p, secondaryLabel, Modifier.fillMaxWidth(), filled = false, onClick = onSecondary)
                    }
                }
            }
        }
    }
}
