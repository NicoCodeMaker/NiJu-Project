package com.example.niju_project

import android.content.Intent
import android.os.Bundle
import android.text.InputType
import android.util.Log
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.google.android.gms.auth.api.signin.*
import com.google.android.gms.common.api.ApiException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.firestore.FirebaseFirestore

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

    private lateinit var mAuth:            FirebaseAuth
    private lateinit var googleClient:     GoogleSignInClient
    private var isPasswordVisible = false

    companion object {
        private const val RC_SIGN_IN = 9001
        private const val TAG = "LoginActivity"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        mAuth = FirebaseAuth.getInstance()
        setupGoogleSignIn()
        bindViews()
        setupListeners()

        if (mAuth.currentUser != null) checkTwoFactorAndProceed()
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
        btnLogin.setOnClickListener         { loginWithEmail() }
        btnRegister.setOnClickListener      { startActivity(Intent(this, RegisterActivity::class.java)) }
        btnGoogle.setOnClickListener        { signInWithGoogle() }
        btnPhone.setOnClickListener         { startActivity(Intent(this, PhoneAuthActivity::class.java)) }
        tvForgotPassword.setOnClickListener { sendPasswordReset() }
    }

    // ── Email / Password ─────────────────────────────────────────────────────
    private fun loginWithEmail() {
        val email    = etEmail.text.toString().trim()
        val password = etPassword.text.toString().trim()
        if (!validate(email, password)) return
        showLoading(true)

        mAuth.signInWithEmailAndPassword(email, password)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    checkTwoFactorAndProceed()
                } else {
                    showLoading(false)
                    toast("Error: ${task.exception?.message}")
                }
            }
    }

    /**
     * Consulta Firestore para ver si el usuario tiene 2FA activo.
     * Si SÍ  → recupera el secret y abre TwoFactorActivity en modo "verify"
     * Si NO  → va directo al Home
     */
    private fun checkTwoFactorAndProceed() {
        val uid = mAuth.currentUser?.uid ?: run { showLoading(false); return }
        showLoading(true)

        FirebaseFirestore.getInstance()
            .collection("users").document(uid)
            .get()
            .addOnSuccessListener { doc ->
                showLoading(false)
                val twoFaEnabled = doc.getBoolean("twoFactorEnabled") ?: false
                val totpSecret   = doc.getString("totpSecret") ?: ""

                if (twoFaEnabled && totpSecret.isNotEmpty()) {
                    // Abre 2FA en modo VERIFY con el secret real
                    val intent = Intent(this, TwoFactorActivity::class.java).apply {
                        putExtra("mode",         "verify")
                        putExtra("totp_secret",  totpSecret)
                        putExtra("user_email",   mAuth.currentUser?.email ?: "")
                    }
                    startActivity(intent)
                    finish()
                } else {
                    goToMain()
                }
            }
            .addOnFailureListener {
                showLoading(false)
                // Si Firestore falla, igual dejamos entrar (degradado)
                Log.w(TAG, "No se pudo leer Firestore: ${it.message}")
                goToMain()
            }
    }

    // ── Google Sign-In ───────────────────────────────────────────────────────
    private fun setupGoogleSignIn() {
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(getString(R.string.default_web_client_id))
            .requestEmail()
            .build()
        googleClient = GoogleSignIn.getClient(this, gso)
    }

    private fun signInWithGoogle() {
        @Suppress("DEPRECATION")
        startActivityForResult(googleClient.signInIntent, RC_SIGN_IN)
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == RC_SIGN_IN) {
            try {
                val account = GoogleSignIn
                    .getSignedInAccountFromIntent(data)
                    .getResult(ApiException::class.java)
                showLoading(true)
                val credential = GoogleAuthProvider.getCredential(account.idToken!!, null)
                mAuth.signInWithCredential(credential)
                    .addOnCompleteListener { task ->
                        if (task.isSuccessful) checkTwoFactorAndProceed()
                        else { showLoading(false); toast("Google Sign-In fallido") }
                    }
            } catch (e: ApiException) {
                toast("Error Google: ${e.statusCode}")
            }
        }
    }

    // ── Recuperar contraseña ─────────────────────────────────────────────────
    private fun sendPasswordReset() {
        val email = etEmail.text.toString().trim()
        if (email.isEmpty()) { etEmail.error = "Ingresa tu correo primero"; return }
        mAuth.sendPasswordResetEmail(email).addOnCompleteListener { task ->
            if (task.isSuccessful) toast("Correo de recuperación enviado")
            else toast("Error: ${task.exception?.message}")
        }
    }

    // ── Validación ───────────────────────────────────────────────────────────
    private fun validate(email: String, password: String): Boolean {
        if (email.isEmpty()) { etEmail.error = "Ingresa un correo"; return false }
        if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            etEmail.error = "Correo inválido"; return false }
        if (password.isEmpty()) { etPassword.error = "Ingresa una contraseña"; return false }
        if (password.length < 6) { etPassword.error = "Mínimo 6 caracteres"; return false }
        return true
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