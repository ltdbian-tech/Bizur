package com.bizur.android.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.bizur.android.model.CallDirection
import com.bizur.android.model.CallLog
import com.bizur.android.model.Contact
import com.bizur.android.model.ContactStatus
import com.bizur.android.model.Conversation
import com.bizur.android.model.Message
import com.bizur.android.model.MessageStatus
import com.bizur.android.model.PresenceStatus

@Entity(tableName = "contacts")
data class ContactEntity(
    @PrimaryKey val id: String,
    val displayName: String,
    val presence: String,
    val lastSeen: String,
    @ColumnInfo(defaultValue = "0") val isBlocked: Boolean = false,
    @ColumnInfo(defaultValue = "0") val isMuted: Boolean = false,
    @ColumnInfo(defaultValue = "Accepted") val status: String = "Accepted"
)

@Entity(tableName = "conversations")
data class ConversationEntity(
    @PrimaryKey val id: String,
    val peerId: String,
    val title: String,
    val lastMessagePreview: String,
    val lastActivityEpochMillis: Long,
    val unreadCount: Int,
    val isSecure: Boolean
)

@Entity(tableName = "messages")
data class MessageEntity(
    @PrimaryKey val id: String,
    val conversationId: String,
    val senderId: String,
    val body: String,
    val sentAtEpochMillis: Long,
    val status: String,
    val attachmentPath: String? = null,
    val attachmentMimeType: String? = null,
    val attachmentDisplayName: String? = null,
    @ColumnInfo(defaultValue = "") val reaction: String? = null
)

@Entity(tableName = "call_logs")
data class CallLogEntity(
    @PrimaryKey val id: String,
    val contactId: String,
    val startedAtMillis: Long,
    val durationSeconds: Int,
    val direction: String
)

fun ContactEntity.toModel() = Contact(
    id = id,
    displayName = displayName,
    presence = PresenceStatus.valueOf(presence),
    lastSeen = lastSeen,
    isBlocked = isBlocked,
    isMuted = isMuted,
    status = runCatching { ContactStatus.valueOf(status) }.getOrDefault(ContactStatus.Accepted)
)

fun Contact.toEntity() = ContactEntity(
    id = id,
    displayName = displayName,
    presence = presence.name,
    lastSeen = lastSeen,
    isBlocked = isBlocked,
    isMuted = isMuted,
    status = status.name
)

fun ConversationEntity.toModel() = Conversation(
    id = id,
    peerId = peerId,
    title = title,
    lastMessagePreview = lastMessagePreview,
    lastActivityEpochMillis = lastActivityEpochMillis,
    unreadCount = unreadCount,
    isSecure = isSecure
)

fun Conversation.toEntity() = ConversationEntity(
    id = id,
    peerId = peerId,
    title = title,
    lastMessagePreview = lastMessagePreview,
    lastActivityEpochMillis = lastActivityEpochMillis,
    unreadCount = unreadCount,
    isSecure = isSecure
)

fun MessageEntity.toModel() = Message(
    id = id,
    conversationId = conversationId,
    senderId = senderId,
    body = body,
    sentAtEpochMillis = sentAtEpochMillis,
    status = MessageStatus.valueOf(status),
    attachmentPath = attachmentPath,
    attachmentMimeType = attachmentMimeType,
    attachmentDisplayName = attachmentDisplayName,
    reaction = reaction?.ifBlank { null }
)

fun Message.toEntity() = MessageEntity(
    id = id,
    conversationId = conversationId,
    senderId = senderId,
    body = body,
    sentAtEpochMillis = sentAtEpochMillis,
    status = status.name,
    attachmentPath = attachmentPath,
    attachmentMimeType = attachmentMimeType,
    attachmentDisplayName = attachmentDisplayName,
    reaction = reaction
)

fun CallLogEntity.toModel() = CallLog(
    id = id,
    contactId = contactId,
    startedAtMillis = startedAtMillis,
    durationSeconds = durationSeconds,
    direction = CallDirection.valueOf(direction)
)

fun CallLog.toEntity() = CallLogEntity(
    id = id,
    contactId = contactId,
    startedAtMillis = startedAtMillis,
    durationSeconds = durationSeconds,
    direction = direction.name
)
