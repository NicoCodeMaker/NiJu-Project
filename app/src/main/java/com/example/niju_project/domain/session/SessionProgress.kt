package com.example.niju_project.domain.session

import com.example.niju_project.data.model.AnswerResult
import com.example.niju_project.data.model.SessionItem

/**
 * Avance local de una sesión de estudio. No decide qué es correcto ni cuánto
 * XP vale (eso lo hace el servidor); solo lleva la cuenta de lo que ya ocurrió
 * para mostrarlo en pantalla. Es código puro: se prueba sin Android.
 */
class SessionProgress(val items: List<SessionItem>) {

    init {
        require(items.isNotEmpty()) { "Una sesión necesita al menos un ejercicio" }
    }

    var index: Int = 0
        private set
    var correct: Int = 0
        private set
    var answered: Int = 0
        private set
    var xpEarned: Int = 0
        private set

    private var currentAnswered = false

    val total: Int get() = items.size
    val current: SessionItem get() = items[index]
    val number: Int get() = index + 1
    val isLast: Boolean get() = index == items.lastIndex
    val isCurrentAnswered: Boolean get() = currentAnswered

    /** Porcentaje de ejercicios ya respondidos (0–100). */
    val percent: Int get() = answered * 100 / total

    /** Registra la corrección del servidor para el ejercicio actual (una sola vez). */
    fun record(result: AnswerResult) {
        check(!currentAnswered) { "El ejercicio actual ya fue respondido" }
        currentAnswered = true
        answered++
        if (result.isCorrect) correct++
        xpEarned += result.xpAwarded
    }

    /**
     * El servidor ya tenía una respuesta para este ejercicio (envío repetido).
     * Se cuenta como respondido sin conocer el resultado.
     */
    fun markAnsweredWithoutResult() {
        if (currentAnswered) return
        currentAnswered = true
        answered++
    }

    /** Pasa al siguiente ejercicio. Devuelve false si ya no quedan. */
    fun advance(): Boolean {
        check(currentAnswered) { "Responde el ejercicio antes de avanzar" }
        if (isLast) return false
        index++
        currentAnswered = false
        return true
    }
}
