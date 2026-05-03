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
import com.example.niju_project.data.model.ContextModel

class RutaActivity : AppCompatActivity() {

    private lateinit var binding: ActivityRutaBinding
    private lateinit var progressIndicator: LinearProgressIndicator

    private lateinit var navHome: LinearLayout
    private lateinit var navContexts: LinearLayout
    private lateinit var navRuta: LinearLayout
    private lateinit var navProfile: LinearLayout
    private var backButton: ImageButton? = null

    // 🔹 Lista de contextos con su porcentaje fijo
    private val listaContextos = listOf(
        ContextModel(
            id = "restaurant",
            name = "Restaurante",
            description = "Aprende vocabulario de restaurantes"
        ),
        ContextModel(
            id = "supermarket",
            name = "Supermercado",
            description = "Aprende vocabulario de supermercado"
        ),
        ContextModel(
            id = "airport",
            name = "Aeropuerto",
            description = "Aprende vocabulario de aeropuerto"
        )
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRutaBinding.inflate(layoutInflater)
        setContentView(binding.root)

        progressIndicator = binding.progressIndicator

        // 🔹 Inicializar navegación inferior
        navHome = binding.bottomNavigation.navHome
        navContexts = binding.bottomNavigation.navContexts
        navRuta = binding.bottomNavigation.navRuta
        navProfile = binding.bottomNavigation.navProfile
        setupNavigation()

        // 🔹 Adapter con callback
        val adapter = ContextosAdapter(listaContextos) { contexto ->

            Toast.makeText(
                this,
                "Seleccionaste ${contexto.name}",
                Toast.LENGTH_SHORT
            ).show()

            actualizarProgreso(40)
        }

        binding.rvContextos.layoutManager =
            LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        binding.rvContextos.adapter = adapter

        updateBottomNavColors(
            current = navRuta,
            navHome, navContexts, navRuta, navProfile
        )
    }

    private fun actualizarProgreso(porcentaje: Int) {
        progressIndicator.setProgress(porcentaje, true)
        binding.tvPercent.text = "$porcentaje%"
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
        // Aquí puedes resaltar el icono actual si lo deseas
    }
}
