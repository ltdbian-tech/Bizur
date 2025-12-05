package com.bizur.android.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.annotation.VisibleForTesting
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Attachment
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.SignalWifiConnectedNoInternet4
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.pullrefresh.PullRefreshIndicator
import androidx.compose.material.pullrefresh.pullRefresh
import androidx.compose.material.pullrefresh.rememberPullRefreshState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.foundation.layout.imePadding
import coil.compose.AsyncImage
import com.bizur.android.data.MediaSendProgress
import com.bizur.android.model.Conversation
import com.bizur.android.model.Message
import com.bizur.android.model.MessageStatus
import com.bizur.android.ui.components.ConversationCard
import com.bizur.android.ui.components.SectionHeader
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween

private const val MESSAGE_HISTORY_LIMIT = 40
private const val MESSAGE_COLUMN_MAX_HEIGHT_DP = 320
private const val TYPING_FADE_OUT_MS = 3000L
private const val REPLY_SWIPE_THRESHOLD_DP = 48
private const val SEARCH_DEBOUNCE_MS = 300L
private val DEFAULT_REACTIONS = listOf("👍", "❤️", "😂", "🎉", "😮", "😢")

@OptIn(ExperimentalMaterialApi::class)
@Composable
fun ChatListScreen(
    conversations: List<Conversation>,
    isOnline: Boolean,
    selectedConversationId: String?,
    onRefresh: () -> Unit,
    onConversationSelected: (Conversation) -> Unit
) {
    val scope = rememberCoroutineScope()
    var refreshing by remember { mutableStateOf(false) }
    var searchQuery by rememberSaveable { mutableStateOf("") }
    val pullRefreshState = rememberPullRefreshState(
        refreshing = refreshing,
        onRefresh = {
            refreshing = true
            scope.launch {
                onRefresh()
                delay(500)
                refreshing = false
            }
        }
    )

    val filteredConversations = remember(conversations, searchQuery) {
        if (searchQuery.isBlank()) {
            conversations
        } else {
            conversations.filter { it.title.contains(searchQuery, ignoreCase = true) }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .imePadding(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        SectionHeader(text = "Secure Chats")
        if (!isOnline) {
            OfflineBanner()
        }

        TextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
            placeholder = { Text("Search contacts") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            colors = TextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
                disabledIndicatorColor = Color.Transparent
            )
        )

        if (filteredConversations.isEmpty()) {
            if (searchQuery.isNotBlank()) {
                Text(
                    text = "No contacts matching \"$searchQuery\"",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                EmptyConversationPlaceholder()
            }
        } else {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .pullRefresh(pullRefreshState)
            ) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(
                        items = filteredConversations,
                        key = { it.id },
                        contentType = { "conversation" }
                    ) { conversation ->
                        ConversationCard(
                            conversation = conversation,
                            isSelected = conversation.id == selectedConversationId,
                            onClick = { onConversationSelected(conversation) }
                        )
                    }
                }
                PullRefreshIndicator(refreshing, pullRefreshState, Modifier.align(Alignment.TopCenter))
            }
        }
    }
}

