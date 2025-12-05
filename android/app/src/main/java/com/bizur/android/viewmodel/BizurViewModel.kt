package com.bizur.android.viewmodel

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.bizur.android.call.CallSessionState
import com.bizur.android.data.BizurDataState
import com.bizur.android.data.BizurRepository
import com.bizur.android.data.MediaSendProgress
import com.bizur.android.model.CallLog
import com.bizur.android.model.Contact
import com.bizur.android.model.Conversation
import com.bizur.android.model.Message
import com.bizur.android.transport.TransportStatus
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class BizurViewModel(private val repository: BizurRepository) : ViewModel() {
    val uiState: StateFlow<BizurUiState> = repository.state
        .map { it.toUiState() }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = repository.state.value.toUiState()
        )

    val callState: StateFlow<CallSessionState> = repository.callState
    val mediaSendProgress: StateFlow<MediaSendProgress?> = repository.mediaSendProgress
    val transportStatus: StateFlow<TransportStatus> = repository.transportStatus
    val typingState: StateFlow<Set<String>> = repository.typingState

    fun updateDraft(text: String) {
        viewModelScope.launch { repository.updateDraft(text) }
    }

    fun sendMessage(conversationId: String, body: String) {
        if (body.isBlank()) return
        viewModelScope.launch { repository.sendMessage(conversationId, body.trim()) }
    }

    fun sendMediaMessage(conversationId: String, uri: Uri, mimeType: String?) {
        viewModelScope.launch { repository.sendMediaMessage(conversationId, uri, mimeType) }
    }

    fun setReaction(messageId: String, reaction: String?) {
        viewModelScope.launch { repository.setReaction(messageId, reaction) }
    }

    fun markConversationRead(conversationId: String, peerId: String) {
        viewModelScope.launch { repository.markConversationRead(conversationId, peerId) }
    }

    fun sendTyping(conversationId: String, peerId: String) {
        viewModelScope.launch { repository.sendTyping(conversationId, peerId) }
    }

    fun resendMessage(messageId: String) {
        viewModelScope.launch { repository.resendMessage(messageId) }
    }

    fun deleteMessage(messageId: String) {
        viewModelScope.launch { repository.deleteMessage(messageId) }
    }

    fun refreshMessages() {
        viewModelScope.launch { repository.requestSync() }
    }

    suspend fun exportAttachment(storagePath: String, displayName: String, freshCopy: Boolean): Uri? {
        return repository.exportAttachment(storagePath, displayName, freshCopy)
    }

    fun pingContact(contactId: String) {
        viewModelScope.launch { repository.pingContact(contactId) }
    }

    fun createContact(name: String, peerCode: String) {
        viewModelScope.launch { repository.createContact(name, peerCode) }
    }

    fun placeCall(contactId: String) {
        viewModelScope.launch { repository.placeCall(contactId) }
    }

    fun setContactBlocked(contactId: String, blocked: Boolean) {
        viewModelScope.launch { repository.setContactBlocked(contactId, blocked) }
    }

    fun setContactMuted(contactId: String, muted: Boolean) {
        viewModelScope.launch { repository.setContactMuted(contactId, muted) }
    }

    fun endCall() {
        viewModelScope.launch { repository.endCall() }
    }

    fun acceptCall() {
        viewModelScope.launch { repository.acceptIncomingCall() }
    }

    fun declineCall() {
        viewModelScope.launch { repository.declineIncomingCall() }
    }

    fun resetState() {
        viewModelScope.launch { repository.reset() }
    }
}

data class BizurUiState(
    val identityCode: String,
    val contacts: List<Contact>,
    val conversations: List<Conversation>,
    val messages: Map<String, List<Message>>,
    val callLogs: List<CallLog>,
    val draft: String
)

private fun BizurDataState.toUiState(): BizurUiState = BizurUiState(
    identityCode = identityCode,
    contacts = contacts,
    conversations = conversations.values.sortedByDescending { it.lastActivityEpochMillis },
    messages = messages,
    callLogs = callLogs.sortedByDescending { it.startedAtMillis },
    draft = draft
)

class BizurViewModelFactory(
    private val repository: BizurRepository
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(BizurViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return BizurViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
