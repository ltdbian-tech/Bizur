package com.bizur.android.ui

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SupervisorAccount
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.NavType
import androidx.navigation.navArgument
import com.bizur.android.LocalBizurViewModelFactory
import com.bizur.android.data.LookupState
import com.bizur.android.ui.theme.BizurTheme
import com.bizur.android.ui.components.CallStatusBanner
import com.bizur.android.ui.screens.CallHistoryScreen
import com.bizur.android.ui.screens.ChatDetailScreen
import com.bizur.android.ui.screens.ChatListScreen
import com.bizur.android.ui.screens.ContactsScreen
import com.bizur.android.ui.screens.SettingsScreen
import com.bizur.android.model.Message
import com.bizur.android.viewmodel.BizurViewModel
import com.bizur.android.transport.TransportStatus
import kotlinx.coroutines.launch

private enum class BizurDestination(val route: String, val label: String) {
    Chats("chats", "Chats"),
    Contacts("contacts", "Contacts"),
    Calls("calls", "Calls"),
    Settings("settings", "Settings")
}

private const val CHAT_DETAIL_ROUTE = "chat/{conversationId}"
private fun chatDetailRoute(conversationId: String) = "chat/$conversationId"

@Composable
fun BizurApp(startConversationId: String? = null) {
    val navController = rememberNavController()
    val factory = LocalBizurViewModelFactory.current
    val viewModel: BizurViewModel = viewModel(factory = factory)
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val callState by viewModel.callState.collectAsStateWithLifecycle()
    val mediaSendProgress by viewModel.mediaSendProgress.collectAsStateWithLifecycle()
    val transportStatus by viewModel.transportStatus.collectAsStateWithLifecycle()
    val typingState by viewModel.typingState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var micPermissionGranted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == android.content.pm.PackageManager.PERMISSION_GRANTED
        )
    }
    var pendingMicAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    val micPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        micPermissionGranted = granted
        if (granted) {
            pendingMicAction?.invoke()
        }
        pendingMicAction = null
    }

    val needsNotificationPermission = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
    var notificationsGranted by remember {
        mutableStateOf(
            !needsNotificationPermission || NotificationManagerCompat.from(context).areNotificationsEnabled()
        )
    }
    val notificationLauncher = if (needsNotificationPermission) {
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            notificationsGranted = granted
        }
    } else {
        null
    }

    DisposableEffect(lifecycleOwner, context, needsNotificationPermission) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                micPermissionGranted = ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.RECORD_AUDIO
                ) == android.content.pm.PackageManager.PERMISSION_GRANTED
                notificationsGranted = !needsNotificationPermission || NotificationManagerCompat.from(context).areNotificationsEnabled()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(needsNotificationPermission, notificationsGranted) {
        if (needsNotificationPermission && !notificationsGranted) {
            notificationLauncher?.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    var selectedConversationId by rememberSaveable { mutableStateOf<String?>(startConversationId) }
    var pendingAttachmentConversationId by rememberSaveable { mutableStateOf<String?>(null) }
    val attachmentScope = rememberCoroutineScope()
    var startConversationHandled by rememberSaveable { mutableStateOf(false) }

    val attachmentLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val conversationId = pendingAttachmentConversationId
        if (uri != null && conversationId != null) {
            val resolver = context.contentResolver
            try {
                resolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } catch (_: SecurityException) {
                // If persistable permission isn't supported, continue with transient grant.
            }
            val mime = resolver.getType(uri)
            viewModel.sendMediaMessage(conversationId, uri, mime)
        }
        pendingAttachmentConversationId = null
    }

    LaunchedEffect(uiState.conversations, selectedConversationId) {
        if (selectedConversationId == null && uiState.conversations.isNotEmpty()) {
            selectedConversationId = uiState.conversations.first().id
        }
    }

    LaunchedEffect(startConversationId, uiState.conversations) {
        val convoId = startConversationId
        if (!startConversationHandled && convoId != null) {
            startConversationHandled = true
            selectedConversationId = convoId
            navController.navigate(chatDetailRoute(convoId))
        }
    }

    val ensureMicPermission: ((() -> Unit) -> Unit) = remember(micPermissionGranted, micPermissionLauncher) {
        { action ->
            if (micPermissionGranted) {
                action()
            } else {
                pendingMicAction = action
                micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            }
        }
    }

    val openAppSettings = remember(context) {
        {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.fromParts("package", context.packageName, null)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        }
    }

    val requestMicPermission = remember(micPermissionGranted, micPermissionLauncher) {
        {
            if (!micPermissionGranted) {
                micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            }
        }
    }

    val requestNotificationPermission = remember(
        notificationsGranted,
        notificationLauncher,
        needsNotificationPermission,
        openAppSettings
    ) {
        {
            if (!notificationsGranted) {
                if (needsNotificationPermission) {
                    notificationLauncher?.launch(Manifest.permission.POST_NOTIFICATIONS) ?: openAppSettings()
                } else {
                    openAppSettings()
                }
            }
        }
    }

    val destinations = remember { BizurDestination.entries }
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route ?: BizurDestination.Chats.route
    val isChatFlow = currentRoute == BizurDestination.Chats.route || currentRoute?.startsWith("chat") == true || currentRoute == CHAT_DETAIL_ROUTE

    val systemDark = isSystemInDarkTheme()
    var useDarkTheme by rememberSaveable(systemDark) { mutableStateOf(systemDark) }

    val totalUnread = remember(uiState.conversations) {
        uiState.conversations.sumOf { it.unreadCount }
    }

    Scaffold(
        bottomBar = {
            NavigationBar {
                destinations.forEach { destination ->
                    val icon = when (destination) {
                        BizurDestination.Chats -> Icons.AutoMirrored.Filled.Chat
                        BizurDestination.Contacts -> Icons.Filled.SupervisorAccount
                        BizurDestination.Calls -> Icons.Filled.Call
                        BizurDestination.Settings -> Icons.Filled.Settings
                    }
                    val showBadge = destination == BizurDestination.Chats && totalUnread > 0
                    NavigationBarItem(
                        selected = if (destination == BizurDestination.Chats) isChatFlow else currentRoute == destination.route,
                        onClick = {
                            navController.navigate(destination.route) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = {
                            if (showBadge) {
                                BadgedBox(badge = {
                                    Badge { Text(totalUnread.coerceAtMost(99).toString()) }
                                }) {
                                    Icon(icon, contentDescription = destination.label)
                                }
                            } else {
                                Icon(icon, contentDescription = destination.label)
                            }
                        },
                        label = { Text(destination.label) }
                    )
                }
            }
        }
    ) { paddingValues ->
        Box(modifier = Modifier.padding(paddingValues)) {
            BizurTheme(useDarkTheme = useDarkTheme) {
                NavHost(
                    navController = navController,
                    startDestination = BizurDestination.Chats.route,
                    modifier = Modifier.fillMaxSize()
                ) {
                    composable(BizurDestination.Chats.route) {
                        ChatListScreen(
                            conversations = uiState.conversations,
                            isOnline = transportStatus == TransportStatus.Connected,
                            selectedConversationId = selectedConversationId,
                            onRefresh = viewModel::refreshMessages,
                            onConversationSelected = { conversation ->
                                selectedConversationId = conversation.id
                                navController.navigate(chatDetailRoute(conversation.id))
                            }
                        )
                    }
                    composable(
                        route = CHAT_DETAIL_ROUTE,
                        arguments = listOf(navArgument("conversationId") { type = NavType.StringType })
                    ) { entry ->
                        val conversationId = entry.arguments?.getString("conversationId")
                        val conversation = uiState.conversations.find { it.id == conversationId }
                        val contact = conversation?.peerId?.let { peerId -> uiState.contacts.find { it.id == peerId } }
                        val isPeerReachable = conversation?.peerId?.let { viewModel.isPeerDirectlyReachable(it) } ?: false
                        ChatDetailScreen(
                            conversation = conversation,
                            messages = uiState.messages[conversationId] ?: emptyList(),
                            mediaSendProgress = mediaSendProgress,
                            selfId = uiState.identityCode,
                            isOnline = transportStatus == TransportStatus.Connected,
                            isPeerDirectlyReachable = isPeerReachable,
                            isContactBlocked = contact?.isBlocked ?: false,
                            isContactMuted = contact?.isMuted ?: false,
                            typingConversations = typingState,
                            draft = uiState.draft,
                            onDraftChanged = viewModel::updateDraft,
                            onSendMessage = { convoId, body ->
                                selectedConversationId = convoId
                                viewModel.sendMessage(convoId, body)
                            },
                            onSendAttachment = { convoId ->
                                pendingAttachmentConversationId = convoId
                                attachmentLauncher.launch(arrayOf("image/*", "audio/*", "video/*", "*/*"))
                            },
                            onOpenAttachment = { message: Message ->
                                val path = message.attachmentPath
                                if (path.isNullOrBlank()) {
                                    Toast.makeText(context, "Attachment missing", Toast.LENGTH_SHORT).show()
                                } else {
                                    attachmentScope.launch {
                                        val uri = viewModel.exportAttachment(
                                            path,
                                            message.attachmentDisplayName ?: path,
                                            freshCopy = false
                                        )
                                        if (uri != null) {
                                            val intent = Intent(Intent.ACTION_VIEW).apply {
                                                setDataAndType(uri, message.attachmentMimeType ?: "*/*")
                                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                            }
                                            runCatching {
                                                context.startActivity(Intent.createChooser(intent, "Open attachment"))
                                            }.onFailure {
                                                Toast.makeText(context, "No app can open this attachment", Toast.LENGTH_SHORT).show()
                                            }
                                        } else {
                                            Toast.makeText(context, "Unable to open attachment", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                }
                            },
                            onShareAttachment = { message: Message ->
                                val path = message.attachmentPath
                                if (path.isNullOrBlank()) {
                                    Toast.makeText(context, "Attachment missing", Toast.LENGTH_SHORT).show()
                                } else {
                                    attachmentScope.launch {
                                        val uri = viewModel.exportAttachment(
                                            path,
                                            message.attachmentDisplayName ?: path,
                                            freshCopy = true
                                        )
                                        if (uri != null) {
                                            val intent = Intent(Intent.ACTION_SEND).apply {
                                                type = message.attachmentMimeType ?: "*/*"
                                                putExtra(Intent.EXTRA_STREAM, uri)
                                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                            }
                                            runCatching {
                                                context.startActivity(Intent.createChooser(intent, "Share attachment"))
                                            }.onFailure {
                                                Toast.makeText(context, "Unable to share attachment", Toast.LENGTH_SHORT).show()
                                            }
                                        } else {
                                            Toast.makeText(context, "Unable to share attachment", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                }
                            },
                            onResolveAttachment = { message ->
                                val path = message.attachmentPath
                                if (path.isNullOrBlank()) {
                                    null
                                } else {
                                    viewModel.exportAttachment(
                                        path,
                                        message.attachmentDisplayName ?: path,
                                        freshCopy = false
                                    )
                                }
                            },
                            onRetryMessage = { msg -> viewModel.resendMessage(msg.id) },
                            onDeleteMessage = { msg -> viewModel.deleteMessage(msg.id) },
                            onSetReaction = { msg, reaction -> viewModel.setReaction(msg.id, reaction) },
                            onMarkRead = { conversationId, peerId -> viewModel.markConversationRead(conversationId, peerId) },
                            onTyping = { conversationId, peerId -> viewModel.sendTyping(conversationId, peerId) },
                            onCall = { peerId -> ensureMicPermission { viewModel.placeCall(peerId) } },
                            onSetBlocked = viewModel::setContactBlocked,
                            onSetMuted = viewModel::setContactMuted,
                            onBack = { navController.navigateUp() }
                        )
                    }
                    composable(BizurDestination.Contacts.route) {
                        val lookupState by viewModel.lookupResult.collectAsState()
                        ContactsScreen(
                            identityCode = uiState.identityCode,
                            contacts = uiState.contacts,
                            lookupState = lookupState,
                            onPingContact = viewModel::pingContact,
                            onCreateContact = viewModel::createContact,
                            onCallContact = { contactId ->
                                ensureMicPermission { viewModel.placeCall(contactId) }
                            },
                            onToggleBlock = viewModel::setContactBlocked,
                            onToggleMute = viewModel::setContactMuted,
                            onAcceptRequest = viewModel::acceptContactRequest,
                            onRejectRequest = viewModel::rejectContactRequest,
                            onValidateCode = viewModel::validatePeerCode,
                            onClearLookup = viewModel::clearLookupResult
                        )
                    }
                    composable(BizurDestination.Calls.route) {
                        CallHistoryScreen(
                            logs = uiState.callLogs,
                            onCallContact = { contactId ->
                                ensureMicPermission { viewModel.placeCall(contactId) }
                            },
                            contacts = uiState.contacts,
                            callState = callState,
                            onEndCall = viewModel::endCall,
                            onAcceptCall = {
                                ensureMicPermission { viewModel.acceptCall() }
                            },
                            onDeclineCall = viewModel::declineCall
                        )
                    }
                    composable(BizurDestination.Settings.route) {
                        SettingsScreen(
                            identity = uiState.identityCode,
                            onReset = viewModel::resetState,
                            onClearChats = {}, // TODO: implement clearAllChats in ViewModel
                            micPermissionGranted = micPermissionGranted,
                            onRequestMicPermission = requestMicPermission,
                            notificationsGranted = notificationsGranted,
                            onRequestNotifications = requestNotificationPermission,
                            onOpenSettings = openAppSettings,
                            useDarkTheme = useDarkTheme,
                            onThemeChanged = { useDarkTheme = it }
                        )
                    }
                }

                CallStatusBanner(
                    callState = callState,
                    onEndCall = viewModel::endCall,
                    onAcceptCall = {
                        ensureMicPermission { viewModel.acceptCall() }
                    },
                    onDeclineCall = viewModel::declineCall,
                    modifier = Modifier.align(Alignment.TopCenter)
                )
            }
        }
    }
}
