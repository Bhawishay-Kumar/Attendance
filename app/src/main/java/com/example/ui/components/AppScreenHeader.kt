package com.example.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.LocalGlassTheme
import com.example.ui.theme.OutfitFontFamily
import com.example.ui.theme.PlusJakartaSansFontFamily

/**
 * The ONE header used by Attendance, Timetable, Reports and Settings (not Home).
 *
 *  - same title / subtitle typography on every screen
 *  - same top offset (status bar + 14dp) and same trailing [AnimatedDayNightToggle]
 *  - title and subtitle slide up + fade in (staggered) every time a tab opens, which together with
 *    the tab transition in MainActivity makes switching between sections feel continuous
 *
 * Horizontal alignment: pass `Modifier.padding(horizontal = X)` so the title always lands 20dp from
 * the screen edge (X = 20dp minus whatever padding the screen's list already has).
 */
@Composable
fun AppScreenHeader(
    title: String,
    subtitle: String,
    isDark: Boolean,
    onToggleTheme: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    leading: (@Composable () -> Unit)? = null
) {
    val glass = LocalGlassTheme.current

    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { shown = true }
    val titleIn by animateFloatAsState(
        targetValue = if (shown) 1f else 0f,
        animationSpec = tween(420, easing = FastOutSlowInEasing),
        label = "headerTitleIn"
    )
    val subtitleIn by animateFloatAsState(
        targetValue = if (shown) 1f else 0f,
        animationSpec = tween(480, delayMillis = 70, easing = FastOutSlowInEasing),
        label = "headerSubtitleIn"
    )

    Row(
        modifier = modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(top = 14.dp, bottom = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier.weight(1f).padding(end = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            leading?.invoke()
            Column {
                Text(
                    text = title,
                    fontFamily = OutfitFontFamily,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (-0.3).sp,
                    color = glass.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.graphicsLayer {
                        alpha = titleIn
                        translationY = (1f - titleIn) * 10.dp.toPx()
                    }
                )
                Text(
                    text = subtitle,
                    fontFamily = PlusJakartaSansFontFamily,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = glass.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .padding(top = 2.dp)
                        .graphicsLayer {
                            alpha = subtitleIn
                            translationY = (1f - subtitleIn) * 8.dp.toPx()
                        }
                )
            }
        }
        AnimatedDayNightToggle(isDark = isDark, onToggle = onToggleTheme)
    }
}
