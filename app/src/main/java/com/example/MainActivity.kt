package com.example

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import com.example.ui.bunk.BunkPlannerScreen
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.components.BlurDirection
import com.example.ui.components.DockTab
import com.example.ui.components.GlassDock
import com.example.ui.components.ProgressiveEdgeBlur
import com.example.ui.components.StarfieldCanvas
import com.example.ui.components.TopEdgeBlur
import com.example.ui.components.dockBottomPadding
import com.example.ui.pdf.PdfReportHost
import com.example.ui.screens.AttendanceScreen
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.OnboardingFlow
import com.example.ui.screens.ReportsScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.TimetableScreen
import com.example.ui.theme.BHTTAttendanceTheme
import com.example.ui.theme.LocalGlassTheme
import com.example.ui.viewmodel.AttendanceViewModel
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.haze

// Developer toggle: Set to true to temporarily bypass onboarding for testing; set back to false to restore onboarding flow.
const val DEV_BYPASS_ONBOARDING = false

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val viewModel: AttendanceViewModel = viewModel()
            val profile by viewModel.profile.collectAsState()

            val systemDark = androidx.compose.foundation.isSystemInDarkTheme()
            val useDark = when (profile.themeModeEnum) {
                com.example.data.model.ThemeMode.DARK -> true
                com.example.data.model.ThemeMode.LIGHT -> false
                com.example.data.model.ThemeMode.SYSTEM -> systemDark
            }

            // One haptics handle for the whole app; always reads the live profile (switch + intensity).
            val hapticsContext = androidx.compose.ui.platform.LocalContext.current
            val appHaptics = androidx.compose.runtime.remember(hapticsContext) {
                com.example.ui.settings.AppHaptics(hapticsContext) { viewModel.profile.value }
            }
            BHTTAttendanceTheme(darkTheme = useDark) {
                androidx.compose.runtime.CompositionLocalProvider(
                    com.example.ui.settings.LocalAppHaptics provides appHaptics
                ) {
                    MainAppContent(viewModel = viewModel)
                }
            }
        }
    }
}

