package com.example.niju_project

import android.app.Activity
import android.content.Intent
import com.example.niju_project.data.model.SessionMode
import com.example.niju_project.ui.practice.PracticeViewModel

/** Abre una sesión de estudio. */
fun Activity.openSession(
    mode: SessionMode,
    contextId: Int? = null,
    contextSlug: String? = null,
    lessonId: Int? = null,
    title: String? = null
) {
    startActivity(Intent(this, PracticeActivity::class.java).apply {
        putExtra(PracticeViewModel.EXTRA_MODE, mode.apiValue)
        contextId?.let { putExtra(PracticeViewModel.EXTRA_CONTEXT_ID, it) }
        contextSlug?.let { putExtra(PracticeViewModel.EXTRA_CONTEXT_SLUG, it) }
        lessonId?.let { putExtra(PracticeViewModel.EXTRA_LESSON_ID, it) }
        title?.let { putExtra(PracticeViewModel.EXTRA_TITLE, it) }
    })
}

/** Vuelve a la pantalla de inicio de sesión y borra la pila de pantallas. */
fun Activity.goToLogin() {
    startActivity(Intent(this, LoginActivity::class.java).apply {
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
    })
    finish()
}

/** Navegación entre las 4 pestañas inferiores sin apilar pantallas repetidas. */
fun Activity.openTab(target: Class<out Activity>) {
    if (this::class.java == target) return
    startActivity(Intent(this, target).apply {
        flags = Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
    })
    overridePendingTransition(0, 0)
}

/** Imagen ilustrativa de cada contexto (por su slug en la base de datos). */
fun contextImage(slug: String?): Int = when (slug) {
    "restaurant" -> R.drawable.restaurant
    "supermarket" -> R.drawable.supermarket
    "airport" -> R.drawable.airport
    else -> R.drawable.beach_scene
}
