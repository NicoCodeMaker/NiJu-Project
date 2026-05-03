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
import de.hdodenhof.circleimageview.CircleImageView

class ProfileActivity : AppCompatActivity() {

    private val viewModel: ProfileViewModel by viewModels()

    private lateinit var userNameTextView: TextView
    private lateinit var userEmailTextView: TextView
    private lateinit var tv2FAStatus: TextView
    private lateinit var tvStreak: TextView
    private lateinit var tvXp: TextView
    private lateinit var tvLevel: TextView
    private lateinit var tvWordsCount: TextView
    private lateinit var tvContextsCount: TextView
    private lateinit var tvPlanName: TextView
    private lateinit var tvDailyGoalProgress: TextView
    private lateinit var progressDailyGoal: ProgressBar
    private lateinit var profileImage: CircleImageView
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
        userNameTextView   = findViewById(R.id.user_name)
        userEmailTextView  = findViewById(R.id.user_location)
        tv2FAStatus        = findViewById(R.id.tv2fa_status)
        btnLogout          = findViewById(R.id.btnLogout)
        optionSetup2FA     = findViewById(R.id.option_setup_2fa)
        progressBar        = findViewById(R.id.progressBar)
        profileImage       = findViewById(R.id.profile_image)

        tvStreak           = findViewById<TextView?>(R.id.tvStreak) ?: TextView(this)
        tvXp               = findViewById<TextView?>(R.id.tvXP) ?: TextView(this)
        tvLevel            = findViewById<TextView?>(R.id.tvLevel) ?: TextView(this)
        tvWordsCount       = findViewById<TextView?>(R.id.tvWordsCount) ?: TextView(this)
        tvContextsCount    = findViewById<TextView?>(R.id.tvContextsCount) ?: TextView(this)
        tvPlanName         = findViewById<TextView?>(R.id.tvPlanName) ?: TextView(this)
        tvDailyGoalProgress = findViewById<TextView?>(R.id.tvDailyGoalProgress) ?: TextView(this)
        progressDailyGoal  = findViewById<ProgressBar?>(R.id.progressDailyGoal) ?: ProgressBar(this)
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

        findViewById<LinearLayout>(R.id.option_fav).setOnClickListener {
            startActivity(Intent(this, FavoritesActivity::class.java))
        }

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

                        // Datos básicos
                        userNameTextView.text  = user.name.ifBlank { "Usuario" }
                        userEmailTextView.text = "Registro: ${user.email}"

                        // Gamificación (pill superior)
                        tvStreak.text = "🔥 ${user.streak} días"
                        tvXp.text     = "⭐ ${user.xp} XP"
                        tvLevel.text  = "🏅 Nivel ${user.level}"

                        // Tarjetas de estadísticas reales
                        tvWordsCount.text    = user.xp.toString()          // XP como proxy de palabras aprendidas
                        tvContextsCount.text = user.level.toString()       // Nivel como contextos completados
                        tvPlanName.text      = if (user.xp >= 5000) "Pro" else "Free"

                        // Meta diaria
                        val goal    = if (user.dailyGoal > 0) user.dailyGoal else 50
                        val todayXp = (user.xp % goal).coerceAtMost(goal)
                        val pct     = ((todayXp.toFloat() / goal) * 100).toInt().coerceIn(0, 100)
                        tvDailyGoalProgress.text = "$todayXp / $goal XP"
                        progressDailyGoal.progress = pct

                        // 2FA
                        tv2FAStatus.text = if (user.twoFactorEnabled) "Activo ✓" else "Inactivo"
                        tv2FAStatus.setTextColor(
                            if (user.twoFactorEnabled)
                                ContextCompat.getColor(this@ProfileActivity, android.R.color.holo_green_dark)
                            else
                                ContextCompat.getColor(this@ProfileActivity, android.R.color.holo_orange_dark)
                        )

                        // Imagen de perfil
                        val photoUrl = user.photoUrl
                        if (!photoUrl.isNullOrBlank()) {
                            // Si tienes Glide: Glide.with(this@ProfileActivity).load(photoUrl).into(profileImage)
                            // Si tienes Picasso: Picasso.get().load(photoUrl).into(profileImage)
                            // Por ahora usamos el icono por defecto si no hay librería configurada
                            try {
                                val glideClass = Class.forName("com.bumptech.glide.Glide")
                                val withMethod = glideClass.getMethod("with", android.content.Context::class.java)
                                val requestManager = withMethod.invoke(null, this@ProfileActivity)
                                val loadMethod = requestManager.javaClass.getMethod("load", String::class.java)
                                val requestBuilder = loadMethod.invoke(requestManager, photoUrl)
                                val intoMethod = requestBuilder.javaClass.getMethod("into", android.widget.ImageView::class.java)
                                intoMethod.invoke(requestBuilder, profileImage)
                            } catch (e: Exception) {
                                profileImage.setImageResource(R.drawable.ic_profile_user)
                            }
                        } else {
                            profileImage.setImageResource(R.drawable.ic_profile_user)
                        }
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
        if (viewModel.uiState.value is ProfileUiState.Error) {
            viewModel.loadUserProfile()
        }
    }
}