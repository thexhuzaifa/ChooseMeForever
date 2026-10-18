package com.chooseme.forever.ui.profile

import android.net.Uri
import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class CreateProfileUiState(
    val name: String = "",
    val age: String = "",
    val bio: String = "",
    val photoUri: Uri? = null,
    val selectedInterests: Set<String> = setOf("Music", "Travel", "Books"),
    val availableInterests: List<String> = listOf("Music", "Travel", "Movies", "Cooking", "Fitness", "Books"),
    val isLoading: Boolean = false,
    val isProfileSaved: Boolean = false
) {
    val isContinueEnabled: Boolean
        get() = name.isNotBlank() && age.isNotBlank() && (age.toIntOrNull() ?: 0) in 18..120
}

class CreateProfileViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(CreateProfileUiState())
    val uiState: StateFlow<CreateProfileUiState> = _uiState.asStateFlow()

    fun onNameChanged(name: String) {
        _uiState.update { it.copy(name = name) }
    }

    fun onAgeChanged(age: String) {
        if (age.isEmpty() || age.all { it.isDigit() }) {
            _uiState.update { it.copy(age = age) }
        }
    }

    fun onBioChanged(bio: String) {
        _uiState.update { it.copy(bio = bio) }
    }

    fun onPhotoSelected(uri: Uri?) {
        _uiState.update { it.copy(photoUri = uri) }
    }

    fun toggleInterest(interest: String) {
        _uiState.update { state ->
            val updated = if (state.selectedInterests.contains(interest)) {
                state.selectedInterests - interest
            } else {
                state.selectedInterests + interest
            }
            state.copy(selectedInterests = updated)
        }
    }

    fun onContinueClicked(onSuccess: () -> Unit) {
        _uiState.update { it.copy(isProfileSaved = true) }
        onSuccess()
    }
}
