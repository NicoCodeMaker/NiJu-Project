package com.example.niju_project

import android.animation.ObjectAnimator
import android.content.Intent
import android.content.res.ColorStateList
import android.media.AudioAttributes
import android.media.SoundPool
import android.os.Bundle
import android.view.HapticFeedbackConstants
import android.view.View
import android.view.animation.OvershootInterpolator
import android.view.inputmethod.EditorInfo
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.niju_project.audio.AudioKind
import com.example.niju_project.audio.AudioTarget
import com.example.niju_project.audio.ExerciseAudio
import com.example.niju_project.audio.PronunciationUi
import com.example.niju_project.data.model.AnswerInput
import com.example.niju_project.data.model.ExerciseType
import com.example.niju_project.data.model.SessionItem
import com.example.niju_project.data.model.SessionMode
import com.example.niju_project.data.model.SessionSummary
import com.example.niju_project.data.remote.NijuJson
import com.example.niju_project.domain.session.WordOrderState
import com.example.niju_project.reminders.StudyReminders
import com.example.niju_project.ui.hideKeyboard
import com.example.niju_project.ui.practice.PracticeViewModel
import com.example.niju_project.ui.practice.RetryAction
import com.example.niju_project.ui.practice.SessionUiState
import com.example.niju_project.ui.results.SessionResultActivity
import com.example.niju_project.ui.shake
import com.google.android.material.button.MaterialButton
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.progressindicator.LinearProgressIndicator
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import kotlinx.coroutines.launch

/**
 * Sesión de estudio (lección, repaso, práctica o evaluación) con 9 tipos de ejercicio:
 * elegir (traducción, palabra, imagen, escuchar, completar, conversación, error),
 * escribir y ordenar. La pantalla solo recoge la respuesta; el servidor la corrige.
 * Extras: mode, lesson_id, context_id, context_slug, title, focus.
 */
class PracticeActivity : AppCompatActivity() {

    private val viewModel: PracticeViewModel by viewModels { PracticeViewModel.Factory }

    private lateinit var loadingOverlay: FrameLayout
    private lateinit var scroll: ScrollView
    private lateinit var tvSessionTitle: TextView
    private lateinit var tvInstruction: TextView
    private lateinit var ivWordImage: ImageView
    private lateinit var tvEmoji: TextView
    private lateinit var btnPlayBig: MaterialButton
    private lateinit var txtQuestion: TextView
    private lateinit var tvHint: TextView
    private lateinit var tvAudioNote: TextView
    private lateinit var tvQuestionIndex: TextView
    private lateinit var tvSessionXp: TextView
    private lateinit var progressSession: LinearProgressIndicator
    private lateinit var btnNext: MaterialButton
    private lateinit var optionsContainer: LinearLayout
    private lateinit var optionButtons: List<MaterialButton>
    private lateinit var typeContainer: TextInputLayout
    private lateinit var etTypeAnswer: TextInputEditText
    private lateinit var orderContainer: View
    private lateinit var cgAnswer: ChipGroup
    private lateinit var cgWords: ChipGroup
    private lateinit var tvOrderPlaceholder: TextView

    private lateinit var correctPanel: FrameLayout
    private lateinit var wrongPanel: FrameLayout
    private lateinit var tvCorrectTitle: TextView
    private lateinit var tvCorrectWord: TextView
    private lateinit var tvCorrectXp: TextView
    private lateinit var tvCorrectExtra: TextView
    private lateinit var tvCorrectAnswer: TextView
    private lateinit var tvSolution: TextView
    private lateinit var tvYourAnswer: TextView
    private lateinit var btnContinuePractice: MaterialButton
    private lateinit var btnFinishPractice: MaterialButton
    private lateinit var btnContinueAfterWrong: MaterialButton

    private var soundPool: SoundPool? = null
    private var soundCorrectId = 0
    private var soundWrongId = 0
    private var soundsLoaded = false

