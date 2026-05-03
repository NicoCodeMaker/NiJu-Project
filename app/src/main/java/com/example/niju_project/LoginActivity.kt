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
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.firestore.FirebaseFirestore
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

    private lateinit var mAuth:             FirebaseAuth
    private lateinit var credentialManager: CredentialManager
    private var isPasswordVisible = false

    companion object {
        private const val TAG = "LoginActivity"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        mAuth             = FirebaseAuth.getInstance()
        credentialManager = CredentialManager.create(this)

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
        btnLogin.setOnClickListener    { loginWithEmail() }
        btnRegister.setOnClickListener { startActivity(Intent(this, RegisterActivity::class.java)) }
        btnGoogle.setOnClickListener   { signInWithGoogle() }
        btnPhone.setOnClickListener    { startActivity(Intent(this, PhoneAuthActivity::class.java)) }
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

    // ── Google Sign-In con Credential Manager (API moderna) ──────────────────
    private fun signInWithGoogle() {
        showLoading(true)

        // Construir la opción de Google ID con el web client ID (type 3 en google-services.json)
        val googleIdOption = GetGoogleIdOption.Builder()
            .setFilterByAuthorizedAccounts(false)   // false = mostrar TODAS las cuentas Google
            .setServerClientId(getString(R.string.default_web_client_id))
            .setAutoSelectEnabled(false)            // false = siempre mostrar el picker
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
                        mAuth.signInWithCredential(firebaseCredential)
                            .addOnCompleteListener { task ->
                                if (task.isSuccessful) {
                                    checkTwoFactorAndProceed()
                                } else {
                                    showLoading(false)
                                    toast("Error autenticando con Google")
                                }
                            }
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

    // ── Verificar 2FA y crear doc en Firestore si no existe ─────────────────
    private fun checkTwoFactorAndProceed() {
        val user = mAuth.currentUser
        val uid  = user?.uid ?: run { showLoading(false); return }
        val db   = FirebaseFirestore.getInstance()
        showLoading(true)

        db.collection("users").document(uid)
            .get()
            .addOnSuccessListener { doc ->
                if (!doc.exists()) {
                    // Primer login con Google: crear documento en Firestore
                    val newUser = hashMapOf(
                        "uid"              to uid,
                        "email"            to (user.email ?: ""),
                        "name"             to (user.displayName ?: ""),
                        "photoUrl"         to (user.photoUrl?.toString() ?: ""),
                        "twoFactorEnabled" to false,
                        "totpSecret"       to "",
                        "xp"               to 0,
                        "level"            to 1,
                        "streak"           to 0,
                        "dailyGoal"        to 50,
                        "lastActiveAt"     to null
                    )
                    db.collection("users").document(uid)
                        .set(newUser)
                        .addOnSuccessListener { showLoading(false); goToMain() }
                        .addOnFailureListener {
                            showLoading(false)
                            toast("Error creando usuario")
                            mAuth.signOut()
                        }
                } else {
                    val twoFAEnabled = doc.getBoolean("twoFactorEnabled") ?: false
                    val totpSecret   = doc.getString("totpSecret") ?: ""
                    showLoading(false)

                    if (twoFAEnabled && totpSecret.isNotEmpty()) {
                        startActivity(Intent(this, TwoFactorActivity::class.java).apply {
                            putExtra("mode", "verify")
                            putExtra("totp_secret", totpSecret)
                            putExtra("user_email", user.email ?: "")
                        })
                    } else {
                        goToMain()
                    }
                }
            }
            .addOnFailureListener {
                showLoading(false)
                toast("Error validando usuario")
                mAuth.signOut()
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
            etEmail.error = "Correo inválido"; return false
        }
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
