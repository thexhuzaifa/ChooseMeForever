package com.chooseme.forever.data.model

data class Match(
    val matchId: String = "",
    val users: List<String> = emptyList(),
    val createdAt: Long = System.currentTimeMillis(),
    val lastMessage: String? = null,
    val lastMessageTimestamp: Long? = null
)
