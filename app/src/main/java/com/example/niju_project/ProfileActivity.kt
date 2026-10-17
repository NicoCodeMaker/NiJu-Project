package com.example.niju_project

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ImageButton
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
import com.example.niju_project.data.repository.AuthRepository
import com.example.niju_project.ui.UiState
import com.example.niju_project.ui.home.DashboardViewModel
import kotlinx.coroutines.launch

class ProfileActivity : AppCompatActivity() {

    private val viewModel: DashboardViewModel by viewModels()
    private val auth by lazy { AuthRepository() }

    private lateinit var tvName: TextView
    private lateinit var tvEmail: TextView
    private lateinit var tvStreak: TextView
    private lateinit var tvXp: TextView
    private lateinit var tvLevel: TextView
    private lateinit var tvPracticed: TextView
    private lateinit var tvLessons: TextView
    private lateinit var tvMastered: TextView
    private lateinit var tvDailyGoal: TextView
    private lateinit var progressDailyGoal: ProgressBar
    private lateinit var progressBar: ProgressBar

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_profile)

        tvName = findViewById(R.id.user_name)
        tvEmail = findViewById(R.id.user_location)
        tvStreak = findViewById(R.id.tvStreak)
        tvXp = findViewById(R.id.tvXP)
        tvLevel = findViewById(R.id.tvLevel)
        tvPracticed = findViewById(R.id.tvWordsCount)
        tvLessons = findViewById(R.id.tvContextsCount)
        tvMastered = findViewById(R.id.tvPlanName)
        tvDailyGoal = findViewById(R.id.tvDailyGoalProgress)
        progressDailyGoal = findViewById(R.id.progressDailyGoal)
        progressBar = findViewById(R.id.progressBar)

        findViewById<ImageButton>(R.id.back_button).setOnClickListener { finish() }
        findViewById<LinearLayout>(R.id.option_fav).setOnClickListener {
            startActivity(Intent(this, FavoritesActivity::class.java))
        }
        findViewById<View>(R.id.option_stats).setOnClickListener {
            startActivity(Intent(this, StatsActivity::class.java))
        }
        findViewById<View>(R.id.option_config).setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
        findViewById<View>(R.id.btnLogout).setOnClickListener {
            lifecycleScope.launch {
                auth.signOut()
                goToLogin()
            }
        }

        setupBottomNav(R.id.navProfile)
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
                    progressBar.visibility = if (state is UiState.Loading) View.VISIBLE else View.GONE
                    when (state) {
                        is UiState.Success -> render(state.data)
                        is UiState.Error ->
                            if (state.sessionExpired) goToLogin()
                            else Toast.makeText(this@ProfileActivity, state.message, Toast.LENGTH_SHORT).show()
                        UiState.Loading -> Unit
                    }
                }
            }
        }
    }

    private fun render(d: Dashboard) {
        tvName.text = d.displayName.ifBlank { "Estudiante" }
        tvEmail.text = auth.currentEmail().orEmpty()
        tvLevel.text = "🏅 Nivel ${d.level}"
        tvStreak.text = "🔥 ${d.currentStreak} ${if (d.currentStreak == 1) "día" else "días"}"
        tvXp.text = "⭐ ${d.xpTotal} XP"
        tvPracticed.text = d.wordsPracticed.toString()
        tvLessons.text = d.lessonsCompleted.toString()
        tvMastered.text = d.wordsMastered.toString()
        tvDailyGoal.text = "${d.xpToday} / ${d.dailyGoalXp} XP"
        progressDailyGoal.progress = d.dailyGoalPercent
    }
}
