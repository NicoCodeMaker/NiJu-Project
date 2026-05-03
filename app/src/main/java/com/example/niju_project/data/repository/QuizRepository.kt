package com.example.niju_project.data.repository

import com.example.niju_project.data.model.QuizModel
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

class QuizRepository {
    private val db = FirebaseFirestore.getInstance()

    suspend fun getQuizzesByContext(contextId: String): Result<List<QuizModel>> = runCatching {
        db.collection("contexts")
            .document(contextId)
            .collection("quizzes")
            .get()
            .await()
            .toObjects(QuizModel::class.java)
    }
}
