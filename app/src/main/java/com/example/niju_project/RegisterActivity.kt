package com.example.niju_project
import com.example.niju_project.utils.TOTPHelper

import com.google.firebase.firestore.FirebaseFirestore
import android.content.Intent
import android.os.Bundle
import android.text.InputType
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.UserProfileChangeRequest

class RegisterActivity : AppCompatActivity() {

    private lateinit var etName: EditText
    private lateinit var etEmail: EditText
    private lateinit var etPassword: EditText
    private lateinit var etConfirmPassword: EditText
    private lateinit var ivTogglePassword: ImageView
    private lateinit var ivToggleConfirm: ImageView
    private lateinit var cbTerms: CheckBox
    private lateinit var btnRegister: Button
    private lateinit var tvSignIn: TextView
    private lateinit var progressBar: ProgressBar


    private lateinit var mAuth: FirebaseAuth
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
            else
                InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            ivTogglePassword.setImageResource(
                if (isPasswordVisible) R.drawable.ic_eye_open else R.drawable.ic_eye_closed
            )
            etPassword.setSelection(etPassword.text.length)
        }

        ivToggleConfirm.setOnClickListener {
            isConfirmVisible = !isConfirmVisible
            etConfirmPassword.inputType = if (isConfirmVisible)
                InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
            else
                InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            ivToggleConfirm.setImageResource(
                if (isConfirmVisible) R.drawable.ic_eye_open else R.drawable.ic_eye_closed
            )
            etConfirmPassword.setSelection(etConfirmPassword.text.length)
        }

        btnRegister.setOnClickListener { registerUser() }
        tvSignIn.setOnClickListener { finish() }   // vuelve al Login
    }

    private fun registerUser() {
        val name            = etName.text.toString().trim()
        val email           = etEmail.text.toString().trim()
        val password        = etPassword.text.toString().trim()
        val confirmPassword = etConfirmPassword.text.toString().trim()

        if (!validateInputs(name, email, password, confirmPassword)) return
        if (!cbTerms.isChecked) {
            Toast.makeText(this, "Debes aceptar los Términos y Condiciones", Toast.LENGTH_SHORT).show()
            return
        }

        showLoading(true)


        mAuth.createUserWithEmailAndPassword(email, password)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    // Actualizar display name

                    val secret = TOTPHelper.generateSecretKey()
                    android.util.Log.d("TOTP", "Secret generado: $secret")

                    val userId = mAuth.currentUser?.uid
                    if (userId == null) {
                        showLoading(false)
                        Toast.makeText(this, "Error inesperado", Toast.LENGTH_SHORT).show()
                        return@addOnCompleteListener
                    }

                    val db = FirebaseFirestore.getInstance()

                    val userData = hashMapOf(
                        "name" to name,
                        "email" to email,
                        "totpSecret" to secret
                    )

                    db.collection("users").document(userId)
                        .set(userData)
                        .addOnSuccessListener {

                            val profileUpdates = UserProfileChangeRequest.Builder()
                                .setDisplayName(name)
                                .build()

                            mAuth.currentUser?.updateProfile(profileUpdates)
                                ?.addOnCompleteListener {

                                    showLoading(false)

                                    mAuth.currentUser?.sendEmailVerification()

                                    Toast.makeText(
                                        this,
                                        "¡Cuenta creada! Revisa tu correo para verificarla.",
                                        Toast.LENGTH_LONG
                                    ).show()

                                    val intent = Intent(this, TwoFactorActivity::class.java)
                                    intent.putExtra("user_email", email)
                                    intent.putExtra("totp_secret", secret)
                                    startActivity(intent)
                                    finish()
                                }
                        }
                        .addOnFailureListener {
                            showLoading(false)
                            Toast.makeText(this, "Error guardando datos", Toast.LENGTH_SHORT).show()
                        }
                } else {
                    showLoading(false)
                    Toast.makeText(this, "Error: ${task.exception?.message}", Toast.LENGTH_LONG).show()
                }
            }
    }

    private fun validateInputs(
        name: String, email: String,
        password: String, confirmPassword: String
    ): Boolean {
        if (name.isEmpty()) {
            etName.error = "Ingresa tu nombre"; return false
        }
        if (name.length < 2) {
            etName.error = "Nombre muy corto"; return false
        }
        if (email.isEmpty()) {
            etEmail.error = "Ingresa un correo"; return false
        }
        if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            etEmail.error = "Correo inválido"; return false
        }
        if (password.isEmpty()) {
            etPassword.error = "Ingresa una contraseña"; return false
        }
        if (password.length < 8) {
            etPassword.error = "Mínimo 8 caracteres"; return false
        }
        if (!password.any { it.isDigit() }) {
            etPassword.error = "Debe contener al menos un número"; return false
        }
        if (!password.any { it.isUpperCase() }) {
            etPassword.error = "Debe contener al menos una mayúscula"; return false
        }
        if (confirmPassword != password) {
            etConfirmPassword.error = "Las contraseñas no coinciden"; return false
        }
        return true
    }

    private fun showLoading(show: Boolean) {
        progressBar.visibility = if (show) View.VISIBLE else View.GONE
        btnRegister.isEnabled  = !show
    }

    private fun navigateToHome() {
        startActivity(Intent(this, HomeActivity::class.java))
        finishAffinity()
    }
}