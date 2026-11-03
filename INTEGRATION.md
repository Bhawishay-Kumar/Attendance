# Reports & Analytics — adapted to your latest app zip (bhtt-attendance.zip, 1 Nov)

**14 files only.** Everything else in your project is untouched. No new Gradle dependencies, no manifest changes.

## Files
New, `app/src/main/java/com/example/data/reports/`: ReportDateRange.kt, ReportAnalyticsEngine.kt, ReportTrendEngine.kt, ReportInsightsEngine.kt
New, `app/src/main/java/com/example/ui/reports/`: ReportsDesign.kt, ReportVisuals.kt, ReportCalendar.kt, ReportCharts.kt, ReportComponents.kt, ReportSections.kt
New tests, `app/src/test/java/com/example/`: ReportAnalyticsEngineTest.kt, ReportTrendInsightsTest.kt (28 tests)
Replaced: `ui/screens/ReportsScreen.kt` (your month-end certificate dialog is kept inside it)
One word changed: `ui/screens/TimetableScreen.kt` — `private fun AktuAcademicCalendarDialog(` is now `fun AktuAcademicCalendarDialog(` so Reports can open it.

## How it was adapted to your latest code
- Calculation rules match the latest `AttendanceRepository`: dates after today never count (even if marked), auto-present slots are credited on past/today working days with no stored record (a stored Absent cannot remove that credit), PENDING and NO_CLASS count for nothing, lecture + tutorial aggregate by subject code, holidays excluded.
- Uses only APIs that exist in your zip: `AttendanceViewModel` (repository, profile, selectedMonthId, selectMonth, showMonthEndSummary, toggleDarkMode, companion liveToday), `repository.recordsMap / holidayOverrides / isDateHoliday / getMonthlyAnalytics`, `TimetableMatrix.getScheduleForDay(Int, …)`, `AktuHolidays`, `AttendanceMath`, `ScenicSkyToggle`, `LocalGlassTheme`, the Outfit/Plus Jakarta fonts, and your `WhatIfPredictorModal` / `BunkPlannerModal`.
- The repository is NOT modified (the old optional delegation patch is dropped because your repository now has its own clock and rules).
- Static checks done here: every `com.example` import in the new files resolves to a real declaration in your zip; no duplicate top-level names in the new packages or against `ui.screens`; brackets balance.
- NOT done (no Kotlin compiler or network here): an actual Gradle build or test run. Run `./gradlew testDebugUnitTest assembleDebug` once after adding.

## Behaviour
- Reports: Monthly or Weekly (Mon–Sun). Custom Range removed.
- Four tiles (Attended / Missed / Conducted / Cancelled, credit hours), month calendar and single-week calendar with tap-a-day detail, animated ring hero, five tabs (Overview, Subjects, Trends, Insights, Smart & PDF).
- Archive ribbon lists only months where you actually marked Present/Absent (plus the current month); a stored selection with no data falls back to the current month; weekly navigation starts at your first marked week.
- Comparison with the previous period, forecast and insights need marked (not auto-only) attendance in the period.
- PDF is a Coming Soon card with no actions.
- Animations are skipped when the system animation scale is 0.

## If AI Studio reports an error
Paste the exact error here. The most likely spots are Compose API differences in `ReportCharts.kt` / `ReportVisuals.kt` (Canvas drawing) because they could not be compiled in this environment.
