package com.example.niju_project

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.niju_project.data.remote.NijuErrors
import com.example.niju_project.data.remote.SupabaseProvider
import com.example.niju_project.data.repository.AuthLinks
import com.example.niju_project.data.repository.AuthRepository
import com.example.niju_project.data.repository.ProfileRepository
import com.example.niju_project.domain.auth.AuthValidation
import com.example.niju_project.ui.auth.ResetPasswordActivity
import com.example.niju_project.ui.check
import com.example.niju_project.ui.hideKeyboard
import com.example.niju_project.ui.shake
import com.example.niju_project.ui.showLegalDialog
import com.google.android.material.button.MaterialButton
import com.google.android.material.progressindicator.LinearProgressIndicator
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import io.github.jan.supabase.auth.status.SessionStatus
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.example.niju_project.ui.scene.ContextSceneView
import java.util.TimeZone

/**
 * Inicio de sesión: correo y contraseña, Microsoft y (si está habilitado) Google.
 *
 * También recibe niju://login-callback (intent-filter en el manifiesto): vuelta del
 * navegador tras Microsoft/Google, confirmación de correo y enlace de recuperación.
 */
class LoginActivity : AppCompatActivity() {

    private lateinit var tilEmail: TextInputLayout
    private lateinit var tilPassword: TextInputLayout
    private lateinit var etEmail: TextInputEditText
    private lateinit var etPassword: TextInputEditText
    private lateinit var btnLogin: MaterialButton
    private lateinit var btnMicrosoft: MaterialButton
    private lateinit var btnGoogle: MaterialButton
    private lateinit var progressBar: LinearProgressIndicator

    private var auth: AuthRepository? = null
    private var waitingForBrowser = false
    private var working = false
    private var navigated = false
    /** El enlace abierto era de recuperación: al tener sesión se pide la contraseña nueva. */
    private var recoveryPending = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)
        startSceneShowcase()

        tilEmail = findViewById(R.id.tilEmail)
        tilPassword = findViewById(R.id.tilPassword)
        etEmail = findViewById(R.id.etEmail)
        etPassword = findViewById(R.id.etPassword)
        btnLogin = findViewById(R.id.btnLogin)
        btnMicrosoft = findViewById(R.id.btnMicrosoft)
        btnGoogle = findViewById(R.id.btnGoogle)
        progressBar = findViewById(R.id.progressBar)

        btnGoogle.visibility = if (BuildConfig.AUTH_GOOGLE_ENABLED) View.VISIBLE else View.GONE
        findViewById<TextView>(R.id.tvTerms).setOnClickListener { showLegalDialog() }
        intent.getStringExtra(EXTRA_EMAIL)?.let { etEmail.setText(it) }

        if (!SupabaseProvider.isConfigured) {
            findViewById<View>(R.id.tvConfigWarning).visibility = View.VISIBLE
            listOf(btnLogin, btnMicrosoft, btnGoogle).forEach { it.isEnabled = false }
            return
        }

        auth = AuthRepository().also { repo ->
            handleLink(repo, intent)
            observeSession(repo)
        }

        btnLogin.setOnClickListener { signInWithEmail() }
        etPassword.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) { signInWithEmail(); true } else false
        }
        btnMicrosoft.setOnClickListener { signInOAuth { it.signInWithMicrosoft() } }
        btnGoogle.setOnClickListener { signInOAuth { it.signInWithGoogle() } }
        findViewById<View>(R.id.btnForgot).setOnClickListener {
            startActivity(Intent(this, com.example.niju_project.ui.auth.ForgotPasswordActivity::class.java)
                .putExtra(EXTRA_EMAIL, etEmail.text?.toString().orEmpty()))
        }
        findViewById<View>(R.id.btnGoRegister).setOnClickListener {
            startActivity(Intent(this, com.example.niju_project.ui.auth.RegisterActivity::class.java))
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        auth?.let { handleLink(it, intent) }
    }

    override fun onResume() {
        super.onResume()
        // Si el usuario cerró el navegador sin terminar, se reactivan los botones.
        if (waitingForBrowser && auth?.isLoggedIn() != true) {
            waitingForBrowser = false
            showLoading(false)
        }
    }

    /** Enlace de vuelta (OAuth, confirmación de correo o recuperación). */
    private fun handleLink(repo: AuthRepository, intent: Intent) {
        val url = intent.data?.toString() ?: return
        AuthLinks.errorDescription(url)?.let { toast(it); return }
        if (AuthLinks.isRecovery(url)) recoveryPending = true
        repo.handleRedirect(intent) { recoveryPending = true }
    }

    private fun signInWithEmail() {
        val repo = auth ?: return
        if (working) return
        val email = etEmail.text?.toString().orEmpty()
        val password = etPassword.text?.toString().orEmpty()
        val ok = tilEmail.check(AuthValidation.email(email)) and
            tilPassword.check(AuthValidation.loginPassword(password))
        if (!ok) { findViewById<View>(R.id.formContainer).shake(); return }
        hideKeyboard()
        showLoading(true)
        lifecycleScope.launch {
            repo.signInWithEmail(email, password)
                .onFailure {
                    Log.w(TAG, "Inicio de sesión con correo fallido", it)
                    showLoading(false)
                    val msg = NijuErrors.messageFor(it)
                    if (NijuErrors.hasCode(it, "invalid_credentials") || NijuErrors.hasCode(it, "Invalid login credentials")) {
                        tilPassword.check(msg)
                    } else toast(msg)
                }
            // Si funciona, sessionStatus pasa a Authenticated y observeSession navega.
        }
    }

    private fun signInOAuth(action: suspend (AuthRepository) -> Result<Unit>) {
        val repo = auth ?: return
        if (working) return
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
            val target = if (recoveryPending) ResetPasswordActivity::class.java else HomeActivity::class.java
            startActivity(Intent(this@LoginActivity, target).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            })
            finish()
        }
    }

    private fun showLoading(show: Boolean) {
        working = show
        progressBar.visibility = if (show) View.VISIBLE else View.GONE
        listOf(btnLogin, btnMicrosoft, btnGoogle).forEach { it.isEnabled = !show }
    }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_LONG).show()


    /** Vitrina de bienvenida: cada 2,6 s cambia a la escena animada de otro contexto. */
    private fun startSceneShowcase() {
        val scene = findViewById<ContextSceneView>(R.id.sceneLogin)
        val slugs = listOf("beach", "airport", "restaurant", "school", "transport", "travel",
            "health", "technology", "supermarket", "home", "shopping", "work")
        scene.slug = slugs.first()
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                var i = 0
                while (true) {
                    delay(2600)
                    i = (i + 1) % slugs.size
                    scene.animate().alpha(0f).setDuration(180).withEndAction {
                        scene.slug = slugs[i]
                        scene.animate().alpha(1f).setDuration(220).start()
                    }.start()
                }
            }
        }
    }

    companion object {
        private const val TAG = "LoginActivity"
        const val EXTRA_EMAIL = "email"
    }
}
