I'm attaching `bhtt-bunk-planner-ui-upgrade-patch.zip`, a UI-only upgrade for my existing Bunk Planner (Kotlin + Jetpack Compose). The Bunk Planner is already integrated, builds, and its tests pass. This patch only replaces its screens.

## Apply
Extract the patch over my project and overwrite these 4 files, with no other changes:
- app/src/main/java/com/example/ui/bunk/BunkPlannerScreen.kt
- app/src/main/java/com/example/ui/bunk/BunkPlannerComponents.kt
- app/src/main/java/com/example/ui/bunk/BunkOptionDetails.kt
- app/src/test/java/com/example/BunkPlannerUiTest.kt (replaces my current UI test; it scrolls to nodes and passes liveMotion = false)

## Then
1. Build (`./gradlew :app:assembleDebug`) and fix any compile, resource or Compose errors. The patch was written without a compiler, so a few small API or import errors are possible.
2. Run `./gradlew :app:testDebugUnitTest` and fix failures, especially BunkPlannerUiTest and BunkPlannerEngineTest.
3. Make only the minimal changes needed to get a clean build and passing tests.

## Rules
- UI only. Do not change BunkPlannerEngine, BunkPlannerModels, AttendanceProjection, the ViewModel, MainActivity, HomeScreen or ReportsScreen.
- Keep the public signatures `BunkPlannerScreen(viewModel)`, `BunkPlannerContent(result, detailRank, onOpenDetails, onCloseDetails, onBack, onRefresh, liveMotion = true)` and `BunkOptionDetails(option, overview, onBack)`.
- Keep all `testTag` names (bunk_planner_list, bunk_card_N, bunk_details_N, bunk_details, bunk_details_back, bunk_back, bunk_refresh, bunk_loading, bunk_no_safe, bunk_insufficient, bunk_no_remaining, bunk_overview, bunk_assumption, bunk_warning, bunk_fewer_note, bunk_risk_N, bunk_explanation).
- Do not change any calculation, ranking, safety rule, or the "month-end projection" and "Provisional" wording.
- Do not write to attendance, timetable, holidays, targets or profile. No new dependencies, no WebView.
- Respect the existing reduced-motion setting (`rememberMotionEnabled`) and keep both light and dark themes working.
- If a test fails because the test is wrong rather than the code, say so before changing it.

## What the upgrade contains (do not strip these)
- Animated aurora background (drifting colour blobs), plus the starfield in dark mode.
- Gradient title, spinning refresh button, springy press-scale on buttons and cards.
- Overview card with an animated attendance ring (sweeping arc, count-up percentage, target tick), stat tiles, and a "where your breaks fall" month strip.
- Loading skeleton with shimmer blocks and cycling status text.
- Recommendation cards with a per-rank accent gradient, pulsing rank medal, shine sweep and twinkles on the best option, count-up days-off badge, staggered date chips, a missed-vs-free bar, animated projected bar, risk pill and gradient "View Details" button.
- Animated list-to-details transition (fade, scale, slide).
- Details screen with a leave-summary hero, projected ring, vertical timeline with expandable day cards, per-subject now-vs-projected bars, and an explanation card.
- Animated empty and warning states (floating icon with pulsing halo).

## Report
Build result, tests run/passed/failed, every file you changed and why, and anything you could not verify. Never claim a build or test passed unless you actually ran it. Do not claim the visuals look right unless you saw them on a device or emulator.
