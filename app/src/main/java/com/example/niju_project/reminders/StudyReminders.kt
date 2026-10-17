package com.example.niju_project.reminders

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.niju_project.MainApplication
import com.example.niju_project.R
import com.example.niju_project.SplashActivity
import com.example.niju_project.data.remote.SupabaseProvider
import com.example.niju_project.data.repository.AuthRepository
import com.example.niju_project.data.repository.LearningRepository
import kotlinx.coroutines.withTimeoutOrNull
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

/** Qué notificación mostrar (o ninguna). Código puro, probado en ReminderPolicyTest. */
object ReminderPolicy {

    data class Message(val title: String, val text: String)

    fun decide(
        dailyReminderOn: Boolean,
        reviewAlertsOn: Boolean,
        studiedToday: Boolean,
        dueReviews: Int?
    ): Message? = when {
        studiedToday -> null
        reviewAlertsOn && (dueReviews ?: 0) > 0 -> Message(
            "Tienes repasos pendientes",
            if (dueReviews == 1) "1 palabra está lista para repasar. ¡No la olvides!"
            else "$dueReviews palabras están listas para repasar. ¡No las olvides!"
        )
        dailyReminderOn -> Message("¿Practicamos hoy?", "Unos minutos en NiJu mantienen tu racha.")
        else -> null
    }

    /** Milisegundos hasta la próxima hora indicada (hoy si aún no pasó, si no mañana). */
    fun delayUntil(hour: Int, now: Calendar = Calendar.getInstance()): Long {
        val target = (now.clone() as Calendar).apply {
            set(Calendar.HOUR_OF_DAY, hour); set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
            if (!after(now)) add(Calendar.DAY_OF_YEAR, 1)
        }
        return target.timeInMillis - now.timeInMillis
    }
}

/** Preferencias y programación de los recordatorios. */
object StudyReminders {

    const val KEY_DAILY = "notif_reminders"
    const val KEY_REVIEWS = "notif_lessons"
    private const val KEY_LAST_STUDY_DAY = "last_study_day"
    private const val WORK_NAME = "niju_daily_reminder"
    const val CHANNEL_ID = "niju_reminders"
    private const val REMINDER_HOUR = 19   // 7:00 p. m. hora local

    private fun prefs(c: Context) = c.getSharedPreferences(MainApplication.PREFS, Context.MODE_PRIVATE)
    private fun today() = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())

    fun isDailyOn(c: Context) = prefs(c).getBoolean(KEY_DAILY, false)
    fun isReviewsOn(c: Context) = prefs(c).getBoolean(KEY_REVIEWS, false)

    fun setEnabled(c: Context, key: String, enabled: Boolean) {
        prefs(c).edit().putBoolean(key, enabled).apply()
        reschedule(c)
    }

    fun markStudiedToday(c: Context) = prefs(c).edit().putString(KEY_LAST_STUDY_DAY, today()).apply()
    fun studiedToday(c: Context) = prefs(c).getString(KEY_LAST_STUDY_DAY, null) == today()

    /** Programa (o cancela) el trabajo diario según las preferencias. */
    fun reschedule(c: Context) {
        val wm = WorkManager.getInstance(c)
        if (!isDailyOn(c) && !isReviewsOn(c)) {
            wm.cancelUniqueWork(WORK_NAME)
            return
        }
        val request = PeriodicWorkRequestBuilder<ReminderWorker>(24, TimeUnit.HOURS)
            .setInitialDelay(ReminderPolicy.delayUntil(REMINDER_HOUR), TimeUnit.MILLISECONDS)
            .build()
        wm.enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.UPDATE, request)
    }

    fun createChannel(c: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            CHANNEL_ID, c.getString(R.string.reminders_channel), NotificationManager.IMPORTANCE_DEFAULT
        ).apply { description = c.getString(R.string.reminders_channel_description) }
        c.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    fun canNotify(c: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(c, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED

    fun show(c: Context, message: ReminderPolicy.Message) {
        if (!canNotify(c)) return
        val open = PendingIntent.getActivity(
            c, 0, Intent(c, SplashActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(c, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_star_filled)
            .setContentTitle(message.title)
            .setContentText(message.text)
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        try {
            NotificationManagerCompat.from(c).notify(1001, notification)
        } catch (_: SecurityException) {
            // Permiso revocado mientras tanto: no se muestra.
        }
    }
}

/** Se ejecuta una vez al día (≈ 7 p. m.) y decide si avisar. */
class ReminderWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val c = applicationContext
        var studied = StudyReminders.studiedToday(c)
        var due: Int? = null

        // Con sesión iniciada se consulta el servidor (máx. 15 s); si falla, se usa lo local.
        if (SupabaseProvider.isConfigured && StudyReminders.isReviewsOn(c)) {
            withTimeoutOrNull(15_000) {
                val auth = AuthRepository()
                auth.awaitInitialization()
                if (auth.isLoggedIn()) {
                    LearningRepository().getDashboard().getOrNull()?.let {
                        due = it.dueReviews
                        studied = studied || it.studiedToday
                    }
                }
            }
        }

        ReminderPolicy.decide(StudyReminders.isDailyOn(c), StudyReminders.isReviewsOn(c), studied, due)
            ?.let { StudyReminders.show(c, it) }
        return Result.success()
    }
}
