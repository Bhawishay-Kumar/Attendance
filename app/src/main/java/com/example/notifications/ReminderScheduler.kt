package com.example.notifications

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.example.data.holidays.AktuHolidays
import com.example.data.model.StudentProfile
import com.example.data.timetable.TimetableMatrix
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/**
 * Schedules the three Settings notifications with AlarmManager (no extra dependencies, no exact-alarm permission).
 * Alarms are inexact "allow while idle" alarms, so Android may deliver them a few minutes late in Doze.
 * Every call replaces the previous alarm (same PendingIntent), so duplicates cannot accumulate.
 */
object ReminderScheduler {
    const val CHANNEL_ID = "attendance_reminders"
    const val ACTION_DAILY = "com.example.action.REMINDER_DAILY"
    const val ACTION_WEEKLY = "com.example.action.REMINDER_WEEKLY"
    const val ACTION_CLASS = "com.example.action.REMINDER_CLASS"
    const val EXTRA_TITLE = "extra_title"
    const val EXTRA_TEXT = "extra_text"

    private const val RC_DAILY = 7101
    private const val RC_WEEKLY = 7102
    private const val RC_CLASS = 7103
    const val CLASS_LEAD_MINUTES = 10

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= 26) {
            val nm = context.getSystemService(NotificationManager::class.java)
            if (nm.getNotificationChannel(CHANNEL_ID) == null) {
                nm.createNotificationChannel(
                    NotificationChannel(CHANNEL_ID, "Attendance reminders", NotificationManager.IMPORTANCE_DEFAULT).apply {
                        description = "Daily reminder, weekly report and class alerts"
                    }
                )
            }
        }
    }

    fun rescheduleAll(
        context: Context,
        profile: StudentProfile,
        isHoliday: (String) -> Boolean = { AktuHolidays.isHoliday(it) }
    ) {
        val app = context.applicationContext
        ensureChannel(app)
        val am = app.getSystemService(Context.ALARM_SERVICE) as AlarmManager

        // Daily
        if (profile.dailyReminderEnabled) {
            set(app, am, ACTION_DAILY, RC_DAILY, nextDaily(profile.dailyReminderTime, System.currentTimeMillis()), null, null)
        } else cancel(app, am, ACTION_DAILY, RC_DAILY)

        // Weekly (Sunday 6:00 PM)
        if (profile.weeklyReminderEnabled) {
            set(app, am, ACTION_WEEKLY, RC_WEEKLY, nextWeekly(System.currentTimeMillis()), null, null)
        } else cancel(app, am, ACTION_WEEKLY, RC_WEEKLY)

        // Class alerts (10 minutes before the next lecture that is not on a holiday)
        val next = if (profile.classScheduleAlertsEnabled) nextClassAlert(profile, System.currentTimeMillis(), isHoliday) else null
        if (next != null) {
            set(app, am, ACTION_CLASS, RC_CLASS, next.triggerAtMs, next.title, next.text)
        } else cancel(app, am, ACTION_CLASS, RC_CLASS)
    }

    class ClassAlert(val triggerAtMs: Long, val title: String, val text: String)

    fun nextDaily(hhmm: String, now: Long): Long {
        val parts = hhmm.split(":")
        val h = parts.getOrNull(0)?.toIntOrNull()?.coerceIn(0, 23) ?: 16
        val m = parts.getOrNull(1)?.toIntOrNull()?.coerceIn(0, 59) ?: 45
        val cal = Calendar.getInstance().apply {
            timeInMillis = now
            set(Calendar.HOUR_OF_DAY, h); set(Calendar.MINUTE, m); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }
        if (cal.timeInMillis <= now) cal.add(Calendar.DAY_OF_YEAR, 1)
        return cal.timeInMillis
    }

    fun nextWeekly(now: Long): Long {
        val cal = Calendar.getInstance().apply {
            timeInMillis = now
            set(Calendar.DAY_OF_WEEK, Calendar.SUNDAY)
            set(Calendar.HOUR_OF_DAY, 18); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }
        while (cal.timeInMillis <= now) cal.add(Calendar.WEEK_OF_YEAR, 1)
        return cal.timeInMillis
    }

    fun nextClassAlert(profile: StudentProfile, now: Long, isHoliday: (String) -> Boolean): ClassAlert? {
        val dateFmt = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val timeFmt = SimpleDateFormat("hh:mm a", Locale.US)
        for (offset in 0..8) {
            val day = Calendar.getInstance().apply { timeInMillis = now; add(Calendar.DAY_OF_YEAR, offset) }
            val dayNum = when (day.get(Calendar.DAY_OF_WEEK)) {
                Calendar.MONDAY -> 1; Calendar.TUESDAY -> 2; Calendar.WEDNESDAY -> 3
                Calendar.THURSDAY -> 4; Calendar.FRIDAY -> 5; else -> 0
            }
            if (dayNum == 0) continue
            if (isHoliday(dateFmt.format(day.time))) continue
            val slots = TimetableMatrix.getScheduleForDay(dayNum, profile.batchEnum, profile.courseSelectionEnum)
            var best: ClassAlert? = null
            for (slot in slots) {
                val start = runCatching { timeFmt.parse(slot.startTime) }.getOrNull() ?: continue
                val t = Calendar.getInstance().apply { time = start }
                val trigger = Calendar.getInstance().apply {
                    timeInMillis = day.timeInMillis
                    set(Calendar.HOUR_OF_DAY, t.get(Calendar.HOUR_OF_DAY))
                    set(Calendar.MINUTE, t.get(Calendar.MINUTE))
                    set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
                    add(Calendar.MINUTE, -CLASS_LEAD_MINUTES)
                }.timeInMillis
                if (trigger > now && (best == null || trigger < best.triggerAtMs)) {
                    best = ClassAlert(
                        trigger,
                        "Class in $CLASS_LEAD_MINUTES minutes",
                        "${slot.subject.shortName} • ${slot.periodLabel} at ${slot.startTime}"
                    )
                }
            }
            if (best != null) return best
        }
        return null
    }

    private fun pending(context: Context, action: String, rc: Int, title: String?, text: String?, create: Boolean): PendingIntent? {
        val intent = Intent(context, ReminderReceiver::class.java).setAction(action).apply {
            if (title != null) putExtra(EXTRA_TITLE, title)
            if (text != null) putExtra(EXTRA_TEXT, text)
        }
        val flags = PendingIntent.FLAG_IMMUTABLE or (if (create) PendingIntent.FLAG_UPDATE_CURRENT else PendingIntent.FLAG_NO_CREATE)
        return PendingIntent.getBroadcast(context, rc, intent, flags)
    }

    private fun set(context: Context, am: AlarmManager, action: String, rc: Int, at: Long, title: String?, text: String?) {
        val pi = pending(context, action, rc, title, text, true) ?: return
        runCatching { am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi) }
    }

    private fun cancel(context: Context, am: AlarmManager, action: String, rc: Int) {
        pending(context, action, rc, null, null, false)?.let { am.cancel(it); it.cancel() }
    }
}
