package com.example.niju_project

import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ImageButton
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.niju_project.audio.AudioKind
import com.example.niju_project.audio.PronunciationUi
import com.example.niju_project.data.model.LessonWord
import com.example.niju_project.data.model.SessionMode
import com.example.niju_project.data.repository.LearningRepository
import com.example.niju_project.ui.Formatters
import com.example.niju_project.ui.factoryOf
import com.example.niju_project.ui.lessons.CardsUiState
import com.example.niju_project.ui.lessons.WordCardsViewModel
import kotlinx.coroutines.launch

/**
 * Paso 1 de una lección: el estudiante conoce cada palabra (significado,
 * pronunciación y ejemplo). Al final empieza los ejercicios (paso 2).
 */
class WordCardsActivity : AppCompatActivity() {

    private val lessonId by lazy { intent.getIntExtra(EXTRA_LESSON_ID, -1) }
    private val lessonTitle by lazy { intent.getStringExtra(EXTRA_LESSON_TITLE) ?: getString(R.string.lessons_title) }

    private val viewModel: WordCardsViewModel by viewModels {
        factoryOf {
            val repo = LearningRepository()
            WordCardsViewModel(
                loadContent = { repo.getLessonContent(lessonId) },
                toggleFavorite = { repo.toggleFavorite(it) }
            )
        }
    }

    private lateinit var progressBar: ProgressBar
    private lateinit var cardContainer: View
    private lateinit var navButtons: View
    private lateinit var emptyState: View
    private lateinit var tvEmpty: TextView
    private lateinit var btnEmptyAction: Button
    private lateinit var tvCardIndex: TextView
    private lateinit var progressCards: ProgressBar
    private lateinit var tvStatus: TextView
    private lateinit var btnFavorite: ImageButton
    private lateinit var tvEnglish: TextView
    private lateinit var tvPronunciation: TextView
    private lateinit var tvPartOfSpeech: TextView
    private lateinit var tvSpanish: TextView
    private lateinit var tvExampleLabel: TextView
    private lateinit var tvExampleEn: TextView
    private lateinit var tvExampleEs: TextView
    private lateinit var btnPrevious: Button
    private lateinit var btnNextCard: Button
    private lateinit var rowExample: View

