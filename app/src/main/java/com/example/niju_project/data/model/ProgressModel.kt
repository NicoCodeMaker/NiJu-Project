package com.example.niju_project.data.model

import com.google.firebase.Timestamp

data class ProgressModel(
    val wordId: String = "",
    val easeFactor: Double = 2.5,
    val intervalDays: Int = 0,
    val repetitions: Int = 0,
    val incorrectCount: Int = 0,
    val mastery: Double = 0.0,
    val lastReviewed: Timestamp? = null,
    val nextReview: Timestamp? = null
)
