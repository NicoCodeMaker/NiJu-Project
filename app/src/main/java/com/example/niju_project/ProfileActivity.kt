package com.example.niju_project

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.niju_project.data.model.SessionMode
import com.example.niju_project.data.repository.AuthRepository
import com.example.niju_project.ui.Formatters
import com.example.niju_project.ui.UiState
import com.example.niju_project.ui.profile.ProfileData
import com.example.niju_project.ui.profile.ProfileViewModel
import com.example.niju_project.ui.profile.initials
import com.example.niju_project.ui.stats.ActivityChartView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.progressindicator.LinearProgressIndicator
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/** Perfil del estudiante con datos reales del servidor. */
class ProfileActivity : AppCompatActivity() {

    private val viewModel: ProfileViewModel by viewModels()
    private val auth by lazy { AuthRepository() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_profile)

        menu(R.id.option_stats, R.drawable.ic_chart, R.string.profile_menu_stats, R.string.profile_menu_stats_sub) {
            startActivity(Intent(this, StatsActivity::class.java))
        }
        menu(R.id.option_ruta, R.drawable.ic_road, R.string.profile_menu_route, R.string.profile_menu_route_sub) {
            startActivity(Intent(this, RutaActivity::class.java))
        }
        menu(R.id.option_fav, R.drawable.ic_star, R.string.profile_menu_favorites, R.string.profile_menu_favorites_sub) {
            startActivity(Intent(this, FavoritesActivity::class.java))
        }
        menu(R.id.option_config, R.drawable.ic_settings, R.string.profile_menu_settings, R.string.profile_menu_settings_sub) {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
        findViewById<View>(R.id.btnEditProfile).setOnClickListener {
            startActivity(Intent(this, EditProfileActivity::class.java))
        }
        findViewById<View>(R.id.btnLogout).setOnClickListener { confirmLogout() }
        findViewById<Button>(R.id.btnEmptyAction).setOnClickListener { viewModel.load() }

        setupBottomNav(R.id.navProfile)
        observe()
    }

    override fun onResume() {
        super.onResume()
        viewModel.load()
    }

    private fun menu(id: Int, icon: Int, title: Int, subtitle: Int, onClick: () -> Unit) {
        val row = findViewById<View>(id)
        row.findViewById<ImageView>(R.id.rowIcon).setImageResource(icon)
        row.findViewById<TextView>(R.id.rowTitle).setText(title)
        row.findViewById<TextView>(R.id.rowSubtitle).setText(subtitle)
        row.setOnClickListener { onClick() }
    }