@OptIn(ExperimentalMaterialApi::class, ExperimentalMaterial3Api::class)
@Composable
fun ChatDetailScreen(
    conversation: Conversation?,
    messages: List<Message>,
    mediaSendProgress: MediaSendProgress?,
    selfId: String,
    isOnline: Boolean,
    isPeerDirectlyReachable: Boolean,
    isContactBlocked: Boolean,
    isContactMuted: Boolean,
    typingConversations: Set<String>,
    draft: String,
    onDraftChanged: (String) -> Unit,
    onSendMessage: (String, String) -> Unit,
    onSendAttachment: (String) -> Unit,
    onOpenAttachment: (Message) -> Unit,
    onShareAttachment: (Message) -> Unit,
    onResolveAttachment: suspend (Message) -> Uri?,
    onRetryMessage: (Message) -> Unit,
    onDeleteMessage: (Message) -> Unit,
    onSetReaction: (Message, String?) -> Unit,
    onMarkRead: (String, String) -> Unit,
    onTyping: (String, String) -> Unit,
    onCall: (String) -> Unit,
    onSetBlocked: (String, Boolean) -> Unit,
    onSetMuted: (String, Boolean) -> Unit,
    onBack: () -> Unit
) {
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var showSearchBar by rememberSaveable { mutableStateOf(false) }
    var debouncedSearch by remember { mutableStateOf("") }
    var replyingTo by remember { mutableStateOf<Message?>(null) }
    var showMenu by remember { mutableStateOf(false) }

    val currentConversation = conversation

    LaunchedEffect(currentConversation?.id, messages.size) {
        val convo = currentConversation ?: return@LaunchedEffect
        if (messages.any { it.senderId != selfId }) {
            onMarkRead(convo.id, convo.peerId)
        }
    }

    LaunchedEffect(searchQuery) {
        delay(SEARCH_DEBOUNCE_MS)
        debouncedSearch = searchQuery
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .imePadding(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            IconButton(onClick = onBack) {
                Icon(imageVector = Icons.Filled.ArrowBack, contentDescription = "Back")
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(text = currentConversation?.title ?: "No conversation", style = MaterialTheme.typography.titleLarge)
                if (!isOnline) {
                    Text("Offline", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            IconButton(onClick = { showSearchBar = !showSearchBar }) {
                Icon(
                    imageVector = Icons.Outlined.Search,
                    contentDescription = "Search messages"
                )
            }
            if (currentConversation != null && !isContactBlocked) {
                IconButton(onClick = { onCall(currentConversation.peerId) }) {
                    Icon(
                        imageVector = Icons.Filled.Call,
                        contentDescription = "Call"
                    )
                }
            }
            Box {
                IconButton(onClick = { showMenu = true }) {
                    Icon(
                        imageVector = Icons.Filled.MoreVert,
                        contentDescription = "More options"
                    )
                }
                DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false }
                ) {
                    currentConversation?.let { convo ->
                        DropdownMenuItem(
                            text = { Text(if (isContactBlocked) "Unblock" else "Block") },
                            onClick = {
                                showMenu = false
                                onSetBlocked(convo.peerId, !isContactBlocked)
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = if (isContactBlocked) Icons.Filled.Person else Icons.Filled.Block,
                                    contentDescription = null
                                )
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(if (isContactMuted) "Unmute" else "Mute") },
                            onClick = {
                                showMenu = false
                                onSetMuted(convo.peerId, !isContactMuted)
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = if (isContactMuted) Icons.Filled.VolumeUp else Icons.Filled.VolumeOff,
                                    contentDescription = null
                                )
                            }
                        )
                    }
                }
            }
        }

        if (showSearchBar) {
            TextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(Icons.Filled.Clear, contentDescription = "Clear")
                        }
                    }
                },
                placeholder = { Text("Search messages") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                    disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    disabledIndicatorColor = Color.Transparent
                )
            )
        }

        if (currentConversation == null) {
            Text("Conversation not found", style = MaterialTheme.typography.bodyMedium)
            return@Column
        }

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            MessageHistory(
                messages = messages,
                selfId = selfId,
                onOpenAttachment = onOpenAttachment,
                onShareAttachment = onShareAttachment,
                onResolveAttachment = onResolveAttachment,
                searchQuery = debouncedSearch,
                onReply = { replyingTo = it },
                onRetryMessage = onRetryMessage,
                onDeleteMessage = onDeleteMessage,
                onSetReaction = onSetReaction,
                isPeerTyping = typingConversations.contains(currentConversation.id)
            )
            mediaSendProgress?.let { progress ->
                MediaSendProgressRow(progress)
            }
            val canSend = draft.isNotBlank()
            MessageInputBar(
                draft = draft,
                onDraftChanged = {
                    onDraftChanged(it)
                    onTyping(currentConversation.id, currentConversation.peerId)
                },
                canAttach = isPeerDirectlyReachable,
                canSend = canSend,
                onAttach = { onSendAttachment(currentConversation.id) },
                onSend = {
                    if (draft.isNotBlank()) {
                        val replyPrefix = replyingTo?.let { target -> "↩ ${target.body.take(40)}\n" } ?: ""
                        onSendMessage(currentConversation.id, replyPrefix + draft)
                        replyingTo = null
                    }
                }
            )
            replyingTo?.let { target ->
                ReplyBanner(target = target, onDismiss = { replyingTo = null })
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun MessageHistory(
    messages: List<Message>,
    selfId: String,
    onOpenAttachment: (Message) -> Unit,
    onShareAttachment: (Message) -> Unit,
    onResolveAttachment: suspend (Message) -> Uri?,
    searchQuery: String,
    onReply: (Message) -> Unit,
    onRetryMessage: (Message) -> Unit,
    onDeleteMessage: (Message) -> Unit,
    onSetReaction: (Message, String?) -> Unit,
    isPeerTyping: Boolean
) {
    val recentMessages = remember(messages, searchQuery) {
        messages.takeLast(MESSAGE_HISTORY_LIMIT).filter { msg ->
            searchQuery.isBlank() || msg.body.contains(searchQuery, ignoreCase = true) ||
                msg.attachmentDisplayName?.contains(searchQuery, ignoreCase = true) == true
        }
    }
    val listState = rememberLazyListState()
    val haptic = LocalHapticFeedback.current
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current
    var menuMessage by remember { mutableStateOf<Message?>(null) }
    var confirmDelete by remember { mutableStateOf<Message?>(null) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var lastMessageId by remember { mutableStateOf<String?>(null) }
    var latestPeerMessageId by remember { mutableStateOf<String?>(null) }

    if (recentMessages.isEmpty()) {
        Text(
            text = "No messages yet",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        return
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = MESSAGE_COLUMN_MAX_HEIGHT_DP.dp)
    ) {
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            state = listState
        ) {
            items(
                items = recentMessages,
                key = { it.id },
                contentType = { "message" }
            ) { message ->
                val isOwnMessage = message.senderId.equals(selfId, ignoreCase = true)
                MessageBubble(
                    message = message,
                    isOwnMessage = isOwnMessage,
                    onOpenAttachment = onOpenAttachment,
                    onShareAttachment = onShareAttachment,
                    onResolveAttachment = onResolveAttachment,
                    highlightQuery = searchQuery,
                    onReply = onReply,
                    onLongPress = { menuMessage = message },
                    onRetry = { onRetryMessage(message) },
                    reaction = message.reaction,
                    onReact = { onSetReaction(message, it) }
                )
            }
            if (isPeerTyping) {
                item(key = "typing-indicator") {
                    TypingIndicatorBubble()
                }
            }
        }

        menuMessage?.let { target ->
            ModalBottomSheet(
                onDismissRequest = { menuMessage = null },
                sheetState = sheetState
            ) {
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(text = "Message actions", style = MaterialTheme.typography.titleMedium)
                    TextButton(onClick = {
                        onReply(target)
                        menuMessage = null
                    }) { Text("Reply") }
                    TextButton(onClick = {
                        clipboard.setText(androidx.compose.ui.text.AnnotatedString(target.body))
                        menuMessage = null
                    }) { Text("Copy") }
                    TextButton(onClick = {
                        if (target.attachmentPath != null) {
                            onShareAttachment(target)
                        } else {
                            val intent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT, target.body)
                            }
                            context.startActivity(Intent.createChooser(intent, "Forward message"))
                        }
                        menuMessage = null
                    }) { Text("Forward") }
                    if (target.status == MessageStatus.Failed) {
                        TextButton(onClick = {
                            onRetryMessage(target)
                            menuMessage = null
                        }) { Text("Retry send") }
                    }
                    TextButton(onClick = {
                        confirmDelete = target
                        menuMessage = null
                    }) { Text("Delete") }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        DEFAULT_REACTIONS.forEach { reaction ->
                            Surface(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .clickable {
                                        onSetReaction(target, reaction)
                                        menuMessage = null
                                    },
                                color = MaterialTheme.colorScheme.surfaceVariant
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(reaction)
                                }
                            }
                        }
                        TextButton(onClick = {
                            onSetReaction(target, null)
                            menuMessage = null
                        }) { Text("Clear") }
                    }
                }
            }
        }

        confirmDelete?.let { target ->
            AlertDialog(
                onDismissRequest = { confirmDelete = null },
                title = { Text("Delete message?") },
                text = { Text("This will remove the message for you. It won't unsend for the peer.") },
                confirmButton = {
                    TextButton(onClick = {
                        onDeleteMessage(target)
                        confirmDelete = null
                    }) { Text("Delete") }
                },
                dismissButton = {
                    TextButton(onClick = { confirmDelete = null }) { Text("Cancel") }
                }
            )
        }
    }
    LaunchedEffect(recentMessages.size) {
        if (recentMessages.isNotEmpty()) {
            listState.animateScrollToItem(recentMessages.lastIndex)
            val latest = recentMessages.last()
            if (latest.id != lastMessageId && !latest.senderId.equals(selfId, ignoreCase = true)) {
                lastMessageId = latest.id
                latestPeerMessageId = latest.id
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            }
        }
    }
}

