BHTT Attendance Companion - Final Verified Build APK
======================================================
Status: Build Succeeded & 100% of Unit & UI Tests Passed
Package (Application ID): com.aistudio.bhttattendance.uxqvzk
Version: 1.0 (versionCode 1)
Build Date: Nov 4, 2026

CRITICAL INSTALLATION / LAUNCH INSTRUCTIONS:
---------------------------------------------
If you previously installed an earlier build of BHTT Attendance on your device:
1. UNINSTALL the old app completely from your phone (Settings > Apps > BHTT Attendance > Uninstall).
   Why? Earlier builds were built under package ID 'com.aistudio.bhtt.attendance'. If not uninstalled,
   Android will block installation due to a signature/package conflict, or tapping your phone's old home-screen
   shortcut will try to launch the uninstalled package and appear to do nothing.
2. Transfer and install 'BHTT-Attendance-Companion.apk' (or 'app-debug.apk').
3. Tap 'Install anyway' if Play Protect warns about an app from unknown sources.
4. Open the freshly installed app icon.

Fixes in this build:
--------------------
- Added custom AttendanceApplication and safe Firebase initialization to prevent any IllegalStateException on cold boot.
- Protected SettingsScreen and AttendanceRepository against missing/offline Firebase states.
- Enabled OnboardingFlow for clean first-time setup (Name, Roll Number, Batch, Course selection).
- Upgraded What-If Predictor with Aurora Glass UI, multi-subject chip selector, scrubbable projection charts, and confetti bursts.
- Upgraded Bunk Planner with Aurora Glass UI, result ring, segmented skip budget, and day-chips.
- Upgraded PDF Report Generator with native file generation and Android FileProvider sharing.
- 100% pass rate verified across all Robolectric UI render tests, tab switches, and calculation engines.

Files Available:
----------------
- /LATEST_BUILD_APK/BHTT-Attendance-Companion.apk (37 MB)
- /LATEST_BUILD_APK/app-debug.apk (37 MB)
- /LATEST_APK_NEW/BHTT-Attendance-Companion.apk (37 MB)
- /LATEST_APK_NEW/app-debug.apk (37 MB)
- /app/build/outputs/apk/debug/app-debug.apk (37 MB)
