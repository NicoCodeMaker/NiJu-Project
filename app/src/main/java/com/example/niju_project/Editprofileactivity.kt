package com.example.niju_project

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.view.View
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.UserProfileChangeRequest
import com.google.firebase.firestore.FirebaseFirestore

class EditProfileActivity : AppCompatActivity() {

    private lateinit var ivProfilePic:  de.hdodenhof.circleimageview.CircleImageView
    private lateinit var btnChangePic:  TextView
    private lateinit var etName:        EditText
    private lateinit var etEmail:       EditText
    private lateinit var etBio:         EditText
    private lateinit var etCountry:     EditText
    private lateinit var btnSave:       Button
    private lateinit var progressBar:   ProgressBar
    private lateinit var btnBack:       ImageButton

    private lateinit var mAuth: FirebaseAuth
    private val db = FirebaseFirestore.getInstance()
    private var selectedPhotoUri: Uri? = null

    private val pickImage = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            selectedPhotoUri = result.data?.data
            ivProfilePic.setImageURI(selectedPhotoUri)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_edit_profile)

        mAuth = FirebaseAuth.getInstance()
        bindViews()
        loadCurrentData()
        setupListeners()
    }

    private fun bindViews() {
        ivProfilePic = findViewById(R.id.ivProfilePic)
        btnChangePic = findViewById(R.id.btnChangePic)
        etName       = findViewById(R.id.etName)
        etEmail      = findViewById(R.id.etEmail)
        etBio        = findViewById(R.id.etBio)
        etCountry    = findViewById(R.id.etCountry)
        btnSave      = findViewById(R.id.btnSave)
        progressBar  = findViewById(R.id.progressBar)
        btnBack      = findViewById(R.id.btnBack)
    }

    private fun loadCurrentData() {
        val user = mAuth.currentUser ?: return
        val uid  = user.uid

        // Firebase Auth: nombre y email
        etName.setText(user.displayName ?: "")
        etEmail.setText(user.email ?: "")
        etEmail.isEnabled = false   // el correo se cambia desde SettingsActivity

        // Foto actual de Firebase Auth
        user.photoUrl?.let { uri ->
            // Si tienes Glide: Glide.with(this).load(uri).into(ivProfilePic)
            ivProfilePic.setImageURI(uri)
        }

        // Bio y país desde Firestore
        db.collection("users").document(uid).get()
            .addOnSuccessListener { doc ->
                etBio.setText(doc.getString("bio") ?: "")
                etCountry.setText(doc.getString("country") ?: "")
            }
    }

    private fun setupListeners() {
        btnBack.setOnClickListener { finish() }

        btnChangePic.setOnClickListener {
            val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
            pickImage.launch(intent)
        }

        btnSave.setOnClickListener { saveProfile() }
    }

    private fun saveProfile() {
        val name    = etName.text.toString().trim()
        val bio     = etBio.text.toString().trim()
        val country = etCountry.text.toString().trim()

        if (name.isEmpty()) { etName.error = "El nombre no puede estar vacío"; return }
        if (name.length < 2) { etName.error = "Nombre muy corto"; return }

        showLoading(true)

        val user = mAuth.currentUser ?: return
        val uid  = user.uid

        // 1. Actualizar displayName (y foto si se cambió) en Firebase Auth
        val profileBuilder = UserProfileChangeRequest.Builder().setDisplayName(name)
        selectedPhotoUri?.let { profileBuilder.setPhotoUri(it) }

        user.updateProfile(profileBuilder.build())
            .addOnCompleteListener { authTask ->
                if (!authTask.isSuccessful) {
                    showLoading(false)
                    Toast.makeText(this, "Error: ${authTask.exception?.message}", Toast.LENGTH_LONG).show()
                    return@addOnCompleteListener
                }

                // 2. Guardar bio, país y nombre en Firestore
                val updates = hashMapOf<String, Any>(
                    "name"    to name,
                    "bio"     to bio,
                    "country" to country
                )

                db.collection("users").document(uid)
                    .update(updates)
                    .addOnSuccessListener {
                        showLoading(false)
                        Toast.makeText(this, "Perfil actualizado ✓", Toast.LENGTH_SHORT).show()
                        finish()
                    }
                    .addOnFailureListener { e ->
                        showLoading(false)
                        Toast.makeText(this, "Error guardando datos: ${e.message}", Toast.LENGTH_LONG).show()
                    }
            }
    }

    private fun showLoading(show: Boolean) {
        progressBar.visibility = if (show) View.VISIBLE else View.GONE
        btnSave.isEnabled      = !show
    }
}