@VisibleForTesting
@Composable
internal fun MessageInputBar(
    draft: String,
    onDraftChanged: (String) -> Unit,
    canAttach: Boolean,
    canSend: Boolean,
    onAttach: () -> Unit,
    onSend: () -> Unit
) {
    val focusManager = LocalFocusManager.current
    val haptic = LocalHapticFeedback.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(32.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onAttach, enabled = canAttach) {
            Icon(
                imageVector = Icons.Filled.Attachment,
                contentDescription = "Attach media",
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        TextField(
            value = draft,
            onValueChange = onDraftChanged,
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 4.dp),
            placeholder = { Text("Send an encrypted whisper") },
            minLines = 1,
            maxLines = 4,
            enabled = canAttach,
            keyboardOptions = KeyboardOptions.Default.copy(imeAction = ImeAction.Send),
            keyboardActions = KeyboardActions(
                onSend = {
                    if (canSend) {
                        onSend()
                        focusManager.clearFocus()
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    }
                }
            ),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = Color.Transparent,
                unfocusedContainerColor = Color.Transparent,
                disabledContainerColor = Color.Transparent,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
                disabledIndicatorColor = Color.Transparent,
                cursorColor = MaterialTheme.colorScheme.primary
            )
        )
        FilledIconButton(
            onClick = {
                if (canSend) {
                    onSend()
                    focusManager.clearFocus()
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                }
            },
            enabled = canSend
        ) {
            Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send message")
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@VisibleForTesting
@Composable
internal fun MessageBubble(
    message: Message,
    isOwnMessage: Boolean,
    onOpenAttachment: (Message) -> Unit,
    onShareAttachment: (Message) -> Unit,
    onResolveAttachment: suspend (Message) -> Uri?,
    highlightQuery: String,
    onReply: (Message) -> Unit,
    onLongPress: () -> Unit,
    onRetry: () -> Unit,
    reaction: String?,
    onReact: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val bubbleColor = if (isOwnMessage) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.surfaceVariant
    }
    val contentColor = if (isOwnMessage) {
        MaterialTheme.colorScheme.onPrimary
    } else {
        MaterialTheme.colorScheme.onSurface
    }
    val statusLabel = when (message.status) {
        MessageStatus.Sending -> "Sending"
        MessageStatus.Delivered -> "Delivered"
        MessageStatus.Failed -> "Failed"
        MessageStatus.Read -> "Read"
    }
    val bubbleShape = RoundedCornerShape(
        topStart = 20.dp,
        topEnd = 20.dp,
        bottomEnd = if (isOwnMessage) 4.dp else 20.dp,
        bottomStart = if (isOwnMessage) 20.dp else 4.dp
    )
    val timestampText = remember(message.sentAtEpochMillis) {
        val formatter = SimpleDateFormat("h:mm a", Locale.getDefault())
        formatter.format(Date(message.sentAtEpochMillis))
    }
    val hasAttachment = message.attachmentDisplayName != null && message.attachmentPath != null
    val directionLabel = if (isOwnMessage) "Sent" else "Received"
    val semanticsDescription = "$directionLabel message, $statusLabel"
    val swipeThresholdPx = with(LocalDensity.current) { REPLY_SWIPE_THRESHOLD_DP.dp.toPx() }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .semantics { contentDescription = semanticsDescription }
            .combinedClickable(
                onClick = {},
                onLongClick = onLongPress
            )
            .pointerInput(message.id) {
                detectHorizontalDragGestures { _, dragAmount ->
                    if (dragAmount > swipeThresholdPx) {
                        onReply(message)
                    }
                }
            }
            .testTag("message-bubble-${message.id}"),
        horizontalArrangement = if (isOwnMessage) Arrangement.End else Arrangement.Start
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 360.dp)
                .clip(bubbleShape)
                .background(bubbleColor)
                .padding(12.dp)
        ) {
            if (message.body.isNotBlank()) {
                val highlightColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)
                val annotatedBody = remember(message.body, highlightQuery, highlightColor, contentColor) {
                    if (highlightQuery.isBlank()) return@remember AnnotatedString(message.body)
                    val lowerBody = message.body.lowercase()
                    val lowerQuery = highlightQuery.lowercase()
                    buildAnnotatedString {
                        var startIndex = 0
                        while (true) {
                            val idx = lowerBody.indexOf(lowerQuery, startIndex)
                            if (idx == -1) {
                                append(message.body.substring(startIndex))
                                break
                            }
                            append(message.body.substring(startIndex, idx))
                            val end = idx + highlightQuery.length
                            pushStyle(
                                SpanStyle(
                                    background = highlightColor,
                                    color = contentColor
                                )
                            )
                            append(message.body.substring(idx, end))
                            pop()
                            startIndex = end
                        }
                    }
                }
                Text(
                    text = annotatedBody,
                    color = contentColor
                )
            }

            if (hasAttachment) {
                if (message.body.isNotBlank()) {
                    Spacer(modifier = Modifier.size(8.dp))
                }
                AttachmentPreviewContent(
                    message = message,
                    contentColor = contentColor,
                    onOpenAttachment = onOpenAttachment,
                    onShareAttachment = onShareAttachment,
                    onResolveAttachment = onResolveAttachment
                )
            }

            if (message.body.isNotBlank() || hasAttachment) {
                Spacer(modifier = Modifier.size(6.dp))
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = timestampText,
                    style = MaterialTheme.typography.labelSmall,
                    color = contentColor.copy(alpha = 0.8f)
                )
                Spacer(modifier = Modifier.width(8.dp))
                val statusIcon = when (message.status) {
                    MessageStatus.Sending -> Icons.Filled.Schedule
                    MessageStatus.Delivered -> Icons.Filled.Done
                    MessageStatus.Failed -> Icons.Filled.ErrorOutline
                    MessageStatus.Read -> Icons.Filled.DoneAll
                }
                val statusTint = if (message.status == MessageStatus.Read) {
                    MaterialTheme.colorScheme.primary
                } else {
                    contentColor.copy(alpha = 0.8f)
                }
                Icon(
                    imageVector = statusIcon,
                    contentDescription = statusLabel,
                    tint = statusTint,
                    modifier = Modifier.size(16.dp)
                )
                if (message.status == MessageStatus.Failed) {
                    TextButton(onClick = onRetry) {
                        Text("Tap to retry")
                    }
                }
            }
            reaction?.let {
                Spacer(modifier = Modifier.size(4.dp))
                Text(text = it, color = contentColor)
            }
        }
    }
}

