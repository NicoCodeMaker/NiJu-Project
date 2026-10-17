package com.example.niju_project

/** Lee respuestas JSON reales del servidor guardadas en src/test/resources/fixtures. */
object TestFixtures {
    fun read(name: String): String =
        requireNotNull(javaClass.classLoader?.getResource("fixtures/$name.json")) {
            "No existe el fixture $name.json"
        }.readText()
}
