package com.example.niju_project

import android.app.Application
import com.google.firebase.FirebaseApp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.google.firebase.firestore.PersistentCacheSettings

class MainApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        
        // Inicializar Firebase
        FirebaseApp.initializeApp(this)

        // Configuración de Firestore con persistencia offline (según tu guía)
        val settings = FirebaseFirestoreSettings.Builder()
            .setLocalCacheSettings(PersistentCacheSettings.newBuilder().build())
            .build()
        
        FirebaseFirestore.getInstance().firestoreSettings = settings
    }
}