@Composable
private fun AttachmentPreviewContent(
    message: Message,
    contentColor: Color,
    onOpenAttachment: (Message) -> Unit,
    onShareAttachment: (Message) -> Unit,
    onResolveAttachment: suspend (Message) -> Uri?
) {
    val previewUri by produceState<Uri?>(
        initialValue = null,
        key1 = message.id,
        key2 = message.attachmentPath
    ) {
        value = if (message.attachmentPath != null) {
            runCatching { onResolveAttachment(message) }.getOrNull()
        } else {
            null
        }
    }
    val isImage = message.attachmentMimeType?.startsWith("image/") == true

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (isImage && previewUri != null) {
                AsyncImage(
                    model = previewUri,
                    contentDescription = message.attachmentDisplayName,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(72.dp)
                        .clip(RoundedCornerShape(12.dp))
                )
            } else {
                Icon(
                    imageVector = Icons.Filled.Attachment,
                    contentDescription = null,
                    tint = contentColor
                )
            }
            Text(
                text = message.attachmentDisplayName.orEmpty(),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                color = contentColor,
                modifier = Modifier.weight(1f, fill = false)
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = { onOpenAttachment(message) }) {
                Text("Open")
            }
            IconButton(onClick = { onShareAttachment(message) }) {
                Icon(
                    imageVector = Icons.Filled.Share,
                    contentDescription = "Share attachment",
                    tint = contentColor
                )
            }
        }
    }
}

