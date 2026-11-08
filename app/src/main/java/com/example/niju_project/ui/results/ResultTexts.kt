package com.example.niju_project.ui.results

import com.example.niju_project.data.model.SessionMode
import com.example.niju_project.data.model.SessionSummary
import kotlin.math.roundToInt

/** Textos del resultado de una sesión. Código puro: se prueba sin Android. */
object ResultTexts {

    data class Header(val emoji: String, val title: String, val celebrate: Boolean)

    fun header(s: SessionSummary): Header {
        val score = s.score.roundToInt()
        return when {
            s.mode == SessionMode.EXAM && s.examPassed -> Header("🎓", "¡Evaluación aprobada!", true)
            s.mode == SessionMode.EXAM -> Header("📝", "Evaluación terminada", false)
            s.mode == SessionMode.LESSON && s.lessonCompleted -> Header("🎉", "¡Lección superada!", true)
            s.perfect -> Header("🌟", "¡Sesión perfecta!", true)
            s.answered == 0 -> Header("👋", "Sesión cerrada", false)
            score >= 70 -> Header("💪", "¡Muy bien!", s.leveledUp)
            else -> Header("📚", "¡Sigue practicando!", s.leveledUp)
        }
    }

    fun message(s: SessionSummary): String {
        val score = s.score.roundToInt()
        val base = "Respondiste bien ${s.correct} de ${s.total}."
        return when (s.mode) {
            SessionMode.LESSON -> when {
                s.lessonCompleted -> "$base Superaste la lección: la siguiente ya está abierta."
                score >= 70 -> "$base Ya habías superado esta lección; repetirla refuerza tu memoria."
                else -> "$base Necesitas 70 % para superar la lección. ¡Puedes repetirla!"
            }
            SessionMode.EXAM -> when {
                s.examPassed -> "$base Aprobaste la evaluación del contexto (se exige 80 %)."
                score >= 80 -> "$base Ya tenías esta evaluación aprobada."
                else -> "$base Se necesita 80 % para aprobar. Repasa las palabras y vuelve a intentarlo."
            }
            SessionMode.REVIEW -> "$base Las palabras que recordaste se volverán a repasar más adelante; las que fallaste, mañana."
            SessionMode.PRACTICE -> "$base La práctica libre suma XP sin adelantar tus repasos programados."
        }
    }

    /** ¿Ofrecer repetir? (lección no superada, evaluación no aprobada, o práctica/repaso). */
    fun canRepeat(s: SessionSummary): Boolean = when (s.mode) {
        SessionMode.LESSON -> !s.lessonCompleted && s.score < 70
        SessionMode.EXAM -> !s.examPassed && s.score < 80
        SessionMode.PRACTICE -> true
        SessionMode.REVIEW -> false
    }
}
