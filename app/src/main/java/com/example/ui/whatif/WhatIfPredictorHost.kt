package com.example.ui.whatif

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.whatif.ProjectionMode
import com.example.ui.settings.rememberMotionEnabled
import com.example.ui.theme.LocalGlassTheme
import kotlinx.coroutines.delay
import java.time.LocalDate

private fun titleFor(s: WhatIfScreen): String = when (s) {
    WhatIfScreen.INTRO -> "What-If Predictor"
    WhatIfScreen.CALENDAR -> "Plan your days"
    WhatIfScreen.CLASSES -> "Choose classes"
    WhatIfScreen.RESULTS -> "Projected result"
    WhatIfScreen.BREAKDOWN -> "Breakdown"
    WhatIfScreen.SAVED -> "Saved scenarios"
    WhatIfScreen.SNAPSHOT -> "Saved scenario"
    WhatIfScreen.COMPARE -> "Compare"
}

@OptIn(ExperimentalAnimationApi::class)
@Composable
fun WhatIfPredictorHost(controller: WhatIfController) {
    val t = LocalGlassTheme.current
    val ui by controller.ui.collectAsState()
    val motion = rememberMotionEnabled()
    var confirmExit by remember { mutableStateOf(false) }

    fun requestExit() { if (controller.hasUnsavedPlan()) confirmExit = true else controller.close() }

    BackHandler(enabled = true) { if (!controller.back()) requestExit() }

    LaunchedEffect(ui.message) {
        if (ui.message != null) { delay(2600); controller.consumeMessage() }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(t.canvasStart, t.canvasEnd)))
            // Swallow touches so nothing behind the full-screen predictor can be tapped.
            .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) {}
    ) {
        Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.systemBars)) {
            WiHeader(
                title = titleFor(ui.screen),
                subtitle = when (ui.screen) {
                    WhatIfScreen.CLASSES -> ui.focusDate?.let { longDate(it) }
                    WhatIfScreen.RESULTS -> ui.mode.label
                    else -> "Simulation only · your real attendance is never changed"
                },
                onBack = { if (!controller.back()) requestExit() },
                trailing = {
                    if (ui.screen == WhatIfScreen.INTRO || ui.screen == WhatIfScreen.CALENDAR || ui.screen == WhatIfScreen.RESULTS) {
                        WiGhostButton("Close", color = t.textSecondary) { requestExit() }
                    }
                }
            )
            AnimatedContent(
                targetState = ui.screen,
                modifier = Modifier.weight(1f),
                transitionSpec = {
                    if (!motion) fadeIn(snap()) togetherWith fadeOut(snap())
                    else {
                        val forward = targetState.ordinal >= initialState.ordinal
                        val dir = if (forward) 1 else -1
                        (slideInHorizontally(tween(320, easing = FastOutSlowInEasing)) { it / 6 * dir } + fadeIn(tween(320))) togetherWith
                            (slideOutHorizontally(tween(240, easing = FastOutSlowInEasing)) { -it / 8 * dir } + fadeOut(tween(200)))
                    }
                },
                label = "whatIfScreen"
            ) { screen ->
                when (screen) {
                    WhatIfScreen.INTRO -> IntroScreen(ui, controller)
                    WhatIfScreen.CALENDAR -> CalendarScreen(ui, controller)
                    WhatIfScreen.CLASSES -> ClassesScreen(ui, controller)
                    WhatIfScreen.RESULTS -> ResultsScreen(ui, controller)
                    WhatIfScreen.BREAKDOWN -> ui.result?.let { BreakdownScreen(it) }
                    WhatIfScreen.SAVED -> SavedScreen(ui, controller)
                    WhatIfScreen.SNAPSHOT -> SnapshotScreen(ui, controller)
                    WhatIfScreen.COMPARE -> ui.compareReport?.let { CompareScreen(it) }
                }
            }
        }

        AnimatedVisibility(
            visible = ui.message != null,
            modifier = Modifier.align(Alignment.BottomCenter).windowInsetsPadding(WindowInsets.navigationBars).padding(bottom = 24.dp),
            enter = fadeIn(), exit = fadeOut()
        ) {
            Box(Modifier.clip(RoundedCornerShape(50)).background(t.royalIndigo).padding(horizontal = 18.dp, vertical = 10.dp)) {
                WiText(ui.message ?: "", size = 13.sp, color = androidx.compose.ui.graphics.Color.White)
            }
        }
    }

    if (ui.saveDraftId != null) {
        NameDialog(
            title = "Save scenario",
            initial = "Scenario ${LocalDate.now().dayOfMonth}-${ui.mode.label}".replace("Plan Only", "plan").replace("Month End", "month end"),
            confirm = "Save",
            onDismiss = { controller.cancelSave() },
            onConfirm = { controller.save(it) }
        )
    }
    if (confirmExit) {
        ConfirmDialog(
            "Leave the predictor?", "Your unsaved plan will be discarded. Save it first if you want to keep it.", "Discard & leave",
            onDismiss = { confirmExit = false },
            onConfirm = { confirmExit = false; controller.close() }
        )
    }
}