    private lateinit var audio: PronunciationUi
    private lateinit var btnListenQuestion: View
    private var questionTarget: AudioTarget? = null
    private var answerTarget: AudioTarget? = null
    private var lastAutoPlayedItem = -1L

    private var currentItem: SessionItem? = null
    private var selectedAnswer: String? = null
    private var orderState: WordOrderState? = null
    private var shownDialog: AlertDialog? = null
    private var lastFeedbackItem: Long = -1
    private var sessionXp = 0

    private val title by lazy { intent.getStringExtra(PracticeViewModel.EXTRA_TITLE) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_practice)
        initSounds()
        bindViews()
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
        scroll = findViewById(R.id.scrollExercise)
        tvSessionTitle = findViewById(R.id.tvSessionTitle)
        tvInstruction = findViewById(R.id.tvWordCategory)
        ivWordImage = findViewById(R.id.ivWordImage)
        tvEmoji = findViewById(R.id.tvEmoji)
        btnPlayBig = findViewById(R.id.btnPlayBig)
        txtQuestion = findViewById(R.id.txtQuestion)
        tvHint = findViewById(R.id.tvDifficulty)
        tvAudioNote = findViewById(R.id.tvAudioNote)
        tvQuestionIndex = findViewById(R.id.tvQuestionIndex)
        tvSessionXp = findViewById(R.id.tvSessionXp)
        progressSession = findViewById(R.id.progressSession)
        btnNext = findViewById(R.id.btnNext)
        optionsContainer = findViewById(R.id.optionsContainer)
        optionButtons = listOf(
            findViewById(R.id.btnOption1), findViewById(R.id.btnOption2),
            findViewById(R.id.btnOption3), findViewById(R.id.btnOption4)
        )
        typeContainer = findViewById(R.id.typeContainer)
        etTypeAnswer = findViewById(R.id.etTypeAnswer)
        orderContainer = findViewById(R.id.orderContainer)
        cgAnswer = findViewById(R.id.cgAnswer)
        cgWords = findViewById(R.id.cgWords)
        tvOrderPlaceholder = findViewById(R.id.tvOrderPlaceholder)

        correctPanel = findViewById(R.id.correctPanel)
        wrongPanel = findViewById(R.id.wrongPanel)
        tvCorrectTitle = findViewById(R.id.tvCorrectTitle)
        tvCorrectWord = findViewById(R.id.tvCorrectWord)
        tvCorrectXp = findViewById(R.id.tvCorrectXp)
        tvCorrectExtra = findViewById(R.id.tvCorrectExtra)
        tvCorrectAnswer = findViewById(R.id.tvCorrectAnswer)
        tvSolution = findViewById(R.id.tvSolution)
        tvYourAnswer = findViewById(R.id.tvYourAnswer)
        btnContinuePractice = findViewById(R.id.btnContinuePractice)
        btnFinishPractice = findViewById(R.id.btnFinishPractice)
        btnContinueAfterWrong = findViewById(R.id.btnContinueAfterWrong)
        btnListenQuestion = findViewById(R.id.btnListenQuestion)

        tvSessionTitle.text = sessionLabel()
        tvSessionXp.text = "0"
        tvSessionXp.contentDescription = getString(R.string.session_xp_desc, 0)

        // Pronunciación: el enunciado (cuando está en inglés) y la respuesta correcta al corregir.
        audio = PronunciationUi(this)
        audio.bindTarget(btnListenQuestion) { questionTarget }
        audio.bindTarget(btnPlayBig) { questionTarget }
        audio.bindTarget(findViewById(R.id.btnListenAnswerCorrect)) { answerTarget }
        audio.bindTarget(findViewById(R.id.btnListenAnswerWrong)) { answerTarget }

