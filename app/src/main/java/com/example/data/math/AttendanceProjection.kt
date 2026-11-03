package com.example.data.math

import kotlin.math.floor

/**
 * Exact (raw-ratio) helpers used by the Bunk Planner. [AttendanceMath] rounds percentages to one
 * decimal for display, which must never decide eligibility, so this wrapper compares the raw
 * integer ratio instead: attended / conducted >= target / 100  <=>  100 * attended >= target * conducted.
 *
 * Nothing here replaces [AttendanceMath]; display percentages, tiers, recovery and safe-skip figures
 * elsewhere in the app keep using it.
 */
object AttendanceProjection {

    /** True when attended/conducted is at or above [targetPercent]. False when nothing is conducted. */
    fun meetsTarget(attended: Int, conducted: Int, targetPercent: Int): Boolean {
        if (conducted <= 0) return false
        val target = targetPercent.coerceIn(0, 100).toLong()
        val a = attended.coerceIn(0, conducted).toLong()
        return 100L * a >= target * conducted.toLong()
    }

    /** Raw (unrounded) percentage, or null when nothing is conducted. */
    fun rawPercent(attended: Int, conducted: Int): Double? {
        if (conducted <= 0) return null
        return attended.coerceIn(0, conducted).toDouble() * 100.0 / conducted.toDouble()
    }

    /** Raw percentage points above (+) or below (-) the target, or null when nothing is conducted. */
    fun marginPoints(attended: Int, conducted: Int, targetPercent: Int): Double? =
        rawPercent(attended, conducted)?.minus(targetPercent.coerceIn(0, 100))

    /**
     * Rounds DOWN to one decimal so a displayed projection can never look like it reaches a
     * target that the raw ratio does not (e.g. 79.96 is shown as 79.9, not 80.0).
     */
    fun displayFloor(value: Double): Double = floor(value * 10.0 + 1e-9) / 10.0
}
