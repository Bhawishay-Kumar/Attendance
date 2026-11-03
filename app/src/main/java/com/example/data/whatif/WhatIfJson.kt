package com.example.data.whatif

import org.json.JSONArray
import org.json.JSONObject

/** Versioned JSON codec. Everything needed to show a snapshot is stored, so opening it never recalculates. */
object WhatIfJson {

    private inline fun <T> JSONArray.mapObjects(f: (JSONObject) -> T): List<T> =
        (0 until length()).map { f(getJSONObject(it)) }

    private fun <T> List<T>.toArr(f: (T) -> JSONObject): JSONArray = JSONArray().also { a -> forEach { a.put(f(it)) } }
    private fun List<String>.toStrArr(): JSONArray = JSONArray().also { a -> forEach { a.put(it) } }
    private fun JSONArray.strings(): List<String> = (0 until length()).map { getString(it) }
    private fun JSONObject.optNullableString(k: String): String? = if (has(k) && !isNull(k)) getString(k) else null

    private fun targetToJson(t: TargetCheck) = JSONObject()
        .put("target", t.target).put("status", t.status.name).put("need", t.hoursNeeded).put("skip", t.safeSkipHours)

    private fun targetFromJson(o: JSONObject) = TargetCheck(
        o.getInt("target"), TargetStatus.valueOf(o.getString("status")), o.getInt("need"), o.getInt("skip")
    )

    private fun lineToJson(l: ClassLine) = JSONObject()
        .put("k", l.kind.name).put("d", l.date).put("p", l.periodNumber).put("pl", l.periodLabel)
        .put("sc", l.subjectCode).put("sn", l.subjectName).put("w", l.weight).put("lab", l.isLab)

    private fun lineFromJson(o: JSONObject) = ClassLine(
        LineKind.valueOf(o.getString("k")), o.getString("d"), o.getInt("p"), o.getString("pl"),
        o.getString("sc"), o.getString("sn"), o.getInt("w"), o.getBoolean("lab")
    )

    private fun plannedToJson(p: PlannedClass) = JSONObject()
        .put("d", p.date).put("p", p.periodNumber).put("sc", p.subjectCode).put("dec", p.decision.name)

    private fun plannedFromJson(o: JSONObject) =
        PlannedClass(o.getString("d"), o.getInt("p"), o.getString("sc"), PlanDecision.valueOf(o.getString("dec")))

    private fun baselineToJson(b: WhatIfBaseline) = JSONObject()
        .put("start", b.rangeStart ?: JSONObject.NULL).put("end", b.rangeEnd)
        .put("att", b.attended).put("cond", b.conducted)
        .put("subjects", b.subjects.toArr {
            JSONObject().put("code", it.code).put("short", it.shortName).put("full", it.fullName)
                .put("lab", it.isLab).put("credit", it.creditHours).put("att", it.attended).put("cond", it.conducted)
        })
        .put("completeness", b.completeness.name).put("unmarkedSessions", b.unmarkedSessions)
        .put("unmarkedDays", b.unmarkedDays).put("hint", b.sessionStartHint ?: JSONObject.NULL)
        .put("notes", b.notes.toStrArr())

    private fun baselineFromJson(o: JSONObject) = WhatIfBaseline(
        rangeStart = o.optNullableString("start"), rangeEnd = o.getString("end"),
        attended = o.getInt("att"), conducted = o.getInt("cond"),
        subjects = o.getJSONArray("subjects").mapObjects {
            SubjectTotals(it.getString("code"), it.getString("short"), it.getString("full"),
                it.getBoolean("lab"), it.getInt("credit"), it.getInt("att"), it.getInt("cond"))
        },
        completeness = BaselineCompleteness.valueOf(o.getString("completeness")),
        unmarkedSessions = o.getInt("unmarkedSessions"), unmarkedDays = o.getInt("unmarkedDays"),
        sessionStartHint = o.optNullableString("hint"), notes = o.getJSONArray("notes").strings()
    )

    private fun resultToJson(r: WhatIfResult) = JSONObject()
        .put("mode", r.mode.name).put("horizonEnd", r.horizonEnd ?: JSONObject.NULL).put("calcDate", r.calcDate)
        .put("batch", r.batch).put("course", r.course)
        .put("examTarget", r.examTarget).put("stipendTarget", r.stipendTarget).put("buffer", r.safetyBuffer)
        .put("baseline", baselineToJson(r.baseline))
        .put("projAtt", r.projectedAttended).put("projCond", r.projectedConducted)
        .put("exam", targetToJson(r.exam)).put("stipend", targetToJson(r.stipend))
        .put("subjects", r.subjects.toArr {
            JSONObject().put("code", it.code).put("short", it.shortName).put("full", it.fullName).put("lab", it.isLab)
                .put("ba", it.baselineAttended).put("bc", it.baselineConducted)
                .put("pa", it.projectedAttended).put("pc", it.projectedConducted)
                .put("exam", targetToJson(it.exam)).put("stipend", targetToJson(it.stipend))
        })
        .put("lines", r.lines.toArr { lineToJson(it) })
        .put("excluded", r.excludedDates.toArr { JSONObject().put("d", it.date).put("r", it.reason) })
        .put("dropped", r.dropped.toArr { JSONObject().put("item", plannedToJson(it.item)).put("r", it.reason) })
        .put("assumptions", r.assumptions.toStrArr())
        .put("boundary", r.boundaryNote ?: JSONObject.NULL)

