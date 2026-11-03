package com.example.ui.timetable

import com.example.data.holidays.AktuHolidays
import com.example.data.holidays.Holiday
import com.example.data.model.Batch
import com.example.data.model.CourseSelection
import com.example.data.model.PeriodSlot
import com.example.data.model.StudentProfile
import com.example.data.model.SubjectCategory
import com.example.data.model.SubjectSummary
import com.example.data.timetable.TimetableMatrix
import java.time.LocalDate
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Maps your real PeriodSlot and TimetableMatrix data into the Timetable UI models.
 * Dynamically resolves real BHTT 5th Sem schedules, live attendance percentages,
 * faculty, venues, water & lunch breaks, workload analytics, and official AKTU holidays.
 */
data class SlotExtras(
    val faculty: String? = null,
    val supportingStaff: String? = null,
    val venue: String? = null, // Only populated for the 3 subjects with both Theory & Lab
    val attendancePercent: Int? = null
)

/**
 * Resolves exact IIHT Varanasi BHTT 5th Sem (AY 2026-27) Faculty, Supporting Staff,
 * and Lab Name strictly for the 3 subjects that have both Theory & Lab (WFM, CPT-II, Testing).
 */
fun resolveBhttSlotExtras(
    slot: PeriodSlot,
    attendancePercent: Int? = null
): SlotExtras {
    val normCode = slot.subject.code.uppercase().replace(" ", "").replace("-", "")
    val normName = slot.subject.fullName.lowercase()

    return when {
        // 1. Woven Fabric Manufacture (Theory + Lab -> Show Lab Name)
        normCode == "BHT501" -> SlotExtras(
            faculty = "Shri Sunil Kr Pandey (SKP)",
            supportingStaff = null,
            venue = "Woven Fabric Manufacture Lab",
            attendancePercent = attendancePercent
        )
        normCode == "BHT551" -> SlotExtras(
            faculty = "Shri Sunil Kr Pandey (SKP)",
            supportingStaff = "Shri Karthikeyan D (DK), Shri Sachin Singh (ShS)",
            venue = "Woven Fabric Manufacture Lab",
            attendancePercent = attendancePercent
        )

        // 2. Chemical Processing of Textiles - II (Theory + Lab -> Show Lab Name)
        normCode == "BHT502" -> SlotExtras(
            faculty = "Dr. Gyana Ranjan Behera (GRB)",
            supportingStaff = null,
            venue = "Chemical Processing of Textiles (CPT-II) Lab",
            attendancePercent = attendancePercent
        )
        normCode == "BHT552" -> SlotExtras(
            faculty = "Dr. Vinay Kr Chauhan (VKC)",
            supportingStaff = "Shri Madan Kumar (MKr), Shri Arun Kr Bharti (AKB)",
            venue = "Chemical Processing of Textiles (CPT-II) Lab",
            attendancePercent = attendancePercent
        )

        // 3. Quality Evaluation of Fibers and Yarns / Textile Testing (Theory + Lab -> Show Lab Name)
        normCode == "BHT503" -> SlotExtras(
            faculty = "Ms. Kanchan Doheray (KD)",
            supportingStaff = null,
            venue = "Textile Testing (Fibre & Yarn Quality Evaluation) Lab",
            attendancePercent = attendancePercent
        )
        normCode == "BHT553" -> SlotExtras(
            faculty = "Ms. Kanchan Doheray (KD)",
            supportingStaff = "Shri M. Karthick (MK), Shri Upendra Kumar (UY)",
            venue = "Textile Testing (Fibre & Yarn Quality Evaluation) Lab",
            attendancePercent = attendancePercent
        )

        // 4. Theory-Only & Activity Subjects (venue = null so Lab Name is NOT shown)
        normCode == "BHT051" -> SlotExtras(
            faculty = "Shri Ajit Kr Sahoo (AJT)",
            venue = null,
            attendancePercent = attendancePercent
        )
        normCode == "BHT055" -> SlotExtras(
            faculty = "Dr. Ashutosh Pandey (AP)",
            venue = null,
            attendancePercent = attendancePercent
        )
        normCode == "BNC501" -> SlotExtras(
            faculty = "Shri Shahrukh Khan (SRK)",
            venue = null,
            attendancePercent = attendancePercent
        )
        normCode == "HTFT501" || "clothing comfort" in normName -> SlotExtras(
            faculty = "Shri Mahesh Pratap Dubey (MPD)",
            venue = null,
            attendancePercent = attendancePercent
        )
        normCode == "MNPM501" || "production planning" in normName -> SlotExtras(
            faculty = "Dr. Manorama Chaurasia (MNC)",
            venue = null,
            attendancePercent = attendancePercent
        )
        "seminar" in normCode.lowercase() || "seminar" in normName -> SlotExtras(
            faculty = "Shri Shahrukh Khan (Class Advisor) • Smt. Shivani Singh (Mentor)",
            venue = null,
            attendancePercent = attendancePercent ?: 100
        )
        "library" in normCode.lowercase() || "library" in normName -> SlotExtras(
            faculty = "Central / Digital Library Faculty",
            venue = null,
            attendancePercent = attendancePercent ?: 100
        )
        else -> SlotExtras(
            faculty = null,
            venue = null,
            attendancePercent = attendancePercent
        )
    }
}

