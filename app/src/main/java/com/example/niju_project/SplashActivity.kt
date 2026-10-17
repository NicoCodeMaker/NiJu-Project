package com.example.niju_project

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.niju_project.data.remote.SupabaseProvider
import com.example.niju_project.data.repository.AuthRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Muestra el logo mientras se recupera la sesión guardada y decide a dónde ir. */
class SplashActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)

        lifecycleScope.launch {
            val started = System.currentTimeMillis()
            val loggedIn = if (SupabaseProvider.isConfigured) {
                runCatching {
                    val auth = AuthRepository()
                    auth.awaitInitialization()
                    auth.isLoggedIn()
                }.getOrDefault(false)
            } else false

            // El logo se ve al menos un instante, sin hacer esperar de más.
            val elapsed = System.currentTimeMillis() - started
            if (elapsed < MIN_SPLASH_MS) delay(MIN_SPLASH_MS - elapsed)

            val target = if (loggedIn) HomeActivity::class.java else LoginActivity::class.java
            startActivity(Intent(this@SplashActivity, target))
            finish()
        }
    }

    private companion object {
        const val MIN_SPLASH_MS = 800L
    }
}
