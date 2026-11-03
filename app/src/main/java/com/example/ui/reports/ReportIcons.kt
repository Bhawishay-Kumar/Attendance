package com.example.ui.reports

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Small drawn glyphs that replace emoji in Reports. Emoji render differently on every phone
 * brand (and some show as boxes); these look identical everywhere and follow the theme colours.
 */

@Composable
fun LockGlyph(color: Color, size: Dp = 12.dp) {
    Canvas(Modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val sw = w * 0.14f
        drawArc(color, 180f, 180f, false, Offset(w * 0.24f, h * 0.06f), Size(w * 0.52f, h * 0.6f), style = Stroke(sw, cap = StrokeCap.Round))
        drawRoundRect(color, Offset(w * 0.14f, h * 0.42f), Size(w * 0.72f, h * 0.5f), CornerRadius(w * 0.14f), style = Fill)
    }
}

@Composable
fun TrophyGlyph(color: Color, size: Dp = 12.dp) {
    Canvas(Modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val cup = Path().apply {
            moveTo(w * 0.24f, h * 0.1f); lineTo(w * 0.76f, h * 0.1f)
            lineTo(w * 0.7f, h * 0.5f)
            quadraticBezierTo(w * 0.62f, h * 0.66f, w * 0.5f, h * 0.66f)
            quadraticBezierTo(w * 0.38f, h * 0.66f, w * 0.3f, h * 0.5f)
            close()
        }
        drawPath(cup, color, style = Fill)
        val st = Stroke(w * 0.1f, cap = StrokeCap.Round, join = StrokeJoin.Round)
        drawArc(color, 90f, 180f, false, Offset(w * 0.0f, h * 0.14f), Size(w * 0.3f, h * 0.3f), style = st)
        drawArc(color, -90f, 180f, false, Offset(w * 0.7f, h * 0.14f), Size(w * 0.3f, h * 0.3f), style = st)
        drawLine(color, Offset(w * 0.5f, h * 0.66f), Offset(w * 0.5f, h * 0.84f), w * 0.12f, StrokeCap.Round)
        drawLine(color, Offset(w * 0.3f, h * 0.92f), Offset(w * 0.7f, h * 0.92f), w * 0.14f, StrokeCap.Round)
    }
}

@Composable
fun CapGlyph(color: Color, size: Dp = 20.dp) {
    Canvas(Modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val top = Path().apply {
            moveTo(w * 0.5f, h * 0.2f); lineTo(w * 0.95f, h * 0.42f); lineTo(w * 0.5f, h * 0.64f); lineTo(w * 0.05f, h * 0.42f); close()
        }
        drawPath(top, color, style = Fill)
        val base = Path().apply {
            moveTo(w * 0.24f, h * 0.55f); lineTo(w * 0.24f, h * 0.72f)
            quadraticBezierTo(w * 0.5f, h * 0.88f, w * 0.76f, h * 0.72f); lineTo(w * 0.76f, h * 0.55f)
        }
        drawPath(base, color.copy(alpha = 0.7f), style = Stroke(w * 0.09f, cap = StrokeCap.Round, join = StrokeJoin.Round))
        drawLine(color, Offset(w * 0.92f, h * 0.44f), Offset(w * 0.92f, h * 0.7f), w * 0.07f, StrokeCap.Round)
    }
}

@Composable
fun CoinGlyph(color: Color, size: Dp = 20.dp) {
    Box(Modifier.size(size).border(androidx.compose.foundation.BorderStroke(2.dp, color), CircleShape), contentAlignment = Alignment.Center) {
        RText("₹", (size.value * 0.55f), color, weight = FontWeight.Bold)
    }
}

@Composable
fun FlameGlyph(color: Color, size: Dp = 14.dp) {
    Canvas(Modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val flame = Path().apply {
            moveTo(w * 0.5f, h * 0.04f)
            cubicTo(w * 0.9f, h * 0.38f, w * 0.92f, h * 0.62f, w * 0.78f, h * 0.8f)
            cubicTo(w * 0.68f, h * 0.94f, w * 0.32f, h * 0.94f, w * 0.22f, h * 0.8f)
            cubicTo(w * 0.08f, h * 0.62f, w * 0.2f, h * 0.44f, w * 0.38f, h * 0.3f)
            cubicTo(w * 0.4f, h * 0.44f, w * 0.46f, h * 0.46f, w * 0.5f, h * 0.04f)
            close()
        }
        drawPath(flame, color, style = Fill)
    }
}
