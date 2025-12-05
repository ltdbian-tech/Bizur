package com.bizur.android.data

import android.content.Context
import android.util.Base64
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.bizur.android.data.local.BizurDatabase
import com.bizur.android.data.local.toEntity
import com.bizur.android.media.AttachmentStore
import com.bizur.android.model.Contact
import com.bizur.android.model.PresenceStatus
import com.bizur.android.transport.ContactEvent
import com.bizur.android.transport.IncomingMessage
import com.bizur.android.transport.MediaChunkEnvelope
import com.bizur.android.transport.MediaEnvelope
import com.bizur.android.transport.MessageTransport
import com.bizur.android.transport.TransportChannel
import com.bizur.android.transport.TransportPayload
import com.bizur.android.transport.TransportStatus
import java.io.File
import org.junit.Assert.assertEquals
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MediaTransportInstrumentationTest {
    private lateinit var context: Context
    private lateinit var database: BizurDatabase
    private lateinit var repository: BizurRepository
    private lateinit var attachmentStore: AttachmentStore
    private lateinit var transport: TestMessageTransport
    private lateinit var scope: CoroutineScope
    private val json = Json { ignoreUnknownKeys = true }

    @Before
    fun setUp() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        File(context.filesDir, "media").deleteRecursively()
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        database = Room.inMemoryDatabaseBuilder(context, BizurDatabase::class.java)
            .allowMainThreadQueries()
            .build()

        val draftStore = DraftStore(context)
        val identityStore = IdentityStore(context)
        attachmentStore = AttachmentStore(context)
        transport = TestMessageTransport()

        repository = BizurRepository(
            contactDao = database.contactDao(),
            conversationDao = database.conversationDao(),
            messageDao = database.messageDao(),
            callLogDao = database.callLogDao(),
            draftStore = draftStore,
            identityStore = identityStore,
            ioDispatcher = Dispatchers.IO,
            externalScope = scope,
            messageTransport = transport,
            messageNotifier = null,
            callCoordinator = null,
            attachmentStore = attachmentStore
        )

        runBlocking {
            val contact = Contact(
                id = TEST_PEER_ID,
                displayName = "Media Peer",
                presence = PresenceStatus.Online,
                lastSeen = "just now"
            )
            database.contactDao().upsert(contact.toEntity())
        }
    }

    @After
    fun tearDown() {
        scope.cancel()
        runBlocking { database.clearAllTables() }
        database.close()
    }

    @Test
    fun mediaPayload_persistsAttachmentAndPreview() = runBlocking {
        val payloadBytes = ByteArray(72_000) { index -> (index % 253).toByte() }
        val chunkSize = 8_192
        val chunkCount = (payloadBytes.size + chunkSize - 1) / chunkSize
        val messageId = "media-${System.currentTimeMillis()}"

        repeat(chunkCount) { index ->
            val start = index * chunkSize
            val end = minOf(payloadBytes.size, start + chunkSize)
            val chunkBytes = payloadBytes.copyOfRange(start, end)
            val envelope = MediaChunkEnvelope(
                messageId = messageId,
                fileName = "photo.png",
                mimeType = "image/png",
                sizeBytes = payloadBytes.size.toLong(),
                caption = if (index == 0) null else null,
                chunkIndex = index,
                totalChunks = chunkCount,
                data = Base64.encodeToString(chunkBytes, Base64.NO_WRAP or Base64.URL_SAFE)
            )
            val payload = TransportPayload(
                messageId = messageId,
                conversationHint = "",
                body = json.encodeToString(envelope),
                sentAtEpochMillis = System.currentTimeMillis(),
                mimeType = "application/bizur-media-chunk+json"
            )

            transport.emit(
                IncomingMessage(
                    peerId = TEST_PEER_ID,
                    payload = payload,
                    via = TransportChannel.DataChannel
                )
            )
        }

        val storedMessage = withTimeout(5_000) {
            database.messageDao().observeMessages()
                .map { items -> items.firstOrNull { it.id == messageId } }
                .filterNotNull()
                .first()
        }

        assertEquals(TEST_PEER_ID, storedMessage.senderId)
        assertEquals("chat-$TEST_PEER_ID", storedMessage.conversationId)
        assertEquals("\uD83D\uDCCE photo.png", storedMessage.body)
        assertEquals("image/png", storedMessage.attachmentMimeType)
        assertEquals("photo.png", storedMessage.attachmentDisplayName)
        val savedPath = requireNotNull(storedMessage.attachmentPath)
        val decryptedBytes = attachmentStore.read(savedPath)
        assertEquals(payloadBytes.toList(), decryptedBytes.toList())

        val conversation = withTimeout(5_000) {
            database.conversationDao().observeConversations()
                .map { list -> list.firstOrNull { it.peerId == TEST_PEER_ID } }
                .filterNotNull()
                .first()
        }
        assertEquals(storedMessage.body, conversation.lastMessagePreview)
    }

    @Test
    fun singleEnvelopeMedia_persistsAttachment() = runBlocking {
        val payloadBytes = ByteArray(4_096) { index -> (index * 17 % 251).toByte() }
        val messageId = "media-single-${System.currentTimeMillis()}"
        val envelope = MediaEnvelope(
            messageId = messageId,
            fileName = "note.txt",
            mimeType = "text/plain",
            sizeBytes = payloadBytes.size.toLong(),
            caption = null,
            data = Base64.encodeToString(payloadBytes, Base64.NO_WRAP or Base64.URL_SAFE)
        )

        val payload = TransportPayload(
            messageId = messageId,
            conversationHint = "",
            body = json.encodeToString(envelope),
            sentAtEpochMillis = System.currentTimeMillis(),
            mimeType = "application/bizur-media+json"
        )

        transport.emit(
            IncomingMessage(
                peerId = TEST_PEER_ID,
                payload = payload,
                via = TransportChannel.DataChannel
            )
        )

        val storedMessage = withTimeout(5_000) {
            database.messageDao().observeMessages()
                .map { items -> items.firstOrNull { it.id == messageId } }
                .filterNotNull()
                .first()
        }

        assertEquals(TEST_PEER_ID, storedMessage.senderId)
        assertEquals("chat-$TEST_PEER_ID", storedMessage.conversationId)
        assertEquals("\uD83D\uDCCE note.txt", storedMessage.body)
        assertEquals("text/plain", storedMessage.attachmentMimeType)
        assertEquals("note.txt", storedMessage.attachmentDisplayName)
        val savedPath = requireNotNull(storedMessage.attachmentPath)
        val decryptedBytes = attachmentStore.read(savedPath)
        assertEquals(payloadBytes.toList(), decryptedBytes.toList())
    }

    private class TestMessageTransport : MessageTransport {
        private val _status = MutableStateFlow(TransportStatus.Connected)
        private val _incoming = MutableSharedFlow<IncomingMessage>()
        private val _contactEvents = MutableSharedFlow<ContactEvent>()

        override val status: StateFlow<TransportStatus> = _status
        override val incomingMessages: Flow<IncomingMessage> = _incoming
        override val contactEvents: Flow<ContactEvent> = _contactEvents

        override suspend fun start() = Unit

        override suspend fun sendMessage(peerId: String, payload: TransportPayload) = Unit

        override suspend fun requestQueueSync() = Unit

        override suspend fun sendContactRequest(peerId: String, displayName: String) = Unit

        override suspend fun sendContactResponse(peerId: String, accepted: Boolean, displayName: String) = Unit

        override suspend fun lookupPeer(peerCode: String) = Unit

        override fun close() {
            _status.value = TransportStatus.Disconnected
        }

        suspend fun emit(message: IncomingMessage) {
            _incoming.emit(message)
        }
    }

    companion object {
        private const val TEST_PEER_ID = "PEER-MEDIA"
    }
}
