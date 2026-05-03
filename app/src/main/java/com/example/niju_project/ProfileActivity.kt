package com.example.niju_project

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.niju_project.ui.profile.ProfileUiState
import com.example.niju_project.ui.profile.ProfileViewModel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import androidx.core.content.ContextCompat

class ProfileActivity : AppCompatActivity() {

    private val viewModel: ProfileViewModel by viewModels()

    private lateinit var userNameTextView: TextView
    private lateinit var userEmailTextView: TextView
    private lateinit var tv2FAStatus: TextView
    private lateinit var tvStreak: TextView
    private lateinit var tvXp: TextView
    private lateinit var tvLevel: TextView
    private lateinit var btnLogout: LinearLayout
    private lateinit var optionSetup2FA: LinearLayout
    private lateinit var progressBar: ProgressBar

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_profile)

        initViews()
        setupListeners()
        observeViewModel()
    }

    private fun initViews() {
        userNameTextView  = findViewById(R.id.user_name)
        userEmailTextView = findViewById(R.id.user_location)
        tv2FAStatus       = findViewById(R.id.tv2fa_status)
        btnLogout         = findViewById(R.id.btnLogout)
        optionSetup2FA    = findViewById(R.id.option_setup_2fa)
        progressBar       = findViewById(R.id.progressBar)

        // 🟡 FIX: campos gamificación — si no existen en el layout se ignoran con seguridad
        tvStreak = findViewById<TextView?>(R.id.tvStreak) ?: TextView(this)
        tvXp     = findViewById<TextView?>(R.id.tvXP)     ?: TextView(this)
        tvLevel  = findViewById<TextView?>(R.id.tvLevel)  ?: TextView(this)
    }

    private fun setupListeners() {
        findViewById<ImageButton>(R.id.back_button).setOnClickListener { finish() }

        btnLogout.setOnClickListener {
            viewModel.logout()
            startActivity(Intent(this, LoginActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            })
            finish()
        }

        optionSetup2FA.setOnClickListener {
            val state = viewModel.uiState.value
            if (state is ProfileUiState.Success) {
                startActivity(Intent(this, TwoFactorActivity::class.java).apply {
                    putExtra("mode", "setup")
                    putExtra("totp_secret", state.user.totpSecret ?: "")
                    putExtra("user_email", state.user.email)
                })
            }
        }

        // Opción Favoritos
        findViewById<LinearLayout>(R.id.option_fav).setOnClickListener {
            startActivity(Intent(this, FavoritesActivity::class.java))
        }

        // Opción Configuración
        findViewById<LinearLayout>(R.id.option_config).setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        // Bottom navigation
        val navHome     = findViewById<LinearLayout>(R.id.navHome)
        val navContexts = findViewById<LinearLayout>(R.id.navContexts)
        val navRuta     = findViewById<LinearLayout>(R.id.navRuta)

        navHome.setOnClickListener {
            startActivity(Intent(this, HomeActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            })
        }
        navContexts.setOnClickListener {
            startActivity(Intent(this, ContextsActivity::class.java))
            finish()
        }
        navRuta.setOnClickListener {
            startActivity(Intent(this, RutaActivity::class.java))
            finish()
        }
        // navProfile ya está activo; resaltar icono actual
        updateBottomNavColors(
            current = findViewById(R.id.navProfile),
            navHome, navContexts, navRuta, findViewById(R.id.navProfile)
        )
    }

    private fun observeViewModel() {
        lifecycleScope.launch {
            viewModel.uiState.collectLatest { state ->
                when (state) {
                    is ProfileUiState.Loading -> progressBar.visibility = View.VISIBLE
                    is ProfileUiState.Success -> {
                        progressBar.visibility = View.GONE
                        val user = state.user
                        userNameTextView.text  = user.name ?: "Usuario"
                        userEmailTextView.text = user.email ?: "Sin email"

                        // 🟡 FIX: mostrar datos de gamificación
                        tvStreak.text = "🔥 ${user.streak} días"
                        tvXp.text     = "⭐ ${user.xp} XP"
                        tvLevel.text  = "Nivel ${user.level}"

                        tv2FAStatus.text = if (user.twoFactorEnabled) "2FA: Activo ✓" else "2FA: Inactivo"
                        tv2FAStatus.setTextColor(
                            if (user.twoFactorEnabled)
                                ContextCompat.getColor(this@ProfileActivity, android.R.color.holo_green_dark)
                            else
                                ContextCompat.getColor(this@ProfileActivity, android.R.color.holo_orange_dark)
                        )
                    }
                    is ProfileUiState.Error -> {
                        progressBar.visibility = View.GONE
                        Toast.makeText(this@ProfileActivity, state.message, Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // El ViewModel carga el perfil en init{} y se mantiene durante el ciclo de vida.
        // Solo recargamos si el estado actual es Error para permitir reintento.
        if (viewModel.uiState.value is ProfileUiState.Error) {
            viewModel.loadUserProfile()
        }
    }
}
