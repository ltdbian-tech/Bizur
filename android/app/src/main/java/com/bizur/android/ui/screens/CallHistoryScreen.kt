package com.bizur.android.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.bizur.android.call.CallSessionState
import com.bizur.android.model.CallLog
import com.bizur.android.model.Contact
import com.bizur.android.ui.components.CallLogCard
import com.bizur.android.ui.components.CallStatusBanner
import com.bizur.android.ui.components.SectionHeader

@Composable
fun CallHistoryScreen(
    logs: List<CallLog>,
    onCallContact: (String) -> Unit,
    contacts: List<Contact> = emptyList(),
    callState: CallSessionState = CallSessionState(),
    onEndCall: () -> Unit = {},
    onAcceptCall: () -> Unit = {},
    onDeclineCall: () -> Unit = {}
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        CallStatusBanner(
            callState = callState,
            onEndCall = onEndCall,
            onAcceptCall = onAcceptCall,
            onDeclineCall = onDeclineCall
        )
        SectionHeader(text = "Call Handshakes")
        if (logs.isEmpty()) {
            Text("No calls yet")
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f, fill = true),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(logs, key = { it.id }) { log ->
                    val displayName = contacts.find { it.id == log.contactId }?.displayName ?: log.contactId
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        CallLogCard(log = log, contactName = displayName)
                        Button(onClick = { onCallContact(log.contactId) }) {
                            Text("Call again")
                        }
                    }
                }
            }
        }
    }
}
