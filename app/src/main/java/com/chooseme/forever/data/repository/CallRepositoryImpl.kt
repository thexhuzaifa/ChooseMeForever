package com.chooseme.forever.data.repository

import com.chooseme.forever.data.model.CallSession
import com.chooseme.forever.data.model.CallStatus
import com.chooseme.forever.data.model.User
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.tasks.await
import java.util.UUID

class CallRepositoryImpl(
    private val firestoreProvider: () -> FirebaseFirestore? = {
        try { FirebaseFirestore.getInstance() } catch (e: Exception) { null }
    }
) : CallRepository {

    override fun startCall(callerUser: User, receiverUser: User): Flow<CallSession> = callbackFlow {
        val firestore = firestoreProvider()
        val callId = UUID.randomUUID().toString()
        val initialCall = CallSession(
            callId = callId,
            callerId = callerUser.userId,
            callerName = callerUser.name.ifBlank { "Caller" },
            callerPhoto = callerUser.photos.firstOrNull() ?: "",
            receiverId = receiverUser.userId,
            channelName = "channel_$callId",
            status = CallStatus.RINGING,
            timestamp = System.currentTimeMillis()
        )

        if (firestore == null) {
            trySend(initialCall)
            awaitClose { }
            return@callbackFlow
        }

        try {
            firestore.collection("calls").document(callId).set(initialCall).await()

            val listener = firestore.collection("calls").document(callId)
                .addSnapshotListener { snapshot, error ->
                    if (error != null || snapshot == null) return@addSnapshotListener
                    val session = snapshot.toObject(CallSession::class.java)
                    if (session != null) {
                        trySend(session)
                    }
                }

            awaitClose { listener.remove() }
        } catch (e: Exception) {
            trySend(initialCall)
            awaitClose { }
        }
    }.catch {
        emit(CallSession(status = CallStatus.RINGING))
    }

    override fun listenForIncomingCalls(currentUserId: String): Flow<CallSession?> = callbackFlow {
        val firestore = firestoreProvider()
        if (firestore == null || currentUserId.isBlank()) {
            trySend(null)
            awaitClose { }
            return@callbackFlow
        }

        try {
            val listener = firestore.collection("calls")
                .whereEqualTo("receiverId", currentUserId)
                .whereEqualTo("status", "RINGING")
                .addSnapshotListener { snapshot, error ->
                    if (error != null || snapshot == null || snapshot.isEmpty) {
                        trySend(null)
                        return@addSnapshotListener
                    }

                    val incomingCall = snapshot.documents
                        .mapNotNull { it.toObject(CallSession::class.java) }
                        .maxByOrNull { it.timestamp }

                    trySend(incomingCall)
                }

            awaitClose { listener.remove() }
        } catch (e: Exception) {
            trySend(null)
            awaitClose { }
        }
    }.catch {
        emit(null)
    }

    override suspend fun acceptCall(callId: String): Result<Unit> {
        return try {
            val firestore = firestoreProvider() ?: return Result.success(Unit)
            firestore.collection("calls").document(callId)
                .update("status", CallStatus.CONNECTED.name)
                .await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.success(Unit)
        }
    }

    override suspend fun endCall(callId: String): Result<Unit> {
        return try {
            val firestore = firestoreProvider() ?: return Result.success(Unit)
            firestore.collection("calls").document(callId)
                .update("status", CallStatus.ENDED.name)
                .await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.success(Unit)
        }
    }

    override suspend fun rejectCall(callId: String): Result<Unit> {
        return try {
            val firestore = firestoreProvider() ?: return Result.success(Unit)
            firestore.collection("calls").document(callId)
                .update("status", CallStatus.REJECTED.name)
                .await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.success(Unit)
        }
    }
}
