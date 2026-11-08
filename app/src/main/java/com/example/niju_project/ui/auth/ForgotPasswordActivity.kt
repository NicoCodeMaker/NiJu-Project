package com.example.niju_project.ui.auth

import android.os.Bundle
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.niju_project.LoginActivity
import com.example.niju_project.R
import com.example.niju_project.data.remote.NijuErrors
import com.example.niju_project.data.remote.SupabaseProvider
import com.example.niju_project.data.repository.AuthRepository
import com.example.niju_project.domain.auth.AuthValidation
import com.example.niju_project.ui.check
import com.example.niju_project.ui.hideKeyboard
import com.example.niju_project.ui.shake
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import kotlinx.coroutines.launch

/**
 * Recuperación de contraseña: Supabase envía un enlace a niju://login-callback;
 * al abrirlo, LoginActivity lleva a [ResetPasswordActivity].
 * Por privacidad la respuesta es la misma exista o no la cuenta.
 */
class ForgotPasswordActivity : AppCompatActivity() {

    private lateinit var til: TextInputLayout
    private lateinit var et: TextInputEditText
    private lateinit var btnSend: MaterialButton
    private var working = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_forgot_password)
        if (!SupabaseProvider.isConfigured) { finish(); return }
        findViewById<TextView>(R.id.tvHeaderTitle).setText(R.string.auth_forgot)
        findViewById<View>(R.id.btnBack).setOnClickListener { finish() }
        findViewById<View>(R.id.btnBackToLogin).setOnClickListener { finish() }
        til = findViewById(R.id.tilEmail)
        et = findViewById(R.id.etEmail)
        btnSend = findViewById(R.id.btnSend)
        intent.getStringExtra(LoginActivity.EXTRA_EMAIL)?.let { et.setText(it) }
        btnSend.setOnClickListener { send() }
        et.setOnEditorActionListener { _, id, _ -> if (id == EditorInfo.IME_ACTION_DONE) { send(); true } else false }
    }

    private fun send() {
        if (working) return
        val email = et.text?.toString().orEmpty()
        if (!til.check(AuthValidation.email(email))) { findViewById<View>(R.id.formContainer).shake(); return }
        hideKeyboard()
        working = true
        btnSend.isEnabled = false
        findViewById<View>(R.id.progressBar).visibility = View.VISIBLE
        lifecycleScope.launch {
            AuthRepository().sendPasswordReset(email)
                .onSuccess { showSent(email.trim()) }
                .onFailure { e ->
                    working = false
                    btnSend.isEnabled = true
                    findViewById<View>(R.id.progressBar).visibility = View.GONE
                    Toast.makeText(this@ForgotPasswordActivity, NijuErrors.messageFor(e), Toast.LENGTH_LONG).show()
                }
        }
    }

    private fun showSent(email: String) {
        findViewById<View>(R.id.progressBar).visibility = View.GONE
        findViewById<TextView>(R.id.tvFormTitle).setText(R.string.auth_forgot_sent_title)
        findViewById<TextView>(R.id.tvFormSubtitle).text = getString(R.string.auth_forgot_sent, email)
        til.visibility = View.GONE
        btnSend.visibility = View.GONE
    }
}
