package com.example.niju_project

import android.content.Intent
import android.os.Bundle
import android.widget.Button
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
import com.example.niju_project.data.model.SessionMode
import com.example.niju_project.ui.UiState
import com.example.niju_project.ui.home.DashboardViewModel
import kotlinx.coroutines.launch

class HomeActivity : AppCompatActivity() {

    private val viewModel: DashboardViewModel by viewModels()

    private lateinit var tvGreeting: TextView
    private lateinit var tvSubtitle: TextView
    private lateinit var tvStreak: TextView
    private lateinit var tvXP: TextView
    private lateinit var tvLevel: TextView
    private lateinit var tvDailyProgress: TextView
    private lateinit var progressDaily: ProgressBar
    private lateinit var btnStartSession: Button

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

        // Con repasos pendientes se repasa; si no, práctica libre de todo el catálogo.
        btnStartSession.setOnClickListener {
            if (dueReviews > 0) openSession(SessionMode.REVIEW, title = "Repaso")
            else openSession(SessionMode.PRACTICE, title = "Práctica libre")
        }
        findViewById<LinearLayout?>(R.id.btnFavoritos)?.setOnClickListener {
            startActivity(Intent(this, FavoritesActivity::class.java))
        }
        findViewById<LinearLayout?>(R.id.btnTrofeos)?.setOnClickListener {
            startActivity(Intent(this, StatsActivity::class.java))
        }
        findViewById<LinearLayout?>(R.id.btnAmigos)?.setOnClickListener { openTab(ContextsActivity::class.java) }

        setupBottomNav(R.id.navHome)
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

    private fun render(d: Dashboard) {
        dueReviews = d.dueReviews
        val name = d.displayName.substringBefore(' ').ifBlank { "estudiante" }
        tvGreeting.text = "¡Hola, $name!"
        tvSubtitle.text = when {
            d.dueReviews > 0 -> "Tienes ${d.dueReviews} ${if (d.dueReviews == 1) "palabra" else "palabras"} por repasar"
            d.wordsPracticed == 0 -> "Empieza tu primera lección en Contextos"
            else -> "¡Estás al día con tus repasos!"
        }
        tvStreak.text = d.currentStreak.toString()
        tvXP.text = d.xpTotal.toString()
        tvLevel.text = d.level.toString()
        tvDailyProgress.text = "${d.xpToday} / ${d.dailyGoalXp} XP"
        progressDaily.progress = d.dailyGoalPercent

        btnStartSession.isEnabled = true
        btnStartSession.text = if (d.dueReviews > 0) "Repasar ahora (${d.dueReviews})" else "Práctica libre"
    }
}
