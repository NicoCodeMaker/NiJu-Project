package com.example.niju_project.ui

import android.app.Activity
import android.view.View
import android.view.inputmethod.InputMethodManager
import androidx.core.content.getSystemService
import com.example.niju_project.R
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputLayout

/** Términos y privacidad en un solo diálogo (login, registro y configuración). */
fun Activity.showLegalDialog() {
    MaterialAlertDialogBuilder(this)
        .setTitle(R.string.terms_title)
        .setMessage(getString(R.string.terms_text) + "\n\n" + getString(R.string.privacy_title) + "\n\n" + getString(R.string.privacy_text))
        .setPositiveButton(R.string.ok, null)
        .show()
}

/** Muestra (o limpia) el error de un campo y devuelve true si NO hay error. */
fun TextInputLayout.check(error: String?): Boolean {
    this.error = error
    isErrorEnabled = error != null
    return error == null
}

fun Activity.hideKeyboard() {
    val view = currentFocus ?: return
    getSystemService<InputMethodManager>()?.hideSoftInputFromWindow(view.windowToken, 0)
    view.clearFocus()
}

/** Pequeña animación de "no" para errores de formulario. */
fun View.shake() {
    animate().cancel()
    translationX = 0f
    animate().translationX(14f).setDuration(50).withEndAction {
        animate().translationX(-14f).setDuration(80).withEndAction {
            animate().translationX(8f).setDuration(60).withEndAction {
                animate().translationX(0f).setDuration(50).start()
            }.start()
        }.start()
    }.start()
}
