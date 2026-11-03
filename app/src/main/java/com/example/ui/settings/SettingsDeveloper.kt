package com.example.ui.settings

import com.example.ui.developer.AnimatedSignature
import com.example.ui.developer.rememberViewportVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
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
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Work
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate as rotateDraw
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.runtime.remember
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.ui.theme.CaveatFontFamily
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/*
 * Developer section — UI upgrade.
 * Matches the supplied "Developer Details" reference: cosmic hero card, message card, "My Aim" card with
 * Earth + rocket artwork, script footer. All existing developer content is preserved verbatim.
 * Artwork: res/drawable-nodpi/dev_bg_{top,earth}_{light,dark}.webp (split from the supplied 4-panel sheet).
 */

private val DevCyan = Color(0xFF22D3EE)
private val DevBlue = Color(0xFF3B82F6)
private val DevViolet = Color(0xFF8B5CF6)

/**
 * Premium serif for the developer's name: Cinzel Bold when `res/font/cinzel_bold.ttf` is bundled
 * (looked up by name, so the app compiles and runs with or without the file), otherwise the system serif.
 */
@Composable
private fun rememberDevNameFont(): FontFamily {
    val ctx = LocalContext.current
    return remember(ctx) {
        val id = ctx.resources.getIdentifier("cinzel_bold", "font", ctx.packageName)
        if (id != 0) FontFamily(Font(id, FontWeight.Bold)) else FontFamily.Serif
    }
}

// ───────────────────────── shared helpers ─────────────────────────

@Composable
private fun DevArt(dark: Boolean, top: Boolean, modifier: Modifier = Modifier, alignment: Alignment = Alignment.Center) {
    Crossfade(targetState = dark, modifier = modifier, animationSpec = tween(400), label = "devArt") { d ->
        val res = when {
            top && d -> R.drawable.dev_bg_top_dark
            top -> R.drawable.dev_bg_top_light
            d -> R.drawable.dev_bg_earth_dark
            else -> R.drawable.dev_bg_earth_light
        }
        // Decorative artwork: aspect preserved (Crop), never stretched.
        Image(painterResource(res), contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop, alignment = alignment)
    }
}

/** Border re-drawn on top of artwork so the card hairline is never covered by an image. */
@Composable
private fun DevBorder(p: SettingsPalette, radius: Dp, modifier: Modifier = Modifier) {
    Box(modifier.border(1.dp, Brush.verticalGradient(listOf(p.cardBorderHi, p.cardBorder, p.cardBorder)), RoundedCornerShape(radius)))
}

@Composable
private fun FadeLine(widthDp: Dp, p: SettingsPalette, reverse: Boolean = false) {
    val c = if (p.dark) DevCyan else Color(0xFF2563EB)
    Box(
        Modifier.size(widthDp, 1.5.dp).background(
            Brush.horizontalGradient(if (reverse) listOf(c.copy(alpha = 0.9f), Color.Transparent) else listOf(Color.Transparent, c.copy(alpha = 0.9f)))
        )
    )
}

private fun starPath(c: Offset, r: Float): Path {
    val path = Path()
    for (i in 0 until 10) {
        val rad = if (i % 2 == 0) r else r * 0.45f
        val a = -PI / 2 + i * PI / 5
        val x = c.x + (rad * cos(a)).toFloat()
        val y = c.y + (rad * sin(a)).toFloat()
        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }
    path.close()
    return path
}

private fun DrawScope.sparkle(c: Offset, r: Float, color: Color) {
    val path = Path().apply {
        moveTo(c.x, c.y - r)
        quadraticTo(c.x, c.y, c.x + r, c.y)
        quadraticTo(c.x, c.y, c.x, c.y + r)
        quadraticTo(c.x, c.y, c.x - r, c.y)
        quadraticTo(c.x, c.y, c.x, c.y - r)
        close()
    }
    drawPath(path, color)
}

