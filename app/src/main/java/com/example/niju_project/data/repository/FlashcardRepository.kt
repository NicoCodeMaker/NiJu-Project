package com.example.niju_project.data.repository

import com.example.niju_project.data.model.FlashcardModel
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

class FlashcardRepository {
    private val db = FirebaseFirestore.getInstance()

    suspend fun getFlashcardsByContext(contextId: String): Result<List<FlashcardModel>> = runCatching {
        db.collection("contexts")
            .document(contextId)
            .collection("flashcards")
            .get()
            .await()
            .toObjects(FlashcardModel::class.java)
    }

    // Si aún necesitas una colección global para pruebas
    suspend fun getAllFlashcards(): Result<List<FlashcardModel>> = runCatching {
        db.collection("flashcards").get().await().toObjects(FlashcardModel::class.java)
    }
}
