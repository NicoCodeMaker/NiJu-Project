package com.example.niju_project.ui

import android.app.Activity
import android.app.Application
import android.content.res.Configuration
import android.os.Bundle
import android.view.View
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import com.example.niju_project.R
import kotlin.math.max

/**
 * Barras del sistema iguales en todas las pantallas y en todas las versiones de Android.
 * Desde Android 15 las apps se dibujan de borde a borde; sin esto los encabezados quedarían
 * debajo de la barra de estado y los botones inferiores debajo de la de navegación.
 * El contenido de cada pantalla recibe como relleno el tamaño de las barras y del teclado.
 */
object SystemBars : Application.ActivityLifecycleCallbacks {

    fun install(app: Application) = app.registerActivityLifecycleCallbacks(this)

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
        WindowCompat.setDecorFitsSystemWindows(activity.window, false)
    }

    override fun onActivityStarted(activity: Activity) {
        val content = activity.findViewById<View>(android.R.id.content) ?: return
        if (content.getTag(R.id.tag_insets_applied) == true) return
        content.setTag(R.id.tag_insets_applied, true)
        ViewCompat.setOnApplyWindowInsetsListener(content) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            val ime = insets.getInsets(WindowInsetsCompat.Type.ime())
            v.setPadding(bars.left, bars.top, bars.right, max(bars.bottom, ime.bottom))
            WindowInsetsCompat.CONSUMED
        }
        ViewCompat.requestApplyInsets(content)
        val night = (activity.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES
        WindowCompat.getInsetsController(activity.window, activity.window.decorView).apply {
            isAppearanceLightStatusBars = !night
            isAppearanceLightNavigationBars = !night
        }
    }

    override fun onActivityResumed(activity: Activity) = Unit
    override fun onActivityPaused(activity: Activity) = Unit
    override fun onActivityStopped(activity: Activity) = Unit
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
    override fun onActivityDestroyed(activity: Activity) = Unit
}
