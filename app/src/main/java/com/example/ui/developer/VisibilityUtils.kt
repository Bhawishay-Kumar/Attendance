package com.example.ui.developer

import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.findViewTreeLifecycleOwner

/**
 * Viewport visibility hook that returns (isVisible, modifier).
 */
@Composable
fun rememberViewportVisibility(): Pair<Boolean, Modifier> {
    val view = LocalView.current
    var isVisible by remember { mutableStateOf(false) }

    val modifier = Modifier.onGloballyPositioned { coordinates ->
        if (!coordinates.isAttached) {
            isVisible = false
            return@onGloballyPositioned
        }
        val windowBounds = Rect(0f, 0f, view.width.toFloat(), view.height.toFloat())
        val itemBounds = coordinates.boundsInWindow()
        isVisible = windowBounds.overlaps(itemBounds) &&
                itemBounds.height > 0f &&
                itemBounds.width > 0f
    }

    return isVisible to modifier
}

/** True while the host lifecycle is RESUMED. Used to pause infinite animations in the background. */
@Composable
fun rememberIsResumed(): State<Boolean> {
    val view = LocalView.current
    val owner = view.findViewTreeLifecycleOwner()
    val state = remember(owner) {
        mutableStateOf(owner?.lifecycle?.currentState?.isAtLeast(Lifecycle.State.RESUMED) ?: true)
    }
    DisposableEffect(owner) {
        if (owner == null) return@DisposableEffect onDispose { }
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> state.value = true
                Lifecycle.Event.ON_PAUSE -> state.value = false
                else -> Unit
            }
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    return state
}

private class VisibilityHolder { var last: Boolean? = null }

/**
 * Reports whether at least [threshold] (0..1) of this element's height is inside the window.
 * [onChange] fires only when the answer flips, so scrolling does not cause recomposition.
 */
@Composable
fun Modifier.trackVisibility(threshold: Float = 0.35f, onChange: (Boolean) -> Unit): Modifier {
    val view: View = LocalView.current
    val callback = rememberUpdatedState(onChange)
    val holder = remember { VisibilityHolder() }
    return this.onGloballyPositioned { coords ->
        val height = coords.size.height.toFloat()
        val visible = if (!coords.isAttached || height <= 0f) {
            false
        } else {
            val b = coords.boundsInWindow()
            val windowH = view.rootView.height.toFloat()
            val shown = (minOf(b.bottom, windowH) - maxOf(b.top, 0f)).coerceAtLeast(0f)
            shown / height >= threshold
        }
        if (holder.last != visible) {
            holder.last = visible
            callback.value(visible)
        }
    }
}
