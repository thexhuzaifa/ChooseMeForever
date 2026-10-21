package com.chooseme.forever.data.remote.agora

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class AgoraManager(private val context: Context) {

    private val _isMuted = MutableStateFlow(false)
    val isMuted: StateFlow<Boolean> = _isMuted.asStateFlow()

    private val _isSpeakerOn = MutableStateFlow(true)
    val isSpeakerOn: StateFlow<Boolean> = _isSpeakerOn.asStateFlow()

    private val _isUserJoined = MutableStateFlow(false)
    val isUserJoined: StateFlow<Boolean> = _isUserJoined.asStateFlow()

    fun joinChannel(channelName: String, uid: Int = 0, token: String? = null) {
        _isUserJoined.value = true
    }

    fun leaveChannel() {
        _isUserJoined.value = false
    }

    fun toggleMute(): Boolean {
        _isMuted.value = !_isMuted.value
        return _isMuted.value
    }

    fun toggleSpeaker(): Boolean {
        _isSpeakerOn.value = !_isSpeakerOn.value
        return _isSpeakerOn.value
    }

    fun release() {
        leaveChannel()
    }
}
