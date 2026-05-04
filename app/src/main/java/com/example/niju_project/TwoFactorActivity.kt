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
import androidx.activity.viewModels
import com.example.niju_project.ui.TwoFactorUiState
import com.example.niju_project.ui.TwoFactorViewModel
import com.google.firebase.auth.FirebaseAuth
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter

class TwoFactorActivity : AppCompatActivity() {

    private lateinit var progressBar: ProgressBar
    private lateinit var tvTitle: TextView
    private lateinit var tvDescription: TextView
    private lateinit var etCode: EditText
    private lateinit var btnVerify: Button
    private lateinit var tvTimer: TextView
    private lateinit var layoutSetup: LinearLayout
    private lateinit var ivQr: ImageView
    private lateinit var tvSecret: TextView
    private lateinit var btnCopySecret: Button
    private lateinit var layoutVerify: LinearLayout

    private val viewModel: TwoFactorViewModel by viewModels()
    private lateinit var mAuth: FirebaseAuth
    private var secret: String = ""
    private var userEmail: String = ""
    private var mode: String = "verify"
    private var countdown: CountDownTimer? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_two_factor)

        mAuth = FirebaseAuth.getInstance()
        mode = intent.getStringExtra("mode") ?: "verify"
        secret = intent.getStringExtra("totp_secret") ?: ""
        userEmail = intent.getStringExtra("user_email") ?: mAuth.currentUser?.email ?: ""

        bindViews()
        setupObservers()

        if (secret.isEmpty()) {
            mAuth.currentUser?.uid?.let { viewModel.fetchSecret(it) } ?: goToLogin()
        } else {
            setupAfterBind()
        }
    }

    private fun bindViews() {
        progressBar = findViewById(R.id.progressBar)
        tvTitle = findViewById(R.id.tvTitle)
        tvDescription = findViewById(R.id.tvDescription)
        etCode = findViewById(R.id.etCode)
        btnVerify = findViewById(R.id.btnVerify)
        tvTimer = findViewById(R.id.tvTimer)
        layoutSetup = findViewById(R.id.layoutSetup)
        ivQr = findViewById(R.id.ivQr)
        tvSecret = findViewById(R.id.tvSecret)
        btnCopySecret = findViewById(R.id.btnCopySecret)
        layoutVerify = findViewById(R.id.layoutVerify)
    }

    private fun setupObservers() {
        viewModel.uiState.observe(this) { state ->
            when (state) {
                is TwoFactorUiState.Loading -> showGlobalLoading(true)
                is TwoFactorUiState.SecretReady -> {
                    showGlobalLoading(false)
                    this.secret = state.secret
                    setupAfterBind()
                }
                is TwoFactorUiState.Success -> {
                    showGlobalLoading(false)
                    if (mode == "setup") {
                        val uid = mAuth.currentUser?.uid ?: ""
                        com.example.niju_project.utils.PrefsUtils.set2FAEnabled(this, uid, true)
                        toast("✓ 2FA activado correctamente")
                    }
                    goToHome()
                }
                is TwoFactorUiState.Error -> {
                    showGlobalLoading(false)
                    toast(state.message)

                    if (mode == "setup" && secret.isEmpty()) {
                        val uid = mAuth.currentUser?.uid ?: return@observe
                        val newSecret = TOTPHelper.generateSecretKey()

                        viewModel.saveSecret(uid, newSecret)
                        this.secret = newSecret
                        setupAfterBind()
                    }
                    // ❌ QUITA el goToLogin()
                }
                else -> showGlobalLoading(false)
            }
        }
    }

    private fun setupAfterBind() {
        when (mode) {
            "setup" -> setupMode()
            else -> verifyMode()
        }
    }

    private fun setupMode() {
        tvTitle.text = "Activa la verificación en 2 pasos"
        tvDescription.text = "Escanea el código QR con tu app autenticadora"
        layoutSetup.visibility = View.VISIBLE
        layoutVerify.visibility = View.GONE
        tvTimer.visibility = View.GONE
        btnVerify.text = "Confirmar código"

        val otpUrl = TOTPHelper.buildOtpAuthUrl(userEmail, secret)
        ivQr.setImageBitmap(generateQrBitmap(otpUrl, 512))
        if (secret.isNotEmpty()) {
            tvSecret.text = secret.chunked(4).joinToString(" ")
        }

        btnCopySecret.setOnClickListener {
            val cm = getSystemService(CLIPBOARD_SERVICE) as android.content.ClipboardManager
            cm.setPrimaryClip(android.content.ClipData.newPlainText("TOTP Secret", secret))
            toast("Secret copiado")
        }

        btnVerify.setOnClickListener {
            if (secret.isEmpty()) {
                toast("Error: no se pudo validar el código")
                return@setOnClickListener
            }

            val uid = mAuth.currentUser?.uid
            val isSetup = true

            viewModel.verifyCode(
                secret,
                etCode.text.toString().trim(),
                isSetup,
                uid
            )
        }
    }

    private fun verifyMode() {
        tvTitle.text = "Verificación en 2 pasos"
        tvDescription.text = "Abre tu app e ingresa el código para $userEmail"
        layoutSetup.visibility = View.GONE
        layoutVerify.visibility = View.VISIBLE
        tvTimer.visibility = View.VISIBLE
        btnVerify.text = "Verificar"

        startCountdown()
        btnVerify.setOnClickListener {
            val uid = mAuth.currentUser?.uid
            val isSetup = false

            viewModel.verifyCode(
                secret,
                etCode.text.toString().trim(),
                isSetup,
                null
            )
        }
    }

    private fun startCountdown() {
        countdown?.cancel()
        val stepMs = 30_000L
        val remaining = stepMs - (System.currentTimeMillis() % stepMs)
        countdown = object : CountDownTimer(remaining, 1000) {
            override fun onTick(ms: Long) {
                val secs = (ms / 1000).toInt()
                tvTimer.text = "Código válido por ${secs}s"
                tvTimer.setTextColor(if (secs <= 5) Color.RED else Color.GRAY)
            }
            override fun onFinish() { startCountdown() }
        }.start()
    }

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

    private fun goToHome() {
        startActivity(Intent(this, HomeActivity::class.java))
        finishAffinity()
    }

    private fun goToLogin() {
        startActivity(Intent(this, LoginActivity::class.java)
            .apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK })
        finish()
    }

    private fun showGlobalLoading(show: Boolean) {
        progressBar.visibility = if (show) View.VISIBLE else View.GONE
        btnVerify.isEnabled = !show
    }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()

    override fun onDestroy() {
        super.onDestroy()
        countdown?.cancel()
    }
}