@Composable
private fun MediaSendProgressRow(progress: MediaSendProgress) {
    val totalChunks = progress.totalChunks.coerceAtLeast(1)
    val sentChunks = progress.sentChunks.coerceIn(0, totalChunks)
    val fraction = sentChunks / totalChunks.toFloat()
    val label = progress.displayName?.takeIf { it.isNotBlank() } ?: "attachment"

    Column(
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        LinearProgressIndicator(
            progress = { fraction },
            modifier = Modifier.fillMaxWidth()
        )
        Text(
            text = "Sending $label (${sentChunks}/${progress.totalChunks})",
            style = MaterialTheme.typography.bodySmall
        )
    }
}

@Composable
private fun OfflineBanner() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.error.copy(alpha = 0.1f))
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(Icons.Filled.SignalWifiConnectedNoInternet4, contentDescription = null)
        Column {
            Text("Offline", style = MaterialTheme.typography.titleSmall)
            Text(
                "Messages will send when you're back online.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun EmptyConversationPlaceholder() {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(
            imageVector = Icons.Filled.Forum,
            contentDescription = null,
            modifier = Modifier.size(48.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Text("Add a contact to start secure chats.")
    }
}

@Composable
private fun ReplyBanner(target: Message, onDismiss: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text("Replying to", style = MaterialTheme.typography.labelSmall)
            Text(
                target.body.ifBlank { target.attachmentDisplayName.orEmpty() },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        TextButton(onClick = onDismiss) { Text("Cancel") }
    }
}

@Composable
private fun TypingIndicatorBubble() {
    val transition = rememberInfiniteTransition(label = "typing")
    val dot1 by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "dot1"
    )
    val dot2 by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, delayMillis = 100, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "dot2"
    )
    val dot3 by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, delayMillis = 200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "dot3"
    )
    val alphaValues = listOf(dot1, dot2, dot3)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp)
            .semantics { contentDescription = "Peer is typing" },
        horizontalArrangement = Arrangement.Start
    ) {
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            alphaValues.forEach { alpha ->
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f + 0.6f * alpha))
                )
            }
        }
    }
}
