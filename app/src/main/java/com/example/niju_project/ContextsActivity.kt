package com.example.niju_project

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.niju_project.ui.contexts.ContextsUiState
import com.example.niju_project.ui.contexts.ContextsViewModel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class ContextsActivity : AppCompatActivity() {

    private val viewModel: ContextsViewModel by viewModels()
    private lateinit var adapter: ContextosAdapter
    private lateinit var rvContexts: RecyclerView
    private lateinit var progressBar: ProgressBar

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_contexts)

        initViews()
        setupRecyclerView()
        setupNavigation()
        observeViewModel()
    }

    private fun initViews() {
        rvContexts = findViewById(R.id.rvContexts)
        progressBar = findViewById(R.id.progressBar)
    }

    private fun setupRecyclerView() {
        adapter = ContextosAdapter(emptyList()) { contexto ->
            val intent = Intent(this, PracticeActivity::class.java).apply {
                putExtra("context_id", contexto.id)
                putExtra("context_name", contexto.name)
            }
            startActivity(intent)
        }
        rvContexts.layoutManager = GridLayoutManager(this, 2)
        rvContexts.adapter = adapter
    }

    private fun observeViewModel() {
        lifecycleScope.launch {
            viewModel.uiState.collectLatest { state ->
                when (state) {
                    is ContextsUiState.Loading -> progressBar.visibility = View.VISIBLE
                    is ContextsUiState.Success -> {
                        progressBar.visibility = View.GONE
                        adapter.updateList(state.contexts)
                    }
                    is ContextsUiState.Error -> {
                        progressBar.visibility = View.GONE
                        Toast.makeText(this@ContextsActivity, state.message, Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    private fun setupNavigation() {
        findViewById<LinearLayout>(R.id.navHome).setOnClickListener {
            startActivity(Intent(this, HomeActivity::class.java))
            finish()
        }
        findViewById<LinearLayout>(R.id.navProfile).setOnClickListener {
            startActivity(Intent(this, ProfileActivity::class.java))
            finish()
        }
    }
}
