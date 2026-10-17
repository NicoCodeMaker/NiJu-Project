package com.example.niju_project

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.niju_project.data.remote.SupabaseProvider
import com.example.niju_project.data.repository.AuthRepository
import com.example.niju_project.data.repository.ProfileRepository
import io.github.jan.supabase.auth.status.SessionStatus
import kotlinx.coroutines.launch
import java.util.TimeZone

/**
 * Inicio de sesión con Microsoft (y Google si está habilitado).
 *
 * Abre el navegador; al terminar, Supabase vuelve a esta pantalla mediante
 * niju://login-callback (ver intent-filter en el AndroidManifest).
 */
class LoginActivity : AppCompatActivity() {

    private lateinit var btnMicrosoft: Button
    private lateinit var btnGoogle: Button
    private lateinit var progressBar: ProgressBar
    private lateinit var tvConfigWarning: TextView

    private var auth: AuthRepository? = null
    private var waitingForBrowser = false
    private var navigated = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        btnMicrosoft = findViewById(R.id.btnMicrosoft)
        btnGoogle = findViewById(R.id.btnGoogle)
        progressBar = findViewById(R.id.progressBar)
        tvConfigWarning = findViewById(R.id.tvConfigWarning)

        btnGoogle.visibility = if (BuildConfig.AUTH_GOOGLE_ENABLED) View.VISIBLE else View.GONE

        if (!SupabaseProvider.isConfigured) {
            tvConfigWarning.visibility = View.VISIBLE
            btnMicrosoft.isEnabled = false
            btnGoogle.isEnabled = false
            return
        }

        auth = AuthRepository().also { repo ->
            repo.handleRedirect(intent)
            observeSession(repo)
        }

        btnMicrosoft.setOnClickListener { signIn { it.signInWithMicrosoft() } }
        btnGoogle.setOnClickListener { signIn { it.signInWithGoogle() } }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        auth?.handleRedirect(intent)
    }

    override fun onResume() {
        super.onResume()
        // Si el usuario cerró el navegador sin terminar, se reactivan los botones.
        if (waitingForBrowser && auth?.isLoggedIn() != true) {
            waitingForBrowser = false
            showLoading(false)
        }
    }

    private fun signIn(action: suspend (AuthRepository) -> Result<Unit>) {
        val repo = auth ?: return
        showLoading(true)
        lifecycleScope.launch {
            action(repo)
                .onSuccess { waitingForBrowser = true }
                .onFailure {
                    Log.e(TAG, "Inicio de sesión fallido", it)
                    showLoading(false)
                    toast(getString(R.string.login_error))
                }
        }
    }

    private fun observeSession(repo: AuthRepository) {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                repo.sessionStatus.collect { status ->
                    when (status) {
                        is SessionStatus.Authenticated -> onSignedIn()
                        is SessionStatus.Initializing -> showLoading(true)
                        else -> if (!waitingForBrowser) showLoading(false)
                    }
                }
            }
        }
    }

    private fun onSignedIn() {
        if (navigated) return
        navigated = true
        lifecycleScope.launch {
            // Zona horaria del teléfono → la racha se calcula con días locales.
            ProfileRepository().syncTimezone(TimeZone.getDefault().id)
            startActivity(Intent(this@LoginActivity, HomeActivity::class.java))
            finish()
        }
    }

    private fun showLoading(show: Boolean) {
        progressBar.visibility = if (show) View.VISIBLE else View.GONE
        btnMicrosoft.isEnabled = !show
        btnGoogle.isEnabled = !show
    }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_LONG).show()

    private companion object {
        const val TAG = "LoginActivity"
    }
}
