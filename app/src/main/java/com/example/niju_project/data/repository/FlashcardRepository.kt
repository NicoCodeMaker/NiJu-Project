package com.example.niju_project.data.repository

import com.example.niju_project.data.model.FlashcardModel
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

class FlashcardRepository {
    private val db = FirebaseFirestore.getInstance()

    // 🔴 FIX CRÍTICO: subcolección correcta + mapear el id del documento al modelo
    suspend fun getFlashcardsByContext(contextId: String): Result<List<FlashcardModel>> = runCatching {
        db.collection("contexts")
            .document(contextId)
            .collection("flashcards")
            .get()
            .await()
            .documents
            .mapNotNull { doc ->
                doc.toObject(FlashcardModel::class.java)
                    ?.copy(id = doc.id)   // asegurar que el id Firestore quede en el modelo
            }
    }

    // Colección global de respaldo (puede usarse para práctica sin contexto)
    suspend fun getAllFlashcards(): Result<List<FlashcardModel>> = runCatching {
        db.collection("flashcards")
            .get()
            .await()
            .documents
            .mapNotNull { doc ->
                doc.toObject(FlashcardModel::class.java)
                    ?.copy(id = doc.id)
            }
    }
}
