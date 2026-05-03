package com.example.niju_project

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.os.Bundle
import android.os.CountDownTimer
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.example.niju_project.utils.TOTPHelper
import com.google.firebase.auth.FirebaseAuth
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter

/**
 * TwoFactorActivity — 2FA con TOTP real (RFC 6238)
 *
 * MODO A — SETUP (viene desde RegisterActivity o SettingsActivity)
 *   intent extras: "mode" = "setup", "totp_secret", "user_email"
 *   1. Muestra el QR para escanear con Google/Microsoft Authenticator
 *   2. Muestra el secret en texto para entrada manual
 *   3. Pide el primer código para confirmar que el setup fue correcto
 *   4. Si es correcto → guarda flag 2fa_enabled en Firestore + SharedPrefs → HomeActivity
 *
 * MODO B — VERIFY (viene desde LoginActivity cuando 2FA ya está activo)
 *   intent extras: "mode" = "verify", "totp_secret", "user_email"
 *   1. Muestra solo el campo de código + countdown
 *   2. Si es correcto → HomeActivity
 */
class TwoFactorActivity : AppCompatActivity() {

    // ── Vistas compartidas ───────────────────────────────────────────────────
    private lateinit var progressBar: ProgressBar
    private lateinit var tvTitle: TextView
    private lateinit var tvDescription: TextView
    private lateinit var etCode: EditText
    private lateinit var btnVerify: Button
    private lateinit var tvTimer: TextView

    // ── Solo modo SETUP ──────────────────────────────────────────────────────
    private lateinit var layoutSetup: LinearLayout
    private lateinit var ivQr: ImageView
    private lateinit var tvSecret: TextView
    private lateinit var btnCopySecret: Button

    // ── Solo modo VERIFY ─────────────────────────────────────────────────────
    private lateinit var layoutVerify: LinearLayout

    // ── Estado ───────────────────────────────────────────────────────────────
    private lateinit var mAuth: FirebaseAuth
    private var secret: String = ""
    private var userEmail: String = ""
    private var mode: String = "verify"    // "setup" | "verify"
    private var countdown: CountDownTimer? = null

    // ────────────────────────────────────────────────────────────────────────
    private fun bindViews() {
        progressBar   = findViewById(R.id.progressBar)
        tvTitle       = findViewById(R.id.tvTitle)
        tvDescription = findViewById(R.id.tvDescription)
        etCode        = findViewById(R.id.etCode)
        btnVerify     = findViewById(R.id.btnVerify)
        tvTimer       = findViewById(R.id.tvTimer)
        layoutSetup   = findViewById(R.id.layoutSetup)
        ivQr          = findViewById(R.id.ivQr)
        tvSecret      = findViewById(R.id.tvSecret)
        btnCopySecret = findViewById(R.id.btnCopySecret)
        layoutVerify  = findViewById(R.id.layoutVerify)
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_two_factor)

        mAuth     = FirebaseAuth.getInstance()
        mode      = intent.getStringExtra("mode")       ?: "verify"
        secret    = intent.getStringExtra("totp_secret") ?: ""
        userEmail = intent.getStringExtra("user_email")  ?: mAuth.currentUser?.email ?: ""

        // PRIMERO inicializas vistas SIEMPRE
        bindViews()

