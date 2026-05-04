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

        // ── Aplicar tema guardado ANTES de que se cree cualquier Activity ──
        val prefs = getSharedPreferences("niju_prefs", Context.MODE_PRIVATE)
        val darkMode = prefs.getBoolean("dark_mode", false)
        AppCompatDelegate.setDefaultNightMode(
            if (darkMode) AppCompatDelegate.MODE_NIGHT_YES
            else AppCompatDelegate.MODE_NIGHT_NO
        )

        // ── Firebase ─────────────────────────────────────────────────────────
        FirebaseApp.initializeApp(this)

        val settings = FirebaseFirestoreSettings.Builder()
            .setLocalCacheSettings(PersistentCacheSettings.newBuilder().build())
            .build()

        FirebaseFirestore.getInstance().firestoreSettings = settings
    }
}