    private lateinit var audio: PronunciationUi
    private var currentWord: LessonWord? = null
    private var lastAutoPlayedWordId = -1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_word_cards)
        if (lessonId <= 0) { finish(); return }

        findViewById<TextView>(R.id.tvHeaderTitle).text = lessonTitle
        findViewById<ImageButton>(R.id.btnBack).setOnClickListener { finish() }
        progressBar = findViewById(R.id.progressBar)
        cardContainer = findViewById(R.id.cardContainer)
        navButtons = findViewById(R.id.navButtons)
        emptyState = findViewById(R.id.emptyState)
        tvEmpty = findViewById(R.id.tvEmptyMessage)
        btnEmptyAction = findViewById(R.id.btnEmptyAction)
        tvCardIndex = findViewById(R.id.tvCardIndex)
        progressCards = findViewById(R.id.progressCards)
        tvStatus = findViewById(R.id.tvStatus)
        btnFavorite = findViewById(R.id.btnFavorite)
        tvEnglish = findViewById(R.id.tvEnglish)
        tvPronunciation = findViewById(R.id.tvPronunciation)
        tvPartOfSpeech = findViewById(R.id.tvPartOfSpeech)
        tvSpanish = findViewById(R.id.tvSpanish)
        tvExampleLabel = findViewById(R.id.tvExampleLabel)
        tvExampleEn = findViewById(R.id.tvExampleEn)
        tvExampleEs = findViewById(R.id.tvExampleEs)
        btnPrevious = findViewById(R.id.btnPrevious)
        btnNextCard = findViewById(R.id.btnNextCard)
        rowExample = findViewById(R.id.rowExample)

        // Pronunciación: «Escuchar» la palabra y la oración; tocar de nuevo = repetir.
        audio = PronunciationUi(this)
        audio.bind(findViewById(R.id.btnListenWord), AudioKind.WORD) { currentWord?.english }
        audio.bind(findViewById(R.id.btnListenExample), AudioKind.SENTENCE) { currentWord?.sentenceEn }

        btnEmptyAction.setOnClickListener { viewModel.load() }
        btnFavorite.setOnClickListener { viewModel.toggleFavoriteCurrent() }
        btnPrevious.setOnClickListener { viewModel.previous() }
        btnNextCard.setOnClickListener {
            val s = viewModel.uiState.value
            if (s is CardsUiState.Card && s.isLast) startExercises() else viewModel.next()
        }
        observe()
    }

    private fun observe() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.uiState.collect { state ->
                        progressBar.visibility = if (state is CardsUiState.Loading) View.VISIBLE else View.GONE
                        val showCard = state is CardsUiState.Card
                        cardContainer.visibility = if (showCard) View.VISIBLE else View.GONE
                        navButtons.visibility = if (showCard) View.VISIBLE else View.GONE
                        emptyState.visibility = if (state is CardsUiState.Error) View.VISIBLE else View.GONE
                        when (state) {
                            is CardsUiState.Card -> render(state)
                            is CardsUiState.Error -> {
                                if (state.sessionExpired) goToLogin()
                                tvEmpty.text = state.message
                                btnEmptyAction.visibility = View.VISIBLE
                            }
                            CardsUiState.Loading -> Unit
                        }
                    }
                }
                launch {
                    viewModel.messages.collect { msg ->
                        if (msg != null) {
                            Toast.makeText(this@WordCardsActivity, msg, Toast.LENGTH_SHORT).show()
                            viewModel.messageShown()
                        }
                    }
                }
            }
        }
    }

    private fun render(s: CardsUiState.Card) {
        val w = s.word
        tvCardIndex.text = getString(R.string.cards_index, s.number, s.total)
        progressCards.progress = s.number * 100 / s.total
        tvStatus.text = w.status.label
        tvEnglish.text = w.english
        tvPronunciation.text = w.pronunciation.orEmpty()
        tvPronunciation.visibility = if (w.pronunciation.isNullOrBlank()) View.GONE else View.VISIBLE
        val pos = Formatters.partOfSpeech(w.partOfSpeech)
        tvPartOfSpeech.text = pos.orEmpty()
        tvPartOfSpeech.visibility = if (pos == null) View.GONE else View.VISIBLE
        tvSpanish.text = w.spanish
        val hasExample = !w.sentenceEn.isNullOrBlank()
        listOf(tvExampleLabel, rowExample, tvExampleEs).forEach {
            it.visibility = if (hasExample) View.VISIBLE else View.GONE
        }
        tvExampleEn.text = w.sentenceEn.orEmpty()
        tvExampleEs.text = w.sentenceEs.orEmpty()

        btnFavorite.setImageResource(if (w.isFavorite) R.drawable.ic_star_filled else R.drawable.ic_star)
        btnFavorite.contentDescription =
            getString(if (w.isFavorite) R.string.favorite_remove else R.string.favorite_add)

        // Audio: actualiza los botones y, si el estudiante lo activó, suena la palabra (una vez por tarjeta).
        currentWord = w
        audio.refresh()
        if (audio.autoPlay && lastAutoPlayedWordId != w.wordId) {
            lastAutoPlayedWordId = w.wordId
            audio.play(w.english, AudioKind.WORD)
        }

        btnPrevious.isEnabled = !s.isFirst
        btnPrevious.alpha = if (s.isFirst) 0.4f else 1f
        btnNextCard.text = getString(if (s.isLast) R.string.cards_start_exercises else R.string.cards_next)
    }

    private fun startExercises() {
        openSession(
            SessionMode.LESSON,
            contextId = intent.getIntExtra(EXTRA_CONTEXT_ID, -1).takeIf { it > 0 },
            contextSlug = intent.getStringExtra(EXTRA_CONTEXT_SLUG),
            lessonId = lessonId,
            title = lessonTitle
        )
        finish()
    }

    companion object {
        const val EXTRA_LESSON_ID = "lesson_id"
        const val EXTRA_LESSON_TITLE = "lesson_title"
        const val EXTRA_CONTEXT_ID = "context_id"
        const val EXTRA_CONTEXT_SLUG = "context_slug"
    }
}
