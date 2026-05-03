package com.example.niju_project

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.niju_project.ui.home.HomeUiState
import com.example.niju_project.ui.home.HomeViewModel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class HomeActivity : AppCompatActivity() {

    private val viewModel: HomeViewModel by viewModels()

    private lateinit var tvGreeting: TextView
    private lateinit var tvStreak: TextView
    private lateinit var tvXP: TextView
    private lateinit var btnStartSession: Button
    
    private lateinit var navHome: LinearLayout
    private lateinit var navContexts: LinearLayout
    private lateinit var navRuta: LinearLayout
    private lateinit var navProfile: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_home)

        initViews()
        setupClickListeners()
        observeViewModel()
    }

    private fun initViews() {
        tvGreeting = findViewById(R.id.tvGreeting)
        tvStreak = findViewById(R.id.tvStreak)
        tvXP = findViewById(R.id.tvXP)
        btnStartSession = findViewById(R.id.btnStartSession)
        
        navHome = findViewById(R.id.navHome)
        navContexts = findViewById(R.id.navContexts)
        navRuta = findViewById(R.id.navRuta)
        navProfile = findViewById(R.id.navProfile)
    }

    private fun observeViewModel() {
        lifecycleScope.launch {
            viewModel.uiState.collectLatest { state ->
                when (state) {
                    is HomeUiState.Loading -> { /* Mostrar algun loader si es necesario */ }
                    is HomeUiState.Success -> {
                        val user = state.user
                        tvGreeting.text = "¡Hola, ${user.name}!"
                        tvStreak.text = user.streak.toString()
                        tvXP.text = user.xp.toString()
                    }
                    is HomeUiState.Error -> {
                        Toast.makeText(this@HomeActivity, state.message, Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    private fun setupClickListeners() {
        btnStartSession.setOnClickListener {
            startActivity(Intent(this, PracticeActivity::class.java))
        }

        navContexts.setOnClickListener {
            startActivity(Intent(this, ContextsActivity::class.java))
            finish()
        }

        navRuta.setOnClickListener {
            startActivity(Intent(this, RutaActivity::class.java))
            finish()
        }

        navProfile.setOnClickListener {
            startActivity(Intent(this, ProfileActivity::class.java))
            finish()
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.loadData()
    }
}
