package com.example.niju_project.ui.scene

import android.content.Context
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.example.niju_project.R
import com.google.android.material.button.MaterialButton
import com.google.android.material.progressindicator.LinearProgressIndicator

/** Ayudas para teñir la interfaz con el tema de un contexto. */
object SceneUi {

    fun isNight(context: Context): Boolean =
        (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES

    /** Color de títulos del contexto: su tinta en modo claro; el texto normal en modo oscuro. */
    fun titleColor(context: Context, theme: ContextTheme): Int =
        if (isNight(context)) ContextCompat.getColor(context, R.color.color_text_primary) else theme.ink

    /** Acento legible: en modo oscuro se usa el inicio del cielo, más claro. */
    fun accentColor(context: Context, theme: ContextTheme): Int =
        if (isNight(context)) theme.skyStart else theme.accent

    fun tint(bar: LinearProgressIndicator, theme: ContextTheme) {
        bar.setIndicatorColor(accentColor(bar.context, theme))
    }

    /** Botón principal con el color del contexto; deshabilitado se ve gris (no solo más claro). */
    fun tint(button: MaterialButton, theme: ContextTheme) {
        val ctx = button.context
        val states = arrayOf(intArrayOf(-android.R.attr.state_enabled), intArrayOf())
        button.backgroundTintList = ColorStateList(states,
            intArrayOf(ContextCompat.getColor(ctx, R.color.color_disabled_bg), theme.accent))
        button.setTextColor(ColorStateList(states,
            intArrayOf(ContextCompat.getColor(ctx, R.color.color_text_hint), 0xFFFFFFFF.toInt())))
        button.iconTint = ColorStateList(states,
            intArrayOf(ContextCompat.getColor(ctx, R.color.color_text_hint), 0xFFFFFFFF.toInt()))
    }

    fun title(view: TextView, theme: ContextTheme) {
        view.setTextColor(titleColor(view.context, theme))
    }
}
