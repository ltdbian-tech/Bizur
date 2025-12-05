package com.bizur.android

import android.app.Application
import android.content.Context
import com.bizur.android.call.CallCoordinator
import com.bizur.android.crypto.IdentityManager
import com.bizur.android.crypto.SignalStore
import com.bizur.android.data.BizurRepository
import com.bizur.android.data.DraftStore
import com.bizur.android.data.IdentityStore
import com.bizur.android.data.SeedData
import com.bizur.android.data.local.BizurDatabase
import com.bizur.android.media.AttachmentStore
import com.bizur.android.notifications.MessageNotifier
import com.bizur.android.push.PushRegistrar
import com.bizur.android.push.PushRegistrationClient
import com.bizur.android.transport.QueueDrainWorker
import com.bizur.android.transport.TransportConfig
import com.bizur.android.transport.WebRtcTransport
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json

class BizurApplication : Application() {
    lateinit var container: BizurContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = BizurContainer(applicationContext)
    }
}

class BizurContainer(
    context: Context,
    ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) {
    private val applicationScope = CoroutineScope(SupervisorJob() + ioDispatcher)
    private val transportConfig = TransportConfig()
    private val json = Json { ignoreUnknownKeys = true }
    private val database = BizurDatabase.build(context, applicationScope) { SeedData.initialState() }
    private val draftStore = DraftStore(context)
    private val identityStore = IdentityStore(context)
    private val attachmentStore = AttachmentStore(context)
    private val selfPeerCode = runBlocking { identityStore.ensureIdentityCode() }
    val identityManager = IdentityManager(context)
    val signalStore = SignalStore(context, identityManager)
    private val notifier = MessageNotifier(context)
    val transport = WebRtcTransport(
        context = context,
        scope = applicationScope,
        identityManager = identityManager,
        signalStore = signalStore,
        selfPeerCode = selfPeerCode,
        config = transportConfig,
        json = json
    )
    private val pushClient = PushRegistrationClient(transportConfig.pushServiceUrl, json)
    val pushRegistrar = PushRegistrar(
        context = context,
        identityStore = identityStore,
        identityManager = identityManager,
        client = pushClient,
        scope = applicationScope
    )
    private val callCoordinator = CallCoordinator(
        context = context,
        transport = transport,
        messaging = transport,
        scope = applicationScope
    )

    val repository: BizurRepository = BizurRepository(
        contactDao = database.contactDao(),
        conversationDao = database.conversationDao(),
        messageDao = database.messageDao(),
        callLogDao = database.callLogDao(),
        draftStore = draftStore,
        identityStore = identityStore,
        ioDispatcher = ioDispatcher,
        externalScope = applicationScope,
        messageTransport = transport,
        messageNotifier = notifier,
        callCoordinator = callCoordinator,
        attachmentStore = attachmentStore
    )

    init {
        QueueDrainWorker.schedule(context)
        pushRegistrar.warmStart()
    }
}
