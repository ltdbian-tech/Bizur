package com.bizur.android.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.bizur.android.model.CallDirection
import com.bizur.android.model.CallLog
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

@Composable
fun CallLogCard(log: CallLog, contactName: String, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Text(contactName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(log.startedAtMillis.asDate(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text("${log.direction.label} • ${log.durationSeconds.asDuration()}", style = MaterialTheme.typography.bodyMedium)
        }
    }
}

private fun Long.asDate(): String {
    val formatter = SimpleDateFormat("MMM d, HH:mm", Locale.getDefault())
    return formatter.format(Date(this))
}

private fun Int.asDuration(): String {
    val minutes = TimeUnit.SECONDS.toMinutes(toLong())
    val seconds = this - TimeUnit.MINUTES.toSeconds(minutes).toInt()
    return "%d:%02d".format(minutes, seconds)
}

private val CallDirection.label: String
    get() = when (this) {
        CallDirection.Outgoing -> "Outgoing"
        CallDirection.Incoming -> "Incoming"
    }
