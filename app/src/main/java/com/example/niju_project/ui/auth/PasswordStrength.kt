package com.example.niju_project.ui.auth

import android.view.View
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.widget.doAfterTextChanged
import com.example.niju_project.R
import com.example.niju_project.domain.auth.AuthValidation
import com.google.android.material.progressindicator.LinearProgressIndicator
import com.google.android.material.textfield.TextInputEditText

/** Conecta la barra de fuerza de contraseña (strengthRow) a un campo. */
internal fun bindPasswordStrength(field: TextInputEditText, row: View) {
    val bar = row.findViewById<LinearProgressIndicator>(R.id.progressStrength)
    val label = row.findViewById<TextView>(R.id.tvStrength)
    val labels = intArrayOf(R.string.auth_strength_0, R.string.auth_strength_1, R.string.auth_strength_2,
        R.string.auth_strength_3, R.string.auth_strength_4)
    val colors = intArrayOf(R.color.color_error, R.color.color_error, R.color.color_warning,
        R.color.color_success, R.color.color_success)
    field.doAfterTextChanged { text ->
        val value = text?.toString().orEmpty()
        row.visibility = if (value.isEmpty()) View.GONE else View.VISIBLE
        val s = AuthValidation.strength(value)
        bar.setProgressCompat(s, true)
        val color = ContextCompat.getColor(row.context, colors[s])
        bar.setIndicatorColor(color)
        label.setText(labels[s])
        label.setTextColor(color)
    }
}
