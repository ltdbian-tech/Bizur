package com.bizur.android.data

import android.content.Context
import android.net.Uri
import android.util.Base64
import android.util.Log
import com.bizur.android.call.CALL_MIME_TYPE
import com.bizur.android.call.CallCoordinator
import com.bizur.android.call.CallSessionState
import com.bizur.android.call.CallStatus
import com.bizur.android.data.local.CallLogDao
import com.bizur.android.data.local.ConversationDao
import com.bizur.android.data.local.ContactDao
import com.bizur.android.data.local.MessageDao
import com.bizur.android.data.local.toEntity
import com.bizur.android.data.local.toModel
import com.bizur.android.media.AttachmentStore
import com.bizur.android.model.CallDirection
import com.bizur.android.model.CallLog
import com.bizur.android.model.Contact
import com.bizur.android.model.ContactStatus
import com.bizur.android.model.Conversation
import com.bizur.android.model.Message
import com.bizur.android.model.MessageStatus
import com.bizur.android.model.PresenceStatus
import com.bizur.android.notifications.MessageNotifier
import com.bizur.android.transport.ContactEvent
import com.bizur.android.transport.IncomingMessage
import com.bizur.android.transport.MediaChunkEnvelope
import com.bizur.android.transport.MediaEnvelope
import com.bizur.android.transport.MessageTransport
import com.bizur.android.transport.TransportPayload
import com.bizur.android.transport.TransportStatus
import com.bizur.android.transport.TransportChannel
import java.io.ByteArrayOutputStream
import java.util.Locale
import java.util.UUID
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.math.min

