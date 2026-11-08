package com.example.niju_project.ui.widget

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import android.view.animation.DecelerateInterpolator
import androidx.core.content.ContextCompat
import com.example.niju_project.R
import kotlin.math.min

/** Anillo de puntuación (0–100 %) con el porcentaje en el centro. Se anima al mostrarse. */
class ScoreRingView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {

    private val d = resources.displayMetrics.density
    private val track = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeWidth = 14 * d; strokeCap = Paint.Cap.ROUND
        color = ContextCompat.getColor(context, R.color.color_hero_chip)
    }
    private val arc = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeWidth = 14 * d; strokeCap = Paint.Cap.ROUND
        color = ContextCompat.getColor(context, R.color.color_on_hero)
    }
    private val big = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.color_on_hero)
        textAlign = Paint.Align.CENTER; textSize = 40 * d; isFakeBoldText = true
    }
    private val small = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.color_on_hero_soft)
        textAlign = Paint.Align.CENTER; textSize = 14 * d
    }
    private val rect = RectF()
    private var shown = 0f
    private var caption = ""

    fun setScore(percent: Int, caption: String) {
        this.caption = caption
        contentDescription = "$percent %. $caption"
        ValueAnimator.ofFloat(0f, percent.coerceIn(0, 100).toFloat()).apply {
            duration = 1100
            interpolator = DecelerateInterpolator()
            addUpdateListener { shown = it.animatedValue as Float; invalidate() }
            start()
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val size = min(width, height).toFloat()
        val pad = track.strokeWidth / 2 + 2 * d
        val left = (width - size) / 2 + pad
        val top = (height - size) / 2 + pad
        rect.set(left, top, left + size - 2 * pad, top + size - 2 * pad)
        canvas.drawArc(rect, 0f, 360f, false, track)
        canvas.drawArc(rect, -90f, 360f * shown / 100f, false, arc)
        val cy = height / 2f
        canvas.drawText("${shown.toInt()} %", width / 2f, cy + big.textSize / 3, big)
        canvas.drawText(caption, width / 2f, cy + big.textSize / 3 + small.textSize * 1.6f, small)
    }
}
