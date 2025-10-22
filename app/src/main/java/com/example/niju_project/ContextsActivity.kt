package com.example.niju_project

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import androidx.appcompat.app.AppCompatActivity

class ContextsActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_contexts)

        // 🔹 Referencias a la barra inferior
        val navHome = findViewById<LinearLayout>(R.id.navHome)
        val navContexts = findViewById<LinearLayout>(R.id.navContexts)
        val navRuta = findViewById<LinearLayout>(R.id.navRuta)
        val navProfile = findViewById<LinearLayout>(R.id.navProfile)

        updateBottomNavColors(
            current = navContexts,
            navHome, navContexts, navRuta, navProfile
        )

        // 🔹 Botón principal
        val btnStartSession = findViewById<Button>(R.id.btnStartSession)

        // 🧭 Navegación inferior
        navHome.setOnClickListener {
            if (this !is HomeActivity) {
                startActivity(Intent(this, HomeActivity::class.java))
                overridePendingTransition(0, 0)
                finish()
            }
        }

        navContexts.setOnClickListener {
            // Ya estás en ContextsActivity → no hace nada
        }

        navRuta.setOnClickListener {
            if (this !is RutaActivity) {
                startActivity(Intent(this, RutaActivity::class.java))
                overridePendingTransition(0, 0)
                finish()
            }
        }

        navProfile.setOnClickListener {
            if (this !is ProfileActivity) {
                startActivity(Intent(this, ProfileActivity::class.java))
                overridePendingTransition(0, 0)
                finish()
            }
        }

        // 🔹 Acción del botón principal
        btnStartSession.setOnClickListener {
            // Aquí puedes dirigir a la pantalla de sesión del día (por ejemplo, LessonActivity)
            val intent = Intent(this, LessonActivity::class.java)
            startActivity(intent)
        }


    }


}
