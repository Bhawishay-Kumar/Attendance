# What-If Attendance Predictor (native)

Replaces the old `WhatIfPredictorModal` (Home and Reports → Smart tools) with a full-screen, simulation-only planner.
Entry points are unchanged: Home "What-If" and Reports → Smart → What-If both call `viewModel.whatIf.open()`.

## Flow
Intro (baseline, mode) → Calendar (Whole day / Choose classes) → Classes → Results → Breakdown → Save.
Saved scenarios list → open (stored values) → recalculate (explicit) / rename / duplicate / delete → Compare (2–3).

## Baseline
Earliest stored manual Present/Absent record (not in the future) through today, computed with the existing
`ReportAnalyticsEngine.analyze`. Past sessions with no status are excluded (never counted Present or Absent).
The baseline is labelled **Partial / "from available records"** when past working days have unmarked sessions, or when
the Academic Session text contains a parseable start date earlier than the first record.

## Modes
- **Plan Only**: only dates you plan are added. Unplanned classes on those dates and all other dates are not counted.
- **Month End**: every remaining working day through the end of the current month is included; unplanned manual classes
  are **assumed Present** (stated on screen and stored in snapshots). Planned dates after month end are excluded with a note.
- Automatic sessions (Library, Seminar) on included dates are credited as attended. Cancelled (stored NO_CLASS) classes and holidays are excluded.
- Today and past dates cannot be planned (they are already actual records).

## Accuracy rules
- Eligibility uses exact integer ratios (`attended*100 >= target*conducted`); rounded percentages are display-only.
- Recovery/safe-skip numbers come from `AttendanceMath`. Recovery "attend the next N classes" uses only real, unplanned upcoming manual
  classes through month end (no invented classes). "Switch planned absences to Present" figures come from a real recalculation.
- Simulation never writes to attendance, holidays or profile.

## Saved snapshots
Stored in their own SharedPreferences file (`bhtt_whatif_prefs`, key `whatif_snapshots_v1`, schema v1). Each snapshot stores baseline
totals + date range, per-subject totals, targets, batch/course, plan, mode/horizon, assumptions, every counted class line,
recovery suggestions, best case and results. Opening one never recalculates. "Recalculate with current data" produces a separate,
unsaved result that can be saved as a new scenario. Unreadable or newer-schema entries are preserved untouched. Saving is idempotent per draft id.

## Comparison
Differences in baseline, targets, horizon/mode, completeness or batch/course are shown prominently; a "Strongest" badge is only
given when the scenarios are like-for-like (exact-ratio comparison). Otherwise numbers are shown without a winner.

## Files
New: `data/whatif/*` (Models, Calculator, Json, ScenarioStore, Comparison), `ui/whatif/*` (Controller, Host, Ui, PlanningUi, ResultsUi, SavedUi),
tests in `app/src/test/java/com/example/whatif/`.
Modified: `MainActivity.kt` (overlay host), `AttendanceViewModel.kt` (lazy `whatIf`), `HomeScreen.kt` (old modal removed, opens new screen),
`ReportsScreen.kt` (What-If tile opens new screen). `AttendanceRepository` and all calculation/report code are untouched.
