package com.chooseme.forever.data.model

enum class CallStatus {
    IDLE,
    RINGING,
    CONNECTED,
    ENDED,
    REJECTED
}

data class CallSession(
    val callId: String = "",
    val callerId: String = "",
    val callerName: String = "",
    val callerPhoto: String = "",
    val receiverId: String = "",
    val channelName: String = "",
    val status: CallStatus = CallStatus.IDLE,
    val timestamp: Long = System.currentTimeMillis()
)
