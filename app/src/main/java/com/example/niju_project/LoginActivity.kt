package com.example.niju_project

import android.content.Intent
import android.os.Bundle
import android.text.InputType
import android.util.Log
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.GetCredentialResponse
import androidx.credentials.exceptions.GetCredentialException
import androidx.lifecycle.lifecycleScope
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenParsingException
import androidx.activity.viewModels
import com.example.niju_project.ui.LoginState
import com.example.niju_project.ui.LoginViewModel
import com.google.firebase.auth.GoogleAuthProvider
import kotlinx.coroutines.launch

class LoginActivity : AppCompatActivity() {

    private lateinit var etEmail:          EditText
    private lateinit var etPassword:       EditText
    private lateinit var ivTogglePassword: ImageView
    private lateinit var progressBar:      ProgressBar
    private lateinit var btnLogin:         Button
    private lateinit var btnRegister:      Button
    private lateinit var btnGoogle:        Button
    private lateinit var btnPhone:         Button
    private lateinit var tvForgotPassword: TextView

    private val viewModel: LoginViewModel by viewModels()
    private lateinit var credentialManager: CredentialManager
    private var isPasswordVisible = false

    companion object {
        private const val TAG = "LoginActivity"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        credentialManager = CredentialManager.create(this)

        bindViews()
        setupListeners()
        setupObservers()

        viewModel.checkCurrentSession()
    }

    private fun setupObservers() {
        viewModel.loginState.observe(this) { state ->
            when (state) {
                is LoginState.Loading -> showLoading(true)
                is LoginState.Success -> {
                    showLoading(false)
                    goToMain()
                }
                is LoginState.Require2FA -> {
                    showLoading(false)
                    startActivity(Intent(this, TwoFactorActivity::class.java).apply {
                        putExtra("mode", "verify")
                        putExtra("totp_secret", state.secret)
                        putExtra("user_email", state.email)
                    })
                }
                is LoginState.Error -> {
                    showLoading(false)
                    toast(state.message)
                }
                else -> showLoading(false)
            }
        }
    }

    private fun bindViews() {
        etEmail          = findViewById(R.id.etEmail)
        etPassword       = findViewById(R.id.etPassword)
        ivTogglePassword = findViewById(R.id.ivTogglePassword)
        progressBar      = findViewById(R.id.progressBar)
        btnLogin         = findViewById(R.id.btnLogin)
        btnRegister      = findViewById(R.id.btnRegister)
        btnGoogle        = findViewById(R.id.btnGoogle)
        btnPhone         = findViewById(R.id.btnPhone)
        tvForgotPassword = findViewById(R.id.tvForgotPassword)
    }

    private fun setupListeners() {
        ivTogglePassword.setOnClickListener {
            isPasswordVisible = !isPasswordVisible
            etPassword.inputType = if (isPasswordVisible)
                InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
            else
                InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            ivTogglePassword.setImageResource(
                if (isPasswordVisible) R.drawable.ic_eye_open else R.drawable.ic_eye_closed
            )
            etPassword.setSelection(etPassword.text.length)
        }
        btnLogin.setOnClickListener    { loginWithEmail() }
        btnRegister.setOnClickListener { startActivity(Intent(this, RegisterActivity::class.java)) }
        btnGoogle.setOnClickListener   { signInWithGoogle() }
        btnPhone.setOnClickListener    { startActivity(Intent(this, PhoneAuthActivity::class.java)) }
        tvForgotPassword.setOnClickListener { sendPasswordReset() }
    }

    // ── Email / Password ─────────────────────────────────────────────────────
    private fun loginWithEmail() {
        viewModel.loginWithEmail(
            etEmail.text.toString().trim(),
            etPassword.text.toString().trim()
        )
    }

    // ── Google Sign-In con Credential Manager (API moderna) ──────────────────
    private fun signInWithGoogle() {
        showLoading(true)

        val googleIdOption = GetGoogleIdOption.Builder()
            .setFilterByAuthorizedAccounts(false)
            .setServerClientId(getString(R.string.default_web_client_id))
            .setAutoSelectEnabled(false)
            .build()

        val request = GetCredentialRequest.Builder()
            .addCredentialOption(googleIdOption)
            .build()

        lifecycleScope.launch {
            try {
                val result: GetCredentialResponse = credentialManager.getCredential(
                    request = request,
                    context = this@LoginActivity
                )
                handleGoogleCredential(result)
            } catch (e: GetCredentialException) {
                showLoading(false)
                Log.e(TAG, "Google Sign-In failed: ${e.type} — ${e.message}")
                toast("Error Google: ${e.message}")
            }
        }
    }

    private fun handleGoogleCredential(result: GetCredentialResponse) {
        when (val credential = result.credential) {
            is CustomCredential -> {
                if (credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                    try {
                        val googleIdToken = GoogleIdTokenCredential
                            .createFrom(credential.data)
                            .idToken

                        val firebaseCredential = GoogleAuthProvider.getCredential(googleIdToken, null)
                        viewModel.loginWithCredential(firebaseCredential)
                    } catch (e: GoogleIdTokenParsingException) {
                        showLoading(false)
                        Log.e(TAG, "Invalid Google ID token", e)
                        toast("Token de Google inválido")
                    }
                } else {
                    showLoading(false)
                    Log.w(TAG, "Credential type not supported: ${credential.type}")
                    toast("Tipo de credencial no soportado")
                }
            }
            else -> {
                showLoading(false)
                Log.w(TAG, "Unexpected credential type")
                toast("Credencial inesperada")
            }
        }
    }

    // ── Recuperar contraseña ─────────────────────────────────────────────────
    private fun sendPasswordReset() {
        val email = etEmail.text.toString().trim()
        if (email.isEmpty()) { etEmail.error = "Ingresa tu correo primero"; return }
        viewModel.sendPasswordReset(email)
        toast("Si el correo existe, se enviarán instrucciones")
    }

    // ── Helpers ──────────────────────────────────────────────────────────────
    private fun showLoading(show: Boolean) {
        progressBar.visibility = if (show) View.VISIBLE else View.GONE
        btnLogin.isEnabled     = !show
        btnGoogle.isEnabled    = !show
        btnPhone.isEnabled     = !show
    }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_LONG).show()

    private fun goToMain() {
        startActivity(Intent(this, HomeActivity::class.java))
        finish()
    }
}
