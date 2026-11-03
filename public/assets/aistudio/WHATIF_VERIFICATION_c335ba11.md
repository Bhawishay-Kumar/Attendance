# Verification status — read this first

| Check | Status |
|---|---|
| Gradle build / compile | **NOT RUN** — no Gradle, Android SDK, Kotlin compiler or network in the authoring environment |
| Unit tests (written) | **NOT RUN** — same reason |
| APK / on-device behaviour (themes, animations, back navigation) | **NOT VERIFIED** |
| Bracket/paren balance of all touched `.kt` files | Passed (scripted check) |
| Manual review of signatures against existing code (`ReportAnalyticsEngine`, `AttendanceMath`, `TimetableMatrix`, `StudentProfile`, repository API) | Done by reading source |

The code has not been compiled. Expect that a first build may surface small compile errors (imports, type inference in Compose lambdas).

## How to verify
1. `./gradlew :app:testDebugUnitTest --tests "com.example.whatif.*"` (Robolectric, sdk 34).
2. `./gradlew :app:assembleDebug`, install, then walk the checklist below.

## Manual checklist
- Home → What-If and Reports → Smart → What-If open the new screen; system Back steps back; Back on first screen with an unsaved plan asks to confirm.
- Light and dark theme: all screens readable; calendar colours distinguishable.
- Plan Thu = Absent (whole day) → Results shows lower %, Exam/Stipend status matches exact ratio.
- Month End shows the "assumed PRESENT" notice; planned dates after month end are listed as excluded.
- Save twice rapidly → one snapshot. Change attendance, reopen snapshot → numbers unchanged; Recalculate → separate banner, original intact.
- Compare two scenarios with different targets/baseline → amber warning, no "Strongest" badge.
- Real attendance (Home/Reports) unchanged after using the predictor.
- With system animations disabled, transitions are instant.

## Tests written (not executed)
`WhatIfCalculatorTest` (baseline start/gaps/partial, raw eligibility, plan-only, month-end, boundary, holidays, cancelled, validation, recovery),
`WhatIfStoreAndComparisonTest` (JSON round-trip, idempotent save, frozen snapshots, rename/duplicate/delete, unreadable & newer-schema preserved,
corrupt-root backup, comparison controlled/uncontrolled/tie).
Test fixtures assume today = 2026-11-04 and Batch A / Major Honours timetable from `TimetableMatrix`.
