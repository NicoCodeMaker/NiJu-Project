package com.example.niju_project

import android.content.Intent
import android.os.Bundle
import android.widget.LinearLayout
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class RutaActivity : AppCompatActivity() {

    private lateinit var navHome: LinearLayout
    private lateinit var navContexts: LinearLayout
    private lateinit var navRuta: LinearLayout
    private lateinit var navProfile: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_ruta)

        initViews()
        setupNavigation()
    }

    private fun initViews() {
        navHome = findViewById(R.id.navHome)
        navContexts = findViewById(R.id.navContexts)
        navRuta = findViewById(R.id.navRuta)
        navProfile = findViewById(R.id.navProfile)
    }

    private fun setupNavigation() {
        navHome.setOnClickListener {
            startActivity(Intent(this, HomeActivity::class.java))
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
        }

        navContexts.setOnClickListener {
            startActivity(Intent(this, ContextsActivity::class.java))
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
        }

        navRuta.setOnClickListener {
            Toast.makeText(this, "Ya estás en tu ruta", Toast.LENGTH_SHORT).show()
        }

        navProfile.setOnClickListener {
            startActivity(Intent(this, ProfileActivity::class.java))
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
        }
    }

    override fun onResume() {
        super.onResume()
        highlightCurrentTab()
    }

    private fun highlightCurrentTab() {
        // Aquí luego puedes implementar el cambio visual del ícono activo
        // (por ejemplo, cambiar color o fondo del tab de "Ruta")
    }
}
