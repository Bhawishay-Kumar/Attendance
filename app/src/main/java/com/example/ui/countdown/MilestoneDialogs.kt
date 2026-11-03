package com.example.ui.countdown

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.model.MilestoneAccent
import com.example.ui.theme.OutfitFontFamily
import com.example.ui.theme.PlusJakartaSansFontFamily
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * "Milestone Added!" popup: card scales/fades in, the check mark draws itself once, a soft glow
 * pulses once (no infinite animations), and dismissal animates out before the dialog closes.
 */
@Composable
fun MilestoneSuccessDialog(onDismiss: () -> Unit) {
    val colors = rememberMilestoneColors()
    val motion = rememberMotionEnabled()
    val scope = rememberCoroutineScope()
    var visible by remember { mutableStateOf(!motion) }
    val check = remember { Animatable(if (motion) 0f else 1f) }
    val glow = remember { Animatable(if (motion) 0f else 0.6f) }
    val green = colors.success

    LaunchedEffect(Unit) {
        if (motion) {
            visible = true
            delay(180)
            check.animateTo(1f, tween(520))
            glow.animateTo(1f, tween(350))
            glow.animateTo(0.6f, tween(450))
        }
    }

    fun dismiss() {
        scope.launch {
            if (motion) {
                visible = false
                delay(220)
            }
            onDismiss()
        }
    }

    Dialog(
        onDismissRequest = { dismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        AnimatedVisibility(
            visible = visible,
            enter = fadeIn(tween(200)) + scaleIn(spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMediumLow), initialScale = 0.82f),
            exit = fadeOut(tween(180)) + scaleOut(tween(180), targetScale = 0.92f)
        ) {
            val shape = RoundedCornerShape(26.dp)
            Column(
                modifier = Modifier
                    .padding(horizontal = 32.dp)
                    .fillMaxWidth()
                    .background(colors.surfaceStrong, shape)
                    .border(1.dp, colors.border, shape)
                    .padding(horizontal = 24.dp, vertical = 28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(modifier = Modifier.size(104.dp), contentAlignment = Alignment.Center) {
                    if (motion) {
                        ConfettiBurst(
                            palette = listOf(
                                colors.accent(MilestoneAccent.PINK), colors.accent(MilestoneAccent.BLUE),
                                colors.accent(MilestoneAccent.AMBER), colors.accent(MilestoneAccent.VIOLET), green
                            ),
                            modifier = Modifier.requiredSize(300.dp),
                            originY = 0.5f
                        )
                    }
                    Canvas(modifier = Modifier.size(104.dp)) {
                        drawCircle(
                            brush = Brush.radialGradient(
                                listOf(green.copy(alpha = 0.35f * glow.value), Color.Transparent)
                            ),
                            radius = size.minDimension / 2f
                        )
                        // Sparkles (static, fade with the glow).
                        val sparkle = listOf(
                            Offset(0.12f, 0.30f) to colors.accent(MilestoneAccent.BLUE),
                            Offset(0.88f, 0.22f) to colors.accent(MilestoneAccent.PINK),
                            Offset(0.80f, 0.82f) to colors.accent(MilestoneAccent.AMBER),
                            Offset(0.18f, 0.80f) to colors.accent(MilestoneAccent.VIOLET)
                        )
                        sparkle.forEach { (p, c) ->
                            drawCircle(c.copy(alpha = 0.8f * glow.value), radius = 3.dp.toPx(), center = Offset(size.width * p.x, size.height * p.y))
                        }
                        val r = size.minDimension * 0.32f
                        drawCircle(green, radius = r)
                        // Check mark, trimmed to the animated progress.
                        val cx = size.width / 2f
                        val cy = size.height / 2f
                        val full = Path().apply {
                            moveTo(cx - r * 0.42f, cy + r * 0.02f)
                            lineTo(cx - r * 0.10f, cy + r * 0.34f)
                            lineTo(cx + r * 0.46f, cy - r * 0.30f)
                        }
                        val measure = PathMeasure().apply { setPath(full, false) }
                        val partial = Path()
                        measure.getSegment(0f, measure.length * check.value, partial, true)
                        drawPath(
                            partial,
                            color = Color.White,
                            style = Stroke(width = 5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
                        )
                    }
                }
                Text(
                    "Milestone Added!",
                    fontFamily = OutfitFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp,
                    color = colors.textPrimary
                )
                Text(
                    "Your milestone has been successfully saved.",
                    fontFamily = PlusJakartaSansFontFamily,
                    fontSize = 13.sp,
                    color = colors.textSecondary,
                    textAlign = TextAlign.Center
                )
                Box(modifier = Modifier.padding(top = 10.dp)) {
                    GradientButton(text = "Great!", onClick = { dismiss() })
                }
            }
        }
    }
}

@Composable
fun ConfirmDeleteDialog(title: String, onConfirm: () -> Unit, onCancel: () -> Unit) {
    val colors = rememberMilestoneColors()
    AlertDialog(
        onDismissRequest = onCancel,
        containerColor = colors.surfaceStrong,
        title = {
            Text("Delete milestone?", fontFamily = OutfitFontFamily, fontWeight = FontWeight.Bold, color = colors.textPrimary)
        },
        text = {
            Text(
                "\"$title\" and its reminders will be removed, including on your other devices.",
                fontFamily = PlusJakartaSansFontFamily,
                fontSize = 13.sp,
                color = colors.textSecondary
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text("Delete", color = colors.danger, fontWeight = FontWeight.Bold) }
        },
        dismissButton = { TextButton(onClick = onCancel) { Text("Cancel") } }
    )
}
