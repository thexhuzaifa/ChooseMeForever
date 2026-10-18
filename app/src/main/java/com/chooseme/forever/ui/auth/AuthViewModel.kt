package com.chooseme.forever.ui.auth

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AuthUiState(
    val email: String = "",
    val password: String = "",
    val confirmPassword: String = "",
    val isSignUpMode: Boolean = true,
    val isLoading: Boolean = false,
    val isPasswordVisible: Boolean = false,
    val errorMessage: String? = null,
    val isAuthenticated: Boolean = false
) {
    val isSubmitEnabled: Boolean
        get() = if (isSignUpMode) {
            email.isNotBlank() && password.length >= 6 && password == confirmPassword
        } else {
            email.isNotBlank() && password.length >= 6
        }
}

class AuthViewModel(
    private val authProvider: () -> FirebaseAuth? = {
        try { FirebaseAuth.getInstance() } catch (e: Exception) { null }
    }
) : ViewModel() {

    private val _uiState = MutableStateFlow(AuthUiState())
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

    private val auth: FirebaseAuth?
        get() = authProvider()

    // Web Client ID from google-services.json
    private val webClientId = "314094702947-40htcusbn1lo8bfc1kkh16pgb8844152.apps.googleusercontent.com"

    fun setInitialMode(isSignUp: Boolean) {
        _uiState.update { it.copy(isSignUpMode = isSignUp, errorMessage = null) }
    }

    fun toggleMode() {
        _uiState.update {
            it.copy(
                isSignUpMode = !it.isSignUpMode,
                errorMessage = null,
                confirmPassword = ""
            )
        }
    }

    fun onEmailChanged(email: String) {
        _uiState.update { it.copy(email = email.trim(), errorMessage = null) }
    }

    fun onPasswordChanged(password: String) {
        _uiState.update { it.copy(password = password, errorMessage = null) }
    }

    fun onConfirmPasswordChanged(password: String) {
        _uiState.update { it.copy(confirmPassword = password, errorMessage = null) }
    }

    fun togglePasswordVisibility() {
        _uiState.update { it.copy(isPasswordVisible = !it.isPasswordVisible) }
    }

    fun onSubmit(onSuccess: () -> Unit) {
        val state = _uiState.value
        if (!state.isSubmitEnabled || state.isLoading) return

        val firebaseAuth = auth
        if (firebaseAuth == null) {
            _uiState.update { it.copy(errorMessage = "Firebase Auth service unavailable") }
            return
        }

        _uiState.update { it.copy(isLoading = true, errorMessage = null) }

        viewModelScope.launch {
            try {
                if (state.isSignUpMode) {
                    firebaseAuth.createUserWithEmailAndPassword(state.email, state.password)
                        .addOnCompleteListener { task ->
                            if (task.isSuccessful) {
                                _uiState.update { it.copy(isLoading = false, isAuthenticated = true) }
                                onSuccess()
                            } else {
                                _uiState.update {
                                    it.copy(
                                        isLoading = false,
                                        errorMessage = task.exception?.localizedMessage ?: "Sign up failed"
                                    )
                                }
                            }
                        }
                } else {
                    firebaseAuth.signInWithEmailAndPassword(state.email, state.password)
                        .addOnCompleteListener { task ->
                            if (task.isSuccessful) {
                                _uiState.update { it.copy(isLoading = false, isAuthenticated = true) }
                                onSuccess()
                            } else {
                                _uiState.update {
                                    it.copy(
                                        isLoading = false,
                                        errorMessage = task.exception?.localizedMessage ?: "Authentication failed"
                                    )
                                }
                            }
                        }
                }
            } catch (e: Throwable) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = e.localizedMessage ?: "An error occurred"
                    )
                }
            }
        }
    }

    fun onGoogleSignInClicked(context: Context, onSuccess: () -> Unit) {
        _uiState.update { it.copy(isLoading = true, errorMessage = null) }

        val activity = context.findActivity()
        if (activity == null) {
            _uiState.update {
                it.copy(
                    isLoading = false,
                    errorMessage = "Google Sign-In requires an Activity context"
                )
            }
            return
        }

        viewModelScope.launch {
            try {
                val googleIdOption = GetGoogleIdOption.Builder()
                    .setFilterByAuthorizedAccounts(false)
                    .setServerClientId(webClientId)
                    .setAutoSelectEnabled(false)
                    .build()

                val request = GetCredentialRequest.Builder()
                    .addCredentialOption(googleIdOption)
                    .build()

                val credentialManager = CredentialManager.create(activity)
                val result = credentialManager.getCredential(activity, request)

                val credential = result.credential
                val idToken = try {
                    if (credential is CustomCredential && credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                        GoogleIdTokenCredential.createFrom(credential.data).idToken
                    } else {
                        null
                    }
                } catch (e: Throwable) {
                    null
                }

                if (!idToken.isNullOrBlank()) {
                    val firebaseCredential = GoogleAuthProvider.getCredential(idToken, null)
                    val firebaseAuth = auth
                    if (firebaseAuth != null) {
                        firebaseAuth.signInWithCredential(firebaseCredential)
                            .addOnCompleteListener { task ->
                                if (task.isSuccessful) {
                                    _uiState.update { it.copy(isLoading = false, isAuthenticated = true) }
                                    onSuccess()
                                } else {
                                    _uiState.update {
                                        it.copy(
                                            isLoading = false,
                                            errorMessage = task.exception?.localizedMessage ?: "Google sign-in with Firebase failed"
                                        )
                                    }
                                }
                            }
                    } else {
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                errorMessage = "Firebase Auth instance unavailable"
                            )
                        }
                    }
                } else {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = "Could not retrieve Google ID Token"
                        )
                    }
                }
            } catch (e: GetCredentialCancellationException) {
                _uiState.update { it.copy(isLoading = false, errorMessage = null) }
            } catch (e: Throwable) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = e.localizedMessage ?: "Google Sign-In failed"
                    )
                }
            }
        }
    }

    private tailrec fun Context.findActivity(): Activity? = when (this) {
        is Activity -> this
        is ContextWrapper -> baseContext.findActivity()
        else -> null
    }
}
