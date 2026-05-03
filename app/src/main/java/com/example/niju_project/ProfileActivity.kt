package com.example.niju_project

import android.content.Intent
import android.os.Bundle
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class ProfileActivity : AppCompatActivity() {

    private lateinit var backButton:         ImageButton
    private lateinit var navHome:            LinearLayout
    private lateinit var navContexts:        LinearLayout
    private lateinit var navRuta:            LinearLayout
    private lateinit var navProfile:         LinearLayout
    private lateinit var btnLogout:          LinearLayout
    private lateinit var userNameTextView:   TextView
    private lateinit var userLocationTextView: TextView
    private lateinit var mAuth:              FirebaseAuth
    private lateinit var optionFav:          LinearLayout
    private lateinit var optionConfig:       LinearLayout   // ← NUEVO: ir a Configuración
    private lateinit var optionSetup2FA:     LinearLayout   // ← NUEVO: reconfigurar 2FA
    private lateinit var tv2FAStatus:        TextView       // ← NUEVO: estado 2FA

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_profile)

        mAuth = FirebaseAuth.getInstance()
        initViews()
        setupNavigation()

        updateBottomNavColors(
            current = navProfile,
            navHome, navContexts, navRuta, navProfile
        )

        backButton.setOnClickListener { finish() }
        showUserData()
        load2FAStatus()

        btnLogout.setOnClickListener { logoutUser() }

        optionFav.setOnClickListener {
            startActivity(Intent(this, FavoritesActivity::class.java))
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
        }

        // Ir a pantalla de configuración general
        optionConfig.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
        }

        // Reconfigurar / activar 2FA
        optionSetup2FA.setOnClickListener {
            val uid = mAuth.currentUser?.uid ?: return@setOnClickListener
            // Recuperar secret de Firestore y abrir setup
            FirebaseFirestore.getInstance()
                .collection("users").document(uid)
                .get()
                .addOnSuccessListener { doc ->
                    val secret = doc.getString("totpSecret") ?: ""
                    val email  = mAuth.currentUser?.email ?: ""
                    if (secret.isEmpty()) {
                        Toast.makeText(this, "No se encontró el secret 2FA", Toast.LENGTH_SHORT).show()
                        return@addOnSuccessListener
                    }
                    val intent = Intent(this, TwoFactorActivity::class.java).apply {
                        putExtra("mode",        "setup")
                        putExtra("totp_secret", secret)
                        putExtra("user_email",  email)
                    }
                    startActivity(intent)
                }
        }
    }

    private fun initViews() {
        backButton            = findViewById(R.id.back_button)
        navHome               = findViewById(R.id.navHome)
        navContexts           = findViewById(R.id.navContexts)
        navRuta               = findViewById(R.id.navRuta)
        navProfile            = findViewById(R.id.navProfile)
        btnLogout             = findViewById(R.id.btnLogout)
        userNameTextView      = findViewById(R.id.user_name)
        userLocationTextView  = findViewById(R.id.user_location)
        optionFav             = findViewById(R.id.option_fav)
        optionConfig          = findViewById(R.id.option_config)
        // Estos dos IDs deben añadirse al activity_profile.xml (ver INSTRUCCIONES)
        optionSetup2FA        = findViewById(R.id.option_setup_2fa)
        tv2FAStatus           = findViewById(R.id.tv2fa_status)
    }

    private fun showUserData() {
        val user        = mAuth.currentUser ?: return
        val displayName = user.displayName ?: user.email ?: "Usuario"
        userNameTextView.text = displayName

        val creationDate = user.metadata?.creationTimestamp?.let {
            val date = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
                .format(java.util.Date(it))
            "Registro: $date"
        } ?: "Registro: desconocido"
        userLocationTextView.text = creationDate
    }

    /** Lee Firestore para mostrar el estado real del 2FA */
    private fun load2FAStatus() {
        val uid = mAuth.currentUser?.uid ?: return
        FirebaseFirestore.getInstance()
            .collection("users").document(uid)
            .get()
            .addOnSuccessListener { doc ->
                val enabled = doc.getBoolean("twoFactorEnabled") ?: false
                tv2FAStatus.text = if (enabled) "2FA: Activo ✓" else "2FA: Inactivo"
                tv2FAStatus.setTextColor(
                    if (enabled) getColor(android.R.color.holo_green_dark)
                    else         getColor(android.R.color.holo_orange_dark)
                )
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
        val intent = Intent(this, LoginActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }

    override fun onResume() {
        super.onResume()
        load2FAStatus()   // actualizar estado 2FA al volver
    }
}