    private fun resultFromJson(o: JSONObject) = WhatIfResult(
        mode = ProjectionMode.valueOf(o.getString("mode")), horizonEnd = o.optNullableString("horizonEnd"),
        calcDate = o.getString("calcDate"), batch = o.getString("batch"), course = o.getString("course"),
        examTarget = o.getInt("examTarget"), stipendTarget = o.getInt("stipendTarget"), safetyBuffer = o.getInt("buffer"),
        baseline = baselineFromJson(o.getJSONObject("baseline")),
        projectedAttended = o.getInt("projAtt"), projectedConducted = o.getInt("projCond"),
        exam = targetFromJson(o.getJSONObject("exam")), stipend = targetFromJson(o.getJSONObject("stipend")),
        subjects = o.getJSONArray("subjects").mapObjects {
            SubjectProjection(it.getString("code"), it.getString("short"), it.getString("full"), it.getBoolean("lab"),
                it.getInt("ba"), it.getInt("bc"), it.getInt("pa"), it.getInt("pc"),
                targetFromJson(it.getJSONObject("exam")), targetFromJson(it.getJSONObject("stipend")))
        },
        lines = o.getJSONArray("lines").mapObjects { lineFromJson(it) },
        excludedDates = o.getJSONArray("excluded").mapObjects { ExcludedDate(it.getString("d"), it.getString("r")) },
        dropped = o.getJSONArray("dropped").mapObjects { DroppedDecision(plannedFromJson(it.getJSONObject("item")), it.getString("r")) },
        assumptions = o.getJSONArray("assumptions").strings(), boundaryNote = o.optNullableString("boundary")
    )

    private fun recoveryToJson(s: RecoverySuggestion) = JSONObject()
        .put("code", s.scopeCode ?: JSONObject.NULL).put("name", s.scopeName)
        .put("kind", s.targetKind.name).put("target", s.target).put("state", s.state.name)
        .put("cur", s.currentPct).put("proj", s.projectedPct).put("need", s.hoursNeeded)
        .put("k", s.classesNeeded ?: JSONObject.NULL).put("avail", s.availableHours)
        .put("first", s.firstDate ?: JSONObject.NULL).put("last", s.lastDate ?: JSONObject.NULL)
        .put("after", s.pctIfAttended ?: JSONObject.NULL).put("skip", s.safeSkipHours)
        .put("flip", s.flip?.let {
            JSONObject().put("n", it.classCount).put("h", it.weightedHours).put("pct", it.projectedPct)
                .put("status", it.status.name).put("first", it.firstDate)
        } ?: JSONObject.NULL)
        .put("msg", s.message)

    private fun recoveryFromJson(o: JSONObject) = RecoverySuggestion(
        scopeCode = o.optNullableString("code"), scopeName = o.getString("name"),
        targetKind = TargetKind.valueOf(o.getString("kind")), target = o.getInt("target"),
        state = RecoveryState.valueOf(o.getString("state")),
        currentPct = o.getDouble("cur"), projectedPct = o.getDouble("proj"), hoursNeeded = o.getInt("need"),
        classesNeeded = if (o.isNull("k")) null else o.getInt("k"), availableHours = o.getInt("avail"),
        firstDate = o.optNullableString("first"), lastDate = o.optNullableString("last"),
        pctIfAttended = if (o.isNull("after")) null else o.getDouble("after"), safeSkipHours = o.getInt("skip"),
        flip = if (o.isNull("flip")) null else o.getJSONObject("flip").let {
            FlipOutcome(it.getInt("n"), it.getInt("h"), it.getDouble("pct"), TargetStatus.valueOf(it.getString("status")), it.getString("first"))
        },
        message = o.getString("msg")
    )

    fun toJson(s: WhatIfSnapshot): JSONObject = JSONObject()
        .put("schema", s.schemaVersion).put("id", s.id).put("name", s.name).put("savedAt", s.savedAtMillis)
        .put("recalc", s.isRecalculation).put("derivedFrom", s.derivedFromId ?: JSONObject.NULL)
        .put("mode", s.mode.name).put("style", s.planningStyle)
        .put("plan", s.plan.items.toArr { plannedToJson(it) })
        .put("result", resultToJson(s.result))
        .put("recovery", s.recovery.toArr { recoveryToJson(it) })
        .put("bestCase", s.bestCase?.let {
            JSONObject().put("att", it.attended).put("cond", it.conducted)
                .put("exam", it.exam.name).put("stipend", it.stipend.name).put("end", it.horizonEnd)
        } ?: JSONObject.NULL)

    /** Returns null for snapshots written by a newer schema (kept untouched in storage). Throws if malformed. */
    fun fromJson(o: JSONObject): WhatIfSnapshot? {
        if (o.optInt("schema", 0) > WhatIfSnapshot.CURRENT_SCHEMA) return null
        return WhatIfSnapshot(
            schemaVersion = o.getInt("schema"), id = o.getString("id"), name = o.getString("name"),
            savedAtMillis = o.getLong("savedAt"), isRecalculation = o.optBoolean("recalc", false),
            derivedFromId = o.optNullableString("derivedFrom"),
            mode = ProjectionMode.valueOf(o.getString("mode")), planningStyle = o.getString("style"),
            plan = WhatIfPlan(o.getJSONArray("plan").mapObjects { plannedFromJson(it) }),
            result = resultFromJson(o.getJSONObject("result")),
            recovery = o.getJSONArray("recovery").mapObjects { recoveryFromJson(it) },
            bestCase = if (o.isNull("bestCase")) null else o.getJSONObject("bestCase").let {
                BestCaseSummary(it.getInt("att"), it.getInt("cond"),
                    TargetStatus.valueOf(it.getString("exam")), TargetStatus.valueOf(it.getString("stipend")), it.getString("end"))
            }
        )
    }
}
