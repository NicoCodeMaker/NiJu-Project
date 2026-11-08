package com.example.niju_project

import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat

/** Destinos de la barra inferior, en orden. */
private val TABS = linkedMapOf(
    R.id.navHome to HomeActivity::class.java,
    R.id.navContexts to ContextsActivity::class.java,
    R.id.navReview to ReviewActivity::class.java,
    R.id.navRewards to RewardsActivity::class.java,
    R.id.navProfile to ProfileActivity::class.java
)

fun AppCompatActivity.updateBottomNavColors(current: LinearLayout?, vararg navItems: LinearLayout) {
    val selectedColor = ContextCompat.getColor(this, R.color.nav_selected)
    val unselectedColor = ContextCompat.getColor(this, R.color.nav_unselected)
    navItems.forEach { nav ->
        val selected = nav == current
        val color = if (selected) selectedColor else unselectedColor
        (nav.getChildAt(0) as? ImageView)?.apply {
            setColorFilter(color)
            isSelected = selected            // fondo de píldora (bg_nav_item)
        }
        (nav.getChildAt(1) as? TextView)?.setTextColor(color)
        nav.isSelected = selected
        ViewCompat.setStateDescription(nav, if (selected) getString(R.string.nav_current) else null)
    }
}

/**
 * Conecta la barra inferior (layout/bottom_navigation) y resalta la pestaña actual.
 * @param currentId id de la pestaña activa (R.id.navHome, …) o null si ninguna.
 */
fun AppCompatActivity.setupBottomNav(currentId: Int?) {
    val views = TABS.keys.mapNotNull { id -> findViewById<LinearLayout?>(id) }
    TABS.forEach { (id, target) ->
        findViewById<LinearLayout?>(id)?.setOnClickListener { openTab(target) }
    }
    updateBottomNavColors(currentId?.let { findViewById<LinearLayout?>(it) }, *views.toTypedArray())
}
