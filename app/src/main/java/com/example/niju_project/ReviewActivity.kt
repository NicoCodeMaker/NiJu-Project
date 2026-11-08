package com.example.niju_project

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.GridLayout
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.niju_project.audio.AudioKind
import com.example.niju_project.audio.PronunciationUi
import com.example.niju_project.data.model.ReviewOverview
import com.example.niju_project.data.model.ReviewWord
import com.example.niju_project.data.model.SessionMode
import com.example.niju_project.data.model.WordBucket
import com.example.niju_project.data.model.WordDetail
import com.example.niju_project.data.repository.LearningRepository
import com.example.niju_project.ui.Formatters
import com.example.niju_project.ui.UiState
import com.example.niju_project.ui.review.ReviewData
import com.example.niju_project.ui.review.ReviewViewModel
import com.example.niju_project.ui.widget.ForecastBarsView
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.button.MaterialButton
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Repaso con repetición espaciada (SM-2 calculado en el servidor):
 * cuántas palabras tocan hoy, cuáles son urgentes, en qué estado está cada palabra,
 * el pronóstico de los próximos 7 días y el historial de cada palabra.
 */
class ReviewActivity : AppCompatActivity() {

    private val viewModel: ReviewViewModel by viewModels()
    private val adapter = ReviewWordsAdapter { viewModel.openDetail(it.wordId) }
    private lateinit var audio: PronunciationUi
    private var sheet: BottomSheetDialog? = null
    private var dueNow = 0

    private val filters = listOf(
        null to R.string.review_filter_all,
        "due" to R.string.review_filter_due,
        "urgent" to R.string.review_filter_urgent,
        WordBucket.LEARNING.apiValue to R.string.review_bucket_learning,
        WordBucket.REINFORCE.apiValue to R.string.review_bucket_reinforce,
        WordBucket.REVIEW.apiValue to R.string.review_bucket_review,
        WordBucket.MASTERED.apiValue to R.string.review_bucket_mastered
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_review)
        audio = PronunciationUi(this)

        findViewById<RecyclerView>(R.id.rvWords).apply {
            layoutManager = LinearLayoutManager(this@ReviewActivity)
            adapter = this@ReviewActivity.adapter
        }
        buildFilters()
        findViewById<MaterialButton>(R.id.btnReviewNow).setOnClickListener {
            if (dueNow > 0) openSession(SessionMode.REVIEW, title = getString(R.string.session_review_title))
            else openSession(SessionMode.PRACTICE, title = getString(R.string.home_free_practice))
        }
        findViewById<MaterialButton>(R.id.btnReinforce).setOnClickListener {
            openSession(SessionMode.REVIEW, title = getString(R.string.review_reinforce_title),
                focus = LearningRepository.FOCUS_REINFORCE)
        }
        findViewById<Button>(R.id.btnEmptyAction).setOnClickListener { viewModel.load() }
        bindTile(R.id.tileLearning, "🌱", R.string.review_bucket_learning, R.string.review_help_learning, WordBucket.LEARNING)
        bindTile(R.id.tileReinforce, "🧩", R.string.review_bucket_reinforce, R.string.review_help_reinforce, WordBucket.REINFORCE)
        bindTile(R.id.tileReview, "🔁", R.string.review_bucket_review, R.string.review_help_review, WordBucket.REVIEW)
        bindTile(R.id.tileMastered, "🏅", R.string.review_bucket_mastered, R.string.review_help_mastered, WordBucket.MASTERED)

