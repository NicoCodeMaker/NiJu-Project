package com.example.niju_project.ui.widget

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat
import com.example.niju_project.R
import com.example.niju_project.data.model.ForecastDay
import com.example.niju_project.ui.Formatters

/** Barras de repasos programados para los próximos 7 días (hoy incluye los atrasados). */
class ForecastBarsView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {

    private val d = resources.displayMetrics.density
    private val bar = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = ContextCompat.getColor(context, R.color.color_accent) }
    private val today = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = ContextCompat.getColor(context, R.color.color_primary) }
    private val empty = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = ContextCompat.getColor(context, R.color.color_accent_container) }
    private val label = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.color_text_secondary)
        textAlign = Paint.Align.CENTER; textSize = 12 * d
    }
    private val value = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.color_text_primary)
        textAlign = Paint.Align.CENTER; textSize = 12 * d; isFakeBoldText = true
    }
    private var days: List<ForecastDay> = emptyList()
    private val r = RectF()

    fun setData(list: List<ForecastDay>) {
        days = list
        contentDescription = list.joinToString(", ") { "${Formatters.shortDate(it.day)}: ${it.count}" }
        invalidate()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), resolveSize((150 * d).toInt(), heightMeasureSpec))
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (days.isEmpty()) return
        val max = (days.maxOf { it.count }).coerceAtLeast(1)
        val slot = width / days.size.toFloat()
        val barW = slot * 0.5f
        val bottom = height - 22 * d
        val top = 20 * d
        days.forEachIndexed { i, day ->
            val cx = slot * i + slot / 2
            val h = (bottom - top) * day.count / max
            r.set(cx - barW / 2, bottom - h.coerceAtLeast(4 * d), cx + barW / 2, bottom)
            canvas.drawRoundRect(r, 8 * d, 8 * d, if (day.count == 0) empty else if (i == 0) today else bar)
            canvas.drawText(day.count.toString(), cx, r.top - 6 * d, value)
            canvas.drawText(if (i == 0) "Hoy" else Formatters.dayInitial(day.day), cx, height - 6 * d, label)
        }
    }
}
