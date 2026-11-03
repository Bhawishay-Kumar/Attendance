# BHTT Attendance Companion (IIHT Varanasi — BHTT 5th Sem Edition)

A high-fidelity, luxury glassmorphic Android attendance tracking application engineered for B.Tech Handloom & Textile Technology (BHTT 5th Semester, AY 2026-27) students at the Indian Institute of Handloom Technology (IIHT), Varanasi. It unites dual-theme visual styling, a celestial moving starfield, real-time timetable resolution, official AKTU 2026 holiday rules, weighted theory/lab attendance mathematics, recovery planners, and Firebase Firestore cloud synchronization.

---

## User Review & Critical Decisions

> [!IMPORTANT]
> The following core product and architecture decisions were aligned based on system recommendations:

- **Confirmed Decision 1 (Course Selection Default)**: Default elective slot is set to **Major Honours: HTFT 501 (Clothing Comfort)** (1 Hr Lecture/Tutorial). Users can seamlessly switch to **Minor: MNPM 501 (Production Planning & Control)** or **Digital Library (Auto-Present 100%)** in Onboarding Screen 3 or Settings.
- **Confirmed Decision 2 (Batch Assignment Default)**: Default practical laboratory allocation is set to **Batch A** (Quality Lab Wed, Woven Lab Thu, CPT-II Lab Fri), with instant toggle to **Batch B** (Seminar Wed, CPT-II Lab Thu, Quality Lab Fri) updating all timetable and attendance views.
- **Confirmed Decision 3 (Firebase Cloud Sync & Auth Architecture)**: Built with an offline-first repository backed by **Firebase Firestore** with **Google Sign-In via Jetpack Credential Manager** (`GetSignInWithGoogleOption`). Attendance data is immediately usable offline and seamlessly syncs to the user's cloud Firestore profile once signed in.
- **Confirmed Decision 4 (Typography Engine)**: Bundle Google Fonts locally in `res/font/` (`Outfit` for bold headings, `Plus Jakarta Sans` for body/cards, `Space Grotesk` with `tabular-nums` for counters/metrics, and `Caveat` for motivational handwritten quotes) to comply with offline Android runtime constraints.

---

## 1. Overview & Core Concept

- **What It Does**: Empowers BHTT 5th semester students to log and monitor daily attendance class-by-class according to their official IIHT Varanasi schedule, track their progress against dual eligibility thresholds (75% Exam cutoff and 80% Stipend cutoff), calculate exact recovery requirements, plan bunks safely, simulate "What-If" scenarios, and generate shareable monthly summary reports.
- **Target Audience / Persona**: Bhawishya Kumar and fellow BHTT 5th semester students at IIHT Varanasi needing automated credit tracking across 1-hour lectures, 2-hour labs, tutorials, and auto-credited library/seminar sessions.
- **Key Value**:
  * Eliminates manual calculation errors with automatic 1-credit vs. 2-credit lab weighting.
  * Guarantees institutional rule compliance with pre-loaded official AKTU 2026 holidays and auto-present immunity for Library/Seminar.
  * Delivers a distinct, polished luxury aesthetic across daylight and cosmic night modes with zero generic defaults.

---

## 2. User Experience & Visual Design

### 2.1 Aesthetic Themes & Visual Tokens

```
┌───────────────────────────────────────────────┬───────────────────────────────────────────────┐
│ LIGHT THEME (Pastel Alabaster Glass)          │ DARK THEME (Celestial Academic Glass)         │
├───────────────────────────────────────────────┼───────────────────────────────────────────────┤
│ Canvas: Soft lavender-alabaster (#F8FAFC →   │ Canvas: Cosmic Obsidian (#070B14 → #0B0F19)   │
│         #EEF2FF) with pastel floating aura    │ Canvas Layer: 3-layer HTML5/Compose starfield │
│         orbs (#C7D2FE, #E9D5FF)               │               with twinkling and shooting     │
│ Cards:  rgba(255, 255, 255, 0.85), blur(20dp) │               stars every 6–10 seconds        │
│ Border: 1px solid rgba(79, 70, 229, 0.14)     │ Cards:  rgba(21, 29, 46, 0.72), blur(24dp)   │
│ Text:   #0F172A (Primary), #475569 (Secondary)│ Border: 1px solid rgba(255, 255, 255, 0.12)   │
│ Accent: Royal Indigo (#4F46E5) & Violet       │ Text:   #F8FAFC (Primary), #94A3B8 (Secondary)│
│                                               │ Accent: Glowing Emerald, Amber, Rose, Cyan    │
└───────────────────────────────────────────────┴───────────────────────────────────────────────┘
```

