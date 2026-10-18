package com.example.niju_project

import android.animation.ObjectAnimator
import android.content.res.ColorStateList
import android.media.AudioAttributes
import android.media.SoundPool
import android.os.Bundle
import android.view.View
import android.view.animation.OvershootInterpolator
import android.widget.Button
import com.google.android.material.button.MaterialButton
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.niju_project.audio.AudioKind
import com.example.niju_project.audio.AudioTarget
import com.example.niju_project.audio.ExerciseAudio
import com.example.niju_project.audio.PronunciationUi
import com.example.niju_project.data.model.ExerciseType
import com.example.niju_project.data.model.SessionMode
import com.example.niju_project.data.model.SessionSummary
import com.example.niju_project.reminders.StudyReminders
import com.example.niju_project.ui.practice.PracticeViewModel
import com.example.niju_project.ui.practice.RetryAction
import com.example.niju_project.ui.practice.SessionUiState
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * Pantalla de una sesión de estudio (lección, repaso o práctica).
 * Extras: mode, lesson_id, context_id, context_slug, title.
 */
class PracticeActivity : AppCompatActivity() {

    private val viewModel: PracticeViewModel by viewModels { PracticeViewModel.Factory }

    private lateinit var loadingOverlay: FrameLayout
    private lateinit var ivWordImage: ImageView
    private lateinit var txtQuestion: TextView
    private lateinit var tvWordCategory: TextView
    private lateinit var tvDifficulty: TextView
    private lateinit var tvQuestionIndex: TextView
    private lateinit var progressSession: ProgressBar
    private lateinit var btnNext: Button
    private lateinit var optionButtons: List<Button>

    private lateinit var correctPanel: FrameLayout
    private lateinit var wrongPanel: FrameLayout
    private lateinit var tvCorrectWord: TextView
    private lateinit var tvCorrectXp: TextView
    private lateinit var tvCorrectAnswer: TextView
    private lateinit var btnContinuePractice: Button
    private lateinit var btnFinishPractice: Button
    private lateinit var btnContinueAfterWrong: Button

    private var soundPool: SoundPool? = null
    private var soundCorrectId = 0
    private var soundWrongId = 0
    private var soundsLoaded = false

    private lateinit var audio: PronunciationUi
    private lateinit var btnListenQuestion: View
    private lateinit var tvYourAnswer: TextView
    private var questionTarget: AudioTarget? = null
    private var answerTarget: AudioTarget? = null
    private var lastAutoPlayedItem = -1L

