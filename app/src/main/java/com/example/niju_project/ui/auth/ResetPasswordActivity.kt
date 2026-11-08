package com.example.niju_project.ui.auth

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.niju_project.HomeActivity
import com.example.niju_project.R
import com.example.niju_project.data.remote.NijuErrors
import com.example.niju_project.data.remote.SupabaseProvider
import com.example.niju_project.data.repository.AuthRepository
import com.example.niju_project.domain.auth.AuthValidation
import com.example.niju_project.goToLogin
import com.example.niju_project.ui.check
import com.example.niju_project.ui.hideKeyboard
import com.example.niju_project.ui.shake
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import kotlinx.coroutines.launch

/** Contraseña nueva tras abrir el enlace de recuperación (la sesión del enlace es temporal). */
class ResetPasswordActivity : AppCompatActivity() {

    private lateinit var tilPassword: TextInputLayout
    private lateinit var tilConfirm: TextInputLayout
    private lateinit var btnSave: MaterialButton
    private var working = false
    /** Abierta desde Configuración (sesión normal) y no desde el enlace de recuperación. */
    private val fromSettings by lazy { intent.getBooleanExtra(EXTRA_FROM_SETTINGS, false) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_reset_password)
        if (!SupabaseProvider.isConfigured) { finish(); return }
        findViewById<TextView>(R.id.tvHeaderTitle).setText(R.string.auth_reset_title)
        findViewById<View>(R.id.btnBack).setOnClickListener { if (fromSettings) finish() else goToLogin() }
        tilPassword = findViewById(R.id.tilPassword)
        tilConfirm = findViewById(R.id.tilConfirm)
        btnSave = findViewById(R.id.btnSave)
        bindPasswordStrength(findViewById(R.id.etPassword), findViewById(R.id.strengthRow))
        btnSave.setOnClickListener { save() }
        findViewById<TextInputEditText>(R.id.etConfirm).setOnEditorActionListener { _, id, _ ->
            if (id == EditorInfo.IME_ACTION_DONE) { save(); true } else false
        }
        if (!AuthRepository().isLoggedIn()) {
            Toast.makeText(this, R.string.auth_reset_no_session, Toast.LENGTH_LONG).show()
            goToLogin()
        }
    }

    private fun save() {
        if (working) return
        val password = findViewById<TextInputEditText>(R.id.etPassword).text?.toString().orEmpty()
        val confirm = findViewById<TextInputEditText>(R.id.etConfirm).text?.toString().orEmpty()
        var ok = tilPassword.check(AuthValidation.newPassword(password))
        ok = tilConfirm.check(AuthValidation.confirmation(password, confirm)) && ok
        if (!ok) { findViewById<View>(R.id.formContainer).shake(); return }
        hideKeyboard()
        working = true
        btnSave.isEnabled = false
        findViewById<View>(R.id.progressBar).visibility = View.VISIBLE
        lifecycleScope.launch {
            AuthRepository().updatePassword(password)
                .onSuccess {
                    Toast.makeText(this@ResetPasswordActivity, R.string.auth_reset_done, Toast.LENGTH_LONG).show()
                    if (fromSettings) { finish(); return@onSuccess }
                    startActivity(Intent(this@ResetPasswordActivity, HomeActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    })
                    finish()
                }
                .onFailure { e ->
                    working = false
                    btnSave.isEnabled = true
                    findViewById<View>(R.id.progressBar).visibility = View.GONE
                    if (NijuErrors.isSessionExpired(e)) {
                        Toast.makeText(this@ResetPasswordActivity, R.string.auth_reset_no_session, Toast.LENGTH_LONG).show()
                        goToLogin()
                    } else tilPassword.check(NijuErrors.messageFor(e))
                }
        }
    }

    companion object {
        const val EXTRA_FROM_SETTINGS = "from_settings"
    }
}
