package com.example.niju_project.utils

import org.jboss.aerogear.security.otp.Totp
import java.security.SecureRandom
import org.apache.commons.codec.binary.Base32

object TOTPHelper {

    // 🔐 Generar clave secreta
    fun generateSecretKey(): String {
        val random = SecureRandom()
        val bytes = ByteArray(20)
        random.nextBytes(bytes)

        val base32 = Base32()
        return base32.encodeToString(bytes)
    }

    // 🔢 Generar código actual
    fun generateCode(secret: String): String {
        val totp = Totp(secret)
        return totp.now()
    }

    // ✅ Validar código ingresado
    fun validateCode(secret: String, code: String): Boolean {
        val totp = Totp(secret)
        return totp.verify(code)
    }
}