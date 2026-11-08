package com.example.niju_project

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.niju_project.data.model.ContextProgress
import com.example.niju_project.ui.UiState
import com.example.niju_project.ui.contexts.RutaViewModel
import com.google.android.material.progressindicator.LinearProgressIndicator
import kotlinx.coroutines.launch

/** Ruta de aprendizaje: los contextos en orden con su avance real (get_context_progress). */
class RutaActivity : AppCompatActivity() {

    private val viewModel: RutaViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_ruta)
        findViewById<TextView>(R.id.tvHeaderTitle).setText(R.string.route_title)
        findViewById<View>(R.id.btnBack).setOnClickListener { finish() }
        findViewById<Button>(R.id.btnEmptyAction).setOnClickListener { viewModel.load() }
        setupBottomNav(R.id.navContexts)
        observe()
    }

    override fun onResume() {
        super.onResume()
        viewModel.load()
    }

    private fun observe() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    findViewById<View>(R.id.progressBar).visibility = if (state is UiState.Loading) View.VISIBLE else View.GONE
                    findViewById<View>(R.id.emptyState).visibility = if (state is UiState.Error) View.VISIBLE else View.GONE
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

    private fun render(list: List<ContextProgress>) {
        val lessons = list.sumOf { it.lessonsTotal }
        val done = list.sumOf { it.lessonsCompleted }
        val pct = if (lessons == 0) 0 else done * 100 / lessons
        findViewById<TextView>(R.id.tvPercent).text = getString(R.string.percent_value, pct)
        findViewById<LinearProgressIndicator>(R.id.progressIndicator).setProgressCompat(pct, true)
        findViewById<TextView>(R.id.tvProgreso).text = if (list.isEmpty()) getString(R.string.contexts_empty)
        else getString(R.string.route_summary, done, lessons, list.sumOf { it.wordsMastered }, list.count { it.examPassed })

        val steps = findViewById<LinearLayout>(R.id.stepsList)
        steps.removeAllViews()
        list.forEachIndexed { i, c ->
            val v = LayoutInflater.from(this).inflate(R.layout.item_route_step, steps, false)
            val complete = c.completed
            v.findViewById<TextView>(R.id.tvStepNumber).apply {
                text = if (complete) "✓" else (i + 1).toString()
                setBackgroundResource(if (complete) R.drawable.bg_circle_success else R.drawable.bg_circle_accent_soft)
            }
            v.findViewById<View>(R.id.stepLine).visibility = if (i == list.lastIndex) View.INVISIBLE else View.VISIBLE
            v.findViewById<TextView>(R.id.tvStepEmoji).text = c.emoji ?: contextEmoji(c.slug)
            v.findViewById<TextView>(R.id.tvStepName).text = c.name
            v.findViewById<TextView>(R.id.tvStepMeta).text = getString(R.string.route_step_meta,
                c.lessonsCompleted, c.lessonsTotal, c.wordsMastered, c.wordsTotal)
            v.findViewById<LinearProgressIndicator>(R.id.progressStep).apply {
                setProgressCompat(c.percentComplete.toInt().coerceIn(0, 100), true)
                setIndicatorColor(ContextCompat.getColor(context, if (complete) R.color.color_success else R.color.color_accent))
            }
            v.findViewById<TextView>(R.id.tvStepBadge).text = when {
                c.examPassed -> "🎓"
                c.examUnlocked -> "📝"
                else -> ""
            }
            v.findViewById<View>(R.id.cardStep).apply {
                contentDescription = getString(R.string.route_step_desc, i + 1, c.name, c.lessonsCompleted, c.lessonsTotal,
                    c.wordsMastered, getString(if (c.examPassed) R.string.route_exam_passed else R.string.route_exam_pending))
                setOnClickListener {
                    startActivity(Intent(this@RutaActivity, LessonActivity::class.java).apply {
                        putExtra(LessonActivity.EXTRA_CONTEXT_ID, c.contextId)
                        putExtra(LessonActivity.EXTRA_CONTEXT_SLUG, c.slug)
                        putExtra(LessonActivity.EXTRA_CONTEXT_NAME, c.name)
                        putExtra(LessonActivity.EXTRA_CONTEXT_DESCRIPTION, c.description)
                    })
                }
            }
            steps.addView(v)
        }
    }
}
