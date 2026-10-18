package com.example.niju_project.audio

import java.util.Locale

/**
 * Clave estable de un audio a partir del texto en inglés.
 *
 * Minúsculas y todo lo que no sea a-z / 0-9 pasa a "_":
 *   "Boarding pass" -> "boarding_pass"
 *   "Can I pay by card?" -> "can_i_pay_by_card"
 *
 * DEBE coincidir con `audio_key()` de tools/audio/generate_audio.py: así la app
 * encuentra el audio solo con el texto de la base de datos, sin columnas nuevas ni
 * identificadores que cambien entre entornos. Hay un vector de pruebas compartido
 * en AudioKeyTest.
 */
object AudioKey {
    private val NOT_ALNUM = Regex("[^a-z0-9]+")

    fun of(text: String): String =
        text.lowercase(Locale.ROOT).replace(NOT_ALNUM, "_").trim('_')
}
