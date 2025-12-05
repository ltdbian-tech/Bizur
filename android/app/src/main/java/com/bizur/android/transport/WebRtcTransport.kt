package com.bizur.android.transport

import android.content.Context
import android.util.Base64
import android.util.Log
import com.bizur.android.crypto.IdentityManager
import com.bizur.android.crypto.PreKeyBundlePayload
import com.bizur.android.crypto.SignalCipher
import com.bizur.android.crypto.SignalStore
import java.nio.ByteBuffer
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.collections.buildSet
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import org.whispersystems.libsignal.ecc.Curve
import org.webrtc.AudioSource
import org.webrtc.AudioTrack
import org.webrtc.DataChannel
import org.webrtc.DefaultVideoDecoderFactory
import org.webrtc.DefaultVideoEncoderFactory
import org.webrtc.EglBase
import org.webrtc.IceCandidate
import org.webrtc.MediaConstraints
import org.webrtc.MediaStreamTrack
import org.webrtc.PeerConnection
import org.webrtc.PeerConnectionFactory
import org.webrtc.RtpSender
import org.webrtc.RtpTransceiver
import org.webrtc.SdpObserver
import org.webrtc.SessionDescription
import org.webrtc.audio.JavaAudioDeviceModule

private const val TRANSPORT_TAG = "BizurWebRTC"
private const val DATA_CHANNEL_LABEL = "bizur-data"