/** Divider: fading line – star – fading line. */
@Composable
private fun StarDivider(p: SettingsPalette, modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
        val pulse by rememberPulse(1500, 0.80f, 1.20f, "starPulse")
        val rock by rememberPulse(2400, -14f, 14f, "starRock")
        val glow by rememberPulse(1500, 0.10f, 0.60f, "starGlow")
        FadeLine(72.dp, p)
        Canvas(
            Modifier.padding(horizontal = 10.dp).size(14.dp)
                .graphicsLayer { scaleX = pulse; scaleY = pulse; rotationZ = rock; clip = false }
        ) {
            // soft twinkling halo + the star itself
            drawCircle(DevCyan.copy(alpha = glow), size.minDimension * 0.95f, center)
            drawPath(starPath(center, size.minDimension / 2f), Brush.linearGradient(listOf(DevBlue, DevViolet)))
        }
        FadeLine(72.dp, p, reverse = true)
    }
}

@Composable
private fun TargetMark(tint: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val c = center
        val r = size.minDimension / 2f
        val sw = 2.dp.toPx()
        drawCircle(tint, r * 0.92f, c, style = Stroke(sw))
        drawCircle(tint.copy(alpha = 0.85f), r * 0.60f, c, style = Stroke(sw))
        drawCircle(tint, r * 0.24f, c)
        drawLine(tint, c, Offset(size.width * 0.96f, size.height * 0.04f), strokeWidth = sw, cap = StrokeCap.Round)
    }
}

// ───────────────────────── 8. Developer hero ─────────────────────────

