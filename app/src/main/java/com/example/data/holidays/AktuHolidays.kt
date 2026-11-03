package com.example.data.holidays

data class Holiday(
    val date: String, // "YYYY-MM-DD"
    val name: String,
    val dayOfWeek: String,
    val isActiveSemesterHoliday: Boolean = true
)

object AktuHolidays {

    val OFFICIAL_HOLIDAYS_2026 = listOf(
        Holiday("2026-01-26", "Republic Day", "Monday", false),
        Holiday("2026-03-02", "Holika Dahan", "Monday", false),
        Holiday("2026-03-04", "Holi", "Wednesday", false),
        Holiday("2026-03-26", "Ram Navami", "Thursday", false),
        Holiday("2026-03-31", "Mahavir Jayanti", "Tuesday", false),
        Holiday("2026-04-03", "Good Friday", "Friday", false),
        Holiday("2026-04-14", "Dr. B.R. Ambedkar Jayanti", "Tuesday", false),
        Holiday("2026-05-01", "Buddha Purnima", "Friday", false),
        Holiday("2026-05-27", "Eid-ul-Adha / Bakrid", "Wednesday", false),
        Holiday("2026-06-26", "Muharram", "Thursday", false),
        Holiday("2026-08-15", "Independence Day", "Saturday", false),
        // Active 5th Semester Holidays (Aug - Dec 2026)
        Holiday("2026-08-26", "Eid-e-Milad / Barawafat", "Wednesday", true),
        Holiday("2026-08-28", "Raksha Bandhan", "Friday", true),
        Holiday("2026-09-04", "Janmashtami", "Friday", true),
        Holiday("2026-10-02", "Mahatma Gandhi Jayanti", "Friday", true),
        Holiday("2026-10-20", "Dussehra / Vijayadashami", "Tuesday", true),
        Holiday("2026-11-09", "Govardhan Puja", "Monday", true),
        Holiday("2026-11-11", "Bhai Dooj / Chitragupta Jayanti", "Wednesday", true),
        Holiday("2026-11-24", "Guru Nanak Jayanti / Kartik Purnima", "Tuesday", true),
        Holiday("2026-12-25", "Christmas Day", "Friday", true)
    )

    private val holidayMap = OFFICIAL_HOLIDAYS_2026.associateBy { it.date }

    /** Years that have an explicit official list above; other years fall back to fixed-date holidays only. */
    private val explicitYears: Set<String> = OFFICIAL_HOLIDAYS_2026.map { it.date.take(4) }.toSet()

    /**
     * Holidays whose calendar date never changes (MM-DD). Used only for years without an explicit
     * official list, so 2027+ is not silently holiday-free. Festival dates that move every year
     * (Holi, Diwali, Eid...) cannot be known here: mark those manually, or extend the list above.
     */
    private val fixedNationalHolidays: Map<String, String> = mapOf(
        "01-26" to "Republic Day",
        "04-14" to "Dr. B.R. Ambedkar Jayanti",
        "08-15" to "Independence Day",
        "10-02" to "Mahatma Gandhi Jayanti",
        "12-25" to "Christmas Day"
    )

    fun getHoliday(date: String): Holiday? {
        holidayMap[date]?.let { return it }
        if (date.length != 10 || date.take(4) in explicitYears) return null
        val name = fixedNationalHolidays[date.substring(5)] ?: return null
        val day = runCatching {
            java.time.LocalDate.parse(date).dayOfWeek
                .getDisplayName(java.time.format.TextStyle.FULL, java.util.Locale.ENGLISH)
        }.getOrNull() ?: return null
        return Holiday(date, name, day, true)
    }
    fun getHoliday(date: java.time.LocalDate): Holiday? = getHoliday(date.toString())

    fun isHoliday(date: String): Boolean = getHoliday(date) != null
    fun isHoliday(date: java.time.LocalDate): Boolean = isHoliday(date.toString())

    fun getUpcomingHolidays(
        fromDate: String = java.time.LocalDate.now(java.time.ZoneId.of("Asia/Kolkata")).toString(),
        limit: Int = 5
    ): List<Holiday> {
        return OFFICIAL_HOLIDAYS_2026
            .filter { it.date >= fromDate }
            .take(limit)
    }
}
