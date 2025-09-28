package com.example.niju_project

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main) // tu layout con el texto "Bienvenido a NiJu English"

        val btnComenzar = findViewById<Button>(R.id.startButton) // id del botón
        btnComenzar.setOnClickListener {
            // Abrir la otra pantalla
            val intent = Intent(this, HomeActivity::class.java)
            startActivity(intent)
            finish() // evita volver atrás al splash
        }
    }
}
