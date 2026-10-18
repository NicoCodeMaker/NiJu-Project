package com.example.niju_project

import android.app.Application
import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import com.example.niju_project.audio.AudioCatalogHolder
import com.example.niju_project.reminders.StudyReminders

class MainApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        applySavedTheme()
        AudioCatalogHolder.preload(this)   // lee assets/audio/manifest.json sin bloquear el arranque
        StudyReminders.createChannel(this)
        StudyReminders.reschedule(this)
    }

    /**
     * Tema guardado en Configuración:
     *  -1 = seguir al sistema (por defecto), 1 = claro, 2 = oscuro.
     */
    private fun applySavedTheme() {
        val prefs = getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        AppCompatDelegate.setDefaultNightMode(
            prefs.getInt(KEY_THEME, AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
        )
    }

    companion object {
        const val PREFS = "niju_prefs"
        const val KEY_THEME = "theme_mode"
    }
}
