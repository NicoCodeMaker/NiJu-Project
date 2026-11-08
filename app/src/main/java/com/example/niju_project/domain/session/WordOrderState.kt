package com.example.niju_project.domain.session

/**
 * Estado del ejercicio «ordenar palabras»: qué palabras ya se colocaron y en qué orden.
 * Trabaja con índices para admitir palabras repetidas ("the … the").
 * No decide si el orden es correcto: eso lo hace el servidor. Código puro.
 */
class WordOrderState(val tokens: List<String>) {

    private val placed = mutableListOf<Int>()

    /** Índices de [tokens] en el orden en que el estudiante los colocó. */
    val answerIndexes: List<Int> get() = placed.toList()

    /** Índices aún disponibles, en el orden mezclado original. */
    val availableIndexes: List<Int> get() = tokens.indices.filter { it !in placed }

    val isComplete: Boolean get() = tokens.isNotEmpty() && placed.size == tokens.size

    /** Respuesta que se envía al servidor: las palabras colocadas separadas por espacios. */
    val answer: String get() = placed.joinToString(" ") { tokens[it] }

    fun place(index: Int): Boolean {
        if (index !in tokens.indices || index in placed) return false
        placed += index
        return true
    }

    fun remove(index: Int): Boolean = placed.remove(index)

    fun undo(): Boolean = if (placed.isEmpty()) false else { placed.removeAt(placed.lastIndex); true }

    fun clear() = placed.clear()
}
