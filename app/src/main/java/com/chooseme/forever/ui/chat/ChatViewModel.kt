package com.chooseme.forever.ui.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.chooseme.forever.data.model.ChatMessage
import com.chooseme.forever.data.model.User
import com.chooseme.forever.data.repository.ChatRepository
import com.chooseme.forever.data.repository.ChatRepositoryImpl
import com.chooseme.forever.data.repository.ProfileRepository
import com.chooseme.forever.data.repository.ProfileRepositoryImpl
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ChatUiState(
    val messages: List<ChatMessage> = emptyList(),
    val partnerUser: User? = null,
    val inputText: String = "",
    val isLoading: Boolean = false,
    val callAlertMessage: String? = null
)

class ChatViewModel(
    private val chatRepository: ChatRepository = ChatRepositoryImpl(),
    private val profileRepository: ProfileRepository = ProfileRepositoryImpl(),
    private val authProvider: () -> FirebaseAuth? = {
        try { FirebaseAuth.getInstance() } catch (e: Exception) { null }
    }
) : ViewModel() {

    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    private val auth: FirebaseAuth?
        get() = authProvider()

    val currentUserId: String
        get() = auth?.currentUser?.uid ?: "me"

    private var currentMatchId: String = ""
    private var currentPartnerId: String = ""

    fun initializeChat(matchId: String, partnerUserId: String) {
        currentMatchId = matchId
        currentPartnerId = partnerUserId

        loadPartnerDetails(partnerUserId)
        loadMessages(matchId)
    }

    private fun loadPartnerDetails(partnerUserId: String) {
        viewModelScope.launch {
            val result = profileRepository.getUserProfile(partnerUserId)
            result.onSuccess { fetchedUser ->
                val partner = fetchedUser ?: User(
                    userId = partnerUserId,
                    name = if (partnerUserId.startsWith("user_")) partnerUserId.removePrefix("user_").replaceFirstChar { it.uppercase() } else "Maria",
                    city = "DC"
                )
                _uiState.update { it.copy(partnerUser = partner) }
            }.onFailure {
                val partner = User(
                    userId = partnerUserId,
                    name = "Maria",
                    city = "DC"
                )
                _uiState.update { it.copy(partnerUser = partner) }
            }
        }
    }

    private fun loadMessages(matchId: String) {
        _uiState.update { it.copy(isLoading = true) }

        viewModelScope.launch {
            try {
                chatRepository.getMessages(matchId)
                    .catch { emit(emptyList()) }
                    .collect { msgs ->
                        _uiState.update {
                            it.copy(messages = msgs, isLoading = false)
                        }
                    }
            } catch (e: Throwable) {
                _uiState.update { it.copy(isLoading = false) }
            }
        }
    }

    fun onInputTextChanged(text: String) {
        _uiState.update { it.copy(inputText = text) }
    }

    fun sendMessage() {
        val text = _uiState.value.inputText.trim()
        if (text.isBlank() || currentMatchId.isBlank()) return

        val newMsg = ChatMessage(
            messageId = "temp_${System.currentTimeMillis()}",
            senderId = currentUserId,
            receiverId = currentPartnerId,
            text = text,
            timestamp = System.currentTimeMillis()
        )

        _uiState.update {
            it.copy(
                messages = it.messages + newMsg,
                inputText = ""
            )
        }

        viewModelScope.launch {
            chatRepository.sendMessage(
                matchId = currentMatchId,
                senderId = currentUserId,
                receiverId = currentPartnerId,
                text = text
            )
        }
    }

    fun onCallButtonClicked(onStartCall: (String) -> Unit, onNavigateToPremium: () -> Unit) {
        viewModelScope.launch {
            val myProfileResult = profileRepository.getUserProfile(currentUserId)
            val isPremium = myProfileResult.getOrNull()?.isPremium ?: false

            if (isPremium) {
                onStartCall(currentPartnerId.ifBlank { "user_maria" })
            } else {
                onNavigateToPremium()
            }
        }
    }

    fun dismissCallAlert() {
        _uiState.update { it.copy(callAlertMessage = null) }
    }
}
