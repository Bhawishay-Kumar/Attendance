package com.example.data.timetable

import com.example.data.model.Batch
import com.example.data.model.CourseSelection
import com.example.data.model.PeriodSlot
import com.example.data.model.Subject
import com.example.data.model.SubjectCategory

object TimetableMatrix {

    // --- Subject Definitions ---
    val WOVEN_FABRIC = Subject(
        code = "BHT501",
        shortName = "Woven Fabric",
        fullName = "Woven Fabric Manufacture",
        creditHours = 1,
        category = SubjectCategory.MAJOR,
        isAutoPresent = false,
        iconKey = "grid"
    )

    val CPT_II = Subject(
        code = "BHT502",
        shortName = "CPT-II",
        fullName = "Chemical Processing of Textiles - II",
        creditHours = 1,
        category = SubjectCategory.MAJOR,
        isAutoPresent = false,
        iconKey = "flask"
    )

    val QUALITY_EVAL = Subject(
        code = "BHT503",
        shortName = "Quality Eval",
        fullName = "Quality Evaluation of Fibers and Yarns",
        creditHours = 1,
        category = SubjectCategory.MAJOR,
        isAutoPresent = false,
        iconKey = "microscope"
    )

    val GARMENT_TECH = Subject(
        code = "BHT051",
        shortName = "Garment Tech",
        fullName = "Garment Manufacturing Technology",
        creditHours = 1,
        category = SubjectCategory.MAJOR,
        isAutoPresent = false,
        iconKey = "scissors"
    )

    val KNITTING_NW = Subject(
        code = "BHT055",
        shortName = "Knitting & NW",
        fullName = "Knitting and Nonwoven Technology",
        creditHours = 1,
        category = SubjectCategory.MAJOR,
        isAutoPresent = false,
        iconKey = "workflow"
    )

    val CONSTITUTION = Subject(
        code = "BNC501",
        shortName = "Constitution",
        fullName = "Constitution of India",
        creditHours = 1,
        category = SubjectCategory.MAJOR,
        isAutoPresent = false,
        iconKey = "scale"
    )

    val WOVEN_LAB = Subject(
        code = "BHT551",
        shortName = "Woven Lab",
        fullName = "Woven Fabric Manufacture Lab",
        creditHours = 2,
        category = SubjectCategory.MAJOR,
        isAutoPresent = false,
        iconKey = "sliders"
    )

    val CPT_II_LAB = Subject(
        code = "BHT552",
        shortName = "CPT-II Lab",
        fullName = "Chemical Processing of Textiles - II Lab",
        creditHours = 2,
        category = SubjectCategory.MAJOR,
        isAutoPresent = false,
        iconKey = "testtube"
    )

    val QUALITY_LAB = Subject(
        code = "BHT553",
        shortName = "Quality Lab",
        fullName = "Fibre & Yarn Quality Evaluation Lab",
        creditHours = 2,
        category = SubjectCategory.MAJOR,
        isAutoPresent = false,
        iconKey = "activity"
    )

    val LIBRARY_SESSION = Subject(
        code = "LIBRARY",
        shortName = "Library",
        fullName = "Institute Library Session",
        creditHours = 1,
        category = SubjectCategory.LIBRARY,
        isAutoPresent = true,
        iconKey = "book"
    )

    val SEMINAR_SESSION = Subject(
        code = "SEMINAR",
        shortName = "Seminar",
        fullName = "Department Seminar",
        creditHours = 2,
        category = SubjectCategory.LIBRARY,
        isAutoPresent = true,
        iconKey = "presentation"
    )

    fun resolveElective(selection: CourseSelection): Subject {
        return when (selection) {
            CourseSelection.MAJOR_HONOURS -> Subject(
                code = "HTFT501",
                shortName = "Honours (HTFT 501)",
                fullName = "Clothing Comfort",
                creditHours = 1,
                category = SubjectCategory.MAJOR, // Fixed Bug #7: HTFT501 is Major
                isAutoPresent = false,
                iconKey = "award"
            )
            CourseSelection.MINOR_ELECTIVE -> Subject(
                code = "MNPM501",
                shortName = "Minor (MNPM 501)",
                fullName = "Production Planning & Control",
                creditHours = 1,
                category = SubjectCategory.MINOR,
                isAutoPresent = false,
                iconKey = "sparkles"
            )
            CourseSelection.DIGITAL_LIBRARY -> Subject(
                code = "DIG_LIB",
                shortName = "Digital Library",
                fullName = "Digital Library Session",
                creditHours = 1,
                category = SubjectCategory.LIBRARY,
                isAutoPresent = true,
                iconKey = "monitorplay"
            )
        }
    }

    fun getAllActiveSubjects(selection: CourseSelection): List<Subject> {
        return listOf(
            WOVEN_FABRIC,
            CPT_II,
            QUALITY_EVAL,
            GARMENT_TECH,
            KNITTING_NW,
            CONSTITUTION,
            WOVEN_LAB,
            CPT_II_LAB,
            QUALITY_LAB,
            resolveElective(selection),
            LIBRARY_SESSION,
            SEMINAR_SESSION
        )
    }

    fun getScheduleForDay(
        dayOfWeek: java.time.DayOfWeek,
        batch: Batch,
        courseSelection: CourseSelection
    ): List<PeriodSlot> = getScheduleForDay(dayOfWeek.value, batch, courseSelection)

