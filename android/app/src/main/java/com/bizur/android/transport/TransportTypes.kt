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

    suspend fun start()
    suspend fun sendMessage(peerId: String, payload: TransportPayload)
    suspend fun requestQueueSync()
    fun close()
}

data class IncomingMessage(
    val peerId: String,
    val payload: TransportPayload,
    val via: TransportChannel
)

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
