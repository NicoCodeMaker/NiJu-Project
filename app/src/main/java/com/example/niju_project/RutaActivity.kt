package com.example.niju_project

import android.content.Intent
import android.os.Bundle
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.niju_project.databinding.ActivityRutaBinding
import com.google.android.material.progressindicator.LinearProgressIndicator

class RutaActivity : AppCompatActivity() {

    private lateinit var binding: ActivityRutaBinding
    private lateinit var progressIndicator: LinearProgressIndicator

    // 🔹 Botones de navegación inferior
    private lateinit var navHome: LinearLayout
    private lateinit var navContexts: LinearLayout
    private lateinit var navRuta: LinearLayout
    private lateinit var navProfile: LinearLayout

    // 🔹 Botón de retroceso (opcional)
    private var backButton: ImageButton? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRutaBinding.inflate(layoutInflater)
        setContentView(binding.root)

        progressIndicator = binding.progressIndicator

        // 🔹 Inicializar Bottom Navigation
        navHome = binding.bottomNavigation.navHome
        navContexts = binding.bottomNavigation.navContexts
        navRuta = binding.bottomNavigation.navRuta
        navProfile = binding.bottomNavigation.navProfile

        setupNavigation()

        // 🔹 Lista de contextos reales
        val listaContextos = listOf(
            Contexto("Restaurante", R.drawable.ic_restaurant),
            Contexto("Supermercado", R.drawable.ic_supermarket),
            Contexto("Aeropuerto", R.drawable.ic_airport)
        )

        val adapter = ContextosAdapter(listaContextos) {
            actualizarProgreso()
        }

        binding.rvContextos.layoutManager =
            LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        binding.rvContextos.adapter = adapter
    }

    private fun actualizarProgreso() {
        var progresoActual = progressIndicator.progress
        if (progresoActual < 100) progresoActual += 20
        progressIndicator.setProgress(progresoActual, true)
        binding.tvPercent.text = "$progresoActual%"
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
            Toast.makeText(this, "Ya estás en Ruta", Toast.LENGTH_SHORT).show()
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
        // Aquí puedes cambiar el color o ícono activo de la pestaña actual
    }
}
