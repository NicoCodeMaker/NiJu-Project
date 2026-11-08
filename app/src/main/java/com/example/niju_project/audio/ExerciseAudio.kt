package com.example.niju_project.audio

import com.example.niju_project.data.model.ExerciseType

/** Qué audio corresponde reproducir al mostrar el resultado de un ejercicio. */
data class AudioTarget(val text: String, val kind: AudioKind)

/** Marca del hueco que genera build_exercise en el servidor para «completar la oración». */
private const val BLANK = "_____"

object ExerciseAudio {

    /**
     * Audio que acompaña a la respuesta correcta (siempre en inglés):
     *  · en_to_es                      -> la palabra del enunciado.
     *  · es_to_en, imagen, escuchar, escribir -> la respuesta correcta (la palabra).
     *  · fill_blank                    -> la oración completa (enunciado con el hueco rellenado).
     *  · dialogue                      -> la línea B completa.
     *  · word_order                    -> la oración ordenada.
     *  · spot_error                    -> la oración corregida.
     */
    fun forAnswer(type: ExerciseType, prompt: String, correctAnswer: String, solution: String? = null): AudioTarget =
        when (type) {
            ExerciseType.EN_TO_ES -> AudioTarget(prompt, AudioKind.WORD)
            ExerciseType.ES_TO_EN, ExerciseType.IMAGE_MATCH, ExerciseType.LISTEN_CHOOSE,
            ExerciseType.TYPE_ANSWER -> AudioTarget(correctAnswer, AudioKind.WORD)
            ExerciseType.FILL_BLANK ->
                if (prompt.contains(BLANK)) AudioTarget(prompt.replace(BLANK, correctAnswer), AudioKind.SENTENCE)
                else AudioTarget(correctAnswer, AudioKind.WORD)
            ExerciseType.DIALOGUE -> {
                val lineB = prompt.lineSequence().lastOrNull { it.startsWith("B:") }?.removePrefix("B:")?.trim()
                if (lineB != null && lineB.contains(BLANK)) AudioTarget(lineB.replace(BLANK, correctAnswer), AudioKind.SENTENCE)
                else AudioTarget(correctAnswer, AudioKind.WORD)
            }
            ExerciseType.WORD_ORDER -> AudioTarget(correctAnswer, AudioKind.SENTENCE)
            ExerciseType.SPOT_ERROR ->
                if (!solution.isNullOrBlank()) AudioTarget(solution, AudioKind.SENTENCE)
                else AudioTarget(correctAnswer, AudioKind.WORD)
        }

    /** Audio del enunciado antes de responder (null = no se ofrece, para no regalar la respuesta). */
    fun forQuestion(type: ExerciseType, prompt: String): AudioTarget? = when (type) {
        ExerciseType.EN_TO_ES, ExerciseType.LISTEN_CHOOSE -> AudioTarget(prompt, AudioKind.WORD)
        else -> null
    }
}
