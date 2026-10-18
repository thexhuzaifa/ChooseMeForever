package com.chooseme.forever.data.repository

import android.net.Uri
import com.chooseme.forever.data.model.User
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.tasks.await
import java.util.UUID

class ProfileRepositoryImpl(
    private val firestoreProvider: () -> FirebaseFirestore? = {
        try { FirebaseFirestore.getInstance() } catch (e: Exception) { null }
    },
    private val storageProvider: () -> FirebaseStorage? = {
        try { FirebaseStorage.getInstance() } catch (e: Exception) { null }
    }
) : ProfileRepository {

    override fun getUserProfileFlow(userId: String): Flow<User?> = callbackFlow {
        val firestore = firestoreProvider()
        if (userId.isBlank() || firestore == null) {
            trySend(null)
            close()
            return@callbackFlow
        }

        var listenerRegistration: ListenerRegistration? = null
        try {
            listenerRegistration = firestore.collection("users")
                .document(userId)
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        // Safe fallback on PERMISSION_DENIED or Firestore connection issues
                        trySend(null)
                        return@addSnapshotListener
                    }
                    val user = snapshot?.toObject(User::class.java)
                    trySend(user)
                }
        } catch (e: Exception) {
            trySend(null)
        }

        awaitClose { listenerRegistration?.remove() }
    }.catch {
        emit(null)
    }

    override suspend fun getUserProfile(userId: String): Result<User?> {
        return try {
            val firestore = firestoreProvider() ?: return Result.success(null)
            if (userId.isBlank()) return Result.success(null)
            val snapshot = firestore.collection("users").document(userId).get().await()
            val user = snapshot.toObject(User::class.java)
            Result.success(user)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun saveUserProfile(user: User): Result<Unit> {
        return try {
            val firestore = firestoreProvider() ?: return Result.failure(Exception("Firestore unavailable"))
            firestore.collection("users")
                .document(user.userId)
                .set(user)
                .await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun uploadUserImage(userId: String, imageUri: Uri): Result<String> {
        return try {
            val storage = storageProvider() ?: return Result.failure(Exception("Firebase Storage unavailable"))
            val fileName = "${UUID.randomUUID()}.jpg"
            val photoRef = storage.reference.child("user_photos/$userId/$fileName")
            photoRef.putFile(imageUri).await()
            val downloadUrl = photoRef.downloadUrl.await().toString()
            Result.success(downloadUrl)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
