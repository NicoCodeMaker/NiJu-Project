package com.example.niju_project.data.repository

import com.example.niju_project.data.model.ContextModel
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

class ContextRepository {
    private val db = FirebaseFirestore.getInstance()
    private val col = db.collection("contexts")

    suspend fun getAllContexts(): Result<List<ContextModel>> = runCatching {
        col.get().await().toObjects(ContextModel::class.java)
    }

    suspend fun getContextById(id: String): Result<ContextModel?> = runCatching {
        col.document(id).get().await().toObject(ContextModel::class.java)
    }
}
