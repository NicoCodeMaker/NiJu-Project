package com.example.niju_project

import android.content.Intent
import android.os.Bundle
import android.os.CountDownTimer
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import kotlin.random.Random

/**
 * Pantalla de Autenticación de Dos Factores (2FA).
 *
 * Estrategia implementada:
 *  - Se genera un código OTP de 6 dígitos del lado del cliente (demo).
 *  - En producción, este código debe generarse y enviarse vía Firebase Cloud Functions
 *    + SendGrid / Twilio, o bien usar un TOTP (Google Authenticator) con HMAC-SHA1.
 *
 * Flujo:
 *  1. Al abrir esta Activity se "envía" el código al correo del usuario.
 *  2. El usuario ingresa el código en el campo OTP.
 *  3. Si coincide y no expiró (2 min), accede al Home.
 */
class TwoFactorActivity : AppCompatActivity() {

    private lateinit var tvDescription: TextView
    private lateinit var etCode: EditText
    private lateinit var btnVerify: Button
    private lateinit var btnResend: Button
    private lateinit var tvTimer: TextView
    private lateinit var progressBar: ProgressBar

    private lateinit var mAuth: FirebaseAuth

    private var generatedCode: String = ""
    private var codeExpiryTime: Long = 0L
    private var countDownTimer: CountDownTimer? = null
    private val CODE_VALIDITY_MS = 120_000L   // 2 minutos

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_two_factor)

        mAuth = FirebaseAuth.getInstance()
        bindViews()

        val userEmail = intent.getStringExtra("user_email") ?: mAuth.currentUser?.email ?: ""
        tvDescription.text = "Ingresa el código de 6 dígitos enviado a\n$userEmail"

        setupListeners()
        generateAndSendCode()
    }

    private fun bindViews() {
        tvDescription = findViewById(R.id.tvDescription)
        etCode        = findViewById(R.id.etCode)
        btnVerify     = findViewById(R.id.btnVerify)
        btnResend     = findViewById(R.id.btnResend)
        tvTimer       = findViewById(R.id.tvTimer)
        progressBar   = findViewById(R.id.progressBar)
    }

    private fun setupListeners() {
        btnVerify.setOnClickListener {
            val input = etCode.text.toString().trim()
            when {
                input.length != 6         -> etCode.error = "El código debe tener 6 dígitos"
                System.currentTimeMillis() > codeExpiryTime ->
                    Toast.makeText(this, "Código expirado. Solicita uno nuevo.", Toast.LENGTH_SHORT).show()
                input != generatedCode    ->
                    Toast.makeText(this, "Código incorrecto", Toast.LENGTH_SHORT).show()
                else                      -> onVerificationSuccess()
            }
        }

        btnResend.setOnClickListener {
            countDownTimer?.cancel()
            generateAndSendCode()
            Toast.makeText(this, "Nuevo código enviado", Toast.LENGTH_SHORT).show()
        }
    }

    // ─── Generar OTP ─────────────────────────────────────────────────────────
    private fun generateAndSendCode() {
        generatedCode   = String.format("%06d", Random.nextInt(0, 999999))
        codeExpiryTime  = System.currentTimeMillis() + CODE_VALIDITY_MS

        /* ── PRODUCCIÓN ────────────────────────────────────────────────────────
         * Llama aquí a tu Cloud Function para enviar el código por correo/SMS.
         * Ejemplo con Retrofit / OkHttp:
         *
         *   val body = mapOf("email" to userEmail, "code" to generatedCode)
         *   apiService.sendOtpEmail(body).enqueue(...)
         *
         * O bien integra un TOTP (RFC 6238) con una librería como:
         *   dev.turingcomplete:kotlin-onetimepassword
         * ──────────────────────────────────────────────────────────────────── */

        // DEMO: muestra el código en un Toast (remover en producción)
        Toast.makeText(this, "Código de prueba: $generatedCode", Toast.LENGTH_LONG).show()

        startCountdown()
        btnResend.isEnabled = false
    }

    // ─── Countdown ───────────────────────────────────────────────────────────
    private fun startCountdown() {
        countDownTimer?.cancel()
        countDownTimer = object : CountDownTimer(CODE_VALIDITY_MS, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                val seconds = millisUntilFinished / 1000
                tvTimer.text = "El código expira en ${seconds}s"
            }
            override fun onFinish() {
                tvTimer.text = "Código expirado"
                btnResend.isEnabled = true
            }
        }.start()
    }

    // ─── Éxito ───────────────────────────────────────────────────────────────
    private fun onVerificationSuccess() {
        countDownTimer?.cancel()
        Toast.makeText(this, "Verificación exitosa ✓", Toast.LENGTH_SHORT).show()
        startActivity(Intent(this, HomeActivity::class.java))
        finishAffinity()
    }

    override fun onDestroy() {
        super.onDestroy()
        countDownTimer?.cancel()
    }
}