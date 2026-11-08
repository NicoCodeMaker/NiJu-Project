package com.example.niju_project

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.niju_project.data.model.Dashboard
import com.example.niju_project.data.model.NextLesson
import com.example.niju_project.data.model.SessionMode
import com.example.niju_project.data.repository.LearningRepository
import com.example.niju_project.ui.Formatters
import com.example.niju_project.ui.LoaderViewModel
import com.example.niju_project.ui.UiState
import com.example.niju_project.ui.factoryOf
import com.example.niju_project.ui.home.DashboardViewModel
import com.google.android.material.button.MaterialButton
import com.google.android.material.progressindicator.LinearProgressIndicator
import kotlinx.coroutines.launch

/** Inicio: datos reales de get_dashboard y get_next_lesson. */
class HomeActivity : AppCompatActivity() {

    private val viewModel: DashboardViewModel by viewModels()
    private val nextLessonVm: LoaderViewModel<NextLesson?> by viewModels {
        factoryOf { LoaderViewModel { LearningRepository().getNextLesson() } }
    }

    private lateinit var cardContinue: View
    private lateinit var btnContinue: MaterialButton
    private var dueReviews = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_home)
        cardContinue = findViewById(R.id.cardContinue)
        btnContinue = findViewById(R.id.btnContinue)

        quick(R.id.quickReview, "🔁", R.string.home_quick_review) { openTab(ReviewActivity::class.java) }
        quick(R.id.quickChallenges, "🎯", R.string.home_quick_challenges) { openTab(RewardsActivity::class.java) }
        quick(R.id.quickPractice, "🎲", R.string.home_quick_practice) {
            openSession(SessionMode.PRACTICE, title = getString(R.string.home_free_practice))
        }
        quick(R.id.quickFavorites, "⭐", R.string.home_quick_favorites) {
            startActivity(Intent(this, FavoritesActivity::class.java))
        }
        setQuickSubtitle(R.id.quickPractice, getString(R.string.home_quick_practice_sub))
        setQuickSubtitle(R.id.quickFavorites, getString(R.string.home_quick_favorites_sub))
        findViewById<View>(R.id.cardRoute).setOnClickListener { startActivity(Intent(this, RutaActivity::class.java)) }
        findViewById<View>(R.id.chipLevel).setOnClickListener { openTab(RewardsActivity::class.java) }
        findViewById<View>(R.id.chipStreak).setOnClickListener { openTab(RewardsActivity::class.java) }

        setupBottomNav(R.id.navHome)
        observe()
        observeNextLesson()
    }

    override fun onResume() {
        super.onResume()
        viewModel.load()
        nextLessonVm.load()
    }

    private fun quick(id: Int, emoji: String, title: Int, onClick: () -> Unit) {
        val v = findViewById<View>(id)
        v.findViewById<TextView>(R.id.tvQuickEmoji).text = emoji
        v.findViewById<TextView>(R.id.tvQuickTitle).setText(title)
        v.setOnClickListener { onClick() }
    }

    private fun setQuickSubtitle(id: Int, text: String) {
        val v = findViewById<View>(id)
        v.findViewById<TextView>(R.id.tvQuickSubtitle).text = text
        v.contentDescription = "${v.findViewById<TextView>(R.id.tvQuickTitle).text}. $text"
    }

    private fun observe() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    when (state) {
                        is UiState.Loading -> Unit
                        is UiState.Success -> render(state.data)
                        is UiState.Error -> {
                            if (state.sessionExpired) goToLogin()
                            else Toast.makeText(this@HomeActivity, state.message, Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
        }
    }

    private fun observeNextLesson() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                nextLessonVm.uiState.collect { state ->
                    when (state) {
                        is UiState.Loading -> Unit
                        // Sin red, la tarjeta se oculta: Aprender sigue funcionando.
                        is UiState.Error -> if (state.sessionExpired) goToLogin() else cardContinue.visibility = View.GONE
                        is UiState.Success -> renderNextLesson(state.data)
                    }
                }
            }
        }
    }

    private fun setContinueImage(slug: String?, emoji: String?) {
        val image = findViewById<ImageView>(R.id.ivContinueImage)
        val emojiView = findViewById<TextView>(R.id.tvContinueEmoji)
        if (hasContextImage(slug) || emoji.isNullOrBlank()) {
            image.setImageResource(contextImage(slug))
            emojiView.visibility = View.GONE
        } else {
            emojiView.text = emoji
            emojiView.visibility = View.VISIBLE
        }
    }

    private fun renderNextLesson(n: NextLesson?) {
        cardContinue.visibility = View.VISIBLE
        btnContinue.isEnabled = true
        if (n == null) {
            setContinueImage(null, "🏆")
            findViewById<TextView>(R.id.tvContinueEyebrow).setText(R.string.home_all_done_eyebrow)
            findViewById<TextView>(R.id.tvContinueTitle).setText(R.string.home_all_done_title)
            findViewById<TextView>(R.id.tvContinueMeta).setText(R.string.home_all_done_meta)
            btnContinue.text = if (dueReviews > 0) getString(R.string.home_review_now, dueReviews)
            else getString(R.string.home_free_practice)
            btnContinue.setOnClickListener {
                btnContinue.isEnabled = false
                if (dueReviews > 0) openSession(SessionMode.REVIEW, title = getString(R.string.session_review_title))
                else openSession(SessionMode.PRACTICE, title = getString(R.string.home_free_practice))
            }
            return
        }
        setContinueImage(n.contextSlug, contextEmoji(n.contextSlug))
        findViewById<TextView>(R.id.tvContinueEyebrow).text = getString(R.string.home_continue_eyebrow, n.contextName)
        findViewById<TextView>(R.id.tvContinueTitle).text = n.lessonTitle
        findViewById<TextView>(R.id.tvContinueMeta).text = getString(
            R.string.home_continue_meta, n.level, n.wordCount,
            n.contextLessonsCompleted + 1, n.contextLessonsTotal
        )
        btnContinue.text = getString(R.string.home_continue_button)
        btnContinue.setOnClickListener {
            btnContinue.isEnabled = false // evita doble toque
            startActivity(Intent(this, WordCardsActivity::class.java).apply {
                putExtra(WordCardsActivity.EXTRA_LESSON_ID, n.lessonId)
                putExtra(WordCardsActivity.EXTRA_LESSON_TITLE, n.lessonTitle)
                putExtra(WordCardsActivity.EXTRA_CONTEXT_ID, n.contextId)
                putExtra(WordCardsActivity.EXTRA_CONTEXT_SLUG, n.contextSlug)
            })
        }
    }

    private fun render(d: Dashboard) {
        dueReviews = d.dueReviews
        val name = d.displayName.substringBefore(' ').ifBlank { getString(R.string.home_default_name) }
        findViewById<TextView>(R.id.tvGreeting).text = getString(R.string.home_greeting, name)
        findViewById<TextView>(R.id.tvSubtitle).text = when {
            d.dueReviews > 0 -> resources.getQuantityString(R.plurals.home_subtitle_due, d.dueReviews, d.dueReviews)
            d.wordsPracticed == 0 -> getString(R.string.home_subtitle_start)
            else -> getString(R.string.home_subtitle_caught_up)
        }
        findViewById<TextView>(R.id.tvStreak).apply {
            text = getString(R.string.home_streak_value, d.currentStreak)
            contentDescription = getString(R.string.home_streak_desc, Formatters.days(d.currentStreak))
        }
        findViewById<TextView>(R.id.tvXP).apply {
            text = getString(R.string.home_xp_value, d.xpTotal)
            contentDescription = getString(R.string.xp_value, d.xpTotal)
        }
        findViewById<TextView>(R.id.tvLevel).apply {
            text = getString(R.string.home_level_chip, d.level)
            contentDescription = getString(R.string.level_value, d.level)
        }
        findViewById<TextView>(R.id.tvDailyProgress).text = getString(R.string.home_daily_goal_value, d.xpToday, d.dailyGoalXp)
        findViewById<LinearProgressIndicator>(R.id.progressDaily).setProgressCompat(d.dailyGoalPercent, true)
        findViewById<TextView>(R.id.tvLevelTitle).text =
            if (d.levelTitle.isBlank()) getString(R.string.home_level_title, d.level)
            else getString(R.string.level_title_value, d.level, d.levelTitle)
        findViewById<TextView>(R.id.tvLevelProgress).text =
            getString(R.string.home_level_value, d.xpTotal - d.levelStartXp, d.nextLevelXp - d.levelStartXp)
        findViewById<LinearProgressIndicator>(R.id.progressLevel).setProgressCompat(d.levelPercent, true)
        findViewById<TextView>(R.id.tvStudyToday).text =
            if (d.studyMsToday > 0) getString(R.string.home_study_today, Formatters.duration(d.studyMsToday))
            else getString(R.string.home_study_none)
        findViewById<TextView>(R.id.tvProgressSummary).text =
            getString(R.string.home_progress_summary, d.wordsPracticed, d.wordsMastered, d.lessonsCompleted)

        setQuickSubtitle(R.id.quickReview, when {
            d.dueReviews == 0 -> getString(R.string.home_quick_review_none)
            d.urgentReviews > 0 -> getString(R.string.home_quick_review_urgent, d.dueReviews, d.urgentReviews)
            else -> resources.getQuantityString(R.plurals.home_quick_review_due, d.dueReviews, d.dueReviews)
        })
        setQuickSubtitle(R.id.quickChallenges,
            getString(R.string.home_quick_challenges_sub, d.challengesDone, d.challengesTotal))
    }
}
