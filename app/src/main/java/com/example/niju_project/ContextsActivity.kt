package com.example.niju_project

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.niju_project.data.model.SessionMode
import com.example.niju_project.ui.UiState
import com.example.niju_project.ui.contexts.ContextsViewModel
import kotlinx.coroutines.launch

/** Catálogo de contextos. Cada contexto abre su lista de lecciones. */
class ContextsActivity : AppCompatActivity() {

    private val viewModel: ContextsViewModel by viewModels()
    private lateinit var adapter: ContextosAdapter
    private lateinit var progressBar: ProgressBar

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_contexts)

        progressBar = findViewById(R.id.progressBar)
        adapter = ContextosAdapter(emptyList()) { ctx ->
            startActivity(Intent(this, LessonActivity::class.java).apply {
                putExtra(LessonActivity.EXTRA_CONTEXT_ID, ctx.id)
                putExtra(LessonActivity.EXTRA_CONTEXT_SLUG, ctx.slug)
                putExtra(LessonActivity.EXTRA_CONTEXT_NAME, ctx.name)
                putExtra(LessonActivity.EXTRA_CONTEXT_DESCRIPTION, ctx.description)
            })
        }
        findViewById<Button>(R.id.btnEmptyAction).setOnClickListener { viewModel.load() }
        findViewById<RecyclerView>(R.id.rvContexts).apply {
            layoutManager = GridLayoutManager(this@ContextsActivity, 2)
            adapter = this@ContextsActivity.adapter
        }
        findViewById<View>(R.id.btnStartSession).setOnClickListener {
            openSession(SessionMode.PRACTICE, title = "Práctica libre")
        }

        setupBottomNav(R.id.navContexts)
        observe()
    }

    private fun showEmpty(message: String?, retry: Boolean) {
        findViewById<View>(R.id.emptyState).visibility = if (message == null) View.GONE else View.VISIBLE
        findViewById<TextView>(R.id.tvEmptyMessage).text = message.orEmpty()
        findViewById<Button>(R.id.btnEmptyAction).visibility = if (retry) View.VISIBLE else View.GONE
    }

    private fun observe() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    progressBar.visibility = if (state is UiState.Loading) View.VISIBLE else View.GONE
                    when (state) {
                        is UiState.Success -> {
                            adapter.updateList(state.data)
                            showEmpty(if (state.data.isEmpty()) getString(R.string.contexts_empty) else null, false)
                        }
                        is UiState.Error ->
                            if (state.sessionExpired) goToLogin() else showEmpty(state.message, true)
                        else -> Unit
                    }
                }
            }
        }
    }
}
