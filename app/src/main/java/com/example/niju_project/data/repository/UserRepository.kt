package com.example.niju_project.data.repository

import com.example.niju_project.data.model.UserModel
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.tasks.await

class UserRepository {
    private val db = FirebaseFirestore.getInstance()
    private val col = db.collection("users")

    // Crear o actualizar perfil (merge = no borra campos existentes)
    suspend fun upsertUser(user: UserModel): Result<Unit> = runCatching {
        col.document(user.uid).set(user, SetOptions.merge()).await()
    }

    // Leer perfil actual
    suspend fun getCurrentUser(uid: String): Result<UserModel?> = runCatching {
        col.document(uid).get().await().toObject(UserModel::class.java)
    }

    // Sumar XP tras completar sesion - usa transaction para evitar race conditions
    suspend fun addXp(uid: String, xpGained: Int): Result<Unit> = runCatching {
        db.runTransaction { tx ->
            val ref = col.document(uid)
            val current = tx.get(ref).getLong("xp")?.toInt() ?: 0
            val newTotalXp = current + xpGained
            val newLevel = calculateLevel(newTotalXp)
            
            tx.update(ref, mapOf(
                "xp" to newTotalXp,
                "level" to newLevel,
                "lastActiveAt" to Timestamp.now()
            ))
        }.await()
    }

    suspend fun updateStreak(uid: String): Result<Unit> = runCatching {
        db.runTransaction { tx ->
            val ref = col.document(uid)
            val doc = tx.get(ref)
            val currentStreak = doc.getLong("streak")?.toInt() ?: 0
            val lastActive = doc.getTimestamp("lastActiveAt")
            
            // Lógica de racha básica: si fue ayer, suma. Si fue hoy, nada. Si fue antes de ayer, reinicia.
            tx.update(ref, "streak", currentStreak + 1)
        }.await()
    }

    private fun calculateLevel(totalXp: Int): Int {
        // Ejemplo simple: 1 nivel cada 1000 XP
        return (totalXp / 1000) + 1
    }
}
