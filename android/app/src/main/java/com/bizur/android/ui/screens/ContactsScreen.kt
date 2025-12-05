package com.bizur.android.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.bizur.android.model.Contact
import com.bizur.android.ui.components.ContactCard
import com.bizur.android.ui.components.SectionHeader

@Composable
fun ContactsScreen(
    identityCode: String,
    contacts: List<Contact>,
    onPingContact: (String) -> Unit,
    onCreateContact: (String, String) -> Unit,
    onCallContact: (String) -> Unit,
    onToggleBlock: (String, Boolean) -> Unit,
    onToggleMute: (String, Boolean) -> Unit
) {
    var newContactName by rememberSaveable { mutableStateOf("") }
    var newContactCode by rememberSaveable { mutableStateOf("") }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        SectionHeader(text = "Your pairing code")
        Text(identityCode.ifBlank { "Generating..." })
        SectionHeader(text = "Add a contact by code")
        Text(
            "Blocked peers disappear from Chats & Calls but remain here so you can unblock them later.",
            style = MaterialTheme.typography.bodySmall
        )
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = newContactName,
                onValueChange = { newContactName = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Display name") },
                singleLine = true
            )
            OutlinedTextField(
                value = newContactCode,
                onValueChange = { newContactCode = it.uppercase() },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Peer code") },
                singleLine = true
            )
            Button(
                onClick = {
                    onCreateContact(newContactName, newContactCode)
                    newContactName = ""
                    newContactCode = ""
                },
                enabled = newContactName.isNotBlank() && newContactCode.isNotBlank()
            ) {
                Text("Link contact")
            }
        }
        if (contacts.isEmpty()) {
            Spacer(modifier = Modifier.height(12.dp))
            Text("Share your code and paste a peer's code here to start chatting.")
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f, fill = true),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(contacts, key = { it.id }) { contact ->
                    ContactCard(
                        contact = contact,
                        onPing = { onPingContact(contact.id) },
                        onCall = { onCallContact(contact.id) },
                        onToggleMute = { onToggleMute(contact.id, !contact.isMuted) },
                        onToggleBlock = { onToggleBlock(contact.id, !contact.isBlocked) }
                    )
                }
            }
        }
    }
}
