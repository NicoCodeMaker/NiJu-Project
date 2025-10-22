package com.example.niju_project

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.niju_project.databinding.ActivityRutaBinding
import com.google.android.material.progressindicator.LinearProgressIndicator

class RutaActivity : AppCompatActivity() {

    private lateinit var binding: ActivityRutaBinding
    private lateinit var progressIndicator: LinearProgressIndicator

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRutaBinding.inflate(layoutInflater)
        setContentView(binding.root)

        progressIndicator = binding.progressIndicator

        // Lista de contextos reales
        val listaContextos = listOf(
            Contexto("Restaurante", R.drawable.ic_restaurant),
            Contexto("Supermercado", R.drawable.ic_supermarket),
            Contexto("Playa", R.drawable.ic_beach)
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
    }
}
