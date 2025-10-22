package com.example.niju_project

import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.appcompat.app.AppCompatActivity

fun AppCompatActivity.updateBottomNavColors(
    current: LinearLayout,
    vararg navItems: LinearLayout
) {
    val selectedColor = ContextCompat.getColor(this, R.color.nav_selected)
    val unselectedColor = ContextCompat.getColor(this, R.color.nav_unselected)

    navItems.forEach { nav ->
        val imageView = nav.getChildAt(0) as ImageView
        val textView = nav.getChildAt(1) as TextView

        if (nav == current) {
            imageView.setColorFilter(selectedColor)
            textView.setTextColor(selectedColor)
        } else {
            imageView.setColorFilter(unselectedColor)
            textView.setTextColor(unselectedColor)
        }
    }
}