        optionButtons.forEach { btn ->
            btn.setOnClickListener {
                selectedAnswer = btn.text.toString()
                optionButtons.forEach { styleOption(it, selected = it === btn) }
                setConfirmEnabled(true)
            }
        }
        etTypeAnswer.doAfterTextChanged {
            if (currentItem?.exerciseType?.input == AnswerInput.TEXT) setConfirmEnabled(!it.isNullOrBlank())
        }
        etTypeAnswer.setOnEditorActionListener { _, id, _ ->
            if (id == EditorInfo.IME_ACTION_DONE && btnNext.isEnabled) { submit(); true } else false
        }
        findViewById<View>(R.id.btnUndoOrder).setOnClickListener {
            if (orderState?.undo() == true) renderOrder()
        }
        btnNext.setOnClickListener { submit() }
        findViewById<ImageButton>(R.id.btnClose).setOnClickListener { confirmExit() }

        btnContinuePractice.setOnClickListener { hidePanel(correctPanel); viewModel.next() }
        btnFinishPractice.setOnClickListener { hidePanel(correctPanel); viewModel.finish() }
        btnContinueAfterWrong.setOnClickListener { hidePanel(wrongPanel); viewModel.next() }
    }

    private fun sessionLabel(): String {
        val mode = SessionMode.fromApi(intent.getStringExtra(PracticeViewModel.EXTRA_MODE))
        val base = getString(when (mode) {
            SessionMode.LESSON -> R.string.session_mode_lesson
            SessionMode.REVIEW -> R.string.session_mode_review
            SessionMode.PRACTICE -> R.string.session_mode_practice
            SessionMode.EXAM -> R.string.session_mode_exam
        })
        return if (title.isNullOrBlank()) base else "$base · $title"
    }

    /** Respuesta según el tipo de ejercicio; null si aún no hay una completa. */
    private fun currentAnswer(): String? = when (currentItem?.exerciseType?.input) {
        AnswerInput.CHOICE -> selectedAnswer
        AnswerInput.TEXT -> etTypeAnswer.text?.toString()?.trim()?.takeIf { it.isNotEmpty() }
        AnswerInput.ORDER -> orderState?.takeIf { it.isComplete }?.answer
        null -> null
    }

    private fun submit() {
        val answer = currentAnswer()
        if (answer == null) {
            findViewById<View>(R.id.exerciseContent).shake()
            return
        }
        hideKeyboard()
        setInputsEnabled(false)
        setConfirmEnabled(false)
        viewModel.submitAnswer(answer)
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
        val type = item.exerciseType
        val isNewItem = currentItem?.itemId != item.itemId
        currentItem = item
        correctPanel.visibility = View.GONE
        wrongPanel.visibility = View.GONE

        tvInstruction.text = type.instruction
        tvQuestionIndex.text = getString(R.string.fraction_value, state.number, state.total)
        tvQuestionIndex.contentDescription = getString(R.string.session_question_desc, state.number, state.total)
        progressSession.setProgressCompat(state.percent, true)

        // Enunciado
        tvEmoji.visibility = View.GONE
        btnPlayBig.visibility = View.GONE
        tvAudioNote.visibility = View.GONE
        txtQuestion.visibility = View.VISIBLE
        txtQuestion.textSize = if (type == ExerciseType.DIALOGUE || type == ExerciseType.SPOT_ERROR ||
            type == ExerciseType.FILL_BLANK || type == ExerciseType.WORD_ORDER) 20f else 28f
        txtQuestion.text = item.prompt
        when (type) {
            ExerciseType.IMAGE_MATCH -> {
                tvEmoji.text = item.prompt
                tvEmoji.contentDescription = getString(R.string.session_image_desc)
                tvEmoji.visibility = View.VISIBLE
                txtQuestion.visibility = View.GONE
            }
            ExerciseType.LISTEN_CHOOSE -> {
                if (audio.controller.hasAudio(item.prompt, AudioKind.WORD)) {
                    btnPlayBig.visibility = View.VISIBLE
                    txtQuestion.visibility = View.GONE
                } else {
                    // Sin clip ni voz del sistema el ejercicio sería imposible: se muestra la palabra.
                    tvAudioNote.visibility = View.VISIBLE
                }
            }
            else -> Unit
        }

        val hint = item.hint
        tvHint.visibility = if (hint.isNullOrBlank()) View.GONE else View.VISIBLE
        tvHint.text = when (type) {
            ExerciseType.FILL_BLANK, ExerciseType.SPOT_ERROR -> getString(R.string.session_hint_translation, hint)
            else -> hint
        }

        questionTarget = ExerciseAudio.forQuestion(type, item.prompt)
        btnListenQuestion.visibility =
            if (questionTarget != null && type != ExerciseType.LISTEN_CHOOSE) View.VISIBLE else View.GONE
        audio.refresh()
        val mustPlay = type == ExerciseType.LISTEN_CHOOSE || audio.autoPlay
        if (questionTarget != null && mustPlay && lastAutoPlayedItem != item.itemId) {
            lastAutoPlayedItem = item.itemId
            audio.play(item.prompt, AudioKind.WORD)
        }

        // Forma de responder
        optionsContainer.visibility = if (type.input == AnswerInput.CHOICE) View.VISIBLE else View.GONE
        typeContainer.visibility = if (type.input == AnswerInput.TEXT) View.VISIBLE else View.GONE
        orderContainer.visibility = if (type.input == AnswerInput.ORDER) View.VISIBLE else View.GONE
        if (isNewItem) {
            selectedAnswer = null
            etTypeAnswer.setText("")
            orderState = if (type.input == AnswerInput.ORDER) WordOrderState(item.options) else null
        }
        when (type.input) {
            AnswerInput.CHOICE -> optionButtons.forEachIndexed { i, btn ->
                val option = item.options.getOrNull(i)
                btn.visibility = if (option == null) View.GONE else View.VISIBLE
                btn.text = option.orEmpty()
                styleOption(btn, selected = option != null && option == selectedAnswer)
            }
            AnswerInput.TEXT -> {
                etTypeAnswer.requestFocus()
                typeContainer.error = null
            }
            AnswerInput.ORDER -> renderOrder()
        }
        setInputsEnabled(true)
        setConfirmEnabled(currentAnswer() != null)

        if (isNewItem) {
            scroll.scrollTo(0, 0)
            findViewById<View>(R.id.promptCard).apply {
                alpha = 0f; translationY = 24f
                animate().alpha(1f).translationY(0f).setDuration(260).start()
            }
        }
    }

    /** Dibuja las palabras disponibles y las ya colocadas (ordenar palabras). */
    private fun renderOrder() {
        val state = orderState ?: return
        cgAnswer.removeAllViews()
        cgWords.removeAllViews()
        state.answerIndexes.forEach { idx ->
            cgAnswer.addView(wordChip(state.tokens[idx], placed = true) {
                state.remove(idx); renderOrder()
            })
        }
        state.availableIndexes.forEach { idx ->
            cgWords.addView(wordChip(state.tokens[idx], placed = false) {
                state.place(idx); renderOrder()
            })
        }
        tvOrderPlaceholder.visibility = if (state.answerIndexes.isEmpty()) View.VISIBLE else View.GONE
        findViewById<View>(R.id.btnUndoOrder).isEnabled = state.answerIndexes.isNotEmpty()
        setConfirmEnabled(state.isComplete)
    }

    private fun wordChip(text: String, placed: Boolean, onTap: () -> Unit): Chip =
        Chip(this).apply {
            setTextAppearance(R.style.TextAppearance_NiJu_Subtitle)
            this.text = text
            textSize = 17f
            isCheckable = false
            chipMinHeight = resources.displayMetrics.density * 48
            chipStrokeWidth = resources.displayMetrics.density * 2
            chipCornerRadius = resources.displayMetrics.density * 14
            chipStrokeColor = ColorStateList.valueOf(getColor(if (placed) R.color.color_accent else R.color.color_option_stroke))
            chipBackgroundColor = ColorStateList.valueOf(getColor(if (placed) R.color.color_accent_container else R.color.color_surface))
            setTextColor(getColor(R.color.color_text_primary))
            contentDescription = getString(if (placed) R.string.session_order_remove else R.string.session_order_add, text)
            setOnClickListener { if (isEnabled) onTap() }
        }

    private fun showFeedback(state: SessionUiState.Feedback) {
        progressSession.setProgressCompat(state.percent, true)
        val firstTime = lastFeedbackItem != state.item.itemId
        lastFeedbackItem = state.item.itemId
        val r = state.result
        val type = state.item.exerciseType
        if (firstTime) {
            sessionXp += r.xpAwarded + r.streakBonus
            tvSessionXp.text = sessionXp.toString()
            tvSessionXp.contentDescription = getString(R.string.session_xp_desc, sessionXp)
            if (r.xpAwarded + r.streakBonus > 0) bump(tvSessionXp)
        }

        answerTarget = ExerciseAudio.forAnswer(type, state.item.prompt, r.correctAnswer, r.solution)
        audio.refresh()
        setInputsEnabled(false)

        val isLast = state.isLast
        val nextLabel = getString(if (isLast) R.string.session_see_result else R.string.session_continue)
        if (r.isCorrect) {
            val titles = resources.getStringArray(R.array.feedback_correct_titles)
            tvCorrectTitle.text = titles[(state.item.itemId % titles.size).toInt()]
            tvCorrectWord.text = when (type) {
                ExerciseType.SPOT_ERROR -> r.solution ?: r.correctAnswer
                ExerciseType.DIALOGUE -> r.solution ?: r.correctAnswer
                else -> r.correctAnswer
            }
            tvCorrectXp.text = if (r.xpAwarded > 0) getString(R.string.xp_plus, r.xpAwarded) else getString(R.string.session_good)
            tvCorrectExtra.visibility = if (r.streakBonus > 0) View.VISIBLE else View.GONE
            tvCorrectExtra.text = getString(R.string.session_streak_bonus, r.streakBonus)
            btnContinuePractice.text = nextLabel
            btnFinishPractice.visibility = if (isLast) View.GONE else View.VISIBLE
            if (firstTime) play(soundCorrectId)
            showPanel(correctPanel, animate = firstTime)
            if (firstTime) highlightCorrectOption(r.correctAnswer)
        } else {
            tvCorrectAnswer.text = r.correctAnswer
            val solution = when (type) {
                ExerciseType.SPOT_ERROR -> r.solution?.let { getString(R.string.session_solution_spot, r.correctAnswer, it) }
                ExerciseType.FILL_BLANK, ExerciseType.DIALOGUE -> r.solution?.let { getString(R.string.session_solution, it) }
                else -> null
            }
            tvSolution.visibility = if (solution == null) View.GONE else View.VISIBLE
            tvSolution.text = solution
            tvYourAnswer.text = getString(R.string.session_your_answer, state.answer)
            tvYourAnswer.visibility = if (state.answer.isBlank()) View.GONE else View.VISIBLE
            btnContinueAfterWrong.text = nextLabel
            if (firstTime) {
                play(soundWrongId)
                window.decorView.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                findViewById<View>(R.id.promptCard).shake()
            }
            showPanel(wrongPanel, animate = firstTime)
        }
    }

    private fun highlightCorrectOption(answer: String) {
        optionButtons.firstOrNull { it.visibility == View.VISIBLE && it.text.toString() == answer }?.let {
            it.strokeColor = ColorStateList.valueOf(getColor(R.color.color_success))
            it.backgroundTintList = ColorStateList.valueOf(getColor(R.color.color_success_container))
        }
    }

    private fun showSummary(s: SessionSummary) {
        StudyReminders.markStudiedToday(this)
        startActivity(Intent(this, SessionResultActivity::class.java).apply {
            putExtra(SessionResultActivity.EXTRA_SUMMARY, NijuJson.encodeToString(SessionSummary.serializer(), s))
            // Para "Repetir lección" / "Otra ronda": los mismos parámetros de esta sesión.
            putExtras(intent)
        })
        finish()
    }

    private fun showError(state: SessionUiState.Error) {
        if (state.sessionExpired) {
            android.widget.Toast.makeText(this, state.message, android.widget.Toast.LENGTH_LONG).show()
            goToLogin()
            return
        }
        val builder = MaterialAlertDialogBuilder(this).setMessage(state.message).setCancelable(false)
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
        MaterialAlertDialogBuilder(this).setMessage(message).setCancelable(false)
            .setPositiveButton(R.string.ok) { _, _ -> finish() }
    )

    /** Salir a mitad de sesión: lo respondido ya quedó guardado en el servidor. */
    private fun confirmExit() {
        val state = viewModel.uiState.value
        if (state is SessionUiState.Finished || state is SessionUiState.Empty) { finish(); return }
        showDialog(
            MaterialAlertDialogBuilder(this)
                .setTitle(R.string.session_exit_title)
                .setMessage(R.string.session_exit_message)
                .setPositiveButton(R.string.session_finish_now) { _, _ -> viewModel.finish() }
                .setNeutralButton(R.string.session_exit) { _, _ -> finish() }
                .setNegativeButton(R.string.cancel, null)
        )
    }

    // ── Utilidades de vista ──────────────────────────────────────────────────
    private fun showDialog(builder: MaterialAlertDialogBuilder) {
        shownDialog?.dismiss()
        shownDialog = builder.show()
    }

    private fun showPanel(panel: FrameLayout, animate: Boolean) {
        panel.visibility = View.VISIBLE
        val sheet = panel.getChildAt(0)
        if (!animate) { panel.alpha = 1f; sheet?.translationY = 0f; return }
        panel.alpha = 0f
        panel.animate().alpha(1f).setDuration(180).start()
        sheet?.apply {
            post {
                translationY = height.toFloat()
                animate().translationY(0f).setDuration(320).setInterpolator(OvershootInterpolator(0.8f)).start()
            }
        }
    }

    private fun hidePanel(panel: FrameLayout) {
        panel.animate().alpha(0f).setDuration(150).withEndAction { panel.visibility = View.GONE }.start()
    }

    private fun bump(view: View) {
        ObjectAnimator.ofFloat(view, View.SCALE_X, 1f, 1.25f, 1f).setDuration(350).start()
        ObjectAnimator.ofFloat(view, View.SCALE_Y, 1f, 1.25f, 1f).setDuration(350).start()
    }

    private fun setConfirmEnabled(enabled: Boolean) {
        btnNext.isEnabled = enabled
    }

    private fun setInputsEnabled(enabled: Boolean) {
        optionButtons.forEach { it.isEnabled = enabled }
        etTypeAnswer.isEnabled = enabled
        for (g in listOf(cgAnswer, cgWords)) for (i in 0 until g.childCount) g.getChildAt(i).isEnabled = enabled
        findViewById<View>(R.id.btnUndoOrder).isEnabled = enabled && (orderState?.answerIndexes?.isNotEmpty() == true)
    }

    /**
     * La opción elegida se distingue por color Y por una marca ✓ (no solo por color),
     * y se anuncia a TalkBack como seleccionada.
     */
    private fun styleOption(btn: MaterialButton, selected: Boolean) {
        btn.backgroundTintList = ColorStateList.valueOf(
            getColor(if (selected) R.color.color_primary_container else R.color.color_surface)
        )
        btn.strokeColor = ColorStateList.valueOf(
            getColor(if (selected) R.color.color_primary else R.color.color_option_stroke)
        )
        btn.setTextColor(getColor(if (selected) R.color.color_on_primary_container else R.color.color_text_primary))
        btn.isSelected = selected
        btn.setIconResource(if (selected) R.drawable.ic_check else 0)
        btn.iconTint = ColorStateList.valueOf(getColor(R.color.color_primary))
        btn.iconGravity = MaterialButton.ICON_GRAVITY_TEXT_START
        ViewCompat.setStateDescription(btn, if (selected) getString(R.string.session_option_selected) else null)
    }

    override fun onDestroy() {
        shownDialog?.dismiss()
        soundPool?.release()
        soundPool = null
        super.onDestroy()
    }
}
