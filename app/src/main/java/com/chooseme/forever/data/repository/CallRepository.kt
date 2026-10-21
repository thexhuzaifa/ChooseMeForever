package com.chooseme.forever.data.repository

import com.chooseme.forever.data.model.CallSession
import com.chooseme.forever.data.model.User
import kotlinx.coroutines.flow.Flow

interface CallRepository {
    fun startCall(callerUser: User, receiverUser: User): Flow<CallSession>
    fun listenForIncomingCalls(currentUserId: String): Flow<CallSession?>
    suspend fun acceptCall(callId: String): Result<Unit>
    suspend fun endCall(callId: String): Result<Unit>
    suspend fun rejectCall(callId: String): Result<Unit>
}
