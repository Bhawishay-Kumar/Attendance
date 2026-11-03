package com.example.data.whatif

data class ComparisonDifference(val title: String, val detail: String)

data class SubjectCompareValue(val projectedAttended: Int, val projectedConducted: Int) {
    val pct: Double get() = WhatIfMath.pct(projectedAttended, projectedConducted)
}

data class SubjectCompareRow(val code: String, val name: String, val values: List<SubjectCompareValue?>)

data class ComparisonReport(
    val entries: List<WhatIfSnapshot>,
    /** Differences in baseline, targets, horizon, mode or completeness that make the comparison uneven. */
    val differences: List<ComparisonDifference>,
    val isControlled: Boolean,
    /** Index of the strongest projected result (exact ratio); null when tied, uncontrolled or no data. */
    val leaderIndex: Int?,
    val explanation: String,
    val subjectRows: List<SubjectCompareRow>
)

object WhatIfComparison {

    fun compare(entries: List<WhatIfSnapshot>): ComparisonReport {
        require(entries.size >= 2) { "Need at least two scenarios to compare" }
        val first = entries.first().result
        val diffs = mutableListOf<ComparisonDifference>()

        fun anyDiff(f: (WhatIfResult) -> Any?) = entries.any { f(it.result) != f(first) }

        if (anyDiff { it.baseline.attended to it.baseline.conducted } || anyDiff { it.baseline.rangeStart to it.baseline.rangeEnd }) {
            diffs.add(ComparisonDifference(
                "Different baselines",
                entries.joinToString("; ") {
                    val b = it.result.baseline
                    "${it.name}: ${b.attended}/${b.conducted} h (${b.rangeStart ?: "no data"} → ${b.rangeEnd})"
                }
            ))
        }
        if (anyDiff { it.examTarget } || anyDiff { it.stipendTarget }) {
            diffs.add(ComparisonDifference(
                "Different targets",
                entries.joinToString("; ") { "${it.name}: exam ${it.result.examTarget}%, stipend ${it.result.stipendTarget}%" }
            ))
        }
        if (entries.any { it.mode != entries.first().mode } || anyDiff { it.horizonEnd }) {
            diffs.add(ComparisonDifference(
                "Different projection horizons",
                entries.joinToString("; ") {
                    "${it.name}: ${it.mode.label}${it.result.horizonEnd?.let { d -> " to $d" } ?: ""}"
                }
            ))
        }
        if (anyDiff { it.baseline.completeness }) {
            diffs.add(ComparisonDifference(
                "Different baseline completeness",
                entries.joinToString("; ") { "${it.name}: ${it.result.baseline.completeness.name.lowercase()}" }
            ))
        }
        if (anyDiff { it.batch } || anyDiff { it.course }) {
            diffs.add(ComparisonDifference(
                "Different batch or course",
                entries.joinToString("; ") { "${it.name}: ${it.result.batch}, ${it.result.course}" }
            ))
        }

        val controlled = diffs.isEmpty()
        var best = 0
        var tie = false
        for (i in 1 until entries.size) {
            val a = entries[i].result; val b = entries[best].result
            val c = WhatIfMath.compareRatio(a.projectedAttended, a.projectedConducted, b.projectedAttended, b.projectedConducted)
            if (c > 0) { best = i; tie = false } else if (c == 0) tie = true
        }
        val hasData = entries.any { it.result.projectedConducted > 0 }
        val leader = if (controlled && hasData && !tie) best else null

        val explanation = explain(entries, controlled, leader, tie)
        return ComparisonReport(entries, diffs, controlled, leader, explanation, subjectRows(entries))
    }

    private fun explain(entries: List<WhatIfSnapshot>, controlled: Boolean, leader: Int?, tie: Boolean): String {
        if (!controlled) {
            return "These scenarios do not start from the same assumptions (see the differences above), so the numbers are " +
                "shown side by side but are not a like-for-like ranking."
        }
        if (entries.none { it.result.projectedConducted > 0 }) return "None of these scenarios has any counted sessions yet."
        if (tie || leader == null) return "These scenarios project the same attendance ratio."
        // Controlled: same baseline, so differences come only from the plan itself.
        val sorted = entries.indices.sortedByDescending { entries[it].result.projectedAttended.toDouble() / maxOf(1, entries[it].result.projectedConducted) }
        val top = entries[sorted[0]]; val next = entries[sorted[1]]
        val gap = WhatIfMath.delta(next.result.projectedPct, top.result.projectedPct)
        val topAbs = top.result.weight(LineKind.PLANNED_ABSENT)
        val nextAbs = next.result.weight(LineKind.PLANNED_ABSENT)
        val reason = when {
            topAbs < nextAbs -> "it plans ${nextAbs - topAbs} fewer absent hour(s)"
            topAbs > nextAbs -> "it counts more attended sessions overall even though it plans ${topAbs - nextAbs} more absent hour(s)"
            else -> "its planned classes add more attended hours"
        }
        return "${top.name} projects ${top.result.projectedPct}% versus ${next.result.projectedPct}% for ${next.name} " +
            "(${if (gap >= 0) "+" else ""}$gap pts) because $reason."
    }

    private fun subjectRows(entries: List<WhatIfSnapshot>): List<SubjectCompareRow> {
        val order = LinkedHashMap<String, String>()
        entries.forEach { e -> e.result.subjects.forEach { order.putIfAbsent(it.code, it.shortName) } }
        return order.map { (code, name) ->
            SubjectCompareRow(code, name, entries.map { e ->
                e.result.subjects.firstOrNull { it.code == code }?.let { SubjectCompareValue(it.projectedAttended, it.projectedConducted) }
            })
        }
    }
}
