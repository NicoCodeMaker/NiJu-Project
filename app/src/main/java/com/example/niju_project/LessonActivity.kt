package com.example.niju_project

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class LessonActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_lesson)

        // Referencias UI
        val btnContinue = findViewById<Button>(R.id.btnContinue)
        val progressBar = findViewById<ProgressBar>(R.id.lessonProgress)
        val progressText = findViewById<TextView>(R.id.txtProgress)



        // Simular progreso actual (luego se puede obtener de BD o ViewModel)
        var progress = 40
        progressBar.progress = progress
        progressText.text = "$progress% completado"

        // Acción del botón continuar
        btnContinue.setOnClickListener {
            // Aquí puedes redirigir al módulo de práctica o cuestionario
            val intent = Intent(this, PracticeActivity::class.java)
            startActivity(intent)
        }

        // 🔹 Barra inferior de navegación
        val navHome = findViewById<LinearLayout>(R.id.navHome)
        val navContexts = findViewById<LinearLayout>(R.id.navContexts)
        val navRuta = findViewById<LinearLayout>(R.id.navRuta)
        val navProfile = findViewById<LinearLayout>(R.id.navProfile)

        navHome.setOnClickListener {
            startActivity(Intent(this, HomeActivity::class.java))
            overridePendingTransition(0, 0)
            finish()
        }

        navContexts.setOnClickListener {
            startActivity(Intent(this, ContextsActivity::class.java))
            overridePendingTransition(0, 0)
            finish()
        }

        navRuta.setOnClickListener {
            startActivity(Intent(this, RutaActivity::class.java))
            overridePendingTransition(0, 0)
            finish()
        }

        navProfile.setOnClickListener {
            startActivity(Intent(this, ProfileActivity::class.java))
            overridePendingTransition(0, 0)
            finish()
        }
    }
}
