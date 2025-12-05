package com.bizur.android.call

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import com.bizur.android.model.CallDirection
import com.bizur.android.transport.MessageTransport
import com.bizur.android.transport.TransportPayload
import com.bizur.android.transport.WebRtcTransport
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class CallCoordinator(
    private val context: Context,
    private val transport: WebRtcTransport,
    private val messaging: MessageTransport,
    private val scope: CoroutineScope,
    private val json: Json = Json { ignoreUnknownKeys = true }
) {
    private val _state = MutableStateFlow(CallSessionState())
    val state: StateFlow<CallSessionState> = _state.asStateFlow()

    suspend fun startCall(peerId: String, displayName: String) {
        if (!hasMicPermission()) {
            android.util.Log.w("CallCoordinator", "Cannot start call - microphone permission not granted")
            return
        }
        val callId = UUID.randomUUID().toString()
        updateState(
            CallSessionState(
            status = CallStatus.Calling,
            callId = callId,
            peerId = peerId,
            displayName = displayName,
            direction = CallDirection.Outgoing,
            startedAtMillis = System.currentTimeMillis()
            )
        )
        transport.enableAudio(peerId)
        sendSignal(peerId, CallSignal(CallSignalType.INVITE, callId, displayName))
    }

    suspend fun handleSignal(peerId: String, displayName: String, payload: TransportPayload) {
        runCatching {
            json.decodeFromString(CallSignal.serializer(), payload.body)
        }.onFailure { return }.onSuccess { signal ->
            when (signal.type) {
                CallSignalType.INVITE -> handleInvite(peerId, displayName, signal)
                CallSignalType.ACCEPT -> handleAccept(peerId, displayName, signal)
                CallSignalType.END -> handleRemoteEnd()
            }
        }
    }

    suspend fun endCall() {
        val active = _state.value
        val peerId = active.peerId ?: return
        sendSignal(peerId, CallSignal(CallSignalType.END, active.callId ?: UUID.randomUUID().toString(), active.displayName))
        transport.disableAudio(peerId)
        updateState(CallSessionState())
    }

    suspend fun acceptCall() {
        val ringingState = _state.value
        if (ringingState.status != CallStatus.Ringing) return
        val peerId = ringingState.peerId ?: return
        val callId = ringingState.callId ?: return
        if (!hasMicPermission()) {
            android.util.Log.w("CallCoordinator", "Cannot accept call - microphone permission not granted")
            return
        }
        transport.enableAudio(peerId)
        sendSignal(peerId, CallSignal(CallSignalType.ACCEPT, callId, ringingState.displayName))
        updateState(
            ringingState.copy(
                status = CallStatus.Connected,
                startedAtMillis = System.currentTimeMillis()
            )
        )
    }

    suspend fun rejectCall() {
        val ringingState = _state.value
        if (ringingState.status != CallStatus.Ringing) return
        val peerId = ringingState.peerId ?: return
        val callId = ringingState.callId ?: return
        sendSignal(peerId, CallSignal(CallSignalType.END, callId, ringingState.displayName))
        updateState(CallSessionState())
    }

    private suspend fun handleInvite(peerId: String, displayName: String, signal: CallSignal) {
        updateState(
            CallSessionState(
            status = CallStatus.Ringing,
            callId = signal.callId,
            peerId = peerId,
            displayName = signal.displayName ?: displayName,
            direction = CallDirection.Incoming,
            startedAtMillis = System.currentTimeMillis()
            )
        )
    }

    private fun handleAccept(peerId: String, displayName: String, signal: CallSignal) {
        updateState(
            CallSessionState(
            status = CallStatus.Connected,
            callId = signal.callId,
            peerId = peerId,
            displayName = displayName,
            direction = CallDirection.Outgoing,
            startedAtMillis = System.currentTimeMillis()
            )
        )
    }

    private fun handleRemoteEnd() {
        val previous = _state.value
        previous.peerId?.let { peer ->
            scope.launch { transport.disableAudio(peer) }
        }
        updateState(CallSessionState())
    }

    private suspend fun sendSignal(peerId: String, signal: CallSignal) {
        val payload = TransportPayload(
            messageId = signal.callId,
            conversationHint = "call-$peerId",
            body = json.encodeToString(signal),
            sentAtEpochMillis = System.currentTimeMillis(),
            mimeType = CALL_MIME_TYPE
        )
        messaging.sendMessage(peerId, payload)
    }

    private fun hasMicPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

    private fun updateState(newState: CallSessionState) {
        _state.value = newState
        CallForegroundService.update(context, newState)
    }
}