    private var selectedAnswer: String? = null
    private var shownDialog: AlertDialog? = null
    private var lastFeedbackItem: Long = -1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_practice)

        initSounds()
        bindViews()
        ivWordImage.setImageResource(contextImage(intent.getStringExtra(PracticeViewModel.EXTRA_CONTEXT_SLUG)))
        setupBottomNav(null)

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = confirmExit()
        })
        observe()
    }

    // ── Sonidos ──────────────────────────────────────────────────────────────
    private fun initSounds() {
        val attrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_GAME)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        soundPool = SoundPool.Builder().setMaxStreams(2).setAudioAttributes(attrs).build().apply {
            setOnLoadCompleteListener { _, _, _ -> soundsLoaded = true }
            soundCorrectId = load(this@PracticeActivity, R.raw.correct, 1)
            soundWrongId = load(this@PracticeActivity, R.raw.wrong, 1)
        }
    }

    private fun play(id: Int) {
        if (soundsLoaded && id != 0) soundPool?.play(id, 1f, 1f, 0, 0, 1f)
    }

    // ── Vistas ───────────────────────────────────────────────────────────────
    private fun bindViews() {
        loadingOverlay = findViewById(R.id.loadingOverlay)
        ivWordImage = findViewById(R.id.ivWordImage)
        txtQuestion = findViewById(R.id.txtQuestion)
        tvWordCategory = findViewById(R.id.tvWordCategory)
        tvDifficulty = findViewById(R.id.tvDifficulty)
        tvQuestionIndex = findViewById(R.id.tvQuestionIndex)
        progressSession = findViewById(R.id.progressSession)
        btnNext = findViewById(R.id.btnNext)
        optionButtons = listOf(
            findViewById(R.id.btnOption1), findViewById(R.id.btnOption2), findViewById(R.id.btnOption3)
        )

        correctPanel = findViewById(R.id.correctPanel)
        wrongPanel = findViewById(R.id.wrongPanel)
        tvCorrectWord = findViewById(R.id.tvCorrectWord)
        tvCorrectXp = findViewById(R.id.tvCorrectXp)
        tvCorrectAnswer = findViewById(R.id.tvCorrectAnswer)
        btnContinuePractice = findViewById(R.id.btnContinuePractice)
        btnFinishPractice = findViewById(R.id.btnFinishPractice)
        btnContinueAfterWrong = findViewById(R.id.btnContinueAfterWrong)

        tvYourAnswer = findViewById(R.id.tvYourAnswer)
        btnListenQuestion = findViewById(R.id.btnListenQuestion)

        // Pronunciación: el enunciado (cuando está en inglés) y la respuesta correcta al corregir.
        audio = PronunciationUi(this)
        audio.bindTarget(btnListenQuestion) { questionTarget }
        audio.bindTarget(findViewById(R.id.btnListenAnswerCorrect)) { answerTarget }
        audio.bindTarget(findViewById(R.id.btnListenAnswerWrong)) { answerTarget }

        optionButtons.forEach { btn ->
            btn.setOnClickListener {
                selectedAnswer = btn.text.toString()
                highlightSelected(btn)
                setConfirmEnabled(true)
            }
        }
        btnNext.setOnClickListener {
            val answer = selectedAnswer
            if (answer == null) {
                Toast.makeText(this, R.string.session_select_answer, Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            optionButtons.forEach { it.isEnabled = false }
            setConfirmEnabled(false)
            viewModel.submitAnswer(answer)
        }
        findViewById<ImageButton>(R.id.btnClose).setOnClickListener { confirmExit() }

        btnContinuePractice.setOnClickListener { hidePanel(correctPanel); viewModel.next() }
        btnFinishPractice.setOnClickListener { hidePanel(correctPanel); viewModel.finish() }
        btnContinueAfterWrong.setOnClickListener { hidePanel(wrongPanel); viewModel.next() }
    }

    // ── Estado ───────────────────────────────────────────────────────────────
    private fun observe() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    loadingOverlay.visibility = if (state is SessionUiState.Loading) View.VISIBLE else View.GONE
                    when (state) {
                        SessionUiState.Loading -> Unit
                        is SessionUiState.Question -> showQuestion(state)
                        is SessionUiState.Feedback -> showFeedback(state)
                        is SessionUiState.Finished -> showSummary(state.summary)
                        is SessionUiState.Empty -> showMessageAndExit(state.message)
                        is SessionUiState.Error -> showError(state)
                    }
                }
            }
        }
    }

    private fun showQuestion(state: SessionUiState.Question) {
        val item = state.item
        correctPanel.visibility = View.GONE
        wrongPanel.visibility = View.GONE

        tvWordCategory.text = item.exerciseType.instruction
        txtQuestion.text = item.prompt
        val hint = item.hint
        tvDifficulty.visibility = if (hint.isNullOrBlank()) View.GONE else View.VISIBLE
        tvDifficulty.text = when (item.exerciseType) {
            // Pista: la traducción de la oración / la pronunciación escrita (IPA) de la palabra.
            ExerciseType.FILL_BLANK -> getString(R.string.session_hint_translation, hint)
            ExerciseType.EN_TO_ES -> hint
            ExerciseType.ES_TO_EN -> hint
        }

        // «Escuchar» solo aparece si el enunciado está en inglés.
        questionTarget = if (item.exerciseType == ExerciseType.EN_TO_ES) AudioTarget(item.prompt, AudioKind.WORD) else null
        btnListenQuestion.visibility = if (questionTarget != null) View.VISIBLE else View.GONE
        audio.refresh()
        if (questionTarget != null && audio.autoPlay && lastAutoPlayedItem != item.itemId) {
            lastAutoPlayedItem = item.itemId
            audio.play(item.prompt, AudioKind.WORD)
        }

        tvQuestionIndex.text = "${state.number} / ${state.total}"
        animateProgress(state.percent)

        selectedAnswer = null
        optionButtons.forEachIndexed { i, btn ->
            val option = item.options.getOrNull(i)
            btn.visibility = if (option == null) View.GONE else View.VISIBLE
            btn.text = option.orEmpty()
            btn.isEnabled = true
            styleOption(btn, selected = false)
        }
        setConfirmEnabled(false)

        ivWordImage.alpha = 0f
        ivWordImage.animate().alpha(1f).setDuration(250).start()
    }

    private fun showFeedback(state: SessionUiState.Feedback) {
        animateProgress(state.percent)
        // Evita repetir sonido/animación si la pantalla se recrea (rotación).
        val firstTime = lastFeedbackItem != state.item.itemId
        lastFeedbackItem = state.item.itemId

        // Audio de la respuesta correcta (palabra u oración completa); se suma al panel.
        answerTarget = ExerciseAudio.forAnswer(state.item.exerciseType, state.item.prompt, state.result.correctAnswer)
        audio.refresh()

        if (state.result.isCorrect) {
            tvCorrectWord.text = state.result.correctAnswer
            tvCorrectXp.text = if (state.result.xpAwarded > 0) "+${state.result.xpAwarded} XP" else "¡Bien!"
            btnContinuePractice.text = if (state.isLast) "Ver resultado" else getString(R.string.session_continue)
            btnFinishPractice.visibility = if (state.isLast) View.GONE else View.VISIBLE
            if (firstTime) play(soundCorrectId)
            showPanel(correctPanel, animate = firstTime)
        } else {
            tvCorrectAnswer.text = state.result.correctAnswer
            tvYourAnswer.text = getString(R.string.session_your_answer, state.answer)
            tvYourAnswer.visibility = if (state.answer.isBlank()) View.GONE else View.VISIBLE
            btnContinueAfterWrong.text = if (state.isLast) "Ver resultado" else getString(R.string.session_continue)
            if (firstTime) play(soundWrongId)
            showPanel(wrongPanel, animate = firstTime)
        }
    }

    private fun showSummary(s: SessionSummary) {
        val lines = buildList {
            add("Aciertos: ${s.correct} de ${s.total} (${s.score.roundToInt()} %)")
            add("XP ganado: +${s.xpEarned}  ·  Nivel ${s.level}")
            if (s.mode == SessionMode.LESSON) {
                add(if (s.lessonCompleted) "✅ ¡Lección superada!" else "Necesitas 70 % para superar la lección.")
            }
            s.newAchievements.forEach { add("🏆 Logro: ${it.title} (+${it.xpReward} XP)") }
        }
        val builder = AlertDialog.Builder(this)
            .setTitle(R.string.session_finish_title)
            .setMessage(lines.joinToString("\n"))
            .setCancelable(false)
            .setPositiveButton(R.string.ok) { _, _ -> finish() }
        if (s.mode == SessionMode.LESSON && !s.lessonCompleted) {
            builder.setNegativeButton(R.string.session_repeat_lesson) { _, _ -> viewModel.start() }
        }
        StudyReminders.markStudiedToday(this)
        showDialog(builder)
    }

    private fun showError(state: SessionUiState.Error) {
        if (state.sessionExpired) {
            Toast.makeText(this, state.message, Toast.LENGTH_LONG).show()
            goToLogin()
            return
        }
        val builder = AlertDialog.Builder(this).setMessage(state.message).setCancelable(false)
        when (state.retry) {
            RetryAction.START -> builder
                .setPositiveButton(R.string.session_retry) { _, _ -> viewModel.start() }
                .setNegativeButton(R.string.session_exit) { _, _ -> finish() }
            RetryAction.QUESTION -> builder
                .setPositiveButton(R.string.session_continue) { _, _ -> viewModel.resumeQuestion() }
            RetryAction.FINISH -> builder
                .setPositiveButton(R.string.session_retry) { _, _ -> viewModel.finish() }
                .setNegativeButton(R.string.session_exit) { _, _ -> finish() }
            RetryAction.NONE -> builder.setPositiveButton(R.string.ok) { _, _ -> finish() }
        }
        showDialog(builder)
    }

    private fun showMessageAndExit(message: String) = showDialog(
        AlertDialog.Builder(this).setMessage(message).setCancelable(false)
            .setPositiveButton(R.string.ok) { _, _ -> finish() }
    )

    /** Salir a mitad de sesión: lo respondido ya quedó guardado en el servidor. */
    private fun confirmExit() {
        val state = viewModel.uiState.value
        if (state is SessionUiState.Finished || state is SessionUiState.Empty) { finish(); return }
        showDialog(
            AlertDialog.Builder(this)
                .setTitle("¿Salir de la sesión?")
                .setMessage("Tus respuestas ya están guardadas. Las que falten no contarán.")
                .setPositiveButton("Terminar ahora") { _, _ -> viewModel.finish() }
                .setNeutralButton(R.string.session_exit) { _, _ -> finish() }
                .setNegativeButton(R.string.cancel, null)
        )
    }

    // ── Utilidades de vista ──────────────────────────────────────────────────
    private fun showDialog(builder: AlertDialog.Builder) {
        shownDialog?.dismiss()
        shownDialog = builder.show()
    }

    private fun showPanel(panel: FrameLayout, animate: Boolean) {
        panel.visibility = View.VISIBLE
        if (!animate) { panel.alpha = 1f; return }
        panel.alpha = 0f
        panel.animate().alpha(1f).setDuration(200).start()
        panel.getChildAt(0)?.apply {
            scaleX = 0.7f; scaleY = 0.7f
            animate().scaleX(1f).scaleY(1f).setDuration(350)
                .setInterpolator(OvershootInterpolator(1.5f)).start()
        }
    }

    private fun hidePanel(panel: FrameLayout) {
        panel.animate().alpha(0f).setDuration(150).withEndAction { panel.visibility = View.GONE }.start()
    }

    private fun animateProgress(pct: Int) {
        ObjectAnimator.ofInt(progressSession, "progress", progressSession.progress, pct)
            .setDuration(400).start()
    }

    private fun setConfirmEnabled(enabled: Boolean) {
        btnNext.isEnabled = enabled
        btnNext.backgroundTintList = ColorStateList.valueOf(
            getColor(if (enabled) R.color.color_primary else R.color.color_disabled_bg)
        )
        btnNext.setTextColor(getColor(if (enabled) R.color.color_on_solid else R.color.color_text_secondary))
    }

    private fun highlightSelected(selected: Button) {
        optionButtons.forEach { styleOption(it, selected = it === selected) }
    }

    /**
     * La opción elegida se distingue por color Y por una marca ✓ (no solo por color),
     * y se anuncia a TalkBack como seleccionada.
     */
    private fun styleOption(btn: Button, selected: Boolean) {
        btn.backgroundTintList = ColorStateList.valueOf(
            getColor(if (selected) R.color.color_primary else R.color.color_surface)
        )
        btn.setTextColor(getColor(if (selected) R.color.color_on_solid else R.color.color_text_primary))
        btn.isSelected = selected
        (btn as? MaterialButton)?.apply {
            setIconResource(if (selected) R.drawable.ic_check else 0)
            iconTint = ColorStateList.valueOf(getColor(R.color.color_on_solid))
            iconGravity = MaterialButton.ICON_GRAVITY_TEXT_START
        }
        ViewCompat.setStateDescription(btn, if (selected) getString(R.string.session_option_selected) else null)
    }

    override fun onDestroy() {
        shownDialog?.dismiss()
        soundPool?.release()
        soundPool = null
        super.onDestroy()
    }
}
