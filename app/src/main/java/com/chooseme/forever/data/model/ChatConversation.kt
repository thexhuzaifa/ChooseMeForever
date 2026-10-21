package com.chooseme.forever.data.model

data class ChatConversation(
    val matchId: String = "",
    val otherUser: User = User(),
    val lastMessage: String = "",
    val lastMessageTimestamp: Long = System.currentTimeMillis(),
    val unreadCount: Int = 0
)