@Composable
fun DeveloperBanner(p: SettingsPalette) {
    val narrow = LocalConfiguration.current.screenWidthDp < 380
    val logo = if (narrow) 112.dp else 128.dp
    val float by rememberPulse(3600, -3.5f, 3.5f, "logoFloat")
    val spin by rememberLoop(9000, "orbit")
    val halo by rememberPulse(2600, 0.18f, 0.50f, "halo")
    val twinkle by rememberPulse(2200, 0.25f, 1f, "twinkle")
    val radius = 28.dp
    val sparkleColor = if (p.dark) Color.White else Color(0xFF6366F1)

    Box(Modifier.fillMaxWidth().glassCard(p, radius)) {
        // 1. dedicated cosmic / pastel artwork (theme-specific, cross-faded)
        DevArt(p.dark, top = true, modifier = Modifier.matchParentSize())
        // 2. legibility scrim — artwork stays visible at the edges, text sits on a calm surface
        Box(
            Modifier.matchParentSize().background(
                Brush.verticalGradient(
                    if (p.dark) listOf(Color(0x330A1030), Color(0x99070B24), Color(0xCC050A1E))
                    else listOf(Color(0x40FFFFFF), Color(0x80F6F4FF), Color(0xB3F1F0FF))
                )
            )
        )
        // 3. twinkling sparkles + Earth-glow horizon (decorative)
        Canvas(Modifier.matchParentSize()) {
            val w = size.width; val h = size.height
            val a = twinkle
            sparkle(Offset(w * 0.10f, h * 0.20f), 7.dp.toPx(), sparkleColor.copy(alpha = 0.55f * a))
            sparkle(Offset(w * 0.90f, h * 0.30f), 5.dp.toPx(), sparkleColor.copy(alpha = 0.45f * (1.25f - a)))
            sparkle(Offset(w * 0.14f, h * 0.66f), 4.dp.toPx(), sparkleColor.copy(alpha = 0.40f * a))
            sparkle(Offset(w * 0.86f, h * 0.74f), 6.dp.toPx(), sparkleColor.copy(alpha = 0.50f * (1.25f - a)))
            val horizonC = Offset(w / 2f, h + w * 0.80f)
            drawCircle(DevCyan.copy(alpha = if (p.dark) 0.10f else 0.08f), w * 0.86f, horizonC)
            drawCircle(DevBlue.copy(alpha = if (p.dark) 0.65f else 0.45f), w * 0.86f, horizonC, style = Stroke(1.5.dp.toPx()))
        }

        Column(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(top = 20.dp, bottom = 34.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // DEVELOPER heading with cyan lines
            Row(verticalAlignment = Alignment.CenterVertically) {
                FadeLine(44.dp, p)
                SText("DEVELOPER", 11.sp, if (p.dark) DevCyan else Color(0xFF1D4ED8), Modifier.padding(horizontal = 12.dp), FontWeight.ExtraBold, letterSpacing = 3.sp, maxLines = 1)
                FadeLine(44.dp, p, reverse = true)
            }

            // Logo: floating, glowing, multi-ring
            Box(
                Modifier.padding(top = 14.dp).size(logo + 44.dp),
                contentAlignment = Alignment.Center
            ) {
                // tilted orbit ring (passes behind the logo)
                Canvas(Modifier.matchParentSize()) {
                    val w = size.width; val h = size.height
                    rotateDraw(-18f, Offset(w / 2f, h / 2f)) {
                        drawOval(
                            Brush.sweepGradient(listOf(DevCyan, DevBlue, DevViolet, DevBlue, DevCyan), Offset(w / 2f, h / 2f)),
                            topLeft = Offset(0f, h * 0.30f), size = Size(w, h * 0.40f), style = Stroke(1.8.dp.toPx())
                        )
                    }
                }
                Box(
                    Modifier.size(logo + 14.dp)
                        .graphicsLayer { translationY = float.dp.toPx() }
                        .drawBehind {
                            drawCircle(
                                Brush.radialGradient(listOf(DevCyan.copy(alpha = halo), DevViolet.copy(alpha = halo * 0.4f), Color.Transparent), center, size.minDimension * 0.85f),
                                size.minDimension * 0.85f, center
                            )
                        },
                    contentAlignment = Alignment.Center
                ) {
                    // L1 rotating shimmer ring
                    Box(
                        Modifier.size(logo + 14.dp).rotate(spin * 360f).clip(CircleShape).graphicsLayer { alpha = 0.9f }
                            .background(Brush.sweepGradient(listOf(Color(0xFFA855F7), Color(0xFF2563EB), Color(0xFF00F0FF), Color(0xFF3B82F6), Color(0xFFA855F7))))
                    )
                    // L2 matte gap
                    Box(Modifier.size(logo + 8.dp).clip(CircleShape).background(if (p.dark) Color(0xFF070B24) else Color(0xFFF4F2FF)))
                    // L3 gradient frame + logo
                    Box(
                        Modifier.size(logo).shadow(14.dp, CircleShape, ambientColor = Color(0xFF6366F1), spotColor = Color(0xFF6366F1))
                            .clip(CircleShape)
                            .background(Brush.linearGradient(listOf(DevCyan, DevBlue, Color(0xFF4F46E5))))
                            .padding(3.dp)
                    ) {
                        Box(Modifier.fillMaxSize().clip(CircleShape), contentAlignment = Alignment.Center) {
                            BkBrandLogoImage(isDark = p.dark, modifier = Modifier.fillMaxSize())
                        }
                    }
                }
            }

            val (isSignatureVisible, visibilityModifier) = rememberViewportVisibility()
            AnimatedSignature(
                isVisible = isSignatureVisible,
                modifier = visibilityModifier
                    .fillMaxWidth(0.78f)
                    .padding(top = 6.dp, bottom = 2.dp)
            )

            // role badge (existing Code icon)
            Row(
                Modifier.padding(top = 10.dp)
                    .clip(RoundedCornerShape(50))
                    .background(if (p.dark) Color(0x990B1A52) else Color(0xCCEEF2FF))
                    .border(1.5.dp, Brush.horizontalGradient(listOf(DevBlue, DevCyan)), RoundedCornerShape(50))
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.Code, null, tint = if (p.dark) DevCyan else Color(0xFF2563EB), modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(8.dp))
                SText("App Developer & Creator", 13.sp, if (p.dark) Color(0xFF7DD3FC) else Color(0xFF1D4ED8), weight = FontWeight.Bold, maxLines = 1)
            }

            StarDivider(p, Modifier.padding(top = 16.dp))

            Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                SText("Built with passion", if (narrow) 28.sp else 32.sp, p.accentText, weight = FontWeight.Bold, family = CaveatFontFamily, maxLines = 1)
                HeartButton(26.dp, Modifier.padding(start = 4.dp))
            }
            SText(
                "BETTER APPS FOR A BRIGHTER TOMORROW", if (narrow) 9.5.sp else 10.5.sp, p.body,
                Modifier.padding(top = 6.dp).fillMaxWidth(), FontWeight.ExtraBold, letterSpacing = 2.sp, align = TextAlign.Center, lineHeight = 16.sp
            )
            Box(
                Modifier.padding(top = 12.dp).size(44.dp, 4.dp).clip(RoundedCornerShape(50))
                    .background(Brush.horizontalGradient(listOf(DevCyan, DevBlue, Color(0xFF4F46E5))))
            )
        }
        DevBorder(p, radius, Modifier.matchParentSize())
    }
}

