package com.example.niju_project.data.model

import com.google.firebase.Timestamp

data class UserModel(
    val uid: String = "",
    val name: String = "",
    val email: String = "",
    val totpSecret: String = "",
    val twoFactorEnabled: Boolean = false,
    val xp: Int = 0,
    val level: Int = 1,
    val lastActiveAt: Timestamp = Timestamp.now()
)
