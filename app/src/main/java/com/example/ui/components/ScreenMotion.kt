package com.example.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.example.ui.settings.rememberMotionEnabled
import kotlinx.coroutines.delay

/**
 * App-wide "component loading" animation. Every screen uses the same recipe so the whole app feels consistent:
 * each section fades in and rises 24dp, staggered 55 ms apart (capped so long screens never feel slow).
 *
 * Plays every time a tab is opened, but NOT again when an item merely scrolls out of / back into view
 * (the [EntranceTracker] lives exactly as long as the screen). Instant when system animations are disabled.
 */
class EntranceTracker {
    internal val seen = HashSet<Int>()
}

@Composable
fun rememberEntranceTracker(): EntranceTracker = remember { EntranceTracker() }

@Composable
fun Modifier.loadIn(index: Int, tracker: EntranceTracker, stepMs: Long = 55L, key: Int = index): Modifier {
    val motion = rememberMotionEnabled()
    var shown by remember { mutableStateOf(!motion || key in tracker.seen) }
    val a by animateFloatAsState(
        targetValue = if (shown) 1f else 0f,
        animationSpec = tween(420, easing = FastOutSlowInEasing),
        label = "loadIn"
    )
    LaunchedEffect(Unit) {
        if (!shown) {
            delay(index.coerceIn(0, 8) * stepMs)
            shown = true
        }
        tracker.seen.add(key)
    }
    return this.graphicsLayer {
        alpha = a
        translationY = (1f - a) * 24.dp.toPx()
    }
}
