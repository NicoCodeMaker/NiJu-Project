package com.example.niju_project.data.repository

import com.google.firebase.auth.AuthCredential
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser

class AuthRepository {
    private val auth = FirebaseAuth.getInstance()

    fun signInWithEmail(email: String, pass: String, callback: (Result<FirebaseUser>) -> Unit) {
        auth.signInWithEmailAndPassword(email, pass)
            .addOnSuccessListener { result ->
                result.user?.let { callback(Result.success(it)) } ?: callback(Result.failure(Exception("User is null")))
            }
            .addOnFailureListener { callback(Result.failure(it)) }
    }

    fun signUpWithEmail(email: String, pass: String, callback: (Result<FirebaseUser>) -> Unit) {
        auth.createUserWithEmailAndPassword(email, pass)
            .addOnSuccessListener { result ->
                result.user?.let { callback(Result.success(it)) } ?: callback(Result.failure(Exception("User is null")))
            }
            .addOnFailureListener { callback(Result.failure(it)) }
    }

    fun updateProfileAndVerify(user: FirebaseUser, name: String, callback: (Result<Unit>) -> Unit) {
        val profileUpdate = com.google.firebase.auth.UserProfileChangeRequest.Builder()
            .setDisplayName(name).build()
        
        user.updateProfile(profileUpdate)
            .addOnSuccessListener {
                user.sendEmailVerification()
                callback(Result.success(Unit))
            }
            .addOnFailureListener { callback(Result.failure(it)) }
    }

    fun signInWithCredential(credential: AuthCredential, callback: (Result<FirebaseUser>) -> Unit) {
        auth.signInWithCredential(credential)
            .addOnSuccessListener { result ->
                result.user?.let { callback(Result.success(it)) } ?: callback(Result.failure(Exception("User is null")))
            }
            .addOnFailureListener { callback(Result.failure(it)) }
    }

    fun sendPasswordReset(email: String, callback: (Result<Unit>) -> Unit) {
        auth.sendPasswordResetEmail(email)
            .addOnSuccessListener { callback(Result.success(Unit)) }
            .addOnFailureListener { callback(Result.failure(it)) }
    }

    fun signOut() {
        auth.signOut()
    }

    fun getCurrentUser(): FirebaseUser? = auth.currentUser
}
