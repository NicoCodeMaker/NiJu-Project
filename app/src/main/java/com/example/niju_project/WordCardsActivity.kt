package com.example.niju_project

import android.content.res.ColorStateList
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.SpannableString
import android.text.Spanned
import android.text.style.BackgroundColorSpan
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.view.animation.DecelerateInterpolator
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
import com.example.niju_project.audio.PlaybackState
import com.example.niju_project.ui.scene.ContextSceneView
import com.example.niju_project.ui.scene.ContextTheme
import com.example.niju_project.ui.scene.SceneUi
import com.example.niju_project.ui.widget.SoundWaveView
import com.google.android.material.button.MaterialButton
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

    // Diseño propio del contexto y animaciones de aprendizaje.
    private val theme by lazy { ContextTheme.of(intent.getStringExtra(EXTRA_CONTEXT_SLUG)) }
    private lateinit var card: View
    private lateinit var wave: SoundWaveView
    private lateinit var btnReveal: View
    private lateinit var meaningBox: View
    private val revealed = mutableSetOf<Int>()   // palabras cuyo significado ya se descubrió
    private var lastCardNumber = 0
    private var sentencePlaying = false

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
        card = findViewById(R.id.card)
        wave = findViewById(R.id.waveWord)
        btnReveal = findViewById(R.id.btnReveal)
        meaningBox = findViewById(R.id.meaningBox)
        applyContextDesign()
        btnReveal.setOnClickListener { reveal(animated = true) }

        // Pronunciación: «Escuchar» la palabra y la oración; tocar de nuevo = repetir.
        audio = PronunciationUi(this)
        audio.bind(findViewById(R.id.btnListenWord), AudioKind.WORD) { currentWord?.english }
        audio.bind(findViewById(R.id.btnListenExample), AudioKind.SENTENCE) { currentWord?.sentenceEn }
        audio.onPlayback = { state ->
            wave.playing = state is PlaybackState.Playing && state.kind == AudioKind.WORD
            val sentence = state is PlaybackState.Playing && state.kind == AudioKind.SENTENCE
            if (sentence != sentencePlaying) { sentencePlaying = sentence; currentWord?.let { highlightExample(it) } }
        }

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

    /** Colores del contexto en la tarjeta, botones y barra; la escena animada arriba. */
    private fun applyContextDesign() {
        val accent = SceneUi.accentColor(this, theme)
        findViewById<ContextSceneView>(R.id.sceneHero).slug = intent.getStringExtra(EXTRA_CONTEXT_SLUG)
        tvCardIndex.setTextColor(accent)
        tvSpanish.setTextColor(accent)
        wave.color = accent
        findViewById<View>(R.id.btnListenWord).background =
            GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(theme.accent) }
        findViewById<TextView>(R.id.tvCardEmoji).background =
            GradientDrawable().apply { cornerRadius = 18 * resources.displayMetrics.density; setColor(theme.accentAlpha(30)) }
        meaningBox.background =
            GradientDrawable().apply { cornerRadius = 14 * resources.displayMetrics.density; setColor(theme.accentAlpha(24)) }
        (btnReveal as MaterialButton).apply {
            setTextColor(accent); iconTint = ColorStateList.valueOf(accent); strokeColor = ColorStateList.valueOf(accent)
        }
        SceneUi.tint(btnNextCard as MaterialButton, theme)
        (btnPrevious as MaterialButton).apply { setTextColor(accent); strokeColor = ColorStateList.valueOf(accent) }
        // Al desplazarse, la barra superior toma el color del contexto para no perderse sobre la tarjeta.
        val topBar = findViewById<View>(R.id.topBar)
        val limit = 150 * resources.displayMetrics.density
        (cardContainer as android.widget.ScrollView).setOnScrollChangeListener { _, _, y, _, _ ->
            topBar.setBackgroundColor(if (y > limit) theme.accent else 0)
        }
    }

    /** Recuerdo activo: el significado aparece girando la tarjeta del significado. */
    private fun reveal(animated: Boolean) {
        currentWord?.let { revealed += it.wordId }
        btnReveal.visibility = View.GONE
        meaningBox.visibility = View.VISIBLE
        tvExampleEs.visibility = if (currentWord?.sentenceEs.isNullOrBlank()) View.GONE else View.VISIBLE
        if (animated) {
            meaningBox.rotationX = -90f; meaningBox.alpha = 0f
            meaningBox.animate().rotationX(0f).alpha(1f).setDuration(320).setInterpolator(DecelerateInterpolator()).start()
            tvExampleEs.alpha = 0f
            tvExampleEs.animate().alpha(1f).setStartDelay(180).setDuration(260).start()
        }
    }

    /** Resalta la palabra dentro del ejemplo (más fuerte mientras suena la oración). */
    private fun highlightExample(w: LessonWord) {
        val sentence = w.sentenceEn.orEmpty()
        val span = SpannableString(sentence)
        val i = sentence.lowercase().indexOf(w.english.lowercase())
        if (i >= 0) {
            val end = i + w.english.length
            span.setSpan(BackgroundColorSpan(theme.accentAlpha(if (sentencePlaying) 90 else 40)), i, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            span.setSpan(StyleSpan(Typeface.BOLD), i, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            span.setSpan(ForegroundColorSpan(SceneUi.titleColor(this, theme)), i, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        tvExampleEn.text = span
    }

    /** La tarjeta entra desde el lado hacia donde se avanza. */
    private fun animateCardChange(number: Int) {
        if (lastCardNumber == 0 || lastCardNumber == number) { lastCardNumber = number; return }
        val dir = if (number > lastCardNumber) 1 else -1
        lastCardNumber = number
        card.translationX = dir * 48 * resources.displayMetrics.density
        card.alpha = 0f
        card.animate().translationX(0f).alpha(1f).setDuration(240).setInterpolator(DecelerateInterpolator()).start()
    }

    private fun render(s: CardsUiState.Card) {
        val w = s.word
        val changed = currentWord?.wordId != w.wordId
        tvCardIndex.text = getString(R.string.cards_index, s.number, s.total)
        progressCards.progress = s.number * 100 / s.total
        tvStatus.text = getString(R.string.cards_status_sep, w.status.label)
        tvEnglish.text = w.english
        findViewById<TextView>(R.id.tvCardEmoji).apply {
            text = w.emoji.orEmpty()
            visibility = if (w.emoji.isNullOrBlank()) View.GONE else View.VISIBLE
        }
        tvPronunciation.text = w.pronunciation.orEmpty()
        tvPronunciation.visibility = if (w.pronunciation.isNullOrBlank()) View.GONE else View.VISIBLE
        val pos = Formatters.partOfSpeech(w.partOfSpeech)
        tvPartOfSpeech.text = pos.orEmpty()
        tvPartOfSpeech.visibility = if (pos == null) View.GONE else View.VISIBLE
        tvSpanish.text = w.spanish
        val hasExample = !w.sentenceEn.isNullOrBlank()
        listOf(tvExampleLabel, rowExample).forEach {
            it.visibility = if (hasExample) View.VISIBLE else View.GONE
        }
        tvExampleEs.text = w.sentenceEs.orEmpty()

        btnFavorite.setImageResource(if (w.isFavorite) R.drawable.ic_star_filled else R.drawable.ic_star)
        btnFavorite.contentDescription =
            getString(if (w.isFavorite) R.string.favorite_remove else R.string.favorite_add)

        // Audio: actualiza los botones y, si el estudiante lo activó, suena la palabra (una vez por tarjeta).
        currentWord = w
        highlightExample(w)
        if (w.wordId in revealed) reveal(animated = false) else {
            btnReveal.visibility = View.VISIBLE
            meaningBox.visibility = View.GONE
            tvExampleEs.visibility = View.GONE
        }
        if (changed) animateCardChange(s.number)
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
