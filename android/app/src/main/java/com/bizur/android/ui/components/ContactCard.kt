package com.bizur.android.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.bizur.android.model.Contact
import com.bizur.android.model.ContactStatus
import com.bizur.android.model.PresenceStatus
import kotlin.math.abs

@Composable
fun ContactCard(
    contact: Contact,
    onPing: (Contact) -> Unit,
    onCall: (Contact) -> Unit,
    onToggleMute: (Contact) -> Unit,
    onToggleBlock: (Contact) -> Unit,
    onAccept: ((Contact) -> Unit)? = null,
    onReject: ((Contact) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            ContactAvatar(contact = contact)
            Column(modifier = Modifier.weight(1f)) {
                Text(contact.displayName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                when (contact.status) {
                    ContactStatus.Accepted -> {
                        Text(contact.presence.label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    ContactStatus.PendingOutgoing -> {
                        Text("Request sent", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.tertiary)
                    }
                    ContactStatus.PendingIncoming -> {
                        Text("Wants to connect", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                    }
                }
                if (contact.isBlocked || contact.isMuted) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 4.dp)) {
                        if (contact.isBlocked) {
                            StatusChip(text = "Blocked", color = MaterialTheme.colorScheme.error)
                        }
                        if (contact.isMuted) {
                            StatusChip(text = "Muted", color = MaterialTheme.colorScheme.secondary)
                        }
                    }
                }
            }
            when (contact.status) {
                ContactStatus.Accepted -> {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp), horizontalAlignment = Alignment.End) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = { onPing(contact) }, enabled = !contact.isBlocked) {
                                Text("Ping")
                            }
                            OutlinedButton(onClick = { onCall(contact) }, enabled = !contact.isBlocked) {
                                Text("Call")
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = { onToggleMute(contact) }) {
                                Text(if (contact.isMuted) "Unmute" else "Mute")
                            }
                            OutlinedButton(onClick = { onToggleBlock(contact) }) {
                                Text(if (contact.isBlocked) "Unblock" else "Block")
                            }
                        }
                    }
                }
                ContactStatus.PendingOutgoing -> {
                    StatusChip(text = "Pending", color = MaterialTheme.colorScheme.tertiary)
                }
                ContactStatus.PendingIncoming -> {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = { onAccept?.invoke(contact) },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                        ) {
                            Text("Accept")
                        }
                        OutlinedButton(
                            onClick = { onReject?.invoke(contact) },
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                        ) {
                            Text("Reject")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ContactAvatar(contact: Contact) {
    val avatarColor = remember(contact.id) {
        val hash = abs(contact.id.hashCode())
        val hue = (hash % 360).toFloat()
        Color.hsl(hue, 0.55f, 0.65f)
    }
    val presenceColor = when (contact.presence) {
        PresenceStatus.Online -> Color(0xFF4CAF50)
        PresenceStatus.Away -> Color(0xFFFFC107)
        PresenceStatus.Offline -> Color.Gray
    }
    Box(modifier = Modifier.size(48.dp)) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            shape = CircleShape,
            color = avatarColor
        ) {
            Box(contentAlignment = Alignment.Center) {
                val initials = contact.displayName
                    .split(" ")
                    .take(2)
                    .mapNotNull { it.firstOrNull()?.uppercaseChar() }
                    .joinToString("")
                    .ifEmpty { "?" }
                Text(
                    text = initials,
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
        // Presence indicator dot
        Surface(
            modifier = Modifier
                .size(14.dp)
                .align(Alignment.BottomEnd),
            shape = CircleShape,
            color = presenceColor
        ) {}
    }
}

@Composable
private fun PresenceBadge(status: PresenceStatus) {
    val color = when (status) {
        PresenceStatus.Online -> MaterialTheme.colorScheme.primary
        PresenceStatus.Away -> MaterialTheme.colorScheme.secondary
        PresenceStatus.Offline -> MaterialTheme.colorScheme.outline
    }
    Surface(
        modifier = Modifier.size(48.dp),
        shape = RoundedCornerShape(16.dp),
        color = color.copy(alpha = 0.2f)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = status.label,
                style = MaterialTheme.typography.labelSmall,
                color = color
            )
        }
    }
}

@Composable
private fun StatusChip(text: String, color: Color) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = color.copy(alpha = 0.16f)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = color,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
        )
    }
}

private val PresenceStatus.label: String
    get() = when (this) {
        PresenceStatus.Online -> "Online"
        PresenceStatus.Away -> "Away"
        PresenceStatus.Offline -> "Offline"
    }
