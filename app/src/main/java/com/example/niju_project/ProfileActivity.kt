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

class ProfileActivity : AppCompatActivity() {

    private val viewModel: ProfileViewModel by viewModels()

    private lateinit var userNameTextView:   TextView
    private lateinit var userEmailTextView:  TextView
    private lateinit var tv2FAStatus:        TextView
    private lateinit var btnLogout:          LinearLayout
    private lateinit var optionSetup2FA:     LinearLayout
    private lateinit var progressBar:       ProgressBar

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_profile)

        initViews()
        setupListeners()
        observeViewModel()
    }

    private fun initViews() {
        userNameTextView = findViewById(R.id.user_name)
        userEmailTextView = findViewById(R.id.user_location) // Reutilizando id de fecha para email o info
        tv2FAStatus = findViewById(R.id.tv2fa_status)
        btnLogout = findViewById(R.id.btnLogout)
        optionSetup2FA = findViewById(R.id.option_setup_2fa)
        
        // Asumiendo que existe un progressBar en el layout
        progressBar = findViewById(R.id.progressBar) 
    }

    private fun setupListeners() {
        findViewById<ImageButton>(R.id.back_button).setOnClickListener { finish() }
        
        btnLogout.setOnClickListener {
            viewModel.logout()
            val intent = Intent(this, LoginActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
            finish()
        }

        optionSetup2FA.setOnClickListener {
            val state = viewModel.uiState.value
            if (state is ProfileUiState.Success) {
                val intent = Intent(this, TwoFactorActivity::class.java).apply {
                    putExtra("mode", "setup")
                    putExtra("totp_secret", state.user.totpSecret)
                    putExtra("user_email", state.user.email)
                }
                startActivity(intent)
            }
        }
    }

    private fun observeViewModel() {
        lifecycleScope.launch {
            viewModel.uiState.collectLatest { state ->
                when (state) {
                    is ProfileUiState.Loading -> progressBar.visibility = View.VISIBLE
                    is ProfileUiState.Success -> {
                        progressBar.visibility = View.GONE
                        val user = state.user
                        userNameTextView.text = user.name
                        userEmailTextView.text = user.email
                        
                        tv2FAStatus.text = if (user.twoFactorEnabled) "2FA: Activo ✓" else "2FA: Inactivo"
                        tv2FAStatus.setTextColor(
                            if (user.twoFactorEnabled) getColor(android.R.color.holo_green_dark)
                            else getColor(android.R.color.holo_orange_dark)
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
        viewModel.loadUserProfile()
    }
}
