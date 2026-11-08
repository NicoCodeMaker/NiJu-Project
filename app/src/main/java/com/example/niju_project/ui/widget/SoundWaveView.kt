package com.example.niju_project.ui.widget

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.os.Build
import android.os.SystemClock
import android.provider.Settings
import android.util.AttributeSet
import android.view.View
import kotlin.math.abs
import kotlin.math.sin

/**
 * Onda de sonido: barras que se mueven mientras suena el audio y quedan bajas y quietas
 * cuando no. Ayuda a asociar lo que se ve con lo que se escucha.
 */
class SoundWaveView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0,
) : View(context, attrs, defStyleAttr) {

    var color: Int = 0xFF4E5BE8.toInt()
        set(value) { field = value; invalidate() }

    var playing: Boolean = false
        set(value) {
            if (field == value) return
            field = value
            invalidate()
        }

    private val d = resources.displayMetrics.density
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rect = RectF()
    private val start = SystemClock.uptimeMillis()

    init { importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO }

    private fun animationsEnabled(): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) ValueAnimator.areAnimatorsEnabled()
        else Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) != 0f

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val barW = 4 * d
        val gap = 3 * d
        val n = ((width + gap) / (barW + gap)).toInt().coerceAtMost(28)
        if (n <= 0) return
        val total = n * barW + (n - 1) * gap
        var x = (width - total) / 2
        val t = (SystemClock.uptimeMillis() - start) / 1000f
        val animate = playing && animationsEnabled()
        for (i in 0 until n) {
            val shape = .4f + .6f * abs(sin(i * 1.3f))
            val level = if (animate) .2f + .8f * abs(sin(t * 6 + i * .7f)) * shape else .18f + .1f * shape
            val h = (height * level).coerceAtLeast(4 * d)
            paint.color = color
            paint.alpha = if (animate) (115 + 140 * abs(sin(t * 3 + i))).toInt().coerceAtMost(255) else 90
            rect.set(x, (height - h) / 2, x + barW, (height + h) / 2)
            canvas.drawRoundRect(rect, barW / 2, barW / 2, paint)
            x += barW + gap
        }
        if (animate && isShown) postInvalidateDelayed(33L)
    }
}
