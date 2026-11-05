package com.example.notifications

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.MainActivity
import com.example.R
import com.example.data.repository.AttendanceRepository
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Posts the notification for a fired alarm, then re-arms the next one. Also re-arms after reboot / app update. */
class ReminderReceiver : BroadcastReceiver() {

    @SuppressLint("MissingPermission")
    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext
        val repo = AttendanceRepository(app)
        val profile = repo.profileState.value

        var title: String? = null
        var text: String? = null
        var id = 0
        when (intent.action) {
            ReminderScheduler.ACTION_DAILY -> if (profile.dailyReminderEnabled) {
                title = "Log today's attendance"
                text = "Mark today's lectures before the evening."
                id = 8101
            }
            ReminderScheduler.ACTION_WEEKLY -> if (profile.weeklyReminderEnabled) {
                val monthId = SimpleDateFormat("yyyy-MM", Locale.US).format(Date())
                val a = repo.getMonthlyAnalytics(monthId)
                title = "Weekly attendance report"
                text = "This month: ${"%.1f".format(a.percentage)}% (target ${a.examTarget}%)."
                id = 8102
            }
            ReminderScheduler.ACTION_CLASS -> if (profile.classScheduleAlertsEnabled) {
                title = intent.getStringExtra(ReminderScheduler.EXTRA_TITLE)
                text = intent.getStringExtra(ReminderScheduler.EXTRA_TEXT)
                id = 8103
            }
        }

        if (title != null && NotificationManagerCompat.from(app).areNotificationsEnabled()) {
            ReminderScheduler.ensureChannel(app)
            val open = PendingIntent.getActivity(
                app, 0,
                Intent(app, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            val n = NotificationCompat.Builder(app, ReminderScheduler.CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_stat_attendance)
                .setContentTitle(title)
                .setContentText(text)
                .setAutoCancel(true)
                .setContentIntent(open)
                .build()
            runCatching { NotificationManagerCompat.from(app).notify(id, n) }
        }
        // Always re-arm so the next occurrence exists (also covers BOOT_COMPLETED / MY_PACKAGE_REPLACED).
        repo.rescheduleReminders()
    }
}
