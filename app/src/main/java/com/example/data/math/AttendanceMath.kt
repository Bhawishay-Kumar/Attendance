package com.example.data.math

import com.example.data.model.TierStatus
import kotlin.math.roundToInt

object AttendanceMath {

    fun calculatePercentage(attended: Int, conducted: Int): Double {
        if (conducted <= 0 || attended <= 0) return 0.0
        val safeAttended = attended.coerceIn(0, conducted)
        val raw = (safeAttended.toDouble() / conducted.toDouble()) * 100.0
        return (raw * 10.0).roundToInt() / 10.0
    }

    fun calculateTierStatus(
        percentage: Double,
        target: Int,
        buffer: Int,
        conducted: Int = 1
    ): TierStatus {
        if (conducted <= 0) return TierStatus.NO_DATA
        val safeTarget = target.coerceIn(1, 100)
        val completelySafeThreshold = (safeTarget + buffer.coerceAtLeast(0)).coerceAtMost(100)
        return when {
            percentage >= completelySafeThreshold -> TierStatus.COMPLETELY_SAFE
            percentage >= safeTarget -> TierStatus.SAFE_ZONE
            else -> TierStatus.DANGER_ZONE
        }
    }

    /**
     * Calculates exact future attended credit hours needed to reach `targetPercent`.
     * Formula: ceil((T * C - 100 * A) / (100 - T))
     *
     * Returns:
     * - `0` if `conducted <= 0`, `targetPercent <= 0`, or already at/above target.
     * - `-1` if `targetPercent >= 100` and `attended < conducted` (mathematically impossible to reach 100% after missing a class).
     */
    fun calculateRecoveryHours(attended: Int, conducted: Int, targetPercent: Int): Int {
        if (conducted <= 0 || targetPercent <= 0) return 0
        val safeAttended = attended.coerceIn(0, conducted)

        if (targetPercent >= 100) {
            return if (safeAttended >= conducted) 0 else -1
        }

        val numerator = (targetPercent.toLong() * conducted.toLong()) - (100L * safeAttended.toLong())
        if (numerator <= 0L) return 0

        val denominator = (100 - targetPercent).toLong()
        val result = (numerator + denominator - 1L) / denominator
        return result.coerceIn(0L, Int.MAX_VALUE.toLong()).toInt()
    }

    /**
     * Calculates how many future credit hours the student can safely miss while staying >= `targetPercent`.
     * Formula: floor((100 * A - T * C) / T)
     */
    fun calculateSafeSkipHours(attended: Int, conducted: Int, targetPercent: Int): Int {
        if (conducted <= 0 || targetPercent <= 0 || targetPercent > 100) return 0
        val safeAttended = attended.coerceIn(0, conducted)

        val numerator = (100L * safeAttended.toLong()) - (targetPercent.toLong() * conducted.toLong())
        if (numerator <= 0L) return 0

        val result = numerator / targetPercent.toLong()
        return result.coerceIn(0L, Int.MAX_VALUE.toLong()).toInt()
    }
}
