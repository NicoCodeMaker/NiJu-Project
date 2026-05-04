package com.example.niju_project.utils

import dev.turingcomplete.kotlinonetimepassword.HmacAlgorithm
import dev.turingcomplete.kotlinonetimepassword.TimeBasedOneTimePasswordConfig
import dev.turingcomplete.kotlinonetimepassword.TimeBasedOneTimePasswordGenerator
import org.apache.commons.codec.binary.Base32
import java.security.SecureRandom
import java.util.concurrent.TimeUnit

/**
 * Helper para TOTP (RFC 6238) compatible con Google Authenticator,
 * Microsoft Authenticator y cualquier app TOTP estándar.
 *
 * Algoritmo: HMAC-SHA1 | Dígitos: 6 | Período: 30 segundos
 */
object TOTPHelper {

    private val config = TimeBasedOneTimePasswordConfig(
        codeDigits     = 6,
        hmacAlgorithm  = HmacAlgorithm.SHA1,
        timeStep       = 30,
        timeStepUnit   = TimeUnit.SECONDS
    )

    /** Genera un secret Base32 de 20 bytes (160 bits) apto para QR TOTP */
    fun generateSecretKey(): String {
        val bytes = ByteArray(20)
        SecureRandom().nextBytes(bytes)
        // Base32 limpio sin padding '=' para mayor compatibilidad con apps auth
        return Base32().encodeToString(bytes).trimEnd('=')
    }

    /** Genera el código TOTP actual para el secret dado */
    fun generateCode(secret: String): String {
        val secretBytes = Base32().decode(padBase32(secret))
        return TimeBasedOneTimePasswordGenerator(secretBytes, config).generate()
    }

    /**
     * Valida el código ingresado contra el secret.
     * Acepta ±1 ventana de 30 s para compensar desfase de reloj (RFC 6238).
     */
    fun validateCode(secret: String, code: String): Boolean {
        return try {
            val secretBytes = Base32().decode(padBase32(secret))
            val generator   = TimeBasedOneTimePasswordGenerator(secretBytes, config)

            val now = System.currentTimeMillis()
            val stepMs = TimeUnit.SECONDS.toMillis(30)

            // Probamos ventana anterior, actual y siguiente (i = -1, 0, 1)
            (-1..1).any { i ->
                generator.isValid(code, timestamp = now + (i * stepMs))
            }
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Construye la URL otpauth:// estándar para generar el QR.
     * Compatible con Google Authenticator, Microsoft Authenticator, Authy, etc.
     */
    fun buildOtpAuthUrl(email: String, secret: String, issuer: String = "NiJu"): String {
        val encodedEmail  = java.net.URLEncoder.encode(email,  "UTF-8")
        val encodedIssuer = java.net.URLEncoder.encode(issuer, "UTF-8")
        return "otpauth://totp/$encodedIssuer:$encodedEmail" +
                "?secret=$secret&issuer=$encodedIssuer&algorithm=SHA1&digits=6&period=30"
    }

    /** Cuántos segundos faltan para que expire el código actual */
    fun secondsUntilNextCode(): Int {
        val stepMs = TimeUnit.SECONDS.toMillis(30)
        return ((stepMs - System.currentTimeMillis() % stepMs) / 1000).toInt()
    }

    // ── Utilidad interna: rellena el padding Base32 si le falta ──────────────
    private fun padBase32(s: String): String {
        val mod = s.length % 8
        return if (mod == 0) s else s + "=".repeat(8 - mod)
    }
}