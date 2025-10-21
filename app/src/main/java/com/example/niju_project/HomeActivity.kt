package com.example.niju_project

import android.os.Bundle
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import android.content.Intent


class HomeActivity : AppCompatActivity() {

    // Variables para las vistas
    private lateinit var btnStartSession: Button
    private lateinit var ivBeachImage: ImageView
    private lateinit var navHome: LinearLayout
    private lateinit var navContexts: LinearLayout
    private lateinit var navFavorites: LinearLayout
    private lateinit var navProfile: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_home)

        // Inicializar vistas
        initViews()

        // Configurar listeners
        setupClickListeners()
    }

    private fun initViews() {
        btnStartSession = findViewById(R.id.btnStartSession)
        ivBeachImage = findViewById(R.id.ivBeachImage)
        navHome = findViewById(R.id.navHome)
        navContexts = findViewById(R.id.navContexts)
        navFavorites = findViewById(R.id.navFavorites)
        navProfile = findViewById(R.id.navProfile)
    }

    private fun setupClickListeners() {
        // Listener para el botón principal
        btnStartSession.setOnClickListener {
            startTodaySession()
        }

        // Listener para la imagen de la playa
        ivBeachImage.setOnClickListener {
            showBeachDetails()
        }

        // Listeners para la navegación inferior
        navHome.setOnClickListener {
            navigateToHome()
        }

        navContexts.setOnClickListener {
            navigateToContexts()
        }

        navFavorites.setOnClickListener {
            navigateToFavorites()
        }

        navProfile.setOnClickListener {
            navigateToProfile()
        }
    }

    /**
     * Función para iniciar la sesión de hoy
     */
    private fun startTodaySession() {
        try {
            // Aquí puedes agregar la lógica para iniciar la sesión
            // Por ejemplo, navegar a una nueva actividad

            showToast("Iniciando sesión de hoy...")

            // Ejemplo de navegación a otra actividad
            // val intent = Intent(this, SessionActivity::class.java)
            // startActivity(intent)

        } catch (e: Exception) {
            showToast("Error al iniciar sesión: ${e.message}")
        }
    }

    /**
     * Función para mostrar detalles de la playa
     */
    private fun showBeachDetails() {
        showToast("Mostrando detalles de la playa")

        // Aquí puedes agregar lógica para mostrar más información
        // Por ejemplo, abrir un diálogo o navegar a otra pantalla
        // val intent = Intent(this, BeachDetailActivity::class.java)
        // startActivity(intent)
    }

    /**
     * Funciones de navegación
     */
    private fun navigateToHome() {
        // Ya estamos en home, no hacer nada o actualizar el estado visual
        showToast("Inicio")
    }

    private fun navigateToContexts() {
        val intent = Intent(this, ContextsActivity::class.java)
        startActivity(intent)
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
    }


    private fun navigateToFavorites() {
        showToast("Navegando a Favoritos")

        // Ejemplo de navegación
        // val intent = Intent(this, FavoritesActivity::class.java)
        // startActivity(intent)
    }

    private fun navigateToProfile() {
        showToast("Navegando a Perfil")

        // Ejemplo de navegación
        // val intent = Intent(this, ProfileActivity::class.java)
        // startActivity(intent)
    }

    /**
     * Función helper para mostrar toasts
     */
    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    /**
     * Función para actualizar el estado visual de la navegación
     */
    private fun updateNavigationState(activeTab: NavigationTab) {
        // Resetear todos los tabs a estado inactivo
        resetNavigationTabs()

        // Activar el tab seleccionado
        when (activeTab) {
            NavigationTab.HOME -> {
                // Cambiar color del ícono y texto de home
                // updateTabAppearance(navHome, true)
            }
            NavigationTab.CONTEXTS -> {
                // updateTabAppearance(navContexts, true)
            }
            NavigationTab.FAVORITES -> {
                // updateTabAppearance(navFavorites, true)
            }
            NavigationTab.PROFILE -> {
                // updateTabAppearance(navProfile, true)
            }
        }
    }

    private fun resetNavigationTabs() {
        // Aquí puedes resetear el aspecto visual de todos los tabs
        // Por ejemplo, cambiar colores de íconos y texto
    }

    /**
     * Enum para manejar los tabs de navegación
     */
    enum class NavigationTab {
        HOME, CONTEXTS, FAVORITES, PROFILE
    }

    override fun onResume() {
        super.onResume()
        // Actualizar estado cuando la actividad regresa al primer plano
        updateNavigationState(NavigationTab.HOME)
    }
}