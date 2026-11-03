package com.example.ui.settings

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import com.example.data.model.HapticIntensity
import com.example.data.model.StudentProfile

/** What the vibration should "feel" like. Strength is scaled by the user's Soft / Medium / Hard setting. */
enum class HapticKind {
    /** Tiny selection step: chips, sliders, calendar days. */
    TICK,
    /** Normal tap: buttons, tabs, expanding a card. */
    CLICK,
    /** Firm single pulse: marking Present, bulk actions, saving. */
    CONFIRM,
    /** Success: two rising pulses. */
    SUCCESS,
    /** Warning: two sharp pulses (Absent, locked action, validation error). */
    WARNING
}

/**
 * Vibration helper driven by the Settings preferences (master switch + intensity).
 *
 * Uses the vibrator directly with explicit amplitudes so the feedback is felt on every phone. It no longer
 * depends on the system "touch feedback" switch (many phones ship with it off, which made the old version
 * silently do nothing); the in-app switch in Settings is the single control.
 */
object Haptics {

    /** Normal tap, only if the master switch is on. (Kept for existing callers.) */
    fun tick(context: Context, profile: StudentProfile) = perform(context, profile, HapticKind.CLICK)

    /** Plays [kind] only if the master switch is on. */
    fun perform(context: Context, profile: StudentProfile, kind: HapticKind) {
        if (!profile.hapticFeedbackEnabled) return
        play(context, profile.hapticIntensityEnum, kind)
    }

    /** Plays a specific level (used by the intensity selector preview). */
    fun play(context: Context, level: HapticIntensity, kind: HapticKind = HapticKind.CLICK) {
        val vibrator = vibrator(context.applicationContext) ?: return
        if (!vibrator.hasVibrator()) return
        val scale = when (level) {
            HapticIntensity.SOFT -> 0.40f
            HapticIntensity.MEDIUM -> 0.70f
            HapticIntensity.HARD -> 1.00f
        }
        // timings: off, on, off, on ...   amplitudes (0 = off, otherwise 1..255) before intensity scaling
        val timings: LongArray
        val base: IntArray
        when (kind) {
            HapticKind.TICK -> { timings = longArrayOf(0, 9); base = intArrayOf(0, 110) }
            HapticKind.CLICK -> { timings = longArrayOf(0, 18); base = intArrayOf(0, 170) }
            HapticKind.CONFIRM -> { timings = longArrayOf(0, 30); base = intArrayOf(0, 235) }
            HapticKind.SUCCESS -> { timings = longArrayOf(0, 14, 50, 28); base = intArrayOf(0, 150, 0, 240) }
            HapticKind.WARNING -> { timings = longArrayOf(0, 24, 60, 34); base = intArrayOf(0, 210, 0, 255) }
        }
        val amps = IntArray(base.size) { i -> if (base[i] == 0) 0 else (base[i] * scale).toInt().coerceIn(40, 255) }
        runCatching {
            if (Build.VERSION.SDK_INT >= 26) {
                val effect = if (vibrator.hasAmplitudeControl()) VibrationEffect.createWaveform(timings, amps, -1)
                else VibrationEffect.createWaveform(timings, -1)
                vibrator.vibrate(effect)
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(timings, -1)
            }
        }
    }

    private fun vibrator(context: Context): Vibrator? =
        if (Build.VERSION.SDK_INT >= 31) {
            context.getSystemService(VibratorManager::class.java)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
}

/**
 * App-wide haptics handle. One instance is provided at the root ([LocalAppHaptics]) and always reads the
 * LIVE profile, so toggling haptics or changing intensity in Settings takes effect immediately everywhere.
 */
class AppHaptics(context: Context, private val profileProvider: () -> StudentProfile) {
    private val app = context.applicationContext
    private fun go(kind: HapticKind) = Haptics.perform(app, profileProvider(), kind)

    fun tick() = go(HapticKind.TICK)
    fun click() = go(HapticKind.CLICK)
    fun confirm() = go(HapticKind.CONFIRM)
    fun success() = go(HapticKind.SUCCESS)
    fun warning() = go(HapticKind.WARNING)
}

val LocalAppHaptics = staticCompositionLocalOf<AppHaptics?> { null }

/** The root-provided [AppHaptics]; falls back to a default-profile instance (haptics on) outside the app root. */
@Composable
fun rememberAppHaptics(): AppHaptics {
    LocalAppHaptics.current?.let { return it }
    val ctx = LocalContext.current
    return remember(ctx) { AppHaptics(ctx) { StudentProfile() } }
}

/**
 * Drop-in replacement for `LocalHapticFeedback.current`: existing `performHapticFeedback(...)` calls keep working
 * but now obey the Settings switch + intensity. LongPress (locked / blocked action) -> warning, everything else -> click.
 */
@Composable
fun rememberAppHapticFeedback(): HapticFeedback {
    val h = rememberAppHaptics()
    return remember(h) {
        object : HapticFeedback {
            override fun performHapticFeedback(hapticFeedbackType: HapticFeedbackType) {
                if (hapticFeedbackType == HapticFeedbackType.LongPress) h.warning() else h.click()
            }
        }
    }
}
