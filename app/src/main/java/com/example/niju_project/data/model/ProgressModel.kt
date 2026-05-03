package com.example.niju_project.data.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.PropertyName

// 🟠 FIX IMPORTANTE: @JvmField evita crashes con Firestore al deserializar Timestamp?
data class ProgressModel(
    @JvmField val wordId: String = "",
    @JvmField val easeFactor: Double = 2.5,
    @JvmField val intervalDays: Int = 0,
    @JvmField val repetitions: Int = 0,
    @JvmField val incorrectCount: Int = 0,
    @JvmField val mastery: Double = 0.0,
    @JvmField @PropertyName("lastReviewed") val lastReviewed: Timestamp? = null,
    @JvmField @PropertyName("nextReview")   val nextReview:   Timestamp? = null
)
