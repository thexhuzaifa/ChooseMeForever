package com.chooseme.forever.data.repository

import com.chooseme.forever.data.model.Like
import com.chooseme.forever.data.model.LikeRequest
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

    private val botProfiles = listOf(
        User(
            userId = "bot_maria",
            name = "Maria",
            age = 24,
            city = "DC",
            bio = "Coffee lover, weekend traveler. Looking for someone to explore new places with.",
            interests = listOf("Music", "Travel", "Books"),
            photos = emptyList()
        ),
        User(
            userId = "bot_sophia",
            name = "Sophia",
            age = 23,
            city = "New York",
            bio = "Architect & food enthusiast. Always up for live concerts and museum dates.",
            interests = listOf("Art", "Coffee", "Fitness"),
            photos = emptyList()
        ),
        User(
            userId = "bot_emma",
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
            trySend(botProfiles)
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
                                trySend(botProfiles.filter { it.userId != effectiveUid && !swipedUserIds.contains(it.userId) })
                                return@addSnapshotListener
                            }

                            val realProfiles = usersSnapshot.documents
                                .mapNotNull { it.toObject(User::class.java) }
                                .filter { it.userId != effectiveUid && !swipedUserIds.contains(it.userId) }

                            if (realProfiles.isEmpty()) {
                                val filteredBots = botProfiles.filter { it.userId != effectiveUid && !swipedUserIds.contains(it.userId) }
                                trySend(filteredBots)
                            } else {
                                trySend(realProfiles)
                            }
                        }
                }
                .addOnFailureListener {
                    trySend(botProfiles)
                }
        } catch (e: Exception) {
            trySend(botProfiles)
        }

        awaitClose { }
    }.catch {
        emit(botProfiles)
    }

    override fun getIncomingLikeRequests(currentUserId: String): Flow<List<LikeRequest>> = callbackFlow {
        val firestore = firestoreProvider()
        if (firestore == null || currentUserId.isBlank()) {
            trySend(emptyList())
            close()
            return@callbackFlow
        }

        try {
            val listener = firestore.collection("requests")
                .document(currentUserId)
                .collection("incomingLikes")
                .whereEqualTo("status", "PENDING")
                .addSnapshotListener { snapshot, error ->
                    if (error != null || snapshot == null) {
                        trySend(emptyList())
                        return@addSnapshotListener
                    }

                    val requests = snapshot.documents.mapNotNull { it.toObject(LikeRequest::class.java) }
                    trySend(requests)
                }

            awaitClose { listener.remove() }
        } catch (e: Exception) {
            trySend(emptyList())
            awaitClose { }
        }
    }.catch {
        emit(emptyList())
    }

    override suspend fun recordSwipe(currentUserId: String, targetUserId: String, isLiked: Boolean): Result<Boolean> {
        return try {
            val firestore = firestoreProvider()
            val effectiveUid = currentUserId.ifBlank { "guest_user" }

            if (firestore == null) {
                val isDemoMatch = isLiked && targetUserId.startsWith("bot_")
                return Result.success(isDemoMatch)
            }

            // 1. Record swipe in likes/{currentUserId}/userLikes/{targetUserId}
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
                // Check if target user has sent an incoming like request or liked current user back
                val incomingRequestDoc = firestore.collection("requests")
                    .document(effectiveUid)
                    .collection("incomingLikes")
                    .document(targetUserId)
                    .get()
                    .await()

                val targetSwipeDoc = firestore.collection("likes")
                    .document(targetUserId)
                    .collection("userLikes")
                    .document(effectiveUid)
                    .get()
                    .await()

                val isMutual = incomingRequestDoc.exists() || (targetSwipeDoc.exists() && targetSwipeDoc.getBoolean("isLiked") == true) || targetUserId.startsWith("bot_")

                if (isMutual) {
                    // Mutual Match Triggered! Create Match Document
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

                    // Remove processed incoming request
                    if (incomingRequestDoc.exists()) {
                        firestore.collection("requests")
                            .document(effectiveUid)
                            .collection("incomingLikes")
                            .document(targetUserId)
                            .delete()
                            .await()
                    }

                    return Result.success(true)
                } else {
                    // Send Like Request to Target User
                    val currentUserDoc = firestore.collection("users").document(effectiveUid).get().await()
                    val currentUser = currentUserDoc.toObject(User::class.java)

                    val likeReq = LikeRequest(
                        senderId = effectiveUid,
                        receiverId = targetUserId,
                        senderName = currentUser?.name ?: "Someone",
                        senderPhoto = currentUser?.photos?.firstOrNull() ?: "",
                        status = "PENDING",
                        timestamp = System.currentTimeMillis()
                    )

                    firestore.collection("requests")
                        .document(targetUserId)
                        .collection("incomingLikes")
                        .document(effectiveUid)
                        .set(likeReq)
                        .await()

                    return Result.success(false)
                }
            }

            Result.success(false)
        } catch (e: Exception) {
            val isDemoMatch = isLiked && targetUserId.startsWith("bot_")
            Result.success(isDemoMatch)
        }
    }

    override suspend fun acceptLikeRequest(currentUserId: String, senderId: String): Result<Boolean> {
        return try {
            val firestore = firestoreProvider()
            if (firestore == null || currentUserId.isBlank()) return Result.success(true)

            // Create Match
            val matchId = UUID.randomUUID().toString()
            val matchData = Match(
                matchId = matchId,
                users = listOf(currentUserId, senderId),
                createdAt = System.currentTimeMillis()
            )

            firestore.collection("matches")
                .document(matchId)
                .set(matchData)
                .await()

            // Delete incoming request
            firestore.collection("requests")
                .document(currentUserId)
                .collection("incomingLikes")
                .document(senderId)
                .delete()
                .await()

            Result.success(true)
        } catch (e: Exception) {
            Result.success(true)
        }
    }

    override suspend fun declineLikeRequest(currentUserId: String, senderId: String): Result<Unit> {
        return try {
            val firestore = firestoreProvider() ?: return Result.success(Unit)

            firestore.collection("requests")
                .document(currentUserId)
                .collection("incomingLikes")
                .document(senderId)
                .delete()
                .await()

            Result.success(Unit)
        } catch (e: Exception) {
            Result.success(Unit)
        }
    }
}