// ───────────────────────── 10. Message / Aim / Thank-you ─────────────────────────

@Composable
fun ThankYouCard(p: SettingsPalette) {
    val msgRadius = 26.dp
    val opaque = if (p.dark) Color(0xFF131A2A) else Color(0xFFF4F6FF)   // same family as the glass card surface

    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(14.dp)) {

        // ── A MESSAGE FOR YOU ──
        Box(Modifier.fillMaxWidth().glassCard(p, msgRadius).glowAura(Color(0x336366F1), atTop = true, atStart = false, radiusDp = 110f)) {
            Column(Modifier.fillMaxWidth().padding(18.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // chat-bubble tile with white heart (existing heart component, white variant)
                    Box(
                        Modifier.size(46.dp).shadow(8.dp, RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp, bottomEnd = 18.dp, bottomStart = 6.dp), ambientColor = DevViolet, spotColor = DevViolet)
                            .clip(RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp, bottomEnd = 18.dp, bottomStart = 6.dp))
                            .background(Brush.linearGradient(listOf(Color(0xFFA855F7), Color(0xFF6366F1)))),
                        contentAlignment = Alignment.Center
                    ) { HeartButton(22.dp, white = true) }
                    Spacer(Modifier.width(14.dp))
                    SText(
                        "A MESSAGE FOR YOU", 12.sp, if (p.dark) Color(0xFFD8B4FE) else Color(0xFF7E22CE),
                        weight = FontWeight.ExtraBold, letterSpacing = 2.sp, maxLines = 1
                    )
                    Spacer(Modifier.width(10.dp))
                    Box(Modifier.weight(1f).height(1.dp).background(Brush.horizontalGradient(listOf(DevViolet.copy(alpha = 0.7f), Color.Transparent))))
                }
                Row(Modifier.padding(top = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                    SText("Thank you for using this app!", 17.sp, p.text, Modifier.weight(1f, fill = false), FontWeight.Bold, lineHeight = 22.sp)
                    HeartButton(24.dp, Modifier.padding(start = 4.dp))
                }
                Row(Modifier.padding(top = 10.dp).height(IntrinsicSize.Min)) {
                    Box(
                        Modifier.fillMaxHeight().width(3.dp).clip(RoundedCornerShape(50))
                            .background(Brush.verticalGradient(listOf(DevViolet, DevBlue)))
                    )
                    Spacer(Modifier.width(12.dp))
                    SText(
                        "I created this app with a simple goal — to make your daily life a little easier, more organized and more productive. Your support and trust mean a lot to me.",
                        13.sp, p.body, Modifier.weight(1f), FontWeight.Normal, lineHeight = 21.sp
                    )
                }
            }
            DevBorder(p, msgRadius, Modifier.matchParentSize())
        }

        // ── MY AIM (Earth + rocket artwork on the right, text on the left) ──
        Box(Modifier.fillMaxWidth().defaultMinSize(minHeight = 136.dp).glassCard(p, msgRadius)) {
            Box(Modifier.matchParentSize()) {
                // artwork: right ~60% of the card, cropped (never stretched) and anchored bottom-right
                // so the Earth horizon + rocket stay in frame
                DevArt(
                    p.dark, top = false,
                    modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight().fillMaxWidth(0.60f),
                    alignment = Alignment.BottomEnd
                )
                // fade the artwork's left edge into the card surface so text never sits on the picture
                Box(
                    Modifier.align(Alignment.CenterEnd).fillMaxHeight().fillMaxWidth(0.60f)
                        .background(Brush.horizontalGradient(0f to opaque, 0.40f to opaque.copy(alpha = 0.55f), 0.80f to Color.Transparent))
                )
            }
            Column(Modifier.fillMaxWidth(0.62f).padding(18.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TargetMark(if (p.dark) DevCyan else Color(0xFF2563EB), Modifier.size(28.dp))
                    Spacer(Modifier.width(10.dp))
                    SText("MY AIM", 12.sp, if (p.dark) DevCyan else Color(0xFF1D4ED8), weight = FontWeight.ExtraBold, letterSpacing = 2.sp, maxLines = 1)
                    Spacer(Modifier.width(6.dp))
                    PulseDot(DevCyan, 4.dp)
                }
                Box(
                    Modifier.padding(vertical = 10.dp).size(64.dp, 2.dp).clip(RoundedCornerShape(50))
                        .background(Brush.horizontalGradient(listOf(Color(0xFF2563EB), Color(0xFF6366F1), DevCyan)))
                )
                SText(
                    "To keep improving, add more useful features and create apps that truly make a difference.",
                    13.sp, p.body, Modifier.fillMaxWidth(), FontWeight.Normal, lineHeight = 20.sp
                )
            }
            DevBorder(p, msgRadius, Modifier.matchParentSize())
        }

        // ── Thank you for being here ──
        Column(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            StarDivider(p)
            Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                SText("Thank you for being here!", 26.sp, p.accentText, Modifier.weight(1f, fill = false), FontWeight.Bold, family = CaveatFontFamily, maxLines = 2, align = TextAlign.Center)
                SmileButton(32.dp, Modifier.padding(start = 4.dp))
            }
        }
    }
}

