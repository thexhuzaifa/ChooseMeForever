package com.chooseme.forever.ui.chat

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.chooseme.forever.data.model.ChatMessage
import com.chooseme.forever.data.model.User
import com.chooseme.forever.data.remote.notification.AppNotificationManager
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
    application: Application
) : AndroidViewModel(application) {

    private val chatRepository: ChatRepository = ChatRepositoryImpl()
    private val profileRepository: ProfileRepository = ProfileRepositoryImpl(context = application)
    private val auth: FirebaseAuth?
        get() = try { FirebaseAuth.getInstance() } catch (e: Exception) { null }

    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    private val notificationManager = try { AppNotificationManager(application) } catch (e: Exception) { null }

    val currentUserId: String
        get() = auth?.currentUser?.uid ?: ""

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
                val partner = fetchedUser ?: User(userId = partnerUserId, name = "Partner")
                _uiState.update { it.copy(partnerUser = partner) }
            }.onFailure {
                val partner = User(userId = partnerUserId, name = "Partner")
                _uiState.update { it.copy(partnerUser = partner) }
            }
        }
    }

    private fun loadMessages(matchId: String) {
        _uiState.update { it.copy(isLoading = true) }

        viewModelScope.launch {
            try {
                var initialLoad = true
                var previousMsgCount = 0
                chatRepository.getMessages(matchId)
                    .catch { emit(emptyList()) }
                    .collect { msgs ->
                        _uiState.update {
                            it.copy(messages = msgs, isLoading = false)
                        }

                        if (!initialLoad && msgs.size > previousMsgCount) {
                            val lastMsg = msgs.lastOrNull()
                            if (lastMsg != null && lastMsg.senderId != currentUserId && lastMsg.senderId != "me") {
                                try {
                                    val senderName = _uiState.value.partnerUser?.name ?: "Partner"
                                    notificationManager?.showNewMessageNotification(senderName, lastMsg.text)
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                }
                            }
                        }
                        initialLoad = false
                        previousMsgCount = msgs.size
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
                if (currentPartnerId.isNotBlank()) {
                    onStartCall(currentPartnerId)
                }
            } else {
                onNavigateToPremium()
            }
        }
    }

    fun dismissCallAlert() {
        _uiState.update { it.copy(callAlertMessage = null) }
    }
}
