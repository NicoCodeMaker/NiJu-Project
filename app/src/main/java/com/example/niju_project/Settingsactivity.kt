package com.example.niju_project

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class SettingsActivity : AppCompatActivity() {

    private lateinit var rowEditProfile:    LinearLayout
    private lateinit var rowChangePassword: LinearLayout
    private lateinit var rowVerifyEmail:    LinearLayout
    private lateinit var tvVerifyBadge:     TextView
    private lateinit var switchDarkMode:    Switch
    private lateinit var switchReminders:   Switch
    private lateinit var switchLessonAlerts: Switch
    private lateinit var switch2FA:         Switch
    private lateinit var rowClearHistory:   LinearLayout
    private lateinit var rowLanguage:       LinearLayout
    private lateinit var tvCurrentLanguage: TextView
    private lateinit var tvVersion:         TextView
    private lateinit var btnLogout:        LinearLayout
    private lateinit var btnDeleteAccount: LinearLayout
    private lateinit var btnBack:          ImageButton

    private lateinit var mAuth: FirebaseAuth
    private lateinit var prefs: SharedPreferences
    private val db = FirebaseFirestore.getInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        mAuth = FirebaseAuth.getInstance()
        prefs = getSharedPreferences("niju_prefs", Context.MODE_PRIVATE)

        bindViews()
        loadPreferences()
        setupListeners()
    }

    private fun bindViews() {
        btnBack              = findViewById(R.id.btnBack)
        rowEditProfile       = findViewById(R.id.rowEditProfile)
        rowChangePassword    = findViewById(R.id.rowChangePassword)
        rowVerifyEmail       = findViewById(R.id.rowVerifyEmail)
        tvVerifyBadge        = findViewById(R.id.tvVerifyBadge)
        switchDarkMode       = findViewById(R.id.switchDarkMode)
        switchReminders      = findViewById(R.id.switchReminders)
        switchLessonAlerts   = findViewById(R.id.switchLessonAlerts)
        switch2FA            = findViewById(R.id.switch2FA)
        rowClearHistory      = findViewById(R.id.rowClearHistory)
        rowLanguage          = findViewById(R.id.rowLanguage)
        tvCurrentLanguage    = findViewById(R.id.tvCurrentLanguage)
        tvVersion            = findViewById(R.id.tvVersion)
        btnLogout            = findViewById(R.id.btnLogout)
        btnDeleteAccount     = findViewById(R.id.btnDeleteAccount)
    }

    private fun loadPreferences() {
        val uid = mAuth.currentUser?.uid ?: ""

        // 1. Cargar Modo Oscuro (SIN disparar listener)
        val themeMode = prefs.getInt("theme_mode", AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
        switchDarkMode.setOnCheckedChangeListener(null) 
        switchDarkMode.isChecked = (themeMode == AppCompatDelegate.MODE_NIGHT_YES)

        // 2. Otros ajustes locales
        switchReminders.isChecked    = prefs.getBoolean("notif_reminders", true)
        switchLessonAlerts.isChecked = prefs.getBoolean("notif_lessons", true)
        tvCurrentLanguage.text       = prefs.getString("app_language", "Español")

        // 3. Estado 2FA desde Firestore
        if (uid.isNotEmpty()) {
            db.collection("users").document(uid).get()
                .addOnSuccessListener { doc ->
                    val enabled = doc.getBoolean("twoFactorEnabled") ?: false
                    // MUY IMPORTANTE: Quitamos el listener antes de cambiar el estado visual
                    switch2FA.setOnCheckedChangeListener(null)
                    switch2FA.isChecked = enabled
                    // Re-asignamos el listener solo después de poner el valor real
                    attach2FAListener()
                }
        }
    }

    private fun setupListeners() {
        btnBack.setOnClickListener { finish() }
        
        // Listener de Modo Oscuro
        switchDarkMode.setOnCheckedChangeListener { _, isChecked ->
            val newMode = if (isChecked) AppCompatDelegate.MODE_NIGHT_YES else AppCompatDelegate.MODE_NIGHT_NO
            prefs.edit().putInt("theme_mode", newMode).apply()
            AppCompatDelegate.setDefaultNightMode(newMode)
        }

        btnLogout.setOnClickListener { doLogout() }
        
        rowEditProfile.setOnClickListener { startActivity(Intent(this, EditProfileActivity::class.java)) }
        
        // ... (resto de tus otros click listeners normales)
    }

    private fun attach2FAListener() {
        switch2FA.setOnCheckedChangeListener { _, isChecked ->
            val uid = mAuth.currentUser?.uid ?: return@setOnCheckedChangeListener
            if (isChecked) {
                // Ir a configuración (Solo si el usuario lo activó manualmente)
                goTo2FASetup(uid)
            } else {
                // Desactivar con confirmación
                showDisable2FADialog(uid)
            }
        }
    }

    private fun goTo2FASetup(uid: String) {
        db.collection("users").document(uid).get().addOnSuccessListener { doc ->
            val secret = doc.getString("totpSecret") ?: ""
            val email = mAuth.currentUser?.email ?: ""
            val intent = Intent(this, TwoFactorActivity::class.java).apply {
                putExtra("mode", "setup")
                putExtra("totp_secret", com.example.niju_project.utils.EncryptionUtils.decrypt(secret))
                putExtra("user_email", email)
            }
            startActivity(intent)
        }
    }

    private fun showDisable2FADialog(uid: String) {
        AlertDialog.Builder(this)
            .setTitle("Desactivar 2FA")
            .setMessage("¿Estás seguro?")
            .setPositiveButton("Desactivar") { _, _ ->
                db.collection("users").document(uid).update("twoFactorEnabled", false)
                    .addOnSuccessListener {
                        com.example.niju_project.utils.PrefsUtils.set2FAEnabled(this, uid, false)
                    }
            }
            .setNegativeButton("Cancelar") { _, _ -> 
                switch2FA.setOnCheckedChangeListener(null)
                switch2FA.isChecked = true
                attach2FAListener()
            }
            .show()
    }

    private fun doLogout() {
        mAuth.signOut()
        startActivity(Intent(this, LoginActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        })
    }
    
    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
}
