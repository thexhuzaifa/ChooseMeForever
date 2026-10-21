package com.chooseme.forever.ui.call

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.chooseme.forever.data.model.CallSession
import com.chooseme.forever.data.model.CallStatus
import com.chooseme.forever.data.model.User
import com.chooseme.forever.data.repository.CallRepository
import com.chooseme.forever.data.repository.CallRepositoryImpl
import com.chooseme.forever.data.repository.ProfileRepository
import com.chooseme.forever.data.repository.ProfileRepositoryImpl
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class CallUiState(
    val callSession: CallSession? = null,
    val partnerUser: User? = null,
    val isMuted: Boolean = false,
    val isSpeakerOn: Boolean = true,
    val durationSeconds: Int = 0,
    val callStatusText: String = "Calling...",
    val isCallEnded: Boolean = false
) {
    val formattedDuration: String
        get() {
            val minutes = durationSeconds / 60
            val seconds = durationSeconds % 60
            return String.format("%02d:%02d", minutes, seconds)
        }
}

class CallViewModel(
    private val callRepository: CallRepository = CallRepositoryImpl(),
    private val profileRepository: ProfileRepository = ProfileRepositoryImpl(),
    private val authProvider: () -> FirebaseAuth? = {
        try { FirebaseAuth.getInstance() } catch (e: Exception) { null }
    }
) : ViewModel() {

    private val _uiState = MutableStateFlow(CallUiState())
    val uiState: StateFlow<CallUiState> = _uiState.asStateFlow()

    private var timerJob: Job? = null

    private val auth: FirebaseAuth?
        get() = authProvider()

    val currentUserId: String
        get() = auth?.currentUser?.uid ?: "me"

    fun startOutgoingCall(partnerUserId: String) {
        viewModelScope.launch {
            val myUser = profileRepository.getUserProfile(currentUserId).getOrNull() ?: User(userId = currentUserId, name = "Tomas")
            val partnerUser = profileRepository.getUserProfile(partnerUserId).getOrNull() ?: User(userId = partnerUserId, name = "Maria")

            _uiState.update { it.copy(partnerUser = partnerUser) }

            callRepository.startCall(myUser, partnerUser)
                .catch { }
                .collect { session ->
                    handleCallSessionUpdate(session)
                }
        }
    }

    fun initializeIncomingCall(callSession: CallSession) {
        _uiState.update {
            it.copy(
                callSession = callSession,
                partnerUser = User(userId = callSession.callerId, name = callSession.callerName),
                callStatusText = "is calling you..."
            )
        }
    }

    private fun handleCallSessionUpdate(session: CallSession) {
        _uiState.update { it.copy(callSession = session) }

        when (session.status) {
            CallStatus.RINGING -> {
                _uiState.update { it.copy(callStatusText = "Calling...") }
            }
            CallStatus.CONNECTED -> {
                _uiState.update { it.copy(callStatusText = _uiState.value.formattedDuration) }
                startCallTimer()
            }
            CallStatus.ENDED, CallStatus.REJECTED -> {
                stopCallTimer()
                _uiState.update { it.copy(isCallEnded = true, callStatusText = "Call Ended") }
            }
            else -> {}
        }
    }

    private fun startCallTimer() {
        if (timerJob != null) return
        timerJob = viewModelScope.launch {
            while (true) {
                delay(1000)
                _uiState.update {
                    val newSecs = it.durationSeconds + 1
                    val newDurationStr = String.format("%02d:%02d", newSecs / 60, newSecs % 60)
                    it.copy(durationSeconds = newSecs, callStatusText = newDurationStr)
                }
            }
        }
    }

    private fun stopCallTimer() {
        timerJob?.cancel()
        timerJob = null
    }

    fun toggleMute() {
        _uiState.update { it.copy(isMuted = !it.isMuted) }
    }

    fun toggleSpeaker() {
        _uiState.update { it.copy(isSpeakerOn = !it.isSpeakerOn) }
    }

    fun acceptCall(onConnected: () -> Unit) {
        val session = _uiState.value.callSession ?: return
        viewModelScope.launch {
            callRepository.acceptCall(session.callId)
            _uiState.update {
                it.copy(
                    callSession = session.copy(status = CallStatus.CONNECTED),
                    callStatusText = "00:00"
                )
            }
            startCallTimer()
            onConnected()
        }
    }

    fun endCall(onEnded: () -> Unit) {
        stopCallTimer()
        val session = _uiState.value.callSession
        viewModelScope.launch {
            if (session != null) {
                callRepository.endCall(session.callId)
            }
            _uiState.update { it.copy(isCallEnded = true) }
            onEnded()
        }
    }

    fun declineCall(onDeclined: () -> Unit) {
        stopCallTimer()
        val session = _uiState.value.callSession
        viewModelScope.launch {
            if (session != null) {
                callRepository.rejectCall(session.callId)
            }
            _uiState.update { it.copy(isCallEnded = true) }
            onDeclined()
        }
    }

    override fun onCleared() {
        super.onCleared()
        stopCallTimer()
    }
}
