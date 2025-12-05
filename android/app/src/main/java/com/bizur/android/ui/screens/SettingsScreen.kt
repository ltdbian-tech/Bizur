package com.bizur.android.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

@Composable
fun SettingsScreen(
    identity: String,
    onReset: () -> Unit,
    micPermissionGranted: Boolean,
    onRequestMicPermission: () -> Unit,
    notificationsGranted: Boolean,
    onRequestNotifications: () -> Unit,
    onOpenSettings: () -> Unit,
    useDarkTheme: Boolean,
    onThemeChanged: (Boolean) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("Your Bizur Code")
        OutlinedTextField(
            value = identity,
            onValueChange = {},
            enabled = false,
            readOnly = true
        )

        Text("Appearance")
        Card(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Theme")
                    Text(
                        text = if (useDarkTheme) "Dark" else "Light",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Switch(checked = useDarkTheme, onCheckedChange = onThemeChanged)
            }
        }

        if (!micPermissionGranted || !notificationsGranted) {
            HorizontalDivider()
            Text("Permissions")
        }

        if (!micPermissionGranted) {
            PermissionCard(
                title = "Microphone access needed",
                description = "Grant microphone permission so you can place or accept Bizur calls.",
                primaryLabel = "Grant access",
                onPrimaryClick = onRequestMicPermission,
                onSecondaryClick = onOpenSettings
            )
        }

        if (!notificationsGranted) {
            PermissionCard(
                title = "Notifications disabled",
                description = "Enable Bizur notifications to receive store-and-forward messages when offline.",
                primaryLabel = "Allow notifications",
                onPrimaryClick = onRequestNotifications,
                onSecondaryClick = onOpenSettings
            )
        }

        Button(onClick = onReset) { Text("Reset demo state") }
    }
}

@Composable
private fun PermissionCard(
    title: String,
    description: String,
    primaryLabel: String,
    onPrimaryClick: () -> Unit,
    secondaryLabel: String = "Open settings",
    onSecondaryClick: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(text = title)
            Text(text = description, maxLines = 3, overflow = TextOverflow.Ellipsis)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(onClick = onPrimaryClick) {
                    Text(primaryLabel)
                }
                OutlinedButton(onClick = onSecondaryClick) {
                    Text(secondaryLabel)
                }
            }
        }
    }
}