@OptIn(ExperimentalCoroutinesApi::class)
class WebRtcTransport(
	context: Context,
	private val scope: CoroutineScope,
	identityManager: IdentityManager,
	private val signalStore: SignalStore,
	private val selfPeerCode: String,
	private val config: TransportConfig = TransportConfig(),
	private val json: Json = Json { ignoreUnknownKeys = true }
) : MessageTransport {
	private val appContext = context.applicationContext
	private val profile = identityManager.getOrCreateProfile()
	private val selfId = buildSelfId(profile.registrationId, profile.deviceId)
	private val localPreKeyBundle: PreKeyBundlePayload by lazy { signalStore.exportPreKeyBundle() }
	private val preKeyService = PreKeyService(config.preKeyServiceUrl, json, config.authToken)
	private val signalCipher = SignalCipher(signalStore, preKeyService)

	private val signalingClient = SignalingClient(
		url = config.signalingUrl,
		identity = selfId,
		deviceId = profile.deviceId,
		scope = scope,
		config = config,
		json = json,
		authProvider = { buildRelayAuthPayload() }
	)

	private val started = AtomicBoolean(false)
	private val sessions = ConcurrentHashMap<String, PeerSession>()
	private val sessionMutex = Mutex()

	private val _status = MutableStateFlow(TransportStatus.Disconnected)
	override val status = _status.asStateFlow()

	private val _incoming = MutableSharedFlow<IncomingMessage>(extraBufferCapacity = 32)
	override val incomingMessages = _incoming.asSharedFlow()

	private val eglBase = EglBase.create()
	private val audioDeviceModule = JavaAudioDeviceModule.builder(appContext)
		.setUseHardwareAcousticEchoCanceler(true)
		.setUseHardwareNoiseSuppressor(true)
		.createAudioDeviceModule()
	private val peerConnectionFactory: PeerConnectionFactory
	private val rtcConfig: PeerConnection.RTCConfiguration
	private val audioSource: AudioSource
	private val localAudioTrack: AudioTrack

	private var queueJob: Job? = null

	init {
		ensureFactoryInitialization(appContext)
		val encoderFactory = DefaultVideoEncoderFactory(eglBase.eglBaseContext, true, true)
		val decoderFactory = DefaultVideoDecoderFactory(eglBase.eglBaseContext)
		peerConnectionFactory = PeerConnectionFactory.builder()
			.setAudioDeviceModule(audioDeviceModule)
			.setVideoEncoderFactory(encoderFactory)
			.setVideoDecoderFactory(decoderFactory)
			.createPeerConnectionFactory()

		rtcConfig = PeerConnection.RTCConfiguration(buildIceServers()).apply {
			sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
		}

		audioSource = peerConnectionFactory.createAudioSource(MediaConstraints())
		localAudioTrack = peerConnectionFactory.createAudioTrack("bizur-audio", audioSource).apply {
			setEnabled(false)
		}
	}

	override suspend fun start() {
		if (!started.compareAndSet(false, true)) return
		_status.value = TransportStatus.Connecting
		signalingClient.start()
		scope.launch { observeSignaling() }
		queueJob = scope.launch { pollQueueLoop() }
		scope.launch { publishLocalBundle() }
	}

	override suspend fun sendMessage(peerId: String, payload: TransportPayload) {
		val plaintext = json.encodeToString(payload).encodeToByteArray()
		val envelope = signalCipher.encrypt(peerId, plaintext)
		val serializedEnvelope = serializeEnvelope(envelope)
		val session = ensureSession(peerId)
		val dataChannel = session?.dataChannel
		if (dataChannel != null && dataChannel.state() == DataChannel.State.OPEN) {
			dataChannel.send(serializedEnvelope.toBuffer())
			return
		}

		val ciphertext = CiphertextPayload(
			blob = serializedEnvelope.toBase64(),
			conversationHint = payload.conversationHint,
			mimeType = payload.mimeType,
			preKeyBundle = localPreKeyBundle
		)
		signalingClient.sendCiphertext(peerId, ciphertext)
	}

	suspend fun enableAudio(peerId: String) {
		val session = ensureSession(peerId) ?: return
		sessionMutex.withLock {
			session.audioTransceiver?.direction = RtpTransceiver.RtpTransceiverDirection.SEND_RECV
			session.audioSender?.setTrack(localAudioTrack, true)
			localAudioTrack.setEnabled(true)
		}
		negotiateOffer(peerId, session)
	}

	suspend fun disableAudio(peerId: String) {
		sessionMutex.withLock {
			val session = sessions[peerId] ?: return
			session.audioSender?.setTrack(null, true)
			session.audioTransceiver?.direction = RtpTransceiver.RtpTransceiverDirection.INACTIVE
			if (sessions.values.none { it.audioTransceiver?.direction == RtpTransceiver.RtpTransceiverDirection.SEND_RECV }) {
				localAudioTrack.setEnabled(false)
			}
		}
	}

	override suspend fun requestQueueSync() {
		signalingClient.requestQueueDrain()
	}

	override fun close() {
		started.set(false)
		queueJob?.cancel()
		signalingClient.close()
		preKeyService.close()
		scope.launch {
			sessionMutex.withLock {
				sessions.values.forEach { session ->
					session.dataChannel?.unregisterObserver()
					session.dataChannel?.dispose()
					session.connection.close()
				}
				sessions.clear()
			}
			localAudioTrack.dispose()
			audioSource.dispose()
			audioDeviceModule.release()
			peerConnectionFactory.dispose()
			eglBase.release()
		}
	}

	private suspend fun ensureSession(peerId: String): PeerSession? =
		sessionMutex.withLock {
			sessions[peerId] ?: createPeerSession(peerId, initiator = true)
		}

	private suspend fun createPeerSession(peerId: String, initiator: Boolean): PeerSession? {
		val connection = peerConnectionFactory.createPeerConnection(rtcConfig, object : PeerConnection.Observer {
			override fun onSignalingChange(newState: PeerConnection.SignalingState) {
				Log.d(TRANSPORT_TAG, "signaling change for $peerId -> $newState")
			}

			override fun onIceConnectionChange(newState: PeerConnection.IceConnectionState) {
				Log.d(TRANSPORT_TAG, "ice state for $peerId -> $newState")
			}

			override fun onIceConnectionReceivingChange(receiving: Boolean) = Unit

			override fun onIceGatheringChange(newState: PeerConnection.IceGatheringState) = Unit

			override fun onIceCandidate(candidate: IceCandidate) {
				scope.launch {
					signalingClient.sendIceCandidate(
						peerId,
						IceCandidatePayload(candidate.sdpMid, candidate.sdpMLineIndex, candidate.sdp)
					)
				}
			}

			override fun onIceCandidatesRemoved(candidates: Array<out IceCandidate>) = Unit

			override fun onDataChannel(channel: DataChannel) {
				attachDataChannel(peerId, channel)
			}

			override fun onRenegotiationNeeded() {
				Log.d(TRANSPORT_TAG, "renegotiation requested for $peerId")
			}

			override fun onAddStream(stream: org.webrtc.MediaStream?) = Unit
			override fun onRemoveStream(stream: org.webrtc.MediaStream?) = Unit
			override fun onAddTrack(receiver: org.webrtc.RtpReceiver?, streams: Array<out org.webrtc.MediaStream>?) = Unit
		}) ?: return null

		val session = PeerSession(peerId, connection, initiator)
		val audioTransceiver = connection.addTransceiver(
			MediaStreamTrack.MediaType.MEDIA_TYPE_AUDIO,
			RtpTransceiver.RtpTransceiverInit(RtpTransceiver.RtpTransceiverDirection.INACTIVE)
		)
		session.audioTransceiver = audioTransceiver
		session.audioSender = audioTransceiver.sender
		sessions[peerId] = session

		if (initiator) {
			val init = DataChannel.Init().apply {
				ordered = true
				negotiated = false
				id = -1
			}
			val channel = connection.createDataChannel(DATA_CHANNEL_LABEL, init)
			attachDataChannel(peerId, channel)
			scope.launch { negotiateOffer(peerId, session) }
		}

		return session
	}

	private suspend fun negotiateOffer(peerId: String, session: PeerSession) {
		val constraints = MediaConstraints()
		val offer = session.connection.awaitCreateOffer(constraints)
		session.connection.awaitSetLocalDescription(offer)
		signalingClient.sendOffer(peerId, offer.toPayload())
	}

	private suspend fun handleRemoteOffer(from: String, payload: SdpPayload) {
		val session = sessionMutex.withLock {
			sessions[from] ?: createPeerSession(from, initiator = false)
		} ?: return

		val description = SessionDescription(payload.type.toSdpType(), payload.sdp)
		session.connection.awaitSetRemoteDescription(description)
		val answer = session.connection.awaitCreateAnswer(MediaConstraints())
		session.connection.awaitSetLocalDescription(answer)
		signalingClient.sendAnswer(from, answer.toPayload())
	}

	private suspend fun handleRemoteAnswer(from: String, payload: SdpPayload) {
		val session = sessions[from] ?: return
		val description = SessionDescription(payload.type.toSdpType(), payload.sdp)
		session.connection.awaitSetRemoteDescription(description)
	}

	private fun handleRemoteCandidate(from: String, payload: IceCandidatePayload) {
		val session = sessions[from] ?: return
		val candidate = IceCandidate(payload.sdpMid, payload.sdpMLineIndex, payload.candidate)
		session.connection.addIceCandidate(candidate)
	}

	private fun attachDataChannel(peerId: String, channel: DataChannel) {
		val session = sessions[peerId] ?: return
		session.dataChannel?.unregisterObserver()
		session.dataChannel = channel
		channel.registerObserver(object : DataChannel.Observer {
			override fun onBufferedAmountChange(previousAmount: Long) = Unit

			override fun onStateChange() {
				Log.d(TRANSPORT_TAG, "data channel state for $peerId -> ${channel.state()}")
			}

			override fun onMessage(buffer: DataChannel.Buffer) {
				val bytes = ByteArray(buffer.data.remaining())
				buffer.data.get(bytes)
				scope.launch {
					runCatching {
						val envelope = deserializeEnvelope(bytes)
						val plaintext = signalCipher.decrypt(peerId, envelope, null)
						val payload = json.decodeFromString<TransportPayload>(plaintext.decodeToString())
						_incoming.emit(
							IncomingMessage(peerId, payload, TransportChannel.DataChannel)
						)
					}.onFailure { err ->
						Log.e(TRANSPORT_TAG, "failed to decode payload", err)
					}
				}
			}
		})
	}

	private suspend fun observeSignaling() {
		signalingClient.events.collect { event ->
			when (event) {
				SignalingEvent.Connected -> _status.value = TransportStatus.Connecting
				SignalingEvent.Registered -> {
					_status.value = TransportStatus.Connected
					runCatching { signalingClient.requestQueueDrain() }
				}
				SignalingEvent.Disconnected -> _status.value = TransportStatus.Disconnected
				is SignalingEvent.Offer -> handleRemoteOffer(event.from, event.payload)
				is SignalingEvent.Answer -> handleRemoteAnswer(event.from, event.payload)
				is SignalingEvent.Ice -> handleRemoteCandidate(event.from, event.payload)
				is SignalingEvent.Ciphertext -> handleCiphertext(event)
				is SignalingEvent.Error -> Log.e(TRANSPORT_TAG, "signaling error: ${event.message}")
			}
		}
	}

	private fun handleCiphertext(event: SignalingEvent.Ciphertext) {
		scope.launch {
			runCatching {
				val envelopeBytes = event.payload.blob.fromBase64()
				val envelope = deserializeEnvelope(envelopeBytes)
				val plaintext = signalCipher.decrypt(event.from, envelope, event.payload.preKeyBundle)
				val decoded = json.decodeFromString<TransportPayload>(plaintext.decodeToString())
				_incoming.emit(IncomingMessage(event.from, decoded, TransportChannel.SignalingQueue))
			}.onFailure { err ->
				Log.e(TRANSPORT_TAG, "failed to decode ciphertext", err)
			}
		}
	}

	private suspend fun pollQueueLoop() {
		while (scope.isActive) {
			delay(config.queuePollIntervalMillis)
			runCatching { signalingClient.requestQueueDrain() }
		}
	}

	private fun buildRelayAuthPayload(): RelayAuthPayload? {
		if (selfPeerCode.isBlank()) return null
		val timestamp = System.currentTimeMillis()
		val statement = "$selfId|$selfPeerCode|$timestamp"
		val signature = Curve.calculateSignature(profile.identityKeyPair.privateKey, statement.encodeToByteArray())
		val identityKey = profile.identityKeyPair.publicKey.serialize().toBase64()
		return RelayAuthPayload(
			peerCode = selfPeerCode,
			timestamp = timestamp,
			identityKey = identityKey,
			signature = signature.toBase64()
		)
	}

	private suspend fun publishLocalBundle() {
		val aliases = buildSet {
			add(selfId)
			if (selfPeerCode.isNotBlank()) add(selfPeerCode)
		}
		aliases.forEach { alias ->
			runCatching { preKeyService.publish(alias, localPreKeyBundle) }
				.onFailure { err -> Log.w(TRANSPORT_TAG, "failed to publish prekeys for $alias", err) }
		}
	}

	private fun serializeEnvelope(envelope: EncryptedEnvelope): ByteArray =
		json.encodeToString(envelope).encodeToByteArray()

	private fun deserializeEnvelope(bytes: ByteArray): EncryptedEnvelope =
		json.decodeFromString<EncryptedEnvelope>(bytes.decodeToString())

	private fun buildIceServers(): List<PeerConnection.IceServer> {
		val stunServers = config.stunServers.map { PeerConnection.IceServer.builder(it).createIceServer() }
		val turnServers = config.turnServers.map {
			PeerConnection.IceServer.builder(it.urls)
				.setUsername(it.username)
				.setPassword(it.credential)
				.createIceServer()
		}
		return stunServers + turnServers
	}

	private fun SessionDescription.toPayload() =
		SdpPayload(type = type.canonicalForm(), sdp = description)

	private fun String.toSdpType(): SessionDescription.Type =
		SessionDescription.Type.fromCanonicalForm(this)

	private fun ByteArray.toBase64(): String =
		Base64.encodeToString(this, Base64.NO_WRAP or Base64.URL_SAFE)

	private fun String.fromBase64(): ByteArray =
		Base64.decode(this, Base64.NO_WRAP or Base64.URL_SAFE)

	private fun ByteArray.toBuffer(): DataChannel.Buffer {
		val buffer = ByteBuffer.allocateDirect(size)
		buffer.put(this)
		buffer.flip()
		return DataChannel.Buffer(buffer, false)
	}

	private suspend fun PeerConnection.awaitCreateOffer(constraints: MediaConstraints) =
		createSdp(::createOffer, constraints)

	private suspend fun PeerConnection.awaitCreateAnswer(constraints: MediaConstraints) =
		createSdp(::createAnswer, constraints)

	private suspend fun PeerConnection.createSdp(
		action: (SdpObserver, MediaConstraints) -> Unit,
		constraints: MediaConstraints
	): SessionDescription = kotlinx.coroutines.suspendCancellableCoroutine { continuation ->
		action(object : SimpleSdpObserver() {
			override fun onCreateSuccess(sdp: SessionDescription) {
				continuation.resume(sdp) {}
			}

			override fun onCreateFailure(error: String?) {
				continuation.resumeWithException(IllegalStateException(error ?: "SDP failure"))
			}
		}, constraints)
	}

	private suspend fun PeerConnection.awaitSetLocalDescription(description: SessionDescription) =
		suspendSetting { setLocalDescription(it, description) }

	private suspend fun PeerConnection.awaitSetRemoteDescription(description: SessionDescription) =
		suspendSetting { setRemoteDescription(it, description) }

	private suspend fun PeerConnection.suspendSetting(action: (SdpObserver) -> Unit) =
		kotlinx.coroutines.suspendCancellableCoroutine<Unit> { continuation ->
			action(object : SimpleSdpObserver() {
				override fun onSetSuccess() {
					continuation.resume(Unit) {}
				}

				override fun onSetFailure(error: String?) {
					continuation.resumeWithException(IllegalStateException(error ?: "SDP set failure"))
				}
			})
		}

	private class PeerSession(
		val peerId: String,
		val connection: PeerConnection,
		val initiator: Boolean,
		var dataChannel: DataChannel? = null,
		var audioTransceiver: RtpTransceiver? = null,
		var audioSender: RtpSender? = null
	)

	private open class SimpleSdpObserver : SdpObserver {
		override fun onCreateSuccess(sdp: SessionDescription) = Unit
		override fun onSetSuccess() = Unit
		override fun onCreateFailure(error: String?) = Unit
		override fun onSetFailure(error: String?) = Unit
	}

	companion object {
		private val factoryInitialized = AtomicBoolean(false)

		private fun ensureFactoryInitialization(context: Context) {
			if (factoryInitialized.compareAndSet(false, true)) {
				val options = PeerConnectionFactory.InitializationOptions.builder(context)
					.setEnableInternalTracer(false)
					.createInitializationOptions()
				PeerConnectionFactory.initialize(options)
			}
		}

		private fun buildSelfId(registrationId: Int, deviceId: Int): String =
			"bizur-$registrationId-$deviceId"
	}
}