    private fun confirmLogout() {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.profile_logout_title)
            .setMessage(R.string.profile_logout_message)
            .setPositiveButton(R.string.profile_logout) { _, _ ->
                lifecycleScope.launch {
                    auth.signOut()
                    goToLogin()
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun observe() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    findViewById<View>(R.id.progressBar).visibility = if (state is UiState.Loading) View.VISIBLE else View.GONE
                    findViewById<View>(R.id.emptyState).visibility = if (state is UiState.Error) View.VISIBLE else View.GONE
                    findViewById<View>(R.id.content).visibility = if (state is UiState.Success) View.VISIBLE else View.GONE
                    when (state) {
                        is UiState.Success -> render(state.data)
                        is UiState.Error -> {
                            if (state.sessionExpired) goToLogin()
                            findViewById<TextView>(R.id.tvEmptyMessage).text = state.message
                            findViewById<View>(R.id.btnEmptyAction).visibility = View.VISIBLE
                        }
                        UiState.Loading -> Unit
                    }
                }
            }
        }
    }

    private fun render(data: ProfileData) {
        val p = data.overview
        val name = p.displayName.ifBlank { getString(R.string.home_default_name) }
        val email = p.email ?: auth.currentEmail()
        findViewById<TextView>(R.id.tvAvatar).text = initials(p.displayName, email)
        findViewById<TextView>(R.id.user_name).text = name
        findViewById<TextView>(R.id.user_location).apply {
            text = email.orEmpty()
            visibility = if (email.isNullOrBlank()) View.GONE else View.VISIBLE
        }
        findViewById<TextView>(R.id.tvLevel).text = getString(R.string.level_title_value, p.level, p.levelTitle)
        findViewById<TextView>(R.id.tvXP).text = getString(R.string.xp_value, p.xpTotal)
        findViewById<TextView>(R.id.tvNextLevel).text = getString(R.string.profile_next_level, p.nextLevelXp, p.level + 1)
        findViewById<LinearProgressIndicator>(R.id.progressLevel).setProgressCompat(p.levelPercent, true)
        findViewById<TextView>(R.id.tvMemberSince).text =
            getString(R.string.profile_member_since, Formatters.shortDate(p.memberSince), p.dailyGoalXp)

        tile(R.id.statLessons, "📘", getString(R.string.fraction_value, p.lessonsCompleted, p.lessonsTotal), R.string.profile_stat_lessons)
        tile(R.id.statWords, "📚", p.wordsLearned.toString(), R.string.profile_stat_words)
        tile(R.id.statMastered, "🧠", p.wordsMastered.toString(), R.string.profile_stat_mastered)
        tile(R.id.statStreak, "🔥", Formatters.days(p.currentStreak),
            R.string.profile_stat_streak, getString(R.string.profile_stat_streak_record, p.longestStreak))
        tile(R.id.statTime, "⏱️", Formatters.duration(p.studyMsTotal), R.string.profile_stat_time,
            getString(R.string.profile_stat_time_week, Formatters.duration(p.studyMsWeek)))
        tile(R.id.statAccuracy, "🎯", Formatters.accuracy(p.accuracy), R.string.profile_stat_accuracy,
            getString(R.string.profile_stat_answers, p.answersTotal))
        tile(R.id.statExams, "🎓", p.examsPassed.toString(), R.string.profile_stat_exams)
        tile(R.id.statBadges, "🏆", getString(R.string.fraction_value, p.achievementsUnlocked, p.achievementsTotal),
            R.string.profile_stat_badges)

        findViewById<ActivityChartView>(R.id.activityChart).setData(data.week)
        findViewById<TextView>(R.id.tvWeekSummary).text = getString(R.string.profile_week_summary,
            data.week.sumOf { it.xp }, data.week.sumOf { it.answers }, data.week.count { it.answers > 0 })

        val list = findViewById<LinearLayout>(R.id.historyList)
        list.removeAllViews()
        if (data.history.isEmpty()) {
            list.addView(TextView(this).apply {
                setText(R.string.profile_history_empty)
                setTextColor(ContextCompat.getColor(context, R.color.color_text_secondary))
                setPadding(0, 24, 0, 24)
            })
        }
        data.history.forEach { h ->
            val v = LayoutInflater.from(this).inflate(R.layout.item_history, list, false)
            val (icon, mode) = when (h.mode) {
                SessionMode.LESSON -> "📘" to getString(R.string.session_mode_lesson)
                SessionMode.REVIEW -> "🔁" to getString(R.string.session_mode_review)
                SessionMode.PRACTICE -> "🎲" to getString(R.string.session_mode_practice)
                SessionMode.EXAM -> "🎓" to getString(R.string.session_mode_exam)
            }
            val what = h.lessonTitle ?: h.contextName
            v.findViewById<TextView>(R.id.tvHistIcon).text = icon
            v.findViewById<TextView>(R.id.tvHistTitle).text = if (what == null) mode else "$mode · $what"
            v.findViewById<TextView>(R.id.tvHistMeta).text = getString(R.string.profile_history_meta,
                Formatters.shortDate(h.finishedAt), h.correct, h.total, Formatters.duration(h.studyMs), h.xpEarned)
            val score = h.score.roundToInt()
            v.findViewById<TextView>(R.id.tvHistScore).apply {
                text = getString(R.string.percent_value, score)
                val good = score >= 70
                setBackgroundResource(if (good) R.drawable.bg_badge_success else R.drawable.bg_badge_error)
                setTextColor(ContextCompat.getColor(context, if (good) R.color.color_success else R.color.color_error))
            }
            v.contentDescription = "${v.findViewById<TextView>(R.id.tvHistTitle).text}. " +
                "${v.findViewById<TextView>(R.id.tvHistMeta).text}. ${getString(R.string.percent_value, score)}"
            list.addView(v)
        }
    }

    private fun tile(id: Int, emoji: String, value: String, label: Int, extra: String? = null) {
        val t = findViewById<View>(id)
        t.findViewById<TextView>(R.id.tvTileEmoji).text = emoji
        t.findViewById<TextView>(R.id.tvTileValue).text = value
        val text = if (extra == null) getString(label) else "${getString(label)} · $extra"
        t.findViewById<TextView>(R.id.tvTileLabel).text = text
        t.contentDescription = "$text: $value"
    }
}