// ───────────────────────── 11. Footer ─────────────────────────

@Composable
fun SettingsFooter(p: SettingsPalette) {
    val waveColorA = if (p.dark) DevBlue else Color(0xFF6366F1)
    val waveColorB = if (p.dark) DevViolet else DevCyan
    Box(Modifier.fillMaxWidth().padding(top = 4.dp)) {
        // subtle bottom wave artwork (stroke only → fades out, no hard cut above the dock area)
        Canvas(Modifier.matchParentSize()) {
            val w = size.width; val h = size.height
            fun wave(baseY: Float, amp: Float, phase: Float, color: Color, alpha: Float) {
                val path = Path()
                val steps = 48
                for (i in 0..steps) {
                    val x = w * i / steps
                    val y = baseY + amp * sin((i / steps.toFloat()) * 2f * PI.toFloat() + phase)
                    if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                }
                drawPath(path, Brush.horizontalGradient(listOf(Color.Transparent, color.copy(alpha = alpha), Color.Transparent)), style = Stroke(1.5.dp.toPx(), cap = StrokeCap.Round))
            }
            wave(h * 0.62f, 8.dp.toPx(), 0.0f, waveColorA, if (p.dark) 0.55f else 0.40f)
            wave(h * 0.74f, 10.dp.toPx(), 1.6f, waveColorB, if (p.dark) 0.45f else 0.35f)
            wave(h * 0.86f, 7.dp.toPx(), 3.1f, waveColorA, if (p.dark) 0.30f else 0.22f)
        }
        Column(Modifier.fillMaxWidth().padding(top = 14.dp, bottom = 52.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                FadeLine(40.dp, p)
                Spacer(Modifier.width(8.dp))
                SText("Made with", 11.5.sp, p.textSecondary)
                HeartButton(18.dp, Modifier.padding(horizontal = 4.dp))
                SText("by Bhawishya Kumar", 11.5.sp, if (p.dark) Color(0xFFD9E0FF) else Color(0xFF283056), weight = FontWeight.Bold)
                Spacer(Modifier.width(8.dp))
                FadeLine(40.dp, p, reverse = true)
            }
            SText("v1.0.0 • 2026–27 Academic Edition", 10.5.sp, p.textMuted, Modifier.padding(top = 6.dp), FontWeight.SemiBold)
        }
    }
}
