package com.example.niju_project.ui.login

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.niju_project.MainActivity
import com.example.niju_project.R

class LoginActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        val username = findViewById<EditText>(R.id.inputEmail)
        val password = findViewById<EditText>(R.id.inputPassword)
        val loginBtn = findViewById<Button>(R.id.btnLogin)

        loginBtn.setOnClickListener {
            val email = username.text.toString().trim()
            val pass = password.text.toString().trim()

            if (email.isEmpty() || pass.isEmpty()) {
                Toast.makeText(this, "Por favor completa todos los campos", Toast.LENGTH_SHORT).show()
            } else {
                // ✅ Aquí va tu validación real
                if (email == "admin@niju.com" && pass == "1234") {
                    // Login válido → pasa a MainActivity
                    val intent = Intent(this, MainActivity::class.java)
                    startActivity(intent)
                    finish() // no vuelve al login con "back"
                } else {
                    Toast.makeText(this, "Credenciales incorrectas", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
}
