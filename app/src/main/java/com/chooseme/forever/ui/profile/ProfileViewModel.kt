package com.chooseme.forever.ui.profile

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.chooseme.forever.data.model.User
import com.chooseme.forever.data.repository.ProfileRepository
import com.chooseme.forever.data.repository.ProfileRepositoryImpl
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ProfileUiState(
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val user: User? = null,
    val errorMessage: String? = null,
    val isLoggedOut: Boolean = false,
    val showLogoutDialog: Boolean = false
)

class ProfileViewModel(
    private val repository: ProfileRepository = ProfileRepositoryImpl(),
    private val authProvider: () -> FirebaseAuth? = {
        try { FirebaseAuth.getInstance() } catch (e: Exception) { null }
    }
) : ViewModel() {

    private val _uiState = MutableStateFlow(ProfileUiState())
    val uiState: StateFlow<ProfileUiState> = _uiState.asStateFlow()

    private val auth: FirebaseAuth?
        get() = authProvider()

    val currentUserId: String
        get() = auth?.currentUser?.uid ?: ""

    init {
        loadUserProfile()
    }

    fun loadUserProfile() {
        val uid = currentUserId
        if (uid.isBlank()) {
            val demoUser = User(
                userId = "demo_user",
                name = "Tomas",
                age = 25,
                city = "Newyork",
                bio = "Coffee lover, weekend traveller. Looking for someone to explore new places with.",
                interests = listOf("Music", "Travel", "Books", "Coffee"),
                photos = emptyList(),
                isPremium = false
            )
            _uiState.update { it.copy(user = demoUser, isLoading = false) }
            return
        }

        _uiState.update { it.copy(isLoading = true, errorMessage = null) }

        viewModelScope.launch {
            try {
                repository.getUserProfileFlow(uid)
                    .catch { emit(null) }
                    .collect { fetchedUser ->
                        val user = fetchedUser ?: User(
                            userId = uid,
                            name = auth?.currentUser?.displayName ?: "Tomas",
                            age = 25,
                            city = "Newyork",
                            bio = "Coffee lover, weekend traveller. Looking for someone to explore new places with.",
                            interests = listOf("Music", "Travel", "Books", "Coffee")
                        )
                        _uiState.update { it.copy(user = user, isLoading = false) }
                    }
            } catch (e: Throwable) {
                _uiState.update { it.copy(isLoading = false) }
            }
        }
    }

    fun updateProfile(
        name: String,
        age: Int,
        city: String,
        bio: String,
        interests: List<String>,
        onSuccess: () -> Unit
    ) {
        val currentUser = _uiState.value.user ?: return
        val updatedUser = currentUser.copy(
            name = name,
            age = age,
            city = city,
            bio = bio,
            interests = interests
        )

        _uiState.update { it.copy(isSaving = true, errorMessage = null) }

        viewModelScope.launch {
            try {
                val result = repository.saveUserProfile(updatedUser)
                result.onSuccess {
                    _uiState.update { it.copy(isSaving = false, user = updatedUser) }
                    onSuccess()
                }.onFailure { error ->
                    _uiState.update {
                        it.copy(
                            isSaving = false,
                            errorMessage = error.localizedMessage ?: "Failed to update profile"
                        )
                    }
                }
            } catch (e: Throwable) {
                _uiState.update {
                    it.copy(
                        isSaving = false,
                        errorMessage = e.localizedMessage ?: "An error occurred"
                    )
                }
            }
        }
    }

    fun uploadAndAddPhoto(uri: Uri) {
        val uid = currentUserId.ifBlank { "demo_user" }
        _uiState.update { it.copy(isSaving = true, errorMessage = null) }

        viewModelScope.launch {
            try {
                val uploadResult = repository.uploadUserImage(uid, uri)
                uploadResult.onSuccess { downloadUrl ->
                    val currentUser = _uiState.value.user ?: User(userId = uid)
                    val updatedPhotos = currentUser.photos + downloadUrl
                    val updatedUser = currentUser.copy(photos = updatedPhotos)

                    repository.saveUserProfile(updatedUser)
                    _uiState.update { it.copy(isSaving = false, user = updatedUser) }
                }.onFailure { error ->
                    _uiState.update {
                        it.copy(
                            isSaving = false,
                            errorMessage = error.localizedMessage ?: "Photo upload failed"
                        )
                    }
                }
            } catch (e: Throwable) {
                _uiState.update {
                    it.copy(
                        isSaving = false,
                        errorMessage = e.localizedMessage ?: "Upload error"
                    )
                }
            }
        }
    }

    fun showLogoutConfirmation(show: Boolean) {
        _uiState.update { it.copy(showLogoutDialog = show) }
    }

    fun logout(onLoggedOut: () -> Unit) {
        try {
            auth?.signOut()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        _uiState.update { it.copy(isLoggedOut = true, showLogoutDialog = false) }
        onLoggedOut()
    }
}
