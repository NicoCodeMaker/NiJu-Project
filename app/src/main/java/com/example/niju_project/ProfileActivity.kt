package com.example.niju_project

import android.content.Intent
import android.os.Bundle
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth

class ProfileActivity : AppCompatActivity() {

    private lateinit var backButton: ImageButton
    private lateinit var navHome: LinearLayout
    private lateinit var navContexts: LinearLayout
    private lateinit var navRuta: LinearLayout
    private lateinit var navProfile: LinearLayout
    private lateinit var btnLogout: LinearLayout
    private lateinit var userNameTextView: TextView
    private lateinit var userLocationTextView: TextView
    private lateinit var mAuth: FirebaseAuth

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_profile)

        mAuth = FirebaseAuth.getInstance()
        initViews()
        setupNavigation()

        backButton.setOnClickListener { finish() }

        // 🔹 Mostrar el usuario actual
        showUserData()

        // 🔹 Cerrar sesión
        btnLogout.setOnClickListener { logoutUser() }
    }

    private fun initViews() {
        backButton = findViewById(R.id.back_button)
        navHome = findViewById(R.id.navHome)
        navContexts = findViewById(R.id.navContexts)
        navRuta = findViewById(R.id.navRuta)
        navProfile = findViewById(R.id.navProfile)
        btnLogout = findViewById(R.id.btnLogout)
        userNameTextView = findViewById(R.id.user_name)
        userLocationTextView = findViewById(R.id.user_location)
    }

    private fun showUserData() {
        val user = mAuth.currentUser
        if (user != null) {
            // 🔸 Si el usuario tiene nombre, lo mostramos. Si no, usamos el correo.
            val displayName = user.displayName ?: user.email ?: "Usuario"
            userNameTextView.text = displayName

            // 🔸 Si quieres mostrar la fecha de creación (registro)
            val creationDate = user.metadata?.creationTimestamp?.let {
                val date = java.text.SimpleDateFormat("yyyy-MM-dd").format(java.util.Date(it))
                "Registro: $date"
            } ?: "Registro: desconocido"

            userLocationTextView.text = creationDate
        } else {
            userNameTextView.text = "Usuario no autenticado"
            userLocationTextView.text = ""
        }
    }

    private fun setupNavigation() {
        navHome.setOnClickListener {
            startActivity(Intent(this, HomeActivity::class.java))
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
        }

        navContexts.setOnClickListener {
            startActivity(Intent(this, ContextsActivity::class.java))
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
        }

        navRuta.setOnClickListener {
            startActivity(Intent(this, RutaActivity::class.java))
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
        }

        navProfile.setOnClickListener {
            Toast.makeText(this, "Ya estás en Perfil", Toast.LENGTH_SHORT).show()
        }
    }

    private fun logoutUser() {
        mAuth.signOut()
        Toast.makeText(this, "Sesión cerrada correctamente", Toast.LENGTH_SHORT).show()

        val intent = Intent(this, LoginActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }

    override fun onResume() {
        super.onResume()
        highlightCurrentTab()
    }

    private fun highlightCurrentTab() {
        // Estilos del bottom nav si quieres
    }
}
