package com.example.niju_project.utils

import dev.turingcomplete.kotlinonetimepassword.TimeBasedOneTimePasswordConfig
import dev.turingcomplete.kotlinonetimepassword.TimeBasedOneTimePasswordGenerator
import dev.turingcomplete.kotlinonetimepassword.HmacAlgorithm
import org.apache.commons.codec.binary.Base32
import java.security.SecureRandom
import java.util.concurrent.TimeUnit

object TOTPHelper {

    private val config = TimeBasedOneTimePasswordConfig(
        codeDigits = 6,
        hmacAlgorithm = HmacAlgorithm.SHA1,
        timeStep = 30,
        timeStepUnit = TimeUnit.SECONDS
    )

    // 🔐 Secret en Base32 (STRING limpio)
    fun generateSecretKey(): String {
        val random = SecureRandom()
        val bytes = ByteArray(20)
        random.nextBytes(bytes)

        return Base32().encodeToString(bytes).trim()
    }

    // 🔢 Generar código actual
    fun generateCode(secret: String): String {
        val secretBytes = Base32().decode(secret)
        val generator = TimeBasedOneTimePasswordGenerator(secretBytes, config)
        return generator.generate()
    }

    // ✅ Validar código
    fun validateCode(secret: String, code: String): Boolean {
        val secretBytes = Base32().decode(secret)
        val generator = TimeBasedOneTimePasswordGenerator(secretBytes, config)
        return generator.isValid(code)
    }
}