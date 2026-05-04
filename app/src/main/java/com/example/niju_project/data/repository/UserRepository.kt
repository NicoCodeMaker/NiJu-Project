package com.example.niju_project.data.repository

import com.example.niju_project.data.model.UserModel
import com.example.niju_project.utils.EncryptionUtils
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.tasks.await
import java.util.concurrent.TimeUnit

class UserRepository {
    private val db  = FirebaseFirestore.getInstance()
    private val col = db.collection("users")

    // Crear o actualizar perfil con cifrado del secreto TOTP (Fase 6)
    suspend fun upsertUser(user: UserModel): Result<Unit> = runCatching {
        val userToSave = user.copy(totpSecret = EncryptionUtils.encrypt(user.totpSecret))
        col.document(user.uid).set(userToSave, SetOptions.merge()).await()
    }

    // Leer perfil y descifrar el secreto TOTP automáticamente
    suspend fun getCurrentUser(uid: String): Result<UserModel?> = runCatching {
        val doc = col.document(uid).get().await()
        if (!doc.exists()) return@runCatching null

        val user = doc.toObject(UserModel::class.java) ?: return@runCatching null

        val decryptedSecret = try {
            EncryptionUtils.decrypt(user.totpSecret)
        } catch (e: Exception) {
            user.totpSecret
        }

        user.copy(totpSecret = decryptedSecret)
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

    suspend fun updateTwoFactorEnabled(uid: String, enabled: Boolean): Result<Unit> = runCatching {
        col.document(uid).update("twoFactorEnabled", enabled).await()
    }

    suspend fun checkOrCreateUser(firebaseUser: com.google.firebase.auth.FirebaseUser): Result<UserModel> = runCatching {
        val doc = col.document(firebaseUser.uid).get().await()

        if (doc.exists()) {
            val user = doc.toObject(UserModel::class.java) ?: UserModel(uid = firebaseUser.uid)

            val decryptedSecret = try {
                EncryptionUtils.decrypt(user.totpSecret)
            } catch (e: Exception) {
                ""
            }

            user.copy(totpSecret = decryptedSecret)
        } else {
            val newUser = UserModel(
                uid = firebaseUser.uid,
                email = firebaseUser.email ?: "",
                name = firebaseUser.displayName ?: ""
            )
            col.document(firebaseUser.uid).set(newUser).await()
            newUser
        }
    }

    private fun calculateLevel(totalXp: Int): Int = (totalXp / 1000) + 1

    suspend fun saveTotpSecret(uid: String, secret: String): Result<Unit> = runCatching {
        col.document(uid)
            .set(mapOf("totpSecret" to EncryptionUtils.encrypt(secret)), SetOptions.merge())
            .await()
    }
}
