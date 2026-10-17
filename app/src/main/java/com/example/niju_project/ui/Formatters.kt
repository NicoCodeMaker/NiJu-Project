package com.example.niju_project.ui

import com.example.niju_project.data.model.DailyActivity
import com.example.niju_project.data.model.LessonStatus
import java.util.Calendar
import kotlin.math.roundToInt

/** Textos de presentación. Código puro: se prueba sin Android. */
object Formatters {

    fun lessonStatus(l: LessonStatus): String = when {
        !l.unlocked -> "🔒 Supera la lección anterior para abrirla"
        l.completed -> "✓ Superada · mejor resultado ${l.bestScore.roundToInt()} %"
        l.bestScore > 0 -> "${words(l.wordCount)} · mejor resultado ${l.bestScore.roundToInt()} % (necesitas 70 %)"
        else -> "${words(l.wordCount)} · nueva"
    }

    fun words(n: Int) = if (n == 1) "1 palabra" else "$n palabras"

    fun partOfSpeech(raw: String?): String? = when (raw) {
        "noun" -> "sustantivo"
        "verb" -> "verbo"
        "adjective" -> "adjetivo"
        "adverb" -> "adverbio"
        "pronoun" -> "pronombre"
        "preposition" -> "preposición"
        "conjunction" -> "conjunción"
        "interjection" -> "interjección"
        "phrase" -> "expresión"
        else -> null
    }

    /** Inicial del día de la semana en español (L M X J V S D) para "yyyy-MM-dd". */
    fun dayInitial(isoDate: String): String = runCatching {
        val (y, m, d) = isoDate.take(10).split("-").map { it.toInt() }
        val cal = Calendar.getInstance().apply { clear(); set(y, m - 1, d) }
        when (cal.get(Calendar.DAY_OF_WEEK)) {
            Calendar.MONDAY -> "L"
            Calendar.TUESDAY -> "M"
            Calendar.WEDNESDAY -> "X"
            Calendar.THURSDAY -> "J"
            Calendar.FRIDAY -> "V"
            Calendar.SATURDAY -> "S"
            else -> "D"
        }
    }.getOrDefault("?")

    /** Altura relativa (0–1) de cada barra de XP diario respecto al máximo. */
    fun barHeights(days: List<DailyActivity>): List<Float> {
        val max = days.maxOfOrNull { it.xp } ?: 0
        return days.map { if (max <= 0) 0f else it.xp.toFloat() / max }
    }

    fun accuracy(value: Double?): String = value?.let { "${it.roundToInt()} %" } ?: "—"
}
