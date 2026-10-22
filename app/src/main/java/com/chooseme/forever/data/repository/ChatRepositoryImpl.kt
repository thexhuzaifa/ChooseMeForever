package com.chooseme.forever.data.repository

import com.chooseme.forever.data.model.ChatConversation
import com.chooseme.forever.data.model.ChatMessage
import com.chooseme.forever.data.model.User
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.tasks.await
import java.util.UUID

class ChatRepositoryImpl(
    private val firestoreProvider: () -> FirebaseFirestore? = {
        try { FirebaseFirestore.getInstance() } catch (e: Exception) { null }
    }
) : ChatRepository {

    override fun getActiveConversations(currentUserId: String): Flow<List<ChatConversation>> = callbackFlow {
        val firestore = firestoreProvider()
        if (firestore == null || currentUserId.isBlank()) {
            trySend(emptyList())
            close()
            return@callbackFlow
        }

        try {
            firestore.collection("matches")
                .whereArrayContains("users", currentUserId)
                .addSnapshotListener { snapshot, error ->
                    if (error != null || snapshot == null || snapshot.isEmpty) {
                        trySend(emptyList())
                        return@addSnapshotListener
                    }

                    val conversations = mutableListOf<ChatConversation>()
                    for (doc in snapshot.documents) {
                        val users = doc.get("users") as? List<*> ?: continue
                        val partnerId = users.firstOrNull { it != currentUserId } as? String ?: continue
                        val lastMsg = doc.getString("lastMessage") ?: "Say hi!"
                        val lastTime = doc.getLong("lastMessageTimestamp") ?: System.currentTimeMillis()

                        conversations.add(
                            ChatConversation(
                                matchId = doc.id,
                                otherUser = User(
                                    userId = partnerId,
                                    name = if (partnerId.startsWith("bot_") || partnerId.startsWith("user_")) {
                                        partnerId.removePrefix("bot_").removePrefix("user_").replaceFirstChar { it.uppercase() }
                                    } else "Match Partner"
                                ),
                                lastMessage = lastMsg,
                                lastMessageTimestamp = lastTime
                            )
                        )
                    }

                    trySend(conversations)
                }
        } catch (e: Exception) {
            trySend(emptyList())
        }

        awaitClose { }
    }.catch {
        emit(emptyList())
    }

    override fun getMessages(matchId: String): Flow<List<ChatMessage>> = callbackFlow {
        val firestore = firestoreProvider()
        if (firestore == null || matchId.isBlank()) {
            trySend(emptyList())
            close()
            return@callbackFlow
        }

        try {
            firestore.collection("matches")
                .document(matchId)
                .collection("messages")
                .orderBy("timestamp", Query.Direction.ASCENDING)
                .addSnapshotListener { snapshot, error ->
                    if (error != null || snapshot == null) {
                        trySend(emptyList())
                        return@addSnapshotListener
                    }

                    val messages = snapshot.documents.mapNotNull { it.toObject(ChatMessage::class.java) }
                    trySend(messages)
                }
        } catch (e: Exception) {
            trySend(emptyList())
        }

        awaitClose { }
    }.catch {
        emit(emptyList())
    }

    override suspend fun sendMessage(
        matchId: String,
        senderId: String,
        receiverId: String,
        text: String
    ): Result<Unit> {
        return try {
            val firestore = firestoreProvider() ?: return Result.success(Unit)

            val msgId = UUID.randomUUID().toString()
            val timestamp = System.currentTimeMillis()

            val chatMsg = ChatMessage(
                messageId = msgId,
                senderId = senderId,
                receiverId = receiverId,
                text = text,
                timestamp = timestamp
            )

            val matchRef = firestore.collection("matches").document(matchId)
            val msgRef = matchRef.collection("messages").document(msgId)

            firestore.runBatch { batch ->
                batch.set(msgRef, chatMsg)
                batch.update(
                    matchRef,
                    mapOf(
                        "lastMessage" to text,
                        "lastMessageTimestamp" to timestamp
                    )
                )
            }.await()

            Result.success(Unit)
        } catch (e: Exception) {
            Result.success(Unit)
        }
    }
}
