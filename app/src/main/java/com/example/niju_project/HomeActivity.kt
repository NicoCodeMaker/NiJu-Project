package com.example.niju_project

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
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
import com.example.niju_project.ui.LoaderViewModel
import com.example.niju_project.ui.UiState
import com.example.niju_project.ui.factoryOf
import com.example.niju_project.ui.home.DashboardViewModel
import kotlinx.coroutines.launch

class HomeActivity : AppCompatActivity() {

    private val viewModel: DashboardViewModel by viewModels()
    private val nextLessonVm: LoaderViewModel<NextLesson?> by viewModels {
        factoryOf { LoaderViewModel { LearningRepository().getNextLesson() } }
    }

    private lateinit var tvGreeting: TextView
    private lateinit var tvSubtitle: TextView
    private lateinit var tvStreak: TextView
    private lateinit var tvXP: TextView
    private lateinit var tvLevel: TextView
    private lateinit var tvDailyProgress: TextView
    private lateinit var progressDaily: ProgressBar
    private lateinit var btnStartSession: Button
    private lateinit var cardContinue: View
    private lateinit var btnContinue: Button
    private lateinit var ivContinueImage: ImageView
    private lateinit var tvContinueEyebrow: TextView
    private lateinit var tvContinueTitle: TextView
    private lateinit var tvContinueMeta: TextView
    private lateinit var tvLevelTitle: TextView
    private lateinit var tvLevelProgress: TextView
    private lateinit var progressLevel: ProgressBar
    private lateinit var tvProgressSummary: TextView

    private var dueReviews = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_home)

        tvGreeting = findViewById(R.id.tvGreeting)
        tvSubtitle = findViewById(R.id.tvSubtitle)
        tvStreak = findViewById(R.id.tvStreak)
        tvXP = findViewById(R.id.tvXP)
        tvLevel = findViewById(R.id.tvLevel)
        tvDailyProgress = findViewById(R.id.tvDailyProgress)
        progressDaily = findViewById(R.id.progressDaily)
        btnStartSession = findViewById(R.id.btnStartSession)
        cardContinue = findViewById(R.id.cardContinue)
        btnContinue = findViewById(R.id.btnContinue)
        ivContinueImage = findViewById(R.id.ivContinueImage)
        tvContinueEyebrow = findViewById(R.id.tvContinueEyebrow)
        tvContinueTitle = findViewById(R.id.tvContinueTitle)
        tvContinueMeta = findViewById(R.id.tvContinueMeta)
        tvLevelTitle = findViewById(R.id.tvLevelTitle)
        tvLevelProgress = findViewById(R.id.tvLevelProgress)
        progressLevel = findViewById(R.id.progressLevel)
        tvProgressSummary = findViewById(R.id.tvProgressSummary)

        // Con repasos pendientes se repasa; si no, práctica libre de todo el catálogo.
        btnStartSession.setOnClickListener {
            if (dueReviews > 0) openSession(SessionMode.REVIEW, title = getString(R.string.session_review_title))
            else openSession(SessionMode.PRACTICE, title = getString(R.string.home_free_practice))
        }
        findViewById<LinearLayout?>(R.id.btnFavoritos)?.setOnClickListener {
            startActivity(Intent(this, FavoritesActivity::class.java))
        }
        findViewById<LinearLayout?>(R.id.btnTrofeos)?.setOnClickListener {
            startActivity(Intent(this, StatsActivity::class.java))
        }
        findViewById<LinearLayout?>(R.id.btnContextos)?.setOnClickListener { openTab(ContextsActivity::class.java) }

        setupBottomNav(R.id.navHome)
        observe()
        observeNextLesson()
    }

    override fun onResume() {
        super.onResume()
        viewModel.load()
        nextLessonVm.load()
    }

    private fun observe() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    when (state) {
                        is UiState.Loading -> btnStartSession.isEnabled = false
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
                        // Sin la migración 4 o sin red, la tarjeta se oculta: Contextos sigue funcionando.
                        is UiState.Error -> if (state.sessionExpired) goToLogin() else cardContinue.visibility = View.GONE
                        is UiState.Success -> renderNextLesson(state.data)
                    }
                }
            }
        }
    }

    private fun renderNextLesson(n: NextLesson?) {
        cardContinue.visibility = View.VISIBLE
        btnContinue.isEnabled = true
        if (n == null) {
            ivContinueImage.setImageResource(contextImage(null))
            tvContinueEyebrow.setText(R.string.home_all_done_eyebrow)
            tvContinueTitle.setText(R.string.home_all_done_title)
            tvContinueMeta.setText(R.string.home_all_done_meta)
            btnContinue.text = if (dueReviews > 0) getString(R.string.home_review_now, dueReviews)
            else getString(R.string.home_free_practice)
            btnContinue.setOnClickListener {
                btnContinue.isEnabled = false
                if (dueReviews > 0) openSession(SessionMode.REVIEW, title = getString(R.string.session_review_title))
                else openSession(SessionMode.PRACTICE, title = getString(R.string.home_free_practice))
            }
            return
        }
        ivContinueImage.setImageResource(contextImage(n.contextSlug))
        tvContinueEyebrow.text = getString(R.string.home_continue_eyebrow, n.contextName)
        tvContinueTitle.text = n.lessonTitle
        tvContinueMeta.text = getString(
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
        tvGreeting.text = getString(R.string.home_greeting, name)
        tvSubtitle.text = when {
            d.dueReviews > 0 -> resources.getQuantityString(R.plurals.home_subtitle_due, d.dueReviews, d.dueReviews)
            d.wordsPracticed == 0 -> getString(R.string.home_subtitle_start)
            else -> getString(R.string.home_subtitle_caught_up)
        }
        tvStreak.text = d.currentStreak.toString()
        tvXP.text = d.xpTotal.toString()
        tvLevel.text = d.level.toString()
        tvDailyProgress.text = getString(R.string.home_daily_goal_value, d.xpToday, d.dailyGoalXp)
        progressDaily.progress = d.dailyGoalPercent
        tvLevelTitle.text = getString(R.string.home_level_title, d.level)
        tvLevelProgress.text = getString(R.string.home_level_value, d.xpTotal - d.levelStartXp, d.nextLevelXp - d.levelStartXp)
        progressLevel.progress = d.levelPercent
        tvProgressSummary.text = getString(R.string.home_progress_summary, d.wordsPracticed, d.wordsMastered, d.lessonsCompleted)

        btnStartSession.isEnabled = true
        btnStartSession.text = if (d.dueReviews > 0) getString(R.string.home_review_now, d.dueReviews) else getString(R.string.ui_practica_libre)
    }
}
