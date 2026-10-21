package com.chooseme.forever.ui.discover

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.chooseme.forever.data.model.User
import com.chooseme.forever.data.repository.DiscoverRepository
import com.chooseme.forever.data.repository.DiscoverRepositoryImpl
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class MatchDialogData(
    val currentUser: User,
    val matchedUser: User
)

data class DiscoverUiState(
    val profiles: List<User> = emptyList(),
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val matchEvent: MatchDialogData? = null
) {
    val topProfile: User?
        get() = profiles.firstOrNull()

    val backgroundProfile: User?
        get() = profiles.getOrNull(1)
}

class DiscoverViewModel(
    private val repository: DiscoverRepository = DiscoverRepositoryImpl(),
    private val authProvider: () -> FirebaseAuth? = {
        try { FirebaseAuth.getInstance() } catch (e: Exception) { null }
    }
) : ViewModel() {

    private val _uiState = MutableStateFlow(DiscoverUiState())
    val uiState: StateFlow<DiscoverUiState> = _uiState.asStateFlow()

    private val auth: FirebaseAuth?
        get() = authProvider()

    val currentUserId: String
        get() = auth?.currentUser?.uid ?: "guest_user"

    init {
        loadDiscoveryProfiles()
    }

    fun loadDiscoveryProfiles() {
        _uiState.update { it.copy(isLoading = true, errorMessage = null) }

        viewModelScope.launch {
            try {
                repository.getDiscoveryProfiles(currentUserId)
                    .catch { emit(emptyList()) }
                    .collect { fetchedProfiles ->
                        _uiState.update {
                            it.copy(
                                profiles = fetchedProfiles,
                                isLoading = false
                            )
                        }
                    }
            } catch (e: Throwable) {
                _uiState.update { it.copy(isLoading = false) }
            }
        }
    }

    fun swipeRight(targetUser: User) {
        handleSwipe(targetUser, isLiked = true)
    }

    fun swipeLeft(targetUser: User) {
        handleSwipe(targetUser, isLiked = false)
    }

    private fun handleSwipe(targetUser: User, isLiked: Boolean) {
        _uiState.update { state ->
            state.copy(profiles = state.profiles.filter { it.userId != targetUser.userId })
        }

        viewModelScope.launch {
            try {
                val result = repository.recordSwipe(currentUserId, targetUser.userId, isLiked)
                result.onSuccess { isMatch ->
                    if (isMatch) {
                        val currentUser = User(
                            userId = currentUserId,
                            name = auth?.currentUser?.displayName ?: "Tomas"
                        )
                        _uiState.update {
                            it.copy(matchEvent = MatchDialogData(currentUser, targetUser))
                        }
                    }
                }
            } catch (e: Throwable) {
                e.printStackTrace()
            }
        }
    }

    fun dismissMatchDialog() {
        _uiState.update { it.copy(matchEvent = null) }
    }
}
