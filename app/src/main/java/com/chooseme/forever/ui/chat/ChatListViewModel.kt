package com.chooseme.forever.ui.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.chooseme.forever.data.model.ChatConversation
import com.chooseme.forever.data.repository.ChatRepository
import com.chooseme.forever.data.repository.ChatRepositoryImpl
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ChatListUiState(
    val conversations: List<ChatConversation> = emptyList(),
    val searchQuery: String = "",
    val isLoading: Boolean = false,
    val errorMessage: String? = null
) {
    val filteredConversations: List<ChatConversation>
        get() = if (searchQuery.isBlank()) {
            conversations
        } else {
            conversations.filter {
                it.otherUser.name.contains(searchQuery, ignoreCase = true) ||
                        it.lastMessage.contains(searchQuery, ignoreCase = true)
            }
        }
}

class ChatListViewModel(
    private val repository: ChatRepository = ChatRepositoryImpl(),
    private val authProvider: () -> FirebaseAuth? = {
        try { FirebaseAuth.getInstance() } catch (e: Exception) { null }
    }
) : ViewModel() {

    private val _uiState = MutableStateFlow(ChatListUiState())
    val uiState: StateFlow<ChatListUiState> = _uiState.asStateFlow()

    private val auth: FirebaseAuth?
        get() = authProvider()

    val currentUserId: String
        get() = auth?.currentUser?.uid ?: "me"

    init {
        loadConversations()
    }

    fun loadConversations() {
        _uiState.update { it.copy(isLoading = true, errorMessage = null) }

        viewModelScope.launch {
            try {
                repository.getActiveConversations(currentUserId)
                    .catch { emit(emptyList()) }
                    .collect { list ->
                        _uiState.update {
                            it.copy(
                                conversations = list,
                                isLoading = false
                            )
                        }
                    }
            } catch (e: Throwable) {
                _uiState.update { it.copy(isLoading = false) }
            }
        }
    }

    fun onSearchQueryChanged(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
    }
}
