package com.tuapp.learning

import android.os.Bundle
import android.widget.LinearLayout
import android.widget.ProgressBar
import androidx.appcompat.app.AppCompatActivity
import com.tuapp.R

class LearningRouteActivity : AppCompatActivity() {

    private lateinit var progressBar: ProgressBar
    private lateinit var module1: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_learning_route)

        progressBar = findViewById(R.id.learningProgressBar)
        module1 = findViewById(R.id.module1)

        // Ejemplo: cuando se toca el módulo, avanza el progreso
        module1.setOnClickListener {
            progressBar.progress = 40 // Simula avance
        }
    }
}
