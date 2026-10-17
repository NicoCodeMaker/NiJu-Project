package com.example.niju_project

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import android.os.Bundle
import android.widget.CompoundButton
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.lifecycle.lifecycleScope
import com.example.niju_project.data.remote.NijuErrors
import com.example.niju_project.data.repository.AuthRepository
import com.example.niju_project.data.repository.LearningRepository
import com.example.niju_project.data.repository.ProfileRepository
import com.example.niju_project.reminders.StudyReminders
import kotlinx.coroutines.launch

class SettingsActivity : AppCompatActivity() {

    private lateinit var prefs: SharedPreferences
    private val auth by lazy { AuthRepository() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        prefs = getSharedPreferences(MainApplication.PREFS, Context.MODE_PRIVATE)

        findViewById<ImageButton>(R.id.btnBack).setOnClickListener { finish() }
        findViewById<LinearLayout>(R.id.rowEditProfile).setOnClickListener {
            startActivity(Intent(this, EditProfileActivity::class.java))
        }

        // Apariencia
        val switchDark = findViewById<CompoundButton>(R.id.switchDarkMode)
        switchDark.isChecked =
            prefs.getInt(MainApplication.KEY_THEME, AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM) ==
                AppCompatDelegate.MODE_NIGHT_YES
        switchDark.setOnCheckedChangeListener { _, checked ->
            val mode = if (checked) AppCompatDelegate.MODE_NIGHT_YES else AppCompatDelegate.MODE_NIGHT_NO
            prefs.edit().putInt(MainApplication.KEY_THEME, mode).apply()
            AppCompatDelegate.setDefaultNightMode(mode)
        }

        // Notificaciones: recordatorio diario y aviso de repasos pendientes.
        bindReminder(R.id.switchReminders, StudyReminders.KEY_DAILY)
        bindReminder(R.id.switchLessonAlerts, StudyReminders.KEY_REVIEWS)

        findViewById<TextView>(R.id.tvCurrentLanguage).text = "Español"
        findViewById<LinearLayout>(R.id.rowLanguage).setOnClickListener {
            toast(getString(R.string.language_only_spanish))
        }
        findViewById<LinearLayout>(R.id.rowPrivacyPolicy).setOnClickListener {
            info(R.string.privacy_title, R.string.privacy_text)
        }
        findViewById<LinearLayout>(R.id.rowTerms).setOnClickListener {
            info(R.string.terms_title, R.string.terms_text)
        }
        findViewById<TextView>(R.id.tvVersion).text = "v${BuildConfig.VERSION_NAME}"

        findViewById<LinearLayout>(R.id.rowClearHistory).setOnClickListener { confirmResetProgress() }
        findViewById<LinearLayout>(R.id.btnLogout).setOnClickListener {
            lifecycleScope.launch { auth.signOut(); goToLogin() }
        }
        findViewById<LinearLayout>(R.id.btnDeleteAccount).setOnClickListener { confirmDeleteAccount() }
    }

    /** Interruptor que pide el permiso de notificaciones (Android 13+) al activarse. */
    private fun bindReminder(switchId: Int, key: String) {
        val sw = findViewById<CompoundButton>(switchId)
        sw.isChecked = prefs.getBoolean(key, false) && StudyReminders.canNotify(this)
        sw.setOnCheckedChangeListener { button, checked ->
            if (checked && !StudyReminders.canNotify(this) &&
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
            ) {
                pendingReminderKey = key
                pendingSwitch = button
                notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                StudyReminders.setEnabled(this, key, checked)
            }
        }
    }

    private var pendingReminderKey: String? = null
    private var pendingSwitch: CompoundButton? = null

    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            val key = pendingReminderKey ?: return@registerForActivityResult
            StudyReminders.setEnabled(this, key, granted)
            if (!granted) {
                pendingSwitch?.isChecked = false
                toast(getString(R.string.reminders_permission_denied))
            }
            pendingReminderKey = null
            pendingSwitch = null
        }

    private fun confirmResetProgress() {
        AlertDialog.Builder(this)
            .setTitle("Reiniciar mi progreso")
            .setMessage(
                "Todas tus palabras volverán a empezar el calendario de repasos. " +
                    "Tu XP, tus logros y tu historial se conservan."
            )
            .setPositiveButton("Reiniciar") { _, _ ->
                lifecycleScope.launch {
                    LearningRepository().resetAllProgress()
                        .onSuccess { n -> toast("Progreso reiniciado ($n palabras)") }
                        .onFailure { toast(NijuErrors.messageFor(it)) }
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun confirmDeleteAccount() {
        AlertDialog.Builder(this)
            .setTitle("Eliminar cuenta")
            .setMessage(
                "Se borrarán tu perfil, tu progreso, tu historial y tus logros. " +
                    "Esta acción no se puede deshacer."
            )
            .setPositiveButton("Eliminar") { _, _ ->
                lifecycleScope.launch {
                    ProfileRepository().deleteAccount()
                        .onSuccess { toast("Tu cuenta fue eliminada"); goToLogin() }
                        .onFailure { toast(NijuErrors.messageFor(it)) }
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun info(title: Int, text: Int) {
        AlertDialog.Builder(this).setTitle(title).setMessage(text)
            .setPositiveButton(R.string.ok, null).show()
    }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()

}
