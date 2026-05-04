package com.example.niju_project

import android.app.Application
import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import com.google.firebase.FirebaseApp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.google.firebase.firestore.PersistentCacheSettings

class MainApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        
        // 1. Aplicar el tema inmediatamente al iniciar
        applySavedTheme()

        // 2. Inicializar Firebase y otros servicios
        FirebaseApp.initializeApp(this)
        val settings = FirebaseFirestoreSettings.Builder()
            .setLocalCacheSettings(PersistentCacheSettings.newBuilder().build())
            .build()
        FirebaseFirestore.getInstance().firestoreSettings = settings
    }

    private fun applySavedTheme() {
        val prefs = getSharedPreferences("niju_prefs", Context.MODE_PRIVATE)
        
        /**
         * Recuperamos el modo guardado.
         *  -1: MODE_NIGHT_FOLLOW_SYSTEM (Default)
         *   1: MODE_NIGHT_NO (Modo Claro)
         *   2: MODE_NIGHT_YES (Modo Oscuro)
         */
        val themeMode = prefs.getInt("theme_mode", AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
        AppCompatDelegate.setDefaultNightMode(themeMode)
    }
}