        setupBottomNav(R.id.navReview)
        observe()
    }

    override fun onResume() {
        super.onResume()
        viewModel.load()
    }

    private fun buildFilters() {
        val group = findViewById<ChipGroup>(R.id.cgFilters)
        filters.forEachIndexed { i, (key, label) ->
            group.addView(Chip(this).apply {
                id = View.generateViewId()
                setText(label)
                isCheckable = true
                isChecked = i == 0
                tag = key
                setOnClickListener { if (isChecked) viewModel.load(key) }
            })
        }
    }

    private fun selectFilter(key: String?) {
        val group = findViewById<ChipGroup>(R.id.cgFilters)
        for (i in 0 until group.childCount) {
            val chip = group.getChildAt(i) as Chip
            chip.isChecked = chip.tag == key
        }
        viewModel.load(key)
    }

    private fun bindTile(id: Int, emoji: String, name: Int, help: Int, bucket: WordBucket) {
        val tile = findViewById<View>(id)
        tile.findViewById<TextView>(R.id.tvTileEmoji).text = emoji
        tile.findViewById<TextView>(R.id.tvTileName).setText(name)
        tile.findViewById<TextView>(R.id.tvTileHelp).setText(help)
        tile.setOnClickListener { selectFilter(bucket.apiValue) }
    }

    private fun setTileCount(id: Int, count: Int) {
        val tile = findViewById<View>(id)
        tile.findViewById<TextView>(R.id.tvTileCount).text = count.toString()
        tile.contentDescription = "${tile.findViewById<TextView>(R.id.tvTileName).text}: $count. " +
            tile.findViewById<TextView>(R.id.tvTileHelp).text
    }

    private fun observe() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.uiState.collect { state ->
                        findViewById<View>(R.id.progressBar).visibility =
                            if (state is UiState.Loading) View.VISIBLE else View.GONE
                        findViewById<View>(R.id.emptyState).visibility =
                            if (state is UiState.Error) View.VISIBLE else View.GONE
                        findViewById<View>(R.id.content).visibility =
                            if (state is UiState.Success) View.VISIBLE else View.GONE
                        when (state) {
                            is UiState.Success -> render(state.data)
                            is UiState.Error -> {
                                if (state.sessionExpired) goToLogin()
                                findViewById<TextView>(R.id.tvEmptyMessage).text = state.message
                                findViewById<View>(R.id.btnEmptyAction).visibility = View.VISIBLE
                            }
                            UiState.Loading -> Unit
                        }
                    }
                }
                launch { viewModel.detail.collect { renderDetail(it) } }
            }
        }
    }

    private fun render(data: ReviewData) {
        val o: ReviewOverview = data.overview
        dueNow = o.dueNow
        findViewById<TextView>(R.id.tvDueNow).text = o.dueNow.toString()
        findViewById<TextView>(R.id.tvDueLabel).text = when {
            o.total == 0 -> getString(R.string.review_none_yet)
            o.dueNow == 0 -> getString(R.string.review_caught_up)
            else -> resources.getQuantityString(R.plurals.review_due_label, o.dueNow)
        }
        findViewById<TextView>(R.id.tvUrgent).apply {
            visibility = if (o.urgent > 0) View.VISIBLE else View.GONE
            text = resources.getQuantityString(R.plurals.review_urgent, o.urgent, o.urgent)
        }
        findViewById<MaterialButton>(R.id.btnReviewNow).text =
            if (o.dueNow > 0) getString(R.string.home_review_now, o.dueNow) else getString(R.string.home_free_practice)
        findViewById<MaterialButton>(R.id.btnReinforce).visibility = if (o.reinforce > 0) View.VISIBLE else View.GONE

        setTileCount(R.id.tileLearning, o.learning)
        setTileCount(R.id.tileReinforce, o.reinforce)
        setTileCount(R.id.tileReview, o.review)
        setTileCount(R.id.tileMastered, o.mastered)

        findViewById<ForecastBarsView>(R.id.forecast).setData(o.forecast)
        val today = o.forecast.firstOrNull()?.day ?: todayIso()
        findViewById<TextView>(R.id.tvForecastNote).text = getString(
            R.string.review_forecast_note,
            o.averageEase?.let { String.format(Locale("es"), "%.2f", it) } ?: "—",
            if (o.nextDueAt == null) "—" else Formatters.dueIn(o.nextDueAt, today)
        )

        adapter.submit(data.words, today)
        findViewById<View>(R.id.tvWordsEmpty).visibility = if (data.words.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun todayIso() = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())

    // ── Detalle de palabra (hoja inferior) ───────────────────────────────────
    private fun renderDetail(state: UiState<WordDetail>?) {
        if (state == null) { sheet?.dismiss(); sheet = null; return }
        val dialog = sheet ?: BottomSheetDialog(this).also { d ->
            d.setContentView(LayoutInflater.from(this).inflate(R.layout.sheet_word_detail, null))
            d.setOnDismissListener { if (sheet === d) { sheet = null; viewModel.closeDetail() } }
            sheet = d
            d.show()
        }
        val progress = dialog.findViewById<View>(R.id.sheetProgress)
        val content = dialog.findViewById<View>(R.id.sheetContent)
        progress?.visibility = if (state is UiState.Loading) View.VISIBLE else View.GONE
        content?.visibility = if (state is UiState.Success) View.VISIBLE else View.GONE
        when (state) {
            is UiState.Success -> fillDetail(dialog, state.data)
            is UiState.Error -> {
                android.widget.Toast.makeText(this, state.message, android.widget.Toast.LENGTH_SHORT).show()
                dialog.dismiss()
            }
            UiState.Loading -> Unit
        }
    }

    private fun fillDetail(dialog: BottomSheetDialog, w: WordDetail) {
        dialog.findViewById<TextView>(R.id.tvDetailEmoji)?.text = w.emoji ?: w.english.take(1).uppercase()
        dialog.findViewById<TextView>(R.id.tvDetailEnglish)?.text = w.english
        dialog.findViewById<TextView>(R.id.tvDetailMeta)?.text = listOfNotNull(
            w.pronunciation, w.spanish, Formatters.partOfSpeech(w.partOfSpeech), w.level
        ).joinToString(" · ")
        dialog.findViewById<TextView>(R.id.tvDetailExample)?.apply {
            visibility = if (w.exampleEn == null) View.GONE else View.VISIBLE
            text = listOfNotNull(w.exampleEn, w.exampleEs).joinToString("\n")
        }
        dialog.findViewById<TextView>(R.id.tvDetailBucket)?.text =
            if (w.urgent) getString(R.string.review_bucket_urgent, w.bucket.label) else w.bucket.label
        dialog.findViewById<View>(R.id.btnDetailListen)?.setOnClickListener { audio.play(w.english, AudioKind.WORD) }
        dialog.findViewById<ImageButton>(R.id.btnDetailFavorite)?.apply {
            setImageResource(if (w.isFavorite) R.drawable.ic_star_filled else R.drawable.ic_star)
            contentDescription = getString(if (w.isFavorite) R.string.favorite_remove else R.string.favorite_add)
            setOnClickListener { viewModel.toggleFavorite(w.wordId) }
        }

        val today = todayIso()
        val grid = dialog.findViewById<GridLayout>(R.id.gridDetail) ?: return
        grid.removeAllViews()
        listOf(
            getString(R.string.review_d_ease) to (w.easeFactor?.let { String.format(Locale("es"), "%.2f", it) } ?: "—"),
            getString(R.string.review_d_interval) to (w.intervalDays?.let { Formatters.days(it) } ?: "—"),
            getString(R.string.review_d_reps) to (w.repetitions?.toString() ?: "—"),
            getString(R.string.review_d_lapses) to (w.lapses?.toString() ?: "—"),
            getString(R.string.review_d_correct) to "${w.correctCount ?: 0} / ${w.incorrectCount ?: 0}",
            getString(R.string.review_d_quality) to Formatters.quality(w.lastQuality),
            getString(R.string.review_d_first) to Formatters.shortDate(w.firstSeenAt),
            getString(R.string.review_d_last) to Formatters.shortDate(w.lastReviewedAt),
            getString(R.string.review_d_next) to (w.dueAt?.let { "${Formatters.shortDate(it)} (${Formatters.dueIn(it, today)})" } ?: "—")
        ).forEach { (label, value) -> grid.addView(statCell(label, value)) }

        val history = dialog.findViewById<LinearLayout>(R.id.historyList) ?: return
        history.removeAllViews()
        if (w.history.isEmpty()) {
            history.addView(TextView(this).apply {
                setText(R.string.review_history_empty)
                setTextColor(ContextCompat.getColor(context, R.color.color_text_secondary))
            })
        }
        w.history.forEach { a ->
            history.addView(TextView(this).apply {
                text = getString(R.string.review_history_row, if (a.isCorrect) "✅" else "❌",
                    Formatters.shortDate(a.createdAt), a.exerciseType.instruction, Formatters.quality(a.quality))
                setTextColor(ContextCompat.getColor(context, R.color.color_text_primary))
                textSize = 14f
                setPadding(0, (6 * resources.displayMetrics.density).toInt(), 0, (6 * resources.displayMetrics.density).toInt())
            })
        }
    }

    private fun statCell(label: String, value: String): View {
        val d = resources.displayMetrics.density
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, (6 * d).toInt(), (8 * d).toInt(), (6 * d).toInt())
            layoutParams = GridLayout.LayoutParams(GridLayout.spec(GridLayout.UNDEFINED), GridLayout.spec(GridLayout.UNDEFINED, 1f)).apply {
                width = 0
            }
            contentDescription = "$label: $value"
            addView(TextView(context).apply {
                text = label
                setTextColor(ContextCompat.getColor(context, R.color.color_text_hint))
                textSize = 12f
            })
            addView(TextView(context).apply {
                text = value
                setTextColor(ContextCompat.getColor(context, R.color.color_text_primary))
                textSize = 15f
                setTypeface(typeface, android.graphics.Typeface.BOLD)
            })
        }
    }

    override fun onDestroy() {
        sheet?.setOnDismissListener(null)
        sheet?.dismiss()
        super.onDestroy()
    }
}

