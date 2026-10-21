package com.chooseme.forever.data.repository

import com.chooseme.forever.data.model.Like
import com.chooseme.forever.data.model.Match
import com.chooseme.forever.data.model.User
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.tasks.await
import java.util.UUID

class DiscoverRepositoryImpl(
    private val firestoreProvider: () -> FirebaseFirestore? = {
        try { FirebaseFirestore.getInstance() } catch (e: Exception) { null }
    }
) : DiscoverRepository {

    private val defaultDemoUsers = listOf(
        User(
            userId = "user_maria",
            name = "Maria",
            age = 24,
            city = "DC",
            bio = "Coffee lover, weekend traveler. Looking for someone to explore new places with.",
            interests = listOf("Music", "Travel", "Books"),
            photos = emptyList()
        ),
        User(
            userId = "user_sophia",
            name = "Sophia",
            age = 23,
            city = "New York",
            bio = "Architect & food enthusiast. Always up for live concerts and museum dates.",
            interests = listOf("Art", "Coffee", "Fitness"),
            photos = emptyList()
        ),
        User(
            userId = "user_emma",
            name = "Emma",
            age = 26,
            city = "San Francisco",
            bio = "Tech designer who loves hiking, photography, and trying new sushi spots.",
            interests = listOf("Cooking", "Photography", "Travel"),
            photos = emptyList()
        )
    )

    override fun getDiscoveryProfiles(currentUserId: String): Flow<List<User>> = callbackFlow {
        val firestore = firestoreProvider()
        val effectiveUid = currentUserId.ifBlank { "guest_user" }

        if (firestore == null) {
            trySend(defaultDemoUsers)
            close()
            return@callbackFlow
        }

        try {
            firestore.collection("likes")
                .document(effectiveUid)
                .collection("userLikes")
                .get()
                .addOnSuccessListener { swipedSnapshots ->
                    val swipedUserIds = swipedSnapshots.documents.map { it.id }.toSet()

                    firestore.collection("users")
                        .addSnapshotListener { usersSnapshot, error ->
                            if (error != null || usersSnapshot == null) {
                                trySend(defaultDemoUsers.filter { it.userId != effectiveUid && !swipedUserIds.contains(it.userId) })
                                return@addSnapshotListener
                            }

                            val profiles = usersSnapshot.documents
                                .mapNotNull { it.toObject(User::class.java) }
                                .filter { it.userId != effectiveUid && !swipedUserIds.contains(it.userId) }

                            if (profiles.isEmpty()) {
                                val demoFiltered = defaultDemoUsers.filter { it.userId != effectiveUid && !swipedUserIds.contains(it.userId) }
                                trySend(demoFiltered)
                            } else {
                                trySend(profiles)
                            }
                        }
                }
                .addOnFailureListener {
                    trySend(defaultDemoUsers)
                }
        } catch (e: Exception) {
            trySend(defaultDemoUsers)
        }

        awaitClose { }
    }.catch {
        emit(defaultDemoUsers)
    }

    override suspend fun recordSwipe(currentUserId: String, targetUserId: String, isLiked: Boolean): Result<Boolean> {
        return try {
            val firestore = firestoreProvider()
            val effectiveUid = currentUserId.ifBlank { "guest_user" }

            if (firestore == null) {
                val isDemoMatch = isLiked && (targetUserId == "user_maria" || targetUserId == "user_sophia")
                return Result.success(isDemoMatch)
            }

            val swipeData = Like(
                fromUserId = effectiveUid,
                toUserId = targetUserId,
                isLiked = isLiked,
                timestamp = System.currentTimeMillis()
            )

            firestore.collection("likes")
                .document(effectiveUid)
                .collection("userLikes")
                .document(targetUserId)
                .set(swipeData)
                .await()

            if (isLiked) {
                val targetSwipeDoc = firestore.collection("likes")
                    .document(targetUserId)
                    .collection("userLikes")
                    .document(effectiveUid)
                    .get()
                    .await()

                val isTargetLikedUs = targetSwipeDoc.exists() && targetSwipeDoc.getBoolean("isLiked") == true

                if (isTargetLikedUs || targetUserId.startsWith("user_")) {
                    val matchId = UUID.randomUUID().toString()
                    val matchData = Match(
                        matchId = matchId,
                        users = listOf(effectiveUid, targetUserId),
                        createdAt = System.currentTimeMillis()
                    )

                    firestore.collection("matches")
                        .document(matchId)
                        .set(matchData)
                        .await()

                    return Result.success(true)
                }
            }

            Result.success(false)
        } catch (e: Exception) {
            val isDemoMatch = isLiked && (targetUserId == "user_maria" || targetUserId == "user_sophia")
            Result.success(isDemoMatch)
        }
    }
}
