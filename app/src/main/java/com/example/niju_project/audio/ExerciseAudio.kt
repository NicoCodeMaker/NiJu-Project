package com.example.niju_project.audio

import com.example.niju_project.data.model.ExerciseType

/** Qué audio corresponde reproducir al mostrar el resultado de un ejercicio. */
data class AudioTarget(val text: String, val kind: AudioKind)

/** Marca del hueco que genera build_exercise en el servidor para «completar la oración». */
private const val BLANK = "_____"

object ExerciseAudio {

    /**
     * Audio que acompaña a la respuesta correcta (siempre en inglés):
     *  · en_to_es   -> la palabra del enunciado.
     *  · es_to_en   -> la respuesta correcta (la palabra en inglés).
     *  · fill_blank -> la oración completa (enunciado con el hueco rellenado).
     */
    fun forAnswer(type: ExerciseType, prompt: String, correctAnswer: String): AudioTarget = when (type) {
        ExerciseType.EN_TO_ES -> AudioTarget(prompt, AudioKind.WORD)
        ExerciseType.ES_TO_EN -> AudioTarget(correctAnswer, AudioKind.WORD)
        ExerciseType.FILL_BLANK ->
            if (prompt.contains(BLANK)) AudioTarget(prompt.replace(BLANK, correctAnswer), AudioKind.SENTENCE)
            else AudioTarget(correctAnswer, AudioKind.WORD)
    }
}
