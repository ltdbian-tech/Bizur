package com.bizur.android.transport

import android.util.Log
import com.bizur.android.crypto.PreKeyBundlePayload
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.websocket.DefaultClientWebSocketSession
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.ws
import io.ktor.serialization.kotlinx.json.json
import io.ktor.websocket.CloseReason
import io.ktor.websocket.Frame
import io.ktor.websocket.close
import io.ktor.websocket.readText
import io.ktor.websocket.send
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

private const val SIGNALING_TAG = "BizurSignaling"

class SignalingClient(
    private val url: String,
    private val identity: String,
    private val deviceId: Int,
    private val scope: CoroutineScope,
    private val config: TransportConfig,
    private val json: Json = Json { ignoreUnknownKeys = true },
    private val authProvider: (suspend () -> RelayAuthPayload?)? = null
) {
    private val httpClient = HttpClient(OkHttp) {
        install(WebSockets)
        install(ContentNegotiation) { json(json) }
    }

    private val sessionMutex = Mutex()
    private var session: DefaultClientWebSocketSession? = null
    private val running = AtomicBoolean(false)

    private val _events = MutableSharedFlow<SignalingEvent>(extraBufferCapacity = 32)
    val events: SharedFlow<SignalingEvent> = _events

    suspend fun start() {
        if (!running.compareAndSet(false, true)) return
        scope.launch { runLoop() }
    }

    suspend fun sendOffer(to: String, payload: SdpPayload) =
        sendRouted("offer", to, json.encodeToJsonElement(SdpPayload.serializer(), payload))

    suspend fun sendAnswer(to: String, payload: SdpPayload) =
        sendRouted("answer", to, json.encodeToJsonElement(SdpPayload.serializer(), payload))

    suspend fun sendIceCandidate(to: String, payload: IceCandidatePayload) =
        sendRouted("ice", to, json.encodeToJsonElement(IceCandidatePayload.serializer(), payload))

    suspend fun sendCiphertext(to: String, payload: CiphertextPayload) =
        sendRouted("ciphertext", to, json.encodeToJsonElement(CiphertextPayload.serializer(), payload))

    suspend fun requestQueueDrain() = sendRaw(
        buildJsonObject {
            put("type", JsonPrimitive("pullQueue"))
            put("from", JsonPrimitive(identity))
        }
    )

    fun close() {
        running.set(false)
        scope.launch {
            sessionMutex.withLock {
                session?.close(CloseReason(CloseReason.Codes.NORMAL, "shutdown"))
                session = null
            }
            httpClient.close()
        }
    }

    private suspend fun runLoop() {
        var nextDelay = config.reconnectDelayMillis
        while (running.get() && scope.isActive) {
            var delayAfter = nextDelay
            try {
                httpClient.ws(urlString = url) {
                    sessionMutex.withLock { session = this }
                    _events.emit(SignalingEvent.Connected)
                    register()
                    readIncoming()
                }
                nextDelay = config.reconnectDelayMillis
                delayAfter = nextDelay
            } catch (cancel: CancellationException) {
                throw cancel
            } catch (err: Exception) {
                Log.w(SIGNALING_TAG, "signaling socket error", err)
                _events.emit(SignalingEvent.Error(err.message ?: "signaling disconnected"))
                delayAfter = nextDelay
                nextDelay = (nextDelay * 2).coerceAtMost(config.maxReconnectDelayMillis)
            } finally {
                sessionMutex.withLock { session = null }
                _events.emit(SignalingEvent.Disconnected)
            }

            if (running.get()) {
                delay(delayAfter)
            }
        }
    }

    private suspend fun DefaultClientWebSocketSession.register() {
        val envelope = buildJsonObject {
            put("type", JsonPrimitive("register"))
            put("from", JsonPrimitive(identity))
            put("deviceId", JsonPrimitive(deviceId))
            authProvider?.invoke()?.let { proof ->
                put("auth", json.encodeToJsonElement(RelayAuthPayload.serializer(), proof))
            }
        }
        send(Frame.Text(envelope.toString()))
        requestQueueDrain()
    }

    private suspend fun DefaultClientWebSocketSession.readIncoming() {
        for (frame in incoming) {
            when (frame) {
                is Frame.Text -> handleFrame(frame.readText())
                is Frame.Binary -> Log.d(SIGNALING_TAG, "binary frame ignored (${frame.data.size} bytes)")
                is Frame.Close -> break
                is Frame.Ping -> send(Frame.Pong(frame.buffer))
                is Frame.Pong -> Unit
            }
        }
    }

    private suspend fun handleFrame(raw: String) {
        try {
            val obj = json.parseToJsonElement(raw)
            dispatchElement(obj)
        } catch (err: Exception) {
            Log.e(SIGNALING_TAG, "failed parsing frame: $raw", err)
        }
    }

    private suspend fun dispatchElement(element: JsonElement) {
        val obj = element as? JsonObject ?: return
        val type = obj["type"]?.jsonPrimitive?.content ?: return
        when (type) {
            "registered" -> _events.emit(SignalingEvent.Registered)
            "offer", "answer", "ice", "ciphertext" -> emitRouted(obj)
            "queued" -> obj["payload"]?.let { dispatchElement(it) }
            "error" -> _events.emit(
                SignalingEvent.Error(obj["message"]?.jsonPrimitive?.content ?: "unknown error")
            )
            "ping" -> sendPong()
            "queueEnd" -> Unit
            else -> Log.d(SIGNALING_TAG, "unhandled envelope: $obj")
        }
    }

    private suspend fun emitRouted(obj: JsonObject) {
        val from = obj["from"]?.jsonPrimitive?.content ?: return
        val payload = obj["payload"] ?: return
        when (obj["type"]?.jsonPrimitive?.content) {
            "offer" -> _events.emit(SignalingEvent.Offer(from, json.decodeFromJsonElement(SdpPayload.serializer(), payload)))
            "answer" -> _events.emit(SignalingEvent.Answer(from, json.decodeFromJsonElement(SdpPayload.serializer(), payload)))
            "ice" -> _events.emit(SignalingEvent.Ice(from, json.decodeFromJsonElement(IceCandidatePayload.serializer(), payload)))
            "ciphertext" -> _events.emit(SignalingEvent.Ciphertext(from, json.decodeFromJsonElement(CiphertextPayload.serializer(), payload)))
        }
    }

    private suspend fun sendRouted(type: String, to: String, payload: JsonElement) {
        val envelope = buildJsonObject {
            put("type", JsonPrimitive(type))
            put("from", JsonPrimitive(identity))
            put("to", JsonPrimitive(to))
            put("payload", payload)
        }
        sendRaw(envelope)
    }

    private suspend fun sendPong() {
        val envelope = buildJsonObject {
            put("type", JsonPrimitive("pong"))
            put("from", JsonPrimitive(identity))
        }
        sendRaw(envelope)
    }

    private suspend fun sendRaw(obj: JsonObject) {
        sessionMutex.withLock {
            val active = session ?: throw IllegalStateException("Signaling socket not ready")
            active.send(Frame.Text(obj.toString()))
        }
    }
}

sealed interface SignalingEvent {
    object Connected : SignalingEvent
    object Disconnected : SignalingEvent
    object Registered : SignalingEvent
    data class Offer(val from: String, val payload: SdpPayload) : SignalingEvent
    data class Answer(val from: String, val payload: SdpPayload) : SignalingEvent
    data class Ice(val from: String, val payload: IceCandidatePayload) : SignalingEvent
    data class Ciphertext(val from: String, val payload: CiphertextPayload) : SignalingEvent
    data class Error(val message: String) : SignalingEvent
}

@Serializable
data class SdpPayload(
    val type: String,
    val sdp: String
)

@Serializable
data class IceCandidatePayload(
    val sdpMid: String?,
    val sdpMLineIndex: Int,
    val candidate: String
)

@Serializable
data class CiphertextPayload(
    val blob: String,
    val conversationHint: String? = null,
    val mimeType: String = "text/plain",
    val preKeyBundle: PreKeyBundlePayload? = null
)

@Serializable
data class RelayAuthPayload(
    val peerCode: String,
    val timestamp: Long,
    val identityKey: String,
    val signature: String
)
