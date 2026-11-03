package com.example.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Backward-compatible entry points. Every existing call site (Home, Attendance, Reports,
 * Settings, Onboarding) keeps compiling unchanged and now shows the single shared
 * [AnimatedDayNightToggle] design.
 */

@Composable
fun ScenicSkyToggle(
    isDarkMode: Boolean,
    onToggle: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    AnimatedDayNightToggle(isDark = isDarkMode, onToggle = onToggle, modifier = modifier)
}
