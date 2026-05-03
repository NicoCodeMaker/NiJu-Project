package com.example.niju_project

import android.animation.ObjectAnimator
import android.content.Intent
import android.content.res.ColorStateList
import android.media.AudioAttributes
import android.media.SoundPool
import android.os.Bundle
import android.view.View
import android.view.animation.OvershootInterpolator
import android.widget.*
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.niju_project.ui.practice.PracticeUiState
import com.example.niju_project.ui.practice.PracticeViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class PracticeActivity : AppCompatActivity() {

    private val viewModel: PracticeViewModel by viewModels { PracticeViewModel.Factory }

    // ── Vistas principales ──────────────────────────────────────────────────
    private lateinit var layoutContent: LinearLayout
    private lateinit var loadingOverlay: FrameLayout
    private lateinit var ivWordImage: ImageView
    private lateinit var txtQuestion: TextView
    private lateinit var tvWordCategory: TextView
    private lateinit var tvDifficulty: TextView
    private lateinit var tvQuestionIndex: TextView
    private lateinit var progressSession: ProgressBar
    private lateinit var btnOption1: Button
    private lateinit var btnOption2: Button
    private lateinit var btnOption3: Button
    private lateinit var btnNext: Button
    private lateinit var btnClose: ImageButton
    private lateinit var optionButtons: List<Button>

    // ── Panels de feedback ──────────────────────────────────────────────────
    private lateinit var correctPanel: FrameLayout
    private lateinit var wrongPanel: FrameLayout
    private lateinit var tvCorrectWord: TextView
    private lateinit var tvCorrectXp: TextView
    private lateinit var tvCorrectAnswer: TextView
    private lateinit var btnContinuePractice: Button
    private lateinit var btnFinishPractice: Button
    private lateinit var btnContinueAfterWrong: Button

    // ── SoundPool ───────────────────────────────────────────────────────────
    private lateinit var soundPool: SoundPool
    private var soundCorrectId: Int = 0
    private var soundWrongId: Int = 0
    private var soundsLoaded = false

    private var selectedAnswer = ""

    // Mapeo contexto → drawable de imagen
    private val contextImageMap = mapOf(
        "restaurant"  to R.drawable.restaurant,
        "airport"     to R.drawable.airport,
        "supermarket" to R.drawable.supermarket,
        "beach"       to R.drawable.beach_scene,
        "playa"       to R.drawable.beach_scene
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_practice)

        initSounds()
        bindViews()
        setupNavigation()
        observeViewModel()
    }

    // ────────────────────────────────────────────────────────────────────────
    // SONIDOS
    // ────────────────────────────────────────────────────────────────────────
    private fun initSounds() {
        val attrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_GAME)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()

        soundPool = SoundPool.Builder()
            .setMaxStreams(2)
            .setAudioAttributes(attrs)
            .build()

        soundPool.setOnLoadCompleteListener { _, _, _ -> soundsLoaded = true }

        // Usamos tonos del sistema como sonidos (no requieren archivos .mp3 propios)
        // Si quieres archivos propios, coloca correct.mp3 y wrong.mp3 en res/raw/
        try {
            soundCorrectId = soundPool.load(this, R.raw.correct, 1)
            soundWrongId   = soundPool.load(this, R.raw.wrong,   1)
        } catch (e: Exception) {
            // Si no existen los archivos raw, generamos el sonido con ToneGenerator
            soundsLoaded = false
        }
    }

    private fun playCorrectSound() {
        if (soundsLoaded && soundCorrectId != 0) {
            soundPool.play(soundCorrectId, 1f, 1f, 0, 0, 1f)
        } else {
            // Fallback: vibración corta y tono del sistema
            try {
                val tg = android.media.ToneGenerator(
                    android.media.AudioManager.STREAM_MUSIC, 90
                )
                tg.startTone(android.media.ToneGenerator.TONE_PROP_ACK, 200)
                lifecycleScope.launch {
                    delay(250)
                    tg.release()
                }
            } catch (_: Exception) {}
        }
    }

    private fun playWrongSound() {
        if (soundsLoaded && soundWrongId != 0) {
            soundPool.play(soundWrongId, 1f, 1f, 0, 0, 1f)
        } else {
            try {
                val tg = android.media.ToneGenerator(
                    android.media.AudioManager.STREAM_MUSIC, 70
                )
                tg.startTone(android.media.ToneGenerator.TONE_PROP_NACK, 300)
                lifecycleScope.launch {
                    delay(350)
                    tg.release()
                }
            } catch (_: Exception) {}
        }
    }

    // ────────────────────────────────────────────────────────────────────────
    // BIND VISTAS
    // ────────────────────────────────────────────────────────────────────────
    private fun bindViews() {
        layoutContent   = findViewById(R.id.layoutContent)
        loadingOverlay  = findViewById(R.id.loadingOverlay)
        ivWordImage     = findViewById(R.id.ivWordImage)
        txtQuestion     = findViewById(R.id.txtQuestion)
        tvWordCategory  = findViewById(R.id.tvWordCategory)
        tvDifficulty    = findViewById(R.id.tvDifficulty)
        tvQuestionIndex = findViewById(R.id.tvQuestionIndex)
        progressSession = findViewById(R.id.progressSession)
        btnOption1      = findViewById(R.id.btnOption1)
        btnOption2      = findViewById(R.id.btnOption2)
        btnOption3      = findViewById(R.id.btnOption3)
        btnNext         = findViewById(R.id.btnNext)
        btnClose        = findViewById(R.id.btnClose)
        optionButtons   = listOf(btnOption1, btnOption2, btnOption3)

        correctPanel          = findViewById(R.id.correctPanel)
        wrongPanel            = findViewById(R.id.wrongPanel)
        tvCorrectWord         = findViewById(R.id.tvCorrectWord)
        tvCorrectXp           = findViewById(R.id.tvCorrectXp)
        tvCorrectAnswer       = findViewById(R.id.tvCorrectAnswer)
        btnContinuePractice   = findViewById(R.id.btnContinuePractice)
        btnFinishPractice     = findViewById(R.id.btnFinishPractice)
        btnContinueAfterWrong = findViewById(R.id.btnContinueAfterWrong)

        // Opciones
        optionButtons.forEach { btn ->
            btn.setOnClickListener {
                selectedAnswer = btn.text.toString()
                highlightSelected(btn)
                // Habilita el botón confirmar
                btnNext.isEnabled = true
                btnNext.backgroundTintList = ColorStateList.valueOf(getColor(R.color.nav_selected))
                btnNext.setTextColor(getColor(android.R.color.white))
            }
        }

        // Confirmar respuesta
        btnNext.setOnClickListener {
            if (selectedAnswer.isEmpty()) {
                Toast.makeText(this, "Selecciona una respuesta", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            // Deshabilitar botones mientras procesamos
            optionButtons.forEach { it.isEnabled = false }
            btnNext.isEnabled = false
            viewModel.submitAnswer(selectedAnswer)
            selectedAnswer = ""
        }

        // Botón cerrar
        btnClose.setOnClickListener { finish() }

        // Panel correcto: Continuar
        btnContinuePractice.setOnClickListener {
            hideCorrectPanel()
            viewModel.advance()
        }

        // Panel correcto: Finalizar
        btnFinishPractice.setOnClickListener {
            hideCorrectPanel()
            viewModel.finishNow()
        }

        // Panel incorrecto: Continuar
        btnContinueAfterWrong.setOnClickListener {
            hideWrongPanel()
            viewModel.advance()
        }
    }

    // ────────────────────────────────────────────────────────────────────────
    // OBSERVAR VIEWMODEL
    // ────────────────────────────────────────────────────────────────────────
    private fun observeViewModel() {
        lifecycleScope.launch {
            viewModel.uiState.collectLatest { state ->
                when (state) {
                    is PracticeUiState.Loading -> showLoading(true)

                    is PracticeUiState.Question -> {
                        showLoading(false)
                        displayQuestion(state)
                    }

                    is PracticeUiState.CorrectAnswer -> {
                        showLoading(false)
                        playCorrectSound()
                        showCorrectPanel(state)
                    }

                    is PracticeUiState.WrongAnswer -> {
                        showLoading(false)
                        playWrongSound()
                        showWrongPanel(state)
                    }

                    is PracticeUiState.SessionResult -> {
                        showLoading(false)
                        showFinalResult(state)
                    }

                    is PracticeUiState.Error -> {
                        showLoading(false)
                        Toast.makeText(this@PracticeActivity, state.message, Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
    }

    // ────────────────────────────────────────────────────────────────────────
    // MOSTRAR PREGUNTA
    // ────────────────────────────────────────────────────────────────────────
    private fun displayQuestion(state: PracticeUiState.Question) {
        val card = state.flashcard

        // Imagen según categoría / context
        val imageRes = contextImageMap[card.category.lowercase()]
            ?: contextImageMap[state.flashcard.category.lowercase()]
            ?: R.drawable.beach_scene
        ivWordImage.setImageResource(imageRes)

        // Animación de entrada en la imagen
        ivWordImage.alpha = 0f
        ivWordImage.animate().alpha(1f).setDuration(300).start()

        txtQuestion.text    = card.spanish
        tvWordCategory.text = card.category.ifBlank { "japonés" }
        tvDifficulty.text   = "⭐ ${card.difficulty}"

        // Progreso de sesión
        val pct = ((state.index.toFloat() / state.total) * 100).toInt()
        tvQuestionIndex.text = "${state.index} / ${state.total}"
        ObjectAnimator.ofInt(progressSession, "progress", progressSession.progress, pct)
            .setDuration(400)
            .start()

        // Opciones
        state.options.forEachIndexed { i, opt ->
            if (i < optionButtons.size) {
                optionButtons[i].visibility = View.VISIBLE
                optionButtons[i].text = opt
                optionButtons[i].isEnabled = true
                optionButtons[i].backgroundTintList =
                    ColorStateList.valueOf(getColor(android.R.color.white))
                optionButtons[i].setTextColor(getColor(android.R.color.black))
            }
        }
        for (i in state.options.size until optionButtons.size) {
            optionButtons[i].visibility = View.GONE
        }

        // Reset confirmar
        btnNext.isEnabled = false
        btnNext.backgroundTintList = ColorStateList.valueOf(getColor(android.R.color.darker_gray))
        btnNext.setTextColor(getColor(android.R.color.white))
    }

    // ────────────────────────────────────────────────────────────────────────
    // PANELES CORRECTO / INCORRECTO
    // ────────────────────────────────────────────────────────────────────────
    private fun showCorrectPanel(state: PracticeUiState.CorrectAnswer) {
        tvCorrectWord.text = state.flashcard.english
        tvCorrectXp.text   = "+${state.xpGained} XP"

        correctPanel.visibility = View.VISIBLE
        correctPanel.alpha = 0f
        correctPanel.animate().alpha(1f).setDuration(200).start()

        // Animación bounce del card interior
        val card = correctPanel.getChildAt(0)
        card.scaleX = 0.7f
        card.scaleY = 0.7f
        card.animate()
            .scaleX(1f).scaleY(1f)
            .setDuration(350)
            .setInterpolator(OvershootInterpolator(1.5f))
            .start()
    }

    private fun hideCorrectPanel() {
        correctPanel.animate().alpha(0f).setDuration(150)
            .withEndAction { correctPanel.visibility = View.GONE }
            .start()
    }

    private fun showWrongPanel(state: PracticeUiState.WrongAnswer) {
        tvCorrectAnswer.text = state.correctWord

        wrongPanel.visibility = View.VISIBLE
        wrongPanel.alpha = 0f
        wrongPanel.animate().alpha(1f).setDuration(200).start()

        val card = wrongPanel.getChildAt(0)
        card.scaleX = 0.7f
        card.scaleY = 0.7f
        card.animate()
            .scaleX(1f).scaleY(1f)
            .setDuration(350)
            .setInterpolator(OvershootInterpolator(1.5f))
            .start()
    }

    private fun hideWrongPanel() {
        wrongPanel.animate().alpha(0f).setDuration(150)
            .withEndAction { wrongPanel.visibility = View.GONE }
            .start()
    }

    // ────────────────────────────────────────────────────────────────────────
    // RESULTADO FINAL
    // ────────────────────────────────────────────────────────────────────────
    private fun showFinalResult(result: PracticeUiState.SessionResult) {
        val intent = Intent(this, HomeActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("session_correct", result.correct)
            putExtra("session_total",   result.total)
            putExtra("session_xp",      result.xpGained)
        }
        startActivity(intent)
        finish()
    }

    // ────────────────────────────────────────────────────────────────────────
    // HELPERS
    // ────────────────────────────────────────────────────────────────────────
    private fun highlightSelected(selected: Button) {
        optionButtons.forEach {
            it.backgroundTintList = ColorStateList.valueOf(getColor(android.R.color.white))
            it.setTextColor(getColor(android.R.color.black))
        }
        selected.backgroundTintList = ColorStateList.valueOf(getColor(R.color.nav_selected))
        selected.setTextColor(getColor(android.R.color.white))
    }

    private fun showLoading(show: Boolean) {
        loadingOverlay.visibility = if (show) View.VISIBLE else View.GONE
    }

    private fun setupNavigation() {
        findViewById<LinearLayout>(R.id.navHome).setOnClickListener {
            startActivity(Intent(this, HomeActivity::class.java))
            finish()
        }
        findViewById<LinearLayout>(R.id.navContexts).setOnClickListener {
            startActivity(Intent(this, ContextsActivity::class.java))
            finish()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        soundPool.release()
    }
}