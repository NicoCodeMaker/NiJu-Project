package com.example.niju_project.ui.stats

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat
import com.example.niju_project.R
import com.example.niju_project.data.model.DailyActivity
import com.example.niju_project.ui.Formatters

/**
 * Barras de XP por día (últimos 7 días). Debajo de cada barra, la inicial del
 * día; encima, el XP si es mayor que 0. Sin dependencias externas.
 */
class ActivityChartView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {

    private var days: List<DailyActivity> = emptyList()
    private var heights: List<Float> = emptyList()

    private val density = resources.displayMetrics.density
    private val barPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.nav_selected)
    }
    private val emptyBarPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.color_divider)
    }
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.color_text_secondary)
        textSize = 12 * resources.displayMetrics.scaledDensity
        textAlign = Paint.Align.CENTER
    }
    private val valuePaint = Paint(labelPaint).apply {
        color = ContextCompat.getColor(context, R.color.color_text_primary)
        textSize = 11 * resources.displayMetrics.scaledDensity
    }
    private val rect = RectF()

    fun setData(data: List<DailyActivity>) {
        days = data
        heights = Formatters.barHeights(data)
        contentDescription = data.joinToString(", ") { "${Formatters.dayInitial(it.day)}: ${it.xp} XP" }
        invalidate()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val desired = (160 * density).toInt()
        setMeasuredDimension(
            getDefaultSize(suggestedMinimumWidth, widthMeasureSpec),
            resolveSize(desired, heightMeasureSpec)
        )
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (days.isEmpty()) return
        val labelArea = 22 * density
        val valueArea = 18 * density
        val chartTop = paddingTop + valueArea
        val chartBottom = height - paddingBottom - labelArea
        val chartHeight = (chartBottom - chartTop).coerceAtLeast(1f)
        val slot = (width - paddingLeft - paddingRight).toFloat() / days.size
        val barWidth = slot * 0.5f
        val radius = 6 * density
        val minBar = 3 * density

        days.forEachIndexed { i, day ->
            val cx = paddingLeft + slot * i + slot / 2
            val h = (heights[i] * chartHeight).coerceAtLeast(minBar)
            rect.set(cx - barWidth / 2, chartBottom - h, cx + barWidth / 2, chartBottom)
            canvas.drawRoundRect(rect, radius, radius, if (day.xp > 0) barPaint else emptyBarPaint)
            if (day.xp > 0) canvas.drawText(day.xp.toString(), cx, rect.top - 4 * density, valuePaint)
            canvas.drawText(Formatters.dayInitial(day.day), cx, height - paddingBottom - 6 * density, labelPaint)
        }
    }
}