@Composable
fun MainAppContent(viewModel: AttendanceViewModel) {
    val glass = LocalGlassTheme.current
    val profile by viewModel.profile.collectAsState()
    val selectedTab by viewModel.selectedTab.collectAsState()
    val bunkPlannerOpen by viewModel.bunkPlannerOpen.collectAsState()
    val whatIfOpen by viewModel.whatIf.isOpen.collectAsState()

    val isOnboardingActive = !DEV_BYPASS_ONBOARDING && !profile.isOnboardingCompleted

    // Back handler: Return to HOME before exiting app
    BackHandler(enabled = !isOnboardingActive && selectedTab != DockTab.HOME) {
        viewModel.selectTab(DockTab.HOME)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    listOf(glass.canvasStart, glass.canvasEnd)
                )
            )
    ) {
        if (!glass.isDark) {
            // Soft pastel glows give the light theme depth instead of a flat wash
            Box(
                Modifier.fillMaxSize().drawWithCache {
                    val c1 = Offset(size.width * 0.10f, size.height * 0.04f)
                    val r1 = size.width * 0.95f
                    val b1 = Brush.radialGradient(
                        listOf(com.example.ui.theme.LightAuraOrb1.copy(alpha = 0.55f), Color.Transparent),
                        center = c1, radius = r1
                    )

                    val c2 = Offset(size.width * 0.95f, size.height * 0.55f)
                    val r2 = size.width * 0.85f
                    val b2 = Brush.radialGradient(
                        listOf(com.example.ui.theme.LightAuraOrb2.copy(alpha = 0.60f), Color.Transparent),
                        center = c2, radius = r2
                    )

                    val c3 = Offset(size.width * 0.20f, size.height * 0.98f)
                    val r3 = size.width * 0.80f
                    val b3 = Brush.radialGradient(
                        listOf(com.example.ui.theme.LightAuraOrb3.copy(alpha = 0.40f), Color.Transparent),
                        center = c3, radius = r3
                    )

                    onDrawBehind {
                        drawCircle(b1, radius = r1, center = c1)
                        drawCircle(b2, radius = r2, center = c2)
                        drawCircle(b3, radius = r3, center = c3)
                    }
                }
            )
        }
        if (glass.isDark && profile.starfieldAnimationEnabled) {
            StarfieldCanvas(
                modifier = Modifier.fillMaxSize(),
                enabled = !bunkPlannerOpen && !whatIfOpen
            )
        }

        if (isOnboardingActive) {
            OnboardingFlow(
                viewModel = viewModel,
                onFinishOnboarding = {
                    viewModel.updateProfile(profile.copy(isOnboardingCompleted = true))
                }
            )
        } else {
            val hazeState = remember { HazeState() }
            Scaffold(
                contentWindowInsets = WindowInsets(0, 0, 0, 0),
                containerColor = androidx.compose.ui.graphics.Color.Transparent
            ) { _ ->
                Box(modifier = Modifier.fillMaxSize()) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .haze(state = hazeState)
                    ) {
                        AnimatedContent(
                            targetState = selectedTab,
                            transitionSpec = {
                                // Direction-aware: moving to a tab on the right slides in from the right (and vice versa),
                                // with a soft scale so the incoming screen "settles" into place.
                                val dir = if (targetState.ordinal >= initialState.ordinal) 1 else -1
                                (fadeIn(tween(300, delayMillis = 70)) +
                                    slideInHorizontally(tween(380, easing = FastOutSlowInEasing)) { full -> dir * full / 10 } +
                                    scaleIn(tween(380, easing = FastOutSlowInEasing), initialScale = 0.96f)) togetherWith
                                    (fadeOut(tween(150)) +
                                        slideOutHorizontally(tween(300, easing = FastOutSlowInEasing)) { full -> -dir * full / 10 } +
                                        scaleOut(tween(300, easing = FastOutSlowInEasing), targetScale = 0.98f))
                            },
                            label = "tabContent"
                        ) { tab ->
                            when (tab) {
                                DockTab.HOME -> HomeScreen(viewModel = viewModel)
                                DockTab.ATTENDANCE -> AttendanceScreen(viewModel = viewModel)
                                DockTab.TIMETABLE -> TimetableScreen(viewModel = viewModel)
                                DockTab.REPORTS -> ReportsScreen(viewModel = viewModel)
                                DockTab.SETTINGS -> SettingsScreen(viewModel = viewModel)
                            }
                        }

                        TopEdgeBlur(
                            modifier = Modifier.align(Alignment.TopCenter),
                            enabled = profile.progressiveBlurEnabled
                        )
                    }

                    if (profile.progressiveBlurEnabled) {
                        ProgressiveEdgeBlur(
                            isDark = glass.isDark,
                            direction = BlurDirection.BOTTOM,
                            height = 105.dp,
                            modifier = Modifier.align(Alignment.BottomCenter)
                        )
                    }

                    if (!bunkPlannerOpen) {
                        GlassDock(
                            selectedIndex = selectedTab.ordinal,
                            onSelect = { index ->
                                if (index in DockTab.entries.indices) {
                                    viewModel.selectTab(DockTab.entries[index])
                                }
                            },
                            hazeState = hazeState,
                            darkTheme = glass.isDark,
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(bottom = dockBottomPadding())
                        )
                    }

                    // Full-screen Bunk Planner overlay (state-driven, like the rest of the app's navigation).
                    AnimatedVisibility(
                        visible = bunkPlannerOpen,
                        enter = fadeIn(tween(220)) + slideInVertically(tween(320, easing = FastOutSlowInEasing)) { it / 12 },
                        exit = fadeOut(tween(160)) + slideOutVertically(tween(240, easing = FastOutSlowInEasing)) { it / 12 }
                    ) {
                        BunkPlannerScreen(viewModel = viewModel)
                    }

                    // Shared PDF Report flow (Home, Reports and Notifications all open this one host).
                    PdfReportHost(attendanceVm = viewModel)
                }
            }
        }
        if (whatIfOpen && !isOnboardingActive) {
            com.example.ui.whatif.WhatIfPredictorHost(viewModel.whatIf)
        }
    }
}
