package com.example.niju_project.ui.scene

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.os.Build
import android.os.SystemClock
import android.provider.Settings
import android.util.AttributeSet
import android.view.View
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * Escena animada de un contexto (playa con olas, avión que cruza, bus en la vía…).
 * Todo se dibuja con formas simples: no usa imágenes, así que se ve igual de nítido en
 * cualquier tamaño y nunca deforma el diseño. Anima a ~30 fps solo mientras está visible
 * y queda quieta si el usuario desactivó las animaciones del sistema.
 */
class ContextSceneView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0,
) : View(context, attrs, defStyleAttr) {

    var slug: String? = null
        set(value) {
            field = value
            theme = ContextTheme.of(value)
            skyShader = null
            invalidate()
        }

    private var theme = ContextTheme.DEFAULT
    private val d = resources.displayMetrics.density
    private val start = SystemClock.uptimeMillis()
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND
    }
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER; typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }
    private val path = Path()
    private val rect = RectF()
    private var skyShader: Shader? = null
    private val dash = DashPathEffect(floatArrayOf(2f * d, 8f * d), 0f)

    init {
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
    }

    private fun animationsEnabled(): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) ValueAnimator.areAnimatorsEnabled()
        else Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) != 0f

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        skyShader = null
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return
        val animate = animationsEnabled()
        val t = if (animate) (SystemClock.uptimeMillis() - start) / 1000f else 1.3f
        drawSky(canvas, w, h)
        when (theme.slug) {
            "home" -> home(canvas, w, h, t)
            "school" -> school(canvas, w, h, t)
            "beach" -> beach(canvas, w, h, t)
            "restaurant" -> restaurant(canvas, w, h, t)
            "airport", "default" -> airport(canvas, w, h, t)
            "supermarket" -> supermarket(canvas, w, h, t)
            "shopping" -> shopping(canvas, w, h, t)
            "health" -> health(canvas, w, h, t)
            "transport" -> transport(canvas, w, h, t)
            "technology" -> technology(canvas, w, h, t)
            "work" -> work(canvas, w, h, t)
            "travel" -> travel(canvas, w, h, t)
        }
        if (animate && isShown) postInvalidateDelayed(FRAME_MS)
    }

    // ── Primitivas ─────────────────────────────────────────────────────────
    private fun white(a: Float) = Color.argb((a.coerceIn(0f, 1f) * 255).toInt(), 255, 255, 255)
    private fun alpha(color: Int, a: Int) = Color.argb(a, Color.red(color), Color.green(color), Color.blue(color))

    private fun drawSky(c: Canvas, w: Float, h: Float) {
        val shader = skyShader ?: LinearGradient(0f, 0f, w, h, theme.skyStart, theme.skyEnd, Shader.TileMode.CLAMP)
            .also { skyShader = it }
        fill.shader = shader
        c.drawRect(0f, 0f, w, h, fill)
        fill.shader = null
    }

    private fun circle(c: Canvas, x: Float, y: Float, r: Float, color: Int) {
        fill.color = color; c.drawCircle(x, y, r, fill)
    }

    private fun rrect(c: Canvas, x: Float, y: Float, w: Float, h: Float, r: Float, color: Int) {
        fill.color = color; rect.set(x, y, x + w, y + h); c.drawRoundRect(rect, r, r, fill)
    }

    private fun line(c: Canvas, color: Int, width: Float, vararg pts: Float) {
        stroke.color = color; stroke.strokeWidth = width
        path.reset(); path.moveTo(pts[0], pts[1])
        var i = 2
        while (i < pts.size) { path.lineTo(pts[i], pts[i + 1]); i += 2 }
        c.drawPath(path, stroke)
    }

    private fun polygon(c: Canvas, color: Int, vararg pts: Float) {
        fill.color = color
        path.reset(); path.moveTo(pts[0], pts[1])
        var i = 2
        while (i < pts.size) { path.lineTo(pts[i], pts[i + 1]); i += 2 }
        path.close(); c.drawPath(path, fill)
    }

    private fun cloud(c: Canvas, x: Float, y: Float, s: Float, a: Float) {
        val k = s * d; val col = white(a)
        circle(c, x, y, 10 * k, col); circle(c, x + 12 * k, y - 5 * k, 13 * k, col)
        circle(c, x + 26 * k, y, 10 * k, col); rrect(c, x - 10 * k, y, 46 * k, 10 * k, 5 * k, col)
    }

    private fun hills(c: Canvas, w: Float, h: Float, color: Int, base: Float, amp: Float, phase: Float) {
        fill.color = color
        path.reset(); path.moveTo(0f, h)
        var x = 0f
        while (x <= w) { path.lineTo(x, base + sin(x / w * PI.toFloat() * 2 + phase) * amp); x += 4 * d }
        path.lineTo(w, h); path.close(); c.drawPath(path, fill)
    }

    private fun wrap(v: Float, m: Float) = ((v % m) + m) % m

    // ── Escenas ────────────────────────────────────────────────────────────
    private fun home(c: Canvas, w: Float, h: Float, t: Float) {
        circle(c, w * .82f, h * .28f, h * .14f, white(.85f))
        hills(c, w, h, white(.18f), h * .78f, 6 * d, 0f)
        val s = h * .5f; val x = w * .5f - s / 2; val y = h * .42f
        rrect(c, x, y, s, s * .62f, 6 * d, white(.95f))
        polygon(c, white(.95f), x - 10 * d, y + 2 * d, x + s / 2, y - s * .38f, x + s + 10 * d, y + 2 * d)
        rrect(c, x + s * .66f, y - s * .3f, s * .12f, s * .22f, 2 * d, white(.95f))
        val on = (sin(t * 1.6f) + 1) / 2
        rrect(c, x + s * .14f, y + s * .14f, s * .24f, s * .2f, 3 * d, Color.argb(((.45f + .55f * on) * 255).toInt(), 255, 214, 102))
        rrect(c, x + s * .44f, y + s * .26f, s * .2f, s * .36f, 3 * d, theme.accent)
        for (i in 0 until 3) {
            val p = (t * .5f + i / 3f) % 1f
            circle(c, x + s * .72f + sin(p * 6) * 4 * d, y - s * .32f - p * h * .3f, (3 + p * 6) * d, white(.6f * (1 - p)))
        }
    }

    private fun school(c: Canvas, w: Float, h: Float, t: Float) {
        rrect(c, w * .14f, h * .18f, w * .72f, h * .56f, 8 * d, Color.argb(140, 10, 40, 36))
        rrect(c, w * .14f, h * .7f, w * .72f, h * .06f, 3 * d, white(.5f))
        val n = floor((t * 1.2f) % 5f).toInt().coerceAtMost(3)
        text.color = white(.95f); text.textSize = h * .26f
        c.drawText("ABC".take(n), w * .5f, h * .46f - (text.descent() + text.ascent()) / 2, text)
        for (i in 0 until 5) {
            val tw = (sin(t * 3 + i * 1.7f) + 1) / 2
            circle(c, w * (.1f + i * .2f), h * (.1f + (i % 2) * .06f), (2 + tw * 2) * d, white(.4f + .6f * tw))
        }
    }

    private fun beach(c: Canvas, w: Float, h: Float, t: Float) {
        circle(c, w * .76f, h * .3f, h * .16f, Color.argb(242, 255, 224, 130))
        cloud(c, wrap(w * .1f + t * 12 * d, w + 80 * d) - 40 * d, h * .22f, .8f, .7f)
        for (k in 0 until 3) hills(c, w, h, white(.18f + k * .12f), h * (.6f + k * .08f), 5 * d, t * (1.2f + k * .4f) + k)
        fill.color = Color.argb(242, 255, 228, 170)
        rect.set(w * .2f - w * .32f, h * 1.02f - h * .2f, w * .2f + w * .32f, h * 1.02f + h * .2f); c.drawOval(rect, fill)
        val trunk = Color.parseColor("#0B3558"); val px = w * .23f; val py = h * .45f
        line(c, trunk, 5 * d, w * .18f, h * .95f, w * .2f, h * .65f, px, py)
        for (a in -2..2) {
            val sw = sin(t * 1.5f) * .08f; val ang = a * .7f - 1.57f + sw
            line(c, Color.parseColor("#1E7A4F"), 6 * d, px, py, px + cos(ang) * 30 * d, py + sin(ang) * 16 * d + 13 * d)
        }
    }

    private fun restaurant(c: Canvas, w: Float, h: Float, t: Float) {
        rrect(c, 0f, h * .76f, w, h * .24f, 0f, Color.argb(64, 70, 20, 10))
        fill.color = white(.95f)
        rect.set(w * .28f, h * .67f, w * .72f, h * .81f); c.drawOval(rect, fill)
        rect.set(w * .5f - w * .14f, h * .66f - w * .14f, w * .5f + w * .14f, h * .66f + w * .14f)
        c.save(); c.clipRect(0f, h * .66f, w, h); c.drawOval(rect, fill); c.restore()
        for (i in 0 until 3) {
            val ph = t * 1.4f + i * 2.1f
            stroke.color = white(.35f + .25f * sin(ph)); stroke.strokeWidth = 4 * d
            path.reset()
            for (k in 0 until 8) {
                val x = w * (.44f + i * .06f) + sin(ph + k * .9f) * 6 * d; val y = h * .56f - k * h * .05f
                if (k == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            c.drawPath(path, stroke)
        }
        line(c, white(.8f), 4 * d, w * .18f, h * .5f, w * .18f, h * .78f)
        line(c, white(.8f), 4 * d, w * .82f, h * .5f, w * .82f, h * .78f)
    }

    private fun airport(c: Canvas, w: Float, h: Float, t: Float) {
        cloud(c, wrap(w * .7f - t * 10 * d, w + 80 * d) - 20 * d, h * .3f, 1f, .55f)
        cloud(c, wrap(w * .2f - t * 16 * d, w + 80 * d) - 40 * d, h * .62f, .7f, .45f)
        val p = (t * .18f) % 1f; val x = -60 * d + p * (w + 120 * d); val y = h * .55f - p * h * .25f
        c.save(); c.translate(x, y); c.rotate(-10f)
        val col = white(.97f)
        rrect(c, -34 * d, -6 * d, 68 * d, 12 * d, 6 * d, col)
        polygon(c, col, -6 * d, 0f, 10 * d, 0f, -10 * d, 26 * d, -18 * d, 26 * d)
        polygon(c, col, -6 * d, 0f, 10 * d, 0f, -10 * d, -22 * d, -18 * d, -22 * d)
        polygon(c, col, -34 * d, -6 * d, -24 * d, -6 * d, -30 * d, -18 * d, -36 * d, -18 * d)
        c.restore()
        for (i in 0 until 6) circle(c, x - (44 + i * 12) * d, y + (8 + i * 2) * d, max(.5f, 2.5f - i * .3f) * d, white(.5f - i * .08f))
    }

    private fun supermarket(c: Canvas, w: Float, h: Float, t: Float) {
        for (i in 0 until 3) rrect(c, w * .08f, h * (.2f + i * .2f), w * .84f, 4 * d, 2 * d, white(.35f))
        val items = intArrayOf(0xFFFFD54F.toInt(), 0xFFFF7043.toInt(), Color.WHITE, 0xFFAED581.toInt(), 0xFFFF8A65.toInt())
        items.forEachIndexed { i, col -> circle(c, w * (.16f + i * .17f), h * .16f + 10 * d - abs(sin(t * 2 + i)) * 6 * d, 8 * d, col) }
        val x = wrap(t * 40 * d, w + 140 * d) - 70 * d
        line(c, white(.97f), 4 * d, x - 30 * d, h * .52f, x - 22 * d, h * .52f, x - 12 * d, h * .8f, x + 30 * d, h * .8f)
        rrect(c, x - 14 * d, h * .56f, 46 * d, h * .18f, 4 * d, white(.9f))
        circle(c, x - 6 * d, h * .88f, 5 * d, white(.97f)); circle(c, x + 24 * d, h * .88f, 5 * d, white(.97f))
        circle(c, x + 2 * d, h * .54f, 7 * d, 0xFFFFD54F.toInt()); circle(c, x + 18 * d, h * .52f, 7 * d, 0xFFFF7043.toInt())
    }

    private fun shopping(c: Canvas, w: Float, h: Float, t: Float) {
        for (i in 0 until 3) {
            val sw = sin(t * 2 + i * 1.3f) * 7f
            c.save(); c.translate(w * (.28f + i * .22f), h * .34f); c.rotate(sw)
            line(c, white(.95f), 3 * d, -10 * d, 0f, -10 * d, -12 * d, 10 * d, -12 * d, 10 * d, 0f)
            rrect(c, -22 * d, 0f, 44 * d, (52 - i * 6) * d, 6 * d, if (i == 1) alpha(theme.ink, 170) else white(.95f))
            c.restore()
        }
        for (i in 0 until 6) {
            val p = (t * .4f + i / 6f) % 1f
            val x = w * (.1f + i * .16f); val y = h * (.9f - p * .7f); val r = 4 * d
            polygon(c, white(1 - p), x, y - r, x + r * .3f, y - r * .3f, x + r, y, x + r * .3f, y + r * .3f,
                x, y + r, x - r * .3f, y + r * .3f, x - r, y, x - r * .3f, y - r * .3f)
        }
    }

    private fun health(c: Canvas, w: Float, h: Float, t: Float) {
        val off = (t * 70 * d) % w; val period = 140 * d
        stroke.color = white(.95f); stroke.strokeWidth = 3 * d
        path.reset()
        var x = 0f
        while (x <= w) {
            val u = ((x + off) % period) / period
            var y = h * .55f
            if (u > .4f && u < .45f) y -= (u - .4f) / .05f * h * .3f
            else if (u >= .45f && u < .5f) y = h * .25f + (u - .45f) / .05f * h * .5f
            else if (u >= .5f && u < .55f) y = h * .75f - (u - .5f) / .05f * h * .2f
            if (x == 0f) path.moveTo(x, y) else path.lineTo(x, y)
            x += 4 * d
        }
        c.drawPath(path, stroke)
        val b = 1 + max(0f, sin(t * 6)) * .12f
        c.save(); c.translate(w * .82f, h * .26f); c.scale(b, b)
        rrect(c, -6 * d, -16 * d, 12 * d, 32 * d, 3 * d, white(.95f)); rrect(c, -16 * d, -6 * d, 32 * d, 12 * d, 3 * d, white(.95f))
        c.restore()
    }

    private fun transport(c: Canvas, w: Float, h: Float, t: Float) {
        rrect(c, 0f, h * .78f, w, h * .22f, 0f, Color.argb(89, 60, 40, 10))
        for (i in -1 until 8) rrect(c, wrap(i * 60 * d - t * 80 * d, w + 60 * d) - 30 * d, h * .88f, 30 * d, 4 * d, 2 * d, white(.8f))
        val bw = w * .4f; val x = w * .3f + sin(t) * 8 * d; val y = h * .38f + abs(sin(t * 8)) * 1.5f * d
        rrect(c, x, y, bw, h * .36f, 10 * d, white(.97f))
        val cell = (bw - 16 * d) / 4
        for (i in 0 until 4) rrect(c, x + 8 * d + i * cell, y + 8 * d, cell - 6 * d, h * .12f, 3 * d, alpha(theme.accent, 153))
        for (cx in floatArrayOf(x + w * .08f, x + w * .32f)) {
            circle(c, cx, y + h * .38f, 9 * d, theme.ink); circle(c, cx, y + h * .38f, 3 * d, white(.9f))
        }
    }

    private fun technology(c: Canvas, w: Float, h: Float, t: Float) {
        for (i in 0 until 18) {
            val tw = (sin(t * 2 + i * 2.3f) + 1) / 2
            circle(c, (i * 53 * d) % w, (i * 37 * d) % (h * .6f), (1 + tw) * d, white(.2f + .5f * tw))
        }
        rrect(c, w * .24f, h * .2f, w * .52f, h * .5f, 8 * d, white(.95f))
        rrect(c, w * .27f, h * .25f, w * .46f, h * .4f, 4 * d, theme.ink)
        rrect(c, w * .16f, h * .7f, w * .68f, h * .06f, 3 * d, white(.8f))
        val lengths = floatArrayOf(.6f, .4f, .75f, .3f)
        lengths.forEachIndexed { i, l ->
            val vis = min(1f, max(0f, (t * .8f % 4f) - i))
            if (vis > 0f) rrect(c, w * .3f, h * (.3f + i * .08f), w * .4f * l * vis, 5 * d, 2 * d,
                if (i % 2 == 1) 0xFF7DF9C3.toInt() else theme.skyStart)
        }
        if (floor(t * 2).toInt() % 2 == 1) rrect(c, w * .42f + 4 * d, h * .54f, 6 * d, 9 * d, d, white(.95f))
    }

    private fun work(c: Canvas, w: Float, h: Float, t: Float) {
        val cx = w * .68f; val cy = h * .45f; val r = h * .26f
        circle(c, cx, cy, r, white(.95f)); circle(c, cx, cy, r * .85f, alpha(theme.ink, 16))
        for (i in 0 until 12) {
            val a = i / 12f * PI.toFloat() * 2
            line(c, theme.ink, 2 * d, cx + cos(a) * r * .78f, cy + sin(a) * r * .78f, cx + cos(a) * r * .88f, cy + sin(a) * r * .88f)
        }
        val a1 = t * .8f - PI.toFloat() / 2; val a2 = t * .07f - PI.toFloat() / 2
        line(c, theme.accent, 3 * d, cx, cy, cx + cos(a1) * r * .7f, cy + sin(a1) * r * .7f)
        line(c, theme.ink, 4 * d, cx, cy, cx + cos(a2) * r * .5f, cy + sin(a2) * r * .5f)
        rrect(c, w * .14f, h * .48f, w * .26f, h * .3f, 6 * d, white(.9f))
        line(c, white(.9f), 4 * d, w * .22f, h * .48f, w * .22f, h * .42f, w * .32f, h * .42f, w * .32f, h * .48f)
    }

    private fun travel(c: Canvas, w: Float, h: Float, t: Float) {
        circle(c, w * .5f, h * 1.2f, h * .9f, white(.16f))
        stroke.color = white(.9f); stroke.strokeWidth = 3 * d; stroke.pathEffect = dash
        path.reset()
        for (i in 0..40) {
            val u = i / 40f; val x = w * (.12f + u * .76f); val y = h * (.7f - sin(u * PI.toFloat()) * .45f)
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        c.drawPath(path, stroke); stroke.pathEffect = null
        val p = (t * .25f) % 1f
        val px = w * (.12f + p * .76f); val py = h * (.7f - sin(p * PI.toFloat()) * .45f)
        circle(c, w * .12f, h * .7f, 5 * d, white(.95f)); circle(c, w * .88f, h * .7f, 5 * d, white(.95f))
        circle(c, px, py - 12 * d, 9 * d, white(.97f))
        polygon(c, white(.97f), px - 7 * d, py - 8 * d, px + 7 * d, py - 8 * d, px, py + 2 * d)
        circle(c, px, py - 12 * d, 3.5f * d, theme.accent)
    }

    private companion object {
        const val FRAME_MS = 33L
    }
}
