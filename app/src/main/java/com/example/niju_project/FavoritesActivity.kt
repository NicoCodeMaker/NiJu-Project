package com.example.niju_project

import android.content.Intent
import android.os.Bundle
import android.widget.ImageButton
import android.widget.LinearLayout
import androidx.appcompat.app.AppCompatActivity

class FavoritesActivity : AppCompatActivity() {

    private lateinit var backButton: ImageButton
    private lateinit var navHome: LinearLayout
    private lateinit var navContexts: LinearLayout
    private lateinit var navRuta: LinearLayout
    private lateinit var navProfile: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_favorites)

        // Inicializamos vistas
        initViews()
        setupNavigation()

        // Botón atrás
        backButton.setOnClickListener { finish() }
    }

    private fun initViews() {
        backButton = findViewById(R.id.btnBack)
        navHome = findViewById(R.id.navHome)
        navContexts = findViewById(R.id.navContexts)
        navRuta = findViewById(R.id.navRuta)
        navProfile = findViewById(R.id.navProfile)
    }

    private fun setupNavigation() {
        navHome.setOnClickListener {
            val intent = Intent(this, HomeActivity::class.java)
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            startActivity(intent)
            overridePendingTransition(0, 0)
        }

        navContexts.setOnClickListener {
            val intent = Intent(this, ContextsActivity::class.java)
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            startActivity(intent)
            overridePendingTransition(0, 0)
        }

        navRuta.setOnClickListener {
            val intent = Intent(this, RutaActivity::class.java)
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            startActivity(intent)
            overridePendingTransition(0, 0)
        }

        navProfile.setOnClickListener {
            val intent = Intent(this, ProfileActivity::class.java)
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            startActivity(intent)
            overridePendingTransition(0, 0)
        }
    }

    override fun onResume() {
        super.onResume()
        highlightCurrentTab()
    }

    private fun highlightCurrentTab() {
        // Aquí puedes resaltar el botón de "Favoritos" si luego lo agregas al bottom nav
        // (por ejemplo, cambiar color o ícono activo)
    }
}
