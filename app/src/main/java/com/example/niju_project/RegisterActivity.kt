package com.example.niju_project

import android.content.Intent
import android.os.Bundle
import android.text.InputType
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.example.niju_project.utils.TOTPHelper
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.UserProfileChangeRequest
import com.google.firebase.firestore.FirebaseFirestore

class RegisterActivity : AppCompatActivity() {

    private lateinit var etName:            EditText
    private lateinit var etEmail:           EditText
    private lateinit var etPassword:        EditText
    private lateinit var etConfirmPassword: EditText
    private lateinit var ivTogglePassword:  ImageView
    private lateinit var ivToggleConfirm:   ImageView
    private lateinit var cbTerms:           CheckBox
    private lateinit var btnRegister:       Button
    private lateinit var tvSignIn:          TextView
    private lateinit var progressBar:       ProgressBar

    private lateinit var mAuth: FirebaseAuth
    private val db = FirebaseFirestore.getInstance()
    private var isPasswordVisible = false
    private var isConfirmVisible  = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_register)
        mAuth = FirebaseAuth.getInstance()
        bindViews()
        setupListeners()
    }

    private fun bindViews() {
        etName            = findViewById(R.id.etName)
        etEmail           = findViewById(R.id.etEmail)
        etPassword        = findViewById(R.id.etPassword)
        etConfirmPassword = findViewById(R.id.etConfirmPassword)
        ivTogglePassword  = findViewById(R.id.ivTogglePassword)
        ivToggleConfirm   = findViewById(R.id.ivToggleConfirm)
        cbTerms           = findViewById(R.id.cbTerms)
        btnRegister       = findViewById(R.id.btnRegister)
        tvSignIn          = findViewById(R.id.tvSignIn)
        progressBar       = findViewById(R.id.progressBar)
    }

    private fun setupListeners() {
        ivTogglePassword.setOnClickListener {
            isPasswordVisible = !isPasswordVisible
            etPassword.inputType = if (isPasswordVisible)
                InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
            else InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            ivTogglePassword.setImageResource(
                if (isPasswordVisible) R.drawable.ic_eye_open else R.drawable.ic_eye_closed)
            etPassword.setSelection(etPassword.text.length)
        }
        ivToggleConfirm.setOnClickListener {
            isConfirmVisible = !isConfirmVisible
            etConfirmPassword.inputType = if (isConfirmVisible)
                InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
            else InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            ivToggleConfirm.setImageResource(
                if (isConfirmVisible) R.drawable.ic_eye_open else R.drawable.ic_eye_closed)
            etConfirmPassword.setSelection(etConfirmPassword.text.length)
        }
        btnRegister.setOnClickListener { registerUser() }
        tvSignIn.setOnClickListener    { finish() }
    }

    private fun registerUser() {
        val name     = etName.text.toString().trim()
        val email    = etEmail.text.toString().trim()
        val password = etPassword.text.toString().trim()
        val confirm  = etConfirmPassword.text.toString().trim()

        if (!validateInputs(name, email, password, confirm)) return
        if (!cbTerms.isChecked) {
            Toast.makeText(this, "Debes aceptar los Términos y Condiciones", Toast.LENGTH_SHORT).show()
            return
        }

        showLoading(true)

        mAuth.createUserWithEmailAndPassword(email, password)
            .addOnCompleteListener { task ->
                if (!task.isSuccessful) {
                    showLoading(false)
                    Toast.makeText(this, "Error: ${task.exception?.message}", Toast.LENGTH_LONG).show()
                    return@addOnCompleteListener
                }

                val uid    = mAuth.currentUser?.uid ?: return@addOnCompleteListener
                // Generamos el secret TOTP y lo guardamos en Firestore
                val secret = TOTPHelper.generateSecretKey()

                val userData = hashMapOf(
                    "name"             to name,
                    "email"            to email,
                    "totpSecret"       to secret,
                    "twoFactorEnabled" to false      // se activa cuando el usuario confirma el QR
                )

                db.collection("users").document(uid)
                    .set(userData)
                    .addOnSuccessListener {
                        // Actualizar displayName en Firebase Auth
                        val profileUpdate = UserProfileChangeRequest.Builder()
                            .setDisplayName(name).build()
                        mAuth.currentUser?.updateProfile(profileUpdate)
                            ?.addOnCompleteListener {
                                mAuth.currentUser?.sendEmailVerification()
                                showLoading(false)
                                Toast.makeText(
                                    this,
                                    "¡Cuenta creada! Configura tu 2FA ahora.",
                                    Toast.LENGTH_LONG
                                ).show()
                                // ─── Ir a TwoFactorActivity en modo SETUP ───
                                val intent = Intent(this, TwoFactorActivity::class.java).apply {
                                    putExtra("mode",        "setup")
                                    putExtra("totp_secret", secret)
                                    putExtra("user_email",  email)
                                }
                                startActivity(intent)
                                finishAffinity()
                            }
                    }
                    .addOnFailureListener {
                        showLoading(false)
                        Toast.makeText(this, "Error guardando datos: ${it.message}", Toast.LENGTH_SHORT).show()
                    }
            }
    }

    private fun validateInputs(
        name: String, email: String, password: String, confirm: String
    ): Boolean {
        if (name.length < 2)       { etName.error = "Nombre muy corto"; return false }
        if (email.isEmpty())       { etEmail.error = "Ingresa un correo"; return false }
        if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            etEmail.error = "Correo inválido"; return false }
        if (password.length < 8)   { etPassword.error = "Mínimo 8 caracteres"; return false }
        if (!password.any { it.isDigit() })     { etPassword.error = "Debe tener al menos un número"; return false }
        if (!password.any { it.isUpperCase() }) { etPassword.error = "Debe tener al menos una mayúscula"; return false }
        if (confirm != password)   { etConfirmPassword.error = "Las contraseñas no coinciden"; return false }
        return true
    }

    private fun showLoading(show: Boolean) {
        progressBar.visibility = if (show) View.VISIBLE else View.GONE
        btnRegister.isEnabled  = !show
    }
}