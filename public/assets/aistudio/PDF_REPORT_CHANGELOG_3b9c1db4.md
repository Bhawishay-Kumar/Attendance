# Changelog — PDF Report feature

## Added
| File | Purpose |
|---|---|
| `data/pdf/PdfReportModels.kt` | Request/outcome/row models, `PdfRangeValidator` |
| `data/pdf/PdfConfig.kt` | User choices + `PdfConfigResolver` (week/month/custom → validated `ReportDateRange`) |
| `data/pdf/PdfReportBuilder.kt` | Adapter over `ReportAnalyticsEngine`; exact weighted eligibility; rows/footnotes/labels |
| `data/pdf/PdfReportRenderer.kt` | Multi-page `PdfDocument` drawing (white, print-friendly) |
| `data/pdf/PdfFileManager.kt` | Cache file, FileProvider share intent, save-to-picked-URI |
| `ui/viewmodel/PdfReportViewModel.kt` | Flow state, off-main-thread generation, duplicate-tap guard, real save/share feedback |
| `ui/pdf/PdfReportHost.kt` | Single overlay host (config → generating → preview/empty/insufficient/error) |
| `ui/pdf/PdfConfigScreen.kt` | Period + type selection, week stepper, month chips, custom date pickers |
| `ui/pdf/PdfPreviewScreen.kt` | `PdfRenderer` page preview, zoom, Download / Share / Back actions |
| `ui/pdf/PdfStateScreens.kt` | Loading, empty, insufficient, error states; shared button |
| `ui/pdf/PdfEntryCard.kt` | Reports-screen entry card (replaces "Coming soon") |
| `res/xml/file_paths.xml` | FileProvider path: `cache/reports/` only |
| `test/.../PdfReportBuilderTest.kt` | 20 JVM tests comparing against the existing engine |

## UI motion upgrade (in-app screens only; the exported PDF is unchanged)
| File | Change |
|---|---|
| `ui/pdf/PdfMotion.kt` (new) | `staggerIn`, `floating`, `shimmer`, `AiOrb` (glowing sweep-ring orb with orbiting sparkles), `CyclingStatus`, `SuccessBurst` (self-drawing check) |
| `ui/pdf/PdfConfigScreen.kt` | Staggered section entrance; animated hero; option cards with colour/spring-scale/check-pop; direction-aware period panel; sliding week label; popping month chips; live "Your report" strip; flowing-gradient glowing Generate button |
| `ui/pdf/PdfStateScreens.kt` | Generating: orb + cycling status + indeterminate bar; empty/warning/error cards with pulsing halo, floating/wobbling icon and staggered text/buttons |
| `ui/pdf/PdfPreviewScreen.kt` | "Report ready" burst (tap to skip), shimmer skeleton then page fade-in, staggered pages/buttons, spring zoom, floating "Page X of Y" pill, animated notice banner (success auto-dismisses) |
| `ui/pdf/PdfReportHost.kt` | Overlay scale+slide+fade; direction-aware step transitions (config → creating → preview, and back); animated title |
| `ui/pdf/PdfEntryCard.kt` | Floating gradient icon and soft light sweep on the Reports card |
| `ui/viewmodel/PdfReportViewModel.kt` | Creating animation shown for at least 1.3 s so it reads as a transition, not a flash |

All loops are driven in draw/graphicsLayer blocks (no per-frame recomposition) and stop when the system "reduced motion" setting is on.

## Modified (small, additive)
| File | Change |
|---|---|
| `AndroidManifest.xml` | `<provider>` for FileProvider |
| `MainActivity.kt` | Import + mount `PdfReportHost` after the dock |
| `ui/reports/ReportSections.kt` | Two `PdfComingSoonCard` call sites → `PdfReportEntryCard`; new optional params (`onOpenPdf`, `analytics`) |
| `ui/screens/ReportsScreen.kt` | Pass `showPdfPreview` callbacks to the two sections |
| `ui/screens/HomeScreen.kt` | Notification sheet + inline "Weekly Attendance Report" rows open the report (mode `WEEKLY_SUMMARY`) |

## Not changed
`AttendanceMath`, `ReportAnalyticsEngine`, `ReportDateRange`, repository, timetable, `Models.kt` (profile), `AttendanceViewModel`, theme, What-If, Bunk Planner, reminders. `PdfComingSoonCard` is kept (now unused).
Home's "PDF Report" card already called `showPdfPreview`, which nothing observed; it now works without edits.