- **Top & Bottom Progressive Edge Blur**:
  * Top fixed blur (height 64dp) dissolve gradient under status bar and sticky header.
  * Bottom fixed blur (height 104dp) dissolve gradient behind the floating bottom dock so cards scroll seamlessly without harsh cutoffs.
- **Scenic Animated Day/Night Sky Pill**:
  * 68dp × 34dp interactive pill in top headers and settings.
  * Day: Sky-blue track (`#38BDF8` to `#60A5FA`), floating clouds, glowing sun orb (`#FACC15`).
  * Night: Midnight cosmic track (`#090D16` to `#1E1B4B`), twinkling stars, cratered crescent moon orb (`#FEF08A`).
  * Spring slide transition with 180° rotation and smooth theme switching.

### 2.2 Navigation & User Flows

1. **Onboarding Flow (Screens 1 to 5)**:
   * *Screen 1 (Intro)*: Animated falling stars, mortarboard icon, 2x2 Bento features grid, Caveat quote, and "Get Started".
   * *Screen 2 (Student Profile)*: Name input ("Bhawishya Kumar"), DOB ("2005-10-26" with Birthday Magic badge), and Batch A / B selector.
   * *Screen 3 (Subject Mapping)*: Course selection (Major Honours HTFT 501 / Minor MNPM 501 / Digital Library) with curriculum overview cards.
   * *Screen 4 (Preferences & Targets)*: Day/Night theme preview, 75% Exam & 80% Stipend cutoff steppers, 3%–5% Safety Buffer slider with 3-tier status preview, and reminder toggles.
   * *Screen 5 (Welcome & Google Backup)*: Personalized welcome greeting, dusk campus banner, Google Sign-In connect button, Mid-Month Bulk Backfill modal launcher, and "Enter Dashboard".
2. **5-Tab Floating Glassmorphic Bottom Dock**:
   * **Tab 1: Home (`Home`)**: Hero Monthly Attendance Radial Gauge, Dual Eligibility Cards (Exam 75% & Stipend 80%), Recovery Box (hours needed to reach target or safe hours to skip), 2-page swipeable Quick Activities carousel (*Page 1: What-If, PDF Report, Bunk Planner* ↔ *Page 2: Mark Attendance, Reports, Timetable*), and Today's Live Schedule Snapshot.
   * **Tab 2: Attendance (`Attendance`)**: Interactive Mon–Fri date ribbon with status dots, Month calendar picker, "Future Marking: OFF/ON" toggle, Bulk Attendance Maker (`Mark All Present`, `Mark All Absent` respecting Auto-Present Immunity, and `Holiday / No Classes Today` toggle), Category Filter chips, and 7-Period checklist (09:30 AM – 04:40 PM) with 1-hr lecture and 2-hr lab badges, water break dividers (11:10 & 03:40), and lunch break divider (01:00–02:00).
   * **Tab 3: Reports (`Reports`)**: Monthly archive selector (August, September, October, November), timeframe switcher, Hero Analytics Bento, Auto-Present Boost strip, Month-End Summary dialog, PDF Report preview modal, and Subject-Wise Breakdown table with recovery/safe-skip badges.
   * **Tab 4: Timetable (`Timetable`)**: Weekday tab bar (Mon–Fri), 2-subjects-side-by-side compact bento grid for 1-hour classes, full-width spanned cards for 2-hour labs and seminars with "2 HRS • 2X CREDIT" badge, break timeline banners, Period VIII excluded, and upcoming AKTU 2026 holiday preview strip.
   * **Tab 5: Settings (`Settings`)**: Profile and batch/course live edit card, Attendance rules and target steppers, Appearance & celestial motion switches, Google Cloud sync and JSON backup/export/import tools, Replay Onboarding button, and Developer Details footer card crediting **Bhawishya Kumar (B.Tech Handloom & Textile Technology, IIHT Varanasi)**.

---

## 3. Key Product Decisions & Trade-Offs

- **Decision 1: Explicit 7-Period Schedule with Period VIII Exclusion**:
  * *Approach*: Periods I through VII (09:30 AM – 04:40 PM) form the daily schedule (35 active credit-hours/week). Period VIII (04:40–05:30 PM Swacchtaa Abhiyan / Doubt Clearing) is strictly omitted from all attendance calculations and timetable cards.
  * *Why*: Conforms strictly to official academic timetable guidelines where Period VIII is voluntary/remedial and does not count towards credit attendance.