/**
 * Maps a single PeriodSlot into a TimelineItemUi.PeriodItem.
 */
fun PeriodSlot.toPeriodItem(extras: SlotExtras = SlotExtras()): TimelineItemUi.PeriodItem {
    val categoryName = subject.category.name.lowercase()
    val isLab = isTwoHourBlock || "lab" in categoryName || "practical" in categoryName
    val isSeminar = subject.code == "SEMINAR" || "seminar" in categoryName
    val type = when {
        isLab && !isSeminar -> SessionType.Lab
        isSeminar -> SessionType.Seminar
        else -> SessionType.Theory
    }

    val sessionLabel = when {
        isSeminar -> "Departmental Seminar (2 Hours • Auto-Present)"
        isLab -> "Practical Lab (2 Hours • 2x Credit)"
        subject.isAutoPresent -> "Institute Library (1 Hour • Auto-Present)"
        isTutorial -> "Tutorial (1 Hour)"
        else -> "Theory Lecture (1 Hour)"
    }

    val highlightLabel = if (isTwoHourBlock) "2 HRS · 2X CREDIT" else null

    return TimelineItemUi.PeriodItem(
        key = "p$periodNumber-${subject.code}",
        number = periodNumber,
        timeLabel = timeRange,
        subjectName = subject.fullName,
        subjectCode = subject.code,
        type = type,
        icon = iconFor(subject.code, subject.fullName),
        highlightLabel = highlightLabel,
        fullName = subject.fullName,
        sessionLabel = sessionLabel,
        faculty = extras.faculty,
        supportingStaff = extras.supportingStaff,
        venue = extras.venue,
        attendancePercent = extras.attendancePercent
    )
}

/**
 * Builds one day's schedule with exact break insertions matching BHTT 5th Sem:
 * - Water Break 1 (11:10 – 11:20 AM) after Period 2
 * - Lunch Break (01:00 – 02:00 PM) after Period 4 (Lecture or 2-Hour Lab block)
 * - Water Break 2 (03:40 – 03:50 PM) after Period 6 ONLY when Period 6 is a 1-hour slot
 */
