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
import com.example.niju_project.LoginActivity
import com.example.niju_project.R
import com.example.niju_project.data.remote.NijuErrors
import com.example.niju_project.data.remote.SupabaseProvider
import com.example.niju_project.data.repository.AuthRepository
import com.example.niju_project.data.repository.ProfileRepository
import com.example.niju_project.data.repository.SignUpOutcome
import com.example.niju_project.domain.auth.AuthValidation
import com.example.niju_project.ui.check
import com.example.niju_project.ui.hideKeyboard
import com.example.niju_project.ui.shake
import com.example.niju_project.ui.showLegalDialog
import com.google.android.material.button.MaterialButton
import com.google.android.material.checkbox.MaterialCheckBox
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import kotlinx.coroutines.launch
import java.util.TimeZone

/** Registro con correo y contraseña (Supabase Auth). */
class RegisterActivity : AppCompatActivity() {

    private lateinit var tilName: TextInputLayout
    private lateinit var tilEmail: TextInputLayout
    private lateinit var tilPassword: TextInputLayout
    private lateinit var tilConfirm: TextInputLayout
    private lateinit var cbTerms: MaterialCheckBox
    private lateinit var btnRegister: MaterialButton
    private var working = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_register)
        if (!SupabaseProvider.isConfigured) { finish(); return }

        findViewById<TextView>(R.id.tvHeaderTitle).setText(R.string.auth_create_account)
        findViewById<View>(R.id.btnBack).setOnClickListener { finish() }
        tilName = findViewById(R.id.tilName)
        tilEmail = findViewById(R.id.tilEmail)
        tilPassword = findViewById(R.id.tilPassword)
        tilConfirm = findViewById(R.id.tilConfirm)
        cbTerms = findViewById(R.id.cbTerms)
        btnRegister = findViewById(R.id.btnRegister)

        bindPasswordStrength(findViewById(R.id.etPassword), findViewById(R.id.strengthRow))
        cbTerms.setOnCheckedChangeListener { _, checked ->
            if (checked) findViewById<View>(R.id.tvTermsError).visibility = View.GONE
        }
        findViewById<View>(R.id.btnReadTerms).setOnClickListener { showLegalDialog() }
        findViewById<View>(R.id.btnGoLogin).setOnClickListener { finish() }
        btnRegister.setOnClickListener { register() }
        findViewById<TextInputEditText>(R.id.etConfirm).setOnEditorActionListener { _, id, _ ->
            if (id == EditorInfo.IME_ACTION_DONE) { register(); true } else false
        }
    }

    private fun text(id: Int) = findViewById<TextInputEditText>(id).text?.toString().orEmpty()

    private fun register() {
        if (working) return
        val name = text(R.id.etName)
        val email = text(R.id.etEmail)
        val password = text(R.id.etPassword)
        val confirm = text(R.id.etConfirm)
        var ok = tilName.check(AuthValidation.name(name))
        ok = tilEmail.check(AuthValidation.email(email)) && ok
        ok = tilPassword.check(AuthValidation.newPassword(password)) && ok
        ok = tilConfirm.check(AuthValidation.confirmation(password, confirm)) && ok
        findViewById<View>(R.id.tvTermsError).visibility = if (cbTerms.isChecked) View.GONE else View.VISIBLE
        ok = cbTerms.isChecked && ok
        if (!ok) { findViewById<View>(R.id.formContainer).shake(); return }

        hideKeyboard()
        setWorking(true)
        lifecycleScope.launch {
            val auth = AuthRepository()
            auth.signUpWithEmail(name, email, password)
                .onSuccess { outcome ->
                    when (outcome) {
                        SignUpOutcome.SIGNED_IN -> {
                            ProfileRepository().syncTimezone(TimeZone.getDefault().id)
                            startActivity(Intent(this@RegisterActivity, HomeActivity::class.java).apply {
                                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                            })
                            finish()
                        }
                        SignUpOutcome.CONFIRMATION_SENT -> {
                            setWorking(false)
                            MaterialAlertDialogBuilder(this@RegisterActivity)
                                .setTitle(R.string.auth_confirm_title)
                                .setMessage(getString(R.string.auth_confirm_message, email.trim()))
                                .setCancelable(false)
                                .setPositiveButton(R.string.auth_back_to_login) { _, _ ->
                                    startActivity(Intent(this@RegisterActivity, LoginActivity::class.java)
                                        .putExtra(LoginActivity.EXTRA_EMAIL, email.trim())
                                        .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP))
                                    finish()
                                }
                                .show()
                        }
                    }
                }
                .onFailure { e ->
                    setWorking(false)
                    val msg = NijuErrors.messageFor(e)
                    when {
                        NijuErrors.hasCode(e, "user_already_exists") || NijuErrors.hasCode(e, "User already registered") ->
                            tilEmail.check(msg)
                        NijuErrors.hasCode(e, "weak_password") -> tilPassword.check(msg)
                        else -> Toast.makeText(this@RegisterActivity, msg, Toast.LENGTH_LONG).show()
                    }
                }
        }
    }

    private fun setWorking(on: Boolean) {
        working = on
        btnRegister.isEnabled = !on
        findViewById<View>(R.id.progressBar).visibility = if (on) View.VISIBLE else View.GONE
    }
}
