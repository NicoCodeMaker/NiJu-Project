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

    // ── Cuenta ────────────────────────────────────────────────────────────
    private lateinit var rowEditProfile:    LinearLayout
    private lateinit var rowChangePassword: LinearLayout
    private lateinit var rowVerifyEmail:    LinearLayout
    private lateinit var tvVerifyBadge:     TextView

    // ── Apariencia ────────────────────────────────────────────────────────
    private lateinit var switchDarkMode: Switch

    // ── Notificaciones ────────────────────────────────────────────────────
    private lateinit var switchReminders:   Switch
    private lateinit var switchLessonAlerts: Switch

    // ── Privacidad ────────────────────────────────────────────────────────
    private lateinit var switch2FA:         Switch
    private lateinit var rowClearHistory:   LinearLayout

    // ── App ───────────────────────────────────────────────────────────────
    private lateinit var rowLanguage:       LinearLayout
    private lateinit var tvCurrentLanguage: TextView
    private lateinit var rowTerms:          LinearLayout
    private lateinit var rowPrivacyPolicy:  LinearLayout
    private lateinit var rowVersion:        LinearLayout
    private lateinit var tvVersion:         TextView

    // ── Sesión ────────────────────────────────────────────────────────────
    private lateinit var btnLogout:         LinearLayout
    private lateinit var btnDeleteAccount:  LinearLayout

    // ── Header ────────────────────────────────────────────────────────────
    private lateinit var btnBack: ImageButton

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

    // ── Bind ─────────────────────────────────────────────────────────────────
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
        rowTerms             = findViewById(R.id.rowTerms)
        rowPrivacyPolicy     = findViewById(R.id.rowPrivacyPolicy)
        rowVersion           = findViewById(R.id.rowVersion)
        tvVersion            = findViewById(R.id.tvVersion)
        btnLogout            = findViewById(R.id.btnLogout)
        btnDeleteAccount     = findViewById(R.id.btnDeleteAccount)
    }

    // ── Cargar estado guardado ────────────────────────────────────────────────
    private fun loadPreferences() {
        val uid = mAuth.currentUser?.uid ?: ""

        // Tema
        switchDarkMode.isChecked     = prefs.getBoolean("dark_mode", false)
        // Notificaciones
        switchReminders.isChecked    = prefs.getBoolean("notif_reminders", true)
        switchLessonAlerts.isChecked = prefs.getBoolean("notif_lessons", true)
        // Idioma
        tvCurrentLanguage.text       = prefs.getString("app_language", "Español")
        // Versión
        tvVersion.text = try {
            "v${packageManager.getPackageInfo(packageName, 0).versionName}"
        } catch (e: Exception) { "v1.0" }

        // Badge de verificación de correo
        val user = mAuth.currentUser
        if (user?.isEmailVerified == true) {
            tvVerifyBadge.text = "✓ Verificado"
            tvVerifyBadge.setTextColor(getColor(android.R.color.holo_green_dark))
        } else {
            tvVerifyBadge.text = "Sin verificar"
            tvVerifyBadge.setTextColor(getColor(android.R.color.holo_orange_dark))
        }

        // Estado 2FA desde Firestore (fuente de verdad)
        if (uid.isNotEmpty()) {
            db.collection("users").document(uid).get()
                .addOnSuccessListener { doc ->
                    val enabled = doc.getBoolean("twoFactorEnabled") ?: false
                    // Evitar disparar el listener al setear programáticamente
                    switch2FA.setOnCheckedChangeListener(null)
                    switch2FA.isChecked = enabled
                    setup2FAListener()
                }
        } else {
            switch2FA.isChecked = false
            setup2FAListener()
        }
    }

    // ── Listeners ────────────────────────────────────────────────────────────
    private fun setupListeners() {
        btnBack.setOnClickListener { finish() }

        // ── Cuenta ────────────────────────────────────────────────────────
        rowEditProfile.setOnClickListener {
            startActivity(Intent(this, EditProfileActivity::class.java))
        }

        rowChangePassword.setOnClickListener {
            val email = mAuth.currentUser?.email ?: return@setOnClickListener
            AlertDialog.Builder(this)
                .setTitle("Cambiar contraseña")
                .setMessage("Se enviará un correo de restablecimiento a:\n$email")
                .setPositiveButton("Enviar") { _, _ ->
                    mAuth.sendPasswordResetEmail(email)
                        .addOnCompleteListener { t ->
                            if (t.isSuccessful) toast("Correo enviado a $email")
                            else toast("Error: ${t.exception?.message}")
                        }
                }
                .setNegativeButton("Cancelar", null)
                .show()
        }

        rowVerifyEmail.setOnClickListener {
            val user = mAuth.currentUser ?: return@setOnClickListener
            if (user.isEmailVerified) {
                toast("Tu correo ya está verificado ✓")
            } else {
                user.sendEmailVerification().addOnCompleteListener { t ->
                    if (t.isSuccessful) toast("Correo de verificación enviado")
                    else toast("Error: ${t.exception?.message}")
                }
            }
        }

        // ── Apariencia ────────────────────────────────────────────────────
        switchDarkMode.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("dark_mode", isChecked).apply()
            AppCompatDelegate.setDefaultNightMode(
                if (isChecked) AppCompatDelegate.MODE_NIGHT_YES
                else AppCompatDelegate.MODE_NIGHT_NO
            )
        }

        // ── Notificaciones ────────────────────────────────────────────────
        switchReminders.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("notif_reminders", isChecked).apply()
            toast(if (isChecked) "Recordatorios activados" else "Recordatorios desactivados")
        }
        switchLessonAlerts.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("notif_lessons", isChecked).apply()
        }

        // ── 2FA ───────────────────────────────────────────────────────────
        setup2FAListener()

        // ── Privacidad ────────────────────────────────────────────────────
        rowClearHistory.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("Borrar historial")
                .setMessage("¿Eliminar todo tu historial de lecciones? Esta acción no se puede deshacer.")
                .setPositiveButton("Borrar") { _, _ ->
                    prefs.edit().remove("lesson_history").apply()
                    toast("Historial borrado")
                }
                .setNegativeButton("Cancelar", null)
                .show()
        }

        // ── App ───────────────────────────────────────────────────────────
        rowLanguage.setOnClickListener { showLanguageDialog() }

        rowTerms.setOnClickListener {
            openUrl("https://niju.app/terminos")
        }
        rowPrivacyPolicy.setOnClickListener {
            openUrl("https://niju.app/privacidad")
        }
        rowVersion.setOnClickListener {
            toast("NiJu — Aprende japonés 🇯🇵")
        }

        // ── Sesión ────────────────────────────────────────────────────────
        btnLogout.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("Cerrar sesión")
                .setMessage("¿Quieres cerrar tu sesión actual?")
                .setPositiveButton("Cerrar sesión") { _, _ -> doLogout() }
                .setNegativeButton("Cancelar", null)
                .show()
        }

        btnDeleteAccount.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("⚠️ Eliminar cuenta")
                .setMessage("Se eliminarán todos tus datos y progreso de forma permanente.\n\n¿Estás seguro?")
                .setPositiveButton("Sí, eliminar") { _, _ -> deleteAccount() }
                .setNegativeButton("Cancelar", null)
                .show()
        }
    }

    /**
     * Listener del switch 2FA separado para poder reasignarlo sin dispararse
     * durante la carga inicial del estado desde Firestore.
     */
    private fun setup2FAListener() {
        switch2FA.setOnCheckedChangeListener { _, isChecked ->
            val uid = mAuth.currentUser?.uid ?: return@setOnCheckedChangeListener

            if (isChecked) {
                // Activar → abrir pantalla de setup con QR
                db.collection("users").document(uid).get()
                    .addOnSuccessListener { doc ->
                        val secret = doc.getString("totpSecret") ?: ""
                        val email  = mAuth.currentUser?.email ?: ""
                        if (secret.isEmpty()) {
                            toast("Error: no se encontró el secret. Vuelve a registrarte.")
                            switch2FA.isChecked = false
                            return@addOnSuccessListener
                        }
                        val intent = Intent(this, TwoFactorActivity::class.java).apply {
                            putExtra("mode",        "setup")
                            putExtra("totp_secret", secret)
                            putExtra("user_email",  email)
                        }
                        startActivity(intent)
                    }
                    .addOnFailureListener {
                        toast("Error al obtener datos")
                        switch2FA.isChecked = false
                    }
            } else {
                // Desactivar → confirmar y actualizar Firestore
                AlertDialog.Builder(this)
                    .setTitle("Desactivar 2FA")
                    .setMessage("Tu cuenta será menos segura. ¿Confirmas que quieres desactivar la verificación en 2 pasos?")
                    .setPositiveButton("Desactivar") { _, _ ->
                        db.collection("users").document(uid)
                            .update("twoFactorEnabled", false)
                            .addOnSuccessListener {
                                prefs.edit().putBoolean("2fa_enabled_$uid", false).apply()
                                toast("2FA desactivado")
                            }
                            .addOnFailureListener {
                                switch2FA.isChecked = true   // revertir
                                toast("Error al desactivar 2FA")
                            }
                    }
                    .setNegativeButton("Cancelar") { _, _ ->
                        // El usuario canceló: revertir el switch sin disparar el listener
                        switch2FA.setOnCheckedChangeListener(null)
                        switch2FA.isChecked = true
                        setup2FAListener()
                    }
                    .show()
            }
        }
    }

    // ── Dialogs ───────────────────────────────────────────────────────────────
    private fun showLanguageDialog() {
        val languages = arrayOf("Español", "English", "Português", "日本語")
        val current   = prefs.getString("app_language", "Español") ?: "Español"
        val selected  = languages.indexOf(current).coerceAtLeast(0)

        AlertDialog.Builder(this)
            .setTitle("Idioma de la interfaz")
            .setSingleChoiceItems(languages, selected) { dialog, which ->
                val chosen = languages[which]
                prefs.edit().putString("app_language", chosen).apply()
                tvCurrentLanguage.text = chosen
                toast("Idioma cambiado a $chosen")
                dialog.dismiss()
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    // ── Acciones de sesión ────────────────────────────────────────────────────
    private fun doLogout() {
        mAuth.signOut()
        startActivity(
            Intent(this, LoginActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }
        )
    }

    private fun deleteAccount() {
        val uid = mAuth.currentUser?.uid
        mAuth.currentUser?.delete()
            ?.addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    // Borrar documento de Firestore también
                    if (uid != null) {
                        db.collection("users").document(uid).delete()
                    }
                    toast("Cuenta eliminada")
                    startActivity(
                        Intent(this, LoginActivity::class.java).apply {
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                        }
                    )
                } else {
                    toast("Error: ${task.exception?.message}\nVuelve a iniciar sesión e intenta de nuevo.")
                }
            }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────
    private fun openUrl(url: String) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse(url)))
        } catch (e: Exception) {
            toast("No se pudo abrir el enlace")
        }
    }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()

    override fun onResume() {
        super.onResume()
        // Recargar estado del 2FA al volver de TwoFactorActivity
        val uid = mAuth.currentUser?.uid ?: return
        db.collection("users").document(uid).get()
            .addOnSuccessListener { doc ->
                val enabled = doc.getBoolean("twoFactorEnabled") ?: false
                switch2FA.setOnCheckedChangeListener(null)
                switch2FA.isChecked = enabled
                setup2FAListener()
            }
    }
}