    /**
     * Resolves the periods for a given day of week (1 = Monday, ..., 5 = Friday).
     */
    fun getScheduleForDay(
        dayOfWeek: Int,
        batch: Batch,
        courseSelection: CourseSelection
    ): List<PeriodSlot> {
        val elective = resolveElective(courseSelection)

        return when (dayOfWeek) {
            1 -> listOf( // Monday
                PeriodSlot(1, "Period I", "09:30 AM", "10:20 AM", "09:30 AM – 10:20 AM", QUALITY_EVAL),
                PeriodSlot(2, "Period II", "10:20 AM", "11:10 AM", "10:20 AM – 11:10 AM", KNITTING_NW),
                PeriodSlot(3, "Period III", "11:20 AM", "12:10 PM", "11:20 AM – 12:10 PM", GARMENT_TECH),
                PeriodSlot(4, "Period IV", "12:10 PM", "01:00 PM", "12:10 PM – 01:00 PM", WOVEN_FABRIC),
                PeriodSlot(5, "Period V", "02:00 PM", "02:50 PM", "02:00 PM – 02:50 PM", elective),
                PeriodSlot(6, "Period VI", "02:50 PM", "03:40 PM", "02:50 PM – 03:40 PM", CPT_II),
                PeriodSlot(7, "Period VII", "03:50 PM", "04:40 PM", "03:50 PM – 04:40 PM", LIBRARY_SESSION)
            )
            2 -> listOf( // Tuesday
                PeriodSlot(1, "Period I", "09:30 AM", "10:20 AM", "09:30 AM – 10:20 AM", GARMENT_TECH),
                PeriodSlot(2, "Period II", "10:20 AM", "11:10 AM", "10:20 AM – 11:10 AM", LIBRARY_SESSION),
                PeriodSlot(3, "Period III", "11:20 AM", "12:10 PM", "11:20 AM – 12:10 PM", elective),
                PeriodSlot(4, "Period IV", "12:10 PM", "01:00 PM", "12:10 PM – 01:00 PM", CPT_II),
                PeriodSlot(5, "Period V", "02:00 PM", "02:50 PM", "02:00 PM – 02:50 PM", KNITTING_NW),
                PeriodSlot(6, "Period VI", "02:50 PM", "03:40 PM", "02:50 PM – 03:40 PM", LIBRARY_SESSION),
                PeriodSlot(7, "Period VII", "03:50 PM", "04:40 PM", "03:50 PM – 04:40 PM", QUALITY_EVAL)
            )
            3 -> listOf( // Wednesday
                PeriodSlot(1, "Period I", "09:30 AM", "10:20 AM", "09:30 AM – 10:20 AM", CONSTITUTION),
                PeriodSlot(2, "Period II", "10:20 AM", "11:10 AM", "10:20 AM – 11:10 AM", QUALITY_EVAL),
                PeriodSlot(3, "Period III", "11:20 AM", "12:10 PM", "11:20 AM – 12:10 PM", WOVEN_FABRIC),
                PeriodSlot(4, "Period IV", "12:10 PM", "01:00 PM", "12:10 PM – 01:00 PM", CPT_II),
                PeriodSlot(5, "Period V", "02:00 PM", "02:50 PM", "02:00 PM – 02:50 PM", GARMENT_TECH),
                PeriodSlot(
                    periodNumber = 6,
                    periodLabel = "Period VI & VII",
                    startTime = "02:50 PM",
                    endTime = "04:40 PM",
                    timeRange = "02:50 PM – 04:40 PM",
                    subject = if (batch == Batch.BATCH_A) QUALITY_LAB else SEMINAR_SESSION,
                    isTwoHourBlock = true
                )
            )
            4 -> listOf( // Thursday
                PeriodSlot(1, "Period I", "09:30 AM", "10:20 AM", "09:30 AM – 10:20 AM", WOVEN_FABRIC),
                PeriodSlot(2, "Period II", "10:20 AM", "11:10 AM", "10:20 AM – 11:10 AM", elective),
                PeriodSlot(
                    periodNumber = 3,
                    periodLabel = "Period III & IV",
                    startTime = "11:20 AM",
                    endTime = "01:00 PM",
                    timeRange = "11:20 AM – 01:00 PM",
                    subject = if (batch == Batch.BATCH_A) WOVEN_LAB else CPT_II_LAB,
                    isTwoHourBlock = true
                ),
                PeriodSlot(5, "Period V", "02:00 PM", "02:50 PM", "02:00 PM – 02:50 PM", CPT_II, isTutorial = true),
                PeriodSlot(
                    periodNumber = 6,
                    periodLabel = "Period VI & VII",
                    startTime = "02:50 PM",
                    endTime = "04:40 PM",
                    timeRange = "02:50 PM – 04:40 PM",
                    subject = if (batch == Batch.BATCH_A) SEMINAR_SESSION else QUALITY_LAB,
                    isTwoHourBlock = true
                )
            )
            5 -> listOf( // Friday
                PeriodSlot(1, "Period I", "09:30 AM", "10:20 AM", "09:30 AM – 10:20 AM", CONSTITUTION),
                PeriodSlot(2, "Period II", "10:20 AM", "11:10 AM", "10:20 AM – 11:10 AM", elective, isTutorial = true),
                PeriodSlot(
                    periodNumber = 3,
                    periodLabel = "Period III & IV",
                    startTime = "11:20 AM",
                    endTime = "01:00 PM",
                    timeRange = "11:20 AM – 01:00 PM",
                    subject = if (batch == Batch.BATCH_A) CPT_II_LAB else WOVEN_LAB,
                    isTwoHourBlock = true
                ),
                PeriodSlot(5, "Period V", "02:00 PM", "02:50 PM", "02:00 PM – 02:50 PM", WOVEN_FABRIC, isTutorial = true),
                PeriodSlot(6, "Period VI", "02:50 PM", "03:40 PM", "02:50 PM – 03:40 PM", QUALITY_EVAL, isTutorial = true),
                PeriodSlot(7, "Period VII", "03:50 PM", "04:40 PM", "03:50 PM – 04:40 PM", KNITTING_NW)
            )
            else -> emptyList()
        }
    }
}
