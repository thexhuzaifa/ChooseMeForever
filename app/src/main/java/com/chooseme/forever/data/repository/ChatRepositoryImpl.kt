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

    // Default Demo Conversations matching Figma Screen 4
    private val defaultConversations = listOf(
        ChatConversation(
            matchId = "match_maria",
            otherUser = User(
                userId = "user_maria",
                name = "Maria",
                age = 24,
                city = "DC",
                bio = "Coffee lover, weekend traveler.",
                interests = listOf("Music", "Travel", "Books")
            ),
            lastMessage = "Skardu. We should compare notes over coffee sometime.",
            lastMessageTimestamp = System.currentTimeMillis() - 1000 * 60 * 2,
            unreadCount = 1
        ),
        ChatConversation(
            matchId = "match_alex",
            otherUser = User(
                userId = "user_alex",
                name = "Alex",
                age = 25,
                city = "Lahore",
                bio = "Music & sports enthusiast."
            ),
            lastMessage = "Skardu. We should compare notes..",
            lastMessageTimestamp = System.currentTimeMillis() - 1000 * 60 * 15,
            unreadCount = 0
        ),
        ChatConversation(
            matchId = "match_sara",
            otherUser = User(
                userId = "user_sara",
                name = "Sara",
                age = 23,
                city = "Karachi",
                bio = "Foodie & designer."
            ),
            lastMessage = "Haha that sounds fun!",
            lastMessageTimestamp = System.currentTimeMillis() - 1000 * 60 * 60 * 24,
            unreadCount = 0
        ),
        ChatConversation(
            matchId = "match_hira",
            otherUser = User(
                userId = "user_hira",
                name = "Hira",
                age = 24,
                city = "Islamabad"
            ),
            lastMessage = "Say hi to Hira",
            lastMessageTimestamp = System.currentTimeMillis() - 1000 * 60 * 60 * 48,
            unreadCount = 0
        ),
        ChatConversation(
            matchId = "match_flora",
            otherUser = User(
                userId = "user_flora",
                name = "Flora",
                age = 22,
                city = "Peshawar"
            ),
            lastMessage = "Say hi to Zainab",
            lastMessageTimestamp = System.currentTimeMillis() - 1000 * 60 * 60 * 72,
            unreadCount = 0
        )
    )

    // Default Demo Messages matching Figma Screen 5
    private fun getDefaultMessages(matchId: String): List<ChatMessage> {
        return listOf(
            ChatMessage(
                messageId = "msg_1",
                senderId = "user_maria",
                receiverId = "me",
                text = "Hey! I saw you like travel too.",
                timestamp = System.currentTimeMillis() - 1000 * 60 * 10
            ),
            ChatMessage(
                messageId = "msg_2",
                senderId = "me",
                receiverId = "user_maria",
                text = "Hi Maria! Yes, I love it. Favourite place so far?",
                timestamp = System.currentTimeMillis() - 1000 * 60 * 8
            ),
            ChatMessage(
                messageId = "msg_3",
                senderId = "user_maria",
                receiverId = "me",
                text = "Hunza, hands down. You?",
                timestamp = System.currentTimeMillis() - 1000 * 60 * 5
            ),
            ChatMessage(
                messageId = "msg_4",
                senderId = "me",
                receiverId = "user_maria",
                text = "Skardu. We should compare notes over coffee sometime.",
                timestamp = System.currentTimeMillis() - 1000 * 60 * 2
            )
        )
    }

    override fun getActiveConversations(currentUserId: String): Flow<List<ChatConversation>> = callbackFlow {
        val firestore = firestoreProvider()
        val effectiveUid = currentUserId.ifBlank { "me" }

        if (firestore == null) {
            trySend(defaultConversations)
            close()
            return@callbackFlow
        }

        try {
            firestore.collection("matches")
                .whereArrayContains("users", effectiveUid)
                .addSnapshotListener { snapshot, error ->
                    if (error != null || snapshot == null || snapshot.isEmpty) {
                        trySend(defaultConversations)
                        return@addSnapshotListener
                    }

                    val conversations = snapshot.documents.mapNotNull { doc ->
                        val users = doc.get("users") as? List<*> ?: return@mapNotNull null
                        val partnerId = users.firstOrNull { it != effectiveUid } as? String ?: return@mapNotNull null
                        val lastMsg = doc.getString("lastMessage") ?: "Say hi!"
                        val lastTime = doc.getLong("lastMessageTimestamp") ?: System.currentTimeMillis()

                        ChatConversation(
                            matchId = doc.id,
                            otherUser = User(userId = partnerId, name = partnerId.replaceFirstChar { it.uppercase() }),
                            lastMessage = lastMsg,
                            lastMessageTimestamp = lastTime
                        )
                    }

                    if (conversations.isEmpty()) {
                        trySend(defaultConversations)
                    } else {
                        trySend(conversations)
                    }
                }
        } catch (e: Exception) {
            trySend(defaultConversations)
        }

        awaitClose { }
    }.catch {
        emit(defaultConversations)
    }

    override fun getMessages(matchId: String): Flow<List<ChatMessage>> = callbackFlow {
        val firestore = firestoreProvider()

        if (firestore == null || matchId.startsWith("match_")) {
            trySend(getDefaultMessages(matchId))
            close()
            return@callbackFlow
        }

        try {
            firestore.collection("matches")
                .document(matchId)
                .collection("messages")
                .orderBy("timestamp", Query.Direction.ASCENDING)
                .addSnapshotListener { snapshot, error ->
                    if (error != null || snapshot == null || snapshot.isEmpty) {
                        trySend(getDefaultMessages(matchId))
                        return@addSnapshotListener
                    }

                    val messages = snapshot.documents.mapNotNull { it.toObject(ChatMessage::class.java) }
                    trySend(messages)
                }
        } catch (e: Exception) {
            trySend(getDefaultMessages(matchId))
        }

        awaitClose { }
    }.catch {
        emit(getDefaultMessages(matchId))
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
