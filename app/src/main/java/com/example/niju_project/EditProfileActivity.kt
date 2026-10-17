package com.example.niju_project

import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ProgressBar
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.niju_project.data.remote.NijuErrors
import com.example.niju_project.data.repository.AuthRepository
import com.example.niju_project.data.repository.ProfileRepository
import kotlinx.coroutines.launch

/** Edita el nombre visible y la meta diaria de XP. */
class EditProfileActivity : AppCompatActivity() {

    private val profiles by lazy { ProfileRepository() }

    private lateinit var etName: EditText
    private lateinit var etEmail: EditText
    private lateinit var etDailyGoal: EditText
    private lateinit var btnSave: Button
    private lateinit var progressBar: ProgressBar

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_edit_profile)

        etName = findViewById(R.id.etName)
        etEmail = findViewById(R.id.etEmail)
        etDailyGoal = findViewById(R.id.etDailyGoal)
        btnSave = findViewById(R.id.btnSave)
        progressBar = findViewById(R.id.progressBar)

        etEmail.setText(AuthRepository().currentEmail().orEmpty())
        etEmail.isEnabled = false

        findViewById<ImageButton>(R.id.btnBack).setOnClickListener { finish() }
        btnSave.setOnClickListener { save() }
        load()
    }

    private fun load() {
        showLoading(true)
        lifecycleScope.launch {
            profiles.getProfile()
                .onSuccess {
                    etName.setText(it.displayName)
                    etDailyGoal.setText(it.dailyGoalXp.toString())
                }
                .onFailure { toast(NijuErrors.messageFor(it)) }
            showLoading(false)
        }
    }

    private fun save() {
        val name = etName.text.toString().trim()
        val goal = etDailyGoal.text.toString().trim().toIntOrNull()
        ProfileRepository.validate(name, goal)?.let { msg ->
            if (name.length < 2 || name.length > 60) etName.error = msg else etDailyGoal.error = msg
            return
        }
        showLoading(true)
        lifecycleScope.launch {
            profiles.updateProfile(name, goal!!)
                .onSuccess { toast("Perfil actualizado ✓"); finish() }
                .onFailure { toast(NijuErrors.messageFor(it)); showLoading(false) }
        }
    }

    private fun showLoading(show: Boolean) {
        progressBar.visibility = if (show) View.VISIBLE else View.GONE
        btnSave.isEnabled = !show
    }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
}
