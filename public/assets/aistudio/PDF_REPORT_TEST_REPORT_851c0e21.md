# Test & verification report

## Environment
JDK 21, standalone Kotlin 2.2.10 compiler, JUnit 4.13.2. **No Android SDK, no `gradlew`, Google Maven unreachable → the Android app was NOT built; no APK was produced.**

## What was verified (and how)
The pure-Kotlin data layer (`PdfReportModels`, `PdfConfig`, `PdfReportBuilder`) plus the app's real `Models`, `AttendanceMath`, `TimetableMatrix`, `AktuHolidays`, `ReportDateRange`, `ReportAnalyticsEngine` were compiled with `kotlinc` (Firebase annotations stubbed) and run with JUnit:

**40 tests, 0 failures** = 20 new `PdfReportBuilderTest` + 20 existing `ReportAnalyticsEngineTest` (still green).

| Requirement | Result |
|---|---|
| Totals and every subject row equal engine output; rows sum to totals | Pass |
| Lecture + tutorial of one subject = one row; lab not merged | Pass |
| Lab credit applied once (2/2, total 7) | Pass |
| Eligibility weighted, not session-count (5/7=71.4% → not met although 5/6 sessions=83%) | Pass |
| Eligibility exact: 749/999 not met though app rounds to 75.0; text prints 74.97% | Pass |
| Targets from profile (65/90 custom) | Pass |
| Auto-credited-only → InsufficientData; no sessions → NoRecords | Pass |
| Future sessions never counted; no future rows | Pass |
| Detailed: Present/Absent only; Pending and Library/Seminar rows excluded; footnotes with unit | Pass |
| No Class as context only; fully cancelled day → "All classes"; holiday row; none counted | Pass |
| "To date" label only for running weekly/monthly | Pass |
| Custom range validation; weekly/monthly/custom resolution; future week/month rejected | Pass |
| Records map unchanged after building | Pass |
| Safe file names; no Semester field | Pass |
| Static check: PDF code contains no calls to any mark/holiday/profile/backup write API | Pass |
| Diff vs original ZIP: only the 5 listed files modified | Pass |

## NOT verified (needs Android SDK / device) — please test
Compilation of all Android/Compose files (renderer, file manager, ViewModel, UI, edits to Home/Reports/MainActivity/manifest), Gradle build, APK, and:
1. Summary and Detailed PDFs open correctly; multi-page tables continue without clipping; "Page X of Y" correct; text not cut off (long subject names wrap).
2. Preview shows the generated file; Download via picker writes a readable PDF; Share opens the chooser and the receiving app opens it.
3. Save/share failure messages (e.g. cancel picker, no handler).
4. Light and dark UI themes; zoom/scroll in preview; rotation during preview.
5. Entry points: Home PDF card, Reports (Overview and Smart & PDF tabs), Home notification sheet and inline row.
6. Empty / insufficient-data / error screens; Generate button disabled while generating.
7. Regression: attendance marking, timetable, What-If, Bunk Planner, settings, reminders, theme toggle.
8. Robolectric/instrumented tests (`AttendanceMathUnitTest` etc.) were not run here.

## UI motion upgrade — verification status
Not compiled or run (no Android SDK). A syntax-level pass with the standalone Kotlin compiler found no syntax errors; all remaining compiler output was unresolved Android/Compose symbols. On a device please check: smoothness on a low-end phone, the generating orb, the "Report ready" burst, selection animations, step transitions (and back button), zoom spring, page indicator, notice banner, light and dark themes, and that animations stay still with Remove animations / animator scale 0.

## Known notes
- `PdfPagesHolder.close()` is not serialised with an in-flight page render; a failure there is caught and ignored.
- Existing `MonthEndSummaryDialog` still hardcodes "(75%)/(80%)" labels (out of scope, untouched).
- Existing engine flags (`isExamEligible`) still use the 1-decimal rounded value, so at an exact boundary Home/Reports may say "eligible" while the PDF (exact) says "not met". Fixing the engine needs your approval; not done.
