package com.chooseme.forever.data.model

data class LikeRequest(
    val senderId: String = "",
    val receiverId: String = "",
    val senderName: String = "",
    val senderPhoto: String = "",
    val status: String = "PENDING",
    val timestamp: Long = System.currentTimeMillis()
)
