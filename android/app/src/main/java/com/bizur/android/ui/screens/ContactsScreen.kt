package com.bizur.android.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import android.content.Intent
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import com.bizur.android.data.LookupState
import com.bizur.android.model.Contact
import com.bizur.android.model.ContactStatus
import com.bizur.android.ui.components.ContactCard
import com.bizur.android.ui.components.SectionHeader

@Composable
fun ContactsScreen(
    identityCode: String,
    contacts: List<Contact>,
    lookupState: LookupState,
    onPingContact: (String) -> Unit,
    onCreateContact: (String, String) -> Unit,
    onCallContact: (String) -> Unit,
    onToggleBlock: (String, Boolean) -> Unit,
    onToggleMute: (String, Boolean) -> Unit,
    onAcceptRequest: (String) -> Unit,
    onRejectRequest: (String) -> Unit,
    onValidateCode: (String) -> Unit,
    onClearLookup: () -> Unit
) {
    var newContactName by rememberSaveable { mutableStateOf("") }
    var newContactCode by rememberSaveable { mutableStateOf("") }
    var searchQuery by rememberSaveable { mutableStateOf("") }
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current

    // Separate pending incoming requests from regular contacts
    val pendingRequests = remember(contacts) {
        contacts.filter { it.status == ContactStatus.PendingIncoming }
    }
    
    val filteredContacts = remember(contacts, searchQuery) {
        val regularContacts = contacts.filter { it.status != ContactStatus.PendingIncoming }
        if (searchQuery.isBlank()) {
            regularContacts
        } else {
            regularContacts.filter { it.displayName.contains(searchQuery, ignoreCase = true) }
        }
    }

    // Clear lookup state on successful create
    LaunchedEffect(lookupState) {
        if (lookupState is LookupState.Found) {
            // Ready to create contact
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        SectionHeader(text = "Your pairing code")
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = identityCode.ifBlank { "Generating..." },
                modifier = Modifier.weight(1f)
            )
            if (identityCode.isNotBlank()) {
                IconButton(onClick = { clipboardManager.setText(AnnotatedString(identityCode)) }) {
                    Icon(
                        imageVector = Icons.Filled.ContentCopy,
                        contentDescription = "Copy pairing code"
                    )
                }
                IconButton(onClick = {
                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, "Add me on Bizur! My pairing code is: $identityCode")
                    }
                    context.startActivity(Intent.createChooser(shareIntent, "Share pairing code"))
                }) {
                    Icon(
                        imageVector = Icons.Filled.Share,
                        contentDescription = "Share pairing code"
                    )
                }
            }
        }
        
        // Show pending requests section if any
        if (pendingRequests.isNotEmpty()) {
            SectionHeader(text = "Contact Requests (${pendingRequests.size})")
            LazyColumn(
                modifier = Modifier.height((pendingRequests.size * 80).coerceAtMost(200).dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(pendingRequests, key = { it.id }) { contact ->
                    ContactCard(
                        contact = contact,
                        onPing = { },
                        onCall = { },
                        onToggleMute = { },
                        onToggleBlock = { },
                        onAccept = { onAcceptRequest(contact.id) },
                        onReject = { onRejectRequest(contact.id) }
                    )
                }
            }
        }
        
        SectionHeader(text = "Add a contact by code")
        Text(
            "Enter a peer's pairing code to send them a connection request.",
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
                onValueChange = { 
                    newContactCode = it.uppercase()
                    onClearLookup()
                },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Peer code") },
                singleLine = true,
                isError = lookupState is LookupState.Invalid,
                supportingText = when (lookupState) {
                    is LookupState.Invalid -> {{ Text(lookupState.reason, color = MaterialTheme.colorScheme.error) }}
                    is LookupState.Searching -> {{ 
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                            Text("Validating...")
                        }
                    }}
                    is LookupState.Found -> {{ Text("User found!", color = MaterialTheme.colorScheme.primary) }}
                    else -> null
                }
            )
            Button(
                onClick = {
                    onCreateContact(newContactName, newContactCode)
                    newContactName = ""
                    newContactCode = ""
                    onClearLookup()
                },
                enabled = newContactName.isNotBlank() && newContactCode.isNotBlank() && lookupState !is LookupState.Searching
            ) {
                Text("Send Request")
            }
        }
        if (contacts.isEmpty()) {
            Spacer(modifier = Modifier.height(12.dp))
            Text("Share your code and paste a peer's code here to start chatting.")
        } else {
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
            if (filteredContacts.isEmpty() && searchQuery.isNotBlank()) {
                Text(
                    text = "No contacts matching \"$searchQuery\"",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f, fill = true),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(filteredContacts, key = { it.id }) { contact ->
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
}
