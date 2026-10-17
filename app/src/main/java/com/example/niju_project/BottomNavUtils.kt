package com.example.niju_project

import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

fun AppCompatActivity.updateBottomNavColors(
    current: LinearLayout,
    vararg navItems: LinearLayout
) {
    val selectedColor = ContextCompat.getColor(this, R.color.nav_selected)
    val unselectedColor = ContextCompat.getColor(this, R.color.nav_unselected)

    navItems.forEach { nav ->
        val color = if (nav == current) selectedColor else unselectedColor
        (nav.getChildAt(0) as? ImageView)?.setColorFilter(color)
        (nav.getChildAt(1) as? TextView)?.setTextColor(color)
    }
}

/**
 * Conecta la barra inferior (layout/bottom_navigation) y resalta la pestaña actual.
 * @param currentId id de la pestaña activa (R.id.navHome, …) o null si ninguna.
 */
fun AppCompatActivity.setupBottomNav(currentId: Int?) {
    val tabs = linkedMapOf(
        R.id.navHome to HomeActivity::class.java,
        R.id.navContexts to ContextsActivity::class.java,
        R.id.navRuta to RutaActivity::class.java,
        R.id.navProfile to ProfileActivity::class.java
    )
    val views = tabs.keys.mapNotNull { id -> findViewById<LinearLayout?>(id) }
    tabs.forEach { (id, target) ->
        findViewById<LinearLayout?>(id)?.setOnClickListener { openTab(target) }
    }
    currentId?.let { findViewById<LinearLayout?>(it) }?.let {
        updateBottomNavColors(it, *views.toTypedArray())
    }
}
