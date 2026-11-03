# Changelog — What-If Predictor

## Added
- Full-screen What-If Predictor with calendar planning (whole-day or per-class), Plan Only / Month End modes, projected totals,
  exam/stipend status, per-subject breakdown, day-by-day lines, assumptions, best case.
- Smart Recovery Suggestions (per subject and overall; real upcoming classes only; flip-absences alternative).
- Saved Scenario Snapshots (save, open exactly as saved, explicit recalculation, rename, duplicate, delete).
- Scenario Comparison (2–3 scenarios, mismatch warnings, subject table, plain-language explanation).
- Unit tests: calculator, baseline, eligibility, month-end/boundary, recovery, JSON round-trip, store safety, comparison.

## Changed
- Home and Reports What-If entry points open the new screen. Removed the old `WhatIfPredictorModal` (simple `days * 7` estimate).

## Not implemented (by request)
- C: Custom Projection Range. E: Smart Plan Suggestions.

## Deviation from the Phase 1 plan
- No change to `AttendanceRepository`: the cumulative baseline is a read-only wrapper inside `WhatIfCalculator`
  using public repository state and the existing `ReportAnalyticsEngine`.
- Reports' What-If tile also uses the new screen (it called the same old modal).
