package com.example.niju_project

import android.os.Bundle
import android.widget.ImageButton
import androidx.appcompat.app.AppCompatActivity

class FavoritesActivity : AppCompatActivity() {

    private lateinit var backButton: ImageButton

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_favorites)

        backButton = findViewById(R.id.back_button)
        backButton.setOnClickListener { finish() }
    }
}
