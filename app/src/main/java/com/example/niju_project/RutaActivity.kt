package com.example.niju_project

import android.os.Bundle
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.niju_project.data.model.ContextProgress
import com.example.niju_project.databinding.ActivityRutaBinding
import com.example.niju_project.ui.UiState
import com.example.niju_project.ui.contexts.RutaViewModel
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/** Avance real por contexto: lecciones superadas y palabras dominadas. */
class RutaActivity : AppCompatActivity() {

    private lateinit var binding: ActivityRutaBinding
    private val viewModel: RutaViewModel by viewModels()
    private var progressById: Map<Int, ContextProgress> = emptyMap()
    private var selectedId: Int? = null

    private val adapter = ContextosAdapter(emptyList()) { ctx ->
        selectedId = ctx.id
        progressById[ctx.id]?.let { show(it) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRutaBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.rvContextos.layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        binding.rvContextos.adapter = adapter

        setupBottomNav(R.id.navRuta)
        observe()
    }

    override fun onResume() {
        super.onResume()
        viewModel.load()
    }

    private fun observe() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    when (state) {
                        is UiState.Success -> render(state.data)
                        is UiState.Error ->
                            if (state.sessionExpired) goToLogin()
                            else Toast.makeText(this@RutaActivity, state.message, Toast.LENGTH_SHORT).show()
                        UiState.Loading -> Unit
                    }
                }
            }
        }
    }

    private fun render(list: List<ContextProgress>) {
        progressById = list.associateBy { it.contextId }
        adapter.updateList(list.map { it.toContextRow() })
        val selected = selectedId?.let { progressById[it] } ?: list.firstOrNull()
        if (selected != null) show(selected) else showOverall(list)
    }

    private fun show(p: ContextProgress) {
        val pct = p.percentComplete.roundToInt().coerceIn(0, 100)
        binding.progressIndicator.setProgress(pct, true)
        binding.tvPercent.text = "$pct%"
        binding.tvProgreso.text =
            "${p.name}: ${p.lessonsCompleted}/${p.lessonsTotal} lecciones · " +
            "${p.wordsMastered}/${p.wordsTotal} palabras dominadas"
    }

    private fun showOverall(list: List<ContextProgress>) {
        binding.progressIndicator.setProgress(0, false)
        binding.tvPercent.text = "0%"
        binding.tvProgreso.text = if (list.isEmpty()) "Aún no hay contextos disponibles" else ""
    }
}
