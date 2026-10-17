package com.example.niju_project

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageButton
import android.widget.ProgressBar
import android.widget.TextView
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.niju_project.data.model.FavoriteWord
import com.example.niju_project.ui.UiState
import com.example.niju_project.ui.stats.FavoritesViewModel
import kotlinx.coroutines.launch

/** Palabras marcadas con estrella en las tarjetas de las lecciones. */
class FavoritesActivity : AppCompatActivity() {

    private val viewModel: FavoritesViewModel by viewModels()
    private val adapter = FavoritesAdapter { viewModel.remove(it) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_favorites)

        findViewById<TextView>(R.id.tvHeaderTitle).setText(R.string.favorites_title)
        findViewById<ImageButton>(R.id.btnBack).setOnClickListener { finish() }
        findViewById<RecyclerView>(R.id.rvFavorites).apply {
            layoutManager = LinearLayoutManager(this@FavoritesActivity)
            adapter = this@FavoritesActivity.adapter
        }
        findViewById<Button>(R.id.btnEmptyAction).setOnClickListener { viewModel.load() }
        setupBottomNav(null)
        observe()
    }

    override fun onResume() {
        super.onResume()
        viewModel.load()
    }

    private fun observe() {
        val progress = findViewById<ProgressBar>(R.id.progressBar)
        val empty = findViewById<View>(R.id.emptyState)
        val tvEmpty = findViewById<TextView>(R.id.tvEmptyMessage)
        val btnRetry = findViewById<Button>(R.id.btnEmptyAction)
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    progress.visibility = if (state is UiState.Loading) View.VISIBLE else View.GONE
                    when (state) {
                        is UiState.Success -> {
                            adapter.submit(state.data)
                            empty.visibility = if (state.data.isEmpty()) View.VISIBLE else View.GONE
                            tvEmpty.setText(R.string.favorites_empty)
                            btnRetry.visibility = View.GONE
                        }
                        is UiState.Error -> {
                            if (state.sessionExpired) goToLogin()
                            empty.visibility = View.VISIBLE
                            tvEmpty.text = state.message
                            btnRetry.visibility = View.VISIBLE
                        }
                        UiState.Loading -> Unit
                    }
                }
            }
        }
    }
}

private class FavoritesAdapter(
    private val onRemove: (FavoriteWord) -> Unit
) : RecyclerView.Adapter<FavoritesAdapter.VH>() {

    private var items: List<FavoriteWord> = emptyList()

    fun submit(list: List<FavoriteWord>) {
        items = list
        notifyDataSetChanged()
    }

    class VH(v: View) : RecyclerView.ViewHolder(v) {
        val english: TextView = v.findViewById(R.id.tvFavEnglish)
        val spanish: TextView = v.findViewById(R.id.tvFavSpanish)
        val example: TextView = v.findViewById(R.id.tvFavExample)
        val status: TextView = v.findViewById(R.id.tvFavStatus)
        val remove: ImageButton = v.findViewById(R.id.btnFavRemove)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        VH(LayoutInflater.from(parent.context).inflate(R.layout.item_favorite_word, parent, false))

    override fun onBindViewHolder(h: VH, position: Int) {
        val w = items[position]
        h.english.text = listOfNotNull(w.english, w.pronunciation).joinToString("  ")
        h.spanish.text = w.spanish
        h.example.text = w.sentenceEn.orEmpty()
        h.example.visibility = if (w.sentenceEn.isNullOrBlank()) View.GONE else View.VISIBLE
        h.status.text = w.status.label
        h.remove.setOnClickListener { onRemove(w) }
    }

    override fun getItemCount() = items.size
}
