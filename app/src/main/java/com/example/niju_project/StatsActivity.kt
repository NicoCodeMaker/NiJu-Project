package com.example.niju_project

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.niju_project.data.model.AchievementStatus
import com.example.niju_project.data.model.SessionMode
import com.example.niju_project.ui.Formatters
import com.example.niju_project.ui.UiState
import com.example.niju_project.ui.stats.ActivityChartView
import com.example.niju_project.ui.stats.StatsData
import com.example.niju_project.ui.stats.StatsViewModel
import kotlinx.coroutines.launch

/**
 * Estadísticas y logros. Distingue palabras vistas, practicadas y dominadas
 * (ver supabase/README.md) para no presentar como aprendizaje lo que no lo es.
 */
class StatsActivity : AppCompatActivity() {

    private val viewModel: StatsViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_stats)
        findViewById<TextView>(R.id.tvHeaderTitle).setText(R.string.stats_title)
        findViewById<ImageButton>(R.id.btnBack).setOnClickListener { finish() }
        findViewById<Button>(R.id.btnEmptyAction).setOnClickListener { viewModel.load() }
        findViewById<Button>(R.id.btnPracticeStruggling).setOnClickListener {
            openSession(SessionMode.REVIEW, title = getString(R.string.session_review_title))
        }
        setupBottomNav(R.id.navProfile)
        observe()
    }

    override fun onResume() {
        super.onResume()
        viewModel.load()
    }

    private fun observe() {
        val progress = findViewById<ProgressBar>(R.id.progressBar)
        val content = findViewById<View>(R.id.content)
        val empty = findViewById<View>(R.id.emptyState)
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    progress.visibility = if (state is UiState.Loading) View.VISIBLE else View.GONE
                    content.visibility = if (state is UiState.Success) View.VISIBLE else View.GONE
                    empty.visibility = if (state is UiState.Error) View.VISIBLE else View.GONE
                    when (state) {
                        is UiState.Success -> render(state.data)
                        is UiState.Error -> {
                            if (state.sessionExpired) goToLogin()
                            findViewById<TextView>(R.id.tvEmptyMessage).text = state.message
                            findViewById<Button>(R.id.btnEmptyAction).visibility = View.VISIBLE
                        }
                        UiState.Loading -> Unit
                    }
                }
            }
        }
    }

    private fun render(data: StatsData) {
        val d = data.dashboard
        renderTiles(
            listOf(
                "${d.xpTotal}" to "XP total · nivel ${d.level}",
                "${d.currentStreak}" to "Racha actual (récord ${d.longestStreak})",
                Formatters.accuracy(d.accuracy) to "Precisión de respuestas",
                "${d.lessonsCompleted}" to "Lecciones superadas",
                "${d.wordsSeen}" to "Palabras vistas",
                "${d.wordsPracticed}" to "Palabras practicadas",
                "${d.wordsMastered}" to "Palabras dominadas (21+ días)",
                "${d.dueReviews}" to "Repasos pendientes"
            )
        )

        findViewById<ActivityChartView>(R.id.activityChart).setData(data.activity)
        val week = data.activity
        val answers = week.sumOf { it.answers }
        val correct = week.sumOf { it.correct }
        findViewById<TextView>(R.id.tvActivitySummary).text =
            if (answers == 0) getString(R.string.stats_no_activity)
            else "Últimos 7 días: ${week.sumOf { it.xp }} XP · $answers respuestas · " +
                "${correct * 100 / answers} % correctas · ${week.count { it.answers > 0 }} días activos"

        renderAchievements(data.achievements)

        val list = findViewById<LinearLayout>(R.id.strugglingList)
        list.removeAllViews()
        if (data.struggling.isEmpty()) {
            list.addView(text(getString(R.string.stats_no_struggling), R.color.color_text_secondary))
        } else data.struggling.forEach { w ->
            list.addView(text("${w.english} — ${w.spanish}", R.color.color_text_primary, bold = true))
            list.addView(text("${w.incorrectCount} fallos · ${w.correctCount} aciertos · olvidada ${w.lapses} veces",
                R.color.color_text_hint, small = true))
        }
        findViewById<Button>(R.id.btnPracticeStruggling).visibility =
            if (d.dueReviews > 0) View.VISIBLE else View.GONE
    }

    private fun renderTiles(tiles: List<Pair<String, String>>) {
        val grid = findViewById<LinearLayout>(R.id.statsGrid)
        grid.removeAllViews()
        tiles.chunked(2).forEach { row ->
            val line = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            row.forEach { (value, label) ->
                val tile = LayoutInflater.from(this).inflate(R.layout.item_stat_tile, line, false)
                tile.findViewById<TextView>(R.id.tvTileValue).text = value
                tile.findViewById<TextView>(R.id.tvTileLabel).text = label
                tile.contentDescription = "$label: $value"
                line.addView(tile)
            }
            grid.addView(line)
        }
    }

    private fun renderAchievements(items: List<AchievementStatus>) {
        val list = findViewById<LinearLayout>(R.id.achievementsList)
        list.removeAllViews()
        if (items.isEmpty()) {
            list.addView(text(getString(R.string.stats_no_achievements), R.color.color_text_secondary))
            return
        }
        items.forEach { a ->
            val v = LayoutInflater.from(this).inflate(R.layout.item_achievement, list, false)
            v.findViewById<TextView>(R.id.tvAchIcon).text = if (a.unlocked) "🏆" else "🔒"
            v.findViewById<TextView>(R.id.tvAchTitle).text =
                if (a.xpReward > 0) "${a.title}  (+${a.xpReward} XP)" else a.title
            v.findViewById<TextView>(R.id.tvAchDescription).text = a.description
            v.findViewById<ProgressBar>(R.id.progressAch).progress = a.percent
            v.findViewById<TextView>(R.id.tvAchProgress).text =
                if (a.unlocked) getString(R.string.stats_unlocked) else "${a.currentValue} / ${a.threshold}"
            v.alpha = if (a.unlocked) 1f else 0.75f
            v.contentDescription = "${a.title}. ${a.description}. " +
                if (a.unlocked) getString(R.string.stats_unlocked) else "Avance ${a.currentValue} de ${a.threshold}"
            list.addView(v)
        }
    }

    private fun text(value: String, color: Int, bold: Boolean = false, small: Boolean = false) =
        TextView(this).apply {
            text = value
            setTextColor(ContextCompat.getColor(this@StatsActivity, color))
            textSize = if (small) 12f else 14f
            if (bold) setTypeface(typeface, android.graphics.Typeface.BOLD)
            setPadding(0, if (small) 0 else (6 * resources.displayMetrics.density).toInt(), 0, 0)
        }
}
