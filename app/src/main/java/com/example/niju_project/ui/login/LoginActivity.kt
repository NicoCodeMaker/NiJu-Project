package com.example.niju_project.ui.login

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.niju_project.databinding.ActivityLoginBinding

class LoginActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLoginBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val username = binding.inputEmail
        val password = binding.inputPassword
        val loginBtn = binding.btnLogin

        loginBtn?.setOnClickListener {
            val email = username?.text.toString().trim()
            val pass = password!!.text.toString().trim()

            if (email.isEmpty() || pass.isEmpty()) {
                Toast.makeText(this, "Por favor completa todos los campos", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Login con $email", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
