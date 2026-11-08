package com.example.niju_project.ui.results

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.example.niju_project.PracticeActivity
import com.example.niju_project.R
import com.example.niju_project.data.model.SessionMode
import com.example.niju_project.data.model.SessionSummary
import com.example.niju_project.data.remote.NijuJson
import com.example.niju_project.ui.Formatters
import com.example.niju_project.ui.practice.PracticeViewModel
import com.example.niju_project.ui.widget.ConfettiView
import com.example.niju_project.ui.widget.ScoreRingView
import com.google.android.material.button.MaterialButton
import kotlin.math.roundToInt

/**
 * Resultado de una sesión con celebración. Todo lo que muestra viene del resumen
 * de finish_session (servidor): XP, nivel, logros, retos y lección/evaluación.
 */
class SessionResultActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_session_result)
        val s = intent.getStringExtra(EXTRA_SUMMARY)
            ?.let { runCatching { NijuJson.decodeFromString(SessionSummary.serializer(), it) }.getOrNull() }
        if (s == null) { finish(); return }
        render(s)
    }

    private fun render(s: SessionSummary) {
        val header = ResultTexts.header(s)
        findViewById<TextView>(R.id.tvResultEmoji).text = header.emoji
        findViewById<TextView>(R.id.tvResultTitle).text = header.title
        val title = intent.getStringExtra(PracticeViewModel.EXTRA_TITLE)
        findViewById<TextView>(R.id.tvResultSubtitle).apply {
            text = title.orEmpty()
            visibility = if (title.isNullOrBlank()) View.GONE else View.VISIBLE
        }
        findViewById<ScoreRingView>(R.id.scoreRing)
            .setScore(s.score.roundToInt(), getString(R.string.result_correct_of, s.correct, s.total))
        findViewById<TextView>(R.id.tvStatXp).apply {
            text = getString(R.string.result_stat_xp, s.xpEarned)
            contentDescription = getString(R.string.result_stat_xp_desc, s.xpEarned)
        }
        findViewById<TextView>(R.id.tvStatTime).apply {
            text = getString(R.string.result_stat_time, Formatters.duration(s.studyMs))
            contentDescription = getString(R.string.result_stat_time_desc, Formatters.duration(s.studyMs))
        }
        findViewById<TextView>(R.id.tvStatStreak).apply {
            text = getString(R.string.result_stat_streak, Formatters.days(s.currentStreak))
            contentDescription = getString(R.string.result_stat_streak_desc, Formatters.days(s.currentStreak))
        }
        findViewById<TextView>(R.id.tvResultMessage).text = ResultTexts.message(s)

        val list = findViewById<LinearLayout>(R.id.rewardsList)
        list.removeAllViews()
        if (s.leveledUp) addRow(list, "⬆️", getString(R.string.result_level_up, s.level),
            s.levelTitle.ifBlank { getString(R.string.level_value, s.level) }, null)
        if (s.mode == SessionMode.LESSON && s.lessonCompleted) addRow(list, "📘",
            getString(R.string.result_lesson_passed), getString(R.string.result_lesson_passed_sub), 50)
        if (s.examPassed) addRow(list, "🎓", getString(R.string.result_exam_passed),
            getString(R.string.result_exam_passed_sub), 100)
        if (s.perfect) addRow(list, "🌟", getString(R.string.result_perfect), getString(R.string.result_perfect_sub), null)
        s.challengesCompleted.forEach {
            addRow(list, if (it.period == "weekly") "📅" else "🎯", it.title,
                getString(if (it.period == "weekly") R.string.result_weekly_done else R.string.result_challenge_done), it.xpReward)
        }
        s.newAchievements.forEach { addRow(list, "🏆", it.title, getString(R.string.result_achievement), it.xpReward) }
        val hasRewards = list.childCount > 0
        findViewById<View>(R.id.tvRewardsTitle).visibility = if (hasRewards) View.VISIBLE else View.GONE
        findViewById<View>(R.id.cardRewards).visibility = if (hasRewards) View.VISIBLE else View.GONE
        animateRows(list)

        findViewById<MaterialButton>(R.id.btnResultPrimary).setOnClickListener { finish() }
        findViewById<MaterialButton>(R.id.btnResultSecondary).apply {
            visibility = if (ResultTexts.canRepeat(s)) View.VISIBLE else View.GONE
            setText(when (s.mode) {
                SessionMode.LESSON -> R.string.session_repeat_lesson
                SessionMode.EXAM -> R.string.result_retry_exam
                else -> R.string.result_another_round
            })
            setOnClickListener {
                startActivity(Intent(this@SessionResultActivity, PracticeActivity::class.java).apply {
                    putExtras(intent)
                    removeExtra(EXTRA_SUMMARY)
                })
                finish()
            }
        }

        if (header.celebrate || s.leveledUp) findViewById<ConfettiView>(R.id.confetti).burst()
    }

    private fun addRow(list: LinearLayout, icon: String, title: String, subtitle: String, xp: Int?) {
        val row = LayoutInflater.from(this).inflate(R.layout.item_reward_row, list, false)
        row.findViewById<TextView>(R.id.tvRowIcon).text = icon
        row.findViewById<TextView>(R.id.tvRowTitle).text = title
        row.findViewById<TextView>(R.id.tvRowSubtitle).text = subtitle
        row.findViewById<TextView>(R.id.tvRowBadge).apply {
            visibility = if (xp != null && xp > 0) View.VISIBLE else View.GONE
            text = getString(R.string.xp_plus, xp ?: 0)
        }
        row.contentDescription = listOfNotNull(title, subtitle, xp?.takeIf { it > 0 }?.let { getString(R.string.xp_plus, it) })
            .joinToString(". ")
        list.addView(row)
    }

    private fun animateRows(list: LinearLayout) {
        for (i in 0 until list.childCount) {
            list.getChildAt(i).apply {
                alpha = 0f; translationY = 30f
                animate().alpha(1f).translationY(0f).setStartDelay(500L + i * 120L).setDuration(300).start()
            }
        }
    }

    companion object {
        const val EXTRA_SUMMARY = "summary"
    }
}