fun buildDaySchedule(
    shortLabel: String,
    fullTitle: String,
    slots: List<PeriodSlot>,
    extrasFor: (PeriodSlot) -> SlotExtras = { SlotExtras() }
): DayScheduleUi {
    val sorted = slots.sortedBy { it.periodNumber }
    val items = buildList<TimelineItemUi> {
        sorted.forEach { slot ->
            add(slot.toPeriodItem(extrasFor(slot)))
            val lastCoveredPeriod = if (slot.isTwoHourBlock) slot.periodNumber + 1 else slot.periodNumber

            // Morning Water Break (11:10 – 11:20 AM): immediately after Period 2
            if (lastCoveredPeriod == 2) {
                add(TimelineItemUi.BreakItem("water1-$shortLabel", BreakKind.Water, "11:10 – 11:20 AM"))
            }

            // Lunch Break (01:00 – 02:00 PM): immediately after Period 4
            if (lastCoveredPeriod == 4) {
                add(TimelineItemUi.BreakItem("lunch-$shortLabel", BreakKind.Lunch, "01:00 – 02:00 PM"))
            }

            // Afternoon Water Break (03:40 – 03:50 PM): immediately after Period 6 ONLY when Period 6 is a 1-hour slot
            if (!slot.isTwoHourBlock && lastCoveredPeriod == 6) {
                add(TimelineItemUi.BreakItem("water2-$shortLabel", BreakKind.Water, "03:40 – 03:50 PM"))
            }
        }
    }
    val periodCount = sorted.fold(0) { acc, s -> acc + (if (s.isTwoHourBlock) 2 else 1) }
    val practical = sorted.count { it.isTwoHourBlock }
    val freePeriods = sorted.count { it.subject.category == SubjectCategory.LIBRARY }
    return DayScheduleUi(
        shortLabel = shortLabel,
        fullTitle = fullTitle,
        periodsLabel = "$periodCount Periods",
        practicalLabel = "$practical Practical",
        freeLabel = "$freePeriods Free",
        items = items
    )
}

/**
 * Maps every BHTT 5th Sem Textile & Handloom subject to a distinct, appropriate icon.
 */
fun iconFor(code: String, name: String): SubjectIcon {
    val c = code.uppercase().replace(" ", "")
    val n = name.lowercase()
    return when {
        c == "BHT501" || "woven fabric" in n -> SubjectIcon.Structure
        c == "BHT502" || "chemical processing" in n || "cpt" in n -> SubjectIcon.Physics
        c == "BHT503" || "quality eval" in n || "fiber" in n -> SubjectIcon.Graphics
        c == "BHT051" || "garment" in n -> SubjectIcon.Logic
        c == "BHT055" || "knitting" in n || "nonwoven" in n -> SubjectIcon.Network
        c == "BNC501" || "constitution" in n -> SubjectIcon.Web
        c == "HTFT501" || c == "MNPM501" || "clothing comfort" in n || "production planning" in n || "honours" in n || "minor" in n -> SubjectIcon.Brain
        c == "BHT551" || c == "BHT552" || c == "BHT553" || "lab" in n || "practical" in n -> SubjectIcon.Database
        c == "SEMINAR" || "seminar" in n -> SubjectIcon.Seminar
        c == "LIBRARY" || c == "DIG_LIB" || "library" in n -> SubjectIcon.Generic
        else -> SubjectIcon.Generic
    }
}


/**
 * Computes weekly workload summary across all 5 weekdays (35 credit hours).
 */
fun buildWorkloadUi(
    batch: Batch,
    courseSelection: CourseSelection
): WorkloadUi {
    val allDaysSlots = (1..5).map { day ->
        TimetableMatrix.getScheduleForDay(day, batch, courseSelection)
    }
    val allSlots = allDaysSlots.flatten()

    val totalPeriods = 35 // 7 per day × 5 days
    val practicalSessions = allSlots.count { it.isTwoHourBlock } // 3 sessions
    val practicalHours = practicalSessions * 2 // 6 hours
    val freePeriods = allSlots.count { it.subject.category == SubjectCategory.LIBRARY }

    var coreTheory = 0
    var practicalLabs = 0
    var electiveHours = 0
    var libAndSeminar = 0

    allSlots.forEach { slot ->
        val credits = if (slot.isTwoHourBlock) 2 else 1
        when {
            slot.subject.code == "SEMINAR" || slot.subject.category == SubjectCategory.LIBRARY -> {
                libAndSeminar += credits
            }
            slot.subject.category == SubjectCategory.MINOR -> {
                electiveHours += credits
            }
            slot.isTwoHourBlock -> {
                practicalLabs += credits
            }
            else -> {
                coreTheory += credits
            }
        }
    }

    val distribution = listOf(
        DistributionSlice("Core Theory", coreTheory),
        DistributionSlice("Practical Labs", practicalLabs),
        DistributionSlice("Honours / Minor", electiveHours),
        DistributionSlice("Library & Seminar", libAndSeminar)
    )

    return WorkloadUi(
        totalPeriods = totalPeriods,
        practicalSessions = practicalSessions,
        practicalHours = practicalHours,
        freePeriods = freePeriods,
        distribution = distribution,
        distributionCaption = "35 Periods / Week"
    )
}

