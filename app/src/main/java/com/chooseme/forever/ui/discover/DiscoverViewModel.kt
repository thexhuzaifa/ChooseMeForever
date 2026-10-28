package com.chooseme.forever.ui.discover

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.chooseme.forever.data.model.LikeRequest
import com.chooseme.forever.data.model.User
import com.chooseme.forever.data.repository.DiscoverRepository
import com.chooseme.forever.data.repository.DiscoverRepositoryImpl
import com.chooseme.forever.data.repository.ProfileRepository
import com.chooseme.forever.data.repository.ProfileRepositoryImpl
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
    val matchEvent: MatchDialogData? = null,
    val incomingLikeRequest: LikeRequest? = null
) {
    val topProfile: User?
        get() = profiles.firstOrNull()

    val backgroundProfile: User?
        get() = profiles.getOrNull(1)
}

class DiscoverViewModel(
    private val repository: DiscoverRepository = DiscoverRepositoryImpl(),
    private val profileRepository: ProfileRepository = ProfileRepositoryImpl(),
    private val authProvider: () -> FirebaseAuth? = {
        try { FirebaseAuth.getInstance() } catch (e: Exception) { null }
    }
) : ViewModel() {

    private val _uiState = MutableStateFlow(DiscoverUiState())
    val uiState: StateFlow<DiscoverUiState> = _uiState.asStateFlow()

    private val auth: FirebaseAuth?
        get() = authProvider()

    val currentUserId: String
        get() = auth?.currentUser?.uid ?: ""

    init {
        loadDiscoveryProfiles()
        listenForIncomingRequests()
    }

    fun loadDiscoveryProfiles() {
        if (currentUserId.isBlank()) return
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

    private fun listenForIncomingRequests() {
        if (currentUserId.isBlank()) return

        viewModelScope.launch {
            try {
                repository.getIncomingLikeRequests(currentUserId)
                    .catch { emit(emptyList()) }
                    .collect { requests ->
                        val latestRequest = requests.firstOrNull()
                        _uiState.update { it.copy(incomingLikeRequest = latestRequest) }
                    }
            } catch (e: Throwable) {
                e.printStackTrace()
            }
        }
    }

    fun acceptIncomingRequest(request: LikeRequest) {
        viewModelScope.launch {
            val senderProfileResult = profileRepository.getUserProfile(request.senderId)
            val senderUser = senderProfileResult.getOrNull() ?: User(
                userId = request.senderId,
                name = request.senderName,
                photos = if (request.senderPhoto.isNotBlank()) listOf(request.senderPhoto) else emptyList()
            )

            val myProfileResult = profileRepository.getUserProfile(currentUserId)
            val currentUser = myProfileResult.getOrNull() ?: User(userId = currentUserId, name = auth?.currentUser?.displayName ?: "Me")

            val result = repository.acceptLikeRequest(currentUserId, request.senderId)
            result.onSuccess {
                _uiState.update {
                    it.copy(
                        incomingLikeRequest = null,
                        matchEvent = MatchDialogData(currentUser, senderUser)
                    )
                }
            }
        }
    }

    fun declineIncomingRequest(request: LikeRequest) {
        viewModelScope.launch {
            repository.declineLikeRequest(currentUserId, request.senderId)
            _uiState.update { it.copy(incomingLikeRequest = null) }
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
                            name = auth?.currentUser?.displayName ?: "Me"
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
