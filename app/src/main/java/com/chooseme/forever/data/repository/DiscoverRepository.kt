package com.chooseme.forever.data.repository

import com.chooseme.forever.data.model.User
import kotlinx.coroutines.flow.Flow

interface DiscoverRepository {
    fun getDiscoveryProfiles(currentUserId: String): Flow<List<User>>
    suspend fun recordSwipe(currentUserId: String, targetUserId: String, isLiked: Boolean): Result<Boolean>
}
