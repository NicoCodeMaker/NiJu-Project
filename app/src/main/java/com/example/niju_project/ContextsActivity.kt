package com.example.niju_project

import android.os.Bundle
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import android.widget.TextView


class ContextsActivity : AppCompatActivity() {

    private lateinit var navHome: TextView
    private lateinit var navContexts: TextView
    private lateinit var navFavorites: TextView
    private lateinit var navProfile: TextView


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_contexts)

        initViews()
        setupClickListeners()
    }

    private fun initViews() {
        navHome = findViewById(R.id.navHome)
        navContexts = findViewById(R.id.navContexts)
        navFavorites = findViewById(R.id.navFavorites)
        navProfile = findViewById(R.id.navProfile)

    }

    private fun setupClickListeners() {
        navHome.setOnClickListener {
            finish() // vuelve a Home
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
        }

        navContexts.setOnClickListener {
            showToast("Ya estás en Contextos")
        }

        navFavorites.setOnClickListener {
            showToast("Navegando a Favoritos")
        }

        navProfile.setOnClickListener {
            showToast("Navegando a Perfil")
        }
    }

    private fun showToast(msg: String) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
    }
}
