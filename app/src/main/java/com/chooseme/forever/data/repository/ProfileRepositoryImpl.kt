package com.chooseme.forever.data.repository

import android.content.Context
import android.content.SharedPreferences
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
    private val context: Context? = null,
    private val firestoreProvider: () -> FirebaseFirestore? = {
        try { FirebaseFirestore.getInstance() } catch (e: Exception) { null }
    },
    private val storageProvider: () -> FirebaseStorage? = {
        try { FirebaseStorage.getInstance() } catch (e: Exception) { null }
    }
) : ProfileRepository {

    private val prefs: SharedPreferences?
        get() = context?.getSharedPreferences("user_profile_cache", Context.MODE_PRIVATE)

    override fun getUserProfileFlow(userId: String): Flow<User?> = callbackFlow {
        val cachedUser = getCachedUser(userId)
        if (cachedUser != null) {
            trySend(cachedUser)
        }

        val firestore = firestoreProvider()
        if (userId.isBlank() || firestore == null) {
            if (cachedUser == null) trySend(null)
            close()
            return@callbackFlow
        }

        var listenerRegistration: ListenerRegistration? = null
        try {
            listenerRegistration = firestore.collection("users")
                .document(userId)
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        trySend(getCachedUser(userId))
                        return@addSnapshotListener
                    }
                    val user = snapshot?.toObject(User::class.java)
                    if (user != null) {
                        saveToCache(user)
                        trySend(user)
                    } else {
                        trySend(getCachedUser(userId))
                    }
                }
        } catch (e: Exception) {
            trySend(getCachedUser(userId))
        }

        awaitClose { listenerRegistration?.remove() }
    }.catch {
        emit(getCachedUser(userId))
    }

    override suspend fun getUserProfile(userId: String): Result<User?> {
        val cached = getCachedUser(userId)
        if (cached != null) return Result.success(cached)

        return try {
            val firestore = firestoreProvider() ?: return Result.success(getCachedUser(userId))
            if (userId.isBlank()) return Result.success(null)
            val snapshot = firestore.collection("users").document(userId).get().await()
            val user = snapshot.toObject(User::class.java)
            if (user != null) {
                saveToCache(user)
            }
            Result.success(user ?: getCachedUser(userId))
        } catch (e: Exception) {
            Result.success(getCachedUser(userId))
        }
    }

    override suspend fun saveUserProfile(user: User): Result<Unit> {
        // Save to local cache first
        saveToCache(user)

        return try {
            val firestore = firestoreProvider()
            if (firestore != null && user.userId.isNotBlank()) {
                firestore.collection("users")
                    .document(user.userId)
                    .set(user)
                    .await()
            }
            Result.success(Unit)
        } catch (e: Exception) {
            // Local cache is saved, so return success
            Result.success(Unit)
        }
    }

    override suspend fun uploadUserImage(userId: String, imageUri: Uri): Result<String> {
        return try {
            val storage = storageProvider() ?: return Result.success(imageUri.toString())
            val fileName = "${UUID.randomUUID()}.jpg"
            val photoRef = storage.reference.child("user_photos/$userId/$fileName")
            photoRef.putFile(imageUri).await()
            val downloadUrl = photoRef.downloadUrl.await().toString()
            Result.success(downloadUrl)
        } catch (e: Exception) {
            // If Storage fails or is disabled, fallback to image URI string
            Result.success(imageUri.toString())
        }
    }

    private fun saveToCache(user: User) {
        try {
            prefs?.edit()?.apply {
                putString("userId_${user.userId}", user.userId)
                putString("name_${user.userId}", user.name)
                putInt("age_${user.userId}", user.age)
                putString("city_${user.userId}", user.city)
                putString("bio_${user.userId}", user.bio)
                putString("interests_${user.userId}", user.interests.joinToString(","))
                putString("photos_${user.userId}", user.photos.joinToString(","))
                putString("last_user_id", user.userId)
                apply()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun getCachedUser(userId: String): User? {
        val targetUid = userId.ifBlank { prefs?.getString("last_user_id", "") ?: "" }
        if (targetUid.isBlank()) return null

        val name = prefs?.getString("name_$targetUid", "") ?: ""
        if (name.isBlank()) return null

        val age = prefs?.getInt("age_$targetUid", 0) ?: 0
        val city = prefs?.getString("city_$targetUid", "") ?: ""
        val bio = prefs?.getString("bio_$targetUid", "") ?: ""
        val interestsStr = prefs?.getString("interests_$targetUid", "") ?: ""
        val photosStr = prefs?.getString("photos_$targetUid", "") ?: ""

        val interests = if (interestsStr.isNotBlank()) interestsStr.split(",") else emptyList()
        val photos = if (photosStr.isNotBlank()) photosStr.split(",") else emptyList()

        return User(
            userId = targetUid,
            name = name,
            age = age,
            city = city,
            bio = bio,
            interests = interests,
            photos = photos
        )
    }
}
