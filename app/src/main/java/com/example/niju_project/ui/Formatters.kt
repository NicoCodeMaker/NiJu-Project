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

    /** Tiempo de estudio legible: "45 s", "12 min", "2 h 5 min". */
    fun duration(ms: Long): String {
        val totalSec = (ms / 1000).coerceAtLeast(0)
        val h = totalSec / 3600
        val m = (totalSec % 3600) / 60
        val sec = totalSec % 60
        return when {
            h > 0 -> if (m > 0) "$h h $m min" else "$h h"
            m > 0 -> "$m min"
            else -> "$sec s"
        }
    }

    fun days(n: Int) = if (n == 1) "1 día" else "$n días"

    /** Fecha ISO (yyyy-MM-dd…) a "8 nov". */
    fun shortDate(iso: String?): String {
        if (iso.isNullOrBlank() || iso.length < 10) return "—"
        val months = listOf("ene", "feb", "mar", "abr", "may", "jun", "jul", "ago", "sep", "oct", "nov", "dic")
        return runCatching {
            val (_, mo, d) = iso.take(10).split("-").map { it.toInt() }
            "$d ${months[mo - 1]}"
        }.getOrDefault("—")
    }

    /** Días que faltan para un repaso (o "hoy" si ya venció), a partir de dos fechas ISO. */
    fun dueIn(dueIso: String?, todayIso: String): String {
        val due = epochDay(dueIso) ?: return "—"
        val today = epochDay(todayIso) ?: return "—"
        val days = due - today
        return when {
            days <= 0 -> "hoy"
            days == 1L -> "mañana"
            else -> "en $days días"
        }
    }

    /** Día (contado desde 1970-01-01) de una fecha ISO; sin java.time para soportar API 24. */
    fun epochDay(iso: String?): Long? {
        if (iso.isNullOrBlank() || iso.length < 10) return null
        return runCatching {
            val (y, m, d) = iso.take(10).split("-").map { it.toInt() }
            val cal = Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC")).apply { clear(); set(y, m - 1, d) }
            cal.timeInMillis / 86_400_000L
        }.getOrNull()
    }

    /** Etiqueta de la calidad SM-2 (0–5). */
    fun quality(q: Int?): String = when (q) {
        5 -> "Fácil"
        4 -> "Bien"
        3 -> "Con esfuerzo"
        2 -> "Fallo"
        1, 0 -> "Fallo (producción)"
        else -> "—"
    }

    /** Motivo de un premio del libro de puntos. */
    fun rewardReason(reason: String): Pair<String, String> = when (reason) {
        "lesson_complete" -> "📘" to "Lección superada"
        "achievement" -> "🏆" to "Logro desbloqueado"
        "daily_challenge" -> "🎯" to "Reto diario cumplido"
        "weekly_goal" -> "📅" to "Meta semanal cumplida"
        "streak_bonus" -> "🔥" to "Bono de racha"
        "exam_pass" -> "🎓" to "Evaluación aprobada"
        else -> "⭐" to "Recompensa"
    }
}
