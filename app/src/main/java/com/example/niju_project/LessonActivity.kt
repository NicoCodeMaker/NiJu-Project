package com.example.niju_project

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.niju_project.data.model.LessonStatus
import com.example.niju_project.data.model.SessionMode
import com.example.niju_project.data.repository.LearningRepository
import com.example.niju_project.ui.Formatters
import com.example.niju_project.ui.LoaderViewModel
import com.example.niju_project.ui.UiState
import com.example.niju_project.ui.factoryOf
import kotlinx.coroutines.launch

/**
 * Lecciones de un contexto, en orden. La primera está abierta; cada una se
 * desbloquea al superar la anterior con al menos 70 % (regla del servidor).
 * Extras: context_id, context_slug, context_name, context_description.
 */
class LessonActivity : AppCompatActivity() {

    private val contextId by lazy { intent.getIntExtra(EXTRA_CONTEXT_ID, -1) }
    private val contextSlug by lazy { intent.getStringExtra(EXTRA_CONTEXT_SLUG) }
    private val contextName by lazy { intent.getStringExtra(EXTRA_CONTEXT_NAME) ?: getString(R.string.lessons_title) }

    private val viewModel: LoaderViewModel<List<LessonStatus>> by viewModels {
        factoryOf { LoaderViewModel { LearningRepository().getContextLessons(contextId) } }
    }

    private lateinit var progressBar: ProgressBar
    private lateinit var emptyState: View
    private lateinit var tvEmpty: TextView
    private lateinit var btnEmptyAction: Button
    private val adapter = LessonsAdapter { onLessonClick(it) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_lesson)
        if (contextId <= 0) { finish(); return }

        findViewById<TextView>(R.id.tvHeaderTitle).text = contextName
        findViewById<ImageButton>(R.id.btnBack).setOnClickListener { finish() }
        findViewById<ImageView>(R.id.ivContextImage).setImageResource(contextImage(contextSlug))
        findViewById<TextView>(R.id.tvContextDescription).apply {
            text = intent.getStringExtra(EXTRA_CONTEXT_DESCRIPTION).orEmpty()
            visibility = if (text.isNullOrBlank()) View.GONE else View.VISIBLE
        }
        progressBar = findViewById(R.id.progressBar)
        emptyState = findViewById(R.id.emptyState)
        tvEmpty = findViewById(R.id.tvEmptyMessage)
        btnEmptyAction = findViewById(R.id.btnEmptyAction)
        btnEmptyAction.setOnClickListener { viewModel.load() }

        findViewById<RecyclerView>(R.id.rvLessons).apply {
            layoutManager = LinearLayoutManager(this@LessonActivity)
            adapter = this@LessonActivity.adapter
        }
        findViewById<Button>(R.id.btnPracticeContext).setOnClickListener {
            openSession(SessionMode.PRACTICE, contextId = contextId, contextSlug = contextSlug, title = contextName)
        }

        setupBottomNav(R.id.navContexts)
        observe()
    }

    override fun onResume() {
        super.onResume()
        viewModel.load()   // al volver de una lección, refleja el desbloqueo
    }

    private fun observe() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    progressBar.visibility = if (state is UiState.Loading) View.VISIBLE else View.GONE
                    when (state) {
                        is UiState.Success -> {
                            adapter.submit(state.data)
                            showEmpty(if (state.data.isEmpty()) getString(R.string.lessons_empty) else null, retry = false)
                        }
                        is UiState.Error ->
                            if (state.sessionExpired) goToLogin() else showEmpty(state.message, retry = true)
                        UiState.Loading -> Unit
                    }
                }
            }
        }
    }

    private fun showEmpty(message: String?, retry: Boolean) {
        emptyState.visibility = if (message == null) View.GONE else View.VISIBLE
        tvEmpty.text = message.orEmpty()
        btnEmptyAction.visibility = if (retry) View.VISIBLE else View.GONE
    }

    private fun onLessonClick(lesson: LessonStatus) {
        if (!lesson.unlocked) {
            Toast.makeText(this, R.string.lessons_locked, Toast.LENGTH_SHORT).show()
            return
        }
        startActivity(Intent(this, WordCardsActivity::class.java).apply {
            putExtra(WordCardsActivity.EXTRA_LESSON_ID, lesson.lessonId)
            putExtra(WordCardsActivity.EXTRA_LESSON_TITLE, lesson.title)
            putExtra(WordCardsActivity.EXTRA_CONTEXT_ID, contextId)
            putExtra(WordCardsActivity.EXTRA_CONTEXT_SLUG, contextSlug)
        })
    }

    companion object {
        const val EXTRA_CONTEXT_ID = "context_id"
        const val EXTRA_CONTEXT_SLUG = "context_slug"
        const val EXTRA_CONTEXT_NAME = "context_name"
        const val EXTRA_CONTEXT_DESCRIPTION = "context_description"
    }
}

private class LessonsAdapter(
    private val onClick: (LessonStatus) -> Unit
) : RecyclerView.Adapter<LessonsAdapter.VH>() {

    private var items: List<LessonStatus> = emptyList()

    fun submit(list: List<LessonStatus>) {
        items = list.sortedBy { it.sortOrder }
        notifyDataSetChanged()
    }

    class VH(v: View) : RecyclerView.ViewHolder(v) {
        val number: TextView = v.findViewById(R.id.tvLessonNumber)
        val title: TextView = v.findViewById(R.id.tvLessonTitle)
        val description: TextView = v.findViewById(R.id.tvLessonDescription)
        val status: TextView = v.findViewById(R.id.tvLessonStatus)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        VH(LayoutInflater.from(parent.context).inflate(R.layout.item_lesson, parent, false))

    override fun onBindViewHolder(h: VH, position: Int) {
        val l = items[position]
        val ctx = h.itemView.context
        // Estado por forma y texto, no solo por color: ✓ superada, número disponible, 🔒 bloqueada.
        h.number.text = when {
            l.completed -> "✓"
            l.unlocked -> (position + 1).toString()
            else -> "🔒"
        }
        h.number.setBackgroundResource(
            when {
                l.completed -> R.drawable.bg_icon_success
                l.unlocked -> R.drawable.bg_icon_blue
                else -> R.drawable.bg_icon_neutral
            }
        )
        h.title.text = l.title
        h.description.text = l.description
        h.description.visibility = if (l.description.isBlank()) View.GONE else View.VISIBLE
        h.status.text = Formatters.lessonStatus(l)
        h.status.setTextColor(
            ContextCompat.getColor(ctx, if (l.completed) R.color.color_success else R.color.color_text_secondary)
        )
        h.itemView.alpha = if (l.unlocked) 1f else 0.6f
        h.itemView.contentDescription = "${l.title}. ${Formatters.lessonStatus(l)}"
        h.itemView.setOnClickListener { onClick(l) }
    }

    override fun getItemCount() = items.size
}
