package com.example.niju_project

import android.content.Intent
import android.os.Bundle
import android.text.InputType
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.viewModels
import com.example.niju_project.ui.RegisterState
import com.example.niju_project.ui.RegisterViewModel

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

    private val viewModel: RegisterViewModel by viewModels()
    private var isPasswordVisible = false
    private var isConfirmVisible  = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_register)
        bindViews()
        setupListeners()
        setupObservers()
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

    private fun setupObservers() {
        viewModel.registerState.observe(this) { state ->
            when (state) {
                is RegisterState.Loading -> showLoading(true)
                is RegisterState.Success -> {
                    showLoading(false)
                    Toast.makeText(this, "¡Cuenta creada! Configura tu 2FA.", Toast.LENGTH_LONG).show()
                    val intent = Intent(this, TwoFactorActivity::class.java).apply {
                        putExtra("mode", "setup")
                        putExtra("totp_secret", state.secret)
                        putExtra("user_email", state.email)
                    }
                    startActivity(intent)
                    finishAffinity()
                }
                is RegisterState.Error -> {
                    showLoading(false)
                    Toast.makeText(this, state.message, Toast.LENGTH_LONG).show()
                }
                else -> showLoading(false)
            }
        }
    }

    private fun registerUser() {
        if (!cbTerms.isChecked) {
            Toast.makeText(this, "Debes aceptar los Términos y Condiciones", Toast.LENGTH_SHORT).show()
            return
        }

        viewModel.register(
            etName.text.toString().trim(),
            etEmail.text.toString().trim(),
            etPassword.text.toString().trim(),
            etConfirmPassword.text.toString().trim()
        )
    }

    private fun showLoading(show: Boolean) {
        progressBar.visibility = if (show) View.VISIBLE else View.GONE
        btnRegister.isEnabled  = !show
    }
}
