package com.example.niju_project.data.repository

import com.example.niju_project.data.model.UserModel
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.tasks.await
import java.util.concurrent.TimeUnit

class UserRepository {
    private val db  = FirebaseFirestore.getInstance()
    private val col = db.collection("users")

    suspend fun upsertUser(user: UserModel): Result<Unit> = runCatching {
        col.document(user.uid).set(user, SetOptions.merge()).await()
    }

    suspend fun getCurrentUser(uid: String): Result<UserModel?> = runCatching {
        col.document(uid).get().await().toObject(UserModel::class.java)
    }

    suspend fun addXp(uid: String, xpGained: Int): Result<Unit> = runCatching {
        db.runTransaction { tx ->
            val ref     = col.document(uid)
            val current = tx.get(ref).getLong("xp")?.toInt() ?: 0
            val newXp   = current + xpGained
            tx.update(ref, mapOf(
                "xp"           to newXp,
                "level"        to calculateLevel(newXp),
                "lastActiveAt" to Timestamp.now()
            ))
        }.await()
    }

    // 🟠 FIX IMPORTANTE: lógica real de racha por días
    suspend fun updateStreak(uid: String): Result<Unit> = runCatching {
        db.runTransaction { tx ->
            val ref        = col.document(uid)
            val doc        = tx.get(ref)
            val streak     = doc.getLong("streak")?.toInt() ?: 0
            val lastActive = doc.getTimestamp("lastActiveAt")
            val now        = Timestamp.now()

            val newStreak = if (lastActive == null) {
                // Primera sesión
                1
            } else {
                val diffMs   = now.toDate().time - lastActive.toDate().time
                val diffDays = TimeUnit.MILLISECONDS.toDays(diffMs)
                when {
                    diffDays == 0L -> streak           // ya practicó hoy, no modifica
                    diffDays == 1L -> streak + 1       // ayer practicó → racha continúa
                    else           -> 1                // pasaron más de 1 día → reiniciar
                }
            }

            tx.update(ref, mapOf(
                "streak"       to newStreak,
                "lastActiveAt" to now
            ))
        }.await()
    }

    private fun calculateLevel(totalXp: Int): Int = (totalXp / 1000) + 1
}
