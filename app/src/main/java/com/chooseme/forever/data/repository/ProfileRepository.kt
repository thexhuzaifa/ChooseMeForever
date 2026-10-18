package com.chooseme.forever.data.repository

import android.net.Uri
import com.chooseme.forever.data.model.User
import kotlinx.coroutines.flow.Flow

interface ProfileRepository {
    fun getUserProfileFlow(userId: String): Flow<User?>
    suspend fun getUserProfile(userId: String): Result<User?>
    suspend fun saveUserProfile(user: User): Result<Unit>
    suspend fun uploadUserImage(userId: String, imageUri: Uri): Result<String>
}
