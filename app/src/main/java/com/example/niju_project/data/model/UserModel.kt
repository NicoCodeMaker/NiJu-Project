package com.example.niju_project.data.model

import com.google.firebase.Timestamp

/**
 * Modelo de usuario para Firestore.
 * Se usan valores por defecto y nulabilidad para evitar crashes al mapear datos.
 */
data class UserModel(
    val uid: String = "",
    val name: String = "",
    val email: String = "",
    val photoUrl: String? = null,
    val totpSecret: String = "",
    val twoFactorEnabled: Boolean = false,
    val xp: Int = 0,
    val level: Int = 1,
    val streak: Int = 0,
    val dailyGoal: Int = 50,
    val lastActiveAt: Timestamp? = null
)
