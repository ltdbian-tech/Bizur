package com.bizur.android.transport

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.Serializable

/**
 * Shared contract for components that deliver encrypted payloads between peers.
 */
interface MessageTransport {
    val status: StateFlow<TransportStatus>
    val incomingMessages: Flow<IncomingMessage>
    val contactEvents: Flow<ContactEvent>

    suspend fun start()
    suspend fun sendMessage(peerId: String, payload: TransportPayload)
    suspend fun requestQueueSync()
    suspend fun sendContactRequest(peerId: String, displayName: String)
    suspend fun sendContactResponse(peerId: String, accepted: Boolean, displayName: String)
    suspend fun lookupPeer(peerCode: String)
    fun close()

    /**
     * Returns true if the peer has an open P2P data channel for direct messaging.
     * Useful for checking if large payloads (attachments) can be sent without queuing.
     */
    fun isPeerDirectlyReachable(peerId: String): Boolean = false
}

data class IncomingMessage(
    val peerId: String,
    val payload: TransportPayload,
    val via: TransportChannel
)

sealed interface ContactEvent {
    data class RequestReceived(val from: String, val displayName: String, val timestamp: Long) : ContactEvent
    data class ResponseReceived(val from: String, val accepted: Boolean, val displayName: String) : ContactEvent
    data class LookupResult(val peerCode: String, val found: Boolean) : ContactEvent
}

enum class TransportChannel { DataChannel, SignalingQueue }

enum class TransportStatus { Disconnected, Connecting, Connected }

@Serializable
data class TransportPayload(
    val messageId: String,
    val conversationHint: String,
    val body: String,
    val sentAtEpochMillis: Long,
    val mimeType: String = "text/plain"
)

@Serializable
data class EncryptedEnvelope(
    val ciphertext: String,
    val messageType: Int
)

@Serializable
data class MediaEnvelope(
    val messageId: String,
    val fileName: String,
    val mimeType: String,
    val sizeBytes: Long,
    val caption: String? = null,
    val data: String
)

@Serializable
data class MediaChunkEnvelope(
    val messageId: String,
    val fileName: String,
    val mimeType: String,
    val sizeBytes: Long,
    val caption: String? = null,
    val chunkIndex: Int,
    val totalChunks: Int,
    val data: String
)
