package com.example.niju_project.ui.widget

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.provider.Settings
import android.util.AttributeSet
import android.view.View
import android.view.animation.LinearInterpolator
import androidx.core.content.ContextCompat
import com.example.niju_project.R
import kotlin.random.Random

/**
 * Confeti de celebración (al superar una lección, aprobar una evaluación o subir de nivel).
 * Dibujo propio con Canvas: sin dependencias. Si el usuario desactivó las animaciones
 * del sistema, no se muestra.
 */
class ConfettiView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {

    private class Piece(var x: Float, var y: Float, val w: Float, val h: Float, val vx: Float, val vy: Float,
                        var angle: Float, val spin: Float, val color: Int)

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val pieces = mutableListOf<Piece>()
    private var animator: ValueAnimator? = null
    private val colors = intArrayOf(R.color.color_confetti_1, R.color.color_confetti_2, R.color.color_confetti_3,
        R.color.color_confetti_4, R.color.color_confetti_5).map { ContextCompat.getColor(context, it) }

    init {
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
    }

    private fun animationsEnabled(): Boolean =
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) > 0f

    /** Lanza una ráfaga. Se puede llamar antes de que la vista tenga tamaño. */
    fun burst(count: Int = 140) {
        if (!animationsEnabled()) return
        if (width == 0) { post { burst(count) }; return }
        val d = resources.displayMetrics.density
        pieces.clear()
        repeat(count) {
            pieces += Piece(
                x = Random.nextFloat() * width,
                y = -Random.nextFloat() * height * 0.5f,
                w = (6 + Random.nextFloat() * 6) * d,
                h = (10 + Random.nextFloat() * 8) * d,
                vx = (Random.nextFloat() - 0.5f) * 120 * d,
                vy = (220 + Random.nextFloat() * 260) * d,
                angle = Random.nextFloat() * 360,
                spin = (Random.nextFloat() - 0.5f) * 720,
                color = colors[Random.nextInt(colors.size)]
            )
        }
        animator?.cancel()
        var last = 0L
        animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 3200
            interpolator = LinearInterpolator()
            addUpdateListener { a ->
                val now = a.currentPlayTime
                val dt = (now - last).coerceAtLeast(0) / 1000f
                last = now
                pieces.forEach { p ->
                    p.x += p.vx * dt
                    p.y += p.vy * dt
                    p.angle += p.spin * dt
                }
                alpha = if (a.animatedFraction > 0.8f) (1f - a.animatedFraction) * 5f else 1f
                invalidate()
            }
            start()
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        pieces.forEach { p ->
            if (p.y > height + p.h) return@forEach
            paint.color = p.color
            canvas.save()
            canvas.rotate(p.angle, p.x, p.y)
            canvas.drawRoundRect(p.x - p.w / 2, p.y - p.h / 2, p.x + p.w / 2, p.y + p.h / 2, 2f, 2f, paint)
            canvas.restore()
        }
    }

    override fun onDetachedFromWindow() {
        animator?.cancel()
        super.onDetachedFromWindow()
    }
}
