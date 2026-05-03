package com.example.niju_project.domain.service

import com.example.niju_project.data.model.ProgressModel
import com.google.firebase.Timestamp
import java.util.Calendar
import java.util.Date

class SpacedRepetitionService {

    companion object {
        const val MIN_EASE = 1.3    // easeFactor nunca baja de esto
        const val INIT_EASE = 2.5   // valor inicial estándar SM-2
    }

    /**
     * Actualiza el estado SM-2 de una palabra.
     * @param existing  Estado actual (null = primera vez)
     * @param wordId    ID de la flashcard
     * @param correct   true si el usuario respondió correctamente
     * @return Nuevo ProgressModel con nextReview calculado
     */
    fun update(
        existing: ProgressModel?,
        wordId: String,
        correct: Boolean
    ): ProgressModel {
        val current = existing ?: ProgressModel(
            wordId = wordId,
            easeFactor = INIT_EASE,
            intervalDays = 1,
            repetitions = 0
        )

        // SM-2 usa escala 0-5; simplificado: correcto=4, incorrecto=1
        val quality = if (correct) 4 else 1

        return if (quality < 3) {
            // Respuesta incorrecta - reiniciar a 1 día
            val newIncorrectCount = current.incorrectCount + 1
            current.copy(
                repetitions = 0,
                intervalDays = 1,
                incorrectCount = newIncorrectCount,
                mastery = calcMastery(current.repetitions, newIncorrectCount),
                lastReviewed = Timestamp.now(),
                nextReview = daysFromNow(1)
            )
        } else {
            // Respuesta correcta - aplicar fórmula SM-2
            val newInterval = when (current.repetitions) {
                0 -> 1
                1 -> 6
                else -> (current.intervalDays * current.easeFactor).toInt()
            }

            val newEase = current.easeFactor + (0.1 - (5 - quality) * (0.08 + (5 - quality) * 0.02))
            val finalEase = if (newEase < MIN_EASE) MIN_EASE else newEase

            val newRepetitions = current.repetitions + 1

            current.copy(
                repetitions = newRepetitions,
                intervalDays = newInterval,
                easeFactor = finalEase,
                mastery = calcMastery(newRepetitions, current.incorrectCount),
                lastReviewed = Timestamp.now(),
                nextReview = daysFromNow(newInterval)
            )
        }
    }

    private fun calcMastery(reps: Int, incorrects: Int): Double {
        if (reps == 0) return 0.0
        val total = reps + incorrects
        val ratio = reps.toDouble() / total.toDouble()
        // Una fórmula simple de progreso: 0 a 100%
        return (ratio * 100).coerceIn(0.0, 100.0)
    }

    private fun daysFromNow(days: Int): Timestamp {
        val calendar = Calendar.getInstance()
        calendar.add(Calendar.DAY_OF_YEAR, days)
        return Timestamp(calendar.time)
    }
}
