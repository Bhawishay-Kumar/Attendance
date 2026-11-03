package com.example.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import com.example.data.model.PeriodStatus
import com.example.ui.theme.LocalGlassTheme
import kotlin.math.PI
import kotlin.math.sin

/**
 * Animation only. Plays ONE shine across a period card whenever its status changes:
 *   PRESENT (or AUTO_PRESENT) -> green shine
 *   ABSENT                    -> red shade shine
 *   unmarked (PENDING) / NO_CLASS -> normal (neutral) shine
 * It changes no layout, colors, text or state, and it does not play on first load.
 * Apply it AFTER the existing .border(...) so the shine is clipped inside the card corners.
 */
fun Modifier.attendanceShine(status: PeriodStatus?): Modifier = composed {
    val dark = LocalGlassTheme.current.isDark
    val progress = remember { Animatable(1f) }
    var first by remember { mutableStateOf(true) }
    var tone by remember { mutableStateOf(Color.Unspecified) }

    val green = Color(0xFF10B981)
    val red = Color(0xFFF43F5E)
    val normal = if (dark) Color.White else Color(0xFF64748B)

    LaunchedEffect(status) {
        if (first) { first = false; return@LaunchedEffect }
        tone = when (status) {
            PeriodStatus.PRESENT, PeriodStatus.AUTO_PRESENT -> green
            PeriodStatus.ABSENT -> red
            else -> normal
        }
        progress.snapTo(0f)
        progress.animateTo(1f, tween(900, easing = FastOutSlowInEasing))
    }

    clip(RoundedCornerShape(16.dp)).drawWithContent {
        drawContent()
        val p = progress.value
        if (p < 1f && tone != Color.Unspecified) {
            val colored = tone != normal
            // soft shade wash that rises and fades
            val wash = sin(PI * p).toFloat() * (if (colored) 0.16f else 0.06f)
            drawRect(tone.copy(alpha = wash))
            // diagonal shine band sweeping left -> right
            val x = -size.width * 0.5f + p * size.width * 2f
            val peak = if (colored) 0.55f else (if (dark) 0.30f else 0.35f)
            drawRect(
                Brush.linearGradient(
                    listOf(Color.Transparent, tone.copy(alpha = peak), Color.Transparent),
                    start = Offset(x, 0f),
                    end = Offset(x + size.width * 0.45f, size.height * 0.5f)
                )
            )
        }
    }
}
