package com.chooseme.forever.ui.profile

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.chooseme.forever.data.model.User
import com.chooseme.forever.data.repository.ProfileRepository
import com.chooseme.forever.data.repository.ProfileRepositoryImpl
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class CreateProfileUiState(
    val name: String = "",
    val age: String = "",
    val bio: String = "",
    val city: String = "",
    val photoUri: Uri? = null,
    val selectedInterests: Set<String> = emptySet(),
    val availableInterests: List<String> = listOf("Music", "Travel", "Movies", "Cooking", "Fitness", "Books", "Photography", "Art"),
    val isLoading: Boolean = false,
    val isProfileSaved: Boolean = false,
    val ageError: String? = null
) {
    val ageInt: Int?
        get() = age.toIntOrNull()

    val isAgeValid: Boolean
        get() = ageInt != null && ageInt!! >= 18

    val isContinueEnabled: Boolean
        get() = name.isNotBlank() && isAgeValid && !isLoading
}

class CreateProfileViewModel(
    application: Application
) : AndroidViewModel(application) {

    private val repository: ProfileRepository = ProfileRepositoryImpl(context = application)
    private val auth: FirebaseAuth?
        get() = try { FirebaseAuth.getInstance() } catch (e: Exception) { null }

    private val _uiState = MutableStateFlow(CreateProfileUiState())
    val uiState: StateFlow<CreateProfileUiState> = _uiState.asStateFlow()

    val currentUserId: String
        get() = auth?.currentUser?.uid ?: ""

    fun onNameChanged(name: String) {
        _uiState.update { it.copy(name = name) }
    }

    fun onAgeChanged(age: String) {
        if (age.isEmpty() || age.all { it.isDigit() }) {
            val ageVal = age.toIntOrNull()
            val error = if (age.isNotBlank() && (ageVal == null || ageVal < 18)) "Must be at least 18 years old" else null
            _uiState.update { it.copy(age = age, ageError = error) }
        }
    }

    fun onCityChanged(city: String) {
        _uiState.update { it.copy(city = city) }
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

    fun addCustomInterest(customInterest: String) {
        val trimmed = customInterest.trim()
        if (trimmed.isBlank()) return

        _uiState.update { state ->
            val updatedAvailable = if (!state.availableInterests.contains(trimmed)) {
                state.availableInterests + trimmed
            } else {
                state.availableInterests
            }
            val updatedSelected = state.selectedInterests + trimmed
            state.copy(
                availableInterests = updatedAvailable,
                selectedInterests = updatedSelected
            )
        }
    }

    fun onContinueClicked(onSuccess: () -> Unit) {
        val state = _uiState.value
        if (!state.isContinueEnabled || state.isLoading) return

        _uiState.update { it.copy(isLoading = true) }

        viewModelScope.launch {
            val uid = currentUserId.ifBlank { "user_${System.currentTimeMillis()}" }

            var photoUrlList = emptyList<String>()
            state.photoUri?.let { uri ->
                val uploadResult = repository.uploadUserImage(uid, uri)
                photoUrlList = listOf(uploadResult.getOrDefault(uri.toString()))
            }

            val user = User(
                userId = uid,
                name = state.name.trim(),
                age = state.ageInt ?: 18,
                city = state.city.trim(),
                bio = state.bio.trim(),
                interests = state.selectedInterests.toList(),
                photos = photoUrlList,
                createdAt = System.currentTimeMillis()
            )

            repository.saveUserProfile(user)
            _uiState.update { it.copy(isLoading = false, isProfileSaved = true) }
            onSuccess()
        }
    }
}