class BizurRepository(
    private val contactDao: ContactDao,
    private val conversationDao: ConversationDao,
    private val messageDao: MessageDao,
    private val callLogDao: CallLogDao,
    private val draftStore: DraftStore,
    private val identityStore: IdentityStore,
    private val ioDispatcher: CoroutineDispatcher,
    externalScope: CoroutineScope,
    private val messageTransport: MessageTransport? = null,
    private val messageNotifier: MessageNotifier? = null,
    private val callCoordinator: CallCoordinator? = null,
    private val attachmentStore: AttachmentStore
) {
    private val appScope = externalScope
    private val json = Json { ignoreUnknownKeys = true }
    private val pendingMediaChunks = mutableMapOf<String, PendingMedia>()
    private val _mediaSendProgress = MutableStateFlow<MediaSendProgress?>(null)
    val mediaSendProgress: StateFlow<MediaSendProgress?> = _mediaSendProgress.asStateFlow()
    val transportStatus: StateFlow<TransportStatus> = messageTransport?.status
        ?: MutableStateFlow(TransportStatus.Disconnected)
    private val _typingState = MutableStateFlow<Set<String>>(emptySet())
    val typingState: StateFlow<Set<String>> = _typingState.asStateFlow()

    private fun evictExpiredPendingChunks(referenceTimeMillis: Long) {
        if (pendingMediaChunks.isEmpty()) return
        val iterator = pendingMediaChunks.entries.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            if (entry.value.isExpired(referenceTimeMillis, PENDING_CHUNK_TTL_MILLIS)) {
                Log.w(TAG, "Evicting stale chunk buffer for ${entry.key}")
                iterator.remove()
            }
        }
    }
    private val identityCodeState: StateFlow<String> = identityStore.identityCode.stateIn(
        scope = appScope,
        started = SharingStarted.Eagerly,
        initialValue = ""
    )

    private val dataStreams = combine(
        contactDao.observeContacts(),
        conversationDao.observeConversations(),
        messageDao.observeMessages(),
        callLogDao.observeCallLogs(),
        draftStore.draft
    ) { contacts, conversations, messages, callLogs, draft ->
        val contactModels = contacts.map { it.toModel() }
        val blockedIds = contactModels.filter { it.isBlocked }.map { it.id }.toSet()

        val conversationModels = conversations
            .map { it.toModel() }
            .filterNot { it.peerId in blockedIds }

        val conversationMap = conversationModels.associateBy { it.id }

        val messageMap = messages
            .groupBy { it.conversationId }
            .filterKeys { key -> conversationMap.containsKey(key) }
            .mapValues { entry -> entry.value.map { it.toModel() }.sortedBy { it.sentAtEpochMillis } }

        val callLogModels = callLogs
            .map { it.toModel() }
            .filterNot { it.contactId in blockedIds }

        BizurDataState(
            identityCode = "",
            contacts = contactModels,
            conversations = conversationMap,
            messages = messageMap,
            callLogs = callLogModels,
            draft = draft
        )
    }

    val state: StateFlow<BizurDataState> = combine(identityCodeState, dataStreams) { identityCode, snapshot ->
        snapshot.copy(identityCode = identityCode.ifBlank { "generating..." })
    }.stateIn(
        scope = appScope,
        started = SharingStarted.Eagerly,
        initialValue = BizurDataState.empty(identityCodeState.value.ifBlank { "" })
    )

    val callState: StateFlow<CallSessionState> = callCoordinator?.state
        ?: MutableStateFlow(CallSessionState())

    init {
        appScope.launch { identityStore.ensureIdentityCode() }

        messageTransport?.let { transport ->
            appScope.launch { transport.start() }
            appScope.launch {
                transport.incomingMessages.collect { incoming ->
                    handleIncomingMessage(incoming)
                }
            }
            appScope.launch {
                transport.contactEvents.collect { event ->
                    handleContactEvent(event)
                }
            }
        }

        callCoordinator?.let { coordinator ->
            appScope.launch { observeCallSessions(coordinator) }
        }
    }

    /**
     * Returns true if the peer has an open P2P data channel.
     * Useful for gating attachment sends that should only happen when both users are online.
     */
    fun isPeerDirectlyReachable(peerId: String): Boolean =
        messageTransport?.isPeerDirectlyReachable(peerId) == true

    suspend fun updateDraft(draft: String) {
        withContext(ioDispatcher) { draftStore.update(draft) }
    }

    suspend fun setReaction(messageId: String, reaction: String?) {
        withContext(ioDispatcher) {
            messageDao.updateReaction(messageId, reaction)
        }
    }

    suspend fun markConversationRead(conversationId: String, peerId: String) {
        withContext(ioDispatcher) {
            val latest = messageDao.latestForConversation(conversationId) ?: return@withContext
            conversationDao.getById(conversationId)?.let { entity ->
                conversationDao.upsert(entity.toModel().copy(unreadCount = 0).toEntity())
            }
            val payload = TransportPayload(
                messageId = "read-${latest.id}",
                conversationHint = conversationId,
                body = latest.id,
                sentAtEpochMillis = System.currentTimeMillis(),
                mimeType = READ_RECEIPT_MIME
            )
            runCatching { messageTransport?.sendMessage(peerId, payload) }
        }
    }

    suspend fun sendTyping(conversationId: String, peerId: String) {
        withContext(ioDispatcher) {
            val payload = TransportPayload(
                messageId = "typing-$conversationId-${System.currentTimeMillis()}",
                conversationHint = conversationId,
                body = "typing",
                sentAtEpochMillis = System.currentTimeMillis(),
                mimeType = TYPING_MIME
            )
            runCatching { messageTransport?.sendMessage(peerId, payload) }
        }
    }

    suspend fun sendMessage(conversationId: String, body: String) {
        withContext(ioDispatcher) {
            val trimmed = body.trim()
            if (trimmed.isEmpty()) return@withContext

            val timestamp = System.currentTimeMillis()
            val conversationSnapshot = buildConversationSnapshot(conversationId, trimmed, timestamp)
            val peerId = conversationSnapshot.peerId
            val contactRecord = contactDao.getById(peerId)
            if (contactRecord?.isBlocked == true) return@withContext

            val message = Message(
                id = UUID.randomUUID().toString(),
                conversationId = conversationId,
                senderId = resolveIdentityCode(),
                body = trimmed,
                sentAtEpochMillis = timestamp,
                status = MessageStatus.Sending
            )

            messageDao.insert(message.toEntity())
            conversationDao.upsert(conversationSnapshot.toEntity())
            draftStore.update("")

            val payload = TransportPayload(
                messageId = message.id,
                conversationHint = conversationId,
                body = trimmed,
                sentAtEpochMillis = timestamp
            )

            transmitPayload(peerId, payload, message.id)
        }
    }

    suspend fun sendMediaMessage(conversationId: String, uri: Uri, mimeType: String?) {
        withContext(ioDispatcher) {
            val attachment = attachmentStore.fromUri(uri, mimeType)
            val timestamp = System.currentTimeMillis()
            val preview = attachmentPreview(attachment.file.displayName)
            val conversationSnapshot = buildConversationSnapshot(conversationId, preview, timestamp)
            val peerId = conversationSnapshot.peerId
            val contactRecord = contactDao.getById(peerId)
            if (contactRecord?.isBlocked == true) return@withContext

            val message = Message(
                id = UUID.randomUUID().toString(),
                conversationId = conversationId,
                senderId = resolveIdentityCode(),
                body = preview,
                sentAtEpochMillis = timestamp,
                status = MessageStatus.Sending,
                attachmentPath = attachment.file.storagePath,
                attachmentMimeType = attachment.file.mimeType,
                attachmentDisplayName = attachment.file.displayName
            )

            messageDao.insert(message.toEntity())
            conversationDao.upsert(conversationSnapshot.toEntity())

            val chunks = chunkify(attachment.bytes)
            val chunkCount = chunks.size
            val trackProgress = chunkCount > 1
            if (trackProgress) {
                _mediaSendProgress.value = MediaSendProgress(
                    messageId = message.id,
                    displayName = attachment.file.displayName,
                    sentChunks = 0,
                    totalChunks = chunkCount
                )
            }

            try {
                if (chunkCount == 1) {
                val envelope = MediaEnvelope(
                    messageId = message.id,
                    fileName = attachment.file.displayName,
                    mimeType = attachment.file.mimeType,
                    sizeBytes = attachment.file.sizeBytes,
                    caption = preview,
                    data = chunks.first().toBase64()
                )

                val payload = TransportPayload(
                    messageId = message.id,
                    conversationHint = conversationId,
                    body = json.encodeToString(envelope),
                    sentAtEpochMillis = timestamp,
                    mimeType = MEDIA_WRAPPER_MIME
                )

                    transmitPayload(peerId, payload, message.id)
                } else {
                    chunks.forEachIndexed { index, chunk ->
                        val chunkEnvelope = MediaChunkEnvelope(
                            messageId = message.id,
                            fileName = attachment.file.displayName,
                            mimeType = attachment.file.mimeType,
                            sizeBytes = attachment.file.sizeBytes,
                            caption = if (index == 0) preview else null,
                            chunkIndex = index,
                            totalChunks = chunkCount,
                            data = chunk.toBase64()
                        )
                        val payload = TransportPayload(
                            messageId = message.id,
                            conversationHint = conversationId,
                            body = json.encodeToString(chunkEnvelope),
                            sentAtEpochMillis = timestamp,
                            mimeType = MEDIA_CHUNK_MIME
                        )
                        transmitPayload(peerId, payload, message.id)
                        if (trackProgress) {
                            _mediaSendProgress.value = MediaSendProgress(
                                messageId = message.id,
                                displayName = attachment.file.displayName,
                                sentChunks = index + 1,
                                totalChunks = chunkCount
                            )
                        }
                    }
                }
            } finally {
                if (trackProgress) {
                    _mediaSendProgress.value = null
                }
            }
        }
    }

    suspend fun resendMessage(messageId: String) {
        withContext(ioDispatcher) {
            val message = messageDao.getById(messageId) ?: return@withContext
            if (message.attachmentPath != null) {
                val uri = attachmentStore.exportToCache(
                    storagePath = message.attachmentPath,
                    displayName = message.attachmentDisplayName ?: message.attachmentPath,
                    reuseExisting = false
                )
                sendMediaMessage(message.conversationId, uri, message.attachmentMimeType)
                messageDao.deleteById(messageId)
                return@withContext
            }
            sendMessage(message.conversationId, message.body)
            messageDao.deleteById(messageId)
        }
    }

    suspend fun deleteMessage(messageId: String) {
        withContext(ioDispatcher) {
            val message = messageDao.getById(messageId) ?: return@withContext
            messageDao.deleteById(messageId)
            val latest = messageDao.latestForConversation(message.conversationId)
            val conversation = conversationDao.getById(message.conversationId)?.toModel() ?: return@withContext
            val updated = conversation.copy(
                lastMessagePreview = latest?.body ?: "",
                lastActivityEpochMillis = latest?.sentAtEpochMillis ?: conversation.lastActivityEpochMillis
            )
            conversationDao.upsert(updated.toEntity())
        }
    }

    suspend fun pingContact(contactId: String) {
        withContext(ioDispatcher) {
            val contactEntity = contactDao.getById(contactId) ?: return@withContext
            if (contactEntity.isBlocked) return@withContext
            val updated = contactEntity.toModel().copy(presence = PresenceStatus.Online)
            contactDao.upsert(updated.toEntity())
        }
    }

    suspend fun exportAttachment(storagePath: String, displayName: String, freshCopy: Boolean): Uri? {
        if (storagePath.isBlank()) return null
        return withContext(ioDispatcher) {
            runCatching {
                attachmentStore.pruneCache()
                attachmentStore.exportToCache(
                    storagePath = storagePath,
                    displayName = displayName.ifBlank { "attachment" },
                    reuseExisting = !freshCopy
                )
            }.onFailure { err ->
                Log.w(TAG, "Unable to export attachment $storagePath", err)
            }.getOrNull()
        }
    }

    // State for tracking lookup results - exposed for UI validation feedback
    private val _lookupResult = MutableStateFlow<LookupState>(LookupState.Idle)
    val lookupResult: StateFlow<LookupState> = _lookupResult.asStateFlow()

    suspend fun validatePeerCode(peerCode: String) {
        withContext(ioDispatcher) {
            val trimmedCode = peerCode.trim().uppercase()
            if (trimmedCode.isEmpty()) {
                _lookupResult.value = LookupState.Invalid("Enter a valid code")
                return@withContext
            }
            val selfCode = resolveIdentityCode()
            if (trimmedCode.equals(selfCode, ignoreCase = true)) {
                _lookupResult.value = LookupState.Invalid("Cannot add your own code")
                return@withContext
            }
            // Check if already a contact
            val existing = contactDao.getById(trimmedCode)
            if (existing != null) {
                _lookupResult.value = LookupState.Invalid("Contact already exists")
                return@withContext
            }
            _lookupResult.value = LookupState.Searching
            messageTransport?.lookupPeer(trimmedCode)
        }
    }

    fun clearLookupResult() {
        _lookupResult.value = LookupState.Idle
    }

    suspend fun createContact(displayName: String, peerCode: String) {
        withContext(ioDispatcher) {
            val trimmedName = displayName.trim()
            val trimmedCode = peerCode.trim()
            if (trimmedName.isEmpty() || trimmedCode.isEmpty()) return@withContext

            val selfCode = resolveIdentityCode()
            require(!trimmedCode.equals(selfCode, ignoreCase = true)) { "Cannot add your own code." }

            val normalizedId = trimmedCode.uppercase()
            
            // Check if contact already exists
            val existing = contactDao.getById(normalizedId)
            if (existing != null) {
                Log.w(TAG, "Contact $normalizedId already exists")
                return@withContext
            }

            // Create contact with PendingOutgoing status - they need to accept
            val contact = Contact(
                id = normalizedId,
                displayName = trimmedName,
                presence = PresenceStatus.Offline,
                lastSeen = "",
                status = ContactStatus.PendingOutgoing
            )
            
            contactDao.upsert(contact.toEntity())
            
            // Send contact request to peer - use our peer code as identifier
            val myCode = resolveIdentityCode()
            messageTransport?.sendContactRequest(normalizedId, myCode)
            
            Log.i(TAG, "Sent contact request to $normalizedId")
        }
    }

    suspend fun acceptContactRequest(contactId: String) {
        withContext(ioDispatcher) {
            val existing = contactDao.getById(contactId)?.toModel() ?: return@withContext
            if (existing.status != ContactStatus.PendingIncoming) return@withContext

            // Update to Accepted
            contactDao.setStatus(contactId, ContactStatus.Accepted.name)
            
            // Create conversation if not exists
            val conversationId = "chat-$contactId"
            val existingConvo = conversationDao.getById(conversationId)
            if (existingConvo == null) {
                val conversation = Conversation(
                    id = conversationId,
                    peerId = contactId,
                    title = existing.displayName,
                    lastMessagePreview = "",
                    lastActivityEpochMillis = System.currentTimeMillis(),
                    unreadCount = 0,
                    isSecure = true
                )
                conversationDao.upsert(conversation.toEntity())
            }
            
            // Send acceptance response to peer
            val myCode = resolveIdentityCode()
            messageTransport?.sendContactResponse(contactId, accepted = true, displayName = myCode)
            
            Log.i(TAG, "Accepted contact request from $contactId")
        }
    }

    suspend fun rejectContactRequest(contactId: String) {
        withContext(ioDispatcher) {
            val existing = contactDao.getById(contactId)?.toModel() ?: return@withContext
            if (existing.status != ContactStatus.PendingIncoming) return@withContext

            // Delete the contact
            contactDao.deleteById(contactId)
            
            // Send rejection response to peer
            messageTransport?.sendContactResponse(contactId, accepted = false, displayName = "")
            
            Log.i(TAG, "Rejected contact request from $contactId")
        }
    }

    private suspend fun handleContactEvent(event: ContactEvent) {
        withContext(ioDispatcher) {
            when (event) {
                is ContactEvent.RequestReceived -> {
                    val existing = contactDao.getById(event.from)
                    if (existing != null) {
                        // If we already sent them a request, auto-accept both sides
                        if (existing.toModel().status == ContactStatus.PendingOutgoing) {
                            contactDao.setStatus(event.from, ContactStatus.Accepted.name)
                            // Create conversation
                            val conversationId = "chat-${event.from}"
                            val existingConvo = conversationDao.getById(conversationId)
                            if (existingConvo == null) {
                                conversationDao.upsert(Conversation(
                                    id = conversationId,
                                    peerId = event.from,
                                    title = existing.displayName,
                                    lastMessagePreview = "",
                                    lastActivityEpochMillis = System.currentTimeMillis(),
                                    unreadCount = 0,
                                    isSecure = true
                                ).toEntity())
                            }
                            val myCode = resolveIdentityCode()
                            messageTransport?.sendContactResponse(event.from, accepted = true, displayName = myCode)
                            Log.i(TAG, "Auto-accepted mutual contact request from ${event.from}")
                        }
                        return@withContext
                    }
                    
                    // Create incoming request
                    val contact = Contact(
                        id = event.from,
                        displayName = event.displayName.ifBlank { event.from },
                        presence = PresenceStatus.Offline,
                        lastSeen = "",
                        status = ContactStatus.PendingIncoming
                    )
                    contactDao.upsert(contact.toEntity())
                    Log.i(TAG, "Received contact request from ${event.from}")
                }
                
                is ContactEvent.ResponseReceived -> {
                    val existing = contactDao.getById(event.from) ?: return@withContext
                    if (event.accepted) {
                        contactDao.setStatus(event.from, ContactStatus.Accepted.name)
                        // Update display name if provided
                        if (event.displayName.isNotBlank()) {
                            val updated = existing.toModel().copy(displayName = event.displayName)
                            contactDao.upsert(updated.toEntity())
                        }
                        // Create conversation
                        val conversationId = "chat-${event.from}"
                        val existingConvo = conversationDao.getById(conversationId)
                        if (existingConvo == null) {
                            conversationDao.upsert(Conversation(
                                id = conversationId,
                                peerId = event.from,
                                title = existing.displayName,
                                lastMessagePreview = "",
                                lastActivityEpochMillis = System.currentTimeMillis(),
                                unreadCount = 0,
                                isSecure = true
                            ).toEntity())
                        }
                        Log.i(TAG, "Contact ${event.from} accepted our request")
                    } else {
                        // They rejected - remove the contact
                        contactDao.deleteById(event.from)
                        Log.i(TAG, "Contact ${event.from} rejected our request")
                    }
                }
                
                is ContactEvent.LookupResult -> {
                    _lookupResult.value = if (event.found) {
                        LookupState.Found(event.peerCode)
                    } else {
                        LookupState.Invalid("User not found")
                    }
                }
            }
        }
    }

    suspend fun placeCall(contactId: String) {
        withContext(ioDispatcher) {
            val contact = contactDao.getById(contactId)?.toModel() ?: return@withContext
            if (contact.isBlocked) return@withContext
            callCoordinator?.let { coordinator ->
                appScope.launch { coordinator.startCall(contact.id, contact.displayName) }
            }
        }
    }

    suspend fun endCall() {
        withContext(ioDispatcher) {
            callCoordinator?.endCall()
        }
    }

    suspend fun acceptIncomingCall() {
        withContext(ioDispatcher) {
            callCoordinator?.acceptCall()
        }
    }

    suspend fun declineIncomingCall() {
        withContext(ioDispatcher) {
            callCoordinator?.rejectCall()
        }
    }

    suspend fun requestSync() {
        withContext(ioDispatcher) {
            try {
                messageTransport?.requestQueueSync()
            } catch (err: Exception) {
                Log.w(TAG, "Failed to request sync", err)
            }
        }
    }

    suspend fun reset() {
        withContext(ioDispatcher) {
            messageDao.clear()
            conversationDao.clear()
            contactDao.clear()
            callLogDao.clear()
            draftStore.update("")
            val seed = SeedData.initialState()
            contactDao.insertAll(seed.contacts.map { it.toEntity() })
            conversationDao.insertAll(seed.conversations.values.map { it.toEntity() })
            val messages = seed.messages.values.flatten().map { it.toEntity() }
            if (messages.isNotEmpty()) {
                messageDao.insertAll(messages)
            }
            callLogDao.insertAll(seed.callLogs.map { it.toEntity() })
        }
    }

    suspend fun setContactBlocked(contactId: String, blocked: Boolean) {
        withContext(ioDispatcher) { contactDao.setBlocked(contactId, blocked) }
    }

    suspend fun setContactMuted(contactId: String, muted: Boolean) {
        withContext(ioDispatcher) { contactDao.setMuted(contactId, muted) }
    }

    private suspend fun resolveIdentityCode(): String {
        val cached = identityCodeState.value
        return if (cached.isNotBlank()) cached else identityStore.ensureIdentityCode()
    }

    private suspend fun handleIncomingMessage(incoming: IncomingMessage) {
        withContext(ioDispatcher) {
            val peerId = incoming.peerId.uppercase()
            val payload = incoming.payload
            val messageId = payload.messageId.ifBlank { UUID.randomUUID().toString() }

                if (payload.mimeType == READ_RECEIPT_MIME) {
                    messageDao.updateStatus(messageId, MessageStatus.Read.name)
                    return@withContext
                }

                if (payload.mimeType == TYPING_MIME) {
                    val convoId = payload.conversationHint.ifBlank { "chat-$peerId" }
                    _typingState.update { it + convoId }
                    appScope.launch {
                        delay(3000)
                        _typingState.update { state -> state - convoId }
                    }
                    return@withContext
                }

            val contact = contactDao.getById(peerId)?.toModel() ?: Contact(
                id = peerId,
                displayName = peerId,
                presence = PresenceStatus.Online,
                lastSeen = "just now"
            ).also { contactDao.upsert(it.toEntity()) }

            if (contact.isBlocked) return@withContext

            if (payload.mimeType == CALL_MIME_TYPE) {
                callCoordinator?.let { coordinator ->
                    appScope.launch { coordinator.handleSignal(peerId, contact.displayName, payload) }
                }
                return@withContext
            }

            val hintedId = payload.conversationHint.ifBlank { "chat-$peerId" }
            val conversationId = if (hintedId.startsWith("chat-", ignoreCase = true)) {
                "chat-" + hintedId.substringAfter("chat-", missingDelimiterValue = peerId).uppercase()
            } else {
                hintedId
            }
            val baseConversation = conversationDao.getById(conversationId)?.toModel()
                ?: conversationDao.getByPeerId(peerId)?.toModel()
                ?: Conversation(
                    id = conversationId,
                    peerId = peerId,
                    title = contact.displayName,
                    lastMessagePreview = "",
                    lastActivityEpochMillis = 0L,
                    unreadCount = 0,
                    isSecure = true
                )

            var resolvedBody = payload.body
            var attachmentPath: String? = null
            var attachmentMimeType: String? = null
            var attachmentDisplayName: String? = null

            when (payload.mimeType) {
                MEDIA_WRAPPER_MIME -> {
                    // Persist attachment locally and replace preview text for UI/notifications.
                    val envelope = runCatching { json.decodeFromString<MediaEnvelope>(payload.body) }
                        .onFailure { err -> Log.w(TAG, "Failed to decode media envelope for $messageId", err) }
                        .getOrNull()
                    if (envelope != null) {
                        val fallbackName = envelope.fileName.ifBlank { "attachment" }
                        resolvedBody = envelope.caption ?: attachmentPreview(fallbackName)
                        val stored = runCatching {
                            val bytes = envelope.data.fromBase64()
                            attachmentStore.fromBytes(
                                messageId = messageId,
                                fileName = fallbackName,
                                mimeType = envelope.mimeType,
                                data = bytes
                            )
                        }.onFailure { err ->
                            Log.w(TAG, "Unable to persist attachment for $messageId", err)
                        }.getOrNull()

                        stored?.let { file ->
                            attachmentPath = file.storagePath
                            attachmentMimeType = file.mimeType
                            attachmentDisplayName = file.displayName
                            if (envelope.caption.isNullOrBlank()) {
                                resolvedBody = attachmentPreview(file.displayName)
                            }
                        }
                    } else {
                        resolvedBody = attachmentPreview("Attachment")
                    }
                }

                MEDIA_CHUNK_MIME -> {
                    val envelope = runCatching { json.decodeFromString<MediaChunkEnvelope>(payload.body) }
                        .onFailure { err -> Log.w(TAG, "Failed to decode media chunk for $messageId", err) }
                        .getOrNull()
                    if (envelope == null) {
                        Log.w(TAG, "Dropping malformed media chunk for $messageId")
                        return@withContext
                    }
                    val assembled = runCatching { processChunkEnvelope(envelope) }
                        .onFailure { err -> Log.w(TAG, "Failed assembling chunks for $messageId", err) }
                        .getOrNull()
                    if (assembled == null) {
                        // Still waiting for remaining chunks.
                        return@withContext
                    }
                    attachmentPath = assembled.file.storagePath
                    attachmentMimeType = assembled.file.mimeType
                    attachmentDisplayName = assembled.file.displayName
                    resolvedBody = assembled.caption ?: attachmentPreview(assembled.file.displayName)
                }
            }

            val message = Message(
                id = messageId,
                conversationId = baseConversation.id,
                senderId = peerId,
                body = resolvedBody,
                sentAtEpochMillis = payload.sentAtEpochMillis,
                status = MessageStatus.Delivered,
                attachmentPath = attachmentPath,
                attachmentMimeType = attachmentMimeType,
                attachmentDisplayName = attachmentDisplayName
            )

            messageDao.insert(message.toEntity())

            val updatedConversation = baseConversation.copy(
                lastMessagePreview = message.body,
                lastActivityEpochMillis = message.sentAtEpochMillis,
                unreadCount = baseConversation.unreadCount + 1
            )

            conversationDao.upsert(updatedConversation.toEntity())
            if (!contact.isMuted) {
                messageNotifier?.showIncomingMessage(
                    conversationId = updatedConversation.id,
                    title = contact.displayName,
                    body = message.body
                )
            }
        }
    }

    private suspend fun observeCallSessions(coordinator: CallCoordinator) {
        var previous = CallSessionState()
        coordinator.state.collect { latest ->
            if (previous.status != CallStatus.Idle && latest.status == CallStatus.Idle) {
                previous.peerId?.let { peerId ->
                    val durationSeconds = ((System.currentTimeMillis() - (previous.startedAtMillis ?: System.currentTimeMillis())) / 1000)
                        .coerceAtLeast(0)
                    val log = CallLog(
                        id = UUID.randomUUID().toString(),
                        contactId = peerId,
                        startedAtMillis = previous.startedAtMillis ?: System.currentTimeMillis(),
                        durationSeconds = durationSeconds.toInt(),
                        direction = previous.direction ?: CallDirection.Outgoing
                    )
                    callLogDao.insert(log.toEntity())
                }
            }
            previous = latest
        }
    }

    private suspend fun buildConversationSnapshot(
        conversationId: String,
        preview: String,
        timestamp: Long
    ): Conversation {
        val existing = conversationDao.getById(conversationId)?.toModel()
        val fallbackPeer = existing?.peerId ?: conversationId.removePrefix("chat-")
        val contactName = contactDao.getById(existing?.peerId ?: fallbackPeer)?.displayName
        val baseTitle = when {
            existing != null -> existing.title
            !contactName.isNullOrBlank() -> contactName
            else -> fallbackPeer.replaceFirstChar { ch ->
                if (ch.isLowerCase()) ch.titlecase(Locale.getDefault()) else ch.toString()
            }
        }

        val baseConversation = existing ?: Conversation(
            id = conversationId,
            peerId = fallbackPeer,
            title = baseTitle,
            lastMessagePreview = preview,
            lastActivityEpochMillis = timestamp,
            unreadCount = 0,
            isSecure = true
        )

        return baseConversation.copy(
            title = contactName ?: baseConversation.title,
            lastMessagePreview = preview,
            lastActivityEpochMillis = timestamp,
            unreadCount = 0
        )
    }

    private suspend fun transmitPayload(peerId: String, payload: TransportPayload, messageId: String) {
        val transport = messageTransport
        if (transport == null) {
            messageDao.updateStatus(messageId, MessageStatus.Delivered.name)
        } else {
            try {
                transport.sendMessage(peerId, payload)
                messageDao.updateStatus(messageId, MessageStatus.Delivered.name)
            } catch (err: Exception) {
                messageDao.updateStatus(messageId, MessageStatus.Failed.name)
                throw err
            }
        }
    }

    private fun attachmentPreview(displayName: String?): String =
        "\uD83D\uDCCE ${displayName ?: "Attachment"}"

    private fun ByteArray.toBase64(): String =
        Base64.encodeToString(this, Base64.NO_WRAP or Base64.URL_SAFE)

    private fun String.fromBase64(): ByteArray =
        Base64.decode(this, Base64.NO_WRAP or Base64.URL_SAFE)

    private fun chunkify(bytes: ByteArray): List<ByteArray> {
        if (bytes.size <= MAX_MEDIA_CHUNK_BYTES) return listOf(bytes)
        val chunks = mutableListOf<ByteArray>()
        var offset = 0
        while (offset < bytes.size) {
            val end = min(bytes.size, offset + MAX_MEDIA_CHUNK_BYTES)
            chunks.add(bytes.copyOfRange(offset, end))
            offset = end
        }
        return chunks
    }

    private suspend fun processChunkEnvelope(envelope: MediaChunkEnvelope): ChunkAssembly? {
        val now = System.currentTimeMillis()
        evictExpiredPendingChunks(now)

        if (envelope.totalChunks <= 0) {
            Log.w(TAG, "Ignoring chunk with invalid total count for ${envelope.messageId}")
            return null
        }
        if (envelope.totalChunks > MAX_MEDIA_CHUNKS) {
            Log.w(TAG, "Rejecting chunk for ${envelope.messageId} exceeding cap: ${envelope.totalChunks}")
            pendingMediaChunks.remove(envelope.messageId)
            return null
        }

        val key = envelope.messageId
        val candidate = pendingMediaChunks[key]
        val accumulator = if (candidate == null || candidate.totalChunks != envelope.totalChunks) {
            if (candidate != null) {
                Log.w(TAG, "Resetting chunk buffer for $key due to metadata mismatch")
            }
            PendingMedia(
                messageId = key,
                fileName = envelope.fileName.ifBlank { "attachment" },
                mimeType = envelope.mimeType,
                sizeBytes = envelope.sizeBytes,
                totalChunks = envelope.totalChunks
            ).also { pendingMediaChunks[key] = it }
        } else {
            candidate
        }

        accumulator.registerChunk(
            index = envelope.chunkIndex,
            data = envelope.data.fromBase64(),
            incomingCaption = envelope.caption,
            receivedAtMillis = now
        )

        if (!accumulator.isComplete()) {
            return null
        }

        pendingMediaChunks.remove(key)
        val assembled = accumulator.buildPayload()
        val stored = attachmentStore.fromBytes(
            messageId = key,
            fileName = accumulator.fileName,
            mimeType = accumulator.mimeType,
            data = assembled
        )
        return ChunkAssembly(file = stored, caption = accumulator.caption)
    }

    private data class PendingMedia(
        val messageId: String,
        val fileName: String,
        val mimeType: String,
        val sizeBytes: Long,
        val totalChunks: Int,
        private val chunks: Array<ByteArray?> = arrayOfNulls(maxOf(1, totalChunks))
    ) {
        private var receivedChunks = 0
        var caption: String? = null
            private set
        private var lastTouchedAtMillis: Long = System.currentTimeMillis()

        fun registerChunk(index: Int, data: ByteArray, incomingCaption: String?, receivedAtMillis: Long) {
            if (index !in chunks.indices) {
                Log.w(TAG, "Ignoring out-of-range chunk $index for $messageId")
                return
            }
            if (chunks[index] == null) {
                chunks[index] = data
                receivedChunks++
            }
            if (!incomingCaption.isNullOrBlank()) {
                caption = incomingCaption
            }
            lastTouchedAtMillis = receivedAtMillis
        }

        fun isComplete(): Boolean = receivedChunks == chunks.size

        fun buildPayload(): ByteArray {
            val estimatedSize = sizeBytes.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
            val buffer = ByteArrayOutputStream(estimatedSize)
            chunks.forEach { chunk ->
                buffer.write(chunk ?: ByteArray(0))
            }
            return buffer.toByteArray()
        }

        fun isExpired(referenceTimeMillis: Long, ttlMillis: Long): Boolean =
            referenceTimeMillis - lastTouchedAtMillis > ttlMillis
    }

    private data class ChunkAssembly(
        val file: AttachmentStore.AttachmentFile,
        val caption: String?
    )

    companion object {
        private const val MEDIA_WRAPPER_MIME = "application/bizur-media+json"
        private const val MEDIA_CHUNK_MIME = "application/bizur-media-chunk+json"
        private const val READ_RECEIPT_MIME = "application/vnd.bizur.read-receipt"
        private const val TYPING_MIME = "application/vnd.bizur.typing"
        private const val MAX_MEDIA_CHUNK_BYTES = 24 * 1024
        private const val MAX_MEDIA_CHUNKS = 500
        private const val PENDING_CHUNK_TTL_MILLIS = 5 * 60 * 1000L
        private const val TAG = "BizurRepository"
    }
}

data class MediaSendProgress(
    val messageId: String,
    val displayName: String?,
    val sentChunks: Int,
    val totalChunks: Int
)

data class BizurDataState(
    val identityCode: String,
    val contacts: List<com.bizur.android.model.Contact>,
    val conversations: Map<String, Conversation>,
    val messages: Map<String, List<Message>>,
    val callLogs: List<CallLog>,
    val draft: String
) {
    companion object {
        fun empty(identityCode: String = "") = BizurDataState(
            identityCode = identityCode,
            contacts = emptyList(),
            conversations = emptyMap(),
            messages = emptyMap(),
            callLogs = emptyList(),
            draft = ""
        )
    }
}

sealed interface LookupState {
    object Idle : LookupState
    object Searching : LookupState
    data class Found(val peerCode: String) : LookupState
    data class Invalid(val reason: String) : LookupState
}