private class ReviewWordsAdapter(
    private val onClick: (ReviewWord) -> Unit
) : RecyclerView.Adapter<ReviewWordsAdapter.VH>() {

    private var items: List<ReviewWord> = emptyList()
    private var today: String = ""

    fun submit(list: List<ReviewWord>, todayIso: String) {
        items = list
        today = todayIso
        notifyDataSetChanged()
    }

    class VH(v: View) : RecyclerView.ViewHolder(v) {
        val icon: TextView = v.findViewById(R.id.tvWordIcon)
        val english: TextView = v.findViewById(R.id.tvWordEnglish)
        val spanish: TextView = v.findViewById(R.id.tvWordSpanish)
        val due: TextView = v.findViewById(R.id.tvWordDue)
        val bucket: TextView = v.findViewById(R.id.tvWordBucket)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        VH(LayoutInflater.from(parent.context).inflate(R.layout.item_review_word, parent, false))

    override fun onBindViewHolder(h: VH, position: Int) {
        val w = items[position]
        val ctx = h.itemView.context
        h.icon.text = w.emoji ?: w.english.take(1).uppercase()
        h.english.text = w.english
        h.spanish.text = w.spanish
        h.due.text = ctx.getString(R.string.review_word_due, Formatters.dueIn(w.dueAt, today),
            String.format(Locale("es"), "%.2f", w.easeFactor))
        h.bucket.text = if (w.urgent) "⚠️ ${w.bucket.label}" else w.bucket.label
        h.bucket.setBackgroundResource(when (w.bucket) {
            WordBucket.MASTERED -> R.drawable.bg_badge_success
            WordBucket.REINFORCE -> R.drawable.bg_badge_error
            WordBucket.LEARNING -> R.drawable.bg_badge_xp
            else -> R.drawable.bg_badge_accent
        })
        h.itemView.contentDescription = "${w.english}, ${w.spanish}. ${h.bucket.text}. ${h.due.text}"
        h.itemView.setOnClickListener { onClick(w) }
    }

    override fun getItemCount() = items.size
}
