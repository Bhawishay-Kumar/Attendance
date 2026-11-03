# PDF Report feature — README

A native (Kotlin + Jetpack Compose) PDF report generator: **Weekly / Monthly / Custom Range**, **Summary / Detailed**, with in-app preview of the real PDF, Download (system file picker) and Share.

## How it works
`Home PDF card / Reports PDF card / Notifications "Weekly Attendance Report" row`
→ `AttendanceViewModel.showPdfPreview(true, mode)` (existing hook, previously unused)
→ `PdfReportHost` (one full-screen overlay mounted in `MainActivity`)
→ `PdfReportViewModel` → `PdfReportBuilder` → **`ReportAnalyticsEngine.analyze()`** (existing engine, unchanged) → `PdfReportRenderer` (`android.graphics.pdf.PdfDocument`) → `cache/reports/<name>.pdf`
→ preview (`PdfRenderer`) / save (`CreateDocument`) / share (`FileProvider`) all use that **same file**.

Mode strings for `showPdfPreview`: `SUMMARY`, `DETAILED`, `WEEKLY_SUMMARY`, `WEEKLY_DETAILED`.

## Calculation rules (approved decisions)
- No new attendance maths. Attended / conducted / per-subject credits come straight from `ReportAnalyticsEngine` (lecture+tutorial merged by subject code, lab weight once per slot, pending/No Class/holiday/future excluded, auto-present rules).
- **Eligibility** is exact and weighted: `attendedCredits*100 >= target*conductedCredits` (integers, no rounding). Targets come from the profile (`examTarget`, `stipendTarget`); nothing hardcoded. If the app's 1-decimal rounding would display a target as reached when it is not (e.g. 74.96% → "75.0%"), the PDF prints two truncated decimals (74.96%).
- **Only auto-credited sessions** in the period → *Insufficient data* state (no PDF). **No sessions at all** → *No records* state.
- **Detailed rows:** Present and Absent; No Class only for non-auto subjects (a fully cancelled day collapses to one "All classes" row); weekday holidays as one context row. Pending and per-session Library/Seminar rows are excluded; a footnote gives auto-credited **sessions and credit hours**, and another counts unmarked past sessions.
- **Custom Range:** picker blocks dates after today; start ≤ end validated. Weekly/Monthly periods still running are labelled **"To date (through <date>)"**. Future sessions are never counted.
- No semester anywhere; the profile model is untouched.

## In-app motion
Staggered entrances, animated selection, direction-aware transitions, a glowing "creating" orb, a "Report ready" check burst, shimmer page skeletons and a page indicator. Honours the system reduced-motion setting. Exported PDF is unaffected.

## PDF design
A4, white, navy headings, teal accents, grey support text, header/footer, "Page X of Y", repeating table headers, rows never split across pages. Uses the system sans typeface (the app's Outfit is a variable font; not embedded to avoid unverified rendering differences).

## Files
See `PDF_REPORT_CHANGELOG.md`. No new dependencies. Manifest gains a `FileProvider` (cache path `reports/` only). No storage permission required.

## Build & verify (Android Studio)
1. Open the project in Android Studio (AGP 9.1.1 / Gradle 9.3.1 per `gradle-wrapper.properties`; note the ZIP has no `gradlew` script/jar — Android Studio regenerates them, or run `gradle wrapper`).
2. `./gradlew :app:testDebugUnitTest` (runs `PdfReportBuilderTest` and the existing tests).
3. `./gradlew :app:installDebug`, then follow the on-device checklist in `PDF_REPORT_TEST_REPORT.md`.
