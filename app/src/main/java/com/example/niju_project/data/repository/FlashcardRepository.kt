package com.example.niju_project.data.repository

import com.example.niju_project.data.model.FlashcardModel
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

class FlashcardRepository {
    private val db = FirebaseFirestore.getInstance()
    private val col = db.collection("flashcards")

    suspend fun getFlashcardsByCategory(category: String): Result<List<FlashcardModel>> = runCatching {
        col.whereEqualTo("category", category)
            .get()
            .await()
            .toObjects(FlashcardModel::class.java)
    }

    suspend fun getAllFlashcards(): Result<List<FlashcardModel>> = runCatching {
        col.get().await().toObjects(FlashcardModel::class.java)
    }
}
