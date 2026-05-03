package com.example.niju_project

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.FirebaseException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.PhoneAuthCredential
import com.google.firebase.auth.PhoneAuthOptions
import com.google.firebase.auth.PhoneAuthProvider
import java.util.concurrent.TimeUnit

/**
 * Pantalla para inicio de sesión / registro con número de teléfono (SMS OTP).
 *
 * Flujo:
 *  1. Usuario ingresa número con código de país (+57 para Colombia, etc.)
 *  2. Firebase envía SMS con código de 6 dígitos
 *  3. Usuario ingresa código → se autentica y va al Home
 */
class PhoneAuthActivity : AppCompatActivity() {

    // Paso 1: ingresar teléfono
    private lateinit var layoutPhone: LinearLayout
    private lateinit var etPhone: EditText
    private lateinit var btnSendCode: Button

    // Paso 2: ingresar OTP
    private lateinit var layoutOtp: LinearLayout
    private lateinit var etOtp: EditText
    private lateinit var btnVerifyCode: Button
    private lateinit var btnResend: TextView

    private lateinit var progressBar: ProgressBar
    private lateinit var mAuth: FirebaseAuth

    private var storedVerificationId: String? = null
    private var resendToken: PhoneAuthProvider.ForceResendingToken? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_phone_auth)

        mAuth = FirebaseAuth.getInstance()
        bindViews()
        setupListeners()
        showPhasePhone()
    }

    private fun bindViews() {
        layoutPhone    = findViewById(R.id.layoutPhone)
        etPhone        = findViewById(R.id.etPhone)
        btnSendCode    = findViewById(R.id.btnSendCode)
        layoutOtp      = findViewById(R.id.layoutOtp)
        etOtp          = findViewById(R.id.etOtp)
        btnVerifyCode  = findViewById(R.id.btnVerifyCode)
        btnResend      = findViewById(R.id.btnResend)
        progressBar    = findViewById(R.id.progressBar)
    }

    private fun setupListeners() {
        btnSendCode.setOnClickListener {
            val phone = etPhone.text.toString().trim()
            if (phone.length < 10) {
                etPhone.error = "Número inválido (incluye código de país, ej. +573001234567)"
                return@setOnClickListener
            }
            sendVerificationCode(phone)
        }

        btnVerifyCode.setOnClickListener {
            val code = etOtp.text.toString().trim()
            if (code.length != 6) {
                etOtp.error = "El código debe tener 6 dígitos"; return@setOnClickListener
            }
            verifyCode(code)
        }

        btnResend.setOnClickListener {
            val phone = etPhone.text.toString().trim()
            if (resendToken != null) {
                sendVerificationCode(phone, resendToken)
            }
        }
    }

    // ─── Fase 1: enviar SMS ──────────────────────────────────────────────────
    private fun sendVerificationCode(
        phoneNumber: String,
        token: PhoneAuthProvider.ForceResendingToken? = null
    ) {
        showLoading(true)
        val optionsBuilder = PhoneAuthOptions.newBuilder(mAuth)
            .setPhoneNumber(phoneNumber)
            .setTimeout(60L, TimeUnit.SECONDS)
            .setActivity(this)
            .setCallbacks(callbacks)

        if (token != null) optionsBuilder.setForceResendingToken(token)

        PhoneAuthProvider.verifyPhoneNumber(optionsBuilder.build())
    }

    private val callbacks = object : PhoneAuthProvider.OnVerificationStateChangedCallbacks() {

        override fun onVerificationCompleted(credential: PhoneAuthCredential) {
            // Auto-verificación (en algunos dispositivos Android el SMS se lee automáticamente)
            showLoading(false)
            signInWithCredential(credential)
        }

        override fun onVerificationFailed(e: FirebaseException) {
            showLoading(false)
            Toast.makeText(
                this@PhoneAuthActivity,
                "Error al enviar SMS: ${e.message}",
                Toast.LENGTH_LONG
            ).show()
        }

        override fun onCodeSent(
            verificationId: String,
            token: PhoneAuthProvider.ForceResendingToken
        ) {
            showLoading(false)
            storedVerificationId = verificationId
            resendToken = token
            showPhaseOtp()
            Toast.makeText(this@PhoneAuthActivity, "Código enviado por SMS", Toast.LENGTH_SHORT).show()
        }
    }

    // ─── Fase 2: verificar OTP ───────────────────────────────────────────────
    private fun verifyCode(code: String) {
        val vId = storedVerificationId ?: return
        showLoading(true)
        val credential = PhoneAuthProvider.getCredential(vId, code)
        signInWithCredential(credential)
    }

    private fun signInWithCredential(credential: PhoneAuthCredential) {
        mAuth.signInWithCredential(credential)
            .addOnCompleteListener { task ->
                showLoading(false)
                if (task.isSuccessful) {
                    startActivity(Intent(this, HomeActivity::class.java))
                    finishAffinity()
                } else {
                    Toast.makeText(this, "Código incorrecto o expirado", Toast.LENGTH_LONG).show()
                }
            }
    }

    // ─── UI helpers ──────────────────────────────────────────────────────────
    private fun showPhasePhone() {
        layoutPhone.visibility = View.VISIBLE
        layoutOtp.visibility   = View.GONE
    }

    private fun showPhaseOtp() {
        layoutPhone.visibility = View.GONE
        layoutOtp.visibility   = View.VISIBLE
    }

    private fun showLoading(show: Boolean) {
        progressBar.visibility = if (show) View.VISIBLE else View.GONE
        btnSendCode.isEnabled  = !show
        btnVerifyCode.isEnabled = !show
    }
}