        if (secret.isEmpty()) {
            fetchSecretAndContinue()
        } else {
            setupAfterBind()
        }
    }

    // ── Cuando el login no pasa el secret: lo recuperamos de Firestore ───────
    private fun fetchSecretAndContinue() {
        val uid = mAuth.currentUser?.uid ?: run { goToLogin(); return }
        showGlobalLoading(true)

        com.google.firebase.firestore.FirebaseFirestore.getInstance()
            .collection("users").document(uid)
            .get()
            .addOnSuccessListener { doc ->
                showGlobalLoading(false)
                secret = doc.getString("totpSecret") ?: ""
                if (secret.isEmpty()) {
                    toast("Error: no se encontró el secret 2FA")
                    goToLogin()
                } else {
                    setupAfterBind()
                }
            }
            .addOnFailureListener {
                showGlobalLoading(false)
                toast("Error al obtener datos de seguridad")
                goToLogin()
            }
    }

    // ── Bind vistas y configurar pantalla según modo ─────────────────────────
    private fun setupAfterBind() {
        progressBar   = findViewById(R.id.progressBar)
        tvTitle       = findViewById(R.id.tvTitle)
        tvDescription = findViewById(R.id.tvDescription)
        etCode        = findViewById(R.id.etCode)
        btnVerify     = findViewById(R.id.btnVerify)
        tvTimer       = findViewById(R.id.tvTimer)
        layoutSetup   = findViewById(R.id.layoutSetup)
        ivQr          = findViewById(R.id.ivQr)
        tvSecret      = findViewById(R.id.tvSecret)
        btnCopySecret = findViewById(R.id.btnCopySecret)
        layoutVerify  = findViewById(R.id.layoutVerify)

        when (mode) {
            "setup"  -> setupMode()
            else     -> verifyMode()
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // MODO SETUP
    // ══════════════════════════════════════════════════════════════════════════
    private fun setupMode() {
        tvTitle.text       = "Activa la verificación en 2 pasos"
        tvDescription.text = "Escanea el código QR con tu app autenticadora\n(Google Authenticator, Microsoft Authenticator, Authy…)"

        layoutSetup.visibility  = View.VISIBLE
        layoutVerify.visibility = View.GONE
        tvTimer.visibility      = View.GONE
        btnVerify.text          = "Confirmar código"

        // Generar QR
        val otpUrl = TOTPHelper.buildOtpAuthUrl(userEmail, secret)
        ivQr.setImageBitmap(generateQrBitmap(otpUrl, 512))

        // Mostrar secret limpio (grupos de 4 para legibilidad)
        tvSecret.text = secret.chunked(4).joinToString(" ")

        // Copiar secret
        btnCopySecret.setOnClickListener {
            val cm = getSystemService(CLIPBOARD_SERVICE) as android.content.ClipboardManager
            cm.setPrimaryClip(android.content.ClipData.newPlainText("TOTP Secret", secret))
            toast("Secret copiado al portapapeles")
        }

        btnVerify.setOnClickListener { verifyCode(onSuccess = ::onSetupSuccess) }
    }

    private fun onSetupSuccess() {
        val uid = mAuth.currentUser?.uid ?: return
        showGlobalLoading(true)

        // Guardar flag 2FA habilitado en Firestore
        com.google.firebase.firestore.FirebaseFirestore.getInstance()
            .collection("users").document(uid)
            .update("twoFactorEnabled", true)
            .addOnCompleteListener {
                // También en SharedPrefs para acceso rápido local
                getSharedPreferences("niju_prefs", MODE_PRIVATE).edit()
                    .putBoolean("2fa_enabled_$uid", true)
                    .apply()
                showGlobalLoading(false)
                toast("✓ 2FA activado correctamente")
                goToHome()
            }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // MODO VERIFY (login normal con 2FA activo)
    // ══════════════════════════════════════════════════════════════════════════
    private fun verifyMode() {
        tvTitle.text       = "Verificación en 2 pasos"
        tvDescription.text = "Abre tu app autenticadora e ingresa\nel código de 6 dígitos para $userEmail"

        layoutSetup.visibility  = View.GONE
        layoutVerify.visibility = View.VISIBLE
        tvTimer.visibility      = View.VISIBLE
        btnVerify.text          = "Verificar"

        startCountdown()
        btnVerify.setOnClickListener { verifyCode(onSuccess = ::goToHome) }
    }

    // ── Countdown que muestra cuánto falta para el próximo código ────────────
    private fun startCountdown() {
        countdown?.cancel()
        updateTimerLabel()

        // Calcula tiempo hasta el siguiente período de 30 s
        val stepMs   = 30_000L
        val remaining = stepMs - (System.currentTimeMillis() % stepMs)

        countdown = object : CountDownTimer(remaining, 1000) {
            override fun onTick(ms: Long) {
                val secs = (ms / 1000).toInt()
                tvTimer.text = "Código válido por ${secs}s"

                // Color rojo cuando quedan menos de 5 s
                tvTimer.setTextColor(
                    if (secs <= 5) getColor(android.R.color.holo_red_dark)
                    else           getColor(android.R.color.darker_gray)
                )
            }
            override fun onFinish() {
                // Al expirar el período, reinicia el contador
                startCountdown()
            }
        }.start()
    }

    private fun updateTimerLabel() {
        tvTimer.text = "Código válido por ${TOTPHelper.secondsUntilNextCode()}s"
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Lógica de verificación común
    // ══════════════════════════════════════════════════════════════════════════
    private fun verifyCode(onSuccess: () -> Unit) {
        val input = etCode.text.toString().trim()

        if (input.length != 6) {
            etCode.error = "El código debe tener exactamente 6 dígitos"
            return
        }

        showGlobalLoading(true)

        val isValid = TOTPHelper.validateCode(secret, input)
        showGlobalLoading(false)

        if (isValid) {
            onSuccess()
        } else {
            etCode.error = "Código incorrecto"
            etCode.text.clear()
            toast("Código incorrecto. Revisa tu app autenticadora.")
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Generación del QR
    // ══════════════════════════════════════════════════════════════════════════
    private fun generateQrBitmap(content: String, size: Int): Bitmap {
        val bitMatrix = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, size, size)
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.RGB_565)
        for (x in 0 until size) {
            for (y in 0 until size) {
                bmp.setPixel(x, y, if (bitMatrix[x, y]) Color.BLACK else Color.WHITE)
            }
        }
        return bmp
    }

    // ── Helpers de navegación ────────────────────────────────────────────────
    private fun goToHome() {
        startActivity(Intent(this, HomeActivity::class.java))
        finishAffinity()
    }

    private fun goToLogin() {
        startActivity(Intent(this, LoginActivity::class.java)
            .apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK })
    }

    private fun showGlobalLoading(show: Boolean) {
        progressBar.visibility = if (show) View.VISIBLE else View.GONE
        btnVerify.isEnabled    = !show
    }

    private fun toast(msg: String) =
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()

    override fun onDestroy() {
        super.onDestroy()
        countdown?.cancel()
    }
}