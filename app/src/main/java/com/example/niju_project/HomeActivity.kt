package com.example.niju_project

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class HomeActivity : AppCompatActivity() {

    private lateinit var btnStartSession: Button
    private lateinit var ivBeachImage: ImageView
    private lateinit var navHome: LinearLayout
    private lateinit var navContexts: LinearLayout
    private lateinit var navRuta: LinearLayout
    private lateinit var navProfile: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_home)

        initViews()
        setupClickListeners()
    }

    private fun initViews() {
        btnStartSession = findViewById(R.id.btnStartSession)
        ivBeachImage = findViewById(R.id.ivBeachImage)
        navHome = findViewById(R.id.navHome)
        navContexts = findViewById(R.id.navContexts)
        navRuta = findViewById(R.id.navRuta)
        navProfile = findViewById(R.id.navProfile)
    }

    private fun setupClickListeners() {
        btnStartSession.setOnClickListener {
            showToast("Iniciando sesión de hoy...")
            // startActivity(Intent(this, LessonActivity::class.java))
        }

        ivBeachImage.setOnClickListener {
            showToast("Mostrando detalles de la playa")
            // startActivity(Intent(this, BeachDetailActivity::class.java))
        }

        // 🔹 Barra inferior
        navHome.setOnClickListener {
            // Ya estás aquí → no hace nada
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

    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    override fun onResume() {
        super.onResume()
        // Aquí puedes actualizar visualmente el tab activo si luego implementas íconos seleccionados
    }
}
