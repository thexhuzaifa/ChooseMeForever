package com.chooseme.forever.data.repository

import com.chooseme.forever.data.model.LikeRequest
import com.chooseme.forever.data.model.User
import kotlinx.coroutines.flow.Flow

interface DiscoverRepository {
    fun getDiscoveryProfiles(currentUserId: String): Flow<List<User>>
    fun getIncomingLikeRequests(currentUserId: String): Flow<List<LikeRequest>>
    suspend fun recordSwipe(currentUserId: String, targetUserId: String, isLiked: Boolean): Result<Boolean>
    suspend fun acceptLikeRequest(currentUserId: String, senderId: String): Result<Boolean>
    suspend fun declineLikeRequest(currentUserId: String, senderId: String): Result<Unit>
}
