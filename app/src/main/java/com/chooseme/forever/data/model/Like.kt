package com.chooseme.forever.data.model

data class Like(
    val fromUserId: String = "",
    val toUserId: String = "",
    val isLiked: Boolean = false,
    val timestamp: Long = System.currentTimeMillis()
)
