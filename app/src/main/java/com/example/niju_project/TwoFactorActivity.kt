package com.example.niju_project

import com.example.niju_project.utils.TOTPHelper

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import android.widget.ImageView
import android.widget.*
import android.widget.Toast
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.BarcodeFormat




class TwoFactorActivity : AppCompatActivity() {

    private var secret: String = ""
    private lateinit var tvDescription: TextView
    private lateinit var etCode: EditText
    private lateinit var btnVerify: Button
    private lateinit var btnResend: Button
    private lateinit var tvTimer: TextView
    private lateinit var progressBar: ProgressBar

    private lateinit var mAuth: FirebaseAuth

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_two_factor)

        val email = intent.getStringExtra("user_email") ?: ""
        secret = intent.getStringExtra("totp_secret") ?: ""
        if (secret.isEmpty()) {
            Toast.makeText(this, "Secret inválido", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        mAuth = FirebaseAuth.getInstance()
        bindViews()

        val ivQr = findViewById<ImageView>(R.id.ivQr)

        val otpUrl = getOtpAuthUrl(email, secret)
        val qrBitmap = generateQRCode(otpUrl)

        ivQr.setImageBitmap(qrBitmap)
        tvDescription.text = "Escanea el código QR con Microsoft Authenticator\n$email"

        setupListeners()
    }

    fun getOtpAuthUrl(email: String, secret: String): String {
        return "otpauth://totp/NiJu:$email?secret=$secret&issuer=NiJu"
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

            if (input.isEmpty()) {
                etCode.error = "Ingresa el código"
                return@setOnClickListener
            }

            if (TOTPHelper.validateCode(secret, input)) {
                onVerificationSuccess()
            } else {
                Toast.makeText(this, "Código incorrecto", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // ─── Éxito ───────────────────────────────────────────────────────────────
    private fun onVerificationSuccess() {
        Toast.makeText(this, "Verificación exitosa ✓", Toast.LENGTH_SHORT).show()
        startActivity(Intent(this, HomeActivity::class.java))
        finishAffinity()
    }

    override fun onDestroy() {
        super.onDestroy()
    }

    fun generateQRCode(text: String): android.graphics.Bitmap {
        val writer = com.google.zxing.qrcode.QRCodeWriter()
        val bitMatrix = writer.encode(text, com.google.zxing.BarcodeFormat.QR_CODE, 512, 512)

        val width = bitMatrix.width
        val height = bitMatrix.height
        val bmp = android.graphics.Bitmap.createBitmap(width, height, android.graphics.Bitmap.Config.RGB_565)

        for (x in 0 until width) {
            for (y in 0 until height) {
                bmp.setPixel(
                    x,
                    y,
                    if (bitMatrix[x, y]) android.graphics.Color.BLACK else android.graphics.Color.WHITE
                )
            }
        }

        return bmp
    }
}