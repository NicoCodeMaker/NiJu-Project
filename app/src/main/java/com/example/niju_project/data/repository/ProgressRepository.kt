package com.example.niju_project.data.repository

import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

class ProgressRepository {
    private val db = FirebaseFirestore.getInstance()
    
    suspend fun getProgress(userId: String, wordId: String): Result<com.example.niju_project.data.model.ProgressModel?> = runCatching {
        db.collection("users").document(userId)
            .collection("progress").document(wordId)
            .get().await().toObject(com.example.niju_project.data.model.ProgressModel::class.java)
    }

    suspend fun saveProgress(userId: String, progress: com.example.niju_project.data.model.ProgressModel): Result<Unit> = runCatching {
        db.collection("users").document(userId)
            .collection("progress").document(progress.wordId)
            .set(progress).await()
    }
}