- **Decision 2: Auto-Present Immunity Rule**:
  * *Approach*: Library (1 Hr), Seminar (2 Hrs), and Digital Library (1 Hr, if chosen) are locked as `Present ("AUTO_P")` on working days. Tapping `"Mark All Absent"` marks only normal lectures and labs as Absent, leaving auto-credit slots immune.
  * *Why*: Guarantees students earn institutional non-instructional attendance credits automatically as per IIHT departmental practice.
- **Decision 3: Holiday Freezing**:
  * *Approach*: On pre-loaded official AKTU 2026 holidays (or when the manual "Holiday" switch is active), all 7 periods are frozen at 0 conducted / 0 attended.
  * *Why*: Prevents holiday dates from inflating conducted class tallies and distorting student percentages.
- **Decision 4: State Management & Persistence**:
  * *Approach*: `AttendanceViewModel` manages an immutable `AttendanceState` backed by `AttendanceRepository`. State writes persist locally to `SharedPreferences`/JSON storage immediately for zero-latency offline performance, and sync bidirectionally with Firebase Firestore under `/users/{userId}/...` when authenticated with Google.
  * *Why*: Protects user effort from connectivity loss while delivering multi-device cloud backup.

---

## 4. Technical Architecture & Data Strategy

### 4.1 System & Component Diagram

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                             Jetpack Compose UI Layer                        │
├─────────────────────────────────────────────────────────────────────────────┤
│  Top Progressive Edge Blur (64dp) + Sticky Header + Scenic Day/Night Toggle  │
├─────────────────────────────────────────────────────────────────────────────┤
│  [Onboarding Flow: Screens 1–5]  OR  [Main App Content with Floating Dock]  │
│    ├── Tab 1: HomeScreen (Hero Gauge, Dual Targets, Recovery Box, Carousel) │
│    ├── Tab 2: AttendanceScreen (Date Ribbon, Bulk Bar, 7-Period Checklist)  │
│    ├── Tab 3: ReportsScreen (Monthly Archive, Auto Boost, Subject Table)   │
│    ├── Tab 4: TimetableScreen (Compact 2-per-row grid, 2-Hr Lab Spanners)   │
│    └── Tab 5: SettingsScreen (Profile, Targets, Cloud Sync, Dev Credits)   │
├─────────────────────────────────────────────────────────────────────────────┤
│  Bottom Progressive Edge Blur (104dp) + 5-Tab Floating Glassmorphic Dock    │
└──────────────────────────────────────┬──────────────────────────────────────┘
                                       │ StateFlow & Actions
                                       ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│                           AttendanceViewModel                               │
│  - Active Tab, Selected Date, Selected Month ("2026-08".."2026-12")         │
│  - Profile (Name, DOB, Batch A/B, Course: Major/Minor/Library)             │
│  - Settings (Targets: 75% Exam, 80% Stipend, Buffer 4%, Future Marking)    │
│  - Timetable Engine (Dynamic day + batch + elective resolution)             │
│  - Attendance Math Engine (Credits, 3-Tier Status, Recovery / Skip Hours)   │
└──────────────────────────────────────┬──────────────────────────────────────┘
                                       │ Repositories & Local/Cloud Storage
                    ┌──────────────────┴──────────────────┐
                    ▼                                     ▼
