package com.example.data.milestone

import android.Manifest
import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.data.model.Milestone
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Schedules milestone reminders with AlarmManager (inexact, so no exact-alarm permission is needed).
 * Every alarm is identified by a unique data URI (milestone id + days-before), so re-scheduling replaces
 * rather than duplicates, and cancelling an edited/deleted/completed milestone is exact.
 * "Days before = 0" is a silent roll-over alarm that arms the next occurrence of recurring milestones.
 */
class MilestoneReminderScheduler(context: Context) {
    private val app = context.applicationContext
    private val alarmManager: AlarmManager? = app.getSystemService(Context.ALARM_SERVICE) as? AlarmManager

    /** Cancel + re-arm alarms for the given milestones (deleted/completed ones end up cancelled). */
    fun apply(
        items: Collection<Milestone>,
        today: LocalDate = LocalDate.now(),
        zone: ZoneId = ZoneId.systemDefault(),
        now: Instant = Instant.now()
    ) {
        items.forEach { m ->
            cancel(m.id)
            schedule(m, today, zone, now)
        }
    }

    fun schedule(
        m: Milestone,
        today: LocalDate = LocalDate.now(),
        zone: ZoneId = ZoneId.systemDefault(),
        now: Instant = Instant.now()
    ) {
        val am = alarmManager ?: return
        if (m.deleted || m.completed && m.recurrence == com.example.data.model.MilestoneRecurrence.NONE) return
        MilestoneDates.reminderTimes(m, today, zone, now).forEach { (days, at) ->
            arm(am, m.id, days, at)
        }
        MilestoneDates.rolloverTime(m, today, zone, now)?.let { arm(am, m.id, 0, it) }
    }

    fun cancel(id: String) {
        val am = alarmManager ?: return
        (listOf(0) + com.example.data.model.MilestoneLimits.REMINDER_OPTIONS).forEach { n ->
            pending(id, n, create = false)?.let {
                am.cancel(it)
                it.cancel()
            }
        }
    }

    private fun arm(am: AlarmManager, id: String, days: Int, at: Instant) {
        val pi = pending(id, days, create = true) ?: return
        runCatching { am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at.toEpochMilli(), pi) }
    }

    private fun pending(id: String, days: Int, create: Boolean): PendingIntent? {
        val intent = Intent(app, MilestoneReminderReceiver::class.java).apply {
            action = MilestoneReminderReceiver.ACTION_REMINDER
            data = Uri.parse("bhtt-milestone://reminder/$id/$days")
            putExtra(MilestoneReminderReceiver.EXTRA_ID, id)
            putExtra(MilestoneReminderReceiver.EXTRA_DAYS, days)
        }
        val flags = PendingIntent.FLAG_IMMUTABLE or
            if (create) PendingIntent.FLAG_UPDATE_CURRENT else PendingIntent.FLAG_NO_CREATE
        return PendingIntent.getBroadcast(app, 0, intent, flags)
    }
}

object MilestoneNotifications {
    const val CHANNEL_ID = "academic_milestones"

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        if (nm.getNotificationChannel(CHANNEL_ID) == null) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Academic milestones", NotificationManager.IMPORTANCE_DEFAULT).apply {
                    description = "Reminders for exams, deadlines and other milestones"
                }
            )
        }
    }

    fun canPost(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return false
        return NotificationManagerCompat.from(context).areNotificationsEnabled()
    }
}

class MilestoneReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext
        when (intent.action) {
            ACTION_REMINDER -> handleAlarm(app, intent)
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_TIMEZONE_CHANGED,
            Intent.ACTION_TIME_CHANGED -> rearmAll(app)
        }
    }

    private fun rearmAll(app: Context) {
        val store = MilestoneStore(app)
        MilestoneReminderScheduler(app).apply(store.load(store.activeScope))
    }

    @SuppressLint("MissingPermission") // guarded by MilestoneNotifications.canPost
    private fun handleAlarm(app: Context, intent: Intent) {
        val id = intent.getStringExtra(EXTRA_ID) ?: return
        val days = intent.getIntExtra(EXTRA_DAYS, -1)
        if (days < 0) return

        val store = MilestoneStore(app)
        val m = store.load(store.activeScope).firstOrNull { it.id == id && !it.deleted } ?: return
        val scheduler = MilestoneReminderScheduler(app)
        val today = LocalDate.now()

        if (days == 0) {
            // Roll over: arm the next occurrence of a recurring milestone (reference = tomorrow).
            scheduler.cancel(id)
            scheduler.schedule(m, today.plusDays(1))
            return
        }

        val resolved = MilestoneDates.resolve(m, today) ?: return
        if (resolved.status is CountdownStatus.Completed) return
        // Ignore stale alarms (e.g. milestone edited to another date): only fire for the right day.
        if (resolved.date.minusDays(days.toLong()) != today) return
        if (!MilestoneNotifications.canPost(app)) return

        MilestoneNotifications.ensureChannel(app)
        val open = app.packageManager.getLaunchIntentForPackage(app.packageName)?.let {
            PendingIntent.getActivity(app, 0, it, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        }
        val dateText = resolved.date.format(DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH))
        val whenText = if (days == 1) "Tomorrow" else "In $days days"
        val notification = NotificationCompat.Builder(app, MilestoneNotifications.CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setContentTitle(m.title)
            .setContentText("$whenText · $dateText")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(open)
            .build()
        // Same id for the same milestone+offset, so a repeated delivery replaces instead of duplicating.
        runCatching { NotificationManagerCompat.from(app).notify("milestone:$id:$days".hashCode(), notification) }
    }

    companion object {
        const val ACTION_REMINDER = "com.example.milestone.REMINDER"
        const val EXTRA_ID = "milestone_id"
        const val EXTRA_DAYS = "days_before"
    }
}
