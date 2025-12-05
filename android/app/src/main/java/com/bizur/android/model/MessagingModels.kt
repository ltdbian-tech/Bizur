package com.bizur.android.model

data class Contact(
    val id: String,
    val displayName: String,
    val presence: PresenceStatus = PresenceStatus.Offline,
    val lastSeen: String = "",
    val isBlocked: Boolean = false,
    val isMuted: Boolean = false
)

data class Conversation(
    val id: String,
    val peerId: String,
    val title: String,
    val lastMessagePreview: String,
    val lastActivityEpochMillis: Long,
    val unreadCount: Int,
    val isSecure: Boolean = true
)

data class Message(
    val id: String,
    val conversationId: String,
    val senderId: String,
    val body: String,
    val sentAtEpochMillis: Long,
    val status: MessageStatus,
    val attachmentPath: String? = null,
    val attachmentMimeType: String? = null,
    val attachmentDisplayName: String? = null,
    val reaction: String? = null
)

data class CallLog(
    val id: String,
    val contactId: String,
    val startedAtMillis: Long,
    val durationSeconds: Int,
    val direction: CallDirection
)

data class MessageDraft(
    val conversationId: String?,
    val text: String
)

enum class CallDirection { Outgoing, Incoming }

enum class MessageStatus { Sending, Delivered, Failed, Read }

enum class PresenceStatus { Online, Away, Offline }