┌──────────────────────────────────────┐  ┌───────────────────────────────────┐
│     Local Persistent Storage         │  │     Firebase Cloud Backend        │
│  - Encrypted / JSON Preferences      │  │  - Firebase Auth (Google Sign-In) │
│  - Instant offline availability      │  │  - Firestore DB: `/users/{uid}/`  │
│  - Export / Import backup support    │  │  - Zero-trust security rules      │
└──────────────────────────────────────┘  └───────────────────────────────────┘
```

### 4.2 Data Models & Mathematical Formulas

- **StudentProfile**:
  ```kotlin
  data class StudentProfile(
      val name: String = "Bhawishya Kumar",
      val dateOfBirth: String = "2005-10-26",
      val batch: Batch = Batch.BATCH_A,
      val courseSelection: CourseSelection = CourseSelection.MAJOR_HONOURS,
      val examTarget: Int = 75,
      val stipendTarget: Int = 80,
      val safetyBuffer: Int = 4,
      val futureMarkingEnabled: Boolean = false,
      val isDarkMode: Boolean = true,
      val starfieldAnimationEnabled: Boolean = true,
      val progressiveBlurEnabled: Boolean = true
  )
  ```
- **PeriodStatus**:
  * `PRESENT ("P")`: Conducted += weight, Attended += weight
  * `ABSENT ("A")`: Conducted += weight, Attended += 0
  * `NO_CLASS ("N")`: Conducted += 0, Attended += 0
  * `AUTO_PRESENT ("AUTO_P")`: Conducted += weight, Attended += weight (immune to bulk absent)
- **Recovery & Safe-Skip Formulas**:
  * If $\text{Percentage} < \text{Target}$: Hours to recover $x = \lceil \frac{t \cdot C - A}{1 - t} \rceil$
  * If $\text{Percentage} \ge \text{Target}$: Safe hours to skip $y = \lfloor \frac{A - t \cdot C}{t} \rfloor$
- **3-Tier Status Sign**:
  * `Completely Safe` ($\ge \text{Target} + \text{Buffer}$): Emerald Neon (`#10B981`)
  * `Safe Zone` ($\text{Target} \le \% < \text{Target} + \text{Buffer}$): Amber Gold (`#F59E0B`)
  * `Danger Zone` ($< \text{Target}$): Crimson Rose (`#F43F5E`)

---

## 5. Phased Execution Roadmap

1. **Step 1: Configuration & Platform Setup**:
   - Update `app_name` in `res/values/strings.xml` and sync `metadata.json` with descriptive platform metadata.
   - Configure unique `applicationId` (`com.aistudio.bhttattendance.uxqvzk`) in `app/build.gradle.kts`.
   - Download local Google Fonts via `font-util` (`Outfit`, `Plus Jakarta Sans`, `Space Grotesk`, `Caveat`) to `app/src/main/res/font/`.
   - Generate custom adaptive launcher icon foreground and background drawables.
2. **Step 2: Timetable, Holiday & Calculation Engine**:
   - Implement `TimetableMatrix.kt` defining exact Monday–Friday periods, batch splits (Wed/Thu/Fri 2-hr blocks), elective mapping (`HTFT501`, `MNPM501`, `Digital Library`), and break intervals.
   - Implement `AktuHolidays.kt` with official 2026 holidays.
   - Implement `AttendanceMath.kt` calculating weighted credit totals, percentage, 3-tier status, recovery hours, and safe skip capacity.
3. **Step 3: State Management & Persistence**:
   - Implement `AttendanceRepository.kt` with local JSON persistence and seed data for August 2026 (Saved: ~88.0%), September 2026 (Saved: ~85.5%), and October 2026 (Active: ~84.2%).
   - Implement `AttendanceViewModel.kt` exposing unified reactive state flow.
4. **Step 4: Design System & Shared Components**:
   - Implement dual-theme design system in `Theme.kt`, `Color.kt`, and `Type.kt`.
   - Implement `ScenicSkyToggle.kt` for animated day/night switching.
   - Implement `StarfieldCanvas.kt` for interactive 3-layer moving starfield and shooting stars in dark mode.
   - Implement `ProgressiveEdgeBlur.kt` and `FloatingBottomDock.kt`.
5. **Step 5: Views Implementation**:
   - Implement 5-screen Onboarding Flow with Mid-Month Backfill Modal (`OnboardingScreens.kt`).
   - Implement Tab 1 Home Screen (`HomeScreen.kt`) with Hero Radial Gauge, 2-page carousel, and Recovery Box.
   - Implement Tab 2 Attendance Screen (`AttendanceScreen.kt`) with date ribbon, bulk actions, and 7-period list.
   - Implement Tab 3 Reports Screen (`ReportsScreen.kt`) with monthly archive, boost strip, PDF modal, and subject table.
   - Implement Tab 4 Timetable Screen (`TimetableScreen.kt`) with 2-per-row grid, 2-hr lab spanners, and holiday strip.
   - Implement Tab 5 Settings Screen (`SettingsScreen.kt`) with profile editor, targets, backup tools, and developer footer.
6. **Step 6: Firebase Firestore & Google Sign-In Integration**:
   - Provision Firebase Firestore & Auth, generate `firebase-blueprint.json`, write and test `firestore.rules`, and wire Credential Manager Google Sign-In into the backup cards.
7. **Step 7: Verification & Testing**:
   - Compile applet with `compile_applet`, verify zero compile errors, and test edge-to-edge window insets.
