package com.example.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.StartOffset
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.School
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.PeriodSlot
import com.example.ui.theme.LocalGlassTheme
import com.example.ui.theme.OutfitFontFamily
import com.example.ui.theme.PlusJakartaSansFontFamily
import com.example.ui.theme.SpaceGroteskFontFamily

/**
 * Visual-only replacement for the body of SectionSix_TodaysClassesSnapshot.
 * Same inputs, same texts; only styling and animation are new.
 */
@Composable
fun TodaysClassesSnapshotSection(
    activeSlot: PeriodSlot?,
    todayPresent: Int,
    todayTotal: Int,
    todayAuto: Int,
    modifier: Modifier = Modifier,
    phaseLabel: String = "NEXT CLASS",
    statusText: String? = null,
    classProgress: Float = 0f
) {
    val glass = LocalGlassTheme.current
    var appeared by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { appeared = true }

    Column(modifier = modifier.fillMaxWidth()) {
        Text("Today's Classes", fontFamily = OutfitFontFamily, fontSize = 15.sp,
            fontWeight = FontWeight.Bold, color = glass.textPrimary)
        Text("Your schedule for today", fontFamily = PlusJakartaSansFontFamily, fontSize = 11.sp,
            color = glass.textMuted)
        Spacer(Modifier.height(10.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Max),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.Top
        ) {
            // NEXT CLASS
            SnapshotGlassCard(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                accent = Color(0xFF6366F1),
                appeared = appeared,
                delayMs = 0
            ) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            SnapshotIcon(Icons.Default.School, Color(0xFF6366F1))
                            Text(phaseLabel, fontFamily = SpaceGroteskFontFamily, fontSize = 9.sp,
                                fontWeight = FontWeight.ExtraBold, color = glass.textMuted)
                            if (activeSlot != null) LiveDot()
                        }
                        Spacer(Modifier.height(6.dp))
                        AnimatedContent(
                            targetState = activeSlot?.subject?.shortName to activeSlot?.timeRange,
                            transitionSpec = {
                                (fadeIn(tween(320)) + slideInVertically(tween(320)) { it / 2 }) togetherWith
                                    (fadeOut(tween(200)) + slideOutVertically(tween(200)) { -it / 2 })
                            },
                            label = "nextClass"
                        ) { (name, time) ->
                            Column {
                                Text(name ?: "Concluded", fontFamily = OutfitFontFamily, fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold, color = glass.textPrimary,
                                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(time ?: "Done for today", fontFamily = SpaceGroteskFontFamily, fontSize = 10.sp,
                                    color = glass.textMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                        // third line (mirrors the Summary card): minutes left / starts in
                        Text(statusText ?: " ", fontFamily = SpaceGroteskFontFamily, fontSize = 10.sp,
                            color = Color(0xFF818CF8), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    Spacer(Modifier.height(7.dp))
                    // thin progress of the running class (empty track while it has not started)
                    val classProg by animateFloatAsState(classProgress, tween(800, easing = FastOutSlowInEasing), label = "classProg")
                    Box(Modifier.fillMaxWidth().height(4.dp).clip(CircleShape)
                        .background(Color(0xFF6366F1).copy(alpha = 0.16f))) {
                        Box(Modifier.fillMaxWidth(classProg).fillMaxHeight().clip(CircleShape)
                            .background(Brush.horizontalGradient(listOf(Color(0xFF818CF8), Color(0xFF06B6D4)))))
                    }
                }
            }

            // SUMMARY
            SnapshotGlassCard(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                accent = Color(0xFF10B981),
                appeared = appeared,
                delayMs = 90
            ) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            SnapshotIcon(Icons.Default.CheckCircle, Color(0xFF10B981))
                            Text("SUMMARY", fontFamily = SpaceGroteskFontFamily, fontSize = 9.sp,
                                fontWeight = FontWeight.ExtraBold, color = glass.textMuted)
                        }
                        Spacer(Modifier.height(6.dp))
                        Text("$todayPresent/$todayTotal Present", fontFamily = OutfitFontFamily, fontSize = 14.sp,
                            fontWeight = FontWeight.Bold, color = glass.textPrimary, maxLines = 1)
                        Text("$todayAuto Auto-Library/Sem", fontFamily = SpaceGroteskFontFamily, fontSize = 10.sp,
                            color = Color(0xFF10B981), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    Spacer(Modifier.height(7.dp))
                    // thin animated progress of today's present classes
                    val target = if (todayTotal > 0) (todayPresent.toFloat() / todayTotal).coerceIn(0f, 1f) else 0f
                    val prog by animateFloatAsState(target, tween(800, easing = FastOutSlowInEasing), label = "prog")
                    Box(Modifier.fillMaxWidth().height(4.dp).clip(CircleShape)
                        .background(Color(0xFF10B981).copy(alpha = 0.16f))) {
                        Box(Modifier.fillMaxWidth(prog).fillMaxHeight().clip(CircleShape)
                            .background(Brush.horizontalGradient(listOf(Color(0xFF34D399), Color(0xFF06B6D4)))))
                    }
                }
            }
        }
    }
}

@Composable
private fun SnapshotGlassCard(
    modifier: Modifier, accent: Color, appeared: Boolean, delayMs: Int,
    content: @Composable ColumnScope.() -> Unit
) {
    val glass = LocalGlassTheme.current
    val dark = glass.isDark
    val shape = RoundedCornerShape(16.dp)
    val enter by animateFloatAsState(if (appeared) 1f else 0f, tween(520, delayMs, FastOutSlowInEasing), label = "enter")
    val t = rememberInfiniteTransition(label = "idle")
    val breathe by t.animateFloat(0.85f, 1.15f,
        infiniteRepeatable(tween(2000, easing = LinearEasing), RepeatMode.Reverse, StartOffset(delayMs * 3)), label = "b")
    val brush = if (dark) Brush.verticalGradient(listOf(Color(0x40312E81), Color(0x260B1228)))
                else Brush.verticalGradient(listOf(Color(0xCCFFFFFF), Color(0x80EEF0FF)))
    Column(
        modifier = modifier
            .graphicsLayer {
                alpha = enter; translationY = (1f - enter) * 14.dp.toPx()
                val s = 0.95f + 0.05f * enter; scaleX = s; scaleY = s
            }
            .clip(shape)
            .background(brush)
            .drawBehind {
                val c = Offset(size.width * 0.15f, 0f)
                drawCircle(Brush.radialGradient(
                    listOf(accent.copy(alpha = if (dark) 0.30f else 0.14f), Color.Transparent),
                    center = c, radius = size.width * 0.8f * breathe), center = c, radius = size.width * 0.8f * breathe)
            }
            .border(1.dp, if (dark) Color(0x5C818CF8) else Color(0x80C7D2FE), shape)
            .padding(12.dp)
    ) { content() }
}

@Composable
private fun SnapshotIcon(icon: androidx.compose.ui.graphics.vector.ImageVector, tint: Color) {
    Box(Modifier.size(22.dp).clip(RoundedCornerShape(7.dp)).background(tint.copy(alpha = 0.16f))
        .border(0.75.dp, tint.copy(alpha = 0.35f), RoundedCornerShape(7.dp)), Alignment.Center) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(13.dp))
    }
}

/** Small pulsing dot to show the class is upcoming/active. */
@Composable
private fun LiveDot() {
    val t = rememberInfiniteTransition(label = "live")
    val a by t.animateFloat(0.35f, 1f, infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "a")
    Box(Modifier.width(6.dp).height(6.dp).graphicsLayer { alpha = a }.clip(CircleShape).background(Color(0xFF22C55E)))
}
