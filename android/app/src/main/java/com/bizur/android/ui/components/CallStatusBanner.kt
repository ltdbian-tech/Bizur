package com.bizur.android.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.bizur.android.call.CallSessionState
import com.bizur.android.call.CallStatus
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CallStatusBanner(
    callState: CallSessionState,
    onEndCall: () -> Unit,
    onAcceptCall: (() -> Unit)? = null,
    onDeclineCall: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    if (callState.status == CallStatus.Idle) return
    var elapsed by remember { mutableStateOf(0L) }
    LaunchedEffect(callState.startedAtMillis, callState.status) {
        if (callState.status == CallStatus.Connected && callState.startedAtMillis != null) {
            while (callState.status == CallStatus.Connected) {
                elapsed = System.currentTimeMillis() - callState.startedAtMillis
                kotlinx.coroutines.delay(1_000)
            }
        } else {
            elapsed = 0L
        }
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = when (callState.status) {
                    CallStatus.Calling -> "Calling ${callState.displayName}…"
                    CallStatus.Ringing -> "Incoming call from ${callState.displayName}"
                    CallStatus.Connected -> {
                        val minutes = TimeUnit.MILLISECONDS.toMinutes(elapsed)
                        val seconds = TimeUnit.MILLISECONDS.toSeconds(elapsed) % 60
                        "Connected with ${callState.displayName} (${"%02d:%02d".format(minutes, seconds)})"
                    }
                    CallStatus.Idle -> ""
                },
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                modifier = Modifier.fillMaxWidth()
            ) {
                when (callState.status) {
                    CallStatus.Ringing -> {
                        Button(onClick = { onDeclineCall?.invoke() }) {
                            Text("Decline")
                        }
                        Button(onClick = { onAcceptCall?.invoke() }) {
                            Text("Accept")
                        }
                    }
                    CallStatus.Calling, CallStatus.Connected -> {
                        Button(onClick = onEndCall) {
                            Text("End call")
                        }
                    }
                    CallStatus.Idle -> Unit
                }
            }
        }
    }
}
