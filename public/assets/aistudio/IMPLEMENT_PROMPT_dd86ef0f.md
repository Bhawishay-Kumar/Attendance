# Final prompt — paste into AI Studio

Before pasting: upload / attach `AttendanceUiUpgradeV2.kt` to the chat (or paste its full contents right after the prompt). Open `preview.html` in a browser to see the target look and motion.

```
You are a senior Android engineer + motion designer. My project is a Kotlin +
Jetpack Compose attendance tracker. I have NOT applied any UI changes yet.
Apply the full UI upgrade described below to my EXISTING project.

REFERENCE
- AttendanceUiUpgradeV2.kt (attached) is the complete reference implementation:
  design system, What-If Predictor, Bunk Planner, PDF Report screens, and a host
  with a liquid bottom nav. It is self-contained and already includes everything
  — do not look for any earlier version.

STEPS
1. Read my current code. Find the existing screens for What-If Predictor,
   Bunk Planner and PDF Report, plus their ViewModels/state.
2. Add the reference code to my UI package (change the package line). If names
   clash with mine, rename the reference ones, don't rename mine.
3. Replace the UI of the three existing screens with the new composables,
   feeding them MY real data: subject name, attended, total, required %,
   upcoming class labels (Bunk Planner), and my existing PDF state/progress
   (map to ReportState.Idle / Generating / Done and progress 0f..1f).
   Hook onGenerate / onShare / onReset to my EXISTING PDF generation and
   share code. Keep my navigation; use UpgradedFeatureHost only if it fits,
   otherwise call the three screens individually and keep my own nav bar.
4. If my app has multiple subjects, add a simple animated subject selector
   (horizontal chips) above the screens and pass the chosen subject in.
5. Check build.gradle.kts: Compose BOM must provide Animation 1.7+ (needed for
   `togetherWith`). If older, bump the BOM, or replace `togetherWith` with `with`.
6. Make everything compile. Fix imports. Add @Preview (dark) for each screen.

HARD RULES
- UI layer only. Do NOT change business logic, data models, Room/Firebase,
  attendance calculations already in my ViewModels, or the PDF generator.
- Compose + Material3 only. No new libraries.
- Keep 60fps: animate in graphicsLayer / draw phase, no heavy recomposition.
- Keep the exact visual + motion design of the reference: Aurora Glass theme,
  rotating gradient border on hero cards, animated ring with ticks and spark,
  confetti when status becomes Safe, shake on Danger, draggable target slider,
  scrubbable projection chart, segmented skip budget, day-chip ripples and
  auto-plan cascade, fanning PDF pages, step checklist, morphing CTA, haptics.

OUTPUT
- Show every file you changed or created, with full code.
- End with a short checklist of anything I must wire manually.
Work screen by screen, What-If Predictor first.
```
