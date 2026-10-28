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

    override fun getDiscoveryProfiles(currentUserId: String): Flow<List<User>> = callbackFlow {
        val firestore = firestoreProvider()
        val effectiveUid = currentUserId.ifBlank { "guest_user" }

        if (firestore == null) {
            trySend(emptyList())
            close()
            return@callbackFlow
        }

        try {
            // Fetch swiped user IDs for current user
            firestore.collection("likes")
                .document(effectiveUid)
                .collection("userLikes")
                .get()
                .addOnSuccessListener { swipedSnapshots ->
                    val swipedUserIds = swipedSnapshots.documents.map { it.id }.toSet()

                    // Listen to real users in users collection
                    firestore.collection("users")
                        .addSnapshotListener { usersSnapshot, error ->
                            if (error != null || usersSnapshot == null) {
                                trySend(emptyList())
                                return@addSnapshotListener
                            }

                            val realProfiles = usersSnapshot.documents
                                .mapNotNull { doc ->
                                    val user = doc.toObject(User::class.java)
                                    val uid = user?.userId?.ifBlank { doc.id } ?: doc.id
                                    if (user != null) {
                                        user.copy(userId = uid)
                                    } else null
                                }
                                .filter { candidate ->
                                    candidate.userId != effectiveUid &&
                                            !swipedUserIds.contains(candidate.userId) &&
                                            candidate.name.isNotBlank()
                                }

                            trySend(realProfiles)
                        }
                }
                .addOnFailureListener {
                    trySend(emptyList())
                }
        } catch (e: Exception) {
            trySend(emptyList())
        }

        awaitClose { }
    }.catch {
        emit(emptyList())
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
                return Result.success(false)
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

                val isMutual = incomingRequestDoc.exists() || (targetSwipeDoc.exists() && targetSwipeDoc.getBoolean("isLiked") == true)

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
            Result.success(false)
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