/**
 * Resolves upcoming AKTU 2026 holidays relative to today's date.
 */
fun buildUpcomingHolidays(today: LocalDate = LocalDate.now()): Pair<HolidayUi?, List<HolidayUi>> {
    val todayStr = today.toString()
    val upcoming = AktuHolidays.OFFICIAL_HOLIDAYS_2026.filter { it.date >= todayStr }
    if (upcoming.isEmpty()) return null to emptyList()

    val featured = upcoming.firstOrNull()?.let { h ->
        val hDate = runCatching { LocalDate.parse(h.date) }.getOrNull()
        val daysDiff = if (hDate != null) ChronoUnit.DAYS.between(today, hDate) else null
        val countdown = when (daysDiff) {
            0L -> "Today"
            1L -> "Tomorrow"
            null -> null
            else -> "In $daysDiff days"
        }
        val dateLabel = hDate?.let {
            "${it.dayOfMonth} ${it.month.getDisplayName(TextStyle.FULL, Locale.getDefault())} ${it.year}"
        } ?: h.date
        HolidayUi(name = h.name, dateLabel = dateLabel, countdownLabel = countdown)
    }

    val more = upcoming.drop(1).take(4).map { h ->
        val hDate = runCatching { LocalDate.parse(h.date) }.getOrNull()
        val dateLabel = hDate?.let {
            "${it.dayOfMonth} ${it.month.getDisplayName(TextStyle.SHORT, Locale.getDefault())} ${it.year}"
        } ?: h.date
        HolidayUi(name = h.name, dateLabel = dateLabel, countdownLabel = null)
    }

    return featured to more
}

object TimetableMapper {
    /**
     * Builds the complete TimetableUiState from real user profile and live attendance data.
     */
    fun buildTimetableUiState(
        profile: StudentProfile,
        subjectSummaries: List<SubjectSummary>,
        selectedDayIndex: Int,
        today: LocalDate = LocalDate.now()
    ): TimetableUiState {
        val batch = profile.batchEnum
        val courseSelection = profile.courseSelectionEnum
        val summaryMap = subjectSummaries.associateBy { it.subject.code }

        val days = listOf(
            1 to ("MON" to "Monday"),
            2 to ("TUE" to "Tuesday"),
            3 to ("WED" to "Wednesday"),
            4 to ("THU" to "Thursday"),
            5 to ("FRI" to "Friday")
        ).map { (dayNumber, labelPair) ->
            val (shortLabel, fullTitle) = labelPair
            val slots = TimetableMatrix.getScheduleForDay(dayNumber, batch, courseSelection)
            buildDaySchedule(
                shortLabel = shortLabel,
                fullTitle = fullTitle,
                slots = slots,
                extrasFor = { slot ->
                    val summary = summaryMap[slot.subject.code]
                    val attendancePct = when {
                        slot.subject.isAutoPresent -> 100
                        summary != null && summary.conductedCredits > 0 -> summary.percentage.roundToInt()
                        else -> null
                    }
                    resolveBhttSlotExtras(slot, attendancePct)
                }
            )
        }

        val workload = buildWorkloadUi(batch, courseSelection)
        val (featuredHoliday, moreHolidays) = buildUpcomingHolidays(today)

        return TimetableUiState(
            title = "Weekly Timetable",
            subtitle = "BHTT 5th Sem • ${batch.displayName} • ${courseSelection.shortTitle}",
            profileLine = "BHTT • 5th Sem • ${batch.displayName}",
            profileSub = "${courseSelection.shortTitle} • ${courseSelection.subtitle}",
            semesterBadge = "Active Sem",
            waterBreakTimes = listOf("11:10 – 11:20 AM", "03:40 – 03:50 PM"),
            lunchTime = "01:00 – 02:00 PM",
            lunchDuration = "Duration 60 min",
            days = days,
            selectedDayIndex = selectedDayIndex.coerceIn(0, 4),
            workload = workload,
            featuredHoliday = featuredHoliday,
            moreHolidays = moreHolidays
        )
    }
}
