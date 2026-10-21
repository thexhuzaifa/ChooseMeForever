package com.chooseme.forever.data.repository

import com.chooseme.forever.data.model.ChatConversation
import com.chooseme.forever.data.model.ChatMessage
import kotlinx.coroutines.flow.Flow

interface ChatRepository {
    fun getActiveConversations(currentUserId: String): Flow<List<ChatConversation>>
    fun getMessages(matchId: String): Flow<List<ChatMessage>>
    suspend fun sendMessage(matchId: String, senderId: String, receiverId: String, text: String): Result<Unit>
}
