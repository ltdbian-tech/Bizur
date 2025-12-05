package com.bizur.android.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.bizur.android.model.Contact
import com.bizur.android.model.PresenceStatus

@Composable
fun ContactCard(
    contact: Contact,
    onPing: (Contact) -> Unit,
    onCall: (Contact) -> Unit,
    onToggleMute: (Contact) -> Unit,
    onToggleBlock: (Contact) -> Unit,
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
            PresenceBadge(status = contact.presence)
            Column(modifier = Modifier.weight(1f)) {
                Text(contact.displayName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(contact.presence.label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
