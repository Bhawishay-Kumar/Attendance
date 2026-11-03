# Prompt for Google AI Studio (attach `bhtt-fixes-v7.zip`)

Surgical edit task, not a rewrite. My earlier cleanup already removed unused code from `HomeScreen.kt` and `TimetableContent.kt`, so for those two files **apply the patch or make the exact edits below; do NOT overwrite the whole file** and do not bring back any removed declaration. No Gradle, manifest, resource or `data/` changes. No other files.

## A. Replace whole files (cleanup did not touch them)
- `full-files/theme/Color.kt` -> `app/src/main/java/com/example/ui/theme/Color.kt`
- `full-files/settings/SettingsPalette.kt` -> `app/src/main/java/com/example/ui/settings/SettingsPalette.kt`

## B. `HomeScreen.kt` - apply `patches/HomeScreen.kt.patch` (or these 3 edits)
1. **Class cards (light mode).** In the "Today's Classes (All)" period list, the light-mode `surfaceBrush` currently uses `Color.White.copy(alpha = 0.90f)` -> `Color(0xFFF8FAFD).copy(alpha = 0.82f)`. Replace the light branch with a pearl glass tinted by the card's status:
   ```kotlin
   val lightTint = when {
       slot.subject.isAutoPresent -> Color(0xFF06B6D4)
       status == PeriodStatus.PRESENT -> Color(0xFF10B981)
       status == PeriodStatus.ABSENT -> Color(0xFFF43F5E)
       else -> Color(0xFF6366F1)
   }
   Brush.linearGradient(colors = listOf(
       com.example.ui.theme.LightCardSurface,
       androidx.compose.ui.graphics.lerp(com.example.ui.theme.LightPearl, lightTint, 0.10f)))
   ```
2. **Notification bell + number.** The badge was a hardcoded "3" placed inside the 36dp circle that had `.clip(CircleShape)`, so it was cut off and never reflected anything. Fix:
   - Move the 3-item `alerts` list out of the bottom sheet to the top of `HomeScreen` as `val notificationAlerts = listOf(Triple(title, desc, color), ...)` (same three entries), plus `var notificationsSeen by remember { mutableStateOf(false) }`; the sheet iterates `notificationAlerts`.
   - Bell = `Box(Modifier.size(40.dp), contentAlignment = Center)` containing (a) the 36dp glass circle (clip, background, border, `clickable { showNotificationSheet = true; notificationsSeen = true }`, `testTag("notification_bell_button")`) holding the icon, and (b) the badge as a sibling aligned `TopEnd` (not clipped): `defaultMinSize(16.dp,16.dp)`, rose gradient, 1.5dp ring in the background colour, text = count (`9+` above 9), shown only while `!notificationsSeen && notificationAlerts.isNotEmpty()`.
   - Add `import androidx.compose.foundation.layout.defaultMinSize` if missing.

## C. `TimetableContent.kt` - apply `patches/TimetableContent.kt.patch` (10 colour values in `DayColors`)
`glass = Color(0xCCE9ECFF)`, `glassBorder = Color(0x664F46E5)`, `glassSheen = Color(0x33F5F6FF)`, `hiFill = Color(0xE6DFE6FF)`, `dockFill = Color(0xD6E6EAFF)`, `dockBorder = Color(0x664F46E5)`, `trackFill = Color(0x99DCE2FF)`, `shadow = Color(0x264F46E5)`, `waterFill = Color(0xD9DDF4FF)`, `lunchFill = Color(0xE6FFF3D6)`. Only the light `DayColors` block changes; `NightColors` is untouched.

## What these do
- Light mode: Home class cards and Timetable subject/hero/break cards are lavender glass with an indigo hairline and soft indigo shadow instead of white; `LightCardSurface` = `0xE8F1F3FF`, `LightPearl` = `0xFFF3F4FF` (slightly deeper than before everywhere).
- Dark mode Settings: inner boxes (Minimum Attendance, Exam/Stipend/Buffer cells, Batch box), segmented tracks, chips and dividers now use a blue-slate glass (`inset 0x6620294A`, `seg 0x8C161E3A`, `chipBg 0x2E4F5BB8`) with indigo hairlines (`0x30/0x38/0x4D818CF8`) instead of near-black.
- Bell badge shows the real number of pending alerts, is no longer clipped, and clears after opening the sheet.

## Verify
```
grep -n "0xE8F1F3FF" app/src/main/java/com/example/ui/theme/Color.kt                 # 1 hit
grep -n "notificationsSeen" app/src/main/java/com/example/ui/screens/HomeScreen.kt    # 3+ hits
grep -n "Color.White.copy(alpha = 0.90f)" app/src/main/java/com/example/ui/screens/HomeScreen.kt   # no output
grep -n "0xCCE9ECFF" app/src/main/java/com/example/ui/timetable/TimetableContent.kt   # 1 hit
grep -rn "EligibilitySubCard\|BreakLegendCard\|TimetableRoute" app/src/main/java    # still no output (cleanup preserved)
./gradlew testDebugUnitTest assembleDebug
```
Copy the APK to `LATEST BUILD APK/` and tell me its full path and size.
