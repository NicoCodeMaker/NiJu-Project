package com.example.niju_project

import com.example.niju_project.utils.TOTPHelper
import android.content.Intent
import android.os.Bundle
import android.text.InputType
import android.util.Log
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.tasks.Task
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.PhoneAuthCredential
import com.google.firebase.auth.PhoneAuthOptions
import com.google.firebase.auth.PhoneAuthProvider
import java.util.concurrent.TimeUnit



class LoginActivity : AppCompatActivity() {

    // ─── UI refs ────────────────────────────────────────────────────────────
    private lateinit var etEmail: EditText
    private lateinit var etPassword: EditText
    private lateinit var ivTogglePassword: ImageView
    private lateinit var progressBar: ProgressBar
    private lateinit var btnLogin: Button
    private lateinit var btnRegister: Button
    private lateinit var btnGoogle: Button
    private lateinit var btnPhone: Button
    private lateinit var tvForgotPassword: TextView

    // ─── Auth ────────────────────────────────────────────────────────────────
    private lateinit var mAuth: FirebaseAuth
    private lateinit var googleSignInClient: GoogleSignInClient
    private var isPasswordVisible = false



    companion object {
        private const val RC_SIGN_IN = 9001
        private const val TAG = "LoginActivity"

        // Guarda el verificationId para el flujo de SMS
        var pendingVerificationId: String? = null
    }

    // ────────────────────────────────────────────────────────────────────────
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        mAuth = FirebaseAuth.getInstance()
        setupGoogleSignIn()
        bindViews()
        setupListeners()

        val secret = TOTPHelper.generateSecretKey()
        val code = TOTPHelper.generateCode(secret)

        Log.d("TOTP_TEST", "Secret: $secret")
        Log.d("TOTP_TEST", "Code: $code")

        // Auto-login si ya hay sesión activa
        if (mAuth.currentUser != null) goToMain()
    }

    // ─── Bind views ─────────────────────────────────────────────────────────
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

    // ─── Listeners ──────────────────────────────────────────────────────────
    private fun setupListeners() {
        // Toggle contraseña
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

        btnLogin.setOnClickListener { loginWithEmail() }
        btnRegister.setOnClickListener { openRegister() }
        btnGoogle.setOnClickListener { signInWithGoogle() }
        btnPhone.setOnClickListener { openPhoneAuth() }
        tvForgotPassword.setOnClickListener { sendPasswordReset() }
    }

    // ─── Email / Password ────────────────────────────────────────────────────
    private fun loginWithEmail() {
        val email    = etEmail.text.toString().trim()
        val password = etPassword.text.toString().trim()

        if (!validateInputs(email, password)) return
        showLoading(true)

        mAuth.signInWithEmailAndPassword(email, password)
            .addOnCompleteListener { task ->
                showLoading(false)
                if (task.isSuccessful) {
                    val user = mAuth.currentUser
                    // Si el usuario tiene 2FA habilitado (flag en SharedPreferences),
                    // redirigir a TwoFactorActivity; de lo contrario ir al Home.
                    val prefs = getSharedPreferences("niju_prefs", MODE_PRIVATE)
                    val twoFaEnabled = prefs.getBoolean("2fa_enabled_${user?.uid}", false)
                    if (twoFaEnabled) {
                        openTwoFactor(user?.email ?: "")
                    } else {
                        goToMain()
                    }
                } else {
                    showError("Error: ${task.exception?.message}")
                }
            }
    }

    // ─── Google Sign-In ──────────────────────────────────────────────────────
    private fun setupGoogleSignIn() {
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(getString(R.string.default_web_client_id)) // desde google-services.json
            .requestEmail()
            .build()
        googleSignInClient = GoogleSignIn.getClient(this, gso)
    }

    private fun signInWithGoogle() {
        val signInIntent = googleSignInClient.signInIntent
        @Suppress("DEPRECATION")
        startActivityForResult(signInIntent, RC_SIGN_IN)
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == RC_SIGN_IN) {
            val task: Task<GoogleSignInAccount> = GoogleSignIn.getSignedInAccountFromIntent(data)
            try {
                val account = task.getResult(ApiException::class.java)
                firebaseAuthWithGoogle(account.idToken!!)
            } catch (e: ApiException) {
                Log.w(TAG, "Google sign in failed", e)
                showError("Google Sign-In falló: ${e.statusCode}")
            }
        }
    }

    private fun firebaseAuthWithGoogle(idToken: String) {
        showLoading(true)
        val credential = GoogleAuthProvider.getCredential(idToken, null)
        mAuth.signInWithCredential(credential)
            .addOnCompleteListener(this) { task ->
                showLoading(false)
                if (task.isSuccessful) {
                    goToMain()
                } else {
                    showError("Autenticación con Google fallida")
                }
            }
    }

    // ─── Phone Auth (abre PhoneAuthActivity) ────────────────────────────────
    private fun openPhoneAuth() {
        startActivity(Intent(this, PhoneAuthActivity::class.java))
    }

    // ─── Recuperar contraseña ────────────────────────────────────────────────
    private fun sendPasswordReset() {
        val email = etEmail.text.toString().trim()
        if (email.isEmpty()) {
            etEmail.error = "Ingrese su correo primero"
            return
        }
        mAuth.sendPasswordResetEmail(email)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    Toast.makeText(this, "Correo de recuperación enviado", Toast.LENGTH_LONG).show()
                } else {
                    showError("Error: ${task.exception?.message}")
                }
            }
    }

    // ─── Navegar a registro ──────────────────────────────────────────────────
    private fun openRegister() {
        startActivity(Intent(this, RegisterActivity::class.java))
    }

    // ─── Navegar a 2FA ───────────────────────────────────────────────────────
    private fun openTwoFactor(email: String) {
        val intent = Intent(this, TwoFactorActivity::class.java)
        intent.putExtra("user_email", email)
        startActivity(intent)
    }

    // ─── Validaciones ────────────────────────────────────────────────────────
    private fun validateInputs(email: String, password: String): Boolean {
        if (email.isEmpty()) { etEmail.error = "Ingrese un correo"; return false }
        if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            etEmail.error = "Correo inválido"; return false
        }
        if (password.isEmpty()) { etPassword.error = "Ingrese una contraseña"; return false }
        if (password.length < 6) {
            etPassword.error = "Mínimo 6 caracteres"; return false
        }
        return true
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────
    private fun showLoading(show: Boolean) {
        progressBar.visibility = if (show) View.VISIBLE else View.GONE
        btnLogin.isEnabled    = !show
        btnGoogle.isEnabled   = !show
        btnPhone.isEnabled    = !show
    }

    private fun showError(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }

    private fun goToMain() {
        startActivity(Intent(this, HomeActivity::class.java))
        finish()
    }
}