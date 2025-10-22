package com.example.niju_project

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class PracticeActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_practice)

        val txtQuestion = findViewById<TextView>(R.id.txtQuestion)
        val btnOption1 = findViewById<Button>(R.id.btnOption1)
        val btnOption2 = findViewById<Button>(R.id.btnOption2)
        val btnOption3 = findViewById<Button>(R.id.btnOption3)
        val btnNext = findViewById<Button>(R.id.btnNext)

        // Ejemplo simple de pregunta
        txtQuestion.text = "¿Cómo se dice 'Hola' en inglés?"
        btnOption1.text = "Hello"
        btnOption2.text = "Bye"
        btnOption3.text = "Thanks"

        var selectedAnswer = ""

        // Manejo de selección
        val options = listOf(btnOption1, btnOption2, btnOption3)
        options.forEach { button ->
            button.setOnClickListener {
                selectedAnswer = button.text.toString()
                options.forEach { it.setBackgroundTintList(getColorStateList(android.R.color.darker_gray)) }
                button.setBackgroundTintList(getColorStateList(R.color.teal_700))
            }
        }

        btnNext.setOnClickListener {
            if (selectedAnswer.isEmpty()) {
                Toast.makeText(this, "Selecciona una respuesta", Toast.LENGTH_SHORT).show()
            } else if (selectedAnswer == "Hello") {
                Toast.makeText(this, "✅ Correcto", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "❌ Incorrecto", Toast.LENGTH_SHORT).show()
            }
        }

        // 🔹 Barra inferior reutilizable
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
